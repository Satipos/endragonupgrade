package com.endragonupgrade.item;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.PowerParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Drop-in arrow that can be fired from a regular bow or crossbow. Inflicts heavy impact damage and,
 * when it misses every target and lands in the world, leaves a lingering dragon-breath cloud that
 * damages everything that steps through it.
 *
 * <p>Movement and rendering are inherited from {@link AbstractArrow} / {@link EntityType#ARROW} (we
 * reuse the vanilla arrow entity type so no client sync work is needed beyond the custom item).
 */
public final class DragonShotArrow extends AbstractArrow {
    public static final float IMPACT_DAMAGE = 12.0f;
    public static final float CLOUD_RADIUS = 3.5f;
    public static final int CLOUD_DURATION_TICKS = 140; // 7 seconds

    public DragonShotArrow(EntityType<? extends DragonShotArrow> type, Level level) {
        super(type, level);
        this.setBaseDamage(IMPACT_DAMAGE);
    }

    public DragonShotArrow(Level level, LivingEntity owner, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(DragonShotItem.ENTITY_TYPE, owner, level, pickupItemStack, firedFromWeapon);
        this.setBaseDamage(IMPACT_DAMAGE);
    }

    public DragonShotArrow(Level level, double x, double y, double z, ItemStack pickupItemStack, @Nullable ItemStack firedFromWeapon) {
        super(DragonShotItem.ENTITY_TYPE, x, y, z, level, pickupItemStack, firedFromWeapon);
        this.setBaseDamage(IMPACT_DAMAGE);
    }

    @Override
    public void tick() {
        super.tick();
        // Purple particle trail in flight.
        if (!this.isInGround()) {
            Level lv = this.level();
            if (lv.isClientSide()) {
                lv.addParticle(ParticleTypes.PORTAL,
                        this.getX(), this.getY(), this.getZ(),
                        (this.random.nextDouble() - 0.5) * 0.2,
                        (this.random.nextDouble() - 0.5) * 0.2,
                        (this.random.nextDouble() - 0.5) * 0.2);
                if (this.tickCount % 2 == 0) {
                    lv.addParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f),
                            this.getX(), this.getY(), this.getZ(),
                            0, 0, 0);
                }
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        // Damage already handled by base class via setBaseDamage. Add a burst effect.
        if (this.level() instanceof ServerLevel sl) {
            sl.sendParticles(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f),
                    this.getX(), this.getY(), this.getZ(),
                    40, 0.5, 0.5, 0.5, 0.2);
            sl.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 1.0f, 0.3f, 1.0f),
                    this.getX(), this.getY(), this.getZ(),
                    1, 0, 0, 0, 0);
            sl.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 1.5f, 1.3f);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        boolean firstTimeLanding = !this.isInGround();
        super.onHitBlock(result);
        if (firstTimeLanding && this.level() instanceof ServerLevel sl) {
            spawnBreathCloud(sl);
        }
    }

    private void spawnBreathCloud(ServerLevel level) {
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        AreaEffectCloud cloud = new AreaEffectCloud(level, x, y, z);
        LivingEntity owner = this.getOwner() instanceof LivingEntity le ? le : null;
        if (owner != null) cloud.setOwner(owner);
        cloud.setRadius(CLOUD_RADIUS);
        cloud.setDuration(CLOUD_DURATION_TICKS);
        cloud.setRadiusPerTick(-(CLOUD_RADIUS - 0.5f) / (float) CLOUD_DURATION_TICKS);
        cloud.setCustomParticle(PowerParticleOption.create(ParticleTypes.DRAGON_BREATH, 1.0f));
        cloud.setPotionDurationScale(0.25f);
        cloud.addEffect(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 1));
        level.addFreshEntity(cloud);

        level.sendParticles(ParticleTypes.PORTAL, x, y + 0.5, z, 40, 1.0, 0.5, 1.0, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.3, z, 8, 0.6, 0.3, 0.6, 0.05);
        level.playSound(null, x, y, z, SoundEvents.DRAGON_FIREBALL_EXPLODE, SoundSource.PLAYERS, 2.0f, 1.1f);
        level.playSound(null, x, y, z, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 1.5f, 1.4f);
    }

    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(DragonShotItem.INSTANCE);
    }
}
