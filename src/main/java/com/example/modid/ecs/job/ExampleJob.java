package com.example.modid.ecs.job;

import com.cleanroommc.kirino.ecs.entity.EntityManager;
import com.cleanroommc.kirino.ecs.entity.EntityQuery;
import com.cleanroommc.kirino.ecs.job.IParallelJob;
import com.cleanroommc.kirino.ecs.job.JobDataQuery;
import com.cleanroommc.kirino.ecs.storage.IPrimitiveArray;
import com.example.modid.ExampleMod;
import com.example.modid.ecs.component.Transform;
import org.jspecify.annotations.NonNull;

public class ExampleJob implements IParallelJob {

    @JobDataQuery(componentClass = Transform.class, fieldAccessChain = {"position", "pos", "x"})
    IPrimitiveArray posXArr;

    @JobDataQuery(componentClass = Transform.class, fieldAccessChain = {"position", "pos", "y"})
    IPrimitiveArray posYArr;

    @JobDataQuery(componentClass = Transform.class, fieldAccessChain = {"position", "pos", "z"})
    IPrimitiveArray posZArr;

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
