package com.endragonupgrade.client;

import com.endragonupgrade.NetworkPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom difficulty-selection screen. Rendered entirely with fills (no textures) — shows a dark
 * cosmic backdrop with scrolling portal motes, plus three tall "difficulty cards" that react
 * visually to hover. Each card: themed colour, title, value stats, description, stage chips.
 */
public class DifficultySelectScreen extends Screen {
    private static final int PANEL_W = 620;
    private static final int PANEL_H = 340;

    private static final int CARD_W = 136;
    private static final int CARD_H = 230;
    private static final int CARD_GAP = 14;

    public DifficultySelectScreen() {
        super(Component.translatable("endragonupgrade.menu.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelTop = cy - PANEL_H / 2;
        int panelLeft = cx - PANEL_W / 2;
        int cardY = panelTop + 60;
        int cardsTotal = 4 * CARD_W + 3 * CARD_GAP;
        int cardsLeft = cx - cardsTotal / 2;

        this.addRenderableWidget(new DifficultyCard(cardsLeft, cardY,
                0,
                0xFFA825FF, 0xFF5B0F8A,
                Component.translatable("endragonupgrade.menu.hard").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                "600 HP  •  3 стадии",
                Component.translatable("endragonupgrade.menu.hard.desc")));

        this.addRenderableWidget(new DifficultyCard(cardsLeft + (CARD_W + CARD_GAP), cardY,
                1,
                0xFFFF2040, 0xFF800010,
                Component.translatable("endragonupgrade.menu.very_hard").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                "1200 HP  •  4 стадии",
                Component.translatable("endragonupgrade.menu.very_hard.desc")));

        this.addRenderableWidget(new DifficultyCard(cardsLeft + 2 * (CARD_W + CARD_GAP), cardY,
                2,
                0xFFFFB020, 0xFF6A3000,
                Component.translatable("endragonupgrade.menu.extreme").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
                "2000 HP  •  6 стадий",
                Component.translatable("endragonupgrade.menu.extreme.desc")));

        this.addRenderableWidget(new DifficultyCard(cardsLeft + 3 * (CARD_W + CARD_GAP), cardY,
                3,
                0xFFFF30FF, 0xFF300030,
                Component.translatable("endragonupgrade.menu.impossible").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD, ChatFormatting.UNDERLINE),
                "5000 HP  •  10 стадий",
                Component.translatable("endragonupgrade.menu.impossible.desc")));

        // Cancel button at the bottom.
        this.addRenderableWidget(Button.builder(
                        Component.translatable("endragonupgrade.menu.cancel"),
                        b -> this.onClose())
                .bounds(cx - 60, panelTop + PANEL_H - 30, 120, 20)
                .build());
    }

    public static void pick(int difficultyId, DifficultySelectScreen screen) {
        ClientPlayNetworking.send(new NetworkPayloads.SelectDifficultyPayload(difficultyId));
        screen.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        drawCosmicBackdrop(graphics);

        int cx = this.width / 2;
        int cy = this.height / 2;
        int panelTop = cy - PANEL_H / 2;
        int panelLeft = cx - PANEL_W / 2;

        // Outer panel frame with layered glow.
        for (int k = 8; k >= 2; k--) {
            int alpha = 0x08 + (8 - k) * 0x10;
            graphics.fill(panelLeft - k, panelTop - k,
                    panelLeft + PANEL_W + k, panelTop + PANEL_H + k,
                    (alpha << 24) | 0x6020D0);
        }
        graphics.fill(panelLeft - 2, panelTop - 2, panelLeft + PANEL_W + 2, panelTop + PANEL_H + 2, 0xFF000000);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_W + 1, panelTop + PANEL_H + 1, 0xFFB24CFF);
        // Panel interior — a subtle radial gradient from deep-violet to black.
        for (int r = 0; r < PANEL_H / 2; r++) {
            int alpha = 0xFF;
            int base = 0x14;
            int v = (int) (base + (1.0 - (double) r / (PANEL_H / 2.0)) * 0x20);
            int col = (alpha << 24) | (v << 16) | 0x001030;
            graphics.fill(panelLeft, panelTop + r, panelLeft + PANEL_W, panelTop + r + 1, col);
            graphics.fill(panelLeft, panelTop + PANEL_H - r - 1, panelLeft + PANEL_W, panelTop + PANEL_H - r, col);
        }

        // Title + subtitle
        graphics.centeredText(this.font,
                Component.translatable("endragonupgrade.menu.title")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                cx, panelTop + 14, 0xFFFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("endragonupgrade.menu.subtitle")
                        .withStyle(ChatFormatting.GRAY),
                cx, panelTop + 28, 0xFFCDA0FF);

        // Animated divider under the title.
        long now = System.currentTimeMillis();
        for (int i = 0; i < PANEL_W - 60; i++) {
            float t = (i + (now / 12f) % PANEL_W) / (float) PANEL_W;
            float a = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
            int col = (int) (0x60 + a * 0x9F) << 24 | 0x00B24CFF;
            graphics.fill(panelLeft + 30 + i, panelTop + 46, panelLeft + 30 + i + 1, panelTop + 48, col);
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    /** Deep-space background: black with slow-drifting violet+gold motes. */
    private void drawCosmicBackdrop(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, this.width, this.height, 0xFF000004);
        long now = System.currentTimeMillis();
        // Soft vignette in centre
        int cx = this.width / 2;
        int cy = this.height / 2;
        for (int k = 0; k < 8; k++) {
            int r = 220 - k * 25;
            int alpha = 0x05 + k * 0x04;
            int col = (alpha << 24) | 0x30105A;
            graphics.fill(cx - r, cy - r, cx + r, cy + r, col);
        }
        // Drifting portal motes
        for (int i = 0; i < 80; i++) {
            double t = (now / 25.0 + i * 29) % 1024;
            int mx = (int) ((i * 73 + t) % this.width);
            int my = (int) ((i * 53 + t * 0.6) % this.height);
            int hueChoice = i % 3;
            int col = switch (hueChoice) {
                case 0 -> 0xFFB24CFF;
                case 1 -> 0xFFFFC050;
                default -> 0xFF4080FF;
            };
            int alpha = 0x40 + (int) (Math.sin(t * 0.2) * 0x40);
            int c = ((Math.max(0, Math.min(0xFF, alpha))) << 24) | (col & 0x00FFFFFF);
            graphics.fill(mx, my, mx + 1, my + 1, c);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Custom widget: a tall card showing a difficulty tier. Renders its own layered background
     * and responds to hover with a brighter rim + scaled border.
     */
    public class DifficultyCard extends AbstractWidget {
        private final int difficultyId;
        private final int accent;
        private final int shadow;
        private final Component title;
        private final String stats;
        private final Component desc;

        public DifficultyCard(int x, int y, int difficultyId,
                              int accent, int shadow,
                              Component title, String stats, Component desc) {
            super(x, y, CARD_W, CARD_H, title);
            this.difficultyId = difficultyId;
            this.accent = accent;
            this.shadow = shadow;
            this.title = title;
            this.stats = stats;
            this.desc = desc;
        }

        @Override
        public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            pick(this.difficultyId, DifficultySelectScreen.this);
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int w = this.getWidth();
            int h = this.getHeight();
            boolean hovered = this.isHovered() || this.isFocused();

            // Glow halo (stronger when hovered)
            int haloLayers = hovered ? 10 : 5;
            for (int k = haloLayers; k >= 2; k--) {
                int alpha = 0x08 + (haloLayers - k) * 0x10;
                int col = (alpha << 24) | (accent & 0x00FFFFFF);
                g.fill(x - k, y - k, x + w + k, y + h + k, col);
            }

            // Card body — 3-layer: outer dark, coloured rim, dark fill.
            g.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF000000);
            int rim = hovered ? accent : shadow;
            g.fill(x - 1, y - 1, x + w + 1, y + h + 1, rim);
            g.fill(x, y, x + w, y + h, 0xFF120018);

            // Top accent stripe + animated shimmer
            g.fill(x, y, x + w, y + 4, accent);
            long now = System.currentTimeMillis();
            int shX = x + (int) ((now / 10) % (w + 40)) - 20;
            for (int dx = -8; dx <= 8; dx++) {
                int sx = shX + dx;
                if (sx < x || sx >= x + w) continue;
                int alpha = (int) (0x70 * (1.0 - Math.abs(dx) / 8.0));
                if (alpha <= 0) continue;
                int col = (alpha << 24) | 0x00FFFFFF;
                g.fill(sx, y, sx + 1, y + 4, col);
            }

            // Huge circular "dragon glyph" icon area — we draw stylised concentric rings.
            int iconCx = x + w / 2;
            int iconCy = y + 62;
            drawDragonGlyph(g, iconCx, iconCy, 32, accent, hovered);

            // Title
            g.centeredText(getFont(), title, x + w / 2, y + 100, 0xFFFFFFFF);

            // Stats
            g.centeredText(getFont(), stats, x + w / 2, y + 116, 0xFFCDA0FF);

            // Separator
            g.fill(x + 12, y + 132, x + w - 12, y + 133, (0x80 << 24) | (accent & 0x00FFFFFF));

            // Description — wrap in up to 6 lines.
            int lineY = y + 142;
            var lines = getFont().split(desc, w - 14);
            for (int i = 0; i < Math.min(5, lines.size()); i++) {
                g.centeredText(getFont(), lines.get(i), x + w / 2, lineY, 0xFFC8C8E0);
                lineY += 11;
            }

            // Stage chips at the bottom — count depends on difficulty tier.
            int chipCount = difficultyId == 0 ? 3 : difficultyId == 1 ? 4
                    : difficultyId == 2 ? 6 : 10;
            String[] romans = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X" };
            // For 10 chips, use two rows of 5 to keep chips readable.
            int chipRows = chipCount >= 10 ? 2 : 1;
            int perRow = chipCount / chipRows;
            int chipW = chipCount >= 10 ? 12 : chipCount == 6 ? 14 : 16;
            int chipH = 9;
            int gap = 3;
            int rowGap = 3;
            int totalW = perRow * chipW + (perRow - 1) * gap;
            int chipStartX = x + w / 2 - totalW / 2;
            int chipY = y + h - 18 - (chipRows - 1) * (chipH + rowGap);
            for (int row = 0; row < chipRows; row++) {
                for (int i = 0; i < perRow; i++) {
                    int idx = row * perRow + i;
                    if (idx >= chipCount) break;
                    int cx0 = chipStartX + i * (chipW + gap);
                    int cy0 = chipY + row * (chipH + rowGap);
                    g.fill(cx0 - 1, cy0 - 1, cx0 + chipW + 1, cy0 + chipH + 1, 0xFF000000);
                    g.fill(cx0, cy0, cx0 + chipW, cy0 + chipH, (0xC0 << 24) | (accent & 0x00FFFFFF));
                    g.centeredText(getFont(), romans[idx], cx0 + chipW / 2, cy0 + 1, 0xFFFFFFFF);
                }
            }
        }

        private void drawDragonGlyph(GuiGraphicsExtractor g, int cx, int cy, int r, int accent, boolean hovered) {
            long now = System.currentTimeMillis();
            // Outer rings (3 levels, each a fat square ring for simplicity)
            for (int i = 0; i < 3; i++) {
                int ringR = r - i * 6;
                int alpha = 0x40 + i * 0x40;
                int col = (alpha << 24) | (accent & 0x00FFFFFF);
                // top edge
                g.fill(cx - ringR, cy - ringR, cx + ringR, cy - ringR + 1, col);
                g.fill(cx - ringR, cy + ringR - 1, cx + ringR, cy + ringR, col);
                g.fill(cx - ringR, cy - ringR, cx - ringR + 1, cy + ringR, col);
                g.fill(cx + ringR - 1, cy - ringR, cx + ringR, cy + ringR, col);
            }
            // Rotating gem in the middle
            double phase = (now / 300.0);
            double s = hovered ? 1.0 + Math.sin(phase * 3) * 0.15 : 1.0;
            int gr = (int) (r / 3 * s);
            g.fill(cx - gr, cy - gr, cx + gr, cy + gr, 0xFF000000);
            g.fill(cx - gr + 1, cy - gr + 1, cx + gr - 1, cy + gr - 1, accent);
            // Highlight
            g.fill(cx - gr + 2, cy - gr + 2, cx - gr + 4, cy - gr + 4, 0xFFFFFFFF);
        }

        private net.minecraft.client.gui.Font getFont() {
            return net.minecraft.client.Minecraft.getInstance().font;
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narrationOutput) {
        }
    }
}
