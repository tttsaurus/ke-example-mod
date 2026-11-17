package com.example.modid.benchmark;

import com.google.common.base.Preconditions;

public record BenchmarkStatistic(String id, String name, String unit, Kind kind) {

    public enum Kind {
        COUNTER,
        VERTEX_SHADER_INVOCATIONS
    }

    public BenchmarkStatistic {
        Preconditions.checkArgument(Preconditions.checkNotNull(id).matches("[a-z][a-z0-9_.]*"));
        Preconditions.checkArgument(!Preconditions.checkNotNull(name).isBlank());
        Preconditions.checkArgument(!Preconditions.checkNotNull(unit).isBlank());
        Preconditions.checkNotNull(kind);
    }
}
