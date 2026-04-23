package com.endragonupgrade.attack;

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
 * Stage 2 attack — spits a DragonFireball (vanilla "purple fireball") that, when it lands,
 * leaves a lingering dragon-breath cloud (vanilla behaviour of DragonFireball is already to do this).
 *
 * <p>Also exposes a helper that drops a short, low-duration breath cloud directly behind the dragon
 * during the faster stage-1 charge, producing the "breath trail" described in the spec.
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

        level.playSound(null, dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.HOSTILE, 2.0f, 1.1f);
    }

    public static void spawnBreathCloud(ServerLevel level, EnderDragon dragon) {
        Vec3 back = dragon.getHeadLookVector(1.0f).reverse().normalize().scale(3.0);
        double x = dragon.getX() + back.x;
        double y = dragon.getY();
        double z = dragon.getZ() + back.z;

        AreaEffectCloud cloud = new AreaEffectCloud(level, x, y, z);
        cloud.setOwner(dragon);
        cloud.setRadius(2.5f);
        cloud.setDuration(80);
        cloud.setRadiusPerTick(-0.01f);
        cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f));
        cloud.setPotionDurationScale(0.15f);
        cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 0));
        level.addFreshEntity(cloud);

        level.sendParticles(ParticleTypes.PORTAL, x, y + 1, z, 15, 1.0, 0.6, 1.0, 0.05);
    }
}
