package com.example.modid.ecs.struct;

import com.cleanroommc.kirino.ecs.component.scan.CleanStruct;
import org.joml.Vector3f;

@CleanStruct
public class Rotation {
    public Vector3f axis = new Vector3f();
    public float theta = 0f;
}
