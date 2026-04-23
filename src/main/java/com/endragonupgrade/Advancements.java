package com.endragonupgrade;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Awards the «Неуязвимый» advancement to every player in the End when a VERY_HARD dragon dies.
 */
public final class Advancements {
    public static final Identifier INVINCIBLE_ID =
            Identifier.fromNamespaceAndPath(EndRagonUpgradeMod.MOD_ID, "invincible");

    private Advancements() {
    }

    /**
     * Grants the invincible advancement to every player in an End dimension (which is where the
     * fight takes place) — matches vanilla's pattern for "Free the End".
     */
    public static void grantInvincibleNearEnd(MinecraftServer server, EmpoweredDragonState state) {
        AdvancementHolder adv = server.getAdvancements().get(INVINCIBLE_ID);
        if (adv == null) {
            EndRagonUpgradeMod.LOGGER.warn("Advancement {} not found on server", INVINCIBLE_ID);
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Level level = p.level();
            if (!(level instanceof ServerLevel sl)) continue;
            // Award in the end dimension only (where the fight happens).
            if (sl.dimension() != Level.END) continue;
            AdvancementProgress progress = p.getAdvancements().getOrStartProgress(adv);
            if (progress.isDone()) continue;
            for (String criterion : progress.getRemainingCriteria()) {
                p.getAdvancements().award(adv, criterion);
            }
        }
    }
}
