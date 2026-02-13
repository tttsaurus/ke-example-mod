package com.example.modid;

import com.cleanroommc.kirino.KirinoCommonCore;
import com.example.modid.listener.ECSEventListeners;
import com.example.modid.listener.EngineEventListeners;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = Reference.MOD_ID, name = Reference.MOD_NAME, version = Reference.VERSION)
public class ExampleMod {

    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_NAME);

    /**
     * <a href="https://cleanroommc.com/wiki/forge-mod-development/event#overview">
     *     Take a look at how many FMLStateEvents you can listen to via the @Mod.EventHandler annotation here
     * </a>
     */
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Hello From {}!", Reference.MOD_NAME);

        // kirino engine can run on server but doesn't provide any server side lifecycle yet.
        // kirino ecs is mostly used for client side objects at the moment.
        //
        // Note:
        //   You're still able to tick kirino ecs on server by listening to server events.
        //   That is, you're providing your own server lifecycle.
        if (FMLCommonHandler.instance().getSide().isClient()) {
            // register ecs event listeners
            KirinoCommonCore.KIRINO_EVENT_BUS.register(new ECSEventListeners());
        }

        // register engine event listeners
        KirinoCommonCore.KIRINO_EVENT_BUS.register(new EngineEventListeners());
    }
}
