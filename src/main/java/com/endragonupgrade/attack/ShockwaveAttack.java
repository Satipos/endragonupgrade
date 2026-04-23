package com.endragonupgrade.attack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.dimension.end.EnderDragonFight;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Stage 3 — the dragon crashes a shockwave into the centre of the island, dealing heavy damage
 * and blasting every living entity in a 20-block radius outward.
 *
 * <p>v1.1: radius 15 → 20, damage 8 → 16, knockback 2.4 → 3.6, with a much richer visual ring
 * (multi-layered particle shells + dust trails + impact flash).
 */
public final class ShockwaveAttack {
    private static final double RADIUS = 20.0;
    private static final float DAMAGE = 16.0f;
    private static final double KNOCKBACK = 3.6;

    private ShockwaveAttack() {
    }

    public static void trigger(ServerLevel level, EnderDragon dragon) {
        BlockPos centre = resolveCentre(dragon);
        Vec3 c = Vec3.atBottomCenterOf(centre);

        // Central flash
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1.0f, 0.3f, 1.0f),
                c.x, c.y + 1.0, c.z, 1, 0, 0, 0, 0);

        // Multi-layered expanding rings
        for (int t = 0; t < 30; t++) {
            double radius = (RADIUS * t) / 30.0;
            int points = Math.max(24, (int) (radius * 5));
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double px = c.x + Math.cos(angle) * radius;
                double pz = c.z + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.EXPLOSION, px, c.y + 0.5, pz,
                        1, 0.0, 0.0, 0.0, 0.0);
                if (i % 4 == 0) {
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, px, c.y + 0.8, pz,
                            2, 0.4, 0.4, 0.4, 0.02);
                }
                if (i % 6 == 0) {
                    level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, px, c.y + 0.3, pz,
                            1, 0.2, 0.2, 0.2, 0.05);
                }
            }
        }

        // Vertical column of portal particles at the impact
        for (int y = 0; y < 20; y++) {
            level.sendParticles(ParticleTypes.PORTAL, c.x, c.y + y * 0.8, c.z,
                    8, 1.0, 0.5, 1.0, 0.5);
        }

        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 12.0f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 8.0f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 6.0f, 0.8f);

        DamageSource source = dragon.damageSources().mobAttack(dragon);
        AABB box = new AABB(c.x - RADIUS, c.y - 4, c.z - RADIUS, c.x + RADIUS, c.y + 8, c.z + RADIUS);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != dragon && e.isAlive() && e.distanceToSqr(c) <= RADIUS * RADIUS);

        for (LivingEntity v : victims) {
            v.hurtServer(level, source, DAMAGE);
            Vec3 away = new Vec3(v.getX() - c.x, 0.0, v.getZ() - c.z);
            if (away.lengthSqr() < 1e-4) {
                away = new Vec3(1, 0, 0);
            }
            away = away.normalize();
            v.push(away.x * KNOCKBACK, 1.2, away.z * KNOCKBACK);
            v.hurtMarked = true;
        }
    }

    private static BlockPos resolveCentre(EnderDragon dragon) {
        EnderDragonFight fight = dragon.getDragonFight();
        if (fight != null) {
            BlockPos origin = dragon.getFightOrigin();
            if (origin != null) return origin;
        }
        return dragon.blockPosition();
    }
}
