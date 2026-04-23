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
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
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
        List<EmpoweredDragonState> deceased = new ArrayList<>();

        for (Map.Entry<UUID, EmpoweredDragonState> entry : registry.entries()) {
            EnderDragon dragon;
            try {
                dragon = findDragon(server, entry.getKey());
            } catch (Throwable t) {
                EndRagonUpgradeMod.LOGGER.error("findDragon error", t);
                continue;
            }
            EmpoweredDragonState state = entry.getValue();

            if (dragon == null || !dragon.isAlive()) {
                stale.add(entry.getKey());
                deceased.add(state);
                continue;
            }

            try {
                tickOne(server, dragon, state);
            } catch (Throwable t) {
                EndRagonUpgradeMod.LOGGER.error("tickOne error for dragon {}", entry.getKey(), t);
            }
        }

        for (int i = 0; i < stale.size(); i++) {
            registry.remove(stale.get(i));
            EmpoweredDragonState state = deceased.get(i);
            broadcastClear(server);
            if (state != null && state.difficulty != Difficulty.HARD && state.stage >= 1) {
                Advancements.grantInvincibleNearEnd(server, state);
            }
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
        applyEmpowermentBuffs(dragon, state);

        int prevStage = state.stage;
        int newStage = computeStage(dragon, state);
        if (newStage != prevStage) {
            state.stage = newStage;
            onStageTransition(level, dragon, state);
        }

        if (state.chainFreezeTicksLeft > 0) {
            tickChainFreeze(level, dragon, state);
        } else {
            maintainSittingSchedule(level, dragon, state);
            runStageAttacks(level, dragon, state);
        }

        if (state.tickCounter % 4 == 0) {
            broadcastHealth(server, dragon, state);
        }
    }

    /**
     * Chain-of-God freeze tick — keeps the dragon in HOVER phase, zeros its velocity, suppresses
     * attacks/knockback. Adds a golden chain vortex around the dragon for the duration.
     */
    private static final net.minecraft.core.particles.DustParticleOptions GOLD_DUST =
            new net.minecraft.core.particles.DustParticleOptions(0xFFD54A, 1.2f);

    private static void tickChainFreeze(ServerLevel level, EnderDragon dragon, EmpoweredDragonState state) {
        state.chainFreezeTicksLeft--;
        try {
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        } catch (Throwable ignored) {
            // Some internal states may refuse the phase transition — ignore, we still zero velocity.
        }
        dragon.setDeltaMovement(0, 0, 0);

        double dx = dragon.getX(), dy = dragon.getY(), dz = dragon.getZ();
        // Golden chain corona — a helix of gold dust + end-rod sparkles.
        if (state.chainFreezeTicksLeft % 2 == 0) {
            double phase = (state.tickCounter % 40) / 40.0 * Math.PI * 2;
            for (int i = 0; i < 6; i++) {
                double layer = i / 6.0;
                double a = phase + layer * Math.PI * 2;
                double r = 4.0 + Math.sin(state.tickCounter * 0.1 + i) * 1.0;
                double px = dx + Math.cos(a) * r;
                double pz = dz + Math.sin(a) * r;
                double py = dy - 1 + layer * 10;
                level.sendParticles(GOLD_DUST, px, py, pz, 2, 0.1, 0.1, 0.1, 0.0);
                level.sendParticles(ParticleTypes.END_ROD, px, py, pz, 1, 0.02, 0.05, 0.02, 0.0);
            }
        }
        // On the last tick release with a bright flash.
        if (state.chainFreezeTicksLeft == 0) {
            level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(
                            ParticleTypes.FLASH, 1.0f, 0.9f, 0.3f),
                    dx, dy + 2, dz, 4, 0.5, 0.5, 0.5, 0.0);
            level.sendParticles(ParticleTypes.END_ROD, dx, dy + 2, dz, 80, 3.0, 3.0, 3.0, 0.4);
            level.playSound(null, dx, dy, dz, SoundEvents.HEAVY_CORE_BREAK,
                    SoundSource.HOSTILE, 4.0f, 1.4f);
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

    private static int computeStage(EnderDragon dragon, EmpoweredDragonState state) {
        float pct = dragon.getHealth() / dragon.getMaxHealth();
        if (state.difficulty == Difficulty.EXTREME) {
            // 6 stages at 5%, 15%, 30%, 50%, 70%, >70%.
            if (pct <= 0.05f) return 6;
            if (pct <= 0.15f) return 5;
            if (pct <= 0.30f) return 4;
            if (pct <= 0.50f) return 3;
            if (pct <= 0.70f) return 2;
            return 1;
        }
        if (state.difficulty == Difficulty.VERY_HARD) {
            if (pct <= 0.10f) return 4;
            if (pct <= 0.25f) return 3;
            if (pct <= 0.50f) return 2;
            return 1;
        }
        if (pct <= 0.25f) return 3;
        if (pct <= 0.50f) return 2;
        return 1;
    }

    private static void applyEmpowermentBuffs(EnderDragon dragon, EmpoweredDragonState state) {
        float targetHp = state.difficulty.maxHp;
        AttributeInstance maxHp = dragon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null && maxHp.getBaseValue() < targetHp - 0.5) {
            maxHp.setBaseValue(targetHp);
            dragon.setHealth(targetHp);
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
                state.stage = computeStage(dragon, state);
                dragon.getPhaseManager().setPhase(EnderDragonPhase.HOLDING_PATTERN);
                level.playSound(null, cx, cy, cz, SoundEvents.WITHER_SPAWN,
                        SoundSource.HOSTILE, 6.0f, 0.8f);
                level.playSound(null, cx, cy, cz, SoundEvents.ENDER_DRAGON_GROWL,
                        SoundSource.HOSTILE, 8.0f, 0.5f);
                applyEmpowermentBuffs(dragon, state);
                // Broadcast difficulty info to clients so the HP bar can pick the right colourway.
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    ServerPlayNetworking.send(p, new NetworkPayloads.DifficultyInfoPayload(state.difficulty.id()));
                }
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
        // Attack cadence by difficulty.
        boolean vh = state.difficulty == Difficulty.VERY_HARD;
        boolean ex = state.difficulty == Difficulty.EXTREME;
        int charge = ex ? 25 : vh ? 40 : 80;
        int endermite = ex ? 20 : vh ? 30 : 60;
        int fireball = ex ? 6 : vh ? 10 : 20;
        int shockwave = ex ? 30 : vh ? 40 : 80;

        // Faster charges (every ~4s) across all stages.
        if (state.chargeCooldown > 0) state.chargeCooldown--;
        if (state.chargeCooldown == 0) {
            var current = dragon.getPhaseManager().getCurrentPhase().getPhase();
            if (current == EnderDragonPhase.HOLDING_PATTERN || current == EnderDragonPhase.STRAFE_PLAYER) {
                Player nearest = level.getNearestPlayer(dragon, 128.0);
                if (nearest != null) {
                    dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
                    if (dragon.getPhaseManager().getCurrentPhase() instanceof
                            net.minecraft.world.entity.boss.enderdragon.phases.DragonChargePlayerPhase ch) {
                        ch.setTarget(nearest.position());
                    }
                    PurpleFireballAttack.spawnBreathCloud(level, dragon);
                }
            }
            state.chargeCooldown = charge + RNG.nextInt(charge / 2);
        }

        if (state.stage >= 2) {
            if (state.endermiteCooldown > 0) state.endermiteCooldown--;
            if (state.endermiteCooldown == 0) {
                summonEndermites(level, dragon, ex ? 7 : vh ? 5 : 3);
                state.endermiteCooldown = endermite;
            }
            if (state.fireballCooldown > 0) state.fireballCooldown--;
            if (state.fireballCooldown == 0) {
                Player nearest = level.getNearestPlayer(dragon, 96.0);
                if (nearest != null) {
                    PurpleFireballAttack.fire(level, dragon, nearest);
                }
                state.fireballCooldown = fireball + RNG.nextInt(fireball);
            }
            if (state.auraCooldown > 0) state.auraCooldown--;
            if (state.auraCooldown == 0) {
                applyAuraDamage(level, dragon, state);
                state.auraCooldown = 40; // 2s
            }
        }

        if (state.stage >= 3) {
            if (state.shockwaveCooldown > 0) state.shockwaveCooldown--;
            if (!state.shockwaveTell && state.shockwaveCooldown == 20) {
                state.shockwaveTell = true;
                level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                        SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 4.0f, 1.0f);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        dragon.getX(), dragon.getY() + 1.5, dragon.getZ(), 40, 1.5, 1.5, 1.5, 0.1);
            }
            if (state.shockwaveCooldown == 0) {
                ShockwaveAttack.trigger(level, dragon);
                state.shockwaveCooldown = shockwave;
                state.shockwaveTell = false;
            }
        }

        // Stage 4 (VERY_HARD and EXTREME) — radial nova + pillar columns.
        if (state.stage >= 4) {
            if (state.nova4Cooldown > 0) state.nova4Cooldown--;
            if (state.nova4Cooldown == 0) {
                radialNova(level, dragon);
                state.nova4Cooldown = ex ? 40 : 60;
            }
            if (state.pillar4Cooldown > 0) state.pillar4Cooldown--;
            if (state.pillar4Cooldown == 0) {
                firePillars(level, dragon);
                state.pillar4Cooldown = ex ? 70 : 100;
            }
        }

        // Stage 5 (EXTREME only) — continuous fireball rain around every player.
        if (state.stage >= 5 && ex) {
            if (state.rainCooldown > 0) state.rainCooldown--;
            if (state.rainCooldown == 0) {
                fireRain(level, dragon);
                state.rainCooldown = 40;
            }
        }

        // Stage 6 (EXTREME only) — call shadow clones that burst breath around players.
        if (state.stage >= 6 && ex) {
            if (state.cloneCooldown > 0) state.cloneCooldown--;
            if (state.cloneCooldown == 0) {
                shadowBurst(level, dragon);
                state.cloneCooldown = 80;
            }
        }
    }

    /**
     * Stage V attack — purple meteors rain from above every nearby player.
     */
    private static void fireRain(ServerLevel level, EnderDragon dragon) {
        for (Player p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            if (p.distanceToSqr(dragon) > 80 * 80) continue;
            // 3 meteors per player.
            for (int i = 0; i < 3; i++) {
                double ox = (RNG.nextDouble() - 0.5) * 10;
                double oz = (RNG.nextDouble() - 0.5) * 10;
                double px = p.getX() + ox;
                double py = p.getY() + 22;
                double pz = p.getZ() + oz;
                DragonFireball meteor = new DragonFireball(level, dragon, new Vec3(0, -1, 0).normalize());
                meteor.setPos(px, py, pz);
                level.addFreshEntity(meteor);
            }
        }
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 3.0f, 0.5f);
    }

    /**
     * Stage VI attack — each player gets a "shadow dragon burst": 60 particles + AoE damage
     * around them to simulate ghost clones swooping in.
     */
    private static void shadowBurst(ServerLevel level, EnderDragon dragon) {
        for (Player p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            if (p.distanceToSqr(dragon) > 96 * 96) continue;
            Vec3 pos = p.position();
            level.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(
                            ParticleTypes.DRAGON_BREATH, 1.0f),
                    pos.x, pos.y + 1, pos.z, 120, 2.5, 1.0, 2.5, 0.3);
            level.sendParticles(ParticleTypes.PORTAL,
                    pos.x, pos.y + 1, pos.z, 60, 2.0, 2.0, 2.0, 0.6);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.x, pos.y + 1, pos.z, 40, 1.5, 1.0, 1.5, 0.1);
            level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENDER_DRAGON_GROWL,
                    SoundSource.HOSTILE, 3.0f, 0.3f);
            // AoE damage
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(
                    pos.x - 3, pos.y - 1, pos.z - 3, pos.x + 3, pos.y + 3, pos.z + 3);
            DamageSource src = dragon.damageSources().mobAttack(dragon);
            for (Player q : level.getEntitiesOfClass(Player.class, box,
                    pp -> pp.isAlive() && !pp.isCreative() && !pp.isSpectator())) {
                q.hurtServer(level, src, 10.0f);
                q.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                q.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
            }
        }
    }

    /**
     * Stage 4 attack — radial purple-fireball nova around the dragon.
     */
    private static void radialNova(ServerLevel level, EnderDragon dragon) {
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0f, 0.4f);
        level.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(
                        ParticleTypes.DRAGON_BREATH, 1.0f),
                dragon.getX(), dragon.getY() + 2, dragon.getZ(), 400, 8.0, 3.0, 8.0, 0.4);
        // 12 fireballs in a horizontal ring.
        for (int i = 0; i < 12; i++) {
            double a = 2 * Math.PI * i / 12.0;
            Vec3 dir = new Vec3(Math.cos(a), 0.0, Math.sin(a));
            PurpleFireballAttack.fireDirectional(level, dragon, dir);
        }
    }

    /**
     * Stage 4 attack — several vertical pillars of flame at random spots around the player.
     */
    private static void firePillars(ServerLevel level, EnderDragon dragon) {
        Player nearest = level.getNearestPlayer(dragon, 80.0);
        if (nearest == null) return;
        Vec3 c = nearest.position();
        for (int i = 0; i < 5; i++) {
            double ox = (RNG.nextDouble() - 0.5) * 14.0;
            double oz = (RNG.nextDouble() - 0.5) * 14.0;
            double px = c.x + ox;
            double pz = c.z + oz;
            // Tell / warning particles at the ground for 1s (client-side-only, best-effort).
            level.sendParticles(ParticleTypes.FLAME, px, c.y, pz, 40, 0.5, 0.1, 0.5, 0.02);
            level.sendParticles(ParticleTypes.SMALL_FLAME, px, c.y, pz, 60, 0.4, 0.2, 0.4, 0.03);
            level.sendParticles(net.minecraft.core.particles.PowerParticleOption.create(
                            ParticleTypes.DRAGON_BREATH, 1.0f),
                    px, c.y + 3, pz, 120, 0.5, 3.0, 0.5, 0.3);
            // Damage anyone standing in the column RIGHT NOW (no delay — the warning lands at tell-time).
            AABB col = new AABB(px - 1.5, c.y - 2, pz - 1.5, px + 1.5, c.y + 6, pz + 1.5);
            DamageSource src = dragon.damageSources().mobAttack(dragon);
            for (Player p : level.getEntitiesOfClass(Player.class, col,
                    pp -> pp.isAlive() && !pp.isCreative() && !pp.isSpectator())) {
                p.hurtServer(level, src, 8.0f);
                p.setRemainingFireTicks(80);
            }
            level.playSound(null, px, c.y, pz, SoundEvents.BLAZE_SHOOT,
                    SoundSource.HOSTILE, 3.0f, 0.6f);
        }
    }

    private static void applyAuraDamage(ServerLevel level, EnderDragon dragon, EmpoweredDragonState state) {
        double radius = state.stage >= 6 ? 18.0 : state.stage >= 5 ? 15.0 : state.stage >= 4 ? 12.0 : 8.0;
        float damage = state.stage >= 6 ? 8.0f : state.stage >= 5 ? 6.0f : state.stage >= 4 ? 4.0f : 2.0f;
        Vec3 c = dragon.position();
        AABB box = new AABB(c.x - radius, c.y - radius, c.z - radius,
                c.x + radius, c.y + radius, c.z + radius);
        List<Player> nearby = level.getEntitiesOfClass(Player.class, box,
                p -> p.isAlive() && !p.isCreative() && !p.isSpectator());
        if (nearby.isEmpty()) return;

        DamageSource source = dragon.damageSources().mobAttack(dragon);
        for (Player p : nearby) {
            if (p.distanceToSqr(dragon) > radius * radius) continue;
            p.hurtServer(level, source, damage);
            p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            if (state.stage >= 4) {
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0));
            }
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
            case 4 -> ChatFormatting.DARK_RED;
            case 5 -> ChatFormatting.GOLD;
            case 6 -> ChatFormatting.DARK_PURPLE;
            default -> ChatFormatting.LIGHT_PURPLE;
        };
        String roman = switch (state.stage) {
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> "I";
        };
        Component title = Component.literal("⚡ Ярость " + roman + " ⚡")
                .withStyle(colour, ChatFormatting.BOLD);
        Component subtitle = Component.literal(switch (state.stage) {
            case 1 -> "Эндер Дракон разбужен";
            case 2 -> "Эндер Дракон освобождает тьму";
            case 3 -> "Последний рубеж — он не будет щадить";
            case 4 -> "КАТАКЛИЗМ — он стал неуязвим ко всему";
            case 5 -> "ОГНЕННЫЙ ДОЖДЬ — небо пылает";
            case 6 -> "ТЕНЕВЫЕ КЛОНЫ — финальный экзамен смерти";
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

        // Giant dragon-breath bloom — scaled up at stage 4.
        int bloomCount = state.stage >= 4 ? 500 : 200;
        level.sendParticles(
                net.minecraft.core.particles.PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f),
                dragon.getX(), dragon.getY() + 2, dragon.getZ(),
                bloomCount, 6.0, 4.0, 6.0, 0.4);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                dragon.getX(), dragon.getY() + 1, dragon.getZ(),
                state.stage >= 4 ? 8 : 3, 3.0, 1.5, 3.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD,
                dragon.getX(), dragon.getY(), dragon.getZ(),
                state.stage >= 4 ? 240 : 120, 4.0, 4.0, 4.0, 0.6);

        // Stage IV cataclysm: massive beacon beam + extra thunder + dense portal corona.
        if (state.stage >= 4) {
            level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(
                            ParticleTypes.FLASH, 1.0f, 0.1f, 0.1f),
                    dragon.getX(), dragon.getY() + 2, dragon.getZ(), 4, 2.0, 1.0, 2.0, 0.0);
            strikeLightningAround(level, dragon.getX(), dragon.getY(), dragon.getZ(), 12, 10.0);
            for (int i = 0; i < 3; i++) {
                level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                        SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 10.0f, 0.4f + i * 0.15f);
            }
            // Skyward portal corona.
            level.sendParticles(ParticleTypes.PORTAL,
                    dragon.getX(), dragon.getY() + 6, dragon.getZ(), 600, 12.0, 10.0, 12.0, 1.0);
        }
        // Stage V gold-red supernova: huge golden flash, fire columns, warden-sonic-boom roar.
        if (state.stage >= 5) {
            level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(
                            ParticleTypes.FLASH, 1.0f, 0.85f, 0.2f),
                    dragon.getX(), dragon.getY() + 2, dragon.getZ(), 8, 4.0, 2.0, 4.0, 0.0);
            for (int ring = 1; ring <= 4; ring++) {
                double r = ring * 4.0;
                for (int j = 0; j < 24; j++) {
                    double a = j / 24.0 * Math.PI * 2;
                    level.sendParticles(ParticleTypes.FLAME,
                            dragon.getX() + Math.cos(a) * r,
                            dragon.getY() + 0.5,
                            dragon.getZ() + Math.sin(a) * r,
                            4, 0.1, 0.6, 0.1, 0.0);
                }
            }
            level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 10.0f, 0.7f);
        }
        // Stage VI void-shatter: everything black, portal storm overhead, triple thunder stinger.
        if (state.stage >= 6) {
            level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(
                            ParticleTypes.FLASH, 0.05f, 0.0f, 0.3f),
                    dragon.getX(), dragon.getY() + 3, dragon.getZ(), 16, 6.0, 3.0, 6.0, 0.0);
            strikeLightningAround(level, dragon.getX(), dragon.getY(), dragon.getZ(), 20, 14.0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    dragon.getX(), dragon.getY() + 2, dragon.getZ(), 400, 10.0, 6.0, 10.0, 0.3);
            level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                    dragon.getX(), dragon.getY() + 10, dragon.getZ(), 1000, 16.0, 12.0, 16.0, 1.4);
            for (int i = 0; i < 5; i++) {
                level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                        SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 12.0f, 0.2f + i * 0.1f);
            }
        }

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, new NetworkPayloads.StageTransitionPayload(state.stage));
        }
    }

    private static void broadcastHealth(MinecraftServer server, EnderDragon dragon, EmpoweredDragonState state) {
        NetworkPayloads.HealthUpdatePayload health = new NetworkPayloads.HealthUpdatePayload(
                dragon.getId(), dragon.getHealth(), dragon.getMaxHealth(), Math.max(1, state.stage));
        NetworkPayloads.DifficultyInfoPayload diff = new NetworkPayloads.DifficultyInfoPayload(state.difficulty.id());
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, health);
            ServerPlayNetworking.send(p, diff);
        }
    }

    private static void broadcastClear(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(p, NetworkPayloads.ClearPayload.INSTANCE);
        }
    }
}
