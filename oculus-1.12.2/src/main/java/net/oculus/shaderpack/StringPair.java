package net.oculus.shaderpack;

/**
 * Simple key/value tuple used for passing preprocessor defines around.
 */
public final class StringPair {
    private final String key;
    private final String value;

    public StringPair(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
