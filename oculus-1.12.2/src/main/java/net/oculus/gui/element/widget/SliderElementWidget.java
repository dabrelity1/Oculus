package net.oculus.gui.element.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import net.oculus.gui.GuiUtil;
import net.oculus.shaderpack.option.menu.OptionMenuStringOptionElement;

public class SliderElementWidget extends StringElementWidget {
    private static final int PREVIEW_SLIDER_WIDTH = 4;
    private static final int ACTIVE_SLIDER_WIDTH = 6;

    private boolean mouseDown;
    private int lastX;
    private int lastWidth;

    public SliderElementWidget(OptionMenuStringOptionElement element) {
        super(element);
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
        this.lastX = x;
        this.lastWidth = width;
        updateRenderParams(width, 35);

        if (!hovered) {
            renderOptionWithValue(x, y, width, height, false, getSliderFraction(), PREVIEW_SLIDER_WIDTH);
        } else {
            renderSlider(x, y, width, height, mouseX);
        }

        if (GuiScreen.isShiftKeyDown()) {
            renderTooltip(SET_TO_DEFAULT, mouseX, mouseY, hovered);
        } else if (this.screen != null && !this.screen.isDisplayingComment()) {
            renderTooltip(this.unmodifiedLabel, mouseX, mouseY, hovered);
        }

        if (this.mouseDown) {
            if (!hovered) {
                onReleased();
            }
            whileDragging(x, width, mouseX);
        }
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button != 0) {
            return false;
        }

        if (GuiScreen.isShiftKeyDown()) {
            if (applyOriginalValue() && this.navigation != null) {
                this.navigation.refresh();
            }
            GuiUtil.playButtonClickSound();
            return true;
        }

        this.mouseDown = true;
        whileDragging(this.lastX, this.lastWidth, mouseX);
        GuiUtil.playButtonClickSound();
        return true;
    }

    @Override
    public boolean mouseReleased(int mouseX, int mouseY, int button) {
        if (button == 0 && this.mouseDown) {
            onReleased();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void renderSlider(int x, int y, int width, int height, int mouseX) {
        GuiUtil.bindIrisWidgetsTexture();
        GuiUtil.drawButton(x, y, width, height, false, false);
        GuiUtil.drawButton(x + 2, y + 2, width - 4, height - 4, false, true);

        int sliderSpace = Math.max(0, (width - 8) - ACTIVE_SLIDER_WIDTH);
        int sliderPos = (x + 4) + (int) (getSliderFraction() * sliderSpace);
        GuiUtil.drawButton(sliderPos, y + 4, ACTIVE_SLIDER_WIDTH, height - 8, this.mouseDown, false);

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String value = this.valueLabel.getFormattedText();
        font.drawStringWithShadow(value, (int) (x + width * 0.5F) - (font.getStringWidth(value) / 2), y + 7, 0xFFFFFF);
    }

    private void whileDragging(int x, int width, int mouseX) {
        if (this.valueCount <= 1) {
            this.valueIndex = 0;
            return;
        }

        float position = clamp((mouseX - (x + 4)) / (float) Math.max(1, width - 8));
        int newValueIndex = Math.min(this.valueCount - 1, (int) (position * this.valueCount));

        if (this.valueIndex != newValueIndex) {
            this.valueIndex = newValueIndex;
            updateLabels();
        }
    }

    private void onReleased() {
        this.mouseDown = false;
        queue();
        if (this.navigation != null) {
            this.navigation.refresh();
        }
        GuiUtil.playButtonClickSound();
    }

    private float getSliderFraction() {
        return this.valueCount <= 1 ? 0.0F : this.valueIndex / (float) (this.valueCount - 1);
    }

    private static float clamp(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
