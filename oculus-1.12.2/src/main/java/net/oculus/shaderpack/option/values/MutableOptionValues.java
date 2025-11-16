package net.oculus.shaderpack.option.values;

import java.util.Map;

import net.oculus.shaderpack.option.OptionSet;

public class MutableOptionValues extends OptionValues {
    public MutableOptionValues(OptionSet optionSet) {
        super(optionSet);
    }

    public MutableOptionValues(OptionSet optionSet, Map<String, String> initialValues) {
        super(optionSet, initialValues);
    }

    public void setBooleanValue(String name, boolean value) {
        values.put(name, Boolean.toString(value));
    }

    public void setStringValue(String name, String value) {
        values.put(name, value);
    }

    public void setFloatValue(String name, float value) {
        values.put(name, Float.toString(value));
    }

    public void addAll(Map<String, String> other) {
        values.putAll(other);
    }

    public void clearAll() {
        values.clear();
    }
}
