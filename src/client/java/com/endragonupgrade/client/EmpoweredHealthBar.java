package com.endragonupgrade.client;

import com.endragonupgrade.EndRagonUpgradeMod;
import com.endragonupgrade.NetworkPayloads;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/**
 * Hand-painted HP bar for the empowered Ender Dragon.
 *
 * <p>v2 totally reworks the look:
 * <ul>
 *     <li>Multi-layer frame: outer glow halo, black bezel, inner gem-rim highlight, dark lining.</li>
 *     <li>Segmented capacity meter — 20 pips, each shaded, with dimmed pips past the current HP.</li>
 *     <li>Continuous animated fill underneath (still smoothly lerps to target HP).</li>
 *     <li>Stage-coloured palette (violet → gold → blood-red) with a travelling highlight sheen.</li>
 *     <li>Two dragon-head chevron silhouettes drawn at either end, and three stage chips above.</li>
 *     <li>"Critical HP" red vignette pulse on the bar itself when below 20 %.</li>
 * </ul>
 *
 * <p>Drawing uses only {@code fill()} rectangles — no texture atlas needed, keeps the mod single-jar.
 */
public final class EmpoweredHealthBar {
    private static final Identifier ID = Identifier.fromNamespaceAndPath(EndRagonUpgradeMod.MOD_ID, "empowered_hp_bar");

    private static final int BAR_WIDTH = 330;
    private static final int BAR_HEIGHT = 18;
    private static final int PIP_COUNT = 22;

    // Per-stage palette: { deep-fill, mid-fill, highlight, glow }
    private static final int[][] STAGE_COLORS = {
            { 0xFF38005E, 0xFFA825FF, 0xFFEE88FF, 0xFFFFC0FF }, // pre/stage 1
            { 0xFF38005E, 0xFFA825FF, 0xFFEE88FF, 0xFFFFC0FF }, // stage 1 — violet/magenta
            { 0xFF5A1800, 0xFFFF7A1F, 0xFFFFD780, 0xFFFFE0AA }, // stage 2 — molten orange
            { 0xFF4A0000, 0xFFFF2A2A, 0xFFFF9090, 0xFFFFC0C0 }  // stage 3 — blood crimson
    };

    private static float health = 0f;
    private static float maxHealth = 0f;
    private static float displayedFraction = 0f;
    private static int stage = 0;
    private static boolean visible = false;

    private EmpoweredHealthBar() {
    }

    public static boolean isActive() {
        return visible && maxHealth > 0f;
    }

    public static void register() {
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, ID, (graphics, deltaTracker) -> {
            if (!isActive()) return;

            // Smoothly animate the visible HP fraction.
            float target = clamp01(health / maxHealth);
            float delta = deltaTracker.getRealtimeDeltaTicks();
            float blend = Math.min(1f, 0.18f * delta);
            displayedFraction += (target - displayedFraction) * blend;

            int[] palette = STAGE_COLORS[Math.max(0, Math.min(3, stage))];
            int fillDeep = palette[0];
            int fillMid = palette[1];
            int highlight = palette[2];
            int glow = palette[3];

            int screenW = graphics.guiWidth();
            int cx = screenW / 2;
            int x = cx - BAR_WIDTH / 2;
            int y = 22;

            drawTitle(graphics, cx, y);
            drawStageChips(graphics, cx, y, palette);
            drawFrame(graphics, x, y, fillDeep, highlight, glow);
            drawFill(graphics, x, y, fillMid, highlight, glow);
            drawPips(graphics, x, y, highlight);
            drawSideOrnaments(graphics, x, y, highlight, glow);
            drawCriticalFlash(graphics, x, y);
            drawHpText(graphics, cx, y);
        });
    }

    private static void drawTitle(GuiGraphicsExtractor g, int cx, int y) {
        String label = "§l§5✦ §d§lE N D E R   D R A G O N §5§l✦";
        g.centeredText(Minecraft.getInstance().font, label, cx, y - 32, 0xFFFFFFFF);
        String sub = "§o§7усиленный страж конца";
        g.centeredText(Minecraft.getInstance().font, sub, cx, y - 20, 0xFFCD9FE0);
    }

    private static void drawStageChips(GuiGraphicsExtractor g, int cx, int y, int[] palette) {
        String[] labels = { "I", "II", "III" };
        int chipW = 36, chipH = 14, gap = 8;
        int total = 3 * chipW + 2 * gap;
        int cxStart = cx - total / 2;
        int chipY = y - 48;
        for (int i = 0; i < 3; i++) {
            boolean active = (i + 1) == Math.max(1, stage);
            int cx0 = cxStart + i * (chipW + gap);
            int bg = active ? palette[1] : 0xFF2A0044;
            int edge = active ? palette[3] : 0xFF5B0F8A;
            int fg = active ? 0xFF000000 : 0xFFCDA0FF;
            // drop shadow
            g.fill(cx0 + 2, chipY + 2, cx0 + chipW + 2, chipY + chipH + 2, 0x80000000);
            // outer frame
            g.fill(cx0, chipY, cx0 + chipW, chipY + chipH, 0xFF0A0012);
            g.fill(cx0 + 1, chipY + 1, cx0 + chipW - 1, chipY + chipH - 1, edge);
            g.fill(cx0 + 2, chipY + 2, cx0 + chipW - 2, chipY + chipH - 2, bg);
            g.centeredText(Minecraft.getInstance().font, labels[i],
                    cx0 + chipW / 2, chipY + 3, fg);
        }
    }

    private static void drawFrame(GuiGraphicsExtractor g, int x, int y, int deep, int highlight, int glow) {
        // Outer soft glow
        for (int k = 5; k >= 2; k--) {
            int a = 0x18 + (5 - k) * 0x14;
            int c = (a << 24) | (glow & 0x00FFFFFF);
            g.fill(x - k, y - k, x + BAR_WIDTH + k, y + BAR_HEIGHT + k, c);
        }
        // Hard bezel (black)
        g.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, 0xFF000000);
        // Gem rim (highlight colour)
        g.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, highlight);
        // Inset shadow
        g.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFF08000F);
        // Deep base colour
        g.fill(x + 1, y + 1, x + BAR_WIDTH - 1, y + BAR_HEIGHT - 1, deep);
    }

    private static void drawFill(GuiGraphicsExtractor g, int x, int y, int mid, int highlight, int glow) {
        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = BAR_WIDTH - 4;
        int innerH = BAR_HEIGHT - 4;

        int fillW = Math.round(innerW * displayedFraction);
        if (fillW <= 0) return;

        // Base fill
        g.fill(innerX, innerY, innerX + fillW, innerY + innerH, mid);
        // Top highlight strip (~1/3 of bar height) for glass sheen
        g.fill(innerX, innerY, innerX + fillW, innerY + Math.max(1, innerH / 3),
                (highlight & 0x00FFFFFF) | 0xC0000000);
        // Bottom dark strip for volume
        g.fill(innerX, innerY + innerH - 2, innerX + fillW, innerY + innerH,
                0x80000000);

        // Travelling highlight sheen — a narrow bright gradient that scrolls left-to-right
        long now = System.currentTimeMillis();
        float t = ((now / 8L) % (innerW + 40)) - 20;
        int sheenX = innerX + (int) t;
        for (int dx = -8; dx <= 8; dx++) {
            int sx = sheenX + dx;
            if (sx < innerX || sx >= innerX + fillW) continue;
            int alpha = (int) (0x50 * (1.0 - Math.abs(dx) / 8.0));
            if (alpha <= 0) continue;
            int colour = (alpha << 24) | (glow & 0x00FFFFFF);
            g.fill(sx, innerY, sx + 1, innerY + innerH, colour);
        }

        // Pulsing bright edge at the fill boundary
        if (fillW < innerW) {
            float pulse = (float) ((Math.sin(now / 120.0) + 1.0) * 0.5);
            int pulseAlpha = 0x90 + (int) (pulse * 0x60);
            int pulseColour = (pulseAlpha << 24) | (glow & 0x00FFFFFF);
            int edgeX = innerX + fillW;
            g.fill(edgeX - 1, innerY - 1, edgeX + 2, innerY + innerH + 1, pulseColour);
            // sparks just past the edge
            g.fill(edgeX + 2, innerY + innerH / 2 - 1, edgeX + 4, innerY + innerH / 2 + 1,
                    (0xE0 << 24) | (highlight & 0x00FFFFFF));
        }
    }

    private static void drawPips(GuiGraphicsExtractor g, int x, int y, int highlight) {
        // 22 pips — 2-pixel wide black notches dividing the bar into uniform segments.
        int innerX = x + 2;
        int innerY = y + 2;
        int innerW = BAR_WIDTH - 4;
        int innerH = BAR_HEIGHT - 4;
        for (int i = 1; i < PIP_COUNT; i++) {
            int px = innerX + (i * innerW) / PIP_COUNT;
            int col = (i == PIP_COUNT / 2 || i == PIP_COUNT * 3 / 4) ? highlight : 0xB0000000;
            g.fill(px, innerY, px + 1, innerY + innerH, col);
        }
    }

    private static void drawSideOrnaments(GuiGraphicsExtractor g, int x, int y, int highlight, int glow) {
        // Draw a small dragon-head chevron at each end (left-facing < on the left, > on the right)
        int[][] left = {
                {-10,  2}, {-10,  3}, {-10,  4}, {-10, 13}, {-10, 14}, {-10, 15},
                { -9,  5}, { -9, 12}, { -8,  6}, { -8, 11}, { -7,  7}, { -7, 10},
                { -6,  8}, { -6,  9}, { -5,  8}, { -5,  9},
        };
        int[][] right = {
                { 9,  2}, { 9,  3}, { 9,  4}, { 9, 13}, { 9, 14}, { 9, 15},
                { 8,  5}, { 8, 12}, { 7,  6}, { 7, 11}, { 6,  7}, { 6, 10},
                { 5,  8}, { 5,  9}, { 4,  8}, { 4,  9},
        };
        for (int[] p : left) {
            int px = x + p[0];
            int py = y + p[1];
            g.fill(px, py, px + 1, py + 1, 0xFF000000);
        }
        for (int[] p : right) {
            int px = x + BAR_WIDTH + p[0];
            int py = y + p[1];
            g.fill(px, py, px + 1, py + 1, 0xFF000000);
        }
        // Gem bullet in the middle of each chevron
        g.fill(x - 7, y + 8, x - 5, y + 10, highlight);
        g.fill(x + BAR_WIDTH + 5, y + 8, x + BAR_WIDTH + 7, y + 10, highlight);
        g.fill(x - 6, y + 8, x - 6, y + 9, glow);
        g.fill(x + BAR_WIDTH + 6, y + 8, x + BAR_WIDTH + 7, y + 9, glow);
    }

    private static void drawCriticalFlash(GuiGraphicsExtractor g, int x, int y) {
        if (displayedFraction > 0.2f) return;
        float pulse = (float) ((Math.sin(System.currentTimeMillis() / 80.0) + 1.0) * 0.5);
        int alpha = 0x40 + (int) (pulse * 0x60);
        int colour = (alpha << 24) | 0x00FF3030;
        g.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, colour);
    }

    private static void drawHpText(GuiGraphicsExtractor g, int cx, int y) {
        String hp = String.format("§f§l%.0f §7/ §f%.0f §8HP", health, maxHealth);
        g.centeredText(Minecraft.getInstance().font, hp, cx, y + BAR_HEIGHT + 4, 0xFFFFFFFF);
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }

    public static void onHealthUpdate(NetworkPayloads.HealthUpdatePayload p) {
        if (maxHealth <= 0f && p.maxHealth() > 0f) {
            displayedFraction = clamp01(p.health() / p.maxHealth());
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
