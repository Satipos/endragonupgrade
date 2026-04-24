package com.endragonupgrade.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Registers a full-replacement lobby: whenever the vanilla {@link TitleScreen} opens,
 * we swap in our custom {@link CustomLobbyScreen}. Done via Fabric's screen events —
 * no mixins. Replacement happens on {@code AFTER_INIT} so all normal init has run
 * (realms notifications, etc.) and we're safely on the render thread.
 */
public final class CustomLobby {
    private static boolean suppressReplace = false;

    private CustomLobby() {
    }

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (suppressReplace) return;
            if (!(screen instanceof TitleScreen)) return;
            if (screen instanceof CustomLobbyScreen) return;
            // Replace on the next tick to avoid re-entrancy while init is running.
            client.execute(() -> {
                if (client.screen instanceof TitleScreen && !(client.screen instanceof CustomLobbyScreen)) {
                    client.setScreen(new CustomLobbyScreen());
                }
            });
        });
    }
}
