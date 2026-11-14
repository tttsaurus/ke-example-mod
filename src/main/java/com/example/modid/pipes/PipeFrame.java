package com.example.modid.pipes;

import com.cleanroommc.kirino.engine.render.core.camera.MinecraftCamera;
import com.cleanroommc.kirino.utils.ReflectionUtils;
import com.google.common.base.Preconditions;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.MobEffects;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryUtil;

import java.lang.invoke.MethodHandle;
import java.nio.FloatBuffer;

public final class PipeFrame implements AutoCloseable {

    private static final MethodHandle LIGHTMAP = Preconditions.checkNotNull(ReflectionUtils.getFieldGetter(
            EntityRenderer.class,
            "lightmapTexture",
            "field_78513_d",
            DynamicTexture.class));

    private final MinecraftCamera camera = new MinecraftCamera();
    private final FrustumIntersection frustum = new FrustumIntersection();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f transform = new Matrix4f();
    private final FloatBuffer transformBuffer;
    private boolean closed;
    public final Vector4f fogPlane = new Vector4f();
    public final DynamicTexture lightmap;
    public int fogMode;
    public float waterFogDensity;

    public PipeFrame(Minecraft minecraft) {
        Preconditions.checkNotNull(minecraft);

        try {
            lightmap = Preconditions.checkNotNull((DynamicTexture) LIGHTMAP.invokeExact(minecraft.entityRenderer));
        } catch (Throwable exception) {
            throw new IllegalStateException("Cannot access Minecraft lightmap.", exception);
        }

        transformBuffer = MemoryUtil.memAllocFloat(16);
    }

    public boolean update(PipeScene scene, Entity entity, float partialTicks) {
        Preconditions.checkState(!closed, "Frame is closed.");
        Preconditions.checkNotNull(scene);
        Preconditions.checkNotNull(entity);
        Preconditions.checkArgument(Float.isFinite(partialTicks));

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        view.set(camera.getViewRotationBuffer()).translate(
                (float) (scene.origin.getX() - x),
                (float) (scene.origin.getY() - y),
                (float) (scene.origin.getZ() - z));

        transform.set(camera.getProjectionBuffer()).mul(view);
        fogPlane.set(view.m02(), view.m12(), view.m22(), view.m32());
        Material material = ActiveRenderInfo.getBlockStateAtEntityViewpoint(scene.world, entity, partialTicks).getMaterial();
        fogMode = 0;
        waterFogDensity = 0.0125f;
        if (!(entity instanceof EntityLivingBase living && living.isPotionActive(MobEffects.BLINDNESS))) {
            if (material == Material.WATER) {
                fogMode = 2;
                if (entity instanceof EntityLivingBase living) {
                    waterFogDensity = living.isPotionActive(MobEffects.WATER_BREATHING)
                            ? 0.00125f
                            : Math.max(0.00125f, 0.0125f - EnchantmentHelper.getRespirationModifier(living) * 0.00375f);
                }
            } else if (material == Material.LAVA) {
                fogMode = 1;
            }
        }

        boolean visible = frustum.set(transform).testAab(0, 0, 0, PipeWorld.SIZE, PipeWorld.SIZE, PipeWorld.SIZE);
        if (visible) {
            transform.get(0, transformBuffer);
        }

        return visible;
    }

    public FloatBuffer transformBuffer() {
        Preconditions.checkState(!closed, "Frame is closed.");

        return transformBuffer;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            MemoryUtil.memFree(transformBuffer);
        }
    }
}
