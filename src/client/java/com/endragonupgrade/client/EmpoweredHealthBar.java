package com.endragonupgrade.client;

import com.endragonupgrade.EndRagonUpgradeMod;
import com.endragonupgrade.NetworkPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Custom Ender-Dragon HP bar. Attached before the vanilla chat layer so it sits above world-space
 * content but under modal overlays. Wider than the vanilla boss bar and drawn in purple on a dark
 * frame, per the spec.
 *
 * <p>Uses {@link HudElementRegistry} because the old {@code HudRenderCallback} was removed in 26.1.
 */
public final class EmpoweredHealthBar {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(EndRagonUpgradeMod.MOD_ID, "empowered_hp_bar");

    private static final int BAR_WIDTH = 260;
    private static final int BAR_HEIGHT = 12;
    private static final int FRAME_COLOR = 0xFF0A0012; // near-black frame
    private static final int BACK_COLOR = 0xFF1D0033;  // dim purple background
    private static final int FILL_COLOR = 0xFFA825FF;  // bright purple fill
    private static final int STAGE_MARK_COLOR = 0xFFE0A0FF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private static float health = 0f;
    private static float maxHealth = 0f;
    private static int stage = 0;
    private static boolean visible = false;

    private EmpoweredHealthBar() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, (graphics, deltaTracker) -> {
            if (!visible || maxHealth <= 0f) return;
            int screenW = graphics.guiWidth();
            int x = (screenW - BAR_WIDTH) / 2;
            int y = 18;

            // Frame (dark border, 1 pixel around the bar)
            graphics.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, FRAME_COLOR);
            // Background (dim purple)
            graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BACK_COLOR);

            // Filled portion
            float pct = Math.max(0f, Math.min(1f, health / maxHealth));
            int fillWidth = Math.round(BAR_WIDTH * pct);
            if (fillWidth > 0) {
                graphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, FILL_COLOR);
            }

            // Stage markers at 50% and 25%
            int fiftyX = x + BAR_WIDTH / 2;
            int twentyFiveX = x + (int) (BAR_WIDTH * 0.25);
            graphics.fill(fiftyX - 1, y, fiftyX + 1, y + BAR_HEIGHT, STAGE_MARK_COLOR);
            graphics.fill(twentyFiveX - 1, y, twentyFiveX + 1, y + BAR_HEIGHT, STAGE_MARK_COLOR);

            // Title
            String title = "§lEnder Dragon · Стадия " + stage;
            graphics.centeredText(Minecraft.getInstance().font, title, screenW / 2, y - 12, TEXT_COLOR);

            // HP text (e.g. 172 / 200)
            String hp = String.format("%.0f / %.0f", health, maxHealth);
            graphics.centeredText(Minecraft.getInstance().font, hp, screenW / 2, y + BAR_HEIGHT + 2, TEXT_COLOR);
        });
    }

    public static void onHealthUpdate(NetworkPayloads.HealthUpdatePayload p) {
        health = p.health();
        maxHealth = p.maxHealth();
        stage = p.stage();
        visible = p.health() > 0f;
    }

    public static void clear() {
        visible = false;
        health = 0f;
        maxHealth = 0f;
        stage = 0;
    }
}
