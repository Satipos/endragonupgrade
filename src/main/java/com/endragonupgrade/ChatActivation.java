package com.endragonupgrade;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/**
 * Listens for the activation phrase in chat and empowers a nearby live Ender Dragon.
 */
public final class ChatActivation {
    private ChatActivation() {
    }

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, boundChatType) -> {
            String raw = message.signedContent();
            if (raw == null) return true;
            if (!raw.trim().equals(EndRagonUpgradeMod.ACTIVATION_PHRASE)) return true;
            handleActivation(sender);
            return true;
        });
    }

    private static void handleActivation(ServerPlayer sender) {
        // Look for a living ender dragon across every loaded server level, not just the sender's one,
        // so the command works whether the player is in The End or not.
        EnderDragon target = findAliveDragon(sender);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("§5[End Dragon Upgrade] §cДракон не найден или мёртв."));
            return;
        }
        DragonRegistry registry = DragonRegistry.get(sender.level().getServer());
        if (registry.isEmpowered(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§5[End Dragon Upgrade] §cДракон уже усилен."));
            return;
        }
        EndRagonUpgradeMod.LOGGER.info("Empowering Ender Dragon {} at request of {}",
                target.getUUID(), sender.getName().getString());
        registry.empower(target);
    }

    private static EnderDragon findAliveDragon(ServerPlayer sender) {
        for (ServerLevel lvl : sender.level().getServer().getAllLevels()) {
            for (Entity e : lvl.getAllEntities()) {
                if (e instanceof EnderDragon d && d.isAlive()) {
                    return d;
                }
            }
        }
        return null;
    }
}
