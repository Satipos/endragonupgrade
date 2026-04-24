package com.endragonupgrade.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * v3.5 custom in-game lobby — paints a cosmic End-themed backdrop and a stylized
 * "END DRAGON UPGRADE" logo OVER the vanilla {@link TitleScreen} before its buttons
 * render. We don't replace the screen class (no mixins) — instead we hook Fabric's
 * {@code afterBackground(screen)} per-screen event so vanilla buttons stay fully
 * functional, and we layer our artwork between the vanilla background and the widgets.
 *
 * <p>Animated elements:
 * <ul>
 *     <li>200+ drifting portal motes in 3 hues</li>
 *     <li>Soft violet vignette + radial "aurora" glow behind the logo</li>
 *     <li>Rotating concentric dragon-sigil ring</li>
 *     <li>Stylised block-letter logo with animated shimmer strip</li>
 *     <li>Subtitle with pulsing runes</li>
 *     <li>Footer "v3.5" badge</li>
 * </ul>
 */
public final class CustomLobby {
    private CustomLobby() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof TitleScreen)) return;
            ScreenEvents.afterBackground(screen).register(CustomLobby::drawLobby);
        });
    }

    private static void drawLobby(Screen screen, GuiGraphicsExtractor g,
                                  int mouseX, int mouseY, float partialTick) {
        int W = screen.width;
        int H = screen.height;
        long now = System.currentTimeMillis();

        // 1. Dark void wash over the vanilla background (soft — keep some of it visible).
        g.fill(0, 0, W, H, 0xC0000010);

        // 2. Radial aurora — violet glow blooming from the screen centre.
        int cx = W / 2;
        int cy = H / 2 - 30;
        for (int k = 0; k < 12; k++) {
            int r = 360 - k * 28;
            if (r <= 0) break;
            int alpha = 0x06 + k * 0x06;
            int col = (alpha << 24) | 0x00402080;
            g.fill(cx - r, cy - r, cx + r, cy + r, col);
        }
        for (int k = 0; k < 8; k++) {
            int r = 220 - k * 22;
            if (r <= 0) break;
            int alpha = 0x08 + k * 0x0A;
            int col = (alpha << 24) | 0x00FF30FF;
            g.fill(cx - r, cy - r, cx + r, cy + r, col);
        }

        // 3. Drifting portal motes (3 hues).
        for (int i = 0; i < 180; i++) {
            double t = (now / 30.0 + i * 37) % 1600;
            int mx = (int) ((i * 89 + t * 1.3) % W);
            int my = (int) ((i * 67 + t * 0.8) % H);
            int col;
            int hue = i % 3;
            if (hue == 0) col = 0xFFB24CFF;
            else if (hue == 1) col = 0xFFFFC050;
            else col = 0xFF4080FF;
            int alpha = 0x30 + (int) (Math.sin(t * 0.2) * 0x50);
            alpha = Math.max(0, Math.min(0xFF, alpha));
            int c = (alpha << 24) | (col & 0x00FFFFFF);
            g.fill(mx, my, mx + 1, my + 1, c);
        }

        // 4. Rotating dragon sigil behind the logo.
        drawSigil(g, cx, cy - 70, 50, now);

        // 5. Stylised logo band.
        drawLogoBand(g, screen, cx, cy - 20, now);

        // 6. Subtitle beneath logo with pulsing runes.
        boolean runeOn = (now / 300) % 2 == 0;
        String rune = runeOn ? "§5§l§k###" : "§d§l§k###";
        String subtitle = rune + "§r  §d§oEnd Dragon Upgrade  §8v3.5  " + rune;
        g.centeredText(net.minecraft.client.Minecraft.getInstance().font,
                subtitle, cx, cy + 6, 0xFFE0A0FF);

        // 7. Footer badges — left and right corners.
        g.fill(6, H - 18, 140, H - 4, 0xB0000010);
        g.fill(6, H - 18, 140, H - 17, 0xFFB24CFF);
        g.text(net.minecraft.client.Minecraft.getInstance().font,
                "§d§lEND DRAGON UPGRADE §8v3.5", 10, H - 14, 0xFFE0A0FF);
        g.fill(W - 140, H - 18, W - 6, H - 4, 0xB0000010);
        g.fill(W - 140, H - 18, W - 6, H - 17, 0xFFFFD040);
        g.text(net.minecraft.client.Minecraft.getInstance().font,
                "§6⚡ §fАктивация в бою: §6chat §f⚡", W - 136, H - 14, 0xFFFFE0A0);
    }

    /** 3 concentric rotating rings + central gem, drawn with fills. */
    private static void drawSigil(GuiGraphicsExtractor g, int cx, int cy, int r, long now) {
        for (int ring = 0; ring < 3; ring++) {
            double speed = (ring % 2 == 0 ? 1 : -1) * (0.0008 + ring * 0.0004);
            int radius = r - ring * 10;
            int segments = 36;
            for (int i = 0; i < segments; i++) {
                double a = now * speed + i * (Math.PI * 2.0 / segments);
                int px = cx + (int) (Math.cos(a) * radius);
                int py = cy + (int) (Math.sin(a) * radius);
                int alpha = 0x50 + (int) (Math.sin(now * 0.003 + i + ring) * 0x50);
                alpha = Math.max(0, Math.min(0xFF, alpha));
                int col;
                if (ring == 0) col = 0x00FF40FF;
                else if (ring == 1) col = 0x00FFC040;
                else col = 0x004080FF;
                g.fill(px, py, px + 2, py + 2, (alpha << 24) | col);
            }
        }
        // Central pulsing gem.
        double pulse = 1.0 + Math.sin(now * 0.006) * 0.25;
        int gr = (int) (8 * pulse);
        g.fill(cx - gr, cy - gr, cx + gr, cy + gr, 0xFF000000);
        g.fill(cx - gr + 1, cy - gr + 1, cx + gr - 1, cy + gr - 1, 0xFFFF30FF);
        // Highlight
        g.fill(cx - gr + 2, cy - gr + 2, cx - gr + 4, cy - gr + 4, 0xFFFFFFFF);
        // Star flares
        int flare = (int) (16 * pulse);
        g.fill(cx - flare, cy, cx + flare, cy + 1, 0x80FFFFFF);
        g.fill(cx, cy - flare, cx + 1, cy + flare, 0x80FFFFFF);
    }

    /** Logo band: rim + stylised title text with animated shimmer. */
    private static void drawLogoBand(GuiGraphicsExtractor g, Screen screen, int cx, int cy, long now) {
        int bandW = 360;
        int bandH = 34;
        int bx = cx - bandW / 2;
        int by = cy - bandH / 2;
        // Halo
        for (int k = 10; k >= 2; k--) {
            int alpha = 0x05 + (10 - k) * 0x08;
            int col = (alpha << 24) | 0x00FF30FF;
            g.fill(bx - k, by - k, bx + bandW + k, by + bandH + k, col);
        }
        // Bezel
        g.fill(bx - 2, by - 2, bx + bandW + 2, by + bandH + 2, 0xFF000000);
        g.fill(bx - 1, by - 1, bx + bandW + 1, by + bandH + 1, 0xFFFF30FF);
        // Inner dark fill
        g.fill(bx, by, bx + bandW, by + bandH, 0xFF12001A);
        // Shimmer travelling through the band
        float t = ((now / 10L) % (bandW + 60)) - 30;
        int sheenX = bx + (int) t;
        for (int dx = -16; dx <= 16; dx++) {
            int sx = sheenX + dx;
            if (sx < bx || sx >= bx + bandW) continue;
            int alpha = (int) (0x90 * (1.0 - Math.abs(dx) / 16.0));
            if (alpha <= 0) continue;
            int col = (alpha << 24) | 0x00FFFFFF;
            g.fill(sx, by, sx + 1, by + bandH, col);
        }
        // Title text with big style.
        String title = "§l§d⚡§r  §d§lE N D   D R A G O N  §r  §l§d⚡";
        g.centeredText(net.minecraft.client.Minecraft.getInstance().font,
                title, cx, by + 4, 0xFFFFFFFF);
        // Secondary: "UPGRADE"
        String sub = "§6§l§nU P G R A D E";
        g.centeredText(net.minecraft.client.Minecraft.getInstance().font,
                sub, cx, by + 18, 0xFFFFD080);
    }
}
