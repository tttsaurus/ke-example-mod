package com.example.modid.pipes;

public interface PipeRenderer extends AutoCloseable {

    void update(PipeScene scene);

    void render(PipeFrame frame);

    @Override
    void close();
}
