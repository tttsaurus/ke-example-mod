package com.example.modid.benchmark;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.Set;

public record BenchmarkStatistics(String id, String name, ImmutableList<BenchmarkStatistic> statistics) {

    public BenchmarkStatistics {
        Preconditions.checkArgument(Preconditions.checkNotNull(id).matches("[a-z][a-z0-9_.]*"));
        Preconditions.checkArgument(!Preconditions.checkNotNull(name).isBlank());
        Preconditions.checkNotNull(statistics);
        Preconditions.checkArgument(!statistics.isEmpty(), "A statistics group must not be empty.");

        Set<String> ids = new HashSet<>();
        int queries = 0;
        for (BenchmarkStatistic statistic : statistics) {
            Preconditions.checkArgument(ids.add(statistic.id()), "Duplicate statistic: %s", statistic.id());

            if (statistic.kind() == BenchmarkStatistic.Kind.VERTEX_SHADER_INVOCATIONS) {
                queries++;
            }
        }
        Preconditions.checkArgument(queries <= 1, "A group can contain only one vertex invocation query.");
    }
}
