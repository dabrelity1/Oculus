package net.oculus.shaderpack.option;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import net.oculus.Oculus;

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
            addBoolean(new MergedBooleanOption(option));
            return this;
        }

        public Builder addString(StringOption option) {
            addString(new MergedStringOption(option));
            return this;
        }

        public Builder addAll(OptionSet set) {
            set.booleanOptions.values().forEach(this::addBoolean);
            set.stringOptions.values().forEach(this::addString);
            return this;
        }

        private void addBoolean(MergedBooleanOption proposed) {
            BooleanOption option = proposed.getOption();
            MergedBooleanOption existing = booleanOptions.get(option.getName());
            MergedBooleanOption merged = existing == null ? proposed : existing.merge(proposed);

            if (merged == null) {
                Oculus.LOGGER.warn("Ignoring ambiguous boolean option {}", option.getName());
                booleanOptions.remove(option.getName());
                return;
            }

            booleanOptions.put(option.getName(), merged);
        }

        private void addString(MergedStringOption proposed) {
            StringOption option = proposed.getOption();
            MergedStringOption existing = stringOptions.get(option.getName());
            MergedStringOption merged = existing == null ? proposed : existing.merge(proposed);

            if (merged == null) {
                Oculus.LOGGER.warn("Ignoring ambiguous string option {}", option.getName());
                stringOptions.remove(option.getName());
                return;
            }

            stringOptions.put(option.getName(), merged);
        }

        public OptionSet build() {
            return new OptionSet(booleanOptions, stringOptions);
        }
    }
}
