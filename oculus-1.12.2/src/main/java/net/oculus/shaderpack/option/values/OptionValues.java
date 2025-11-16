package net.oculus.shaderpack.option.values;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import net.oculus.shaderpack.option.MergedBooleanOption;
import net.oculus.shaderpack.option.MergedStringOption;
import net.oculus.shaderpack.option.OptionSet;

public class OptionValues {
    protected final OptionSet optionSet;
    protected final Map<String, String> values;

    public OptionValues(OptionSet optionSet) {
        this(optionSet, Collections.emptyMap());
    }

    public OptionValues(OptionSet optionSet, Map<String, String> initialValues) {
        this.optionSet = optionSet;
        this.values = new HashMap<>(initialValues);
    }

    public boolean getBooleanValueOrDefault(String name) {
        if (values.containsKey(name)) {
            return Boolean.parseBoolean(values.get(name));
        }

        MergedBooleanOption option = optionSet.getBooleanOptions().get(name);
        return option != null && option.getOption().getDefaultValue();
    }

    public String getStringValueOrDefault(String name) {
        if (values.containsKey(name)) {
            return values.get(name);
        }

        MergedStringOption option = optionSet.getStringOptions().get(name);
        return option != null ? option.getOption().getDefaultValue() : "";
    }

    public float getFloatValueOrDefault(String name, float defaultValue) {
        if (values.containsKey(name)) {
            try {
                return Float.parseFloat(values.get(name));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }

        return defaultValue;
    }

    public int getOptionsChanged() {
        return values.size();
    }

    public MutableOptionValues mutableCopy() {
        return new MutableOptionValues(optionSet, values);
    }

    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public OptionSet getOptionSet() {
        return optionSet;
    }
}
