package com.endragonupgrade;

import com.endragonupgrade.attack.PurpleFireballAttack;
import com.endragonupgrade.attack.ShockwaveAttack;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.monster.Endermite;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Central tick handler for empowered Ender Dragons — manages the freeze animation, stage
 * progression, attack cadence (approximately 3× faster than v1.0.0), passive damage aura,
 * cosmetic particle aura, and broadcasts HUD/health updates to clients.
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

        // Always-on cosmetic aura while empowered.
        spawnAura(level, dragon, state);

        if (state.isFrozen()) {
            tickFreezeAnimation(dragon, level, state);
            return;
        }

        // Buff MAX_HEALTH the first time we exit freeze (once per dragon) and apply glowing tag.
        applyEmpowermentBuffs(dragon);

        int prevStage = state.stage;
        int newStage = computeStage(dragon);
        if (newStage != prevStage) {
            state.stage = newStage;
            onStageTransition(level, dragon, state);
        }

        maintainSittingSchedule(level, dragon, state);
        runStageAttacks(level, dragon, state);

        if (state.tickCounter % 4 == 0) {
            broadcastHealth(server, dragon, state);
        }
    }

    /**
     * Periodically forces the dragon to land on the end portal so players get melee windows.
     * Schedules a landing every ~15 s of flying; once the dragon actually sits, keeps track of
     * how long it has been down and forces a takeoff after {@link EmpoweredDragonState#SIT_HOLD_TICKS}.
     */
    private static void maintainSittingSchedule(ServerLevel level, EnderDragon dragon, EmpoweredDragonState state) {
        var current = dragon.getPhaseManager().getCurrentPhase().getPhase();
        boolean sitting = current == EnderDragonPhase.SITTING_SCANNING
                || current == EnderDragonPhase.SITTING_ATTACKING
                || current == EnderDragonPhase.SITTING_FLAMING;

        if (sitting) {
            if (state.sitDurationTicksLeft <= 0) {
                state.sitDurationTicksLeft = EmpoweredDragonState.SIT_HOLD_TICKS;
            } else {
                state.sitDurationTicksLeft--;
                // Periodic breath cloud around the sitting dragon for cinematic effect.
                if (state.sitDurationTicksLeft % 20 == 0) {
                    level.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(
                                    ParticleTypes.DRAGON_BREATH, 1.0f),
                            dragon.getX(), dragon.getY(), dragon.getZ(),
                            40, 4.0, 1.5, 4.0, 0.2);
                }
                if (state.sitDurationTicksLeft <= 0) {
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.TAKEOFF);
                    state.sitScheduleCooldown = EmpoweredDragonState.SIT_INTERVAL_TICKS;
                }
            }
            return;
        }

        // Flying — count down until the next scheduled landing.
        if (state.sitScheduleCooldown > 0) {
            state.sitScheduleCooldown--;
            return;
        }

        // Only nudge if the dragon is idle/flying (don't abort combat phases).
        if (current == EnderDragonPhase.HOLDING_PATTERN || current == EnderDragonPhase.STRAFE_PLAYER) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.LANDING_APPROACH);
            level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 4.0f, 0.8f);
            // Signal players with a brief chat hint
            Component hint = Component.literal("§d§oДракон идёт на посадку...");
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(hint, true); // overlay / action bar
            }
            state.sitScheduleCooldown = EmpoweredDragonState.SIT_INTERVAL_TICKS;
        } else {
            // Combat phase in progress — try again in 1 second.
            state.sitScheduleCooldown = 20;
        }
    }

    private static int computeStage(EnderDragon dragon) {
        float pct = dragon.getHealth() / dragon.getMaxHealth();
        if (pct <= 0.25f) return 3;
        if (pct <= 0.50f) return 2;
        return 1;
    }

    private static void applyEmpowermentBuffs(EnderDragon dragon) {
        AttributeInstance maxHp = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null && maxHp.getBaseValue() < EmpoweredDragonState.EMPOWERED_MAX_HEALTH - 0.5) {
            maxHp.setBaseValue(EmpoweredDragonState.EMPOWERED_MAX_HEALTH);
            dragon.setHealth(EmpoweredDragonState.EMPOWERED_MAX_HEALTH);
        }
        if (!dragon.hasGlowingTag()) {
            dragon.setGlowingTag(true);
        }
    }

    // -------------------- Aura & cosmetic ticks --------------------

    private static void spawnAura(ServerLevel level, EnderDragon dragon, EmpoweredDragonState state) {
        double x = dragon.getX();
        double y = dragon.getY() + 2.5;
        double z = dragon.getZ();
        level.sendParticles(ParticleTypes.PORTAL, x, y, z, 6, 3.5, 2.5, 3.5, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 2, 3.0, 2.0, 3.0, 0.01);
        if (state.tickCounter % 3 == 0) {
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y - 1.0, z, 1, 2.5, 1.5, 2.5, 0.02);
        }
        if (state.tickCounter % 5 == 0) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, y, z, 3, 3.0, 2.0, 3.0, 0.3);
        }
    }

    // -------------------- Freeze / activation animation (5s) --------------------

    private static void tickFreezeAnimation(EnderDragon dragon, ServerLevel level, EmpoweredDragonState state) {
        state.freezeTicksLeft--;
        int elapsed = EmpoweredDragonState.FREEZE_TICKS - state.freezeTicksLeft;

        if (dragon.getPhaseManager().getCurrentPhase().getPhase() != EnderDragonPhase.SITTING_SCANNING) {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.SITTING_SCANNING);
        }
        dragon.setDeltaMovement(Vec3.ZERO);

        double cx = dragon.getX();
        double cy = dragon.getY() + 2.0;
        double cz = dragon.getZ();

        // Rising portal vortex — density ramps up through the animation.
        int density = 40 + elapsed; // 40..140
        level.sendParticles(ParticleTypes.PORTAL, cx, cy, cz, density, 5.0, 4.0, 5.0, 1.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, cx, cy, cz, 18, 3.0, 2.5, 3.0, 0.3);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 6, 4.0, 3.0, 4.0, 0.05);

        // Scripted beats — each at a specific elapsed tick.
        switch (elapsed) {
            case 1 -> {
                level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.HOSTILE, 10.0f, 0.7f);
                level.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_GROWL,
                        SoundSource.HOSTILE, 6.0f, 0.6f);
                broadcastEmpowerStart(level, dragon);
            }
            case 20 -> strikeLightningAround(level, cx, cy, cz, 3, 6.0);
            case 40 -> {
                level.playSound(null, cx, cy, cz, SoundEvents.END_PORTAL_SPAWN,
                        SoundSource.HOSTILE, 2.0f, 0.9f);
                strikeLightningAround(level, cx, cy, cz, 5, 8.0);
            }
            case 60 -> {
                level.playSound(null, cx, cy, cz, SoundEvents.WARDEN_SONIC_BOOM,
                        SoundSource.HOSTILE, 6.0f, 0.8f);
                // Outward particle shockwave
                for (int i = 0; i < 60; i++) {
                    double a = 2 * Math.PI * i / 60.0;
                    level.sendParticles(ParticleTypes.EXPLOSION,
                            cx + Math.cos(a) * 10, cy - 1, cz + Math.sin(a) * 10, 1, 0, 0, 0, 0);
                }
            }
            case 80 -> strikeLightningAround(level, cx, cy, cz, 8, 10.0);
            case 100 -> {
                // Release — animation complete.
                state.stage = computeStage(dragon);
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                level.playSound(null, cx, cy, cz, SoundEvents.WITHER_SPAWN,
                        SoundSource.HOSTILE, 6.0f, 0.8f);
                level.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_GROWL,
                        SoundSource.HOSTILE, 8.0f, 0.5f);
                applyEmpowermentBuffs(dragon);
                onStageTransition(level, dragon, state);
                broadcastHealth(level.getServer(), dragon, state);
            }
            default -> {}
        }
    }

    private static void strikeLightningAround(ServerLevel level, double cx, double cy, double cz,
                                              int bolts, double radius) {
        for (int i = 0; i < bolts; i++) {
            double angle = RNG.nextDouble() * Math.PI * 2;
            double r = radius * (0.5 + RNG.nextDouble() * 0.5);
            double bx = cx + Math.cos(angle) * r;
            double bz = cz + Math.sin(angle) * r;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
            if (bolt != null) {
                bolt.snapTo(new Vec3(bx, cy, bz));
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
    }

    private static void broadcastEmpowerStart(ServerLevel level, EnderDragon dragon) {
        for (ServerPlayer p : level.players()) {
            ServerPlayNetworking.send(p, new NetworkPayloads.EmpowerStartPayload(dragon.getId()));
        }
    }

    // -------------------- Stage combat --------------------

    private static void runStageAttacks(ServerLevel level, EnderDragon dragon, EmpoweredDragonState state) {
        // Faster charges (every ~4s) across all stages.
        if (state.chargeCooldown > 0) state.chargeCooldown--;
        if (state.chargeCooldown == 0) {
            var current = dragon.getPhaseManager().getCurrentPhase().getPhase();
            if (current == EnderDragonPhase.HOLDING_PATTERN || current == EnderDragonPhase.STRAFE_PLAYER) {
                Player nearest = level.getNearestPlayer(dragon, 128.0);
                if (nearest != null) {
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
                    if (dragon.getPhaseManager().getCurrentPhase() instanceof
                            net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase charge) {
                        charge.setTarget(nearest.position());
                    }
                    PurpleFireballAttack.spawnBreathCloud(level, dragon);
                }
            }
            state.chargeCooldown = 80 + RNG.nextInt(40); // ~4–6s
        }

        if (state.stage >= 2) {
            if (state.endermiteCooldown > 0) state.endermiteCooldown--;
            if (state.endermiteCooldown == 0) {
                summonEndermites(level, dragon, 3);
                state.endermiteCooldown = 60; // 3s
            }
            if (state.fireballCooldown > 0) state.fireballCooldown--;
            if (state.fireballCooldown == 0) {
                Player nearest = level.getNearestPlayer(dragon, 96.0);
                if (nearest != null) {
                    PurpleFireballAttack.fire(level, dragon, nearest);
                }
                state.fireballCooldown = 20 + RNG.nextInt(20); // ~1–2s
            }
            if (state.auraCooldown > 0) state.auraCooldown--;
            if (state.auraCooldown == 0) {
                applyAuraDamage(level, dragon);
                state.auraCooldown = 40; // 2s
            }
        }

        if (state.stage >= 3) {
            if (state.shockwaveCooldown > 0) state.shockwaveCooldown--;
            if (!state.shockwaveTell && state.shockwaveCooldown == 20) {
                // 1s tell: red sound + charging particles
                state.shockwaveTell = true;
                level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                        SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 4.0f, 1.0f);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        dragon.getX(), dragon.getY() + 1.5, dragon.getZ(), 40, 1.5, 1.5, 1.5, 0.1);
            }
            if (state.shockwaveCooldown == 0) {
                ShockwaveAttack.trigger(level, dragon);
                state.shockwaveCooldown = 80; // 4s
                state.shockwaveTell = false;
            }
        }
    }

    private static void applyAuraDamage(ServerLevel level, EnderDragon dragon) {
        double radius = 8.0;
        Vec3 c = dragon.position();
        AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius,
                c.x + radius, c.y + radius, c.z + radius);
        List<Player> nearby = level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        if (nearby.isEmpty()) return;

        DamageSource source = dragon.damageSources().mobAttack(dragon);
        for (Player p : nearby) {
            if (p.distanceToSqr(dragon) > radius * radius) continue;
            p.hurtServer(level, source, 2.0f);
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                    p.getX(), p.getY() + 1, p.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_AMBIENT,
                SoundSource.HOSTILE, 1.5f, 1.4f);
    }

    private static void summonEndermites(ServerLevel level, EnderDragon dragon, int count) {
        Vec3 pos = dragon.position();
        for (int i = 0; i < count; i++) {
            Endermite mite = EntityType.ENDERMITE.create(level, EntitySpawnReason.MOB_SUMMONED);
            if (mite == null) continue;
            double ox = (RNG.nextDouble() - 0.5) * 6.0;
            double oz = (RNG.nextDouble() - 0.5) * 6.0;
            mite.snapTo(pos.x + ox, pos.y, pos.z + oz, RNG.nextFloat() * 360f, 0f);
            level.addFreshEntity(mite);
            level.sendParticles(ParticleTypes.PORTAL,
                    mite.getX(), mite.getY() + 0.5, mite.getZ(),
                    36, 0.6, 0.6, 0.6, 0.8);
        }
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMITE_AMBIENT,
                SoundSource.HOSTILE, 3.0f, 0.5f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 2.5f, 0.7f);
    }

    // -------------------- Stage transitions & HUD --------------------

    private static void onStageTransition(ServerLevel level, EnderDragon dragon, EmpoweredDragonState state) {
        if (state.stage < 1) return;
        MinecraftServer server = level.getServer();

        ChatFormatting colour = switch (state.stage) {
            case 2 -> ChatFormatting.GOLD;
            case 3 -> ChatFormatting.RED;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
        String roman = switch (state.stage) {
            case 2 -> "II";
            case 3 -> "III";
            default -> "I";
        };
        Component title = Component.literal("⚡ Ярость " + roman + " ⚡")
                .withStyle(colour, ChatFormatting.BOLD);
        Component subtitle = Component.literal(switch (state.stage) {
            case 1 -> "Эндер Дракон разбужен";
            case 2 -> "Эндер Дракон освобождает тьму";
            case 3 -> "Последний рубеж — он не будет щадить";
            default -> "";
        }).withStyle(colour);
        Component chat = Component.empty()
                .append(Component.literal("⚡ ").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD))
                .append(Component.literal("Дракон впадает в ярость! Стадия " + roman).withStyle(colour, ChatFormatting.BOLD))
                .append(Component.literal(" ⚡").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(chat);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        }

        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 6.0f, 1.0f);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 8.0f, 0.5f);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 8.0f, 0.5f);

        // Cinematic lightning burst when changing stage.
        strikeLightningAround(level, dragon.getX(), dragon.getY(), dragon.getZ(),
                4 + state.stage, 6.0);

        // Giant dragon-breath bloom.
        level.sendParticles(
                net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f),
                dragon.getX(), dragon.getY() + 2, dragon.getZ(),
                200, 6.0, 4.0, 6.0, 0.4);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                dragon.getX(), dragon.getY() + 1, dragon.getZ(), 3, 3.0, 1.5, 3.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD,
                dragon.getX(), dragon.getY(), dragon.getZ(), 120, 4.0, 4.0, 4.0, 0.6);

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
