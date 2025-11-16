package net.oculus.shaderpack;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.oculus.Oculus;
import net.oculus.gl.blending.AlphaTest;
import net.oculus.gl.blending.AlphaTestFunction;
import net.oculus.gl.blending.AlphaTestOverride;
import net.oculus.gl.blending.BlendMode;
import net.oculus.gl.blending.BlendModeFunction;
import net.oculus.gl.blending.BlendModeOverride;
import net.oculus.gl.blending.BufferBlendInformation;
import net.oculus.gl.texture.TextureScaleOverride;
import net.oculus.shaderpack.texture.TextureStage;
import net.oculus.shaderpack.PackRenderTargetDirectives;

public final class ShaderProperties {
    private CloudSetting cloudSetting = CloudSetting.DEFAULT;
    private OptionalBoolean oldHandLight = OptionalBoolean.DEFAULT;
    private OptionalBoolean dynamicHandLight = OptionalBoolean.DEFAULT;
    private OptionalBoolean supportsColorCorrection = OptionalBoolean.DEFAULT;
    private OptionalBoolean oldLighting = OptionalBoolean.DEFAULT;
    private OptionalBoolean shadowTerrain = OptionalBoolean.DEFAULT;
    private OptionalBoolean shadowTranslucent = OptionalBoolean.DEFAULT;
    private OptionalBoolean shadowEntities = OptionalBoolean.DEFAULT;
    private OptionalBoolean shadowPlayer = OptionalBoolean.DEFAULT;
    private OptionalBoolean shadowBlockEntities = OptionalBoolean.DEFAULT;
    private OptionalBoolean underwaterOverlay = OptionalBoolean.DEFAULT;
    private OptionalBoolean sun = OptionalBoolean.DEFAULT;
    private OptionalBoolean moon = OptionalBoolean.DEFAULT;
    private OptionalBoolean vignette = OptionalBoolean.DEFAULT;
    private OptionalBoolean rainDepth = OptionalBoolean.DEFAULT;
    private OptionalBoolean concurrentCompute = OptionalBoolean.DEFAULT;
    private OptionalBoolean separateAo = OptionalBoolean.DEFAULT;
    private OptionalBoolean shadowCulling = OptionalBoolean.DEFAULT;
    private OptionalBoolean shadowEnabled = OptionalBoolean.DEFAULT;
    private OptionalBoolean particlesBeforeDeferred = OptionalBoolean.DEFAULT;
    private OptionalBoolean prepareBeforeShadow = OptionalBoolean.DEFAULT;

    private final List<String> sliderOptions = new ArrayList<>();
    private final Map<String, List<String>> profiles = new LinkedHashMap<>();
    private List<String> mainScreenOptions = null;
    private Integer mainScreenColumns = null;
    private final Map<String, List<String>> subScreenOptions = new HashMap<>();
    private final Map<String, Integer> subScreenColumns = new HashMap<>();

    private final EnumMap<TextureStage, Map<String, String>> customTextures = new EnumMap<>(TextureStage.class);
    private final Map<String, TextureScaleOverride> textureScaleOverrides = new HashMap<>();
    private final Map<String, Float> viewportScaleOverrides = new HashMap<>();
    private final Map<String, AlphaTestOverride> alphaTestOverrides = new HashMap<>();
    private final Map<String, BlendModeOverride> blendModeOverrides = new HashMap<>();
    private final Map<String, List<BufferBlendInformation>> bufferBlendOverrides = new HashMap<>();
    private final Map<String, Map<String, Boolean>> explicitFlips = new HashMap<>();

    private String noiseTexturePath = null;

    private ShaderProperties() {
    }

    public ShaderProperties(String contents) {
        Properties properties = new OrderBackedProperties();
        try {
            properties.load(new StringReader(contents));
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to read shaders.properties", exception);
            return;
        }

        properties.forEach((keyObject, valueObject) -> {
            String key = (String) keyObject;
            String value = (String) valueObject;

            if ("texture.noise".equals(key)) {
                noiseTexturePath = value;
                return;
            }

            if ("clouds".equals(key)) {
                switch (value) {
                    case "off":
                        cloudSetting = CloudSetting.OFF;
                        break;
                    case "fast":
                        cloudSetting = CloudSetting.FAST;
                        break;
                    case "fancy":
                        cloudSetting = CloudSetting.FANCY;
                        break;
                    default:
                        Oculus.LOGGER.error("Unrecognized clouds setting: {}", value);
                        break;
                }
                return;
            }

            handleBooleanDirective(key, value, "oldHandLight", bool -> oldHandLight = bool);
            handleBooleanDirective(key, value, "dynamicHandLight", bool -> dynamicHandLight = bool);
            handleBooleanDirective(key, value, "oldLighting", bool -> oldLighting = bool);
            handleBooleanDirective(key, value, "shadowTerrain", bool -> shadowTerrain = bool);
            handleBooleanDirective(key, value, "shadowTranslucent", bool -> shadowTranslucent = bool);
            handleBooleanDirective(key, value, "shadowEntities", bool -> shadowEntities = bool);
            handleBooleanDirective(key, value, "shadowPlayer", bool -> shadowPlayer = bool);
            handleBooleanDirective(key, value, "shadowBlockEntities", bool -> shadowBlockEntities = bool);
            handleBooleanDirective(key, value, "underwaterOverlay", bool -> underwaterOverlay = bool);
            handleBooleanDirective(key, value, "sun", bool -> sun = bool);
            handleBooleanDirective(key, value, "moon", bool -> moon = bool);
            handleBooleanDirective(key, value, "vignette", bool -> vignette = bool);
            handleBooleanDirective(key, value, "rain.depth", bool -> rainDepth = bool);
            handleBooleanDirective(key, value, "allowConcurrentCompute", bool -> concurrentCompute = bool);
            handleBooleanDirective(key, value, "separateAo", bool -> separateAo = bool);
            handleBooleanDirective(key, value, "shadow.culling", bool -> shadowCulling = bool);
            handleBooleanDirective(key, value, "shadow.enabled", bool -> shadowEnabled = bool);
            handleBooleanDirective(key, value, "particles.before.deferred", bool -> particlesBeforeDeferred = bool);
            handleBooleanDirective(key, value, "prepareBeforeShadow", bool -> prepareBeforeShadow = bool);
            handleBooleanDirective(key, value, "supportsColorCorrection", bool -> supportsColorCorrection = bool);

            handlePassDirective("scale.", key, value, pass -> {
                try {
                    viewportScaleOverrides.put(pass, Float.parseFloat(value));
                } catch (NumberFormatException ex) {
                    Oculus.LOGGER.error("Unable to parse scale directive for {}: {}", pass, value);
                }
            });

            handlePassDirective("size.buffer.", key, value, pass -> {
                String[] parts = value.split(" ");
                if (parts.length != 2) {
                    Oculus.LOGGER.error("Unable to parse size.buffer directive for {}: {}", pass, value);
                    return;
                }
                try {
                    textureScaleOverrides.put(pass, new TextureScaleOverride(parts[0], parts[1]));
                } catch (IllegalArgumentException ex) {
                    Oculus.LOGGER.warn("Ignoring size.buffer directive for {}: {}", pass, value, ex);
                }
            });

            handlePassDirective("alphaTest.", key, value, pass -> {
                if ("off".equals(value)) {
                    alphaTestOverrides.put(pass, AlphaTestOverride.OFF);
                    return;
                }

                String[] parts = value.split(" ");
                if (parts.length != 2) {
                    Oculus.LOGGER.error("Invalid alpha test directive for {}: {}", pass, value);
                    return;
                }

                Optional<AlphaTestFunction> function = AlphaTestFunction.fromString(parts[0]);
                if (!function.isPresent()) {
                    Oculus.LOGGER.error("Unknown alpha test function {} for {}", parts[0], pass);
                    return;
                }

                try {
                    float reference = Float.parseFloat(parts[1]);
                    alphaTestOverrides.put(pass, new AlphaTestOverride(new AlphaTest(function.get(), reference)));
                } catch (NumberFormatException ex) {
                    Oculus.LOGGER.error("Invalid alpha test value for {}: {}", pass, value, ex);
                }
            });

            handlePassDirective("blend.", key, value, pass -> {
                if (pass.contains(".")) {
                    int dot = pass.indexOf('.');
                    String program = pass.substring(0, dot);
                    String buffer = pass.substring(dot + 1);
                    int index = resolveBufferIndex(buffer);
                    if (index == -1) {
                        Oculus.LOGGER.error("Unknown buffer {} in blend directive {}", buffer, key);
                        return;
                    }

                    if ("off".equals(value)) {
                        bufferBlendOverrides
                            .computeIfAbsent(program, ignored -> new ArrayList<>())
                            .add(new BufferBlendInformation(index, null));
                        return;
                    }

                    BlendMode mode = parseBlendMode(value);
                    if (mode != null) {
                        bufferBlendOverrides
                            .computeIfAbsent(program, ignored -> new ArrayList<>())
                            .add(new BufferBlendInformation(index, mode));
                    }
                    return;
                }

                if ("off".equals(value)) {
                    blendModeOverrides.put(pass, BlendModeOverride.OFF);
                    return;
                }

                BlendMode mode = parseBlendMode(value);
                if (mode != null) {
                    blendModeOverrides.put(pass, BlendModeOverride.of(mode));
                }
            });

            handleTwoArgDirective("texture.", key, value, (stageName, samplerName) -> {
                Optional<TextureStage> optionalTextureStage = TextureStage.parse(stageName);
                if (!optionalTextureStage.isPresent()) {
                    Oculus.LOGGER.warn("Unknown texture stage '{}' for directive {}", stageName, key);
                    return;
                }
                customTextures
                    .computeIfAbsent(optionalTextureStage.get(), ignored -> new HashMap<>())
                    .put(samplerName, value);
            });

            handleTwoArgDirective("flip.", key, value, (pass, buffer) -> {
                boolean parsed = "true".equals(value) || "false".equals(value);
                if (!parsed) {
                    Oculus.LOGGER.warn("Unexpected flip value '{}' for {}", value, key);
                    return;
                }
                explicitFlips
                    .computeIfAbsent(pass, ignored -> new HashMap<>())
                    .put(buffer, Boolean.parseBoolean(value));
            });
        });

        parseLayoutProperties(properties);
    }

    private static BlendMode parseBlendMode(String value) {
        String[] modeArray = value.split(" ");
        if (modeArray.length != 4) {
            Oculus.LOGGER.error("Blend mode requires four tokens, got {}", value);
            return null;
        }

        int[] modes = new int[4];
        for (int i = 0; i < modeArray.length; i++) {
            Optional<BlendModeFunction> function = BlendModeFunction.fromString(modeArray[i]);
            if (!function.isPresent()) {
                return null;
            }
            modes[i] = function.get().getGlId();
        }

        return new BlendMode(modes[0], modes[1], modes[2], modes[3]);
    }

    private static int resolveBufferIndex(String buffer) {
        int index = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(buffer);
        if (index == -1 && buffer.startsWith("colortex")) {
            try {
                index = Integer.parseInt(buffer.substring("colortex".length()));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return index;
    }

    private void parseLayoutProperties(Properties properties) {
        properties.forEach((keyObject, valueObject) -> {
            String key = (String) keyObject;
            String value = (String) valueObject;

            handleWhitespacedListDirective(key, value, "sliders", list -> {
                sliderOptions.clear();
                sliderOptions.addAll(list);
            });
            handlePrefixedWhitespacedListDirective("profile.", key, value, profiles::put);

            if (handleIntDirective(key, value, "screen.columns", columns -> mainScreenColumns = columns)) {
                return;
            }

            if (handleAffixedIntDirective("screen.", ".columns", key, value, subScreenColumns::put)) {
                return;
            }

            handleWhitespacedListDirective(key, value, "screen", options -> mainScreenOptions = options);
            if (!key.endsWith(".columns")) {
                handlePrefixedWhitespacedListDirective("screen.", key, value, subScreenOptions::put);
            }
        });
    }

    private static void handleBooleanDirective(String key, String value, String expectedKey, Consumer<OptionalBoolean> handler) {
        if (!expectedKey.equals(key)) {
            return;
        }

        if ("true".equals(value)) {
            handler.accept(OptionalBoolean.TRUE);
        } else if ("false".equals(value)) {
            handler.accept(OptionalBoolean.FALSE);
        } else {
            Oculus.LOGGER.warn("Unexpected value for boolean key {} in shaders.properties: {}", key, value);
        }
    }

    private static void handlePassDirective(String prefix, String key, String value, Consumer<String> handler) {
        if (!key.startsWith(prefix)) {
            return;
        }
        String pass = key.substring(prefix.length());
        if (!pass.isEmpty()) {
            handler.accept(pass);
        }
    }

    private static void handleTwoArgDirective(String prefix, String key, String value, BiConsumer<String, String> handler) {
        if (!key.startsWith(prefix)) {
            return;
        }
        String directive = key.substring(prefix.length());
        int separator = directive.indexOf('.');
        if (separator <= 0 || separator >= directive.length() - 1) {
            return;
        }
        handler.accept(directive.substring(0, separator), directive.substring(separator + 1));
    }

    private static void handleWhitespacedListDirective(String key, String value, String expectedKey, Consumer<List<String>> handler) {
        if (!expectedKey.equals(key)) {
            return;
        }
        handler.accept(parseList(value));
    }

    private static void handlePrefixedWhitespacedListDirective(String prefix, String key, String value, BiConsumer<String, List<String>> handler) {
        if (!key.startsWith(prefix)) {
            return;
        }
        String stripped = key.substring(prefix.length());
        handler.accept(stripped, parseList(value));
    }

    private static boolean handleIntDirective(String key, String value, String expectedKey, Consumer<Integer> handler) {
        if (!expectedKey.equals(key)) {
            return false;
        }
        try {
            handler.accept(Integer.parseInt(value));
        } catch (NumberFormatException ex) {
            Oculus.LOGGER.warn("Unexpected value for integer key {}: {}", key, value);
        }
        return true;
    }

    private static boolean handleAffixedIntDirective(String prefix, String suffix, String key, String value, BiConsumer<String, Integer> handler) {
        if (key.startsWith(prefix) && key.endsWith(suffix)) {
            String name = key.substring(prefix.length(), key.length() - suffix.length());
            try {
                handler.accept(name, Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                Oculus.LOGGER.warn("Unexpected value for integer key {}: {}", key, value);
            }
            return true;
        }
        return false;
    }

    private static List<String> parseList(String value) {
        List<String> result = new ArrayList<>();
        for (String component : value.split("\\s+")) {
            if (!component.isEmpty()) {
                result.add(component);
            }
        }
        return result;
    }

    public static ShaderProperties empty() {
        return new ShaderProperties();
    }

    public CloudSetting getCloudSetting() {
        return cloudSetting;
    }

    public OptionalBoolean getOldHandLight() {
        return oldHandLight;
    }

    public OptionalBoolean getDynamicHandLight() {
        return dynamicHandLight;
    }

    public OptionalBoolean supportsColorCorrection() {
        return supportsColorCorrection;
    }

    public OptionalBoolean getOldLighting() {
        return oldLighting;
    }

    public OptionalBoolean getShadowTerrain() {
        return shadowTerrain;
    }

    public OptionalBoolean getShadowTranslucent() {
        return shadowTranslucent;
    }

    public OptionalBoolean getShadowEntities() {
        return shadowEntities;
    }

    public OptionalBoolean getShadowPlayer() {
        return shadowPlayer;
    }

    public OptionalBoolean getShadowBlockEntities() {
        return shadowBlockEntities;
    }

    public OptionalBoolean getUnderwaterOverlay() {
        return underwaterOverlay;
    }

    public OptionalBoolean getSun() {
        return sun;
    }

    public OptionalBoolean getMoon() {
        return moon;
    }

    public OptionalBoolean getVignette() {
        return vignette;
    }

    public OptionalBoolean getRainDepth() {
        return rainDepth;
    }

    public OptionalBoolean getConcurrentCompute() {
        return concurrentCompute;
    }

    public OptionalBoolean getSeparateAo() {
        return separateAo;
    }

    public OptionalBoolean getShadowCulling() {
        return shadowCulling;
    }

    public OptionalBoolean getShadowEnabled() {
        return shadowEnabled;
    }

    public OptionalBoolean getParticlesBeforeDeferred() {
        return particlesBeforeDeferred;
    }

    public OptionalBoolean getPrepareBeforeShadow() {
        return prepareBeforeShadow;
    }

    public List<String> getSliderOptions() {
        return Collections.unmodifiableList(sliderOptions);
    }

    public Map<String, List<String>> getProfiles() {
        return Collections.unmodifiableMap(profiles);
    }

    public Optional<List<String>> getMainScreenOptions() {
        return Optional.ofNullable(mainScreenOptions);
    }

    public Optional<Integer> getMainScreenColumnCount() {
        return Optional.ofNullable(mainScreenColumns);
    }

    public Map<String, List<String>> getSubScreenOptions() {
        return Collections.unmodifiableMap(subScreenOptions);
    }

    public Map<String, Integer> getSubScreenColumnCount() {
        return Collections.unmodifiableMap(subScreenColumns);
    }

    public Map<String, TextureScaleOverride> getTextureScaleOverrides() {
        return Collections.unmodifiableMap(textureScaleOverrides);
    }

    public Map<String, Float> getViewportScaleOverrides() {
        return Collections.unmodifiableMap(viewportScaleOverrides);
    }

    public Map<String, AlphaTestOverride> getAlphaTestOverrides() {
        return Collections.unmodifiableMap(alphaTestOverrides);
    }

    public Map<String, BlendModeOverride> getBlendModeOverrides() {
        return Collections.unmodifiableMap(blendModeOverrides);
    }

    public Map<String, List<BufferBlendInformation>> getBufferBlendOverrides() {
        return Collections.unmodifiableMap(bufferBlendOverrides);
    }

    public Map<String, Map<String, Boolean>> getExplicitFlips() {
        Map<String, Map<String, Boolean>> copy = new HashMap<>();
        explicitFlips.forEach((pass, map) -> copy.put(pass, Collections.unmodifiableMap(new HashMap<>(map))));
        return Collections.unmodifiableMap(copy);
    }

    public Optional<String> getNoiseTexturePath() {
        return Optional.ofNullable(noiseTexturePath);
    }

    public Map<TextureStage, Map<String, String>> getCustomTextures() {
        EnumMap<TextureStage, Map<String, String>> copy = new EnumMap<>(TextureStage.class);
        customTextures.forEach((stage, map) -> copy.put(stage, Collections.unmodifiableMap(new HashMap<>(map))));
        return Collections.unmodifiableMap(copy);
    }
}
