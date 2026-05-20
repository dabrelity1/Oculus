package net.oculus.gui.element.widget;

import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import net.oculus.gui.GuiUtil;
import net.oculus.gui.NavigationController;
import net.oculus.gui.ShaderPackScreen;
import net.oculus.shaderpack.option.BooleanOption;
import net.oculus.shaderpack.option.MergedBooleanOption;
import net.oculus.shaderpack.option.menu.OptionMenuBooleanOptionElement;
import net.oculus.shaderpack.option.values.MutableOptionValues;

public class BooleanElementWidget extends BaseOptionElementWidget<OptionMenuBooleanOptionElement> {
    private static final ITextComponent TEXT_TRUE = translated("label.iris.true", TextFormatting.GREEN);
    private static final ITextComponent TEXT_FALSE = translated("label.iris.false", TextFormatting.RED);
    private static final ITextComponent TEXT_TRUE_DEFAULT = new TextComponentTranslation("label.iris.true");
    private static final ITextComponent TEXT_FALSE_DEFAULT = new TextComponentTranslation("label.iris.false");

    private final BooleanOption option;

    private boolean appliedValue;
    private boolean value;
    private boolean defaultValue;

    public BooleanElementWidget(OptionMenuBooleanOptionElement element) {
        super(element);
        this.option = element.option;
    }

    @Override
    public void init(ShaderPackScreen screen, NavigationController navigation) {
        super.init(screen, navigation);
        this.element.setPendingValuesSupplier(screen::getWorkingOptionValues);
        this.appliedValue = this.element.getAppliedOptionValues().getBooleanValueOrDefault(this.option.getName());
        this.value = this.element.getPendingOptionValues().getBooleanValueOrDefault(this.option.getName());

        MergedBooleanOption merged = this.element.getAppliedOptionValues().getOptionSet().getBooleanOptions().get(this.option.getName());
        this.defaultValue = merged != null ? merged.getOption().getDefaultValue() : this.option.getDefaultValue();

        setLabel(GuiUtil.translateShaderPackOrDefault(screen.getCurrentPack(), new TextComponentString(this.option.getName()), "option." + this.option.getName()));
    }

    @Override
    public void render(int x, int y, int width, int height, int mouseX, int mouseY, float partialTicks, boolean hovered) {
        updateRenderParams(width, 28);
        renderOptionWithValue(x, y, width, height, hovered);
        tryRenderTooltip(mouseX, mouseY, hovered);
    }

    @Override
    protected ITextComponent createValueLabel() {
        return (this.value == this.defaultValue)
            ? (this.value ? TEXT_TRUE_DEFAULT.createCopy() : TEXT_FALSE_DEFAULT.createCopy())
            : (this.value ? TEXT_TRUE.createCopy() : TEXT_FALSE.createCopy());
    }

    @Override
    public boolean applyNextValue() {
        this.value = !this.value;
        queue();
        return true;
    }

    @Override
    public boolean applyPreviousValue() {
        return applyNextValue();
    }

    @Override
    public boolean applyOriginalValue() {
        this.value = this.option.getDefaultValue();
        queue();
        return true;
    }

    @Override
    public boolean isValueModified() {
        return this.value != this.appliedValue;
    }

    @Override
    public String getCommentKey() {
        return "option." + this.option.getName() + ".comment";
    }

    private void queue() {
        MutableOptionValues values = this.screen != null ? this.screen.getWorkingOptionValues() : null;
        if (values != null) {
            values.setBooleanValue(this.option.getName(), this.value);
        }
        if (this.screen != null) {
            this.screen.markPendingChanges();
        }
        updateLabels();
    }

    private static ITextComponent translated(String key, TextFormatting color) {
        TextComponentTranslation component = new TextComponentTranslation(key);
        Style style = component.getStyle();
        if (style == null) {
            style = new Style();
            component.setStyle(style);
        }
        style.setColor(color);
        return component;
    }
}
