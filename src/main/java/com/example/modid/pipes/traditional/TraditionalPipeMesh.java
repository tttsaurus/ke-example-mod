package com.example.modid.pipes.traditional;

import com.cleanroommc.kirino.gl.buffer.GLBuffer;
import com.cleanroommc.kirino.gl.buffer.meta.BufferUploadHint;
import com.cleanroommc.kirino.gl.buffer.view.EBOView;
import com.cleanroommc.kirino.gl.buffer.view.VBOView;
import com.cleanroommc.kirino.gl.vao.VAO;
import com.cleanroommc.kirino.gl.vao.attribute.AttributeLayout;
import com.cleanroommc.kirino.gl.vao.attribute.Slot;
import com.cleanroommc.kirino.gl.vao.attribute.Stride;
import com.cleanroommc.kirino.gl.vao.attribute.Type;
import com.example.modid.pipes.PipeGeometry;
import com.example.modid.pipes.PipeScene;
import com.example.modid.pipes.PipeWorld;
import com.example.modid.render.BlockMaterial;
import com.google.common.base.Preconditions;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class TraditionalPipeMesh implements AutoCloseable {

    private static final int VERTEX_STRIDE = 24;
    private static final int[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

    private final BlockMaterial material;
    private final float[][] textureCoordinates = new float[PipeWorld.TOPOLOGY_COUNT][];
    private final GLBuffer vertexBuffer;
    private final GLBuffer indexBuffer;
    private final VBOView vertices;
    private final EBOView indices;
    private final VAO vao;
    private int vertexCapacity = VERTEX_STRIDE;
    private int indexCapacity = Integer.BYTES;
    private int indexCount;
    private boolean closed;

    TraditionalPipeMesh(PipeScene scene) {
        Preconditions.checkNotNull(scene);

        material = scene.material;
        for (int topology = 0; topology < textureCoordinates.length; topology++) {
            float[] uv = new float[PipeGeometry.get(topology).size() * 4 * 2];
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer data = stack.malloc(uv.length * Float.BYTES);
                for (PipeGeometry.Quad quad : PipeGeometry.get(topology)) {
                    for (PipeGeometry.Vertex vertex : quad.vertices()) {
                        material.putUV(data, quad.face(), vertex.x(), vertex.y(), vertex.z());
                    }
                }
                data.flip().asFloatBuffer().get(uv);
            }
            textureCoordinates[topology] = uv;
        }

        vertexBuffer = new GLBuffer(true);
        indexBuffer = new GLBuffer(true);
        vertices = new VBOView(vertexBuffer, true);
        indices = new EBOView(indexBuffer, true);

        AttributeLayout layout = new AttributeLayout()
                .push(new Stride(VERTEX_STRIDE)
                        .push(new Slot(Type.FLOAT, 3))
                        .push(new Slot(Type.FLOAT, 2))
                        .push(new Slot(Type.UNSIGNED_BYTE, 2))
                        .push(new Slot(Type.UNSIGNED_BYTE, 1))
                        .push(new Slot(Type.UNSIGNED_BYTE, 1).setNormalize(true)));

        vao = new VAO(true, layout, indices, vertices);
        update(scene);
    }

    void update(PipeScene scene) {
        Preconditions.checkState(!closed, "Mesh is closed.");
        Preconditions.checkNotNull(scene);
        Preconditions.checkArgument(scene.material == material, "Material changes require a new mesh.");

        int quadCount = 0;
        for (PipeScene.Block block : scene.blocks) {
            quadCount += PipeGeometry.get(block.cell().topology()).size();
        }
        int vertexBytes = quadCount * 4 * VERTEX_STRIDE;
        int indexBytes = quadCount * 6 * Integer.BYTES;
        ByteBuffer data = MemoryUtil.memAlloc(vertexBytes + indexBytes);
        try {
            ByteBuffer vertexData = data.slice(0, vertexBytes).order(ByteOrder.nativeOrder());
            ByteBuffer indexData = data.slice(vertexBytes, indexBytes).order(ByteOrder.nativeOrder());
            int firstVertex = 0;
            for (PipeScene.Block block : scene.blocks) {
                PipeWorld.Cell cell = block.cell();
                float[] uv = textureCoordinates[cell.topology()];
                int uvIndex = 0;
                byte brightness = (byte) Math.round(block.brightness() * 255);
                for (PipeGeometry.Quad quad : PipeGeometry.get(cell.topology())) {
                    for (PipeGeometry.Vertex vertex : quad.vertices()) {
                        vertexData.putFloat(cell.x() + vertex.x());
                        vertexData.putFloat(cell.y() + vertex.y());
                        vertexData.putFloat(cell.z() + vertex.z());
                        vertexData.putFloat(uv[uvIndex++]);
                        vertexData.putFloat(uv[uvIndex++]);
                        vertexData.put((byte) block.lightmap());
                        vertexData.put((byte) (block.lightmap() >>> 16));
                        vertexData.put((byte) quad.face().getIndex());
                        vertexData.put(brightness);
                    }
                    for (int index : QUAD_INDICES) {
                        indexData.putInt(firstVertex + index);
                    }
                    firstVertex += 4;
                }
            }

            if (vertexBytes > vertexCapacity) {
                vertexCapacity = Integer.highestOneBit(vertexBytes - 1) << 1;
            }
            if (indexBytes > indexCapacity) {
                indexCapacity = Integer.highestOneBit(indexBytes - 1) << 1;
            }
            vertices.alloc(vertexCapacity, BufferUploadHint.DYNAMIC_DRAW);
            indices.alloc(indexCapacity, BufferUploadHint.DYNAMIC_DRAW);
            if (quadCount != 0) {
                vertices.uploadBySubData(0, vertexData.flip());
                indices.uploadBySubData(0, indexData.flip());
            }
            indexCount = quadCount * 6;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    int draw() {
        Preconditions.checkState(!closed, "Mesh is closed.");

        if (indexCount == 0) {
            return 0;
        }

        vao.bind();
        GL11.glDrawElements(GL11.GL_TRIANGLES, indexCount, GL11.GL_UNSIGNED_INT, 0L);
        return 1;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        vao.disposeManually();
        indexBuffer.disposeManually();
        vertexBuffer.disposeManually();
    }
}
