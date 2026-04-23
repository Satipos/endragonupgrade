package com.endragonupgrade.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.Random;

/**
 * Cheap screen shake effect: briefly jitters the local player's view rotation.
 * Triggered by a server payload at stage transitions.
 */
public final class ScreenShakeHandler {
    private static final Random RNG = new Random();
    private static int ticksLeft = 0;
    private static float intensity = 0f;

    private ScreenShakeHandler() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ScreenShakeHandler::onClientTick);
    }

    public static void trigger(int stage) {
        ticksLeft = 20; // 1s
        intensity = 1.5f + stage * 0.8f;
    }

    private static void onClientTick(Minecraft mc) {
        if (ticksLeft <= 0 || mc.player == null) return;
        ticksLeft--;
        float decay = (float) ticksLeft / 20.0f;
        float amp = intensity * decay;
        float yaw = (RNG.nextFloat() - 0.5f) * 2.0f * amp;
        float pitch = (RNG.nextFloat() - 0.5f) * 2.0f * amp;
        mc.player.setYRot(mc.player.getYRot() + yaw);
        mc.player.setXRot(clampPitch(mc.player.getXRot() + pitch));
    }

    private static float clampPitch(float pitch) {
        if (pitch < -90f) return -90f;
        if (pitch > 90f) return 90f;
        return pitch;
    }
}
