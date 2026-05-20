package net.oculus.shaderpack.option;

public class MergedBooleanOption {
    private final BooleanOption option;

    public MergedBooleanOption(BooleanOption option) {
        this.option = option;
    }

    public BooleanOption getOption() {
        return option;
    }

    public MergedBooleanOption merge(MergedBooleanOption other) {
        if (this.option.getDefaultValue() != other.option.getDefaultValue()) {
            return null;
        }

        if (!this.option.getComment().isEmpty()) {
            return this;
        }

        return other;
    }
}
