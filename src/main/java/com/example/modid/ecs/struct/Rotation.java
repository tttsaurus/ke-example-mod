package com.example.modid.ecs.struct;

import com.cleanroommc.kirino.ecs.component.scan.CleanStructSignature;
import org.joml.Vector3f;

@CleanStructSignature
public class Rotation {
    public Vector3f axis = new Vector3f();
    public float theta = 0f;
}
