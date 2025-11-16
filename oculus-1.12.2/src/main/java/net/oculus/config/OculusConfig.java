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
 *     <li><strong>option.&lt;pack&gt;.&lt;id&gt;</strong> – Per-pack override of shader option values.</li>
 * </ul>
 */
public final class OculusConfig {
    private static final String KEY_SELECTED_PACK = "selectedPackName";
    private static final String KEY_SHADERS_ENABLED = "shadersEnabled";
    private static final String KEY_DEBUG_ENABLED = "debugEnabled";
    private static final String OPTION_PREFIX = "option.";
     private static final String LEGACY_OVERRIDE_KEY = "legacy";

    private final Path configPath;
     private final Map<String, Map<String, String>> overridesByPack = new HashMap<>();

    private String selectedPackName;
    private boolean shadersEnabled;
    private boolean debugEnabled;

    public OculusConfig(Path configPath) {
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.selectedPackName = null;
        this.shadersEnabled = false;
        this.debugEnabled = false;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public synchronized void load() throws IOException {
        overridesByPack.clear();
        Map<String, String> legacyOverrides = new HashMap<>();

        Properties properties = new Properties();
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
            }
        }

        this.selectedPackName = trim(properties.getProperty(KEY_SELECTED_PACK));
        this.shadersEnabled = Boolean.parseBoolean(properties.getProperty(KEY_SHADERS_ENABLED, "false"));
        this.debugEnabled = Boolean.parseBoolean(properties.getProperty(KEY_DEBUG_ENABLED, "false"));

        for (String key : properties.stringPropertyNames()) {
            if (!key.startsWith(OPTION_PREFIX)) {
                continue;
            }

            String remainder = key.substring(OPTION_PREFIX.length());
            int separator = remainder.indexOf('.');

            if (separator <= 0 || separator >= remainder.length() - 1) {
                legacyOverrides.put(remainder, properties.getProperty(key));
                continue;
            }

            String packKey = canonicalizePackName(remainder.substring(0, separator));
            String optionId = remainder.substring(separator + 1);
            putOverride(packKey, optionId, properties.getProperty(key));
        }

        if (!legacyOverrides.isEmpty()) {
            String legacyPack = canonicalizePackName(this.selectedPackName);
            Map<String, String> merged = overridesByPack.computeIfAbsent(legacyPack, key -> new HashMap<>());
            merged.putAll(legacyOverrides);
            Oculus.LOGGER.info("Migrated legacy shader option overrides to pack {}", legacyPack);
        }
    }

    public synchronized void save() throws IOException {
        Properties properties = new Properties();

        if (selectedPackName != null && !selectedPackName.isEmpty()) {
            properties.setProperty(KEY_SELECTED_PACK, selectedPackName);
        }

        properties.setProperty(KEY_SHADERS_ENABLED, Boolean.toString(shadersEnabled));
        properties.setProperty(KEY_DEBUG_ENABLED, Boolean.toString(debugEnabled));

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
        this.selectedPackName = trim(selectedPackName);
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

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void putOverride(String packKey, String optionId, String value) {
        if (optionId == null || optionId.isEmpty()) {
            return;
        }

        overridesByPack
            .computeIfAbsent(packKey == null || packKey.isEmpty() ? LEGACY_OVERRIDE_KEY : canonicalizePackName(packKey), key -> new HashMap<>())
            .put(optionId, value);
    }
}
