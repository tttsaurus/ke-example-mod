package com.example.modid.benchmark;

import com.google.common.base.Preconditions;

public record BenchmarkMetric(String id, String name, String unit, Kind kind, Sampling sampling, RollingAggregation rollingAggregation) {

    public enum Kind {
        CPU_DURATION,
        GPU_DURATION,
        GAUGE
    }

    public enum Sampling {
        FRAME,
        EVENT
    }

    public enum RollingAggregation {
        NONE,
        MEAN,
        FRAME_RATE
    }

    public BenchmarkMetric {
        Preconditions.checkArgument(Preconditions.checkNotNull(id).matches("[a-z][a-z0-9_.]*"));
        Preconditions.checkArgument(!Preconditions.checkNotNull(name).isBlank());
        Preconditions.checkArgument(!Preconditions.checkNotNull(unit).isBlank());
        Preconditions.checkNotNull(kind);
        Preconditions.checkNotNull(sampling);
        Preconditions.checkNotNull(rollingAggregation);
        Preconditions.checkArgument(kind == Kind.GAUGE || unit.equals("ms"));
        Preconditions.checkArgument((sampling == Sampling.EVENT) == (rollingAggregation == RollingAggregation.NONE));
        Preconditions.checkArgument(rollingAggregation != RollingAggregation.FRAME_RATE || kind == Kind.GAUGE && unit.equals("fps"));
    }
}
