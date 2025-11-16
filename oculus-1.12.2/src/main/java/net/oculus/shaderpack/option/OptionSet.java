package net.oculus.shaderpack.option;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OptionSet {
    private final Map<String, MergedBooleanOption> booleanOptions;
    private final Map<String, MergedStringOption> stringOptions;

    private OptionSet(Map<String, MergedBooleanOption> booleanOptions,
                      Map<String, MergedStringOption> stringOptions) {
        this.booleanOptions = Collections.unmodifiableMap(booleanOptions);
        this.stringOptions = Collections.unmodifiableMap(stringOptions);
    }

    public Map<String, MergedBooleanOption> getBooleanOptions() {
        return booleanOptions;
    }

    public Map<String, MergedStringOption> getStringOptions() {
        return stringOptions;
    }

    public boolean isBooleanOption(String name) {
        return booleanOptions.containsKey(name);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<String, MergedBooleanOption> booleanOptions = new LinkedHashMap<>();
        private final Map<String, MergedStringOption> stringOptions = new LinkedHashMap<>();

        public Builder addBoolean(BooleanOption option) {
            booleanOptions.put(option.getName(), new MergedBooleanOption(option));
            return this;
        }

        public Builder addString(StringOption option) {
            stringOptions.put(option.getName(), new MergedStringOption(option));
            return this;
        }

        public Builder addAll(OptionSet set) {
            booleanOptions.putAll(set.booleanOptions);
            stringOptions.putAll(set.stringOptions);
            return this;
        }

        public OptionSet build() {
            return new OptionSet(booleanOptions, stringOptions);
        }
    }
}
