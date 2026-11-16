package com.example.modid.pipes;

import com.example.modid.benchmark.BenchmarkMetric;
import com.example.modid.benchmark.BenchmarkProject;
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

    public static final BenchmarkProject PROJECT = new BenchmarkProject(
            "pipe-rendering",
            "Pipe rendering",
            ImmutableList.of(RENDER_CPU, RENDER_GPU, UPDATE));

    private PipeBenchmark() {
    }
}
