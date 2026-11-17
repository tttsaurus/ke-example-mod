package com.example.modid.benchmark;

import com.google.common.base.Preconditions;
import com.google.gson.stream.JsonWriter;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class BenchmarkStatisticsCollector {

    private record Frame(int index, long[] values, int[] counts, int[] requests) {
    }

    private final BenchmarkStatistics group;
    private final int queryIndex;
    private final List<Frame> frames = new ArrayList<>(8192);
    private @Nullable Frame current;

    BenchmarkStatisticsCollector(BenchmarkStatistics group) {
        this.group = group;
        int index = -1;
        for (int i = 0; i < group.statistics().size(); i++) {
            if (group.statistics().get(i).kind() == BenchmarkStatistic.Kind.VERTEX_SHADER_INVOCATIONS) {
                index = i;
            }
        }
        queryIndex = index;
    }

    boolean requiresPipelineQuery() {
        return queryIndex >= 0;
    }

    Benchmark.Sample begin(int frame, @Nullable PipelineStatisticsProfiler profiler) {
        if (current == null || current.index() != frame) {
            int size = group.statistics().size();
            current = new Frame(frame, new long[size], new int[size], new int[size]);
            frames.add(current);
        }
        if (queryIndex < 0) {
            return () -> {};
        }
        current.requests()[queryIndex]++;
        return Preconditions.checkNotNull(profiler).begin(current.values(), current.counts(), queryIndex);
    }

    void record(int frame, BenchmarkStatistic statistic, long value) {
        Preconditions.checkState(current != null && current.index() == frame, "No statistics sample is running.");
        Preconditions.checkArgument(statistic.kind() == BenchmarkStatistic.Kind.COUNTER && value >= 0);

        int index = group.statistics().indexOf(statistic);
        Preconditions.checkArgument(index >= 0, "Unknown statistic: %s", statistic.id());

        current.values()[index] += value;
        current.counts()[index]++;
    }

    void write(JsonWriter writer, int completedFrames) throws IOException {
        int frameCount = 0;
        for (Frame frame : frames) {
            if (frame.index() < completedFrames) {
                frameCount++;
            }
        }
        if (frameCount == 0) {
            return;
        }

        writer.beginObject();
        writer.name("id").value(group.id());
        writer.name("name").value(group.name());
        writer.name("frames").value(frameCount);
        writer.name("frameSampling").value("observed_completed_frames");
        writer.name("statistics").beginArray();
        for (int i = 0; i < group.statistics().size(); i++) {
            BenchmarkStatistic statistic = group.statistics().get(i);
            long total = 0;
            int samples = 0;
            double mean = 0;
            double squaredDifferences = 0;
            for (Frame frame : frames) {
                if (frame.index() < completedFrames && frame.counts()[i] > 0
                        && (frame.requests()[i] == 0 || frame.counts()[i] == frame.requests()[i])) {
                    total += frame.values()[i];
                    samples++;
                    double delta = frame.values()[i] - mean;
                    mean += delta / samples;
                    squaredDifferences += delta * (frame.values()[i] - mean);
                }
            }
            writer.beginObject();
            writer.name("id").value(statistic.id());
            writer.name("name").value(statistic.name());
            writer.name("unit").value(statistic.unit());
            writer.name("source").value(statistic.kind().name().toLowerCase(Locale.ROOT));
            writer.name("aggregation").value("mean_per_frame");
            writer.name("total").value(total);
            writer.name("frames").value(samples);
            writer.name("missingFrames").value(frameCount - samples);
            writer.name("average");
            if (samples == 0) {
                writer.nullValue();
            } else {
                writer.value((double) total / samples);
            }
            writer.name("standardDeviation");
            if (samples == 0) {
                writer.nullValue();
            } else {
                writer.value(Math.sqrt(squaredDifferences / samples));
            }
            writer.endObject();
        }
        writer.endArray();
        writer.endObject();
    }
}
