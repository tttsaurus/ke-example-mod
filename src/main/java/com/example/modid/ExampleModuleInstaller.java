package com.example.modid;

import com.cleanroommc.kirino.engine.FramePhase;
import com.cleanroommc.kirino.engine.FramePhaseTiming;
import com.cleanroommc.kirino.engine.resource.ResourceLayout;
import com.cleanroommc.kirino.engine.world.ModuleInstaller;
import com.cleanroommc.kirino.engine.world.context.WorldContext;
import com.cleanroommc.kirino.engine.world.type.Headless;
import com.example.modid.ecs.ExampleWorld;
import org.jspecify.annotations.NonNull;

public class ExampleModuleInstaller implements ModuleInstaller<Headless> {

    private boolean init = false;

    private void prepare(WorldContext<Headless> worldContext) {
        if (init) {
            return;
        }
        init = true;

        ExampleMod.LOGGER.info("Hello Kirino! ExampleMod Kirino Module initialized.");
    }

    private ExampleWorld world;

    private void update(WorldContext<Headless> worldContext) {
        world.update();
    }

    @Override
    public void install(@NonNull WorldContext<Headless> worldContext, @NonNull ResourceLayout resourceLayout) {
        worldContext.on(FramePhase.PREPARE, FramePhaseTiming.BEFORE, this::prepare);
        worldContext.on(FramePhase.UPDATE, FramePhaseTiming.BEFORE, this::update);

        world = new ExampleWorld(worldContext.ecs().entityManager, worldContext.ecs().jobScheduler);
    }
}
