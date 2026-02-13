package com.example.modid.listener;

import com.cleanroommc.kirino.engine.world.event.ModuleInstallerRegistrationEvent;
import com.cleanroommc.kirino.engine.world.type.Headless;
import com.example.modid.ExampleModuleInstaller;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class EngineEventListeners {

    @SubscribeEvent
    public void moduleInstallerRegister(ModuleInstallerRegistrationEvent event) {
        event.register(Headless.class, new ExampleModuleInstaller());
    }
}
