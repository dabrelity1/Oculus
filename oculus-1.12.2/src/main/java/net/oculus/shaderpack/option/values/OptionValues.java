package net.oculus.shaderpack.option.values;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.oculus.Oculus;
import net.oculus.shaderpack.OptionalBoolean;
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
        this.values = new HashMap<>();
        addAll(initialValues);
    }

    public OptionalBoolean getBooleanValue(String name) {
        if (!optionSet.getBooleanOptions().containsKey(name) || !values.containsKey(name)) {
            return OptionalBoolean.DEFAULT;
        }

        return Boolean.parseBoolean(values.get(name)) ? OptionalBoolean.TRUE : OptionalBoolean.FALSE;
    }

    public Optional<String> getStringValue(String name) {
        if (!optionSet.getStringOptions().containsKey(name)) {
            return Optional.empty();
        }

        return Optional.ofNullable(values.get(name));
    }

    public boolean getBooleanValueOrDefault(String name) {
        OptionalBoolean value = getBooleanValue(name);

        return value.orElseGet(() -> {
            MergedBooleanOption option = optionSet.getBooleanOptions().get(name);
            if (option == null) {
                Oculus.LOGGER.warn("Tried to get boolean value for unknown option: {}, defaulting to true!", name);
                return true;
            }

            return option.getOption().getDefaultValue();
        });
    }

    public String getStringValueOrDefault(String name) {
        Optional<String> value = getStringValue(name);

        return value.orElseGet(() -> {
            MergedStringOption option = optionSet.getStringOptions().get(name);
            return option != null ? option.getOption().getDefaultValue() : "";
        });
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

    protected void addAll(Map<String, String> newValues) {
        if (newValues == null) {
            return;
        }

        optionSet.getBooleanOptions().forEach((name, option) -> {
            String value = newValues.get(name);
            if (value == null) {
                return;
            }

            Boolean parsed = parseBoolean(value);
            boolean actual = parsed == null ? option.getOption().getDefaultValue() : parsed;

            if (actual == option.getOption().getDefaultValue()) {
                values.remove(name);
            } else {
                values.put(name, Boolean.toString(actual));
            }
        });

        optionSet.getStringOptions().forEach((name, option) -> {
            String value = newValues.get(name);
            if (value == null) {
                return;
            }

            if (value.equals(option.getOption().getDefaultValue())) {
                values.remove(name);
            } else {
                values.put(name, value);
            }
        });
    }

    private static Boolean parseBoolean(String value) {
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
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
