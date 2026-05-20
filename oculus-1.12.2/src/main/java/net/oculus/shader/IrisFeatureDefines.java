package net.oculus.shader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.image.ImageLimits;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.StringPair;

/**
 * Resolves Iris feature flags declared by shader packs into preprocessor defines.
 */
public final class IrisFeatureDefines {
    private IrisFeatureDefines() {
    }

    public static List<StringPair> extendEnvironmentDefines(Iterable<StringPair> baseDefines,
                                                            ShaderProperties properties) {
        List<StringPair> defines = new ArrayList<>();
        if (baseDefines != null) {
            for (StringPair pair : baseDefines) {
                defines.add(pair);
            }
        }
        defines.addAll(createFeatureDefines(properties));
        return Collections.unmodifiableList(defines);
    }

    public static List<StringPair> createFeatureDefines(ShaderProperties properties) {
        if (properties == null) {
            return Collections.emptyList();
        }

        List<StringPair> defines = new ArrayList<>();

        for (String feature : properties.getRequiredIrisFeatures()) {
            if (!isSupported(feature)) {
                throw new ProgramLoadException("Shader pack requires unsupported Iris feature: " + feature);
            }
        }

        for (String feature : properties.getOptionalIrisFeatures()) {
            if (isSupported(feature)) {
                addFeatureDefine(defines, feature);
            }
        }

        return Collections.unmodifiableList(defines);
    }

    public static boolean isSupported(String feature) {
        if (feature == null || feature.isEmpty()) {
            return false;
        }

        switch (feature) {
            case "SEPARATE_HARDWARE_SAMPLERS":
                return true;
            case "PER_BUFFER_BLENDING":
                return OculusRenderSystem.supportsBufferBlending();
            case "CUSTOM_IMAGES":
                return ImageLimits.get().getMaxImageUnits() > 0;
            case "COMPUTE_SHADERS":
                return OculusRenderSystem.supportsCompute();
            case "SSBO":
                return OculusRenderSystem.supportsShaderStorageBuffers();
            case "ENTITY_TRANSLUCENT":
                return true;
            case "BLOCK_EMISSION_ATTRIBUTE":
                return true;
            case "REVERSED_CULLING":
                return true;
            default:
                return false;
        }
    }

    private static void addFeatureDefine(List<StringPair> defines, String feature) {
        if (feature.isEmpty()) {
            return;
        }
        defines.add(new StringPair("IRIS_FEATURE_" + feature, ""));
    }
}
