package com.example.modid.benchmark;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public record BenchmarkProject(String id, String name, ImmutableList<BenchmarkMetric> metrics,
                               ImmutableList<BenchmarkStatistics> statistics,
                               Supplier<@Nullable BenchmarkStatistics> activeStatistics) {

    public BenchmarkProject {
        Preconditions.checkArgument(Preconditions.checkNotNull(id).matches("[a-z][a-z0-9_-]*"));
        Preconditions.checkArgument(!Preconditions.checkNotNull(name).isBlank());
        Preconditions.checkNotNull(metrics);
        Preconditions.checkNotNull(statistics);
        Preconditions.checkNotNull(activeStatistics);

        Set<String> ids = new HashSet<>();
        ids.add("fps");
        for (BenchmarkMetric metric : metrics) {
            Preconditions.checkArgument(ids.add(metric.id()), "Duplicate metric: %s", metric.id());

        }
        ids.clear();
        for (BenchmarkStatistics group : statistics) {
            Preconditions.checkArgument(ids.add(group.id()), "Duplicate statistics group: %s", group.id());

        }
    }
}
