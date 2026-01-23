package com.example.modid;

import com.cleanroommc.kirino.engine.FramePhase;
import com.cleanroommc.kirino.engine.FramePhaseTiming;
import com.cleanroommc.kirino.engine.resource.ResourceLayout;
import com.cleanroommc.kirino.engine.world.ModuleInstaller;
import com.cleanroommc.kirino.engine.world.context.AnalyticalWorldView;
import com.cleanroommc.kirino.engine.world.context.WorldContext;
import com.cleanroommc.kirino.engine.world.type.Headless;
import org.jspecify.annotations.NonNull;

public class TestModuleInstaller implements ModuleInstaller<Headless> {

    private boolean init = false;

    private void prepare(WorldContext<Headless> worldContext) {
        if (init) {
            return;
        }
        init = true;

        ExampleMod.LOGGER.info("ExampleMod Kirino Module initialized.");

        AnalyticalWorldView view = castHeadless(worldContext);
    }

    @Override
    public void install(@NonNull WorldContext<Headless> worldContext, @NonNull ResourceLayout resourceLayout) {
        worldContext.on(FramePhase.PREPARE, FramePhaseTiming.BEFORE, this::prepare);
    }
}
