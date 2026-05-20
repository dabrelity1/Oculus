package net.oculus.shaderpack;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import net.oculus.Oculus;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.blending.AlphaTest;
import net.oculus.gl.blending.AlphaTestFunction;
import net.oculus.gl.blending.AlphaTestOverride;
import net.oculus.gl.blending.BlendMode;
import net.oculus.gl.blending.BlendModeFunction;
import net.oculus.gl.blending.BlendModeOverride;
import net.oculus.gl.blending.BufferBlendInformation;
import net.oculus.gl.texture.TextureScaleOverride;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.gl.texture.PixelFormat;
import net.oculus.gl.texture.PixelType;
import net.oculus.shaderpack.texture.TextureStage;
import net.oculus.shaderpack.texture.CustomImageData;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.option.ShaderPackOptions;

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
    private OptionalBoolean backFaceSolid = OptionalBoolean.DEFAULT;
    private OptionalBoolean backFaceCutout = OptionalBoolean.DEFAULT;
    private OptionalBoolean backFaceCutoutMipped = OptionalBoolean.DEFAULT;
    private OptionalBoolean backFaceTranslucent = OptionalBoolean.DEFAULT;
    private OptionalBoolean rainDepth = OptionalBoolean.DEFAULT;
    private OptionalBoolean beaconBeamDepth = OptionalBoolean.DEFAULT;
    private OptionalBoolean concurrentCompute = OptionalBoolean.DEFAULT;
    private OptionalBoolean separateAo = OptionalBoolean.DEFAULT;
    private OptionalBoolean frustumCulling = OptionalBoolean.DEFAULT;
    private OptionalBoolean occlusionCulling = OptionalBoolean.DEFAULT;
    private OptionalBoolean shadowCulling = OptionalBoolean.DEFAULT;
    private ShadowCullingMode shadowCullingMode = ShadowCullingMode.DEFAULT;
    private OptionalBoolean shadowEnabled = OptionalBoolean.DEFAULT;
    private OptionalBoolean particlesBeforeDeferred = OptionalBoolean.DEFAULT;
    private Optional<ParticleRenderingOrder> particleRenderingOrder = Optional.empty();
    private OptionalBoolean prepareBeforeShadow = OptionalBoolean.DEFAULT;

    private final List<String> sliderOptions = new ArrayList<>();
    private final Map<String, List<String>> profiles = new LinkedHashMap<>();
    private List<String> mainScreenOptions = null;
    private Integer mainScreenColumns = null;
    private final Map<String, List<String>> subScreenOptions = new HashMap<>();
    private final Map<String, Integer> subScreenColumns = new HashMap<>();

    private final EnumMap<TextureStage, Map<String, String>> customTextures = new EnumMap<>(TextureStage.class);
    private final Map<String, CustomImageData> customImages = new LinkedHashMap<>();
    private final Map<String, TextureScaleOverride> textureScaleOverrides = new HashMap<>();
    private final Map<String, Float> viewportScaleOverrides = new HashMap<>();
    private final Map<String, AlphaTestOverride> alphaTestOverrides = new HashMap<>();
    private final Map<String, BlendModeOverride> blendModeOverrides = new HashMap<>();
    private final Map<String, List<BufferBlendInformation>> bufferBlendOverrides = new HashMap<>();
    private final Map<String, Map<String, Boolean>> explicitFlips = new HashMap<>();
    private final Map<String, String> conditionallyEnabledPrograms = new HashMap<>();
    private final Map<String, CustomUniformDirective> customUniforms = new LinkedHashMap<>();
    private final Map<String, CustomUniformDirective> customVariables = new LinkedHashMap<>();
    private final Map<Integer, Long> shaderStorageBufferSizes = new LinkedHashMap<>();
    private final List<String> requiredIrisFeatures = new ArrayList<>();
    private final List<String> optionalIrisFeatures = new ArrayList<>();
    private final Map<String, String> propertyValueDefines = new HashMap<>();

    private String noiseTexturePath = null;

    private ShaderProperties() {
    }

    public ShaderProperties(String contents) {
        this(contents, (ShaderPackOptions) null);
    }

    public ShaderProperties(String contents, ShaderPackOptions shaderPackOptions) {
        this(contents, contents, shaderPackOptions);
    }

    public ShaderProperties(String processedContents, String layoutContents, ShaderPackOptions shaderPackOptions) {
        this.propertyValueDefines.putAll(collectPropertyValueDefines(shaderPackOptions));

        Properties properties = new OrderBackedProperties();
        try {
            properties.load(new StringReader(processedContents));
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

            if ("iris.features.required".equals(key)) {
                requiredIrisFeatures.clear();
                requiredIrisFeatures.addAll(parseList(value));
                return;
            }

            if ("iris.features.optional".equals(key)) {
                optionalIrisFeatures.clear();
                optionalIrisFeatures.addAll(parseList(value));
                return;
            }

            if (key.startsWith("program.")) {
                String programName = key.substring("program.".length(), key.indexOf(".", "program.".length()));
                conditionallyEnabledPrograms.put(programName, value);
                return;
            }

            if (parseCustomUniformDirective(key, value)) {
                return;
            }

            if (key.startsWith("bufferObject.")) {
                parseShaderStorageBufferDirective(key, value);
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

            if ("particles.ordering".equals(key)) {
                switch (value.trim().toLowerCase(Locale.ROOT)) {
                    case "mixed":
                        particleRenderingOrder = Optional.of(ParticleRenderingOrder.MIXED);
                        break;
                    case "after":
                        particleRenderingOrder = Optional.of(ParticleRenderingOrder.AFTER);
                        break;
                    case "before":
                        particleRenderingOrder = Optional.of(ParticleRenderingOrder.BEFORE);
                        break;
                    default:
                        Oculus.LOGGER.warn("Unrecognized particles.ordering value in shaders.properties: {}", value);
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
            handleBooleanDirective(key, value, "backFace.solid", bool -> backFaceSolid = bool);
            handleBooleanDirective(key, value, "backFace.cutout", bool -> backFaceCutout = bool);
            handleBooleanDirective(key, value, "backFace.cutoutMipped", bool -> backFaceCutoutMipped = bool);
            handleBooleanDirective(key, value, "backFace.translucent", bool -> backFaceTranslucent = bool);
            handleBooleanDirective(key, value, "rain.depth", bool -> rainDepth = bool);
            handleBooleanDirective(key, value, "beacon.beam.depth", bool -> beaconBeamDepth = bool);
            handleBooleanDirective(key, value, "allowConcurrentCompute", bool -> concurrentCompute = bool);
            handleBooleanDirective(key, value, "separateAo", bool -> separateAo = bool);
            handleBooleanDirective(key, value, "frustum.culling", bool -> frustumCulling = bool);
            handleBooleanDirective(key, value, "occlusion.culling", bool -> occlusionCulling = bool);
            handleShadowCullingDirective(key, value);
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
                    textureScaleOverrides.put(pass,
                        new TextureScaleOverride(resolvePropertyValue(parts[0]), resolvePropertyValue(parts[1])));
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
                if (parts.length > 2) {
                    Oculus.LOGGER.warn("Alpha test directive for {} contains more parts than expected: {}", pass, value);
                } else if (parts.length < 2) {
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
                    if (!OculusRenderSystem.areCapabilityProbesDisabled()
                        && !OculusRenderSystem.supportsBufferBlending()) {
                        throw new RuntimeException(
                            "Buffer blending is not supported on this platform, however it was attempted to be used!");
                    }

                    String[] parts = pass.split("\\.");
                    int index = resolveBufferIndex(parts[1]);

                    if ("off".equals(value)) {
                        bufferBlendOverrides
                            .computeIfAbsent(parts[0], ignored -> new ArrayList<>())
                            .add(new BufferBlendInformation(index, null));
                        return;
                    }

                    BlendMode mode = parseBlendMode(value);
                    bufferBlendOverrides
                        .computeIfAbsent(parts[0], ignored -> new ArrayList<>())
                        .add(new BufferBlendInformation(index, mode));
                    return;
                }

                if ("off".equals(value)) {
                    blendModeOverrides.put(pass, BlendModeOverride.OFF);
                    return;
                }

                BlendMode mode = parseBlendMode(value);
                blendModeOverrides.put(pass, BlendModeOverride.of(mode));
            });

            handleTwoArgDirective("texture.", key, value, (stageName, samplerName) -> {
                String[] parts = value.split(" ");
                if (parts.length > 1) {
                    Oculus.LOGGER.warn("Custom texture directive for stage {}, sampler {} contains more parts than expected: {}",
                        stageName, samplerName, value);
                    return;
                }

                Optional<TextureStage> optionalTextureStage = TextureStage.parse(stageName);
                if (!optionalTextureStage.isPresent()) {
                    Oculus.LOGGER.warn("Unknown texture stage '{}' for directive {}", stageName, key);
                    return;
                }
                customTextures
                    .computeIfAbsent(optionalTextureStage.get(), ignored -> new HashMap<>())
                    .put(samplerName, value);
            });

            handlePassDirective("customTexture.", key, value, samplerName -> {
                for (TextureStage stage : TextureStage.values()) {
                    customTextures
                        .computeIfAbsent(stage, ignored -> new HashMap<>())
                        .put(samplerName, value);
                }
            });

            handlePassDirective("image.", key, value, imageName -> {
                CustomImageData imageData = parseCustomImage(imageName, value);
                if (imageData != null) {
                    customImages.put(imageName, imageData);
                }
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

        Properties layoutProperties = new OrderBackedProperties();
        try {
            layoutProperties.load(new StringReader(layoutContents));
        } catch (IOException exception) {
            Oculus.LOGGER.warn("Failed to read unprocessed shaders.properties layout", exception);
            return;
        }

        parseLayoutProperties(layoutProperties);
    }

    private static Map<String, String> collectPropertyValueDefines(ShaderPackOptions shaderPackOptions) {
        if (shaderPackOptions == null) {
            return Collections.emptyMap();
        }

        Map<String, String> values = new HashMap<>();
        shaderPackOptions.getOptionSet().getStringOptions().keySet().forEach(optionName -> {
            String optionValue = shaderPackOptions.getOptionValues().getStringValueOrDefault(optionName);
            if (optionValue != null && !optionValue.isEmpty()) {
                values.put(optionName, optionValue);
            }
        });
        return values;
    }

    private String resolvePropertyValue(String token) {
        String trimmed = token == null ? "" : token.trim();
        return propertyValueDefines.getOrDefault(trimmed, trimmed);
    }

    private void parseShaderStorageBufferDirective(String key, String value) {
        String indexString = key.substring("bufferObject.".length()).trim();
        if (indexString.isEmpty()) {
            Oculus.LOGGER.warn("Missing shader storage buffer index in directive {}", key);
            return;
        }

        try {
            int index = Integer.parseInt(indexString);
            long size = Long.parseLong(value.trim());
            if (index < 0 || size <= 0L) {
                Oculus.LOGGER.warn("Invalid shader storage buffer directive {}={}", key, value);
                return;
            }

            shaderStorageBufferSizes.put(index, size);
        } catch (NumberFormatException ex) {
            Oculus.LOGGER.warn("Invalid shader storage buffer directive {}={}", key, value, ex);
        }
    }

    private boolean parseCustomUniformDirective(String key, String value) {
        boolean isUniform = key.startsWith("uniform.");
        boolean isVariable = key.startsWith("variable.");
        if (!isUniform && !isVariable) {
            return false;
        }

        String prefix = isUniform ? "uniform." : "variable.";
        String remaining = key.substring(prefix.length());
        int separator = remaining.indexOf('.');
        if (separator <= 0 || separator >= remaining.length() - 1) {
            Oculus.LOGGER.warn("Invalid custom {} directive key {}", isUniform ? "uniform" : "variable", key);
            return true;
        }

        String typeToken = remaining.substring(0, separator);
        String name = remaining.substring(separator + 1).trim();
        Optional<CustomUniformDirective.ValueType> type = CustomUniformDirective.ValueType.fromPropertyToken(typeToken);
        if (!type.isPresent()) {
            Oculus.LOGGER.warn("Unsupported custom {} type '{}' in {}", isUniform ? "uniform" : "variable", typeToken, key);
            return true;
        }

        try {
            CustomUniformDirective directive = new CustomUniformDirective(type.get(), name, value);
            if (isUniform) {
                customUniforms.put(name, directive);
            } else {
                customVariables.put(name, directive);
            }
        } catch (IllegalArgumentException ex) {
            Oculus.LOGGER.warn("Invalid custom {} directive {}={}", isUniform ? "uniform" : "variable", key, value, ex);
        }
        return true;
    }

    private static CustomImageData parseCustomImage(String imageName, String value) {
        String[] parts = value.trim().split("\\s+");
        if (parts.length != 8 && parts.length != 9) {
            Oculus.LOGGER.warn("Invalid image directive for {}: {}", imageName, value);
            return null;
        }

        Optional<PixelFormat> pixelFormat = parseEnum(PixelFormat::fromString, parts[1]);
        Optional<InternalTextureFormat> internalFormat = parseEnum(InternalTextureFormat::fromString, parts[2]);
        Optional<PixelType> pixelType = parseEnum(PixelType::fromString, parts[3]);
        if (!pixelFormat.isPresent() || !internalFormat.isPresent() || !pixelType.isPresent()) {
            Oculus.LOGGER.warn("Invalid image format in directive for {}: {}", imageName, value);
            return null;
        }

        Boolean clearOnNewFrame = parseBoolean(parts[4]);
        Boolean relative = parseBoolean(parts[5]);
        if (clearOnNewFrame == null || relative == null) {
            Oculus.LOGGER.warn("Invalid image boolean flags in directive for {}: {}", imageName, value);
            return null;
        }

        return new CustomImageData(
            imageName,
            parts[0],
            pixelFormat.get(),
            internalFormat.get(),
            pixelType.get(),
            clearOnNewFrame,
            relative,
            parts[6],
            parts[7],
            parts.length == 9 ? parts[8] : null
        );
    }

    private interface EnumParser<T> {
        Optional<T> parse(String name);
    }

    private static <T> Optional<T> parseEnum(EnumParser<T> parser, String name) {
        if (name == null) {
            return Optional.empty();
        }
        return parser.parse(name.trim().toUpperCase(java.util.Locale.ROOT));
    }

    private static Boolean parseBoolean(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static BlendMode parseBlendMode(String value) {
        String[] modeArray = value.split(" ");
        int[] modes = new int[modeArray.length];

        int i = 0;
        for (String modeName : modeArray) {
            modes[i] = BlendModeFunction.fromString(modeName).get().getGlId();
            i++;
        }

        return new BlendMode(modes[0], modes[1], modes[2], modes[3]);
    }

    private static int resolveBufferIndex(String buffer) {
        int index = PackRenderTargetDirectives.LEGACY_RENDER_TARGETS.indexOf(buffer);
        if (index == -1 && buffer.startsWith("colortex")) {
            try {
                index = Integer.parseInt(buffer.substring("colortex".length()));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Failed to parse buffer blend!", e);
            }
        }
        if (index == -1) {
            throw new RuntimeException("Failed to parse buffer blend! index = " + index);
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
            handlePrefixedWhitespacedListDirective("screen.", key, value, subScreenOptions::put);
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

    private void handleShadowCullingDirective(String key, String value) {
        if (!"shadow.culling".equals(key)) {
            return;
        }

        if ("true".equals(value)) {
            shadowCulling = OptionalBoolean.TRUE;
            shadowCullingMode = ShadowCullingMode.ENABLED;
        } else if ("false".equals(value)) {
            shadowCulling = OptionalBoolean.FALSE;
            shadowCullingMode = ShadowCullingMode.DISABLED;
        } else if ("reversed".equals(value)) {
            shadowCulling = OptionalBoolean.TRUE;
            shadowCullingMode = ShadowCullingMode.REVERSED;
        } else {
            Oculus.LOGGER.warn("Unexpected value for shadow.culling in shaders.properties: {}", value);
        }
    }

    private static void handlePassDirective(String prefix, String key, String value, Consumer<String> handler) {
        if (!key.startsWith(prefix)) {
            return;
        }
        String pass = key.substring(prefix.length());
        handler.accept(pass);
    }

    private static void handleTwoArgDirective(String prefix, String key, String value, BiConsumer<String, String> handler) {
        if (key.startsWith(prefix)) {
            int separator = key.indexOf(".", prefix.length());
            String first = key.substring(prefix.length(), separator);
            String second = key.substring(separator + 1);
            handler.accept(first, second);
        }
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
            if (name.isEmpty()) {
                return false;
            }
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
        return Arrays.asList(value.split(" +"));
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

    public OptionalBoolean getBackFaceSolid() {
        return backFaceSolid;
    }

    public OptionalBoolean getBackFaceCutout() {
        return backFaceCutout;
    }

    public OptionalBoolean getBackFaceCutoutMipped() {
        return backFaceCutoutMipped;
    }

    public OptionalBoolean getBackFaceTranslucent() {
        return backFaceTranslucent;
    }

    public OptionalBoolean getRainDepth() {
        return rainDepth;
    }

    public OptionalBoolean getBeaconBeamDepth() {
        return beaconBeamDepth;
    }

    public OptionalBoolean getConcurrentCompute() {
        return concurrentCompute;
    }

    public OptionalBoolean getSeparateAo() {
        return separateAo;
    }

    public OptionalBoolean getFrustumCulling() {
        return frustumCulling;
    }

    public OptionalBoolean getOcclusionCulling() {
        return occlusionCulling;
    }

    public OptionalBoolean getShadowCulling() {
        return shadowCulling;
    }

    public ShadowCullingMode getShadowCullingMode() {
        return shadowCullingMode;
    }

    public OptionalBoolean getShadowEnabled() {
        return shadowEnabled;
    }

    public OptionalBoolean getParticlesBeforeDeferred() {
        return particlesBeforeDeferred;
    }

    public Optional<ParticleRenderingOrder> getParticleRenderingOrder() {
        return particleRenderingOrder;
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

    public Map<String, String> getConditionallyEnabledPrograms() {
        return Collections.unmodifiableMap(conditionallyEnabledPrograms);
    }

    public Map<String, CustomUniformDirective> getCustomUniforms() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(customUniforms));
    }

    public Map<String, CustomUniformDirective> getCustomVariables() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(customVariables));
    }

    public Map<Integer, Long> getShaderStorageBufferSizes() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(shaderStorageBufferSizes));
    }

    public List<String> getRequiredIrisFeatures() {
        return Collections.unmodifiableList(requiredIrisFeatures);
    }

    public List<String> getOptionalIrisFeatures() {
        return Collections.unmodifiableList(optionalIrisFeatures);
    }

    public Optional<String> getNoiseTexturePath() {
        return Optional.ofNullable(noiseTexturePath);
    }

    public Map<TextureStage, Map<String, String>> getCustomTextures() {
        EnumMap<TextureStage, Map<String, String>> copy = new EnumMap<>(TextureStage.class);
        customTextures.forEach((stage, map) -> copy.put(stage, Collections.unmodifiableMap(new HashMap<>(map))));
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, CustomImageData> getCustomImages() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(customImages));
    }
}
