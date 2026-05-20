package net.oculus.uniforms;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class CameraPositionTrackerSourceTest {
    @Test
    public void realRenderCameraUsesActiveRenderInfoAfterVanillaCameraSetup() throws Exception {
        String source = read("src/main/java/net/oculus/uniforms/CameraPositionTracker.java");

        assertTrue(source.contains("import net.minecraft.client.renderer.ActiveRenderInfo;"));
        assertTrue(source.contains("void updateFromActiveRenderInfo(float partialTicks)"));
        assertTrue(source.contains("ActiveRenderInfo.projectViewFromEntity(entity, partialTicks)"));
    }

    @Test
    public void fallbackCameraUsesInterpolatedEyePositionNotFeetPosition() throws Exception {
        String source = read("src/main/java/net/oculus/uniforms/CameraPositionTracker.java");

        assertTrue(source.contains("return entity.getPositionEyes(partialTicks);"));
        assertFalse(source.contains("lastTickPosY + (entity.posY - entity.lastTickPosY)"));
        assertFalse(source.contains("new Vec3d(x, y, z)"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
