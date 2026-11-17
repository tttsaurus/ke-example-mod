package com.example.modid.pipes;

import com.example.modid.benchmark.BenchmarkMetric;
import com.example.modid.benchmark.BenchmarkProject;
import com.example.modid.benchmark.BenchmarkStatistic;
import com.example.modid.benchmark.BenchmarkStatistics;
import com.google.common.collect.ImmutableList;

public final class PipeBenchmark {

    public static final BenchmarkMetric RENDER_CPU = new BenchmarkMetric(
            "pipe.render.cpu",
            "PipeRenderer#render CPU",
            "ms",
            BenchmarkMetric.Kind.CPU_DURATION,
            BenchmarkMetric.Sampling.FRAME,
            BenchmarkMetric.RollingAggregation.MEAN);

    public static final BenchmarkMetric RENDER_GPU = new BenchmarkMetric(
            "pipe.render.gpu",
            "PipeRenderer#render GPU",
            "ms",
            BenchmarkMetric.Kind.GPU_DURATION,
            BenchmarkMetric.Sampling.FRAME,
            BenchmarkMetric.RollingAggregation.MEAN);

    public static final BenchmarkMetric UPDATE = new BenchmarkMetric(
            "pipe.update",
            "PipeRenderer#update CPU",
            "ms",
            BenchmarkMetric.Kind.CPU_DURATION,
            BenchmarkMetric.Sampling.EVENT,
            BenchmarkMetric.RollingAggregation.NONE);

    public static final BenchmarkStatistic DRAW_CALLS = new BenchmarkStatistic(
            "pipe.draw_calls", "Draw calls", "calls/frame", BenchmarkStatistic.Kind.COUNTER);
    public static final BenchmarkStatistic VERTEX_INVOCATIONS = new BenchmarkStatistic(
            "pipe.vertex_invocations", "Vertex shader invocations", "invocations/frame", BenchmarkStatistic.Kind.VERTEX_SHADER_INVOCATIONS);

    public static final BenchmarkStatistics INSTANCED = new BenchmarkStatistics(
            "pipe.instanced", "Instanced pipe renderer", ImmutableList.of(DRAW_CALLS, VERTEX_INVOCATIONS));
    public static final BenchmarkStatistics GPU_DRIVEN = new BenchmarkStatistics(
            "pipe.gpudriven", "GPU-driven pipe renderer", ImmutableList.of(DRAW_CALLS, VERTEX_INVOCATIONS));
    public static final BenchmarkStatistics TRADITIONAL = new BenchmarkStatistics(
            "pipe.traditional", "Traditional pipe renderer", ImmutableList.of(DRAW_CALLS, VERTEX_INVOCATIONS));

    public static final BenchmarkProject PROJECT = new BenchmarkProject(
            "pipe-rendering",
            "Pipe rendering",
            ImmutableList.of(RENDER_CPU, RENDER_GPU, UPDATE),
            ImmutableList.of(INSTANCED, GPU_DRIVEN, TRADITIONAL),
            PipeRenderEvents::benchmarkStatistics);

    private PipeBenchmark() {
    }
}
