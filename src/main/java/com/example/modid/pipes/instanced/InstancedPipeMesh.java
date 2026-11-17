package com.example.modid.pipes.instanced;

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
import com.google.common.collect.ImmutableList;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL42;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class InstancedPipeMesh implements AutoCloseable {

    private record Batch(int indexCount, long indexOffset, int instanceCount, int firstInstance) {
    }

    private static final int VERTEX_STRIDE = 12;
    private static final int INSTANCE_STRIDE = 8;
    private static final int[] QUAD_INDICES = {0, 1, 2, 0, 2, 3};

    private final GLBuffer vertexBuffer;
    private final GLBuffer indexBuffer;
    private final GLBuffer instanceBuffer;
    private final VBOView instances;
    private final VAO vao;
    private final Batch[] batches;
    private final BlockMaterial material;
    private int instanceCapacity = INSTANCE_STRIDE;
    private boolean closed;

    InstancedPipeMesh(PipeScene scene) {
        Preconditions.checkNotNull(scene);

        material = scene.material;
        int quadCount = 0;
        for (int topology = 0; topology < PipeWorld.TOPOLOGY_COUNT; topology++) {
            quadCount += PipeGeometry.get(topology).size();
        }
        int vertexCount = quadCount * 4;
        int indexCount = quadCount * 6;
        Preconditions.checkArgument(vertexCount <= 65536,
                "Pipe geometry exceeds unsigned short indices.");

        batches = new Batch[PipeWorld.TOPOLOGY_COUNT];

        vertexBuffer = new GLBuffer(true);
        indexBuffer = new GLBuffer(true);
        instanceBuffer = new GLBuffer(true);
        VBOView vertices = new VBOView(vertexBuffer, true);
        EBOView indices = new EBOView(indexBuffer, true);
        instances = new VBOView(instanceBuffer, true);

        int vertexBytes = vertexCount * VERTEX_STRIDE;
        int indexBytes = indexCount * Short.BYTES;
        ByteBuffer data = MemoryUtil.memAlloc(vertexBytes + indexBytes);
        try {
            ByteBuffer vertexData = data.slice(0, vertexBytes).order(ByteOrder.nativeOrder());
            ByteBuffer indexData = data.slice(vertexBytes, indexBytes).order(ByteOrder.nativeOrder());
            int firstVertex = 0;
            for (int topology = 0; topology < batches.length; topology++) {
                ImmutableList<PipeGeometry.Quad> quads = PipeGeometry.get(topology);
                batches[topology] = new Batch(quads.size() * 6, indexData.position(), 0, 0);
                for (PipeGeometry.Quad quad : quads) {
                    for (PipeGeometry.Vertex vertex : quad.vertices()) {
                        vertexData.put((byte) Math.round(vertex.x() * 16));
                        vertexData.put((byte) Math.round(vertex.y() * 16));
                        vertexData.put((byte) Math.round(vertex.z() * 16));
                        vertexData.put((byte) quad.face().getIndex());
                        material.putUV(vertexData, quad.face(), vertex.x(), vertex.y(), vertex.z());
                    }
                    for (int index : QUAD_INDICES) {
                        indexData.putShort((short) (firstVertex + index));
                    }
                    firstVertex += 4;
                }
            }
            vertices.uploadDirectly(vertexData.flip(), BufferUploadHint.STATIC_DRAW);
            indices.uploadDirectly(indexData.flip(), BufferUploadHint.STATIC_DRAW);
        } finally {
            MemoryUtil.memFree(data);
        }

        AttributeLayout layout = new AttributeLayout()
                .push(new Stride(VERTEX_STRIDE)
                        .push(new Slot(Type.UNSIGNED_BYTE, 3))
                        .push(new Slot(Type.UNSIGNED_BYTE, 1))
                        .push(new Slot(Type.FLOAT, 2)))
                .push(new Stride(INSTANCE_STRIDE)
                        .push(new Slot(Type.UNSIGNED_BYTE, 3).setDivisor(1))
                        .push(new Slot(Type.UNSIGNED_BYTE, 1).setNormalize(true).setDivisor(1))
                        .push(new Slot(Type.UNSIGNED_SHORT, 2).setDivisor(1)));

        vao = new VAO(true, layout, indices, vertices, instances);
        update(scene);
    }

    void update(PipeScene scene) {
        Preconditions.checkState(!closed, "Mesh is closed.");
        Preconditions.checkNotNull(scene);
        Preconditions.checkArgument(scene.material == material,
                "Material changes require a new mesh.");

        int[] counts = new int[batches.length];
        int[] cursors = new int[batches.length];
        for (PipeScene.Block block : scene.blocks) {
            counts[block.cell().topology()]++;
        }

        int firstInstance = 0;
        for (int topology = 0; topology < batches.length; topology++) {
            Batch batch = batches[topology];
            batches[topology] = new Batch(batch.indexCount(), batch.indexOffset(), counts[topology], firstInstance);
            cursors[topology] = firstInstance;
            firstInstance += counts[topology];
        }

        int instanceBytes = scene.blocks.size() * INSTANCE_STRIDE;
        ByteBuffer data = MemoryUtil.memAlloc(instanceBytes).order(ByteOrder.nativeOrder());
        try {
            for (PipeScene.Block block : scene.blocks) {
                data.position(cursors[block.cell().topology()]++ * INSTANCE_STRIDE);
                data.put((byte) block.cell().x()).put((byte) block.cell().y()).put((byte) block.cell().z());
                data.put((byte) Math.round(block.brightness() * 255));
                data.putShort((short) (block.lightmap() & 0xFFFF));
                data.putShort((short) (block.lightmap() >>> 16));
            }
            if (instanceBytes > instanceCapacity) {
                instanceCapacity = Integer.highestOneBit(instanceBytes - 1) << 1;
            }
            instances.alloc(instanceCapacity, BufferUploadHint.DYNAMIC_DRAW);
            if (instanceBytes != 0) {
                instances.uploadBySubData(0, data.clear());
            }
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    int draw() {
        Preconditions.checkState(!closed, "Mesh is closed.");

        vao.bind();
        int drawCalls = 0;
        for (Batch batch : batches) {
            if (batch.instanceCount() == 0) {
                continue;
            }

            GL42.glDrawElementsInstancedBaseInstance(
                    GL11.GL_TRIANGLES,
                    batch.indexCount(),
                    GL11.GL_UNSIGNED_SHORT,
                    batch.indexOffset(),
                    batch.instanceCount(),
                    batch.firstInstance());

            drawCalls++;
        }

        return drawCalls;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        vao.disposeManually();
        instanceBuffer.disposeManually();
        indexBuffer.disposeManually();
        vertexBuffer.disposeManually();
    }
}
