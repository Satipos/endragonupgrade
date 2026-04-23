package com.endragonupgrade.client;

import com.endragonupgrade.EndRagonUpgradeMod;
import com.endragonupgrade.item.DragonShotItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public class EndRagonUpgradeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EndRagonUpgradeMod.LOGGER.info("[{}] Client side loaded", EndRagonUpgradeMod.MOD_ID);
        ClientNetworkHandler.register();
        EmpoweredHealthBar.register();
        ScreenShakeHandler.register();

        // Required or Minecraft crashes when the custom arrow entity reaches a client.
        EntityRendererRegistry.register(DragonShotItem.ENTITY_TYPE, DragonShotArrowRenderer::new);

        // v2.0: while an empowered dragon is active, suppress the vanilla boss bar rail.
        // We replace BOSS_BAR with a wrapper that skips rendering when we own the HUD.
        HudElementRegistry.replaceElement(
                VanillaHudElements.BOSS_BAR,
                original -> (HudElement) (graphics, delta) -> {
                    if (EmpoweredHealthBar.isActive()) {
                        return; // fully suppress vanilla boss bars during empowered fights
                    }
                    original.extractRenderState(graphics, delta);
                }
        );
    }
}
