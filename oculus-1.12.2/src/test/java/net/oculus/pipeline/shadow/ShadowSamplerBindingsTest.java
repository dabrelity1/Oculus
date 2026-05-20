package net.oculus.pipeline.shadow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public class ShadowSamplerBindingsTest {
    @Test
    public void defaultAndCompatibilityAliasesBindToShadowTargets() {
        CapturingBinder binder = new CapturingBinder(
            "shadow",
            "s_shadow",
            "shadowtex0",
            "shadowtex1",
            "shadowcolor",
            "s_shadowcolor",
            "shadowcolor0",
            "shadowcolor1",
            "oculus_shadow_depth",
            "oculus_shadow_depth_notrans",
            "oculus_shadow_color",
            "oculus_shadow_color1");

        boolean usesShadows = ShadowSamplerBindings.apply(binder, false, false);

        assertTrue(usesShadows);
        assertEquals(ShadowSamplerBindings.Target.DEPTH, binder.target("shadow"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH, binder.target("s_shadow"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH, binder.target("shadowtex0"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH_NO_TRANSLUCENTS, binder.target("shadowtex1"));
        assertEquals(ShadowSamplerBindings.Target.COLOR0, binder.target("shadowcolor"));
        assertEquals(ShadowSamplerBindings.Target.COLOR0, binder.target("s_shadowcolor"));
        assertEquals(ShadowSamplerBindings.Target.COLOR0, binder.target("shadowcolor0"));
        assertEquals(ShadowSamplerBindings.Target.COLOR1, binder.target("shadowcolor1"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH, binder.target("oculus_shadow_depth"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH_NO_TRANSLUCENTS,
            binder.target("oculus_shadow_depth_notrans"));
        assertEquals(ShadowSamplerBindings.Target.COLOR0, binder.target("oculus_shadow_color"));
        assertEquals(ShadowSamplerBindings.Target.COLOR1, binder.target("oculus_shadow_color1"));
    }

    @Test
    public void waterShadowMovesShadowAliasToNoTranslucentsDepth() {
        CapturingBinder binder = new CapturingBinder(
            "watershadow",
            "shadow",
            "s_shadow",
            "shadowtex0",
            "shadowtex1",
            "oculus_shadow_depth",
            "oculus_shadow_depth_notrans");

        boolean usesShadows = ShadowSamplerBindings.apply(binder, false, false);

        assertTrue(usesShadows);
        assertEquals(ShadowSamplerBindings.Target.DEPTH, binder.target("watershadow"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH, binder.target("shadowtex0"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH, binder.target("oculus_shadow_depth"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH_NO_TRANSLUCENTS, binder.target("shadow"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH_NO_TRANSLUCENTS, binder.target("s_shadow"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH_NO_TRANSLUCENTS, binder.target("shadowtex1"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH_NO_TRANSLUCENTS,
            binder.target("oculus_shadow_depth_notrans"));
    }

    @Test
    public void hardwareShadowSamplersOnlyBindWhenHardwareFilteringIsEnabled() {
        CapturingBinder disabled = new CapturingBinder(
            "shadowtex0HW",
            "shadowtex0hw",
            "shadowtex1HW",
            "shadowtex1hw");

        boolean disabledUsesShadows = ShadowSamplerBindings.apply(disabled, false, false);

        assertFalse(disabledUsesShadows);
        assertEquals(ShadowSamplerBindings.Target.UNBOUND, disabled.target("shadowtex0HW"));
        assertEquals(ShadowSamplerBindings.Target.UNBOUND, disabled.target("shadowtex0hw"));
        assertEquals(ShadowSamplerBindings.Target.UNBOUND, disabled.target("shadowtex1HW"));
        assertEquals(ShadowSamplerBindings.Target.UNBOUND, disabled.target("shadowtex1hw"));

        CapturingBinder enabled = new CapturingBinder(
            "shadowtex0HW",
            "shadowtex0hw",
            "shadowtex1HW",
            "shadowtex1hw");

        boolean enabledUsesShadows = ShadowSamplerBindings.apply(enabled, true, true);

        assertFalse(enabledUsesShadows);
        assertEquals(ShadowSamplerBindings.Target.DEPTH, enabled.target("shadowtex0HW"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH, enabled.target("shadowtex0hw"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH_NO_TRANSLUCENTS, enabled.target("shadowtex1HW"));
        assertEquals(ShadowSamplerBindings.Target.DEPTH_NO_TRANSLUCENTS, enabled.target("shadowtex1hw"));
    }

    @Test
    public void shadowResourceDetectionMatchesBoundSamplerAndImageSurface() {
        assertFalse(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery()));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowtex0")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowtex0HW")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowtex0hw")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowtex1HW")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowtex1hw")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadow")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("s_shadow")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("watershadow")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowcolor")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("s_shadowcolor")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowcolor1")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("oculus_shadow_depth")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("oculus_shadow_depth_notrans")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("oculus_shadow_color")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("oculus_shadow_color1")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowcolorimg0")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowcolorimg1")));
    }

    @Test
    public void shadowImageDetectionRequestsShadowTargetsWithoutShadowSamplerUniforms() {
        assertTrue(ShadowSamplerBindings.usesShadowTargets(
            new CapturingResourceQuery(new String[0], "shadowcolorimg0")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(
            new CapturingResourceQuery(new String[0], "shadowcolorimg1")));
    }

    @Test
    public void shadowColorImageNamesTriggerTargetsButAreNotSamplerBindings() {
        CapturingBinder binder = new CapturingBinder("shadowcolorimg0", "shadowcolorimg1");

        boolean usesShadows = ShadowSamplerBindings.apply(binder, false, false);

        assertFalse(usesShadows);
        assertFalse(binder.targets.containsKey("shadowcolorimg0"));
        assertFalse(binder.targets.containsKey("shadowcolorimg1"));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowcolorimg0")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowcolorimg1")));
    }

    @Test
    public void unsupportedShadowResourcesAreDetectedBeforeBinding() {
        assertEquals("shadowtex2", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex2")));
        assertEquals("shadowtex2HW", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex2HW")));
        assertEquals("shadowtex2hw", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex2hw")));
        assertEquals("shadowcolor2", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowcolor2")));
        assertEquals("oculus_shadow_color2", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("oculus_shadow_color2")));
        assertEquals("shadowcolorimg2", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowcolorimg2")));
        assertEquals("shadowcolorimg2", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery(new String[0], "shadowcolorimg2")));
        assertEquals("shadowtex3", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex3")));
        assertEquals("shadowtex4HW", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex4HW")));
        assertEquals("shadowtex5hw", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex5hw")));
        assertEquals("shadowcolor3", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowcolor3")));
        assertEquals("oculus_shadow_color3", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("oculus_shadow_color3")));
        assertEquals("shadowcolorimg3", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery(new String[0], "shadowcolorimg3")));
        assertEquals("shadowtex16", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex16")));
        assertEquals("shadowcolor99", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowcolor99")));
        assertEquals("shadowcolorimg99", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery(new String[0], "shadowcolorimg99")));
        assertEquals("shadowtex100", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex100")));
        assertEquals("shadowtex100HW", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowtex100HW")));
        assertEquals("shadowcolor100", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("shadowcolor100")));
        assertEquals("oculus_shadow_color100", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery("oculus_shadow_color100")));
        assertEquals("shadowcolorimg100", ShadowSamplerBindings.findUnsupportedShadowResource(
            new CapturingResourceQuery(new String[0], "shadowcolorimg100")));

        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("oculus_shadow_color2")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(
            new CapturingResourceQuery(new String[0], "shadowcolorimg2")));
        assertTrue(ShadowSamplerBindings.usesShadowTargets(new CapturingResourceQuery("shadowcolor3")));
    }

    private static final class CapturingBinder implements ShadowSamplerBindings.Binder {
        private final Set<String> samplers;
        private final Map<String, ShadowSamplerBindings.Target> targets = new LinkedHashMap<>();

        private CapturingBinder(String... samplers) {
            this.samplers = new HashSet<>(Arrays.asList(samplers));
        }

        @Override
        public boolean hasSampler(String name) {
            return samplers.contains(name);
        }

        @Override
        public void bind(String name, ShadowSamplerBindings.Target target) {
            targets.put(name, target);
        }

        private ShadowSamplerBindings.Target target(String name) {
            return targets.get(name);
        }
    }

    private static final class CapturingResourceQuery implements ShadowSamplerBindings.ResourceQuery {
        private final Set<String> samplers;
        private final Set<String> images;

        private CapturingResourceQuery(String... samplers) {
            this(samplers, new String[0]);
        }

        private CapturingResourceQuery(String[] samplers, String... images) {
            this.samplers = new HashSet<>(Arrays.asList(samplers));
            this.images = new HashSet<>(Arrays.asList(images));
        }

        @Override
        public boolean hasSampler(String name) {
            return samplers.contains(name);
        }

        @Override
        public boolean hasImage(String name) {
            return images.contains(name);
        }

        @Override
        public Set<String> getActiveSamplerNames() {
            return samplers;
        }

        @Override
        public Set<String> getActiveImageNames() {
            return images;
        }
    }
}
