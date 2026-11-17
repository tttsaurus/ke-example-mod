package com.example.modid.benchmark;

import com.google.common.base.Preconditions;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonWriter;
import org.apache.commons.lang3.time.StopWatch;
import org.jspecify.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class BenchmarkSession implements AutoCloseable {

    private record Frame(long elapsedNs, long frameNs, double[] values, int[] counts, int[] gpuRequests) {
    }

    private final class DurationSample implements Benchmark.Sample {

        private final int index;
        private final StopWatch timer = new StopWatch();
        private final RemoteryProfiler.SampleName name;

        private DurationSample(int index) {
            this.index = index;
            name = profiler.register(metrics.get(index).id());
        }

        private DurationSample begin() {
            Preconditions.checkState(!timer.isStarted(), "Sample is already running.");

            profiler.begin(name);
            timer.start();
            return this;
        }

        @Override
        public void close() {
            Preconditions.checkState(timer.isStarted(), "Sample is not running.");

            timer.stop();
            double milliseconds = timer.getNanoTime() / 1_000_000.0;
            timer.reset();
            profiler.end();
            values[index] = counts[index] == 0 ? milliseconds : values[index] + milliseconds;
            counts[index]++;
        }
    }

    final BenchmarkProject project;
    final @Nullable BenchmarkStatistics identity;
    final String displayName;
    private final Instant startedAt = Instant.now();
    private final RemoteryProfiler profiler = new RemoteryProfiler();
    private final RemoteryProfiler.SampleName frameSample;
    private final List<BenchmarkMetric> metrics;
    private final Map<BenchmarkMetric, DurationSample> samples = new HashMap<>();
    private final Map<BenchmarkMetric, RemoteryProfiler.GpuTimer> gpuTimers = new HashMap<>();
    private final Map<BenchmarkStatistics, BenchmarkStatisticsCollector> statistics = new LinkedHashMap<>();
    private final List<Frame> frames = new ArrayList<>(8192);
    private final StopWatch elapsed = new StopWatch();
    private final StopWatch frameTimer = new StopWatch();
    private double[] values;
    private int[] counts;
    private int[] gpuRequests;
    private @Nullable PipelineStatisticsProfiler pipelineProfiler;
    private boolean frameOpen;
    private boolean closed;

    BenchmarkSession(BenchmarkProject project, @Nullable BenchmarkStatistics identity) {
        this.project = Preconditions.checkNotNull(project);
        Preconditions.checkArgument(identity == null || project.statistics().contains(identity), "Unknown benchmark identity.");

        this.identity = identity;
        displayName = project.name() + (identity == null ? "" : " / " + identity.name());

        metrics = new ArrayList<>(project.metrics().size() + 1);
        metrics.add(new BenchmarkMetric("fps", "FPS", "fps", BenchmarkMetric.Kind.GAUGE,
                BenchmarkMetric.Sampling.FRAME, BenchmarkMetric.RollingAggregation.FRAME_RATE));
        metrics.addAll(project.metrics());
        values = new double[metrics.size()];
        counts = new int[metrics.size()];
        gpuRequests = new int[metrics.size()];
        for (BenchmarkStatistics group : project.statistics()) {
            statistics.put(group, new BenchmarkStatisticsCollector(group));
        }
        try {
            frameSample = profiler.register("Frame");
            for (int i = 1; i < metrics.size(); i++) {
                if (metrics.get(i).kind() == BenchmarkMetric.Kind.CPU_DURATION) {
                    samples.put(metrics.get(i), new DurationSample(i));
                } else if (metrics.get(i).kind() == BenchmarkMetric.Kind.GPU_DURATION) {
                    gpuTimers.put(metrics.get(i), profiler.registerGpu(metrics.get(i).id(), i));
                }
            }
        } catch (Throwable exception) {
            profiler.close();
            throw exception;
        }
        elapsed.start();
    }

    boolean nextFrame() {
        Preconditions.checkState(!closed, "Session is closed.");

        if (frameOpen) {
            long frameNs = frameTimer.getNanoTime();
            frameTimer.reset();
            frameTimer.start();
            profiler.endFrame();
            values[0] = 1_000_000_000.0 / Math.max(1, frameNs);
            counts[0] = 1;
            frames.add(new Frame(elapsed.getNanoTime(), frameNs, values, counts, gpuRequests));
            frameOpen = false;
        } else {
            frameTimer.start();
        }

        profiler.collectGpuResults();
        if (pipelineProfiler != null) {
            pipelineProfiler.collectResults();
        }
        if (elapsed.getTime(TimeUnit.SECONDS) >= Benchmark.DURATION_SECONDS) {
            return false;
        }

        values = new double[metrics.size()];
        Arrays.fill(values, Double.NaN);
        counts = new int[metrics.size()];
        gpuRequests = new int[metrics.size()];
        profiler.begin(frameSample);
        frameOpen = true;
        return true;
    }

    Benchmark.Sample measure(BenchmarkMetric metric) {
        Preconditions.checkState(frameOpen, "No frame is open.");

        if (metric.kind() == BenchmarkMetric.Kind.GPU_DURATION) {
            RemoteryProfiler.GpuTimer timer = Preconditions.checkNotNull(gpuTimers.get(metric), "Unknown GPU metric: %s", metric.id());

            gpuRequests[metrics.indexOf(metric)]++;
            return timer.begin(values, counts);
        }
        return Preconditions.checkNotNull(samples.get(metric), "Unknown duration metric: %s", metric.id()).begin();
    }

    void record(BenchmarkMetric metric, double value) {
        Preconditions.checkState(frameOpen, "No frame is open.");
        Preconditions.checkArgument(metric.kind() == BenchmarkMetric.Kind.GAUGE && Double.isFinite(value));

        int index = metrics.indexOf(metric);
        Preconditions.checkArgument(index > 0, "Unknown gauge: %s", metric.id());

        values[index] = value;
        counts[index]++;
    }

    boolean frameOpen() {
        return frameOpen;
    }

    Benchmark.Sample measureStatistics(BenchmarkStatistics group) {
        Preconditions.checkState(frameOpen, "No frame is open.");
        BenchmarkStatisticsCollector collector = Preconditions.checkNotNull(statistics.get(group), "Unknown statistics group: %s", group.id());

        if (collector.requiresPipelineQuery() && pipelineProfiler == null) {
            pipelineProfiler = new PipelineStatisticsProfiler();
        }
        return collector.begin(frames.size(), pipelineProfiler);
    }

    void recordStatistic(BenchmarkStatistics group, BenchmarkStatistic statistic, long value) {
        Preconditions.checkState(frameOpen, "No frame is open.");
        BenchmarkStatisticsCollector collector = Preconditions.checkNotNull(statistics.get(group), "Unknown statistics group: %s", group.id());

        collector.record(frames.size(), statistic, value);
    }

    int secondsRemaining() {
        return Math.max(0, Benchmark.DURATION_SECONDS - (int) elapsed.getTime(TimeUnit.SECONDS));
    }

    Path write(Path directory, String reason) throws IOException {
        Preconditions.checkState(closed, "Close the session before saving.");

        Files.createDirectories(directory);
        String timestamp = DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss").withZone(ZoneOffset.UTC).format(startedAt);
        String filename = project.id() + "-" + timestamp + "-" + UUID.randomUUID().toString().substring(0, 8) + ".jsonl";
        Path destination = directory.resolve(filename);
        Path temporary = directory.resolve(filename + ".part");

        try (BufferedWriter output = Files.newBufferedWriter(temporary); JsonWriter writer = new JsonWriter(output)) {
            writer.setStrictness(Strictness.LENIENT);
            writer.beginObject();
            writer.name("type").value("session");
            writer.name("protocol").value("kirino-frame-benchmark");
            writer.name("version").value(3);
            writer.name("project").value(project.id());
            writer.name("name").value(project.name());
            if (identity != null) {
                writer.name("identity").beginObject();
                writer.name("id").value(identity.id());
                writer.name("name").value(identity.name());
                writer.endObject();
            }
            writer.name("startedAt").value(startedAt.toString());
            writer.name("durationSeconds").value(Benchmark.DURATION_SECONDS);
            writer.name("frameInterval").value("render_tick_start_to_start");
            writer.name("statistics").beginObject();
            writer.name("average").value("arithmetic");
            writer.name("standardDeviation").value("population");
            writer.name("weighting").value("per_frame_sample");
            writer.endObject();
            writer.name("rolling").beginObject();
            writer.name("windowSeconds").value(Benchmark.ROLLING_WINDOW_SECONDS);
            writer.name("alignment").value("trailing");
            writer.name("warmup").value("partial");
            writer.endObject();
            writer.name("metrics").beginArray();
            for (BenchmarkMetric metric : metrics) {
                writer.beginObject();
                writer.name("id").value(metric.id());
                writer.name("name").value(metric.name());
                writer.name("unit").value(metric.unit());
                writer.name("kind").value(metric.kind().name().toLowerCase(Locale.ROOT));
                writer.name("sampling").value(metric.sampling().name().toLowerCase(Locale.ROOT));
                writer.name("aggregation").value(metric.kind() == BenchmarkMetric.Kind.GAUGE ? "last" : "sum");
                writer.name("rollingAggregation").value(metric.rollingAggregation().name().toLowerCase(Locale.ROOT));
                if (metric.kind() == BenchmarkMetric.Kind.GPU_DURATION) {
                    writer.name("backend").value("remotery_opengl");
                    writer.name("precisionNs").value(1000);
                    writer.name("frameAssociation").value("issue_frame");
                    writer.name("missingSamples").value("omit_incomplete_frame");
                }
                writer.endObject();
            }
            writer.endArray();
            writer.endObject();
            output.newLine();

            for (int index = 0; index < frames.size(); index++) {
                Frame frame = frames.get(index);
                writer.beginObject();
                writer.name("type").value("frame");
                writer.name("frame").value(index);
                writer.name("elapsedNs").value(frame.elapsedNs());
                writer.name("frameNs").value(frame.frameNs());
                writer.name("values").beginObject();
                for (int i = 0; i < metrics.size(); i++) {
                    if (frame.counts()[i] > 0 && (frame.gpuRequests()[i] == 0 || frame.counts()[i] == frame.gpuRequests()[i])) {
                        writer.name(metrics.get(i).id()).value(frame.values()[i]);
                    }
                }
                writer.endObject();
                writer.name("counts").beginObject();
                for (int i = 1; i < metrics.size(); i++) {
                    if (frame.counts()[i] > 0 && (frame.gpuRequests()[i] == 0 || frame.counts()[i] == frame.gpuRequests()[i])) {
                        writer.name(metrics.get(i).id()).value(frame.counts()[i]);
                    }
                }
                writer.endObject();
                writer.name("missing").beginObject();
                for (int i = 1; i < metrics.size(); i++) {
                    if (frame.gpuRequests()[i] > frame.counts()[i]) {
                        writer.name(metrics.get(i).id()).value(frame.gpuRequests()[i] - frame.counts()[i]);
                    }
                }
                writer.endObject();
                writer.endObject();
                output.newLine();
            }

            writer.beginObject();
            writer.name("type").value("end");
            writer.name("reason").value(reason);
            writer.name("frames").value(frames.size());
            writer.name("elapsedNs").value(elapsed.getNanoTime());
            writer.name("statisticsGroups").beginArray();
            for (BenchmarkStatisticsCollector collector : statistics.values()) {
                collector.write(writer, frames.size());
            }
            writer.endArray();
            writer.endObject();
            output.newLine();
        }
        Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        return destination;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        elapsed.stop();
        if (frameOpen) {
            profiler.endFrame();
            frameOpen = false;
        }
        profiler.close();
        if (pipelineProfiler != null) {
            pipelineProfiler.close();
        }
        frameTimer.reset();
    }
}
