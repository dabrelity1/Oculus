package net.oculus.shader;

import net.oculus.gl.shader.ShaderType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShaderCompatibilityPatcherTest {
    @Test
    public void rewritesLegacyFogBlock() {
        String legacySource = "#version 120\n" +
            "#if defined SET_FOG_COLOR\n" +
            "        block_color = clamp(block_color, vec3(0.0), vec3(50.0));\n" +
            "        gl_FragData[0] = vec4(block_color, 1.0);\n" +
            "        gl_FragData[1] = vec4(block_color, 1.0);\n" +
            "#endif";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "prepare", ShaderType.FRAGMENT, legacySource);

        assertTrue("Expected fog_color helper to be injected", patched.contains("vec3 fog_color"));
        assertTrue(patched.contains("gl_FragData[0].rgb = fog_color;"));
        assertFalse("Legacy vec4 constructor should be removed", patched.contains("vec4(block_color, 1.0)"));
    }

    @Test
    public void leavesModernFogBlockUntouched() {
        String modernSource = "#if defined SET_FOG_COLOR\n" +
            "    vec3 fog_color = clamp(block_color.rgb, vec3(0.0), vec3(50.0));\n" +
            "    gl_FragData[0].rgb = fog_color;\n" +
            "    gl_FragData[0].a = 1.0;\n" +
            "    gl_FragData[1].rgb = fog_color;\n" +
            "    gl_FragData[1].a = 1.0;\n" +
            "#endif";

        String patched = ShaderCompatibilityPatcher.patch("test-pack", "prepare", ShaderType.FRAGMENT, modernSource);
        assertEquals(modernSource, patched);
    }
}
