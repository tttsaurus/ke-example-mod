package com.example.modid.benchmark;

import com.cleanroommc.kirino.gl.GLDisposable;
import com.cleanroommc.kirino.gl.GLResourceManager;
import com.google.common.base.Preconditions;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.ARBPipelineStatisticsQuery;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GL45;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

final class PipelineStatisticsProfiler extends GLDisposable implements AutoCloseable {

    private static final class Query {

        private final int id;
        private long @Nullable [] values;
        private int @Nullable [] counts;
        private int index;

        private Query(int id) {
            this.id = id;
        }
    }

    private static final int TARGET = ARBPipelineStatisticsQuery.GL_VERTEX_SHADER_INVOCATIONS_ARB;
    private static final Benchmark.Sample NO_SAMPLE = () -> {};

    private final int[] queryIds = new int[128];
    private final Queue<Query> available = new ArrayDeque<>(queryIds.length);
    private final Queue<Query> pending = new ArrayDeque<>(queryIds.length);
    private @Nullable Query active;
    private final Benchmark.Sample sample = () -> {
        Query query = Preconditions.checkNotNull(active, "Pipeline statistics sample is not running.");

        GL15.glEndQuery(TARGET);
        active = null;
        pending.add(query);
    };
    private boolean closed;

    PipelineStatisticsProfiler() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer ids = stack.mallocInt(queryIds.length);
            GL45.glCreateQueries(TARGET, ids);
            ids.get(queryIds);
        }
        GLResourceManager.addDisposable(this);
        for (int id : queryIds) {
            available.add(new Query(id));
        }
    }

    Benchmark.Sample begin(long[] values, int[] counts, int index) {
        Preconditions.checkState(!closed, "Pipeline statistics profiler is closed.");
        Preconditions.checkState(active == null, "Pipeline statistics samples cannot overlap.");

        collectResults();
        Query query = available.poll();
        if (query == null) {
            return NO_SAMPLE;
        }
        query.values = values;
        query.counts = counts;
        query.index = index;
        active = query;
        GL15.glBeginQuery(TARGET, query.id);
        return sample;
    }

    void collectResults() {
        Query query;
        while ((query = pending.peek()) != null) {
            if (GL15.glGetQueryObjecti(query.id, GL15.GL_QUERY_RESULT_AVAILABLE) == GL11.GL_FALSE) {
                return;
            }
            pending.remove();
            long[] values = Preconditions.checkNotNull(query.values);
            int[] counts = Preconditions.checkNotNull(query.counts);

            values[query.index] += GL33.glGetQueryObjectui64(query.id, GL15.GL_QUERY_RESULT);
            counts[query.index]++;
            query.values = null;
            query.counts = null;
            available.add(query);
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        Preconditions.checkState(active == null, "A pipeline statistics sample is still running.");

        collectResults();
        closed = true;
        disposeManually();
        available.clear();
        pending.clear();
    }

    @Override
    protected void dispose() {
        for (int id : queryIds) {
            GL15.glDeleteQueries(id);
        }
    }
}
