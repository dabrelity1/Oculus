package net.oculus.shaderpack.option;

public class MergedBooleanOption {
    private final BooleanOption option;

    public MergedBooleanOption(BooleanOption option) {
        this.option = option;
    }

    public BooleanOption getOption() {
        return option;
    }
}
