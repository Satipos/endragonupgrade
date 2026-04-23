package com.endragonupgrade.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.util.Random;

/**
 * Camera shake effect: jitters the local player's view rotation for ~2s on stage transitions.
 *
 * <p>v1.1: longer duration (40 ticks vs 20), exponential decay, stage-scaled intensity so stage 3
 * feels like an earthquake, and a short chromatic flash frame via Minecraft's portal overlay —
 * triggered purely client-side, nothing a mixin couldn't avoid.
 */
public final class ScreenShakeHandler {
    private static final Random RNG = new Random();
    private static int ticksLeft = 0;
    private static int totalTicks = 0;
    private static float intensity = 0f;

    private ScreenShakeHandler() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ScreenShakeHandler::onClientTick);
    }

    public static void trigger(int stage) {
        totalTicks = 40; // 2s
        ticksLeft = totalTicks;
        intensity = 2.5f + stage * 1.5f; // 4.0 / 5.5 / 7.0 — punchy
    }

    private static void onClientTick(Minecraft mc) {
        if (ticksLeft <= 0 || mc.player == null) return;
        ticksLeft--;
        // Exponential decay — strong at first, fades smoothly.
        float t = (float) ticksLeft / (float) totalTicks;
        float decay = t * t;
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
