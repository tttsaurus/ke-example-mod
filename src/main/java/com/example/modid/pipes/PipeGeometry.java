package com.example.modid.pipes;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import net.minecraft.util.EnumFacing;

import java.util.stream.IntStream;

public final class PipeGeometry {

    public record Vertex(float x, float y, float z) {

        public Vertex {
            Preconditions.checkArgument(x >= 0 && x <= 1 && y >= 0 && y <= 1 && z >= 0 && z <= 1,
                    "Vertex coordinates must be within the block.");
        }
    }

    public record Quad(EnumFacing face, ImmutableList<Vertex> vertices) {

        public Quad {
            Preconditions.checkNotNull(face);
            Preconditions.checkNotNull(vertices);
            Preconditions.checkArgument(vertices.size() == 4);
        }
    }

    private static final float[] CUTS = {0, 5 / 16f, 11 / 16f, 1};
    private static final ImmutableList<ImmutableList<Quad>> TOPOLOGIES = IntStream
            .range(0, PipeWorld.TOPOLOGY_COUNT)
            .mapToObj(PipeGeometry::build)
            .collect(ImmutableList.toImmutableList());

    private PipeGeometry() {
    }

    public static ImmutableList<Quad> get(int topology) {
        return TOPOLOGIES.get(Preconditions.checkElementIndex(topology, TOPOLOGIES.size()));
    }

    private static ImmutableList<Quad> build(int topology) {
        boolean[] occupied = new boolean[27];
        occupied[13] = true;
        for (EnumFacing face : EnumFacing.VALUES) {
            if ((topology & (1 << face.getIndex())) != 0) {
                occupied[((1 + face.getXOffset()) * 3 + 1 + face.getYOffset()) * 3 + 1 + face.getZOffset()] = true;
            }
        }

        ImmutableList.Builder<Quad> quads = ImmutableList.builder();
        for (int x = 0; x < 3; x++) {
            for (int y = 0; y < 3; y++) {
                for (int z = 0; z < 3; z++) {
                    if (!occupied[(x * 3 + y) * 3 + z]) {
                        continue;
                    }

                    float x0 = CUTS[x], y0 = CUTS[y], z0 = CUTS[z];
                    float x1 = CUTS[x + 1], y1 = CUTS[y + 1], z1 = CUTS[z + 1];
                    for (EnumFacing face : EnumFacing.VALUES) {
                        int nx = x + face.getXOffset();
                        int ny = y + face.getYOffset();
                        int nz = z + face.getZOffset();
                        if (nx < 0 || ny < 0 || nz < 0 || nx >= 3 || ny >= 3 || nz >= 3 || occupied[(nx * 3 + ny) * 3 + nz]) {
                            continue;
                        }

                        ImmutableList<Vertex> vertices = switch (face) {
                            case DOWN -> ImmutableList.of(
                                    new Vertex(x0, y0, z0),
                                    new Vertex(x1, y0, z0),
                                    new Vertex(x1, y0, z1),
                                    new Vertex(x0, y0, z1));
                            case UP -> ImmutableList.of(
                                    new Vertex(x0, y1, z1),
                                    new Vertex(x1, y1, z1),
                                    new Vertex(x1, y1, z0),
                                    new Vertex(x0, y1, z0));
                            case NORTH -> ImmutableList.of(
                                    new Vertex(x1, y0, z0),
                                    new Vertex(x0, y0, z0),
                                    new Vertex(x0, y1, z0),
                                    new Vertex(x1, y1, z0));
                            case SOUTH -> ImmutableList.of(
                                    new Vertex(x0, y0, z1),
                                    new Vertex(x1, y0, z1),
                                    new Vertex(x1, y1, z1),
                                    new Vertex(x0, y1, z1));
                            case WEST -> ImmutableList.of(
                                    new Vertex(x0, y0, z0),
                                    new Vertex(x0, y0, z1),
                                    new Vertex(x0, y1, z1),
                                    new Vertex(x0, y1, z0));
                            case EAST -> ImmutableList.of(
                                    new Vertex(x1, y0, z1),
                                    new Vertex(x1, y0, z0),
                                    new Vertex(x1, y1, z0),
                                    new Vertex(x1, y1, z1));
                        };

                        quads.add(new Quad(face, vertices));
                    }
                }
            }
        }

        return quads.build();
    }
}
