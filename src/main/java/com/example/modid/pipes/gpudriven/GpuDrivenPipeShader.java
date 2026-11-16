package com.example.modid.pipes.gpudriven;

import com.cleanroommc.kirino.ICS;
import com.cleanroommc.kirino.gl.shader.Shader;
import com.cleanroommc.kirino.gl.shader.ShaderProgram;
import com.example.modid.pipes.PipeFrame;
import com.example.modid.pipes.PipeWorld;
import com.example.modid.render.BlockMaterial;
import com.google.common.base.Preconditions;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.model.pipeline.LightUtil;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

final class GpuDrivenPipeShader implements AutoCloseable {

    static final int[][] TOPOLOGY_FACES = buildTopologyFaces();

    private final Shader vertexShader;
    private final Shader fragmentShader;
    private final ShaderProgram program;
    private boolean closed;

    GpuDrivenPipeShader(BlockMaterial material, int blockTextureUnit) {
        Preconditions.checkNotNull(material);

        vertexShader = ICS.instance().shader().makeShader("@{MOD_ID}", "shaders/pipes/gpudriven.vert");
        fragmentShader = ICS.instance().shader().makeShader("@{MOD_ID}", "shaders/pipes/gpudriven.frag");

        ICS.instance().shader().submitToGL(vertexShader, fragmentShader);

        program = ICS.instance().shader().makeProgram(true, vertexShader, fragmentShader);
        program.uniform1i("atlas", 0);
        program.uniform1i("lightmap", 1);
        program.uniform1i("blocks", blockTextureUnit);
        for (int topology = 0; topology < TOPOLOGY_FACES.length; topology++) {
            int[] faces = TOPOLOGY_FACES[topology];
            program.uniform4ui("topologyFaces[" + topology * 2 + "]", faces[0], faces[1], faces[2], faces[3]);
            program.uniform4ui("topologyFaces[" + (topology * 2 + 1) + "]", faces[4], faces[5], faces[6], faces[7]);
        }
        for (EnumFacing face : EnumFacing.VALUES) {
            program.uniform1f("faceShade[" + face.getIndex() + "]", LightUtil.diffuseLight(face));
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer uv = stack.malloc(6 * 4 * 2 * Float.BYTES);
            for (EnumFacing face : EnumFacing.VALUES) {
                for (int corner = 0; corner < 4; corner++) {
                    float s = corner & 1;
                    float t = corner >>> 1;
                    material.putUV(uv, face,
                            face.getAxis() == EnumFacing.Axis.X ? 0 : s,
                            face.getAxis() == EnumFacing.Axis.Y ? 0 : t,
                            face.getAxis() == EnumFacing.Axis.X ? s : face.getAxis() == EnumFacing.Axis.Y ? t : 0);
                }
            }
            program.uniform2fv("faceUV", uv.flip().asFloatBuffer());
        }
    }

    private static int[][] buildTopologyFaces() {
        int[][] topologies = new int[PipeWorld.TOPOLOGY_COUNT][8];

        for (int topology = 0; topology < topologies.length; topology++) {
            int[] faces = topologies[topology];
            int quad = 0;
            for (EnumFacing face : EnumFacing.VALUES) {
                if ((topology & 1 << face.getIndex()) == 0) {
                    faces[quad / 4] |= face.getIndex() << (quad % 4 * 8);
                    quad++;
                }
            }
            for (EnumFacing direction : EnumFacing.VALUES) {
                if ((topology & 1 << direction.getIndex()) == 0) {
                    continue;
                }

                for (EnumFacing face : EnumFacing.VALUES) {
                    if (face.getAxis() == direction.getAxis()) {
                        continue;
                    }

                    int descriptor = face.getIndex() | (direction.getIndex() + 1) << 3;
                    faces[quad / 4] |= descriptor << (quad % 4 * 8);
                    quad++;
                }
            }
            Preconditions.checkState(quad == 6 + 3 * Integer.bitCount(topology),
                    "Unexpected topology face count.");
        }

        return topologies;
    }

    void update(IntBuffer quadEnds, IntBuffer blockOffsets) {
        Preconditions.checkState(!closed, "Shader is closed.");
        Preconditions.checkNotNull(quadEnds);
        Preconditions.checkNotNull(blockOffsets);
        Preconditions.checkArgument(quadEnds.remaining() == GpuDrivenPipeRenderer.CONNECTION_GROUP_COUNT
                && blockOffsets.remaining() == GpuDrivenPipeRenderer.CONNECTION_GROUP_COUNT);

        program.uniform1iv("quadEnds", quadEnds);
        program.uniform1iv("blockOffsets", blockOffsets);
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
