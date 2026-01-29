package com.example.modid.ecs.component;

import com.cleanroommc.kirino.ecs.component.ICleanComponent;
import com.cleanroommc.kirino.ecs.component.scan.CleanComponent;
import com.example.modid.ecs.struct.Position;
import com.example.modid.ecs.struct.Rotation;
import com.example.modid.ecs.struct.Scale;

@CleanComponent
public class Transform implements ICleanComponent {
    public Position position = new Position();
    public Rotation rotation = new Rotation();
    public Scale scale = new Scale();
}
