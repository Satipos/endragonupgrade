package com.endragonupgrade.attack;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.phys.Vec3;

/**
 * Stage 2+ ranged attack — spits a DragonFireball (vanilla "purple fireball") that, on impact,
 * leaves a lingering dragon-breath cloud.
 *
 * <p>Also exposes a helper that drops a short breath trail cloud behind the dragon during charges
 * (stage-1 behaviour), and a particle muzzle-flash effect when firing to beef up visuals.
 */
public final class PurpleFireballAttack {
    private PurpleFireballAttack() {
    }

    public static void fire(ServerLevel level, EnderDragon dragon, LivingEntity target) {
        Vec3 mouth = dragon.getHeadLookVector(1.0f).normalize();
        Vec3 origin = dragon.head.position().add(mouth.scale(2.5));
        Vec3 toTarget = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(origin).normalize();

        DragonFireball fireball = new DragonFireball(level, dragon, toTarget);
        fireball.setPos(origin.x, origin.y, origin.z);
        level.addFreshEntity(fireball);

        // Muzzle flash
        level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1.0f, 0.3f, 1.0f),
                origin.x, origin.y, origin.z, 1, 0, 0, 0, 0);
        level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f),
                origin.x, origin.y, origin.z,
                30, 0.4, 0.4, 0.4, 0.15);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, origin.x, origin.y, origin.z,
                12, 0.3, 0.3, 0.3, 0.05);

        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 3.0f, 1.0f);
        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 2.0f, 0.6f);
    }

    /**
     * Fires a dragon fireball in an arbitrary horizontal direction (used by Stage IV radial nova).
     */
    public static void fireDirectional(ServerLevel level, EnderDragon dragon, Vec3 direction) {
        Vec3 origin = dragon.position().add(0, dragon.getBbHeight() * 0.6, 0);
        DragonFireball fireball = new DragonFireball(level, dragon, direction.normalize());
        fireball.setPos(origin.x, origin.y, origin.z);
        level.addFreshEntity(fireball);
        level.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f),
                origin.x, origin.y, origin.z, 20, 0.4, 0.4, 0.4, 0.15);
    }

    public static void spawnBreathCloud(ServerLevel level, EnderDragon dragon) {
        Vec3 back = dragon.getHeadLookVector(1.0f).reverse().normalize().scale(3.0);
        double x = dragon.getX() + back.x;
        double y = dragon.getY();
        double z = dragon.getZ() + back.z;

        AreaEffectCloud cloud = new AreaEffectCloud(level, x, y, z);
        cloud.setOwner(dragon);
        cloud.setRadius(3.5f);
        cloud.setDuration(100);
        cloud.setRadiusPerTick(-0.01f);
        cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f));
        cloud.setPotionDurationScale(0.20f);
        cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));
        level.addFreshEntity(cloud);

        level.sendParticles(ParticleTypes.PORTAL, x, y + 1, z, 25, 1.2, 0.8, 1.2, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 6, 1.0, 0.5, 1.0, 0.02);
    }
}
