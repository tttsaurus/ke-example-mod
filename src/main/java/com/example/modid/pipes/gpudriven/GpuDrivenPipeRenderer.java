package com.example.modid.pipes.gpudriven;

import com.cleanroommc.kirino.gl.buffer.GLBuffer;
import com.cleanroommc.kirino.gl.buffer.meta.BufferUploadHint;
import com.cleanroommc.kirino.gl.buffer.view.EBOView;
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
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public final class GpuDrivenPipeRenderer implements PipeRenderer {

    static final int CONNECTION_GROUP_COUNT = 7;

    private static final int BLOCK_STRIDE = 2 * Integer.BYTES;
    private static final int BLOCK_TEXTURE_UNIT = 2;
    private static final int QUADS_PER_BATCH = 64;
    private static final int INDICES_PER_BATCH = QUADS_PER_BATCH * 5;

    private final BlockMaterial material;
    private final GLBuffer indexBuffer;
    private final GLBuffer blockBuffer;
    private final TBOView blocks;
    private final GLTexture blockTexture;
    private final TextureBufferAccessor blockTextureAccessor;
    private final VAO vao;
    private final GpuDrivenPipeShader shader;
    private int blockCapacity = BLOCK_STRIDE;
    private int batchCount;
    private boolean closed;

    public GpuDrivenPipeRenderer(PipeScene scene) {
        Preconditions.checkNotNull(scene);

        material = scene.material;
        shader = new GpuDrivenPipeShader(material, BLOCK_TEXTURE_UNIT);
        indexBuffer = new GLBuffer(true);
        EBOView indices = new EBOView(indexBuffer, true);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = stack.malloc(INDICES_PER_BATCH * Short.BYTES);
            for (int quad = 0; quad < QUADS_PER_BATCH; quad++) {
                int firstVertex = quad * 4;
                data.putShort((short) (firstVertex + 1));
                data.putShort((short) (firstVertex + 2));
                data.putShort((short) firstVertex);
                data.putShort((short) (firstVertex + 3));
                data.putShort((short) 0xFFFF);
            }
            indices.uploadDirectly(data.flip(), BufferUploadHint.STATIC_DRAW);
        }

        blockBuffer = new GLBuffer(true);
        blocks = new TBOView(blockBuffer, true);
        blockTexture = GLTexture.newDsaTexBuffer();
        blockTextureAccessor = new TextureBufferAccessor(true, blockTexture);
        vao = new VAO(true, new AttributeLayout(), indices, (VBOView[]) null);

        update(scene);
        blockTextureAccessor.texBuffer(GL30.GL_RG32UI, blockBuffer.bufferID);
    }

    @Override
    public void update(PipeScene scene) {
        Preconditions.checkState(!closed, "Renderer is closed.");
        Preconditions.checkNotNull(scene);
        Preconditions.checkArgument(scene.material == material, "Material changes require a new renderer.");

        int[] counts = new int[CONNECTION_GROUP_COUNT];
        int[] cursors = new int[CONNECTION_GROUP_COUNT];
        for (PipeScene.Block block : scene.blocks) {
            counts[Integer.bitCount(block.cell().topology())]++;
        }

        int blockBytes = scene.blocks.size() * BLOCK_STRIDE;
        ByteBuffer data = MemoryUtil.memAlloc(blockBytes).order(ByteOrder.nativeOrder());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer quadEnds = stack.mallocInt(CONNECTION_GROUP_COUNT);
            IntBuffer blockOffsets = stack.mallocInt(CONNECTION_GROUP_COUNT);
            int firstBlock = 0;
            int quads = 0;
            for (int connections = 0; connections < counts.length; connections++) {
                cursors[connections] = firstBlock;
                blockOffsets.put(firstBlock);
                firstBlock += counts[connections];
                quads += counts[connections] * (6 + 3 * connections);
                quadEnds.put(quads);
            }

            for (PipeScene.Block block : scene.blocks) {
                PipeWorld.Cell cell = block.cell();
                int offset = cursors[Integer.bitCount(cell.topology())]++ * BLOCK_STRIDE;
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
            shader.update(quadEnds.flip(), blockOffsets.flip());
            batchCount = (quads + QUADS_PER_BATCH - 1) / QUADS_PER_BATCH;
        } finally {
            MemoryUtil.memFree(data);
        }
    }

    @Override
    public void render(PipeFrame frame) {
        Preconditions.checkState(!closed, "Renderer is closed.");
        Preconditions.checkNotNull(frame);

        if (batchCount == 0) {
            return;
        }

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.bindTexture(frame.lightmap.getGlTextureId());
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        blockTextureAccessor.bindUnit(BLOCK_TEXTURE_UNIT);
        shader.bind(frame);
        vao.bind();
        GL11.glEnable(GL43.GL_PRIMITIVE_RESTART_FIXED_INDEX);
        GL31.glDrawElementsInstanced(GL11.GL_TRIANGLE_STRIP, INDICES_PER_BATCH, GL11.GL_UNSIGNED_SHORT, 0L, batchCount);
        GL11.glDisable(GL43.GL_PRIMITIVE_RESTART_FIXED_INDEX);
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
        indexBuffer.disposeManually();
        blockTexture.disposeManually();
        blockBuffer.disposeManually();
    }
}
