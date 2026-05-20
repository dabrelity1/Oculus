package net.oculus.shaderpack;

import java.util.Objects;

/**
 * An absurdly simple class for storing pairs of strings because Java lacks pair / tuple types.
 */
public class StringPair {
    private final String key;
    private final String value;

    public StringPair(String key, String value) {
        this.key = Objects.requireNonNull(key);
        this.value = Objects.requireNonNull(value);
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
