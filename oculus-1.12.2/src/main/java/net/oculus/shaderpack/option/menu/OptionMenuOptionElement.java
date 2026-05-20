package net.oculus.shaderpack.option.menu;

import java.util.function.Supplier;

import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.option.values.OptionValues;

public abstract class OptionMenuOptionElement extends OptionMenuElement {
    public final boolean slider;
    public final OptionMenuContainer container;
    public final String optionId;

    private final OptionValues packAppliedValues;
    private Supplier<MutableOptionValues> pendingValuesSupplier;

    public OptionMenuOptionElement(String elementString,
                                   OptionMenuContainer container,
                                   ShaderProperties shaderProperties,
                                   OptionValues packAppliedValues) {
        this.slider = shaderProperties != null && shaderProperties.getSliderOptions().contains(elementString);
        this.container = container;
        this.optionId = elementString;
        this.packAppliedValues = packAppliedValues;
    }

    public OptionValues getAppliedOptionValues() {
        return this.packAppliedValues;
    }

    public void setPendingValuesSupplier(Supplier<MutableOptionValues> pendingValuesSupplier) {
        this.pendingValuesSupplier = pendingValuesSupplier;
    }

    public OptionValues getPendingOptionValues() {
        MutableOptionValues values = this.packAppliedValues != null
            ? this.packAppliedValues.mutableCopy()
            : null;

        MutableOptionValues pending = this.pendingValuesSupplier != null ? this.pendingValuesSupplier.get() : null;
        if (pending != null) {
            if (values == null) {
                values = pending.mutableCopy();
            } else {
                values.addAll(pending.asMap());
            }
        }

        return values != null ? values : new MutableOptionValues(net.oculus.shaderpack.option.OptionSet.builder().build());
    }
}
