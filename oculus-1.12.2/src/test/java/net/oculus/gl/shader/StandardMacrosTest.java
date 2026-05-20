package net.oculus.gl.shader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import net.oculus.shaderpack.StringPair;
import org.junit.Test;

public class StandardMacrosTest {
    private static final String VERSION_OVERRIDE_PROPERTY = "oculus.mcVersionOverride";

    @Test
    public void environmentReportsForge1122TargetVersionByDefault() {
        String previous = System.getProperty(VERSION_OVERRIDE_PROPERTY);
        String previousGlProbeBypass = System.getProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY);
        System.clearProperty(VERSION_OVERRIDE_PROPERTY);
        System.setProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY, "true");
        try {
            assertTrue(contains(StandardMacros.createStandardEnvironmentDefines(), "MC_VERSION", "11202"));
        } finally {
            restoreVersionOverride(previous);
            restoreProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY, previousGlProbeBypass);
        }
    }

    @Test
    public void explicitMinecraftVersionOverrideIsPreservedForPackDebugging() {
        String previous = System.getProperty(VERSION_OVERRIDE_PROPERTY);
        String previousGlProbeBypass = System.getProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY);
        System.setProperty(VERSION_OVERRIDE_PROPERTY, "12104");
        System.setProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY, "true");
        try {
            assertTrue(contains(StandardMacros.createStandardEnvironmentDefines(), "MC_VERSION", "12104"));
        } finally {
            restoreVersionOverride(previous);
            restoreProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY, previousGlProbeBypass);
        }
    }

    @Test
    public void unknownGlVersionDoesNotFallBackToMinecraftVersion() {
        String previousGlProbeBypass = System.getProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY);
        System.setProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY, "true");
        try {
            assertEquals("000", StandardMacros.getGlVersion(-1));
        } finally {
            restoreProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY, previousGlProbeBypass);
        }
    }

    @Test
    public void environmentAdvertisesGlobalIsIrisMacroForPackCompatibility() {
        String previousGlProbeBypass = System.getProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY);
        System.setProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY, "true");
        try {
            assertTrue(containsName(StandardMacros.createStandardEnvironmentDefines(), "IS_IRIS"));
        } finally {
            restoreProperty(StandardMacros.DISABLE_GL_STRING_PROBES_PROPERTY, previousGlProbeBypass);
        }
    }

    @Test
    public void glVendorClassificationMatches1165PrefixTable() {
        assertEquals("MC_GL_VENDOR_ATI", StandardMacros.classifyVendorMacro("ATI Technologies Inc."));
        assertEquals("MC_GL_VENDOR_INTEL", StandardMacros.classifyVendorMacro("Intel Open Source Technology Center"));
        assertEquals("MC_GL_VENDOR_NVIDIA", StandardMacros.classifyVendorMacro("NVIDIA Corporation"));
        assertEquals("MC_GL_VENDOR_AMD", StandardMacros.classifyVendorMacro("AMD"));
        assertEquals("MC_GL_VENDOR_XORG", StandardMacros.classifyVendorMacro("X.Org"));
        assertEquals("MC_GL_VENDOR_OTHER", StandardMacros.classifyVendorMacro("llvmpipe"));
        assertEquals(null, StandardMacros.classifyVendorMacro(null));
    }

    @Test
    public void glRendererClassificationMatches1165PrefixTable() {
        assertEquals("MC_GL_RENDERER_RADEON", StandardMacros.classifyRendererMacro("AMD Radeon RX"));
        assertEquals("MC_GL_RENDERER_RADEON", StandardMacros.classifyRendererMacro("ATI Radeon HD"));
        assertEquals("MC_GL_RENDERER_RADEON", StandardMacros.classifyRendererMacro("RadeonSI"));
        assertEquals("MC_GL_RENDERER_GALLIUM", StandardMacros.classifyRendererMacro("Gallium 0.4"));
        assertEquals("MC_GL_RENDERER_INTEL", StandardMacros.classifyRendererMacro("Intel Iris"));
        assertEquals("MC_GL_RENDERER_GEFORCE", StandardMacros.classifyRendererMacro("GeForce RTX"));
        assertEquals("MC_GL_RENDERER_GEFORCE", StandardMacros.classifyRendererMacro("NVIDIA GeForce"));
        assertEquals("MC_GL_RENDERER_QUADRO", StandardMacros.classifyRendererMacro("Quadro P4000"));
        assertEquals("MC_GL_RENDERER_QUADRO", StandardMacros.classifyRendererMacro("NVS 310"));
        assertEquals("MC_GL_RENDERER_MESA", StandardMacros.classifyRendererMacro("Mesa Intel"));
        assertEquals("MC_GL_RENDERER_OTHER", StandardMacros.classifyRendererMacro("Software Rasterizer"));
        assertEquals(null, StandardMacros.classifyRendererMacro(null));
    }

    private static void restoreVersionOverride(String previous) {
        restoreProperty(VERSION_OVERRIDE_PROPERTY, previous);
    }

    private static void restoreProperty(String property, String previous) {
        if (previous == null) {
            System.clearProperty(property);
        } else {
            System.setProperty(property, previous);
        }
    }

    private static boolean contains(Iterable<StringPair> defines, String name, String value) {
        for (StringPair pair : defines) {
            if (name.equals(pair.getKey()) && value.equals(pair.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsName(Iterable<StringPair> defines, String name) {
        for (StringPair pair : defines) {
            if (name.equals(pair.getKey())) {
                return true;
            }
        }
        return false;
    }

}
