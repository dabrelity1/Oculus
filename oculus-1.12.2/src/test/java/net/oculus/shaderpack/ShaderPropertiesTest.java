package net.oculus.shaderpack;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;

import org.junit.Test;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.blending.AlphaTestFunction;
import net.oculus.gl.blending.AlphaTestOverride;
import net.oculus.gl.blending.BlendMode;
import net.oculus.gl.blending.BlendModeFunction;
import net.oculus.gl.blending.BlendModeOverride;
import net.oculus.shaderpack.texture.TextureStage;

public class ShaderPropertiesTest {
    @Test
    public void layoutProfileAndSliderPropertiesUseOriginalUnpreprocessedSourceLikeReference() {
        String processedContents =
            "program.composite.enabled=false\n" +
            "screen=PROCESSED_ONLY\n";
        String originalContents =
            "#if 0\n" +
            "screen=ORIGINAL_SCREEN [quality]\n" +
            "screen.columns=3\n" +
            "screen.quality=QUALITY SHADOW_QUALITY\n" +
            "screen.quality.columns=2\n" +
            "sliders=QUALITY SHADOW_QUALITY\n" +
            "profile.HIGH=QUALITY=2 SHADOW_QUALITY=4\n" +
            "#endif\n";

        ShaderProperties properties = new ShaderProperties(processedContents, originalContents, null);

        assertEquals(Arrays.asList("ORIGINAL_SCREEN", "[quality]"), properties.getMainScreenOptions().get());
        assertEquals(Integer.valueOf(3), properties.getMainScreenColumnCount().get());
        assertEquals(Arrays.asList("QUALITY", "SHADOW_QUALITY"), properties.getSubScreenOptions().get("quality"));
        assertEquals(Integer.valueOf(2), properties.getSubScreenColumnCount().get("quality"));
        assertEquals(Arrays.asList("QUALITY", "SHADOW_QUALITY"), properties.getSliderOptions());
        assertEquals(Arrays.asList("QUALITY=2", "SHADOW_QUALITY=4"), properties.getProfiles().get("HIGH"));

        assertEquals("false", properties.getConditionallyEnabledPrograms().get("composite"));
        assertFalse(properties.getConditionallyEnabledPrograms().containsKey("ORIGINAL_SCREEN"));
        assertTrue(properties.getProfiles().containsKey("HIGH"));
    }

    @Test
    public void bufferBlendCapabilityBoundaryMatchesReferenceSource() throws Exception {
        String source = new String(
            Files.readAllBytes(Paths.get("src/main/java/net/oculus/shaderpack/ShaderProperties.java")),
            StandardCharsets.UTF_8);

        assertTrue(source.contains("OculusRenderSystem.supportsBufferBlending()"));
        assertTrue(source.contains(
            "Buffer blending is not supported on this platform, however it was attempted to be used!"));
    }

    @Test
    public void bufferBlendDirectiveParsesWhenCapabilityProbesAreExplicitlyDisabledForHeadlessLoading() {
        withDisabledGlCapabilityProbes(() -> {
            ShaderProperties properties = new ShaderProperties("blend.gbuffers_water.colortex4=off\n");

            assertEquals(1, properties.getBufferBlendOverrides().get("gbuffers_water").size());
        });
    }

    @Test
    public void bufferBlendUnknownBufferFailsFastLikeReference() {
        withDisabledGlCapabilityProbes(() -> {
            try {
                new ShaderProperties("blend.gbuffers_water.not_a_buffer=off\n");
                fail("Expected unknown buffer blend target to throw");
            } catch (RuntimeException expected) {
                assertEquals("Failed to parse buffer blend! index = -1", expected.getMessage());
            }
        });
    }

    @Test
    public void bufferBlendInvalidColortexIdWrapsNumberFormatLikeReference() {
        withDisabledGlCapabilityProbes(() -> {
            try {
                new ShaderProperties("blend.gbuffers_water.colortexabc=off\n");
                fail("Expected malformed colortex blend target to throw");
            } catch (RuntimeException expected) {
                assertEquals("Failed to parse buffer blend!", expected.getMessage());
                assertTrue(expected.getCause() instanceof NumberFormatException);
            }
        });
    }

    @Test
    public void blendModeInvalidTokenThrowsLikeReference() {
        try {
            new ShaderProperties("blend.gbuffers_water=NOPE ONE ZERO ONE\n");
            fail("Expected malformed blend mode to throw");
        } catch (NoSuchElementException expected) {
            assertEquals("No value present", expected.getMessage());
        }
    }

    @Test
    public void blendModeExtraTokensAreParsedBeforeFirstFourAreUsedLikeReference() {
        ShaderProperties properties = new ShaderProperties("blend.gbuffers_water=ONE ZERO ONE ZERO SRC_ALPHA\n");

        BlendModeOverride override = properties.getBlendModeOverrides().get("gbuffers_water");
        assertTrue(override.hasCustomBlendMode());

        BlendMode mode = override.getBlendMode();
        assertEquals(BlendModeFunction.ONE.getGlId(), mode.getSrcRgb());
        assertEquals(BlendModeFunction.ZERO.getGlId(), mode.getDstRgb());
        assertEquals(BlendModeFunction.ONE.getGlId(), mode.getSrcAlpha());
        assertEquals(BlendModeFunction.ZERO.getGlId(), mode.getDstAlpha());
    }

    @Test
    public void textureDirectiveRejectsRawMultiTokenValuesLikeReference() {
        ShaderProperties properties = new ShaderProperties(
            "texture.deferred.colortex3=lib/textures/cloud-water.png\n" +
            "texture.composite.rawSampler=RGBA8 raw.bin 16 16\n");

        assertEquals("lib/textures/cloud-water.png",
            properties.getCustomTextures().get(TextureStage.DEFERRED).get("colortex3"));
        assertFalse(properties.getCustomTextures().containsKey(TextureStage.COMPOSITE_AND_FINAL));
    }

    @Test
    public void textureDirectiveStageNamesAreCaseSensitiveLikeReference() {
        ShaderProperties properties = new ShaderProperties(
            "texture.DEFERRED.colortex3=lib/textures/ignored.png\n" +
            "texture.deferred.colortex4=lib/textures/used.png\n");

        assertEquals("lib/textures/used.png",
            properties.getCustomTextures().get(TextureStage.DEFERRED).get("colortex4"));
        assertFalse(properties.getCustomTextures().get(TextureStage.DEFERRED).containsKey("colortex3"));
    }

    @Test
    public void twoArgDirectiveWithoutSecondDotFailsFastLikeReference() {
        try {
            new ShaderProperties("texture.gbuffers=textures/noise.png\n");
            fail("Expected malformed texture directive to throw");
        } catch (StringIndexOutOfBoundsException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    @Test
    public void flipDirectivePreservesEmptyBufferLikeReference() {
        ShaderProperties properties = new ShaderProperties("flip.deferred.=true\n");

        assertTrue(properties.getExplicitFlips().containsKey("deferred"));
        assertTrue(properties.getExplicitFlips().get("deferred").containsKey(""));
        assertEquals(Boolean.TRUE, properties.getExplicitFlips().get("deferred").get(""));
    }

    @Test
    public void explicitFlipDuplicateAliasesFailLikeReferenceImmutableMapBuilder() {
        ShaderProperties properties = new ShaderProperties(
            "flip.composite.gcolor=false\n" +
            "flip.composite.colortex0=true\n");

        try {
            new PackDirectives(properties).getExplicitFlips("composite");
            fail("Expected duplicate flip aliases to fail");
        } catch (IllegalArgumentException expected) {
            assertEquals("Multiple entries with same key: 0", expected.getMessage());
        }
    }

    @Test
    public void explicitFlipResultIsImmutableLikeReference() {
        ShaderProperties properties = new ShaderProperties("flip.composite.colortex0=true\n");

        Map<Integer, Boolean> flips = new PackDirectives(properties).getExplicitFlips("composite");
        try {
            flips.put(1, Boolean.FALSE);
            fail("Expected explicit flip map to be immutable");
        } catch (UnsupportedOperationException expected) {
            assertTrue(flips.get(0));
        }
    }

    @Test
    public void passDirectivePreservesEmptyPassLikeReference() {
        ShaderProperties properties = new ShaderProperties(
            "scale.=0.5\n" +
            "blend.=off\n" +
            "alphaTest.=off\n");

        assertEquals(Float.valueOf(0.5F), properties.getViewportScaleOverrides().get(""));
        assertTrue(properties.getBlendModeOverrides().containsKey(""));
        assertTrue(properties.getAlphaTestOverrides().containsKey(""));
        assertTrue(properties.getAlphaTestOverrides().get("").isDisabled());
    }

    @Test
    public void programEnabledDirectiveUsesFirstDotBoundaryLikeReference() {
        ShaderProperties properties = new ShaderProperties(
            "program.composite.enabled=false\n" +
            "program.world0/shadow.enabled=false\n" +
            "program.foo.extra.enabled=BAR\n" +
            "program..enabled=true\n");

        assertEquals("false", properties.getConditionallyEnabledPrograms().get("composite"));
        assertEquals("false", properties.getConditionallyEnabledPrograms().get("world0/shadow"));
        assertEquals("BAR", properties.getConditionallyEnabledPrograms().get("foo"));
        assertFalse(properties.getConditionallyEnabledPrograms().containsKey("foo.extra"));
        assertEquals("true", properties.getConditionallyEnabledPrograms().get(""));
    }

    @Test
    public void programDirectiveWithoutSecondDotFailsFastLikeReference() {
        try {
            new ShaderProperties("program.shadow=true\n");
            fail("Expected malformed program directive to throw");
        } catch (StringIndexOutOfBoundsException expected) {
            assertTrue(expected.getMessage() != null);
        }
    }

    @Test
    public void sizeBufferDirectiveUsesLiteralSpaceSplitLikeReference() {
        ShaderProperties properties = new ShaderProperties(
            "size.buffer.colortex1=0.5 0.5\n" +
            "size.buffer.colortex2=0.5\t0.5\n" +
            "size.buffer.colortex3=0.5  0.5\n");

        assertTrue(properties.getTextureScaleOverrides().containsKey("colortex1"));
        assertFalse(properties.getTextureScaleOverrides().containsKey("colortex2"));
        assertFalse(properties.getTextureScaleOverrides().containsKey("colortex3"));
    }

    @Test
    public void alphaTestDirectiveUsesFirstTwoTokensWhenExtraTokensArePresentLikeReference() {
        ShaderProperties properties = new ShaderProperties("alphaTest.gbuffers_water=GREATER 0.25 ignored\n");

        AlphaTestOverride override = properties.getAlphaTestOverrides().get("gbuffers_water");
        assertFalse(override.isDisabled());
        assertEquals(AlphaTestFunction.GREATER, override.getAlphaTest().getFunction());
        assertEquals(0.25F, override.getAlphaTest().getReference(), 0.0F);
    }

    @Test
    public void emptySubScreenColumnAffixFallsThroughToScreenListLikeReference() {
        ShaderProperties properties = new ShaderProperties("", "screen..columns=2\n", null);

        assertFalse(properties.getSubScreenColumnCount().containsKey(""));
        assertEquals(Arrays.asList("2"), properties.getSubScreenOptions().get(".columns"));
    }

    @Test
    public void layoutListsSplitOnlyOnSpacesLikeReference() {
        ShaderProperties properties = new ShaderProperties("", ""
            + "screen=FIRST\tSECOND THIRD\n"
            + "screen.detail=DETAIL\tONE DETAIL_TWO\n"
            + "sliders=QUALITY\tSHADOW_QUALITY EXTRA\n"
            + "profile.TABBED=QUALITY=2\tSHADOW_QUALITY=4 EXTRA=1\n",
            null);

        assertEquals(Arrays.asList("FIRST\tSECOND", "THIRD"), properties.getMainScreenOptions().get());
        assertEquals(Arrays.asList("DETAIL\tONE", "DETAIL_TWO"), properties.getSubScreenOptions().get("detail"));
        assertEquals(Arrays.asList("QUALITY\tSHADOW_QUALITY", "EXTRA"), properties.getSliderOptions());
        assertEquals(Arrays.asList("QUALITY=2\tSHADOW_QUALITY=4", "EXTRA=1"),
            properties.getProfiles().get("TABBED"));
    }

    @Test
    public void irisFeatureListsUseSpaceSplitAndPreserveTokensLikeReference() {
        ShaderProperties properties = new ShaderProperties(
            "iris.features.required=SEPARATE_HARDWARE_SAMPLERS\tPER_BUFFER_BLENDING CUSTOM_IMAGES\n" +
            "iris.features.optional=SSBO SSBO block_emission_attribute\n");

        assertEquals(Arrays.asList("SEPARATE_HARDWARE_SAMPLERS\tPER_BUFFER_BLENDING", "CUSTOM_IMAGES"),
            properties.getRequiredIrisFeatures());
        assertEquals(Arrays.asList("SSBO", "SSBO", "block_emission_attribute"),
            properties.getOptionalIrisFeatures());
    }

    private static void withDisabledGlCapabilityProbes(Runnable runnable) {
        String previous = System.getProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
        System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, "true");

        try {
            runnable.run();
        } finally {
            if (previous == null) {
                System.clearProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY);
            } else {
                System.setProperty(OculusRenderSystem.DISABLE_GL_CAPABILITY_PROBES_PROPERTY, previous);
            }
        }
    }
}
