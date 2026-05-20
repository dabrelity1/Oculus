package net.oculus.gl.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;
import org.lwjgl.opengl.GL11;

public class GameDataSuppliersTest {
    @Test
    public void fogModeMatchesReferenceDisabledBehavior() {
        assertEquals(0, GameDataSuppliers.computeFogMode(false, GL11.GL_LINEAR));
        assertEquals(GL11.GL_LINEAR, GameDataSuppliers.computeFogMode(true, GL11.GL_LINEAR));
        assertEquals(GL11.GL_EXP2, GameDataSuppliers.computeFogMode(true, GL11.GL_EXP2));
    }

    @Test
    public void viewportDimensionsPreferMainFramebufferLikeReference() {
        assertEquals(1920, GameDataSuppliers.chooseViewportDimension(1920, 1280));
        assertEquals(1280, GameDataSuppliers.chooseViewportDimension(0, 1280));
        assertEquals(1280, GameDataSuppliers.chooseViewportDimension(-1, 1280));
    }

    @Test
    public void aspectRatioUsesResolvedFramebufferDimensions() {
        assertEquals(16.0F / 9.0F, GameDataSuppliers.computeAspectRatio(1920, 1080), 0.0001F);
        assertEquals(1.0F, GameDataSuppliers.computeAspectRatio(1920, 0), 0.0001F);
    }

    @Test
    public void viewportSuppliersReadMinecraftFramebufferDimensions() throws Exception {
        String suppliers = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/gl/state/GameDataSuppliers.java")), StandardCharsets.UTF_8);
        String gameplay = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/uniforms/GameplayUniforms.java")), StandardCharsets.UTF_8);

        assertTrue(suppliers.contains("Framebuffer framebuffer = mc.getFramebuffer();"));
        assertTrue(suppliers.contains("framebuffer.framebufferWidth"));
        assertTrue(suppliers.contains("framebuffer.framebufferHeight"));
        assertTrue(gameplay.contains("GameDataSuppliers.updateScreenSize(SCREEN_SIZE);"));
    }
}
