package com.endragonupgrade;

import com.endragonupgrade.attack.PurpleFireballAttack;
import com.endragonupgrade.attack.ShockwaveAttack;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Central tick handler for empowered Ender Dragons — manages stages, attack cadence, transitions
 * and broadcasts HUD/health updates to clients.
 */
public final class DragonBehavior {
    private static final Random RNG = new Random();

    private DragonBehavior() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(DragonBehavior::onServerTick);
    }

    private static void onServerTick(MinecraftServer server) {
        DragonRegistry registry = DragonRegistry.get(server);
        List<UUID> stale = new ArrayList<>();

        for (Map.Entry<UUID, EmpoweredDragonState> entry : registry.entries()) {
            EnderDragon dragon = findDragon(server, entry.getKey());
            EmpoweredDragonState state = entry.getValue();

            if (dragon == null || !dragon.isAlive()) {
                stale.add(entry.getKey());
                continue;
            }

            tickOne(server, dragon, state);
        }

        for (UUID id : stale) {
            registry.remove(id);
            broadcastClear(server);
        }
    }

    private static EnderDragon findDragon(MinecraftServer server, UUID id) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getEntity(id) instanceof EnderDragon d) return d;
        }
        return null;
    }

    private static void tickOne(MinecraftServer server, EnderDragon dragon, EmpoweredDragonState state) {
        state.tickCounter++;
        ServerLevel level = (ServerLevel) dragon.level();

        if (state.isFrozen()) {
            tickFreezeAnimation(dragon, level, state);
            return;
        }

        // Clamp to at least stage 1 once the animation finishes.
        int prevStage = state.stage;
        int newStage = computeStage(dragon);
        if (newStage != prevStage) {
            state.stage = newStage;
            onStageTransition(level, dragon, state);
        }

        runStageAttacks(level, dragon, state);

        // Broadcast HUD update every 4 ticks (5 Hz) — cheap and responsive.
        if (state.tickCounter % 4 == 0) {
            broadcastHealth(server, dragon, state);
        }
    }

    private static int computeStage(EnderDragon dragon) {
        float pct = dragon.getHealth() / dragon.getMaxHealth();
        if (pct <= 0.25f) return 3;
        if (pct <= 0.50f) return 2;
        return 1;
    }

    private static void tickFreezeAnimation(EnderDragon dragon, ServerLevel level, EmpoweredDragonState state) {
        state.freezeTicksLeft--;

        // Clamp dragon to a sitting-scanning stance so it visibly freezes.
        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.SITTING_SCANNING) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
        }
        dragon.setDeltaMovement(Vec3.ZERO);

        // Spawn end-portal particles around the dragon every tick.
        double cx = dragon.getX();
        double cy = dragon.getY() + 2.0;
        double cz = dragon.getZ();
        level.sendParticles(ParticleTypes.PORTAL, cx, cy, cz,
                40, 4.0, 3.5, 4.0, 0.8);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz,
                12, 3.0, 2.5, 3.0, 0.2);

        // Thunder sound at start, end-gateway-like rumble mid-way.
        if (state.freezeTicksLeft == EmpoweredDragonState.FREEZE_TICKS - 1) {
            level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER,
                    SoundSource.HOSTILE, 8.0f, 0.7f);
            // Let clients know the empowerment started so they can render cosmetic local effects.
            for (ServerPlayer p : level.players()) {
                ServerPlayNetworking.send(p, new NetworkPayloads.EmpowerStartPayload(dragon.getId()));
            }
        }
        if (state.freezeTicksLeft == 30) {
            level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN,
                    SoundSource.HOSTILE, 2.0f, 1.0f);
        }
        if (state.freezeTicksLeft == 0) {
            // Animation complete → jump into stage 1.
            state.stage = computeStage(dragon);
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
            level.playSound(null, cx, cy, cz, SoundEvents.WITHER_SPAWN,
                    SoundSource.HOSTILE, 4.0f, 0.9f);
            onStageTransition(level, dragon, state);
            broadcastHealth(level.getServer(), dragon, state);
        }
    }

    private static void runStageAttacks(ServerLevel level, EnderDragon dragon, EmpoweredDragonState state) {
        // Common to all stages: occasional charge bias (overrides holding-pattern more often than vanilla).
        if (state.chargeCooldown > 0) state.chargeCooldown--;
        if (state.chargeCooldown == 0) {
            var current = dragon.getPhaseManager().getCurrentPhase().getPhase();
            if (current == EnderDragonPhase.HOLDING_PATTERN || current == EnderDragonPhase.STRAFE_PLAYER) {
                Player nearest = level.getNearestPlayer(dragon, 96.0);
                if (nearest != null) {
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
                    if (dragon.getPhaseManager().getCurrentPhase() instanceof
                            net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase charge) {
                        charge.setTarget(nearest.position());
                    }
                    // Stage-1 style breath trail while charging: spawn a short-lived AoE cloud behind the dragon.
                    PurpleFireballAttack.spawnBreathCloud(level, dragon);
                }
            }
            // Rush roughly 1.5× more often than vanilla charge (~every 10s instead of ~15s).
            state.chargeCooldown = 10 * 20 + RNG.nextInt(40);
        }

        // Stage 2+
        if (state.stage >= 2) {
            if (state.endermiteCooldown > 0) state.endermiteCooldown--;
            if (state.endermiteCooldown == 0) {
                summonEndermites(level, dragon);
                state.endermiteCooldown = 8 * 20;
            }
            if (state.fireballCooldown > 0) state.fireballCooldown--;
            if (state.fireballCooldown == 0) {
                Player nearest = level.getNearestPlayer(dragon, 64.0);
                if (nearest != null) {
                    PurpleFireballAttack.fire(level, dragon, nearest);
                }
                state.fireballCooldown = 3 * 20 + RNG.nextInt(40);
            }
        }

        // Stage 3
        if (state.stage >= 3) {
            if (state.shockwaveCooldown > 0) state.shockwaveCooldown--;
            if (state.shockwaveCooldown == 0) {
                ShockwaveAttack.trigger(level, dragon);
                state.shockwaveCooldown = 12 * 20;
            }
        }
    }

    private static void summonEndermites(ServerLevel level, EnderDragon dragon) {
        Vec3 pos = dragon.position();
        for (int i = 0; i < 2; i++) {
            Endermite mite = EntityType.ENDERMITE.create(level, net.minecraft.world.entity.EntitySpawnReason.MOB_SUMMONED);
            if (mite == null) continue;
            double ox = (RNG.nextDouble() - 0.5) * 4.0;
            double oz = (RNG.nextDouble() - 0.5) * 4.0;
            mite.snapTo(pos.x + ox, pos.y, pos.z + oz, RNG.nextFloat() * 360f, 0f);
            level.addFreshEntity(mite);
            level.sendParticles(ParticleTypes.PORTAL, mite.getX(), mite.getY() + 0.5, mite.getZ(),
                    18, 0.3, 0.3, 0.3, 0.5);
        }
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMITE_AMBIENT,
                SoundSource.HOSTILE, 2.5f, 0.7f);
    }

    private static void onStageTransition(ServerLevel level, EnderDragon dragon, EmpoweredDragonState state) {
        if (state.stage < 1) return;
        MinecraftServer server = level.getServer();

        // Chat message
        Component msg = Component.literal("§5§lДракон впадает в ярость! Стадия " + state.stage);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }

        // Wither spawn sound for all players in the dragon's level
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 4.0f, 1.0f);

        // Screen shake packet
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new NetworkPayloads.StageTransitionPayload(state.stage));
        }
    }

    private static void broadcastHealth(MinecraftServer server, EnderDragon dragon, EmpoweredDragonState state) {
        NetworkPayloads.HealthUpdatePayload payload = new NetworkPayloads.HealthUpdatePayload(
                dragon.getId(), dragon.getHealth(), dragon.getMaxHealth(), Math.max(1, state.stage));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, payload);
        }
    }

    private static void broadcastClear(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, NetworkPayloads.ClearPayload.INSTANCE);
        }
    }
}
