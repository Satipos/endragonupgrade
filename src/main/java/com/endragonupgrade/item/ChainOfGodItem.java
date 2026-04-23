package com.endragonupgrade.item;

import com.endragonupgrade.DragonRegistry;
import com.endragonupgrade.EmpoweredDragonState;
import com.endragonupgrade.EndRagonUpgradeMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;

/**
 * Chain of God — right-click on an empowered Ender Dragon to freeze it in place for 10s.
 *
 * <p>While frozen the dragon can't attack, can't knock players back, and holds its hover pose.
 * After firing, the item goes on a 60-second cooldown so it can't be chain-used.</p>
 */
public final class ChainOfGodItem extends Item {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(EndRagonUpgradeMod.MOD_ID, "chain_of_god");
    public static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, ID);

    public static ChainOfGodItem INSTANCE;

    /** Cooldown in ticks applied to the stack after a successful freeze. */
    public static final int COOLDOWN_TICKS = 1200; // 60s

    private ChainOfGodItem(Properties properties) {
        super(properties);
    }

    public static void register() {
        Item.Properties properties = new Item.Properties()
                .stacksTo(1)
                .rarity(Rarity.EPIC)
                .setId(ITEM_KEY);
        INSTANCE = Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, new ChainOfGodItem(properties));
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, net.minecraft.world.entity.LivingEntity entity, InteractionHand hand) {
        return tryFreeze(stack, player, entity, hand);
    }

    private InteractionResult tryFreeze(ItemStack stack, Player player, Entity entity, InteractionHand hand) {
        if (!(entity instanceof EnderDragon dragon)) return InteractionResult.PASS;
        if (!(player.level() instanceof ServerLevel level)) return InteractionResult.SUCCESS;

        EmpoweredDragonState state = DragonRegistry.get(level.getServer()).state(dragon);
        if (state == null) {
            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal("§7Эта цепь работает только на усиленном драконе."));
            return InteractionResult.CONSUME;
        }

        // Apply freeze.
        state.chainFreezeTicksLeft = EmpoweredDragonState.CHAIN_FREEZE_TICKS;

        // Visual burst at activation.
        double dx = dragon.getX(), dy = dragon.getY(), dz = dragon.getZ();
        level.sendParticles(net.minecraft.core.particles.ColorParticleOption.create(
                        ParticleTypes.FLASH, 1.0f, 0.9f, 0.3f),
                dx, dy + 2, dz, 3, 2.0, 1.0, 2.0, 0.0);
        level.sendParticles(ParticleTypes.END_ROD, dx, dy + 2, dz, 200, 5, 5, 5, 0.5);
        level.playSound(null, dx, dy, dz, SoundEvents.HEAVY_CORE_BREAK,
                SoundSource.HOSTILE, 4.0f, 0.8f);
        level.playSound(null, dx, dy, dz, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE,
                SoundSource.HOSTILE, 4.0f, 1.2f);

        // Cooldown on the player.
        player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);

        // Damage the item use? Keep stack size, don't consume for now.
        return InteractionResult.SUCCESS;
    }
}
