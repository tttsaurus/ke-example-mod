package com.example.modid.pipes.traditional;

import com.cleanroommc.kirino.ICS;
import com.cleanroommc.kirino.gl.shader.Shader;
import com.cleanroommc.kirino.gl.shader.ShaderProgram;
import com.example.modid.pipes.PipeFrame;
import com.google.common.base.Preconditions;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;

final class TraditionalPipeShader implements AutoCloseable {

    private final Shader vertexShader;
    private final Shader fragmentShader;
    private final ShaderProgram program;
    private boolean closed;

    TraditionalPipeShader() {
        vertexShader = ICS.instance().shader().makeShader("@{MOD_ID}", "shaders/pipes/traditional.vert");
        fragmentShader = ICS.instance().shader().makeShader("@{MOD_ID}", "shaders/pipes/traditional.frag");

        ICS.instance().shader().submitToGL(vertexShader, fragmentShader);

        program = ICS.instance().shader().makeProgram(true, vertexShader, fragmentShader);
        program.uniform1i("atlas", 0);
        program.uniform1i("lightmap", 1);
        for (EnumFacing face : EnumFacing.VALUES) {
            program.uniform1f("faceShade[" + face.getIndex() + "]", LightUtil.diffuseLight(face));
        }
    }

    void bind(PipeFrame frame) {
        Preconditions.checkState(!closed, "Shader is closed.");
        Preconditions.checkNotNull(frame);

        program.uniformMatrix4f("transform", false, frame.transformBuffer());
        program.uniform4f("fogPlane", frame.fogPlane.x, frame.fogPlane.y, frame.fogPlane.z, frame.fogPlane.w);
        program.uniform1i("fogMode", frame.fogMode);
        program.uniform1f("waterFogDensity", frame.waterFogDensity);
        program.use();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        program.disposeManually();
        fragmentShader.disposeManually();
        vertexShader.disposeManually();
    }
}
