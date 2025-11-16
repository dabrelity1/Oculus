package net.oculus.shaderpack.option;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.ImmutableList;

public class StringOption extends BaseOption<StringOption> {
    private final String defaultValue;
    private final ImmutableList<String> allowedValues;

    private StringOption(OptionType type, String name, String comment, String defaultValue, ImmutableList<String> allowedValues) {
        super(type, name, comment);
        this.defaultValue = Objects.requireNonNull(defaultValue);
        this.allowedValues = allowedValues;
    }

    public static StringOption create(OptionType type, String name, String comment, String defaultValue) {
        if (comment == null) {
            return null;
        }

        int openingBracket = comment.indexOf('[');

        if (openingBracket == -1) {
            return null;
        }

        int closingBracket = comment.indexOf(']', openingBracket);

        if (closingBracket == -1) {
            return null;
        }

        String[] allowedValues = comment.substring(openingBracket + 1, closingBracket).split(" ");
        comment = comment.substring(0, openingBracket) + comment.substring(closingBracket + 1);
        boolean allowedValuesContainDefault = false;

        for (String value : allowedValues) {
            if (defaultValue.equals(value)) {
                allowedValuesContainDefault = true;
                break;
            }
        }

        ImmutableList.Builder<String> builder = ImmutableList.builder();
        builder.add(allowedValues);

        if (!allowedValuesContainDefault) {
            builder.add(defaultValue);
        }

        return new StringOption(type, name, comment.trim(), defaultValue, builder.build());
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public StringOption merge(StringOption other) {
        if (!matchesType(other)) {
            throw new IllegalArgumentException("Conflicting string option definitions for " + name);
        }

        if (!defaultValue.equals(other.defaultValue)) {
            throw new IllegalArgumentException("Conflicting default values for string option " + name);
        }

        return this;
    }

    @Override
    public boolean matchesType(StringOption other) {
        return other != null && other.getType() == getType() && other.name.equals(name);
    }

    @Override
    public List<String> getAllowedValues() {
        return allowedValues;
    }
}
