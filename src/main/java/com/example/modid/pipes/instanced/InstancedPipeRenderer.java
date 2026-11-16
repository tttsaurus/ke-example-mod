package com.example.modid.pipes.instanced;

import com.example.modid.pipes.PipeFrame;
import com.example.modid.pipes.PipeRenderer;
import com.example.modid.pipes.PipeScene;
import com.google.common.base.Preconditions;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;

public final class InstancedPipeRenderer implements PipeRenderer {

    private final PipeShader shader;
    private final PipeMesh mesh;
    private boolean closed;

    public InstancedPipeRenderer(PipeScene scene) {
        Preconditions.checkNotNull(scene);

        shader = new PipeShader();
        mesh = new PipeMesh(scene);
    }

    @Override
    public void update(PipeScene scene) {
        Preconditions.checkState(!closed, "Renderer is closed.");
        Preconditions.checkNotNull(scene);

        mesh.update(scene);
    }

    @Override
    public void render(PipeFrame frame) {
        Preconditions.checkState(!closed, "Renderer is closed.");
        Preconditions.checkNotNull(frame);

        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.bindTexture(frame.lightmap.getGlTextureId());
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        shader.bind(frame);
        mesh.draw();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        closed = true;
        shader.close();
        mesh.close();
    }
}
