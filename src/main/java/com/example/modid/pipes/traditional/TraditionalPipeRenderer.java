package com.example.modid.pipes.traditional;

import com.example.modid.benchmark.Benchmark;
import com.example.modid.benchmark.BenchmarkStatistics;
import com.example.modid.pipes.PipeBenchmark;
import com.example.modid.pipes.PipeFrame;
import com.example.modid.pipes.PipeRenderer;
import com.example.modid.pipes.PipeScene;
import com.google.common.base.Preconditions;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;

public final class TraditionalPipeRenderer implements PipeRenderer {

    private final TraditionalPipeShader shader;
    private final TraditionalPipeMesh mesh;
    private boolean closed;

    public TraditionalPipeRenderer(PipeScene scene) {
        Preconditions.checkNotNull(scene);

        shader = new TraditionalPipeShader();
        mesh = new TraditionalPipeMesh(scene);
    }

    @Override
    public BenchmarkStatistics benchmarkStatistics() {
        return PipeBenchmark.TRADITIONAL;
    }

    @Override
    public void update(PipeScene scene) {
        Preconditions.checkState(!closed, "Renderer is closed.");
        Preconditions.checkNotNull(scene);

        mesh.update(scene);
    }

    @Override
    public void render(PipeFrame frame) {
        Preconditions.checkState(!closed, "Renderer is closed.");
        Preconditions.checkNotNull(frame);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.bindTexture(frame.lightmap.getGlTextureId());
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        shader.bind(frame);
        int drawCalls = mesh.draw();
        Benchmark.recordStatistic(PipeBenchmark.PROJECT, PipeBenchmark.TRADITIONAL, PipeBenchmark.DRAW_CALLS, drawCalls);
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        shader.close();
        mesh.close();
    }
}
