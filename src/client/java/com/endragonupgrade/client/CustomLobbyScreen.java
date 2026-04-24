package com.endragonupgrade.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.Component;

/**
 * v3.5.1 — fully custom in-game lobby (replaces the vanilla {@link net.minecraft.client.gui.screens.TitleScreen}).
 *
 * <p>Cinematic End-themed lobby with:
 * <ul>
 *     <li>Layered sky gradient (deep void → violet → cosmic pink) with soft aurora halo.</li>
 *     <li>3-layer parallax starfield (different speeds / sizes / hues).</li>
 *     <li>Big silhouette of a soaring Ender Dragon drawn with pixel fills in the background.</li>
 *     <li>Slowly rotating mandala sigil behind the logo plate.</li>
 *     <li>Stylised multi-line logo with gradient + shimmer + drop shadow.</li>
 *     <li>Four custom {@link LobbyButton}s (Singleplayer / Multiplayer / Options / Quit) with
 *     animated gradient rim on hover and a chevron-caret indicator.</li>
 *     <li>Footer with version badge and a hint about the activation phrases.</li>
 * </ul>
 *
 * <p>Registered via {@link CustomLobby#register()} — swaps in whenever vanilla TitleScreen opens.
 * No mixins required.
 */
public class CustomLobbyScreen extends Screen {
    // Draw constants.
    private static final int BTN_W = 240;
    private static final int BTN_H = 32;
    private static final int BTN_GAP = 8;

    private final long createdAt = System.currentTimeMillis();
    // Cached star seeds so the field doesn't resample every frame.
    private int[][] stars;

    public CustomLobbyScreen() {
        super(Component.literal("End Dragon Upgrade"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int buttonsY = this.height / 2 + 20;

        // Generate star field once per resize/init.
        stars = new int[260][3]; // x, y, hue(0..2)
        java.util.Random r = new java.util.Random(424242L);
        for (int i = 0; i < stars.length; i++) {
            stars[i][0] = r.nextInt(Math.max(1, this.width));
            stars[i][1] = r.nextInt(Math.max(1, this.height));
            stars[i][2] = r.nextInt(3);
        }

        // Singleplayer
        this.addRenderableWidget(new LobbyButton(cx - BTN_W / 2, buttonsY,
                BTN_W, BTN_H,
                Component.translatable("menu.singleplayer"),
                0xFFB24CFF, 0xFF5B0F8A,
                b -> this.minecraft.setScreen(new SelectWorldScreen(this))));

        // Multiplayer
        this.addRenderableWidget(new LobbyButton(cx - BTN_W / 2, buttonsY + (BTN_H + BTN_GAP),
                BTN_W, BTN_H,
                Component.translatable("menu.multiplayer"),
                0xFFFF30FF, 0xFF6A1099,
                b -> this.minecraft.setScreen(new JoinMultiplayerScreen(this))));

        // Options
        this.addRenderableWidget(new LobbyButton(cx - BTN_W / 2, buttonsY + 2 * (BTN_H + BTN_GAP),
                BTN_W, BTN_H,
                Component.translatable("menu.options"),
                0xFFFFC050, 0xFF6A3000,
                b -> this.minecraft.setScreen(new OptionsScreen(this, this.minecraft.options, true))));

        // Quit
        this.addRenderableWidget(new LobbyButton(cx - BTN_W / 2, buttonsY + 3 * (BTN_H + BTN_GAP),
                BTN_W, BTN_H,
                Component.translatable("menu.quitGame"),
                0xFFFF3030, 0xFF6A0010,
                b -> this.minecraft.stop()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawForeground(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawFooter(g);
    }

    private void drawBackground(GuiGraphicsExtractor g) {
        int W = this.width;
        int H = this.height;
        long now = System.currentTimeMillis() - createdAt;

        // --- 1. Vertical sky gradient (black → deep purple → void indigo → pink-violet).
        int stripes = 16;
        for (int i = 0; i < stripes; i++) {
            float t = i / (float) stripes;
            int r = lerp(0x08, 0x30, t);
            int g1 = lerp(0x00, 0x08, t);
            int b = lerp(0x18, 0x58, t);
            int col = 0xFF000000 | (r << 16) | (g1 << 8) | b;
            int y0 = H * i / stripes;
            int y1 = H * (i + 1) / stripes;
            g.fill(0, y0, W, y1, col);
        }

        // Dark floor gradient near the bottom (end stone-ish purple dust).
        for (int i = 0; i < 40; i++) {
            int alpha = 0x06 + i * 0x03;
            int col = (Math.min(0xFF, alpha) << 24) | 0x00200040;
            g.fill(0, H - 40 + i, W, H - 40 + i + 1, col);
        }

        // --- 2. Aurora radial glow behind the logo plate.
        int cx = W / 2;
        int cy = H / 2 - 60;
        for (int k = 0; k < 16; k++) {
            int r = 420 - k * 24;
            if (r <= 0) break;
            int alpha = 0x04 + k * 0x05;
            int col = (Math.min(0xFF, alpha) << 24) | 0x00602080;
            g.fill(cx - r, cy - r, cx + r, cy + r, col);
        }
        for (int k = 0; k < 10; k++) {
            int r = 200 - k * 16;
            if (r <= 0) break;
            int alpha = 0x05 + k * 0x0A;
            int col = (Math.min(0xFF, alpha) << 24) | 0x00FF30FF;
            g.fill(cx - r, cy - r, cx + r, cy + r, col);
        }

        // --- 3. Three parallax star layers (different speeds, sizes, hues).
        if (stars == null) return;
        for (int i = 0; i < stars.length; i++) {
            int baseX = stars[i][0];
            int baseY = stars[i][1];
            int hue = stars[i][2];
            // drift
            double speed = hue == 0 ? 0.008 : hue == 1 ? 0.015 : 0.025;
            int dx = (int) ((now * speed) % W);
            int x = (baseX + dx) % W;
            if (x < 0) x += W;
            int y = baseY;
            // twinkle
            int twinkle = (int) (Math.sin((now + i * 73) * 0.004) * 0x50);
            int alpha = hue == 0 ? 0x60 + twinkle : hue == 1 ? 0x90 + twinkle : 0xC0 + twinkle;
            alpha = Math.max(0x10, Math.min(0xFF, alpha));
            int col;
            if (hue == 0) col = 0x004080FF;
            else if (hue == 1) col = 0x00B24CFF;
            else col = 0x00FFFFFF;
            int c = (alpha << 24) | col;
            int sz = hue == 2 ? 2 : 1;
            g.fill(x, y, x + sz, y + sz, c);
            if (hue == 2) {
                // cross-highlight for brightest stars
                g.fill(x - 1, y, x + sz + 1, y + sz, (alpha / 3 << 24) | col);
                g.fill(x, y - 1, x + sz, y + sz + 1, (alpha / 3 << 24) | col);
            }
        }

        // --- 4. Big dragon silhouette near the horizon (pixel fills).
        drawDragonSilhouette(g, cx, cy + 20, now);
    }

    private void drawForeground(GuiGraphicsExtractor g) {
        long now = System.currentTimeMillis() - createdAt;
        int W = this.width;
        int H = this.height;
        int cx = W / 2;

        // Rotating mandala sigil behind logo plate.
        drawMandala(g, cx, H / 2 - 100, 70, now);

        // Logo plate.
        drawLogoPlate(g, cx, H / 2 - 50, now);

        // Drifting portal motes on foreground.
        for (int i = 0; i < 45; i++) {
            double t = (now / 20.0 + i * 31) % 2000;
            int mx = (int) ((i * 89 + t * 1.1) % W);
            int my = (int) ((i * 57 + t * 0.7) % H);
            int col = (i % 2 == 0) ? 0xFFFF30FF : 0xFFB24CFF;
            int alpha = 0x40 + (int) (Math.sin(t * 0.2) * 0x40);
            alpha = Math.max(0, Math.min(0xFF, alpha));
            int c = (alpha << 24) | (col & 0x00FFFFFF);
            g.fill(mx, my, mx + 1, my + 1, c);
        }
    }

    private void drawFooter(GuiGraphicsExtractor g) {
        Font font = Minecraft.getInstance().font;
        int W = this.width;
        int H = this.height;
        long now = System.currentTimeMillis() - createdAt;

        // Bottom-left version badge.
        g.fill(6, H - 22, 150, H - 6, 0xE0000018);
        g.fill(6, H - 22, 150, H - 21, 0xFFFF30FF);
        g.fill(6, H - 7, 150, H - 6, 0xFFB24CFF);
        g.text(font, "§d§lEND DRAGON UPGRADE §8v3.5.1", 10, H - 18, 0xFFE0A0FF);

        // Bottom-right chat hint.
        String hint = "§d⚡ §fЧат: §6Endo espo dragonio di quoro. §f/ §6Endo worldio expansia §d⚡";
        int tw = font.width(hint);
        int hx = W - tw - 12;
        g.fill(hx - 4, H - 22, W - 6, H - 6, 0xE0000018);
        g.fill(hx - 4, H - 22, W - 6, H - 21, 0xFFFFC050);
        g.fill(hx - 4, H - 7, W - 6, H - 6, 0xFF6A3000);
        g.text(font, hint, hx, H - 18, 0xFFFFE0A0);

        // Top-right: Minecraft version info (recreate vanilla helpfulness).
        String mcv = "§7Minecraft " + Minecraft.getInstance().getLaunchedVersion();
        int mw = font.width(mcv);
        g.text(font, mcv, W - mw - 6, 6, 0xFFCFCFCF);

        // Copyright line dead-centred bottom.
        String copy = "§8© Mojang / Satipos — §dEnd Dragon Upgrade§8 is a fan mod.";
        g.centeredText(font, copy, W / 2, H - 34, 0xFF808080);

        @SuppressWarnings("unused") long _n = now; // keep parity with other draws
    }

    private void drawMandala(GuiGraphicsExtractor g, int cx, int cy, int r, long now) {
        int rings = 4;
        for (int ring = 0; ring < rings; ring++) {
            double speed = (ring % 2 == 0 ? 1 : -1) * (0.0006 + ring * 0.0003);
            int radius = r - ring * 14;
            if (radius <= 4) continue;
            int segments = 48;
            for (int i = 0; i < segments; i++) {
                double a = now * speed + i * (Math.PI * 2.0 / segments);
                int px = cx + (int) (Math.cos(a) * radius);
                int py = cy + (int) (Math.sin(a) * radius);
                int alpha = 0x40 + (int) (Math.sin(now * 0.003 + i + ring) * 0x40);
                alpha = Math.max(0x10, Math.min(0xFF, alpha));
                int col;
                if (ring == 0) col = 0x00FFC050;
                else if (ring == 1) col = 0x00FF30FF;
                else if (ring == 2) col = 0x00B24CFF;
                else col = 0x004080FF;
                g.fill(px, py, px + 2, py + 2, (alpha << 24) | col);
            }
        }
        // Central gem
        double pulse = 1.0 + Math.sin(now * 0.005) * 0.2;
        int gr = (int) (10 * pulse);
        for (int k = 4; k >= 1; k--) {
            int a = 0x08 + (4 - k) * 0x10;
            int col = (a << 24) | 0x00FF30FF;
            g.fill(cx - gr - k, cy - gr - k, cx + gr + k, cy + gr + k, col);
        }
        g.fill(cx - gr, cy - gr, cx + gr, cy + gr, 0xFF000000);
        g.fill(cx - gr + 1, cy - gr + 1, cx + gr - 1, cy + gr - 1, 0xFFFF30FF);
        g.fill(cx - gr + 2, cy - gr + 2, cx - gr + 4, cy - gr + 4, 0xFFFFFFFF);
        // Star flares
        int flare = (int) (22 * pulse);
        g.fill(cx - flare, cy, cx + flare, cy + 1, 0x90FFFFFF);
        g.fill(cx, cy - flare, cx + 1, cy + flare, 0x90FFFFFF);
    }

    private void drawLogoPlate(GuiGraphicsExtractor g, int cx, int cy, long now) {
        Font font = Minecraft.getInstance().font;
        int plateW = 440;
        int plateH = 70;
        int x = cx - plateW / 2;
        int y = cy - plateH / 2;

        // Glow halo
        for (int k = 14; k >= 2; k--) {
            int alpha = 0x04 + (14 - k) * 0x07;
            int col = (Math.min(0xFF, alpha) << 24) | 0x00FF30FF;
            g.fill(x - k, y - k, x + plateW + k, y + plateH + k, col);
        }
        // Outer bezel
        g.fill(x - 3, y - 3, x + plateW + 3, y + plateH + 3, 0xFF000000);
        // Gem rim (pink)
        g.fill(x - 2, y - 2, x + plateW + 2, y + plateH + 2, 0xFFFF30FF);
        // Thin dark line
        g.fill(x - 1, y - 1, x + plateW + 1, y + plateH + 1, 0xFF000000);
        // Inner dark fill (very dark with slight gradient)
        for (int row = 0; row < plateH; row++) {
            float t = row / (float) plateH;
            int r = lerp(0x10, 0x20, t);
            int gg = lerp(0x00, 0x05, t);
            int b = lerp(0x20, 0x3A, t);
            int col = 0xFF000000 | (r << 16) | (gg << 8) | b;
            g.fill(x, y + row, x + plateW, y + row + 1, col);
        }
        // Corner accent caps
        int capW = 20;
        g.fill(x - 2, y - 2, x + capW, y + 4, 0xFFFFC050);
        g.fill(x + plateW - capW, y - 2, x + plateW + 2, y + 4, 0xFFFFC050);
        g.fill(x - 2, y + plateH - 4, x + capW, y + plateH + 2, 0xFFFFC050);
        g.fill(x + plateW - capW, y + plateH - 4, x + plateW + 2, y + plateH + 2, 0xFFFFC050);

        // Animated shimmer sweeping across
        float t = ((now / 8L) % (plateW + 80)) - 40;
        int sheenX = x + (int) t;
        for (int dx = -24; dx <= 24; dx++) {
            int sx = sheenX + dx;
            if (sx < x || sx >= x + plateW) continue;
            int alpha = (int) (0x70 * (1.0 - Math.abs(dx) / 24.0));
            if (alpha <= 0) continue;
            int col = (alpha << 24) | 0x00FFFFFF;
            g.fill(sx, y, sx + 1, y + plateH, col);
        }

        // Logo text — 3 layers (shadow, base, highlight) for real gradient feel.
        String title = "E N D   D R A G O N";
        String sub = "U P G R A D E";
        // Shadow
        g.centeredText(font, "§8§l" + title, cx + 2, y + 11, 0xFF000000);
        // Base (magenta)
        g.centeredText(font, "§d§l" + title, cx, y + 10, 0xFFFF60FF);
        // Highlight streak on top of big title (render again lighter, offset up 1px)
        g.centeredText(font, "§f§l" + title, cx, y + 9, 0x50FFFFFF);

        // Subtitle "UPGRADE"
        g.centeredText(font, "§8§l§n" + sub, cx + 1, y + 35, 0xFF000000);
        g.centeredText(font, "§6§l§n" + sub, cx, y + 34, 0xFFFFD080);

        // Pulsing rune line below
        boolean pulseOn = (now / 300) % 2 == 0;
        String rune = pulseOn ? "§5§l§k##" : "§d§l§k##";
        String runeLine = rune + "§r  §d§oРастущий страж конца  " + rune;
        g.centeredText(font, runeLine, cx, y + 54, 0xFFE0A0FF);
    }

    private void drawDragonSilhouette(GuiGraphicsExtractor g, int cx, int baseY, long now) {
        // A simple stylised flying dragon silhouette drawn with fills.
        // Wingspan ~240px, "hovering" bob based on time.
        int bob = (int) (Math.sin(now / 900.0) * 3);
        int x = cx;
        int y = baseY + 100 + bob;
        int dark = 0xB0000010;
        int edge = 0x70201050;

        // Body (thicker centre cluster)
        g.fill(x - 12, y - 8, x + 12, y + 8, dark);
        g.fill(x - 10, y - 10, x + 10, y + 10, dark);
        // Head
        g.fill(x + 10, y - 4, x + 30, y + 4, dark);
        g.fill(x + 28, y - 6, x + 38, y + 2, dark);
        // Snout
        g.fill(x + 36, y - 3, x + 44, y + 1, dark);
        // Horns
        g.fill(x + 26, y - 10, x + 30, y - 6, dark);
        g.fill(x + 22, y - 12, x + 26, y - 8, dark);
        // Tail (tapered, curving down-left)
        for (int i = 0; i < 26; i++) {
            int tx = x - 12 - i * 3;
            int ty = y + (int) (Math.sin(i * 0.2) * 3) + (i / 4);
            int h = Math.max(1, 8 - i / 4);
            g.fill(tx, ty, tx + 4, ty + h, dark);
        }
        // Tail tip fork
        g.fill(x - 92, y + 10, x - 88, y + 14, dark);
        g.fill(x - 92, y + 4, x - 88, y + 8, dark);

        // Wings — large triangular shapes from body outward.
        // Flap phase
        double flap = Math.sin(now / 800.0) * 0.6;
        drawWing(g, x, y, 1, flap, dark, edge);
        drawWing(g, x, y, -1, flap, dark, edge);

        // Faint aura/outline around the whole dragon.
        for (int k = 3; k >= 1; k--) {
            int alpha = 0x08 * k;
            int col = (alpha << 24) | 0x004080FF;
            g.fill(x - 100, y - 40, x + 50, y + 40, col);
        }
    }

    private void drawWing(GuiGraphicsExtractor g, int cx, int cy, int dir, double flap, int dark, int edge) {
        // Each wing is 7 staggered rectangles forming a triangle.
        int baseX = cx + dir * 8;
        int baseY = cy;
        int segments = 9;
        for (int i = 0; i < segments; i++) {
            double spread = (i / (double) segments);
            int wx = baseX + dir * (int) (spread * 120);
            int wy = baseY - (int) (Math.sin((1 - spread) * Math.PI / 2) * 60 - flap * spread * 20);
            int h = 4 + (int) ((1 - spread) * 18);
            g.fill(wx - 6, wy, wx + 6, wy + h, dark);
        }
        // Trailing edge feathers
        for (int i = 0; i < 5; i++) {
            int wx = baseX + dir * (30 + i * 18);
            int wy = baseY + (int) (i * 6 - flap * 4);
            g.fill(wx - 4, wy, wx + 4, wy + 3, edge);
        }
    }

    private static int lerp(int a, int b, float t) {
        return (int) (a + (b - a) * t);
    }

    /** Custom Lobby button with gradient rim, hover glow, and chevron caret. */
    private static class LobbyButton extends AbstractWidget {
        private final int accent;
        private final int shadow;
        private final java.util.function.Consumer<LobbyButton> onClickAction;
        private final long createdAt = System.currentTimeMillis();

        LobbyButton(int x, int y, int w, int h, Component label,
                    int accent, int shadow,
                    java.util.function.Consumer<LobbyButton> onClickAction) {
            super(x, y, w, h, label);
            this.accent = accent;
            this.shadow = shadow;
            this.onClickAction = onClickAction;
        }

        @Override
        public void onClick(net.minecraft.client.input.MouseButtonEvent e, boolean dbl) {
            onClickAction.accept(this);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            boolean hovered = this.isHovered() || this.isFocused();
            long now = System.currentTimeMillis() - createdAt;

            // Glow halo (stronger on hover)
            int halo = hovered ? 8 : 3;
            for (int k = halo; k >= 2; k--) {
                int alpha = 0x06 + (halo - k) * 0x10;
                int col = (Math.min(0xFF, alpha) << 24) | (accent & 0x00FFFFFF);
                g.fill(x - k, y - k, x + w + k, y + h + k, col);
            }

            // Outer black border
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
            // Gradient rim — accent on top fading to shadow on bottom
            for (int row = 0; row < h + 2; row++) {
                float t = row / (float) (h + 2);
                int rr = lerp((accent >> 16) & 0xFF, (shadow >> 16) & 0xFF, t);
                int gg = lerp((accent >> 8) & 0xFF, (shadow >> 8) & 0xFF, t);
                int bb = lerp(accent & 0xFF, shadow & 0xFF, t);
                int col = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
                g.fill(x - 1, y - 1 + row, x + w + 1, y - 1 + row + 1, col);
            }
            // Inner fill (subtle gradient black → deep tone)
            for (int row = 0; row < h; row++) {
                float t = row / (float) h;
                int rr = lerp(0x0C, 0x1A, t);
                int gg = lerp(0x00, 0x06, t);
                int bb = lerp(0x1A, 0x30, t);
                int col = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
                g.fill(x, y + row, x + w, y + row + 1, col);
            }

            // Hover shimmer travelling across
            if (hovered) {
                float t = ((now / 6L) % (w + 60)) - 30;
                int sheenX = x + (int) t;
                for (int dx = -16; dx <= 16; dx++) {
                    int sx = sheenX + dx;
                    if (sx < x || sx >= x + w) continue;
                    int alpha = (int) (0x70 * (1.0 - Math.abs(dx) / 16.0));
                    if (alpha <= 0) continue;
                    int col = (alpha << 24) | 0x00FFFFFF;
                    g.fill(sx, y, sx + 1, y + h, col);
                }
            }

            // Left accent bar
            g.fill(x, y, x + 4, y + h, accent);
            // Right chevron caret (>) drawn with fills
            int cx = x + w - 14;
            int cy = y + h / 2;
            int off = hovered ? (int) ((now / 100) % 4) : 0;
            for (int i = 0; i < 5; i++) {
                g.fill(cx + i + off, cy - 4 + i, cx + i + off + 2, cy - 4 + i + 2, accent);
                g.fill(cx + i + off, cy + 4 - i, cx + i + off + 2, cy + 4 - i + 2, accent);
            }

            // Label — centred, with drop shadow.
            Font font = Minecraft.getInstance().font;
            Component label = this.getMessage();
            int textY = y + (h - 8) / 2;
            int textX = x + 20;
            // shadow
            g.text(font, label, textX + 1, textY + 1, 0xFF000000);
            // main
            int textCol = hovered ? 0xFFFFFFFF : 0xFFE0E0FF;
            g.text(font, label, textX, textY, textCol);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationOutput) {
        }
    }

}
