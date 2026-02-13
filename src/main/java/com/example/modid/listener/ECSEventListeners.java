package com.example.modid.listener;

import com.cleanroommc.kirino.ecs.component.scan.event.ComponentScanningEvent;
import com.cleanroommc.kirino.ecs.component.scan.event.StructScanningEvent;
import com.cleanroommc.kirino.ecs.job.event.JobRegistrationEvent;
import com.example.modid.ecs.job.ExampleJob;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class ECSEventListeners {

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
