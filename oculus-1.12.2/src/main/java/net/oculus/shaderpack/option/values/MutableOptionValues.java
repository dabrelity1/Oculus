package net.oculus.shaderpack.option.values;

import java.util.Map;

import net.oculus.shaderpack.option.MergedBooleanOption;
import net.oculus.shaderpack.option.MergedStringOption;
import net.oculus.shaderpack.option.OptionSet;

public class MutableOptionValues extends OptionValues {
    public MutableOptionValues(OptionSet optionSet) {
        super(optionSet);
    }

    public MutableOptionValues(OptionSet optionSet, Map<String, String> initialValues) {
        super(optionSet, initialValues);
    }

    public void setBooleanValue(String name, boolean value) {
        MergedBooleanOption option = optionSet.getBooleanOptions().get(name);
        if (option == null) {
            return;
        }

        if (value == option.getOption().getDefaultValue()) {
            values.remove(name);
        } else {
            values.put(name, Boolean.toString(value));
        }
    }

    public void setStringValue(String name, String value) {
        MergedStringOption option = optionSet.getStringOptions().get(name);
        if (option == null) {
            return;
        }

        if (option.getOption().getDefaultValue().equals(value)) {
            values.remove(name);
        } else {
            values.put(name, value);
        }
    }

    public void setFloatValue(String name, float value) {
        setStringValue(name, Float.toString(value));
    }

    public void addAll(Map<String, String> other) {
        super.addAll(other);
    }

    public void clearAll() {
        values.clear();
    }
}
