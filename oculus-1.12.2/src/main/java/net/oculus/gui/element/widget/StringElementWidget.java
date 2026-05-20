package net.oculus.gui.element.widget;

import java.util.List;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.shaderpack.option.StringOption;
import net.oculus.shaderpack.option.menu.OptionMenuStringOptionElement;
import net.oculus.shaderpack.option.values.MutableOptionValues;

public class StringElementWidget extends BaseOptionElementWidget<OptionMenuStringOptionElement> {
    protected final StringOption option;

    protected String appliedValue;
    protected int valueCount;
    protected int valueIndex;

    public StringElementWidget(OptionMenuStringOptionElement element) {
        super(element);
        this.option = element.option;
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        super.init(screen, navigation);
        this.element.setPendingValuesSupplier(screen::getWorkingOptionValues);

        String pendingValue = this.element.getPendingOptionValues().getStringValueOrDefault(this.option.getName());
        this.appliedValue = this.element.getAppliedOptionValues().getStringValueOrDefault(this.option.getName());

        setLabel(GuiUtil.translateShaderPackOrDefault(screen.getCurrentPack(), new TextComponentString(this.option.getName()), "option." + this.option.getName()));

        List<String> values = this.option.getAllowedValues();
        this.valueCount = values.size();
        this.valueIndex = values.indexOf(pendingValue);
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
        updateRenderParams(width, 0);
        renderOptionWithValue(x, y, width, height, hovered);
        tryRenderTooltip(mouseX, mouseY, hovered);
    }

    @Override
    protected ITextComponent createValueLabel() {
        ITextComponent label = GuiUtil.translateShaderPackOrDefault(
            this.screen != null ? this.screen.getCurrentPack() : null,
            new TextComponentString(getValue()),
            "value." + this.option.getName() + "." + getValue()
        );
        Style style = label.getStyle();
        if (style == null) {
            style = new Style();
            label.setStyle(style);
        }
        style.setColor(TextFormatting.BLUE);
        return label;
    }

    @Override
    public boolean applyNextValue() {
        increment(1);
        queue();
        return true;
    }

    @Override
    public boolean applyPreviousValue() {
        increment(-1);
        queue();
        return true;
    }

    @Override
    public boolean applyOriginalValue() {
        this.valueIndex = this.option.getAllowedValues().indexOf(this.option.getDefaultValue());
        if (this.valueIndex < 0 && this.valueCount > 0) {
            this.valueIndex = 0;
        }
        queue();
        return true;
    }

    @Override
    public boolean isValueModified() {
        return !this.appliedValue.equals(getValue());
    }

    @Override
    public String getCommentKey() {
        return "option." + this.option.getName() + ".comment";
    }

    public String getValue() {
        if (this.valueCount <= 0 || this.valueIndex < 0) {
            return this.appliedValue != null ? this.appliedValue : this.option.getDefaultValue();
        }
        return this.option.getAllowedValues().get(this.valueIndex);
    }

    protected void queue() {
        MutableOptionValues values = this.screen != null ? this.screen.getWorkingOptionValues() : null;
        if (values != null) {
            values.setStringValue(this.option.getName(), getValue());
        }
        if (this.screen != null) {
            this.screen.markPendingChanges();
        }
        updateLabels();
    }

    protected void increment(int amount) {
        if (this.valueCount <= 0) {
            return;
        }

        this.valueIndex = Math.max(this.valueIndex, 0);
        this.valueIndex = Math.floorMod(this.valueIndex + amount, this.valueCount);
    }
}
