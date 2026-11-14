package com.example.modid.pipes.instanced;

import com.cleanroommc.kirino.ICS;
import com.cleanroommc.kirino.gl.shader.Shader;
import com.cleanroommc.kirino.gl.shader.ShaderProgram;
import com.cleanroommc.kirino.gl.shader.reflect.ShaderReflection;
import com.cleanroommc.kirino.gl.shader.reflect.resource.Variable;
import com.cleanroommc.kirino.gl.shader.reflect.type.ArrayType;
import com.cleanroommc.kirino.gl.shader.reflect.type.GlslType;
import com.cleanroommc.kirino.gl.shader.reflect.type.MatrixType;
import com.cleanroommc.kirino.gl.shader.reflect.type.ScalarType;
import com.cleanroommc.kirino.gl.shader.reflect.type.VectorType;
import com.example.modid.pipes.PipeFrame;
import com.google.common.base.Preconditions;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;

final class PipeShader implements AutoCloseable {

    private final Shader vertexShader;
    private final Shader fragmentShader;
    private final ShaderProgram program;
    private boolean closed;

    PipeShader() {
        vertexShader = ICS.instance().shader().makeShader("@{MOD_ID}", "shaders/pipes/instanced.vert");
        fragmentShader = ICS.instance().shader().makeShader("@{MOD_ID}", "shaders/pipes/instanced.frag");

        ICS.instance().shader().submitToGL(vertexShader, fragmentShader);

        program = ICS.instance().shader().makeProgram(true, vertexShader, fragmentShader);
        program.uniform1i("atlas", 0);
        program.uniform1i("lightmap", 1);
        for (EnumFacing face : EnumFacing.VALUES) {
            program.uniform1f("faceShade[" + face.getIndex() + "]", LightUtil.diffuseLight(face));
        }

        validateInterface();
    }

    /**
     * Demonstrates some of the shader reflection APIs.
     */
    private void validateInterface() {
        ShaderReflection vertex = program.vertRefl().orElseThrow();
        ShaderReflection fragment = program.fragRefl().orElseThrow();

        MatrixType transform = vertex.uniform("transform")
                .orElseThrow()
                .type()
                .as(MatrixType.class)
                .orElseThrow();
        Preconditions.checkState(transform.equals(new MatrixType(ScalarType.FLOAT, 4, 4)),
                "\"transform\" must be mat4.");

        ArrayType faceShade = vertex.uniform("faceShade")
                .orElseThrow()
                .type()
                .as(ArrayType.class)
                .orElseThrow();
        Preconditions.checkState(faceShade.elementType() == ScalarType.FLOAT
                && faceShade.extent().length().orElseThrow() == 6,
                "\"faceShade\" must be float[6].");

        Variable position = vertex.input("position").orElseThrow();
        Preconditions.checkState(position.type().equals(new VectorType(ScalarType.FLOAT, 3)),
                "\"position\" must be vec3.");
        Preconditions.checkState(Preconditions.checkNotNull(position.layout().location()).requireValue() == 0,
                "\"position\" must use attribute location zero.");

        Variable vertexUV = vertex.output("UV").orElseThrow();
        Variable fragmentUV = fragment.input("UV").orElseThrow();
        Preconditions.checkState(vertexUV.type().equals(new VectorType(ScalarType.FLOAT, 2))
                && GlslType.sameType(vertexUV.type(), fragmentUV.type()),
                "\"UV\" must be vec2 in both stages.");
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
