package com.endragonupgrade.client;

import com.endragonupgrade.NetworkPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;

/**
 * Receives all clientbound payloads from {@link com.endragonupgrade.NetworkPayloads}.
 *
 * <p>v1.1 boosts the local empowerment spectacle: a dense multi-layer particle vortex
 * (portal + reverse_portal + end_rod + soul_fire_flame + enchant), an expanding impact ring
 * and a two-sound layered thunder stinger.
 */
public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.EmpowerStartPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> safe(() -> onEmpowerStart(payload))));
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.HealthUpdatePayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> safe(() -> EmpoweredHealthBar.onHealthUpdate(payload))));
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.StageTransitionPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> safe(() -> ScreenShakeHandler.trigger(payload.newStage()))));
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.ClearPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> safe(EmpoweredHealthBar::clear)));
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.OpenMenuPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> safe(() -> Minecraft.getInstance().setScreen(new DifficultySelectScreen()))));
        ClientPlayNetworking.registerGlobalReceiver(NetworkPayloads.DifficultyInfoPayload.TYPE,
                (payload, ctx) -> ctx.client().execute(() -> safe(() -> EmpoweredHealthBar.onDifficultyInfo(payload))));
    }

    private static void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) {
            com.endragonupgrade.EndRagonUpgradeMod.LOGGER.error("Client packet handler error", t);
        }
    }

    private static void onEmpowerStart(NetworkPayloads.EmpowerStartPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity dragon = mc.level.getEntity(payload.dragonEntityId());
        if (dragon == null) return;

        double dx = dragon.getX(), dy = dragon.getY(), dz = dragon.getZ();

        // Dense portal vortex (400 particles) swirling toward the dragon.
        for (int i = 0; i < 400; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = 4 + Math.random() * 6;
            double px = Math.cos(angle) * r;
            double pz = Math.sin(angle) * r;
            double py = (Math.random() - 0.5) * 10;
            mc.level.addParticle(ParticleTypes.PORTAL,
                    dx + px, dy + py + 2, dz + pz,
                    -px * 0.15, -py * 0.08, -pz * 0.15);
        }
        // End-rod sparkles for star-like glints.
        for (int i = 0; i < 80; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = 5 + Math.random() * 4;
            mc.level.addParticle(ParticleTypes.END_ROD,
                    dx + Math.cos(angle) * r, dy + Math.random() * 6 - 1, dz + Math.sin(angle) * r,
                    0, 0.04, 0);
        }
        // Soul fire — eerie mood.
        for (int i = 0; i < 60; i++) {
            double angle = Math.random() * Math.PI * 2;
            double r = 2 + Math.random() * 4;
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    dx + Math.cos(angle) * r, dy + Math.random() * 4, dz + Math.sin(angle) * r,
                    0, 0.02, 0);
        }
        // Enchant glyphs streaming upward.
        for (int i = 0; i < 60; i++) {
            mc.level.addParticle(ParticleTypes.ENCHANT,
                    dx + (Math.random() - 0.5) * 8, dy + 8 + Math.random() * 6, dz + (Math.random() - 0.5) * 8,
                    0, -0.2, 0);
        }
        // Reverse portal ring at ground level.
        for (int i = 0; i < 64; i++) {
            double a = (2 * Math.PI * i) / 64.0;
            mc.level.addParticle(ParticleTypes.REVERSE_PORTAL,
                    dx + Math.cos(a) * 10, dy - 1, dz + Math.sin(a) * 10,
                    -Math.cos(a) * 0.2, 0.1, -Math.sin(a) * 0.2);
        }

        mc.level.playLocalSound(dx, dy, dz, SoundEvents.LIGHTNING_BOLT_THUNDER,
                SoundSource.HOSTILE, 10.0f, 0.6f, false);
        mc.level.playLocalSound(dx, dy, dz, SoundEvents.LIGHTNING_BOLT_IMPACT,
                SoundSource.HOSTILE, 6.0f, 0.9f, false);
        mc.level.playLocalSound(dx, dy, dz, SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 8.0f, 0.6f, false);
    }
}
