package net.oculus.shaderpack.option.menu;

import java.util.function.Supplier;

import net.oculus.shaderpack.option.OptionSet;
import net.oculus.shaderpack.option.ProfileSet;
import net.oculus.shaderpack.option.values.MutableOptionValues;
import net.oculus.shaderpack.option.values.OptionValues;

/**
 * Menu element that exposes shader pack profiles to the option widgets.
 * Mirrors the Iris 1.16.5 implementation but pulls pending overrides from the
 * legacy screen's working option values.
 */
public class OptionMenuProfileElement extends OptionMenuElement {
    public final ProfileSet profiles;
    public final OptionSet options;

    private final OptionValues packAppliedValues;
    private Supplier<MutableOptionValues> pendingValuesSupplier;

    public OptionMenuProfileElement(ProfileSet profiles, OptionSet options, OptionValues packAppliedValues) {
        this.profiles = profiles;
        this.options = options;
        this.packAppliedValues = packAppliedValues;
    }

    public void setPendingValuesSupplier(Supplier<MutableOptionValues> pendingValuesSupplier) {
        this.pendingValuesSupplier = pendingValuesSupplier;
    }

    /**
     * @return an OptionValues snapshot that merges the pack's applied settings
     * with any overrides currently queued in the GUI.
     */
    public OptionValues getPendingOptionValues() {
        MutableOptionValues values = this.packAppliedValues != null
            ? this.packAppliedValues.mutableCopy()
            : createEmptyValues();

        MutableOptionValues pending = fetchPendingValues();
        if (pending != null) {
            values.addAll(pending.asMap());
        }

        return values;
    }

    private MutableOptionValues fetchPendingValues() {
        return this.pendingValuesSupplier != null ? this.pendingValuesSupplier.get() : null;
    }

    private MutableOptionValues createEmptyValues() {
        OptionSet optionSet = this.options != null ? this.options : emptyOptionSet();
        return new MutableOptionValues(optionSet);
    }

    private static OptionSet emptyOptionSet() {
        return OptionSet.builder().build();
    }
}
