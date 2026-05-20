package net.oculus.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import net.oculus.Oculus;
import net.oculus.colorspace.ColorSpace;

/**
 * Minimal configuration shim inspired by IrisConfig. Stores the user's shader
 * selection and simple toggles inside {@code config/oculus.properties} so the
 * GUI and keybinds can pick up the last known state.
 *
 * <p>Keys stored in the backing properties file:</p>
 * <ul>
 *     <li><strong>selectedPackName</strong> – Last pack highlighted in the GUI.</li>
 *     <li><strong>shadersEnabled</strong> – Whether shaders were last applied.</li>
 *     <li><strong>debugEnabled</strong> – Enables verbose logging hooks.</li>
 *     <li><strong>disableUpdateMessage</strong> – Suppresses update notifications when an update checker is present.</li>
 *     <li><strong>colorSpace</strong> – Output color-space transform selected by the user.</li>
 *     <li><strong>maxShadowRenderDistance</strong> – User shadow render distance in chunks.</li>
 *     <li><strong>option.&lt;pack&gt;.&lt;id&gt;</strong> – Per-pack override of shader option values.</li>
 * </ul>
 */
public final class OculusConfig {
    private static final String KEY_SELECTED_PACK = "selectedPackName";
    private static final String KEY_SHADERS_ENABLED = "shadersEnabled";
    private static final String KEY_DEBUG_ENABLED = "debugEnabled";
    private static final String KEY_DISABLE_UPDATE_MESSAGE = "disableUpdateMessage";
    private static final String KEY_COLOR_SPACE = "colorSpace";
    private static final String KEY_MAX_SHADOW_RENDER_DISTANCE = "maxShadowRenderDistance";
    private static final String LEGACY_KEY_SELECTED_PACK = "shaderPack";
    private static final String LEGACY_KEY_SHADERS_ENABLED = "enableShaders";
    private static final String LEGACY_KEY_DEBUG_ENABLED = "enableDebugOptions";
    private static final String OPTION_PREFIX = "option.";
    private static final String LEGACY_OVERRIDE_KEY = "legacy";
    public static final int DEFAULT_MAX_SHADOW_RENDER_DISTANCE = 32;

    private final Path configPath;
    private final Map<String, Map<String, String>> overridesByPack = new HashMap<>();

    private String selectedPackName;
    private boolean shadersEnabled;
    private boolean debugEnabled;
    private boolean disableUpdateMessage;
    private ColorSpace colorSpace;
    private int maxShadowRenderDistance;

    public OculusConfig(Path configPath) {
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.selectedPackName = null;
        this.shadersEnabled = true;
        this.debugEnabled = false;
        this.disableUpdateMessage = false;
        this.colorSpace = ColorSpace.SRGB;
        this.maxShadowRenderDistance = DEFAULT_MAX_SHADOW_RENDER_DISTANCE;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public synchronized void initialize() throws IOException {
        boolean exists = Files.exists(configPath);
        String previousSelectedPackName = this.selectedPackName;
        boolean previousShadersEnabled = this.shadersEnabled;
        boolean previousDebugEnabled = this.debugEnabled;
        boolean previousDisableUpdateMessage = this.disableUpdateMessage;
        ColorSpace previousColorSpace = this.colorSpace;
        int previousMaxShadowRenderDistance = this.maxShadowRenderDistance;
        Map<String, Map<String, String>> previousOverrides = deepCopyOverrides(this.overridesByPack);

        load();
        if (!exists) {
            try {
                save();
            } catch (IOException exception) {
                applyLoadedState(
                    previousSelectedPackName,
                    previousShadersEnabled,
                    previousDebugEnabled,
                    previousDisableUpdateMessage,
                    previousColorSpace,
                    previousMaxShadowRenderDistance,
                    previousOverrides);
                throw exception;
            }
        }
    }

    public synchronized void load() throws IOException {
        String previousSelectedPackName = this.selectedPackName;
        boolean previousShadersEnabled = this.shadersEnabled;
        boolean previousDebugEnabled = this.debugEnabled;
        boolean previousDisableUpdateMessage = this.disableUpdateMessage;
        ColorSpace previousColorSpace = this.colorSpace;
        int previousMaxShadowRenderDistance = this.maxShadowRenderDistance;
        Map<String, Map<String, String>> previousOverrides = deepCopyOverrides(this.overridesByPack);

        Map<String, Map<String, String>> loadedOverrides = new HashMap<>();
        Map<String, String> legacyOverrides = new HashMap<>();

        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            }
        }

        String loadedSelectedPackName = normalizeSelectedPackName(getProperty(properties, KEY_SELECTED_PACK, LEGACY_KEY_SELECTED_PACK));
        boolean loadedShadersEnabled = !"false".equals(getProperty(properties, KEY_SHADERS_ENABLED, LEGACY_KEY_SHADERS_ENABLED));
        boolean loadedDebugEnabled = "true".equals(getProperty(properties, KEY_DEBUG_ENABLED, LEGACY_KEY_DEBUG_ENABLED));
        boolean loadedDisableUpdateMessage = "true".equals(properties.getProperty(KEY_DISABLE_UPDATE_MESSAGE));

        String colorSpaceValue = properties.getProperty(KEY_COLOR_SPACE);
        boolean invalidColorSpace = colorSpaceValue != null && !ColorSpace.isConfigValueRecognized(colorSpaceValue);
        boolean invalidShadowDistance = false;
        int loadedShadowDistance = DEFAULT_MAX_SHADOW_RENDER_DISTANCE;
        try {
            loadedShadowDistance = Integer.parseInt(
                properties.getProperty(KEY_MAX_SHADOW_RENDER_DISTANCE, Integer.toString(DEFAULT_MAX_SHADOW_RENDER_DISTANCE)));
        } catch (IllegalArgumentException exception) {
            invalidShadowDistance = true;
        }

        ColorSpace loadedColorSpace = invalidColorSpace || invalidShadowDistance
            ? ColorSpace.SRGB
            : ColorSpace.fromConfigValue(colorSpaceValue);
        int loadedMaxShadowRenderDistance = invalidColorSpace || invalidShadowDistance
            ? DEFAULT_MAX_SHADOW_RENDER_DISTANCE
            : loadedShadowDistance;

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(OPTION_PREFIX)) {
                continue;
            }

            String remainder = key.substring(OPTION_PREFIX.length());
            int separator = remainder.lastIndexOf('.');

            if (separator <= 0 || separator >= remainder.length() - 1) {
                legacyOverrides.put(remainder, properties.getProperty(key));
                continue;
            }

            String packKey = canonicalizePackName(remainder.substring(0, separator));
            String optionId = remainder.substring(separator + 1);
            putOverride(loadedOverrides, packKey, optionId, properties.getProperty(key));
        }

        if (!legacyOverrides.isEmpty()) {
            String legacyPack = canonicalizePackName(loadedSelectedPackName);
            Map<String, String> merged = loadedOverrides.computeIfAbsent(legacyPack, key -> new HashMap<>());
            merged.putAll(legacyOverrides);
            Oculus.LOGGER.info("Migrated legacy shader option overrides to pack {}", legacyPack);
        }

        if (invalidColorSpace) {
            Oculus.LOGGER.error("Color space setting reset; value is invalid.");
        }
        if (invalidShadowDistance) {
            Oculus.LOGGER.error("Shadow distance setting reset; value is invalid.");
        }

        applyLoadedState(
            loadedSelectedPackName,
            loadedShadersEnabled,
            loadedDebugEnabled,
            loadedDisableUpdateMessage,
            loadedColorSpace,
            loadedMaxShadowRenderDistance,
            loadedOverrides);

        if (invalidColorSpace || invalidShadowDistance) {
            try {
                save();
            } catch (IOException exception) {
                applyLoadedState(
                    previousSelectedPackName,
                    previousShadersEnabled,
                    previousDebugEnabled,
                    previousDisableUpdateMessage,
                    previousColorSpace,
                    previousMaxShadowRenderDistance,
                    previousOverrides);
                throw exception;
            }
        }
    }

    public synchronized void save() throws IOException {
        Properties properties = new Properties();

        if (selectedPackName != null && !selectedPackName.isEmpty()) {
            properties.setProperty(KEY_SELECTED_PACK, selectedPackName);
            properties.setProperty(LEGACY_KEY_SELECTED_PACK, selectedPackName);
        } else {
            properties.setProperty(LEGACY_KEY_SELECTED_PACK, "");
        }

        properties.setProperty(KEY_SHADERS_ENABLED, Boolean.toString(shadersEnabled));
        properties.setProperty(LEGACY_KEY_SHADERS_ENABLED, Boolean.toString(shadersEnabled));
        properties.setProperty(KEY_DEBUG_ENABLED, Boolean.toString(debugEnabled));
        properties.setProperty(LEGACY_KEY_DEBUG_ENABLED, Boolean.toString(debugEnabled));
        properties.setProperty(KEY_DISABLE_UPDATE_MESSAGE, Boolean.toString(disableUpdateMessage));
        properties.setProperty(KEY_COLOR_SPACE, colorSpace.name());
        properties.setProperty(KEY_MAX_SHADOW_RENDER_DISTANCE, Integer.toString(maxShadowRenderDistance));

        overridesByPack.forEach((packKey, overrides) -> {
            overrides.forEach((optionId, value) -> {
                if (value != null) {
                    properties.setProperty(OPTION_PREFIX + packKey + '.' + optionId, value);
                }
            });
        });

        if (configPath.getParent() != null) {
            Files.createDirectories(configPath.getParent());
        }

        try (OutputStream output = Files.newOutputStream(configPath)) {
            properties.store(output, "Oculus configuration");
        }
    }

    public synchronized String getSelectedPackName() {
        return selectedPackName;
    }

    public synchronized void setSelectedPackName(String selectedPackName) {
        this.selectedPackName = normalizeSelectedPackName(selectedPackName);
    }

    public synchronized boolean areShadersEnabled() {
        return shadersEnabled;
    }

    public synchronized void setShadersEnabled(boolean shadersEnabled) {
        this.shadersEnabled = shadersEnabled;
    }

    public synchronized boolean isDebugEnabled() {
        return debugEnabled;
    }

    public synchronized void setDebugEnabled(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public synchronized boolean shouldDisableUpdateMessage() {
        return disableUpdateMessage;
    }

    public synchronized void setDisableUpdateMessage(boolean disableUpdateMessage) {
        this.disableUpdateMessage = disableUpdateMessage;
    }

    public synchronized ColorSpace getColorSpace() {
        return colorSpace;
    }

    public synchronized void setColorSpace(ColorSpace colorSpace) {
        this.colorSpace = colorSpace == null ? ColorSpace.SRGB : colorSpace;
    }

    public synchronized int getMaxShadowRenderDistance() {
        return maxShadowRenderDistance;
    }

    public synchronized void setMaxShadowRenderDistance(int maxShadowRenderDistance) {
        this.maxShadowRenderDistance = maxShadowRenderDistance;
    }

    public synchronized Map<String, Map<String, String>> getShaderOptionOverrides() {
        Map<String, Map<String, String>> deepCopy = new HashMap<>();
        overridesByPack.forEach((pack, overrides) ->
            deepCopy.put(pack, Collections.unmodifiableMap(new HashMap<>(overrides)))
        );
        return Collections.unmodifiableMap(deepCopy);
    }

    @Deprecated
    public synchronized void setShaderOptionOverride(String option, String value) {
        if (option == null || option.isEmpty()) {
            return;
        }

        String packKey = canonicalizePackName(this.selectedPackName);
        Map<String, String> overrides = overridesByPack.computeIfAbsent(packKey, key -> new HashMap<>());
        if (value == null) {
            overrides.remove(option);
        } else {
            overrides.put(option, value);
        }

        if (overrides.isEmpty()) {
            overridesByPack.remove(packKey);
        }
    }

    public synchronized void clearShaderOptionOverrides() {
        overridesByPack.clear();
    }

    public synchronized Map<String, String> getOptionOverrides(String packName) {
        String canonical = canonicalizePackName(packName);
        Map<String, String> overrides = overridesByPack.get(canonical);
        return overrides == null ? Collections.emptyMap() : new HashMap<>(overrides);
    }

    public synchronized void setOptionOverrides(String packName, Map<String, String> values) {
        String canonical = canonicalizePackName(packName);
        if (values == null || values.isEmpty()) {
            overridesByPack.remove(canonical);
            return;
        }
        overridesByPack.put(canonical, new HashMap<>(values));
    }

    public synchronized void clearOptionOverrides(String packName) {
        overridesByPack.remove(canonicalizePackName(packName));
    }

    public static String canonicalizePackName(String rawName) {
        if (rawName == null) {
            return "internal";
        }

        String canonical = rawName.trim().toLowerCase(Locale.ROOT);
        if (canonical.isEmpty() || "(internal)".equals(canonical)) {
            canonical = "internal";
        }

        canonical = canonical.replace('\\', '/');
        canonical = canonical.replaceAll("[\\s/]+", "_");
        canonical = canonical.replaceAll("[^a-z0-9._-]", "_");

        if (canonical.isEmpty()) {
            return "internal";
        }

        return canonical;
    }

    private static String normalizeSelectedPackName(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty() || "(internal)".equals(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private static String getProperty(Properties properties, String key, String legacyKey) {
        if (properties.containsKey(key)) {
            return properties.getProperty(key);
        }
        return properties.getProperty(legacyKey);
    }

    private static String getProperty(Properties properties, String key, String legacyKey, String defaultValue) {
        String value = getProperty(properties, key, legacyKey);
        return value == null ? defaultValue : value;
    }

    private void applyLoadedState(String selectedPackName, boolean shadersEnabled, boolean debugEnabled,
                                  boolean disableUpdateMessage, ColorSpace colorSpace,
                                  int maxShadowRenderDistance,
                                  Map<String, Map<String, String>> loadedOverrides) {
        this.selectedPackName = selectedPackName;
        this.shadersEnabled = shadersEnabled;
        this.debugEnabled = debugEnabled;
        this.disableUpdateMessage = disableUpdateMessage;
        this.colorSpace = colorSpace == null ? ColorSpace.SRGB : colorSpace;
        this.maxShadowRenderDistance = maxShadowRenderDistance;

        this.overridesByPack.clear();
        this.overridesByPack.putAll(deepCopyOverrides(loadedOverrides));
    }

    private static Map<String, Map<String, String>> deepCopyOverrides(Map<String, Map<String, String>> source) {
        Map<String, Map<String, String>> copy = new HashMap<>();
        source.forEach((pack, overrides) -> copy.put(pack, new HashMap<>(overrides)));
        return copy;
    }

    private static void putOverride(Map<String, Map<String, String>> target, String packKey, String optionId,
                                    String value) {
        if (optionId == null || optionId.isEmpty()) {
            return;
        }

        target
            .computeIfAbsent(packKey == null || packKey.isEmpty() ? LEGACY_OVERRIDE_KEY : canonicalizePackName(packKey), key -> new HashMap<>())
            .put(optionId, value);
    }
}
