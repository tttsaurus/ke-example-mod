package com.example.modid.benchmark;

import com.google.common.base.Preconditions;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

final class BenchmarkMenu extends GuiScreen {

    private final List<BenchmarkProject> projects;
    private final List<String> lines;
    private int selected;

    BenchmarkMenu(List<BenchmarkProject> projects) {
        this.projects = Preconditions.checkNotNull(projects);

        lines = new ArrayList<>(projects.size() + 3);
        updateLines();
    }

    private void updateLines() {
        lines.clear();
        lines.add("Benchmark - " + Benchmark.DURATION_SECONDS + " seconds");
        lines.add("Up / Down: select   Enter: start   Esc: cancel");
        for (int i = 0; i < projects.size(); i++) {
            lines.add((i == selected ? "> " : "  ") + projects.get(i).name());
        }
        if (projects.isEmpty()) {
            lines.add("No benchmark projects registered");
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawRect(0, 0, width, height, 0x80101820);
        BenchmarkHud.draw(lines, Math.max(16, width / 2 - 175), height / 2 - 40);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            mc.setIngameFocus();
        } else if (!projects.isEmpty()) {
            if (keyCode == Keyboard.KEY_UP) {
                selected = Math.floorMod(selected - 1, projects.size());
                updateLines();
            } else if (keyCode == Keyboard.KEY_DOWN) {
                selected = (selected + 1) % projects.size();
                updateLines();
            } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
                mc.displayGuiScreen(null);
                mc.setIngameFocus();
                Benchmark.request(projects.get(selected));
            }
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
