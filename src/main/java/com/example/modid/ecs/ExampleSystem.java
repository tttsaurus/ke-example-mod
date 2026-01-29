package com.example.modid.ecs;

import com.cleanroommc.kirino.ecs.entity.EntityManager;
import com.cleanroommc.kirino.ecs.job.JobScheduler;
import com.cleanroommc.kirino.ecs.system.CleanSystem;
import com.example.modid.ecs.job.ExampleJob;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.ForkJoinPool;

public class ExampleSystem extends CleanSystem {

    @Override
    public void update(@NonNull EntityManager entityManager, @NonNull JobScheduler jobScheduler) {
        var handle = jobScheduler.executeParallelJob(entityManager, ExampleJob.class, null, ForkJoinPool.commonPool());
        execution.updateExecutions(handle);
    }
}
