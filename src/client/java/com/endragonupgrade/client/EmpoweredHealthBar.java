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

    // Per-stage palette (HARD): { deep-fill, mid-fill, highlight, glow }
    private static final int[][] STAGE_COLORS = {
            { 0xFF38005E, 0xFFA825FF, 0xFFEE88FF, 0xFFFFC0FF }, // pre/stage 1
            { 0xFF38005E, 0xFFA825FF, 0xFFEE88FF, 0xFFFFC0FF }, // stage 1 — violet/magenta
            { 0xFF5A1800, 0xFFFF7A1F, 0xFFFFD780, 0xFFFFE0AA }, // stage 2 — molten orange
            { 0xFF4A0000, 0xFFFF2A2A, 0xFFFF9090, 0xFFFFC0C0 }, // stage 3 — blood crimson
            { 0xFF000000, 0xFF2A2A2A, 0xFFFF2020, 0xFFFF0040 }  // stage 4 — void/black (unused for HARD)
    };

    // VERY_HARD palette — colder, more aggressive tones.
    private static final int[][] VH_STAGE_COLORS = {
            { 0xFF120026, 0xFF6B12A0, 0xFFFF4BFF, 0xFFE080FF }, // pre/stage 1
            { 0xFF120026, 0xFF6B12A0, 0xFFFF4BFF, 0xFFE080FF }, // stage 1 — violet with hard edge
            { 0xFF3A0C00, 0xFFFF4A00, 0xFFFF9040, 0xFFFFC080 }, // stage 2 — molten
            { 0xFF1A0000, 0xFFFF0020, 0xFFFF4060, 0xFFFF9090 }, // stage 3 — crimson
            { 0xFF000000, 0xFF220022, 0xFFFF0040, 0xFFFF20FF }  // stage 4 — black-magenta void
    };

    // EXTREME palette — 6 stages, gold+crimson on a black canvas.
    private static final int[][] EX_STAGE_COLORS = {
            { 0xFF1A0D00, 0xFFFFB020, 0xFFFFE080, 0xFFFFF2B0 }, // pre/stage 1 — gold
            { 0xFF1A0D00, 0xFFFFB020, 0xFFFFE080, 0xFFFFF2B0 }, // stage 1 — gold
            { 0xFF2A1500, 0xFFFF8000, 0xFFFFC060, 0xFFFFE0A0 }, // stage 2 — amber
            { 0xFF3A0000, 0xFFFF5010, 0xFFFFA040, 0xFFFFD090 }, // stage 3 — burnt orange
            { 0xFF2A0000, 0xFFFF1020, 0xFFFF6060, 0xFFFFB0B0 }, // stage 4 — crimson
            { 0xFF1A0020, 0xFFFF00FF, 0xFFFFA0FF, 0xFFFFE0FF }, // stage 5 — shock pink
            { 0xFF000000, 0xFF400040, 0xFFFFD040, 0xFFFFFF80 }  // stage 6 — black void with gold
    };

    // IMPOSSIBLE palette — 10 stages, void-black → cosmic-magenta → supernova-white.
    private static final int[][] IM_STAGE_COLORS = {
            { 0xFF0A0014, 0xFF3A0066, 0xFF8040FF, 0xFFA080FF }, // pre/1 — dark violet
            { 0xFF0A0014, 0xFF3A0066, 0xFF8040FF, 0xFFA080FF }, // 1
            { 0xFF120022, 0xFF6010B0, 0xFFB060FF, 0xFFD0A0FF }, // 2
            { 0xFF1A0030, 0xFF9020D0, 0xFFE080FF, 0xFFFFB0FF }, // 3
            { 0xFF280000, 0xFFC02060, 0xFFFF60A0, 0xFFFFA0C0 }, // 4 — blood-magenta
            { 0xFF2A0000, 0xFFFF0040, 0xFFFF5060, 0xFFFFA0A0 }, // 5 — crimson
            { 0xFF00181A, 0xFF003A5A, 0xFF00B0FF, 0xFF60E0FF }, // 6 — void teal
            { 0xFF000000, 0xFF2A003A, 0xFF400040, 0xFF8000C0 }, // 7 — black hole
            { 0xFF000020, 0xFF0040A0, 0xFF4080FF, 0xFF80C0FF }, // 8 — void wave
            { 0xFF400000, 0xFFE02010, 0xFFFF8040, 0xFFFFE0A0 }, // 9 — meteor red-hot
            { 0xFF200020, 0xFF8000C0, 0xFFFFFFFF, 0xFFFFFFFF }  // 10 — reality tear supernova
    };

    private static float health = 0f;
    private static float maxHealth = 0f;
    private static float displayedFraction = 0f;
    private static int stage = 0;
    private static int difficultyId = 0; // 0 = HARD, 1 = VERY_HARD
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

            int[][] table = difficultyId == 3 ? IM_STAGE_COLORS
                    : difficultyId == 2 ? EX_STAGE_COLORS
                    : difficultyId == 1 ? VH_STAGE_COLORS
                    : STAGE_COLORS;
            int[] palette = table[Math.max(0, Math.min(table.length - 1, stage))];
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
            if (difficultyId >= 1) {
                drawVeryHardOrnaments(graphics, x, y, highlight, glow);
            }
            if (difficultyId == 2) {
                drawExtremeOrnaments(graphics, x, y, highlight, glow);
            }
            if (difficultyId == 3) {
                drawImpossibleOrnaments(graphics, x, y, highlight, glow);
            }
            drawCriticalFlash(graphics, x, y);
            drawHpText(graphics, cx, y);
        });
    }

    private static void drawTitle(GuiGraphicsExtractor g, int cx, int y) {
        if (difficultyId == 3) {
            // IMPOSSIBLE — void-black with flickering obfuscated sigils and supernova subtitle.
            long now = System.currentTimeMillis();
            int flick = (int) ((now / 120) % 3);
            String sigilL = flick == 0 ? "§0§l§k##" : flick == 1 ? "§c§l§k##" : "§d§l§k##";
            String sigilR = flick == 0 ? "§d§l§k##" : flick == 1 ? "§0§l§k##" : "§c§l§k##";
            String label = sigilL + "§r  §4§l§nE N D E R   D R A G O N§r  " + sigilR;
            g.centeredText(Minecraft.getInstance().font, label, cx, y - 32, 0xFFFFFFFF);
            String sub = "§4§l§n✹ Н Е В О З М О Ж Н О ✹§r  §8• §c"
                    + (int) health + " §8/ §c" + (int) maxHealth;
            g.centeredText(Minecraft.getInstance().font, sub, cx, y - 20, 0xFFFF80C0);
            return;
        }
        if (difficultyId == 2) {
            // EXTREME — gold crown + crimson title with pulsing "⚔" swords.
            long now = System.currentTimeMillis();
            boolean strobe = (now / 200) % 2 == 0;
            String label = strobe
                    ? "§6§l⚔ §c§lE N D E R   D R A G O N §6§l⚔"
                    : "§e§l⚔ §4§lE N D E R   D R A G O N §e§l⚔";
            g.centeredText(Minecraft.getInstance().font, label, cx, y - 32, 0xFFFFFFFF);
            String sub = "§6§l§n⚠ Э К С Т Р И М ⚠§r  §8• §c" + (int) health + " §8/ §c" + (int) maxHealth;
            g.centeredText(Minecraft.getInstance().font, sub, cx, y - 20, 0xFFFFD080);
        } else if (difficultyId == 1) {
            String label = "§4§l☠ §c§lE N D E R   D R A G O N §4§l☠";
            g.centeredText(Minecraft.getInstance().font, label, cx, y - 32, 0xFFFFFFFF);
            String sub = "§4§lО Ч Е Н Ь   С Л О Ж Н А Я   §8• §c" + (int) health + " §8/ §c" + (int) maxHealth;
            g.centeredText(Minecraft.getInstance().font, sub, cx, y - 20, 0xFFFFB0B0);
        } else {
            String label = "§l§5✦ §d§lE N D E R   D R A G O N §5§l✦";
            g.centeredText(Minecraft.getInstance().font, label, cx, y - 32, 0xFFFFFFFF);
            String sub = "§o§7усиленный страж конца";
            g.centeredText(Minecraft.getInstance().font, sub, cx, y - 20, 0xFFCD9FE0);
        }
    }

    private static void drawStageChips(GuiGraphicsExtractor g, int cx, int y, int[] palette) {
        int stageCount = difficultyId == 3 ? 10 : difficultyId == 2 ? 6 : difficultyId == 1 ? 4 : 3;
        String[] allLabels = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
        int chipW = difficultyId == 3 ? 16 : difficultyId == 2 ? 22 : 30;
        int chipH = 14;
        int gap = difficultyId == 3 ? 4 : difficultyId == 2 ? 6 : 8;
        int total = stageCount * chipW + (stageCount - 1) * gap;
        int cxStart = cx - total / 2;
        int chipY = y - 48;
        for (int i = 0; i < stageCount; i++) {
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
            g.centeredText(Minecraft.getInstance().font, allLabels[i],
                    cx0 + chipW / 2, chipY + 3, fg);
        }
    }

    /**
     * Extra scare-factor decoration layered over the bar on VERY_HARD — animated electric arcs
     * jittering along the top/bottom of the rim + subtle diagonal "chain" pattern on the bezel.
     */
    private static void drawVeryHardOrnaments(GuiGraphicsExtractor g, int x, int y, int highlight, int glow) {
        long now = System.currentTimeMillis();
        // Spikes along the top edge
        for (int i = 0; i < BAR_WIDTH; i += 6) {
            int h = ((int) ((now / 50 + i * 7) % 4)) + 2;
            int sx = x + i;
            g.fill(sx, y - 2 - h, sx + 2, y - 2, 0xC0000000);
            g.fill(sx + 1, y - 2 - h + 1, sx + 2, y - 2, highlight);
        }
        // Spikes along the bottom edge
        for (int i = 0; i < BAR_WIDTH; i += 6) {
            int h = ((int) ((now / 50 + i * 11 + 123) % 4)) + 2;
            int sx = x + i + 3;
            g.fill(sx, y + BAR_HEIGHT + 2, sx + 2, y + BAR_HEIGHT + 2 + h, 0xC0000000);
            g.fill(sx, y + BAR_HEIGHT + 2, sx + 1, y + BAR_HEIGHT + 2 + h - 1, highlight);
        }
        // Fast electric arc travelling through the bar
        int arcX = x + 2 + (int) ((now / 6) % (BAR_WIDTH - 4));
        for (int i = 0; i < 6; i++) {
            int ax = arcX + i;
            if (ax < x + 2 || ax >= x + BAR_WIDTH - 2) continue;
            int ay = y + 3 + ((int) (now / 20 + i * 3) % (BAR_HEIGHT - 6));
            g.fill(ax, ay, ax + 1, ay + 1, glow);
        }
    }

    /**
     * EXTREME on top of the VH ornament: crown-like gold spikes above the bar,
     * blood drip-lines below, embers floating around.
     */
    private static void drawExtremeOrnaments(GuiGraphicsExtractor g, int x, int y, int highlight, int glow) {
        long now = System.currentTimeMillis();
        // Crown of gold spikes (5 big, 4 small) above centred on top edge.
        int cx = x + BAR_WIDTH / 2;
        int[] spikeH = {4, 6, 8, 10, 12, 10, 8, 6, 4};
        for (int i = 0; i < spikeH.length; i++) {
            int sx = cx - (spikeH.length * 6) / 2 + i * 6;
            int h = spikeH[i] + (int) ((now / 80 + i * 17) % 3);
            // drop shadow
            g.fill(sx, y - 6 - h - 1, sx + 4, y - 6, 0xB0000000);
            // gold filling
            g.fill(sx, y - 6 - h, sx + 4, y - 6, 0xFFFFC040);
            g.fill(sx + 1, y - 6 - h + 1, sx + 3, y - 7, 0xFFFFF080);
        }
        // Blood drip lines below bar
        for (int i = 0; i < 12; i++) {
            int dripX = x + 20 + i * 26;
            int dripLen = 3 + (int) ((now / 100 + i * 47) % 4);
            g.fill(dripX, y + BAR_HEIGHT + 6, dripX + 1, y + BAR_HEIGHT + 6 + dripLen, 0xC0B00010);
            g.fill(dripX, y + BAR_HEIGHT + 6 + dripLen, dripX + 1, y + BAR_HEIGHT + 7 + dripLen, 0xFFFF4040);
        }
        // Floating embers (random points above bar)
        for (int i = 0; i < 10; i++) {
            double t = (now / 40.0 + i * 37) % 100;
            int ex = x + (int) ((i * 33 + t * 3) % BAR_WIDTH);
            int ey = y - 12 - (int) ((t / 100) * 18);
            int alpha = 0x80 + (int) (Math.sin(t * 0.5) * 0x40);
            int col = (Math.max(0, Math.min(0xFF, alpha)) << 24) | 0xFFD050;
            g.fill(ex, ey, ex + 1, ey + 1, col);
        }
    }

    /**
     * IMPOSSIBLE ornaments — cosmic void backdrop: twin rotating particle orbits around the bar,
     * jagged magenta glyphs at either end, and a "heartbeat" red pulse below the bar that
     * speeds up as HP drops. Designed to feel oppressive and unworldly.
     */
    private static void drawImpossibleOrnaments(GuiGraphicsExtractor g, int x, int y, int highlight, int glow) {
        long now = System.currentTimeMillis();
        int cx = x + BAR_WIDTH / 2;
        int cy = y + BAR_HEIGHT / 2;
        // --- Cosmic orbit: two rings of moving stars around the bar.
        for (int ring = 0; ring < 2; ring++) {
            double radX = BAR_WIDTH / 2.0 + 18 + ring * 8;
            double radY = BAR_HEIGHT / 2.0 + 14 + ring * 8;
            double speed = ring == 0 ? 0.0010 : -0.0006;
            int count = 14;
            for (int i = 0; i < count; i++) {
                double a = now * speed + i * (Math.PI * 2.0 / count);
                int px = cx + (int) (Math.cos(a) * radX);
                int py = cy + (int) (Math.sin(a) * radY);
                int alpha = 0x90 + (int) (Math.sin(now * 0.004 + i) * 0x40);
                int col = (Math.max(0, Math.min(0xFF, alpha)) << 24) | (glow & 0x00FFFFFF);
                g.fill(px, py, px + 2, py + 2, col);
                g.fill(px - 1, py, px + 3, py + 1, (col & 0x00FFFFFF) | 0x40000000);
            }
        }
        // --- Jagged magenta glyphs flanking the bar ends (void signature).
        int[] glyphX = { -18, BAR_WIDTH + 16 };
        for (int side = 0; side < 2; side++) {
            int gx = x + glyphX[side];
            int shake = ((int) (now / 180 + side * 19)) % 3 - 1;
            for (int row = 0; row < 14; row++) {
                int bits = (row * 31 + side * 17 + (int) (now / 260)) & 7;
                for (int col = 0; col < 3; col++) {
                    if ((bits & (1 << col)) != 0) {
                        int px = gx + col + shake;
                        int py = y + 2 + row;
                        g.fill(px, py, px + 1, py + 1, 0xFFFF30FF);
                    }
                }
            }
        }
        // --- Heartbeat under the bar: faster as HP approaches 0.
        float frac = Math.max(0f, Math.min(1f, displayedFraction));
        long beatPeriod = Math.max(200L, (long) (300 + frac * 700));
        long phase = now % beatPeriod;
        float pulse = phase < beatPeriod / 4 ? 1.0f - phase / (float) (beatPeriod / 4) : 0f;
        int beatAlpha = (int) (0xA0 * pulse);
        if (beatAlpha > 0) {
            int colour = (beatAlpha << 24) | 0x00FF2040;
            // Pulse line
            g.fill(x + 20, y + BAR_HEIGHT + 8, x + BAR_WIDTH - 20, y + BAR_HEIGHT + 10, colour);
            // Small crown above bar.
            g.fill(cx - 2, y - 8, cx + 2, y - 6, colour);
        }
        // --- Cosmic aura shadow just beyond the frame (violet halo).
        for (int k = 8; k >= 4; k--) {
            int a = 0x0A + (8 - k) * 0x08;
            int c = (a << 24) | 0x008000FF;
            g.fill(x - k, y - k, x + BAR_WIDTH + k, y + BAR_HEIGHT + k, c);
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

    public static void onDifficultyInfo(NetworkPayloads.DifficultyInfoPayload p) {
        difficultyId = p.difficultyId();
    }

    public static void clear() {
        visible = false;
        health = 0f;
        maxHealth = 0f;
        displayedFraction = 0f;
        stage = 0;
        difficultyId = 0;
    }
}
