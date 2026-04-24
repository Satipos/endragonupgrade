package com.endragonupgrade;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/**
 * Listens for the activation phrase in chat and, on success, opens the difficulty-selection
 * menu on the sender's client instead of empowering the dragon immediately.
 * Also handles the {@code SelectDifficulty} response from the menu to actually empower the dragon.
 */
public final class ChatActivation {
    private ChatActivation() {
    }

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, boundChatType) -> {
            try {
                String raw = message.signedContent();
                if (raw == null) return true;
                String trimmed = raw.trim();
                if (trimmed.equals(EndRagonUpgradeMod.ACTIVATION_PHRASE)) {
                    // Defer actual activation to the menu — just open it.
                    openMenuFor(sender);
                    return false;
                }
                if (trimmed.equals(EndRagonUpgradeMod.IMPOSSIBLE_PHRASE)) {
                    // v3.5 shortcut — skip menu, empower directly on IMPOSSIBLE.
                    sender.level().getServer().execute(() -> {
                        try {
                            applySelection(sender, Difficulty.IMPOSSIBLE);
                        } catch (Throwable t) {
                            EndRagonUpgradeMod.LOGGER.error("IMPOSSIBLE direct activation failed", t);
                        }
                    });
                    return false;
                }
                return true;
            } catch (Throwable t) {
                EndRagonUpgradeMod.LOGGER.error("Chat activation error", t);
                return true;
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(NetworkPayloads.SelectDifficultyPayload.TYPE,
                (payload, ctx) -> {
                    ServerPlayer sender = ctx.player();
                    int id = payload.difficultyId();
                    sender.level().getServer().execute(() -> {
                        try {
                            applySelection(sender, Difficulty.byId(id));
                        } catch (Throwable t) {
                            EndRagonUpgradeMod.LOGGER.error("Applying difficulty selection failed", t);
                        }
                    });
                });
    }

    private static void openMenuFor(ServerPlayer sender) {
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
        ServerPlayNetworking.send(sender, NetworkPayloads.OpenMenuPayload.INSTANCE);
    }

    private static void applySelection(ServerPlayer sender, Difficulty difficulty) {
        EnderDragon target = findAliveDragon(sender);
        if (target == null) {
            sender.sendSystemMessage(Component.literal("§5[End Dragon Upgrade] §cДракон пропал или уже мёртв."));
            return;
        }
        DragonRegistry registry = DragonRegistry.get(sender.level().getServer());
        if (registry.isEmpowered(target.getUUID())) {
            sender.sendSystemMessage(Component.literal("§5[End Dragon Upgrade] §cДракон уже усилен."));
            return;
        }
        EndRagonUpgradeMod.LOGGER.info("Empowering Ender Dragon {} on difficulty {} (player {})",
                target.getUUID(), difficulty, sender.getName().getString());
        registry.empower(target, difficulty);
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
