package com.example.modid.ecs;

import com.cleanroommc.kirino.ecs.entity.EntityManager;
import com.cleanroommc.kirino.ecs.job.JobScheduler;
import com.cleanroommc.kirino.ecs.system.exegraph.ISystemExeFlowGraph;
import com.cleanroommc.kirino.ecs.system.exegraph.SingleFlow;
import com.cleanroommc.kirino.ecs.world.CleanWorld;
import com.example.modid.ExampleMod;
import com.example.modid.ecs.component.Transform;
import org.joml.Vector3f;

import java.util.concurrent.ForkJoinPool;

public class ExampleWorld extends CleanWorld {

    public ExampleWorld(EntityManager entityManager, JobScheduler jobScheduler) {
        super(entityManager, jobScheduler);
    }

    private boolean flag1 = true;
    private boolean flag2 = true;

    private final SingleFlow<ExampleSystem> flow = SingleFlow
            .newBuilder(this, ExampleSystem.class)
            .setStartNodeCallback(() -> ExampleMod.LOGGER.info("Before ExampleSystem execution."))
            .setEndNodeCallback(() -> ExampleMod.LOGGER.info("After ExampleSystem execution."))
            .addTransition(new ExampleSystem(), ISystemExeFlowGraph.START_NODE, ISystemExeFlowGraph.END_NODE)
            .build();

    @Override
    public void update() {
        if (flag1) {
            flag1 = false;

            Transform transform0 = new Transform();
            transform0.position.pos = new Vector3f(1, 1, 1);
            Transform transform1 = new Transform();
            transform1.position.pos = new Vector3f(2, 2, 2);
            Transform transform2 = new Transform();
            transform2.position.pos = new Vector3f(3, 3, 3);

            entityManager.createEntity(transform0);
            entityManager.createEntity(transform1);
            entityManager.createEntity(transform2);
        } else if (flag2) {
            flag2 = false;
            flow.executeAsync(ForkJoinPool.commonPool());
        }

        super.update();
    }
}
