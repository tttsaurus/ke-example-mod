package com.example.modid;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = "@{MOD_ID}", name = "@{MOD_NAME}", version = "@{MOD_VERSION}")
public class ExampleMod {

    public static final Logger LOGGER = LogManager.getLogger("@{MOD_NAME}");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", "@{MOD_NAME}");
    }
}
