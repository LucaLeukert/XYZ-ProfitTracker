package net.leukert.hud;

import cc.polyfrost.oneconfig.config.annotations.Button;
import cc.polyfrost.oneconfig.config.annotations.Color;
import cc.polyfrost.oneconfig.config.annotations.Dropdown;
import cc.polyfrost.oneconfig.config.core.OneColor;
import cc.polyfrost.oneconfig.hud.BasicHud;
import cc.polyfrost.oneconfig.libs.universal.UMatrixStack;
import cc.polyfrost.oneconfig.platform.Platform;
import cc.polyfrost.oneconfig.renderer.NanoVGHelper;
import cc.polyfrost.oneconfig.renderer.TextRenderer;
import net.leukert.client.GameStateHelper;
import net.leukert.xyz.ProfitTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Tuple;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class XYZProfitTrackerHud extends BasicHud {
    private final float iconWidth = 12 * scale;
    private final float iconHeight = 12 * scale;
    protected transient ArrayList<Tuple<String, String>> lines = new ArrayList<>();

    @Color(name = "Text Color")
    protected OneColor color = new OneColor(255, 255, 255);

    @Dropdown(name = "Text Type", options = {"No Shadow", "Shadow", "Full Shadow"})
    protected int textType = 0;

    @Button(name = "Reset Statistics", text = "Reset", description = "Resets the profit statistics")
    protected Runnable reset = ProfitTracker.instance::reset;

    public XYZProfitTrackerHud() {
        super(true,
                1f,
                1f,
                1,
                true,
                true,
                4,
                5,
                5,
                new OneColor(0, 0, 0, 150),
                false,
                2,
                new OneColor(0, 0, 0, 127));
    }

    @Override
    protected void draw(UMatrixStack matrices, float x, float y, float scale, boolean example) {
        addLines();
        float textX = position.getX() + 1 * scale;
        NanoVGHelper.INSTANCE.setupAndDraw(true, (vgContext) -> {
            float textY = position.getY() + 1 * scale;
            for (Tuple<String, String> line : lines) {
                drawImage(vgContext, line.getSecond(), textX, textY, scale);
                textY += 15 * scale;
            }
        });
        float textY = position.getY() + 1 * scale;
        for (Tuple<String, String> line : lines) {
            drawLine(line.getFirst(), textX, textY, scale);
            textY += 15 * scale;
        }
    }

    @Override
    protected float getWidth(float scale, boolean example) {
        if (lines == null) return 0;
        float width = 0;
        for (Tuple<String, String> line : lines) {
            width = Math.max(width, getLineWidth(line.getFirst(), scale));
        }
        return width + iconWidth * scale + 10 * scale + 1 * scale;
    }

    protected float getLineWidth(String line, float scale) {
        return Platform.getGLPlatform().getStringWidth(line) * scale;
    }

    @Override
    protected float getHeight(float scale, boolean example) {
        if (lines == null) return 0;
        return lines.size() * 15 * scale;
    }

    @Override
    protected boolean shouldShow() {
        if (!super.shouldShow()) {
            return false;
        }
        return GameStateHelper.instance.inCrimsonIsle();
    }

    public void addLines() {
        if (ProfitTracker.instance.updateClock.isScheduled() && !ProfitTracker.instance.updateClock.passed()) {
            return;
        }
        ProfitTracker.instance.updateClock.schedule(100);
        lines.clear();

        lines.add(new Tuple<>(ProfitTracker.instance.getRealProfitString(), "/xyzprofittracker/textures/gui/profit.png"));
        lines.add(new Tuple<>(ProfitTracker.instance.getProfitPerHourString(), "/xyzprofittracker/textures/gui/profithr.png"));
        List<ProfitTracker.BazaarItem> linesCopy = new ArrayList<>(ProfitTracker.instance.xyzItems);

        linesCopy.stream()
                .filter(crop -> crop.currentAmount > 0)
                .sorted(Comparator.comparing((ProfitTracker.BazaarItem item) -> -item.currentAmount))
                .forEachOrdered(item -> lines.add(new Tuple<>(String.valueOf(item.currentAmount), item.image)));
    }

    protected void drawImage(long vgContext, String iconPath, float x, float y, float scale) {
        if (iconPath != null) {
            NanoVGHelper.INSTANCE.drawImage(vgContext, iconPath, x + paddingX * scale, y + paddingY * scale, iconWidth * scale, iconHeight * scale, this.getClass());
        }
    }

    protected void drawLine(String text, float x, float y, float scale) {
        if (text != null) {
            TextRenderer.drawScaledString(text, x + iconWidth * scale + (3.5f * scale) + paddingX * scale, y + paddingY * scale + iconHeight * scale / 2 - getLineHeight(scale) / 2.5f, color.getRGB(), TextRenderer.TextType.toType(textType), scale);
        }
    }

    protected float getLineHeight(float scale) {
        return Minecraft.getMinecraft().fontRendererObj.FONT_HEIGHT * scale;
    }
}