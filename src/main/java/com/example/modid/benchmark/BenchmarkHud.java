package com.example.modid.benchmark;

import com.cleanroommc.kirino.ICS;
import com.cleanroommc.kirino.ui.simpletext.SimpleTextProducer;
import com.cleanroommc.kirino.ui.simpletext.SimpleTextRuntime;
import com.cleanroommc.kirino.ui.transform.UiTransformContext;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;

import java.util.List;

final class BenchmarkHud {

    private BenchmarkHud() {
    }

    static void draw(List<String> lines, int x, int y) {
        if (lines.isEmpty()) {
            return;
        }

        ICS.instance().assertFullAvailability();
        SimpleTextRuntime font = ICS.instance().text();

        float width = 0;
        float lineHeight = 0;
        for (String line : lines) {
            SimpleTextProducer.LineInfo info = font.simulate(line, 0, 0);
            width = Math.max(width, info.getLineWidth());
            lineHeight = Math.max(lineHeight, info.getLineTopToBaseline());
        }
        float lineSpacing = (float) Math.ceil(lineHeight) + 6;
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableCull();
        Gui.drawRect(x - 6, y - 6, x + (int) Math.ceil(width) + 6,
                y + (int) Math.ceil((lines.size() - 1) * lineSpacing + lineHeight) + 6, 0xCC101820);
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);

        font.begin(UiTransformContext.legacyGui());
        for (int i = 0; i < lines.size(); i++) {
            font.append(lines.get(i), x, y + i * lineSpacing);
        }
        font.endDraw();

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableCull();
        GlStateManager.color(1, 1, 1, 1);
    }
}
