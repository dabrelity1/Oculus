package net.oculus.gl.program;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime-defined mapping between sampler uniforms and concrete texture units.
 * This mirrors the override tables provided by CustomTextureManager in the
 * modern pipeline while remaining lightweight for the 1.12.2 port.
 */
public final class SamplerOverrideMap {
    private static final SamplerOverrideMap EMPTY = new SamplerOverrideMap(Collections.emptyMap());

    private final Map<String, Integer> overrides;

    private SamplerOverrideMap(Map<String, Integer> overrides) {
        this.overrides = overrides;
    }

    public static SamplerOverrideMap empty() {
        return EMPTY;
    }

    public int resolve(String name) {
        if (name == null) {
            return -1;
        }
        return overrides.getOrDefault(name, -1);
    }

    public boolean isEmpty() {
        return overrides.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public SamplerOverrideMap merge(SamplerOverrideMap other) {
        if (other == null || other.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return other;
        }

        Map<String, Integer> merged = new HashMap<>(overrides);
        merged.putAll(other.overrides);
        return new SamplerOverrideMap(Collections.unmodifiableMap(merged));
    }

    public static final class Builder {
        private final Map<String, Integer> entries = new HashMap<>();

        public Builder put(String name, int unit) {
            if (name == null) {
                return this;
            }
            entries.put(name, unit);
            return this;
        }

        public Builder putAll(Map<String, Integer> aliasMap) {
            if (aliasMap == null) {
                return this;
            }
            aliasMap.forEach((key, value) -> {
                if (key != null && value != null) {
                    put(key, value);
                }
            });
            return this;
        }

        public SamplerOverrideMap build() {
            if (entries.isEmpty()) {
                return SamplerOverrideMap.empty();
            }
            return new SamplerOverrideMap(Collections.unmodifiableMap(new HashMap<>(entries)));
        }
    }
}
