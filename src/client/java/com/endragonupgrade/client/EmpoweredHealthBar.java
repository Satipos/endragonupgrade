package com.endragonupgrade.client;

import com.endragonupgrade.EndRagonUpgradeMod;
import com.endragonupgrade.NetworkPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Custom Ender-Dragon HP bar. Attached before the vanilla chat layer so it sits above world-space
 * content but under modal overlays. Wider than the vanilla boss bar and drawn on a dark frame,
 * per the spec.
 *
 * <p>v1.1: smoothly lerps the displayed HP, pulses the fill colour by stage (magenta / orange /
 * red), and draws a crisp 3-segment layout with markers at the 25 % / 50 % thresholds.
 *
 * <p>Uses {@link HudElementRegistry} because the old {@code HudRenderCallback} was removed in 26.1.
 */
public final class EmpoweredHealthBar {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(EndRagonUpgradeMod.MOD_ID, "empowered_hp_bar");

    private static final int BAR_WIDTH = 300;
    private static final int BAR_HEIGHT = 14;
    private static final int FRAME_COLOR = 0xFF0A0012;
    private static final int FRAME_HIGHLIGHT = 0xFF5B0F8A;
    private static final int BACK_COLOR = 0xFF120020;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int TEXT_SHADOW = 0xFF230033;

    // Per-stage palette (inner fill, bright highlight used for pulsing glow)
    private static final int[][] STAGE_COLORS = {
            { 0xFFA825FF, 0xFFEE88FF }, // pre-stage / stage 1 — violet/magenta
            { 0xFFA825FF, 0xFFEE88FF }, // stage 1 — violet/magenta
            { 0xFFFF7A1F, 0xFFFFD780 }, // stage 2 — fire-orange
            { 0xFFFF2A2A, 0xFFFF9090 }  // stage 3 — blood red
    };

    private static float health = 0f;
    private static float maxHealth = 0f;
    private static float displayedFraction = 0f;
    private static int stage = 0;
    private static boolean visible = false;

    private EmpoweredHealthBar() {
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, (graphics, deltaTracker) -> {
            if (!visible || maxHealth <= 0f) return;

            // Smoothly animate the visible HP fraction.
            float target = Math.max(0f, Math.min(1f, health / maxHealth));
            float delta = deltaTracker.getRealtimeDeltaTicks();
            float blend = Math.min(1f, 0.18f * delta);
            displayedFraction += (target - displayedFraction) * blend;

            int screenW = graphics.guiWidth();
            int x = (screenW - BAR_WIDTH) / 2;
            int y = 20;

            int stageIdx = Math.max(0, Math.min(3, stage));
            int fillColor = STAGE_COLORS[stageIdx][0];
            int glowColor = STAGE_COLORS[stageIdx][1];

            // Outer glow (2-pixel halo)
            graphics.fill(x - 3, y - 3, x + BAR_WIDTH + 3, y + BAR_HEIGHT + 3, 0x40000000 | (glowColor & 0x00FFFFFF));
            // Frame
            graphics.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, FRAME_COLOR);
            graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, FRAME_HIGHLIGHT);
            graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, BACK_COLOR);

            // Fill
            int fillWidth = Math.round(BAR_WIDTH * displayedFraction);
            if (fillWidth > 0) {
                graphics.fill(x, y, x + fillWidth, y + BAR_HEIGHT, fillColor);
                // Highlight strip across the top third — subtle sheen
                graphics.fill(x, y, x + fillWidth, y + 3, glowColor & 0x80FFFFFF);
            }

            // Pulsing bright edge at the fill boundary
            if (fillWidth > 0 && fillWidth < BAR_WIDTH) {
                float pulse = (float) ((Math.sin(System.currentTimeMillis() / 120.0) + 1.0) * 0.5);
                int pulseAlpha = 0x80 + (int) (pulse * 0x7F);
                int pulseColor = (pulseAlpha << 24) | (glowColor & 0x00FFFFFF);
                graphics.fill(x + fillWidth - 1, y, x + fillWidth + 2, y + BAR_HEIGHT, pulseColor);
            }

            // Stage markers at 25 % and 50 % thresholds — drawn as small notched tabs.
            drawMarker(graphics, x + BAR_WIDTH / 2, y);
            drawMarker(graphics, x + BAR_WIDTH / 4, y);

            // Stage chips above the bar (filled pentagon style rectangles)
            int chipY = y - 16;
            String[] labels = { "I", "II", "III" };
            int chipW = 28, chipH = 12, gap = 6;
            int total = 3 * chipW + 2 * gap;
            int cx = (screenW - total) / 2;
            for (int i = 0; i < 3; i++) {
                boolean active = (i + 1) == Math.max(1, stage);
                int cbg = active ? STAGE_COLORS[i + 1][1] : 0xFF2A0044;
                int cfg = active ? 0xFF000000 : 0xFFCDA0FF;
                int cx0 = cx + i * (chipW + gap);
                graphics.fill(cx0, chipY, cx0 + chipW, chipY + chipH, 0xFF0A0012);
                graphics.fill(cx0 + 1, chipY + 1, cx0 + chipW - 1, chipY + chipH - 1, cbg);
                graphics.centeredText(Minecraft.getInstance().font, labels[i],
                        cx0 + chipW / 2, chipY + 2, cfg);
            }

            // Title + HP numbers
            String title = "§lEnder Dragon — Ярость";
            graphics.centeredText(Minecraft.getInstance().font, title, screenW / 2, y - 30, TEXT_COLOR);
            String hp = String.format("%.0f / %.0f", health, maxHealth);
            graphics.centeredText(Minecraft.getInstance().font, hp, screenW / 2, y + BAR_HEIGHT + 3, TEXT_COLOR);
        });
    }

    private static void drawMarker(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int y) {
        g.fill(mx - 1, y - 2, mx + 1, y + BAR_HEIGHT + 2, 0xFF000000);
        g.fill(mx, y - 1, mx + 1, y + BAR_HEIGHT + 1, 0xFFFFC6FF);
    }

    public static void onHealthUpdate(NetworkPayloads.HealthUpdatePayload p) {
        if (maxHealth <= 0f && p.maxHealth() > 0f) {
            // First packet — initialise the animated fraction so it doesn't creep from zero.
            displayedFraction = Math.max(0f, Math.min(1f, p.health() / p.maxHealth()));
        }
        health = p.health();
        maxHealth = p.maxHealth();
        stage = p.stage();
        visible = p.health() > 0f;
    }

    public static void clear() {
        visible = false;
        health = 0f;
        maxHealth = 0f;
        displayedFraction = 0f;
        stage = 0;
    }
}
