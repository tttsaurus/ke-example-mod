package com.example.modid.pipes;

import com.example.modid.benchmark.BenchmarkStatistics;

public interface PipeRenderer extends AutoCloseable {

    BenchmarkStatistics benchmarkStatistics();

    void update(PipeScene scene);

    void render(PipeFrame frame);

    @Override
    void close();
}
