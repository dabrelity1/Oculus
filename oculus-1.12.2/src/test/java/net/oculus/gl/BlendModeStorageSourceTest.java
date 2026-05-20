package net.oculus.gl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class BlendModeStorageSourceTest {
    @Test
    public void bufferBlendOverrideUsesRenderSystemFailuresInsteadOfPreSkippingUnsupportedState() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/blending/BlendModeStorage.java")), StandardCharsets.UTF_8);
        String overrideBufferBlend = source.substring(
            source.indexOf("public static void overrideBufferBlend"),
            source.indexOf("public static void deferBlendModeToggle"));

        int saveState = overrideBufferBlend.indexOf("saveBlendState();");
        int nullOverride = overrideBufferBlend.indexOf("if (override == null)", saveState);
        int disable = overrideBufferBlend.indexOf("OculusRenderSystem.disableBufferBlend(index);", nullOverride);
        int enable = overrideBufferBlend.indexOf("OculusRenderSystem.enableBufferBlend(index);", disable);
        int blendFunc = overrideBufferBlend.indexOf("OculusRenderSystem.blendFuncSeparatei(", enable);
        int locked = overrideBufferBlend.indexOf("blendLocked = true;", blendFunc);

        assertTrue("Buffer blend overrides must save global blend state before applying per-buffer state",
            saveState >= 0);
        assertTrue(disable > nullOverride);
        assertTrue(enable > disable);
        assertTrue(blendFunc > enable);
        assertTrue(locked > blendFunc);
        assertFalse("Unsupported per-buffer blending must fail through OculusRenderSystem, not a storage pre-check",
            overrideBufferBlend.contains("supportsBufferBlending()"));
        assertFalse("Unsupported per-buffer blending must not become a silent storage-level return",
            overrideBufferBlend.contains("return;"));
    }
}
