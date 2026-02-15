package com.example.modid.listener;

import com.cleanroommc.kirino.engine.world.event.ModuleInstallerRegistrationEvent;
import com.cleanroommc.kirino.engine.world.type.Headless;
import com.example.modid.ExampleModuleInstaller;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class EngineEventListeners {

    @SubscribeEvent
    public void moduleInstallerRegister(ModuleInstallerRegistrationEvent event) {
        // must only register headless installers for now
        // since rendering part of kirino is disabled at the moment
        event.register(Headless.class, new ExampleModuleInstaller());
    }
}
