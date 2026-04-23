package com.endragonupgrade.attack;

import net.minecraft.core.BlockPos;
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
 * Stage 3 — the dragon crashes a shockwave into the centre of the island (the exit portal),
 * dealing damage and knocking back every living entity within 15 blocks.
 */
public final class ShockwaveAttack {
    private static final double RADIUS = 15.0;
    private static final float DAMAGE = 8.0f;
    private static final double KNOCKBACK = 2.4;

    private ShockwaveAttack() {
    }

    public static void trigger(ServerLevel level, EnderDragon dragon) {
        BlockPos centre = resolveCentre(level, dragon);
        Vec3 c = Vec3.atBottomCenterOf(centre);

        // Visual ring: expanding particle shell for a quarter second.
        for (int t = 0; t < 20; t++) {
            double radius = (RADIUS * t) / 20.0;
            int points = Math.max(16, (int) (radius * 4));
            for (int i = 0; i < points; i++) {
                double angle = (2 * Math.PI * i) / points;
                double px = c.x + Math.cos(angle) * radius;
                double pz = c.z + Math.sin(angle) * radius;
                level.sendParticles(ParticleTypes.EXPLOSION, px, c.y + 0.5, pz,
                        1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.HOSTILE, 8.0f, 0.6f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 6.0f, 0.7f);

        DamageSource source = dragon.damageSources().mobAttack(dragon);
        AABB box = new AABB(c.x - RADIUS, c.y - 3, c.z - RADIUS, c.x + RADIUS, c.y + 6, c.z + RADIUS);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e != dragon && e.isAlive() && e.distanceToSqr(c) <= RADIUS * RADIUS);

        for (LivingEntity v : victims) {
            v.hurtServer(level, source, DAMAGE);
            Vec3 away = new Vec3(v.getX() - c.x, 0.0, v.getZ() - c.z).normalize();
            v.push(away.x * KNOCKBACK, 0.8, away.z * KNOCKBACK);
            v.hurtMarked = true;
        }
    }

    private static BlockPos resolveCentre(ServerLevel level, EnderDragon dragon) {
        EnderDragonFight fight = dragon.getDragonFight();
        if (fight != null) {
            BlockPos origin = dragon.getFightOrigin();
            if (origin != null) return origin;
        }
        // Fallback: the dragon's own position.
        return dragon.blockPosition();
    }
}
