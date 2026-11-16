package com.example.modid.pipes;

import com.cleanroommc.kirino.engine.ShutdownManager;
import com.cleanroommc.kirino.gl.buffer.view.VBOView;
import com.cleanroommc.kirino.gl.shader.ShaderProgram;
import com.cleanroommc.kirino.gl.vao.VAO;
import com.example.modid.pipes.gpudriven.GpuDrivenPipeRenderer;
import com.example.modid.pipes.instanced.InstancedPipeRenderer;
import com.example.modid.render.BlockMaterial;
import com.example.modid.benchmark.Benchmark;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.init.MobEffects;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.commons.lang3.time.StopWatch;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;

import java.util.concurrent.TimeUnit;
import java.util.random.RandomGenerator;

@Mod.EventBusSubscriber(modid = "@{MOD_ID}", value = Side.CLIENT)
public final class PipeRenderEvents {

    private static final RandomGenerator RANDOM = RandomGenerator.getDefault();
    private static final StopWatch REGENERATION_TIMER = new StopWatch();

    private static @Nullable PipeScene scene;
    private static @Nullable PipeFrame frame;
    private static @Nullable PipeRenderer renderer;
    private static boolean renderPending;
    private static boolean skyFogPending;

    static {
        // resources close on world unload
        // this hook provides fallback cleanup on client shutdown
        ShutdownManager.register(PipeRenderEvents::close);
    }

    private PipeRenderEvents() {
    }

    @SubscribeEvent
    public static void onFogColors(EntityViewRenderEvent.FogColors event) {
        renderPending = true;
        skyFogPending = Minecraft.getMinecraft().gameSettings.renderDistanceChunks >= 4;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onFogDensity(EntityViewRenderEvent.FogDensity event) {
        if (skyFogPending) {
            skyFogPending = false;
            return;
        }

        if (renderPending
                && event.getState().getMaterial() == Material.WATER
                && !(event.getEntity() instanceof EntityLivingBase living && living.isPotionActive(MobEffects.BLINDNESS))) {

            renderPending = false;
            render((float) event.getRenderPartialTicks());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderFog(EntityViewRenderEvent.RenderFogEvent event) {
        if (event.getFogMode() == 0 && renderPending) {
            renderPending = false;
            render((float) event.getRenderPartialTicks());
        }
    }

    @SubscribeEvent
    public static void onRenderWorldLast(RenderWorldLastEvent event) {
        if (renderPending) {
            renderPending = false;
            render(event.getPartialTicks());
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote && scene != null && scene.world == event.getWorld()) {
            close();
        }
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (scene != null && event.getMap() == minecraft.getTextureMapBlocks()) {
            PipeScene previous = scene;
            close();
            if (previous.world.isAreaLoaded(previous.origin, previous.origin.add(PipeWorld.SIZE - 1, PipeWorld.SIZE - 1, PipeWorld.SIZE - 1))) {
                scene = new PipeScene(previous.world, previous.origin, previous.network, new BlockMaterial(minecraft, previous.world, previous.material.state));
                REGENERATION_TIMER.start();
            }
        }
    }

    private static void render(float partialTicks) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity camera = minecraft.getRenderViewEntity();

        if (minecraft.world == null
                || camera == null
                || minecraft.player == null
                || minecraft.currentScreen instanceof GuiDownloadTerrain) {
            return;
        }

        if (scene != null && scene.world != minecraft.world) {
            close();
        }

        if (scene == null) {
            EnumFacing facing = camera.getHorizontalFacing();
            int distance = PipeWorld.SIZE / 2 + 6;
            BlockPos origin = new BlockPos(
                    camera.posX + facing.getXOffset() * distance - (double) PipeWorld.SIZE / 2,
                    MathHelper.clamp(MathHelper.floor(camera.posY) + 2, 0, minecraft.world.getHeight() - PipeWorld.SIZE),
                    camera.posZ + facing.getZOffset() * distance - (double) PipeWorld.SIZE / 2);

            if (!minecraft.world.isAreaLoaded(origin, origin.add(PipeWorld.SIZE - 1, PipeWorld.SIZE - 1, PipeWorld.SIZE - 1))) {
                return;
            }

            scene = new PipeScene(minecraft.world, origin, PipeWorld.generate(RANDOM), new BlockMaterial(minecraft, minecraft.world, Blocks.IRON_BLOCK.getDefaultState()));
            REGENERATION_TIMER.start();
        }

        if (REGENERATION_TIMER.getTime(TimeUnit.SECONDS) >= 5 && scene.world.isAreaLoaded(scene.origin, scene.origin.add(PipeWorld.SIZE - 1, PipeWorld.SIZE - 1, PipeWorld.SIZE - 1))) {
            scene = new PipeScene(scene.world, scene.origin, PipeWorld.generate(RANDOM), scene.material);
            REGENERATION_TIMER.reset();
            REGENERATION_TIMER.start();
            if (renderer != null) {
                try (Benchmark.Sample ignored = Benchmark.measure(PipeBenchmark.PROJECT, PipeBenchmark.UPDATE)) {
                    renderer.update(scene);
                }
            }
        }

        if (frame == null) {
            frame = new PipeFrame(minecraft);
        }

        if (!frame.update(scene, camera, partialTicks)) {
            return;
        }

        if (renderer == null) {
            renderer = new InstancedPipeRenderer(scene);
        }

        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
        GlStateManager.cullFace(GlStateManager.CullFace.BACK);
        GlStateManager.disableAlpha();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        try (Benchmark.Sample gpu = Benchmark.measure(PipeBenchmark.PROJECT, PipeBenchmark.RENDER_GPU);
             Benchmark.Sample cpu = Benchmark.measure(PipeBenchmark.PROJECT, PipeBenchmark.RENDER_CPU)) {
            renderer.render(frame);
        }

        ShaderProgram.useRaw(0);
        VAO.bind(0);
        VBOView.bindRaw(0);
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GlStateManager.disableTexture2D();
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.cullFace(GlStateManager.CullFace.BACK);
        GlStateManager.disableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ZERO);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f);
        GlStateManager.disableFog();
        GlStateManager.color(1, 1, 1, 1);
    }

    private static void close() {
        PipeRenderer previous = renderer;
        PipeFrame previousFrame = frame;
        renderer = null;
        scene = null;
        frame = null;
        renderPending = false;
        skyFogPending = false;
        REGENERATION_TIMER.reset();

        try {
            if (previous != null) {
                previous.close();
            }
        } finally {
            if (previousFrame != null) {
                previousFrame.close();
            }
        }
    }
}
