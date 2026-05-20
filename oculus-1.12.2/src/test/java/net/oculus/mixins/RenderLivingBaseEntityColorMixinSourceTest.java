package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RenderLivingBaseEntityColorMixinSourceTest {
    @Test
    public void capturesEntityColorFromVanillaBrightnessBufferInsteadOfRecomputingIt() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/RenderLivingBaseEntityColorMixin.java")),
            StandardCharsets.UTF_8);

        assertTrue(source.contains("@Mixin(RenderLivingBase.class)"));
        assertTrue(source.contains("protected FloatBuffer brightnessBuffer;"));
        assertTrue(source.contains("setBrightness(Lnet/minecraft/entity/EntityLivingBase;FZ)Z"));
        assertTrue(source.contains("brightnessBuffer.get(0)"));
        assertTrue(source.contains("brightnessBuffer.get(1)"));
        assertTrue(source.contains("brightnessBuffer.get(2)"));
        assertTrue(source.contains("brightnessBuffer.get(3)"));
        assertTrue(source.contains("GameplayUniforms.setEntityColor("));
        assertTrue(source.contains("GameplayUniforms.clearEntityColor();"));
        assertTrue(source.contains("unsetBrightness()V"));
        assertTrue(source.contains("OculusRuntimeValidation.logLocalPlayerModelRender(entity, \"normal-draw-state\");"));
        assertTrue(source.contains("OculusRuntimeValidation.logLocalPlayerModelRender(entity, \"normal-after\");"));
        assertTrue(source.contains("oculus$disableBlendForShaderEntityBaseModel();"));
        assertTrue(source.contains("oculus$syncShaderEntityProgramForBaseModel();"));
        assertTrue(source.contains("oculus$resetShaderEntityTextureMatrix();"));
        assertTrue(source.contains("if (pipeline instanceof ShaderWorldRenderingPipeline)"));
        assertTrue(source.contains("((ShaderWorldRenderingPipeline) pipeline).syncEntityProgramForLegacyDraw();"));
        assertTrue(source.contains("oculus$restoreBlendAfterShaderEntityBaseModel();"));
        assertTrue(source.contains("oculus$syncShaderEntityProgramForBaseModel();\n        OculusRuntimeValidation.logLocalPlayerModelRender(entity, \"normal-after\");"));
        assertTrue(source.contains("oculus$syncShaderEntityProgramForBaseModel();\n        layer.doRenderLayer(entity"));
        assertTrue(source.contains("oculus$restoreOpaqueWhiteColorForShaderEntityBaseModel();"));
        assertTrue(source.contains("GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);"));
        assertTrue(source.contains("GlStateManager.matrixMode(GL11.GL_TEXTURE);"));
        assertTrue(source.contains("GlStateManager.loadIdentity();"));
        assertTrue(source.contains("GlStateManager.disableBlend();"));
        assertTrue(source.contains("GlStateManager.enableBlend();"));
        assertTrue(source.contains("GL11.glIsEnabled(GL11.GL_BLEND)"));
    }

    @Test
    public void mixinConfigQueuesEntityColorCapture() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/oculus.mixins.json")), StandardCharsets.UTF_8);

        assertTrue(source.contains("\"pipeline.RenderLivingBaseEntityColorMixin\""));
    }
}
