package com.endragonupgrade.client;

import com.endragonupgrade.EndRagonUpgradeMod;
import net.fabricmc.api.ClientModInitializer;

public class EndRagonUpgradeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EndRagonUpgradeMod.LOGGER.info("[{}] Client side loaded", EndRagonUpgradeMod.MOD_ID);
        ClientNetworkHandler.register();
        EmpoweredHealthBar.register();
        ScreenShakeHandler.register();
    }
}
