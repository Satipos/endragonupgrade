package com.endragonupgrade.client;

import com.endragonupgrade.NetworkPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Custom screen shown when the activation phrase is recognised — player picks which difficulty
 * to empower the dragon on. Uses fill-based painting to avoid shipping any GUI textures.
 */
public class DifficultySelectScreen extends Screen {
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 240;
    private static final int BTN_W = 260;
    private static final int BTN_H = 48;

    public DifficultySelectScreen() {
        super(Component.translatable("endragonupgrade.menu.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int panelTop = (this.height - PANEL_H) / 2;
        int hardY = panelTop + 70;
        int vhY = panelTop + 130;
        int cancelY = panelTop + 195;

        this.addRenderableWidget(
                Button.builder(Component.translatable("endragonupgrade.menu.hard"),
                                b -> pick(0))
                        .bounds(cx - BTN_W / 2, hardY, BTN_W, BTN_H)
                        .build()
        );
        this.addRenderableWidget(
                Button.builder(Component.translatable("endragonupgrade.menu.very_hard")
                                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                                b -> pick(1))
                        .bounds(cx - BTN_W / 2, vhY, BTN_W, BTN_H)
                        .build()
        );
        this.addRenderableWidget(
                Button.builder(Component.translatable("endragonupgrade.menu.cancel"),
                                b -> this.onClose())
                        .bounds(cx - 60, cancelY, 120, 20)
                        .build()
        );
    }

    private void pick(int difficultyId) {
        ClientPlayNetworking.send(new NetworkPayloads.SelectDifficultyPayload(difficultyId));
        this.onClose();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Dim background with a colour wash, no blur (blur requires resource shaders).
        graphics.fill(0, 0, this.width, this.height, 0xD0000008);

        int cx = this.width / 2;
        int panelTop = (this.height - PANEL_H) / 2;
        int panelLeft = cx - PANEL_W / 2;

        // Layered panel — outer glow, black bezel, violet rim, dark interior.
        for (int k = 6; k >= 2; k--) {
            int alpha = 0x10 + (6 - k) * 0x18;
            graphics.fill(panelLeft - k, panelTop - k,
                    panelLeft + PANEL_W + k, panelTop + PANEL_H + k,
                    (alpha << 24) | 0x9040E0);
        }
        graphics.fill(panelLeft - 2, panelTop - 2, panelLeft + PANEL_W + 2, panelTop + PANEL_H + 2, 0xFF000000);
        graphics.fill(panelLeft - 1, panelTop - 1, panelLeft + PANEL_W + 1, panelTop + PANEL_H + 1, 0xFFB24CFF);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_W, panelTop + PANEL_H, 0xFF140028);

        // Animated top divider — a scrolling "portal" gradient under the title.
        long now = System.currentTimeMillis();
        for (int i = 0; i < PANEL_W; i++) {
            float t = (i + (now / 12f) % PANEL_W) / PANEL_W;
            float a = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
            int col = (int) (0x60 + a * 0x9F) << 24 | 0x00B24CFF;
            graphics.fill(panelLeft + i, panelTop + 40, panelLeft + i + 1, panelTop + 42, col);
        }

        // Title + subtitle
        graphics.centeredText(this.font,
                Component.translatable("endragonupgrade.menu.title")
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                cx, panelTop + 16, 0xFFFFFFFF);
        graphics.centeredText(this.font,
                Component.translatable("endragonupgrade.menu.subtitle")
                        .withStyle(ChatFormatting.GRAY),
                cx, panelTop + 28, 0xFFCDA0FF);

        // Descriptions under each button
        graphics.centeredText(this.font,
                Component.translatable("endragonupgrade.menu.hard.desc")
                        .withStyle(ChatFormatting.GRAY),
                cx, panelTop + 70 + BTN_H + 2, 0xFF8AC9FF);
        graphics.centeredText(this.font,
                Component.translatable("endragonupgrade.menu.very_hard.desc")
                        .withStyle(ChatFormatting.GRAY),
                cx, panelTop + 130 + BTN_H + 2, 0xFFFF9090);

        // Warning ribbon above VERY_HARD
        String warn = "§4§l⚠  §c§lЭкстремальная сложность  §4§l⚠";
        graphics.centeredText(this.font, warn, cx, panelTop + 110, 0xFFFFFFFF);

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
