package com.example.modid.benchmark;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.Set;

public record BenchmarkProject(String id, String name, ImmutableList<BenchmarkMetric> metrics) {

    public BenchmarkProject {
        Preconditions.checkArgument(Preconditions.checkNotNull(id).matches("[a-z][a-z0-9_-]*"));
        Preconditions.checkArgument(!Preconditions.checkNotNull(name).isBlank());
        Preconditions.checkNotNull(metrics);

        Set<String> ids = new HashSet<>();
        ids.add("fps");
        for (BenchmarkMetric metric : metrics) {
            Preconditions.checkArgument(ids.add(metric.id()), "Duplicate metric: %s", metric.id());
        }
    }
}
