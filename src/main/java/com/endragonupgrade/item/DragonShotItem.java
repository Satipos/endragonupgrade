package com.endragonupgrade.item;

import com.endragonupgrade.EndRagonUpgradeMod;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The "Драконий Снаряд" (Dragon Shot) item — a magic arrow that any bow or crossbow can fire.
 *
 * <ul>
 *     <li>Hits deal {@link DragonShotArrow#IMPACT_DAMAGE}.</li>
 *     <li>Misses (arrow lands in the world) leave a lingering dragon-breath cloud at the landing
 *         point for {@link DragonShotArrow#CLOUD_DURATION_TICKS} ticks.</li>
 * </ul>
 *
 * <p>Because this item extends {@link ArrowItem}, the vanilla bow automatically treats it as
 * valid ammunition (via the {@code minecraft:arrows} item tag we contribute). No Mixins.
 */
public final class DragonShotItem extends ArrowItem {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(EndRagonUpgradeMod.MOD_ID, "dragon_shot");
    public static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, ID);
    public static final ResourceKey<EntityType<?>> ENTITY_KEY =
            ResourceKey.create(Registries.ENTITY_TYPE, ID);

    public static EntityType<DragonShotArrow> ENTITY_TYPE;
    public static DragonShotItem INSTANCE;

    private DragonShotItem(Item.Properties properties) {
        super(properties);
    }

    public static void register() {
        ENTITY_TYPE = Registry.register(
                BuiltInRegistries.ENTITY_TYPE,
                ENTITY_KEY,
                EntityType.Builder.<DragonShotArrow>of(DragonShotArrow::new, MobCategory.MISC)
                        .noLootTable()
                        .sized(0.5F, 0.5F)
                        .eyeHeight(0.13F)
                        .clientTrackingRange(4)
                        .updateInterval(20)
                        .build(ENTITY_KEY)
        );

        Item.Properties properties = new Item.Properties()
                .stacksTo(64)
                .rarity(Rarity.EPIC)
                .setId(ITEM_KEY);
        INSTANCE = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, new DragonShotItem(properties));
    }

    @Override
    public AbstractArrow createArrow(Level level, ItemStack itemStack, LivingEntity owner, @Nullable ItemStack firedFromWeapon) {
        return new DragonShotArrow(level, owner, itemStack.copyWithCount(1), firedFromWeapon);
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack itemStack, Direction direction) {
        DragonShotArrow arrow = new DragonShotArrow(level, position.x(), position.y(), position.z(),
                itemStack.copyWithCount(1), null);
        arrow.pickup = AbstractArrow.Pickup.ALLOWED;
        return arrow;
    }
}
