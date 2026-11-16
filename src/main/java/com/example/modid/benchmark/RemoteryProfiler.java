package com.example.modid.benchmark;

import com.google.common.base.Preconditions;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.remotery.Remotery;
import org.lwjgl.util.remotery.RemoteryGL;
import org.lwjgl.util.remotery.RMTSampleTreeHandler;
import org.lwjgl.util.remotery.RMTSettings;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

final class RemoteryProfiler implements AutoCloseable {

    private record GpuResult(GpuSample sample, double milliseconds) {
    }

    static final class SampleName {

        private final ByteBuffer name;
        private final IntBuffer hash;

        private SampleName(String name) {
            hash = MemoryUtil.memCallocInt(1);
            try {
                this.name = MemoryUtil.memUTF8(name);
            } catch (RuntimeException | Error exception) {
                MemoryUtil.memFree(hash);
                throw exception;
            }
        }
    }

    final class GpuTimer {

        private final Queue<GpuSample> available = new ArrayDeque<>();

        private GpuTimer(String name, int index) {
            for (int i = 0; i < 128; i++) {
                GpuSample sample = new GpuSample(this, register(name + ".slot" + i), index);
                int hash = Remotery._rmt_HashString32(sample.name.name, sample.name.name.remaining() - 1, 0);
                Preconditions.checkState(gpuSamples.putIfAbsent(hash, sample) == null, "Duplicate GPU sample hash.");

                available.add(sample);
            }
        }

        Benchmark.Sample begin(double[] values, int[] counts) {
            Preconditions.checkState(!closed, "Profiler is closed.");
            Preconditions.checkState(!gpuSampleOpen, "GPU samples cannot overlap.");

            collectGpuResults();
            GpuSample sample = available.poll();
            if (sample == null) {
                return NO_SAMPLE;
            }
            sample.values = values;
            sample.counts = counts;
            sample.running = true;
            gpuSampleOpen = true;
            RemoteryGL.rmt_BeginOpenGLSample(sample.name.name, sample.name.hash);
            return sample;
        }
    }

    private final class GpuSample implements Benchmark.Sample {

        private final GpuTimer timer;
        private final SampleName name;
        private final int index;
        private double @Nullable [] values;
        private int @Nullable [] counts;
        private boolean running;

        private GpuSample(GpuTimer timer, SampleName name, int index) {
            this.timer = timer;
            this.name = name;
            this.index = index;
        }

        @Override
        public void close() {
            Preconditions.checkState(running, "GPU sample is not running.");

            running = false;
            gpuSampleOpen = false;
            RemoteryGL.rmt_EndOpenGLSample();
        }
    }

    private static final Benchmark.Sample NO_SAMPLE = () -> {};
    private final RMTSettings settings;
    private final long instance;
    private final RMTSampleTreeHandler sampleTreeHandler;
    private final List<SampleName> names = new ArrayList<>();
    private final Map<Integer, GpuSample> gpuSamples = new ConcurrentHashMap<>();
    private final Queue<GpuResult> gpuResults = new ConcurrentLinkedQueue<>();
    private boolean openGlBound;
    private boolean gpuSampleOpen;
    private boolean closed;

    RemoteryProfiler() {
        Preconditions.checkState(Remotery.rmt_GetGlobalInstance() == MemoryUtil.NULL,
                "Another Remotery profiler is already running.");
        settings = Preconditions.checkNotNull(Remotery.rmt_Settings(), "Remotery settings are unavailable.");

        sampleTreeHandler = RMTSampleTreeHandler.create((context, tree) -> {
            long root = Remotery.rmt_SampleTreeGetRootSample(tree);
            if (Remotery.rmt_SampleGetType(root) == Remotery.RMT_SampleType_OpenGL) {
                GpuSample sample = gpuSamples.get(Remotery.rmt_SampleGetNameHash(root));
                if (sample != null) {
                    gpuResults.add(new GpuResult(sample, Remotery.rmt_SampleGetTime(root) / 1000.0));
                }
            }
        });
        try {
            settings
                    .port((short) 0)
                    .limit_connections_to_localhost(true)
                    .enableThreadSampler(false)
                    .sampletree_handler(sampleTreeHandler);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer pointer = stack.callocPointer(1);
                int result = Remotery.rmt_CreateGlobalInstance(pointer);
                Preconditions.checkState(result == Remotery.RMT_ERROR_NONE,
                        "Cannot create Remotery profiler: %s (%s)", result, Remotery.rmt_GetLastErrorMessage());

                instance = pointer.get(0);
            }
        } catch (RuntimeException | Error exception) {
            settings.sampletree_handler(null);
            sampleTreeHandler.free();
            throw exception;
        }
    }

    GpuTimer registerGpu(String name, int index) {
        Preconditions.checkState(!closed, "Profiler is closed.");
        Preconditions.checkNotNull(name);

        if (!openGlBound) {
            RemoteryGL.rmt_BindOpenGL();
            openGlBound = true;
        }
        return new GpuTimer(name, index);
    }

    void collectGpuResults() {
        GpuResult result;
        while ((result = gpuResults.poll()) != null) {
            GpuSample sample = result.sample();
            double[] values = Preconditions.checkNotNull(sample.values);
            int[] counts = Preconditions.checkNotNull(sample.counts);

            values[sample.index] = counts[sample.index] == 0
                    ? result.milliseconds()
                    : values[sample.index] + result.milliseconds();
            counts[sample.index]++;
            sample.values = null;
            sample.counts = null;
            sample.timer.available.add(sample);
        }
    }

    SampleName register(String name) {
        Preconditions.checkState(!closed, "Profiler is closed.");
        Preconditions.checkNotNull(name);

        SampleName sample = new SampleName(name);
        names.add(sample);
        return sample;
    }

    void begin(SampleName sample) {
        Preconditions.checkState(!closed, "Profiler is closed.");

        Remotery.rmt_BeginCPUSample(sample.name, Remotery.RMTSF_None, sample.hash);
    }

    void end() {
        Preconditions.checkState(!closed, "Profiler is closed.");

        Remotery.rmt_EndCPUSample();
    }

    void endFrame() {
        end();
        Remotery.rmt_MarkFrame();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        Preconditions.checkState(!gpuSampleOpen, "A GPU sample is still running.");

        closed = true;
        try {
            if (openGlBound) {
                RemoteryGL.rmt_UnbindOpenGL();
            }
        } finally {
            try {
                Remotery.rmt_DestroyGlobalInstance(instance);
            } finally {
                settings.sampletree_handler(null);
                sampleTreeHandler.free();
                collectGpuResults();
                gpuSamples.clear();
                for (SampleName sample : names) {
                    MemoryUtil.memFree(sample.name);
                    MemoryUtil.memFree(sample.hash);
                }
                names.clear();
            }
        }
    }
}
