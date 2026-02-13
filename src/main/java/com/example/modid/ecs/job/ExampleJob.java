package com.example.modid.ecs.job;

import com.cleanroommc.kirino.ecs.entity.EntityManager;
import com.cleanroommc.kirino.ecs.entity.EntityQuery;
import com.cleanroommc.kirino.ecs.job.ParallelJob;
import com.cleanroommc.kirino.ecs.job.JobDataQuery;
import com.cleanroommc.kirino.ecs.storage.PrimitiveArray;
import com.example.modid.ExampleMod;
import com.example.modid.ecs.component.Transform;
import org.jspecify.annotations.NonNull;

public class ExampleJob implements ParallelJob {

    @JobDataQuery(componentClass = Transform.class, fieldAccessChain = {"position", "pos", "x"})
    PrimitiveArray posXArr;

    @JobDataQuery(componentClass = Transform.class, fieldAccessChain = {"position", "pos", "y"})
    PrimitiveArray posYArr;

    @JobDataQuery(componentClass = Transform.class, fieldAccessChain = {"position", "pos", "z"})
    PrimitiveArray posZArr;

    @Override
    public void execute(@NonNull EntityManager entityManager, int index, int entityID, int threadOrdinal) {
        ExampleMod.LOGGER.info("Hello, I'm Transform {}. XYZ: {}, {}, {}",
                entityID, posXArr.getFloat(index), posYArr.getFloat(index), posZArr.getFloat(index));
    }

    @Override
    public void query(@NonNull EntityQuery entityQuery) {
        entityQuery.with(Transform.class);
    }

    @Override
    public int estimateWorkload(int i) {
        return 1;
    }
}
