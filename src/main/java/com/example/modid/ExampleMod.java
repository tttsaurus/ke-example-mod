package com.example.modid;

import com.cleanroommc.kirino.KirinoCommonCore;
import com.cleanroommc.kirino.ecs.component.scan.event.ComponentScanningEvent;
import com.cleanroommc.kirino.ecs.component.scan.event.StructScanningEvent;
import com.cleanroommc.kirino.ecs.job.event.JobRegistrationEvent;
import com.cleanroommc.kirino.engine.world.event.ModuleInstallerRegistrationEvent;
import com.cleanroommc.kirino.engine.world.type.Headless;
import com.example.modid.ecs.job.ExampleJob;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
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

        KirinoCommonCore.KIRINO_EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void moduleInstallerRegister(ModuleInstallerRegistrationEvent event) {
        event.register(Headless.class, new ExampleModuleInstaller());
    }

    @SubscribeEvent
    public void structScan(StructScanningEvent event) {
        event.register("com.example.modid.ecs.struct");
    }

    @SubscribeEvent
    public void componentScan(ComponentScanningEvent event) {
        event.register("com.example.modid.ecs.component");
    }

    @SubscribeEvent
    public void jobRegister(JobRegistrationEvent event) {
        event.register(ExampleJob.class);
    }
}
