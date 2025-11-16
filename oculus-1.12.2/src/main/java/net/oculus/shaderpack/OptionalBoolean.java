package net.oculus.shaderpack;

import java.util.function.BooleanSupplier;

/**
 * Simplified tri-state boolean used throughout the shader pack metadata pipeline.
 */
public enum OptionalBoolean {
    DEFAULT,
    FALSE,
    TRUE;

    public boolean orElse(boolean defaultValue) {
        if (this == DEFAULT) {
            return defaultValue;
        }
        return this == TRUE;
    }

    public boolean orElseGet(BooleanSupplier supplier) {
        if (this == DEFAULT) {
            return supplier.getAsBoolean();
        }
        return this == TRUE;
    }
}
