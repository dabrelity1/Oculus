package net.oculus.colorspace;

import java.util.Locale;

/**
 * Mirrors the public color-space enum order from Oculus 1.16.5. Shader-facing
 * ordinals must stay stable because packs compare these values directly.
 */
public enum ColorSpace {
    SRGB,
    DCI_P3,
    DISPLAY_P3,
    REC2020,
    ADOBE_RGB;

    public static ColorSpace fromConfigValue(String value) {
        if (value == null) {
            return SRGB;
        }

        String normalized = normalizeConfigValue(value);
        if (normalized.isEmpty()) {
            return SRGB;
        }

        try {
            return ColorSpace.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return SRGB;
        }
    }

    public static boolean isConfigValueRecognized(String value) {
        if (value == null) {
            return true;
        }

        String normalized = normalizeConfigValue(value);
        if (normalized.isEmpty()) {
            return true;
        }

        try {
            ColorSpace.valueOf(normalized);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static String normalizeConfigValue(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
    }
}
