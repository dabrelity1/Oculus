package net.oculus.shaderpack.option;

public class MergedStringOption {
    private final StringOption option;

    public MergedStringOption(StringOption option) {
        this.option = option;
    }

    public StringOption getOption() {
        return option;
    }
}
