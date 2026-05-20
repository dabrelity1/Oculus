package net.oculus.shaderpack.option;

import java.util.Arrays;
import java.util.List;

public class BooleanOption extends BaseOption<BooleanOption> {
    private final boolean defaultValue;

    public BooleanOption(OptionType type, String name, String comment, boolean defaultValue) {
        super(type, name, comment);
        this.defaultValue = defaultValue;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }

    @Override
    public BooleanOption merge(BooleanOption other) {
        if (!matchesType(other) || defaultValue != other.defaultValue) {
            throw new IllegalArgumentException("Conflicting boolean option definitions for " + name);
        }
        return this;
    }

    @Override
    public boolean matchesType(BooleanOption other) {
        return other != null && other.getType() == getType() && other.name.equals(name);
    }

    @Override
    public List<String> getAllowedValues() {
        return Arrays.asList("false", "true");
    }
}
