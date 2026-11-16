package com.example.modid.benchmark;

import com.cleanroommc.kirino.engine.ShutdownManager;
import com.example.modid.ExampleMod;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import org.apache.commons.lang3.time.StopWatch;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Benchmark {

    public interface Sample extends AutoCloseable {

        @Override
        void close();
    }

    public static final int DURATION_SECONDS = 45;
    public static final double ROLLING_WINDOW_SECONDS = 0.5;

    private static final Sample NO_SAMPLE = () -> {};
    private static final List<BenchmarkProject> PROJECTS = new ArrayList<>();
    private static final List<String> HUD_LINES = new ArrayList<>(2);
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("Benchmark writer").daemon().factory());
    private static final StopWatch STATUS_TIMER = new StopWatch();
    private static @Nullable BenchmarkProject requested;
    private static @Nullable BenchmarkSession session;
    private static @Nullable CompletableFuture<String> saveResult;
    private static String status = "";

    static {
        ShutdownManager.register(() -> finish("shutdown"));
        ShutdownManager.registerAsync(() -> {
            WRITER.shutdown();
            try {
                if (!WRITER.awaitTermination(5, TimeUnit.SECONDS)) {
                    ExampleMod.LOGGER.warn("Benchmark writer is still finishing a capture.");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private Benchmark() {
    }

    public static void register(BenchmarkProject project) {
        Preconditions.checkNotNull(project);
        Preconditions.checkArgument(PROJECTS.stream().noneMatch(existing -> existing.id().equals(project.id())),
                "Duplicate benchmark project: %s", project.id());

        PROJECTS.add(project);
    }

    public static ImmutableList<BenchmarkProject> projects() {
        return ImmutableList.copyOf(PROJECTS);
    }

    public static Sample measure(BenchmarkProject project, BenchmarkMetric metric) {
        Preconditions.checkNotNull(project);
        Preconditions.checkNotNull(metric);

        BenchmarkSession current = session;
        return current != null && current.project == project && current.frameOpen()
                ? current.measure(metric)
                : NO_SAMPLE;
    }

    public static void record(BenchmarkProject project, BenchmarkMetric metric, double value) {
        Preconditions.checkNotNull(project);
        Preconditions.checkNotNull(metric);
        Preconditions.checkArgument(Double.isFinite(value));

        BenchmarkSession current = session;
        if (current != null && current.project == project && current.frameOpen()) {
            current.record(metric, value);
        }
    }

    static boolean busy() {
        return session != null || requested != null || saveResult != null;
    }

    static void request(BenchmarkProject project) {
        Preconditions.checkState(!busy(), "A benchmark is already running or saving.");
        Preconditions.checkArgument(PROJECTS.contains(project), "Unknown benchmark project.");

        requested = project;
    }

    static void nextFrame() {
        if (saveResult != null && saveResult.isDone()) {
            CompletableFuture<String> completed = saveResult;
            saveResult = null;
            try {
                showStatus(completed.getNow("Benchmark save failed; see latest.log"));
            } catch (CompletionException exception) {
                ExampleMod.LOGGER.error("Cannot save benchmark.", exception.getCause());
                showStatus("Benchmark save failed; see latest.log");
            }
        }

        if (requested != null) {
            BenchmarkProject project = requested;
            requested = null;
            try {
                session = new BenchmarkSession(project);
                status = "";
            } catch (Throwable exception) {
                ExampleMod.LOGGER.error("Cannot start benchmark {}.", project.id(), exception);
                showStatus("Benchmark failed to start.");
            }
        }
        if (session != null && !session.nextFrame()) {
            finish("completed");
        }
    }

    static List<String> hudLines() {
        HUD_LINES.clear();
        if (session != null) {
            HUD_LINES.add("Benchmark: " + session.project.name());
            HUD_LINES.add(session.secondsRemaining() + "s remaining");
        } else if (saveResult != null) {
            HUD_LINES.add("Saving benchmark...");
        } else if (!status.isEmpty() && STATUS_TIMER.getTime(TimeUnit.SECONDS) < 8) {
            HUD_LINES.add(status);
        }
        return HUD_LINES;
    }

    private static void showStatus(String message) {
        status = message;
        STATUS_TIMER.reset();
        STATUS_TIMER.start();
    }

    static void finish(String reason) {
        requested = null;
        BenchmarkSession completed = session;
        session = null;
        if (completed == null) {
            return;
        }

        completed.close();
        Path directory = Minecraft.getMinecraft().gameDir.toPath().resolve("benchmarks");
        saveResult = CompletableFuture.supplyAsync(() -> {
            try {
                Path file = completed.write(directory, reason);
                ExampleMod.LOGGER.info("Benchmark saved to {}", file.toAbsolutePath());
                return "Benchmark saved: " + file.getFileName();
            } catch (Exception exception) {
                ExampleMod.LOGGER.error("Cannot save benchmark.", exception);
                return "Benchmark save failed; see latest.log";
            }
        }, WRITER);
    }
}
