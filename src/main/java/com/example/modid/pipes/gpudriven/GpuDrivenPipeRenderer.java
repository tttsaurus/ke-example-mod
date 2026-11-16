package com.example.modid.pipes.gpudriven;

import com.cleanroommc.kirino.gl.buffer.GLBuffer;
import com.cleanroommc.kirino.gl.buffer.meta.BufferUploadHint;
import com.cleanroommc.kirino.gl.buffer.view.TBOView;
import com.cleanroommc.kirino.gl.buffer.view.VBOView;
import com.cleanroommc.kirino.gl.texture.GLTexture;
import com.cleanroommc.kirino.gl.texture.accessor.TextureBufferAccessor;
import com.cleanroommc.kirino.gl.vao.VAO;
import com.cleanroommc.kirino.gl.vao.attribute.AttributeLayout;
import com.example.modid.pipes.PipeFrame;
import com.example.modid.pipes.PipeRenderer;
import com.example.modid.pipes.PipeScene;
import com.example.modid.pipes.PipeWorld;
import com.example.modid.render.BlockMaterial;
import com.google.common.base.Preconditions;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public final class GpuDrivenPipeRenderer implements PipeRenderer {

    private static final int BLOCK_STRIDE = 2 * Integer.BYTES;
    private static final int BLOCK_TEXTURE_UNIT = 2;

    private final BlockMaterial material;
    private final GLBuffer blockBuffer;
    private final TBOView blocks;
    private final GLTexture blockTexture;
    private final TextureBufferAccessor blockTextureAccessor;
    private final VAO vao;
    private final GpuDrivenPipeShader shader;
    private int blockCapacity = BLOCK_STRIDE;
    private int vertexCount;
    private boolean closed;

    public GpuDrivenPipeRenderer(PipeScene scene) {
        Preconditions.checkNotNull(scene);

        material = scene.material;
        shader = new GpuDrivenPipeShader(material, BLOCK_TEXTURE_UNIT);
        blockBuffer = new GLBuffer(true);
        blocks = new TBOView(blockBuffer, true);
        blockTexture = GLTexture.newDsaTexBuffer();
        blockTextureAccessor = new TextureBufferAccessor(true, blockTexture);
        vao = new VAO(true, new AttributeLayout(), null, (VBOView[]) null);

        update(scene);
        blockTextureAccessor.texBuffer(GL30.GL_RG32UI, blockBuffer.bufferID);
    }

    @Override
    public void update(PipeScene scene) {
        Preconditions.checkState(!closed, "Renderer is closed.");
        Preconditions.checkNotNull(scene);
        Preconditions.checkArgument(scene.material == material, "Material changes require a new renderer.");

        int[] counts = new int[PipeWorld.TOPOLOGY_COUNT];
        int[] cursors = new int[PipeWorld.TOPOLOGY_COUNT];
        for (PipeScene.Block block : scene.blocks) {
            counts[block.cell().topology()]++;
        }

        int blockBytes = scene.blocks.size() * BLOCK_STRIDE;
        ByteBuffer data = MemoryUtil.memAlloc(blockBytes).order(ByteOrder.nativeOrder());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer vertexEnds = stack.mallocInt(PipeWorld.TOPOLOGY_COUNT);
            IntBuffer blockOffsets = stack.mallocInt(PipeWorld.TOPOLOGY_COUNT);
            int firstBlock = 0;
            int vertices = 0;
            for (int topology = 0; topology < counts.length; topology++) {
                cursors[topology] = firstBlock;
                blockOffsets.put(firstBlock);
                firstBlock += counts[topology];
                vertices += counts[topology] * (6 + 3 * Integer.bitCount(topology)) * 6;
                vertexEnds.put(vertices);
            }

            for (PipeScene.Block block : scene.blocks) {
                PipeWorld.Cell cell = block.cell();
                int offset = cursors[cell.topology()]++ * BLOCK_STRIDE;
                data.putInt(offset, cell.x() | cell.y() << 8 | cell.z() << 16 | cell.topology() << 24);
                data.putInt(offset + Integer.BYTES, (block.lightmap() & 0xFF)
                        | (block.lightmap() >>> 16 & 0xFF) << 8
                        | Math.round(block.brightness() * 255) << 16);
            }

            if (blockBytes > blockCapacity) {
                blockCapacity = Integer.highestOneBit(blockBytes - 1) << 1;
            }
            blocks.alloc(blockCapacity, BufferUploadHint.DYNAMIC_DRAW);
            if (blockBytes != 0) {
                blocks.uploadBySubData(0, data);
            }
            shader.update(vertexEnds.flip(), blockOffsets.flip());
            vertexCount = vertices;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Override
    public void render(PipeFrame frame) {
        Preconditions.checkState(!closed, "Renderer is closed.");
        Preconditions.checkNotNull(frame);

        if (vertexCount == 0) {
            return;
        }

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.bindTexture(frame.lightmap.getGlTextureId());
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        blockTextureAccessor.bindUnit(BLOCK_TEXTURE_UNIT);
        shader.bind(frame);
        vao.bind();
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertexCount);
        GL45.glBindTextureUnit(BLOCK_TEXTURE_UNIT, 0);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        shader.close();
        vao.disposeManually();
        blockTexture.disposeManually();
        blockBuffer.disposeManually();
    }
}
