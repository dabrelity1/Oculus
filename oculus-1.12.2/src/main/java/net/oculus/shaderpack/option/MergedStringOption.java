package net.oculus.shaderpack.option;

public class MergedStringOption {
    private final StringOption option;

    public MergedStringOption(StringOption option) {
        this.option = option;
    }

    public StringOption getOption() {
        return option;
    }

    public MergedStringOption merge(MergedStringOption other) {
        if (!this.option.getDefaultValue().equals(other.option.getDefaultValue())) {
            return null;
        }

        if (!this.option.getComment().isEmpty()) {
            return this;
        }

        return other;
    }
}
