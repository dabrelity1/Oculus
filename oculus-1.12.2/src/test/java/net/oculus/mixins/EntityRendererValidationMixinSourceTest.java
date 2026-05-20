package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class EntityRendererValidationMixinSourceTest {
    @Test
    public void inventoryScreenshotCapturesAfterFullFrameGuiRender() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/EntityRendererValidationMixin.java");
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(config.contains("\"pipeline.EntityRendererValidationMixin\""));
        assertTrue(source.contains("@Mixin(EntityRenderer.class)"));
        assertTrue(source.contains("@Inject(method = \"updateCameraAndRender(FJ)V\", at = @At(\"RETURN\"))"));
        assertTrue(source.contains("OculusRuntimeValidation.captureInventoryScreenshotAfterGuiRender(mc);"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
