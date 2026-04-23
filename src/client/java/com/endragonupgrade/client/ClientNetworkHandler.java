package com.endragonupgrade.client;

import com.endragonupgrade.NetworkPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.EmpowerStartPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> onEmpowerStart(payload)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.HealthUpdatePayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> EmpoweredHealthBar.onHealthUpdate(payload)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.StageTransitionPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> ScreenShakeHandler.trigger(payload.newStage())));
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.ClearPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(EmpoweredHealthBar::clear));
    }

    private static void onEmpowerStart(NetworkPayloads.EmpowerStartPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity dragon = mc.level.getEntity(payload.dragonEntityId());
        if (dragon == null) return;
        // Local cosmetic: a ring of portal particles around the dragon client-side.
        for (int i = 0; i < 120; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = 4 + Math.random() * 3;
            double dx = Math.cos(angle) * r;
            double dz = Math.sin(angle) * r;
            double dy = (Math.random() - 0.5) * 6;
            mc.level.addParticle(ParticleTypes.PORTAL,
                    dragon.getX() + dx, dragon.getY() + dy + 2, dragon.getZ() + dz,
                    -dx * 0.1, -dy * 0.05, -dz * 0.1);
        }
        mc.level.playLocalSound(dragon.getX(), dragon.getY(), dragon.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.HOSTILE, 6.0f, 0.8f, false);
    }
}
