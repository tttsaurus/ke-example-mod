package com.example.modid.render;

import com.google.common.base.Preconditions;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class BlockMaterial {

    public final IBlockState state;
    private final float[][][] faceUvs = new float[6][4][2];

    public BlockMaterial(Minecraft minecraft, IBlockAccess world, IBlockState state) {
        Preconditions.checkNotNull(minecraft);
        Preconditions.checkNotNull(world);
        Preconditions.checkNotNull(state);

        this.state = state;

        BlockRendererDispatcher dispatcher = minecraft.getBlockRendererDispatcher();
        IBakedModel model = dispatcher.getModelForState(state);

        BufferBuilder builder = new BufferBuilder(256);
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        dispatcher.getBlockModelRenderer().renderModel(world, model, state, BlockPos.ORIGIN, builder, false);
        builder.finishDrawing();

        Preconditions.checkArgument(builder.getVertexCount() == 24,
                "Expected a six-quad full-block model.");

        ByteBuffer data = builder.getByteBuffer().duplicate().order(ByteOrder.nativeOrder());
        int stride = builder.getVertexFormat().getSize();
        int uvOffset = builder.getVertexFormat().getUvOffsetById(0);
        int faces = 0;
        for (int quad = 0; quad < 6; quad++) {
            int offset = quad * 4 * stride;
            EnumFacing candidateFace = null;
            for (EnumFacing candidate : EnumFacing.VALUES) {
                int component = candidate.getXOffset() != 0 ? 0 : candidate.getYOffset() != 0 ? 4 : 8;
                float plane = candidate.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1 : 0;
                boolean matches = true;
                for (int vertex = 0; vertex < 4; vertex++) {
                    matches &= Math.abs(data.getFloat(offset + vertex * stride + component) - plane) < 0.0001f;
                }
                if (matches) {
                    candidateFace = candidate;
                    break;
                }
            }

            EnumFacing face = Preconditions.checkNotNull(candidateFace, "Expected axis-aligned full-block faces.");
            int index = face.getIndex();
            Preconditions.checkArgument((faces & (1 << index)) == 0, "Duplicate model face: %s", face);
            faces |= 1 << index;
            int corners = 0;
            for (int vertex = 0; vertex < 4; vertex++) {
                int position = offset + vertex * stride;
                float x = data.getFloat(position);
                float y = data.getFloat(position + 4);
                float z = data.getFloat(position + 8);
                float s = face.getAxis() == EnumFacing.Axis.X ? z : x;
                float t = face.getAxis() == EnumFacing.Axis.Y ? z : y;

                Preconditions.checkArgument((Math.abs(s) < 0.0001f || Math.abs(s - 1) < 0.0001f)
                        && (Math.abs(t) < 0.0001f || Math.abs(t - 1) < 0.0001f),
                        "Expected full-block corners.");

                int corner = (s > 0.5f ? 1 : 0) | (t > 0.5f ? 2 : 0);
                Preconditions.checkArgument((corners & (1 << corner)) == 0,
                        "Duplicate model corner.");

                corners |= 1 << corner;
                faceUvs[index][corner][0] = data.getFloat(position + uvOffset);
                faceUvs[index][corner][1] = data.getFloat(position + uvOffset + 4);
            }
        }
    }

    public void putUV(ByteBuffer target, EnumFacing face, float x, float y, float z) {
        Preconditions.checkNotNull(target);
        Preconditions.checkNotNull(face);
        Preconditions.checkArgument(!target.isReadOnly(), "UV output buffer must be writable.");
        Preconditions.checkArgument(target.remaining() >= 2 * Float.BYTES, "UV output requires space for two floats.");
        Preconditions.checkArgument(x >= 0 && x <= 1 && y >= 0 && y <= 1 && z >= 0 && z <= 1,
                "UV coordinates must be sampled within the block.");

        float s = face.getAxis() == EnumFacing.Axis.X ? z : x;
        float t = face.getAxis() == EnumFacing.Axis.Y ? z : y;
        float[][] uv = faceUvs[face.getIndex()];
        for (int component = 0; component < 2; component++) {
            float lower = uv[0][component] * (1 - s) + uv[1][component] * s;
            float upper = uv[2][component] * (1 - s) + uv[3][component] * s;
            target.putFloat(lower * (1 - t) + upper * t);
        }
    }
}
