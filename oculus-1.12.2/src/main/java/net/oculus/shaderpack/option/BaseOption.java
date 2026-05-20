package net.oculus.shaderpack.option;

import java.util.List;

public abstract class BaseOption<T extends BaseOption<T>> {
    private final OptionType type;
    protected final String name;
    protected final String comment;

    protected BaseOption(OptionType type, String name, String comment) {
        this.type = type;
        this.name = name;
        this.comment = comment == null ? "" : comment;
    }

    public OptionType getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getComment() {
        return comment;
    }

    public abstract T merge(T other);

    public abstract boolean matchesType(T other);

    public abstract List<String> getAllowedValues();
}
