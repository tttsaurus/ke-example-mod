package com.example.modid.benchmark;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

import java.util.List;

@Mod.EventBusSubscriber(modid = "@{MOD_ID}", value = Side.CLIENT)
public final class BenchmarkEvents {

    private BenchmarkEvents() {
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.KeyInputEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world != null
                && minecraft.currentScreen == null
                && Keyboard.getEventKey() == Keyboard.KEY_Q
                && Keyboard.getEventKeyState()
                && !Keyboard.isRepeatEvent()
                && GuiScreen.isCtrlKeyDown()
                && !Benchmark.busy()) {
            minecraft.displayGuiScreen(new BenchmarkMenu(Benchmark.projects()));
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderTickStart(TickEvent.RenderTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Benchmark.nextFrame();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderTickEnd(TickEvent.RenderTickEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (event.phase == TickEvent.Phase.END && minecraft.world != null) {
            List<String> lines = Benchmark.hudLines();
            if (!lines.isEmpty()) {
                minecraft.entityRenderer.setupOverlayRendering();
                BenchmarkHud.draw(lines, 12, 12);
            }
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) {
            Benchmark.finish("world_unload");
        }
    }
}
