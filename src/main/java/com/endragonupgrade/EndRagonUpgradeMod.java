package com.endragonupgrade;

import com.endragonupgrade.item.ChainOfGodItem;
import com.endragonupgrade.item.DragonShotItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
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
        DragonShotItem.register();
        ChainOfGodItem.register();
        ChatActivation.register();
        DragonBehavior.register();

        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
            output.accept(new ItemStack(DragonShotItem.INSTANCE));
            output.accept(new ItemStack(ChainOfGodItem.INSTANCE));
        });
    }
}
