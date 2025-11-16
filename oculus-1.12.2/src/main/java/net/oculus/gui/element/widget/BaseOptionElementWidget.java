package net.oculus.gui.element.widget;

import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.shaderpack.option.menu.OptionMenuElement;

/**
 * Shared logic for option widgets that present a label/value pair and support
 * cycling through multiple values with keyboard/mouse interactions.
 */
public abstract class BaseOptionElementWidget<T extends OptionMenuElement> extends CommentedElementWidget<T> {
    private static final String DIVIDER = ": ";
    protected static final ITextComponent SET_TO_DEFAULT;

    static {
        TextComponentTranslation setToDefault = new TextComponentTranslation("options.iris.setToDefault");
        setToDefault.getStyle().setColor(TextFormatting.GREEN);
        SET_TO_DEFAULT = setToDefault;
    }

    protected ITextComponent unmodifiedLabel;
    protected ShaderPackScreen screen;
    protected NavigationController navigation;

    private ITextComponent label;
    protected ITextComponent trimmedLabel;
    protected ITextComponent valueLabel;

    private boolean isLabelTrimmed;
    private int maxLabelWidth;
    private int valueSectionWidth;

    protected BaseOptionElementWidget(T element) {
        super(element);
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        this.screen = screen;
        this.navigation = navigation;
        this.valueLabel = null;
        this.trimmedLabel = null;
        this.isLabelTrimmed = false;
    }

    protected final void setLabel(ITextComponent label) {
        this.label = label.createCopy();
        this.label.appendText(DIVIDER);
        this.unmodifiedLabel = label.createCopy();
    }

    protected final void updateRenderParams(int width, int minValueSectionWidth) {
        if (this.valueLabel == null) {
            this.valueLabel = createValueLabel();
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String valueText = this.valueLabel.getFormattedText();
        this.valueSectionWidth = Math.max(minValueSectionWidth, font.getStringWidth(valueText) + 8);

        this.maxLabelWidth = (width - 8) - this.valueSectionWidth;

        boolean needsTrim = font.getStringWidth(this.label.getFormattedText()) > this.maxLabelWidth;
        if (this.trimmedLabel == null || needsTrim != this.isLabelTrimmed) {
            updateLabels();
        }

        this.isLabelTrimmed = needsTrim;
    }

    protected final void renderOptionWithValue(int x, int y, int width, int height, boolean hovered, float sliderPosition, int sliderWidth) {
        GuiUtil.bindIrisWidgetsTexture();

        GuiUtil.drawButton(x, y, width, height, hovered, false);
        GuiUtil.drawButton((x + width) - (this.valueSectionWidth + 2), y + 2, this.valueSectionWidth, height - 4, false, true);

        if (sliderPosition >= 0.0F) {
            int sliderSpace = (this.valueSectionWidth - 4) - sliderWidth;
            int sliderPos = ((x + width) - this.valueSectionWidth) + (int) (sliderPosition * sliderSpace);
            GuiUtil.drawButton(sliderPos, y + 4, sliderWidth, height - 8, false, false);
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        font.drawStringWithShadow(this.trimmedLabel.getFormattedText(), x + 6, y + 7, 0xFFFFFF);

        int valueTextWidth = font.getStringWidth(this.valueLabel.getFormattedText());
        int valueTextX = (x + width - 2) - (this.valueSectionWidth / 2) - (valueTextWidth / 2);
        font.drawStringWithShadow(this.valueLabel.getFormattedText(), valueTextX, y + 7, 0xFFFFFF);
    }

    protected final void renderOptionWithValue(int x, int y, int width, int height, boolean hovered) {
        renderOptionWithValue(x, y, width, height, hovered, -1.0F, 0);
    }

    protected final void tryRenderTooltip(int mouseX, int mouseY, boolean hovered) {
        if (GuiScreen.isShiftKeyDown()) {
            renderTooltip(SET_TO_DEFAULT, mouseX, mouseY, hovered);
        } else if (this.isLabelTrimmed && this.screen != null && !this.screen.isDisplayingComment()) {
            renderTooltip(this.unmodifiedLabel, mouseX, mouseY, hovered);
        }
    }

    protected final void renderTooltip(ITextComponent text, int mouseX, int mouseY, boolean hovered) {
        if (hovered && text != null) {
            FontRenderer font = Minecraft.getMinecraft().fontRenderer;
            ShaderPackScreen.TOP_LAYER_RENDER_QUEUE.add(() -> GuiUtil.drawTextPanel(font, text, mouseX + 2, mouseY - 16));
        }
    }

    protected final void updateLabels() {
        this.trimmedLabel = createTrimmedLabel();
        this.valueLabel = createValueLabel();
    }

    protected final ITextComponent createTrimmedLabel() {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        ITextComponent shortened = GuiUtil.shortenText(font, this.label, this.maxLabelWidth);

        if (isValueModified()) {
            Style style = shortened.getStyle() == null ? new Style() : shortened.getStyle().createDeepCopy();
            style.setColor(TextFormatting.GOLD);
            shortened.setStyle(style);
        }

        return shortened;
    }

    protected abstract ITextComponent createValueLabel();

    public abstract boolean applyNextValue();

    public abstract boolean applyPreviousValue();

    public abstract boolean applyOriginalValue();

    public abstract boolean isValueModified();

    public abstract String getCommentKey();

    @Override
    public Optional<ITextComponent> getCommentTitle() {
        return Optional.ofNullable(this.unmodifiedLabel).map(ITextComponent::createCopy);
    }

    @Override
    public Optional<ITextComponent> getCommentBody() {
        String key = getCommentKey();
        if (key != null && I18n.hasKey(key)) {
            return Optional.of(new TextComponentTranslation(key));
        }
        return Optional.empty();
    }

    @Override
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0 || button == 1) {
            boolean refresh = false;

            if (GuiScreen.isShiftKeyDown()) {
                refresh = applyOriginalValue();
            }

            if (!refresh) {
                refresh = button == 0 ? applyNextValue() : applyPreviousValue();
            }

            if (refresh && this.navigation != null) {
                this.navigation.refresh();
            }

            GuiUtil.playButtonClickSound();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}
