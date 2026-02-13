package com.example.modid.ecs.component;

import com.cleanroommc.kirino.ecs.component.CleanComponent;
import com.cleanroommc.kirino.ecs.component.scan.CleanComponentSignature;
import com.example.modid.ecs.struct.Position;
import com.example.modid.ecs.struct.Rotation;
import com.example.modid.ecs.struct.Scale;

@CleanComponentSignature
public class Transform implements CleanComponent {
    public Position position = new Position();
    public Rotation rotation = new Rotation();
    public Scale scale = new Scale();
}
