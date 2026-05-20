package net.oculus.shader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.shaderpack.StringPair;

public class IrisFeatureDefinesTest {
    private static String previousGlCapabilityProbeSetting;

    @BeforeClass
    public static void disableGlCapabilityProbes() {
        previousGlCapabilityProbeSetting = System.getProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
        System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, "true");
    }

    @AfterClass
    public static void restoreGlCapabilityProbes() {
        if (previousGlCapabilityProbeSetting == null) {
            System.clearProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
        } else {
            System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, previousGlCapabilityProbeSetting);
        }
    }

    @Test
    public void separateHardwareSamplersFeatureIsAlwaysAdvertisedLikeReference() {
        List<StringPair> defines = IrisFeatureDefines.createFeatureDefines(
            new ShaderProperties("iris.features.optional = SEPARATE_HARDWARE_SAMPLERS\n"));

        assertTrue(containsName(defines, "IRIS_FEATURE_SEPARATE_HARDWARE_SAMPLERS"));
    }

    @Test
    public void requiredFeaturesAreValidatedButNotEmittedAsDefinesLikeReference() {
        List<StringPair> defines = IrisFeatureDefines.createFeatureDefines(
            new ShaderProperties("iris.features.required = SEPARATE_HARDWARE_SAMPLERS\n"));

        assertFalse(containsName(defines, "IRIS_FEATURE_SEPARATE_HARDWARE_SAMPLERS"));
    }

    @Test
    public void perBufferBlendingFeatureUsesRuntimeCapabilityGate() {
        List<StringPair> defines = IrisFeatureDefines.createFeatureDefines(
            new ShaderProperties("iris.features.optional = PER_BUFFER_BLENDING\n"));

        assertFalse(containsName(defines, "IRIS_FEATURE_PER_BUFFER_BLENDING"));
    }

    @Test(expected = ProgramLoadException.class)
    public void requiredPerBufferBlendingFailsWhenRuntimeCapabilityIsMissing() {
        IrisFeatureDefines.createFeatureDefines(
            new ShaderProperties("iris.features.required = PER_BUFFER_BLENDING\n"));
    }

    @Test
    public void optionalFeaturesAreCaseSensitiveAndDuplicatesArePreservedLikeReference() {
        List<StringPair> defines = IrisFeatureDefines.createFeatureDefines(
            new ShaderProperties("iris.features.optional = SEPARATE_HARDWARE_SAMPLERS separate_hardware_samplers SEPARATE_HARDWARE_SAMPLERS\n"));

        assertEquals(2, countName(defines, "IRIS_FEATURE_SEPARATE_HARDWARE_SAMPLERS"));
        assertFalse(containsName(defines, "IRIS_FEATURE_separate_hardware_samplers"));
    }

    @Test(expected = ProgramLoadException.class)
    public void requiredFeaturesAreCaseSensitiveLikeReference() {
        IrisFeatureDefines.createFeatureDefines(
            new ShaderProperties("iris.features.required = separate_hardware_samplers\n"));
    }

    private static boolean containsName(List<StringPair> defines, String name) {
        for (StringPair define : defines) {
            if (name.equals(define.getKey())) {
                return true;
            }
        }
        return false;
    }

    private static int countName(List<StringPair> defines, String name) {
        int count = 0;
        for (StringPair define : defines) {
            if (name.equals(define.getKey())) {
                count++;
            }
        }
        return count;
    }
}
