package com.example.modid.pipes;

import com.example.modid.render.BlockMaterial;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.BlockPos;

public final class PipeScene {

    public record Block(PipeWorld.Cell cell, int lightmap, float brightness) {

        public Block {
            Preconditions.checkNotNull(cell);
            Preconditions.checkArgument(brightness >= 0 && brightness <= 1);
        }
    }

    public final WorldClient world;
    public final BlockPos origin;
    public final PipeWorld network;
    public final BlockMaterial material;
    public final ImmutableList<Block> blocks;

    public PipeScene(WorldClient world, BlockPos origin, PipeWorld network, BlockMaterial material) {
        this.world = Preconditions.checkNotNull(world);
        this.origin = Preconditions.checkNotNull(origin).toImmutable();
        this.network = Preconditions.checkNotNull(network);
        this.material = Preconditions.checkNotNull(material);

        Preconditions.checkArgument(this.origin.getY() >= 0 && this.origin.getY() <= world.getHeight() - PipeWorld.SIZE,
                "Pipe scene must fit within the world's height.");
        Preconditions.checkArgument(world.isAreaLoaded(this.origin, this.origin.add(PipeWorld.SIZE - 1, PipeWorld.SIZE - 1, PipeWorld.SIZE - 1)),
                "Pipe scene chunks must be loaded.");

        ImmutableList.Builder<Block> blocks = ImmutableList.builderWithExpectedSize(network.cells().size());
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        for (PipeWorld.Cell cell : network.cells()) {
            position.setPos(this.origin.getX() + cell.x(), this.origin.getY() + cell.y(), this.origin.getZ() + cell.z());
            int lightmap = world.getCombinedLight(position, material.state.getLightValue(world, position));
            float brightness = world.getBlockState(position).getAmbientOcclusionLightValue();
            blocks.add(new Block(cell, lightmap, brightness));
        }

        this.blocks = blocks.build();
    }
}
