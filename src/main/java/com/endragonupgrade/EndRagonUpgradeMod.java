package com.endragonupgrade;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EndRagonUpgradeMod implements ModInitializer {
    public static final String MOD_ID = "endragonupgrade";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String ACTIVATION_PHRASE = "Endo espo dragonio di quoro.";

    @Override
    public void onInitialize() {
        LOGGER.info("[{}] Initializing End Dragon Upgrade mod", MOD_ID);
        NetworkPayloads.registerCommon();
        ChatActivation.register();
        DragonBehavior.register();
    }
}
