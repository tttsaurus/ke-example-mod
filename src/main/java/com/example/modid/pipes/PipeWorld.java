package com.example.modid.pipes;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import net.minecraft.util.EnumFacing;

import java.util.random.RandomGenerator;

public final class PipeWorld {

    public static final int SIZE = 65;
    public static final int TOPOLOGY_COUNT = 64;

    private static final int CELL_SIZE = 4;
    private static final int CELLS_PER_AXIS = (SIZE - 1) / CELL_SIZE;
    private static final boolean[] FRAME = new boolean[SIZE * SIZE * SIZE];

    static {
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    int boundaries = (x % CELL_SIZE == 0 ? 1 : 0) + (y % CELL_SIZE == 0 ? 1 : 0) + (z % CELL_SIZE == 0 ? 1 : 0);
                    if (boundaries >= 2) {
                        FRAME[(y * SIZE + z) * SIZE + x] = true;
                    }
                }
            }
        }
    }

    public record Cell(int x, int y, int z, int topology) {

        public Cell {
            Preconditions.checkElementIndex(x, SIZE);
            Preconditions.checkElementIndex(y, SIZE);
            Preconditions.checkElementIndex(z, SIZE);
            Preconditions.checkElementIndex(topology, TOPOLOGY_COUNT);
        }
    }

    private final boolean[] occupied;
    private final ImmutableList<Cell> cells;

    private PipeWorld(boolean[] occupied) {
        this.occupied = Preconditions.checkNotNull(occupied);
        Preconditions.checkArgument(occupied.length == SIZE * SIZE * SIZE);

        ImmutableList.Builder<Cell> result = ImmutableList.builder();
        for (int y = 0; y < SIZE; y++) {
            for (int z = 0; z < SIZE; z++) {
                for (int x = 0; x < SIZE; x++) {
                    if (contains(x, y, z)) {
                        result.add(new Cell(x, y, z, topologyAt(x, y, z)));
                    }
                }
            }
        }
        cells = result.build();
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < SIZE && y < SIZE && z < SIZE && occupied[(y * SIZE + z) * SIZE + x];
    }

    public int topologyAt(int x, int y, int z) {
        int mask = 0;
        for (EnumFacing direction : EnumFacing.VALUES) {
            if (contains(x + direction.getXOffset(), y + direction.getYOffset(), z + direction.getZOffset())) {
                mask |= (1 << direction.getIndex());
            }
        }
        return mask;
    }

    public ImmutableList<Cell> cells() {
        return cells;
    }

    public static PipeWorld generate(RandomGenerator random) {
        Preconditions.checkNotNull(random);

        boolean[] occupied = FRAME.clone();
        int[] topologies = new int[CELLS_PER_AXIS * CELLS_PER_AXIS * CELLS_PER_AXIS];
        for (int i = 0; i < topologies.length; i++) {
            topologies[i] = i % TOPOLOGY_COUNT;
        }

        for (int i = topologies.length - 1; i > 0; i--) {
            int index = random.nextInt(i + 1);
            int topology = topologies[i];
            topologies[i] = topologies[index];
            topologies[index] = topology;
        }

        for (int i = 0; i < topologies.length; i++) {
            int mask = topologies[i];
            int x = i % CELLS_PER_AXIS * CELL_SIZE + CELL_SIZE / 2;
            int y = i / CELLS_PER_AXIS % CELLS_PER_AXIS * CELL_SIZE + CELL_SIZE / 2;
            int z = i / (CELLS_PER_AXIS * CELLS_PER_AXIS) * CELL_SIZE + CELL_SIZE / 2;
            occupied[(y * SIZE + z) * SIZE + x] = true;
            EnumFacing attachment = null;
            int connection = mask == 0 ? -1 : random.nextInt(Integer.bitCount(mask));
            for (EnumFacing direction : EnumFacing.VALUES) {
                if ((mask & (1 << direction.getIndex())) != 0) {
                    for (int distance = 1; distance < CELL_SIZE / 2; distance++) {
                        int armX = x + distance * direction.getXOffset();
                        int armY = y + distance * direction.getYOffset();
                        int armZ = z + distance * direction.getZOffset();
                        occupied[(armY * SIZE + armZ) * SIZE + armX] = true;
                    }
                    if (connection-- == 0) {
                        attachment = direction;
                    }
                }
            }
            if (attachment != null) {
                joinFrame(occupied, x, y, z, attachment, random);
            }
        }
        return new PipeWorld(occupied);
    }

    private static void joinFrame(boolean[] occupied, int x, int y, int z, EnumFacing direction, RandomGenerator random) {
        int frameX = x + (random.nextBoolean() ? CELL_SIZE / 2 : -CELL_SIZE / 2);
        int frameY = y + (random.nextBoolean() ? CELL_SIZE / 2 : -CELL_SIZE / 2);
        int frameZ = z + (random.nextBoolean() ? CELL_SIZE / 2 : -CELL_SIZE / 2);
        x += direction.getXOffset() * CELL_SIZE / 2;
        y += direction.getYOffset() * CELL_SIZE / 2;
        z += direction.getZOffset() * CELL_SIZE / 2;
        occupied[(y * SIZE + z) * SIZE + x] = true;
        if (direction.getXOffset() == 0) {
            while (x != frameX) {
                x += Integer.compare(frameX, x);
                occupied[(y * SIZE + z) * SIZE + x] = true;
            }
        }
        if (direction.getYOffset() == 0) {
            while (y != frameY) {
                y += Integer.compare(frameY, y);
                occupied[(y * SIZE + z) * SIZE + x] = true;
            }
        }
        if (direction.getZOffset() == 0) {
            while (z != frameZ) {
                z += Integer.compare(frameZ, z);
                occupied[(y * SIZE + z) * SIZE + x] = true;
            }
        }
    }
}
