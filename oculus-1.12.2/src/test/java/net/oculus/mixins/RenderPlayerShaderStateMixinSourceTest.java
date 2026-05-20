package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RenderPlayerShaderStateMixinSourceTest {
    @Test
    public void hardensPlayerBodyAndHandStateForShaderPipeline() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/RenderPlayerShaderStateMixin.java")),
            StandardCharsets.UTF_8);

        assertTrue(source.contains("@Mixin(RenderPlayer.class)"));
        assertTrue(source.contains("RenderPlayer;setModelVisibilities"));
        assertTrue(source.contains("RenderLivingBase;doRender"));
        assertTrue(source.contains("renderRightArm(Lnet/minecraft/client/entity/AbstractClientPlayer;)V"));
        assertTrue(source.contains("renderLeftArm(Lnet/minecraft/client/entity/AbstractClientPlayer;)V"));
        assertTrue(source.contains("oculus$showModelPart(model.bipedBody);"));
        assertTrue(source.contains("oculus$showModelPart(model.bipedLeftArm);"));
        assertTrue(source.contains("oculus$showModelPart(model.bipedRightLeg);"));
        assertTrue(source.contains("part.showModel = true;"));
        assertTrue(source.contains("part.isHidden = false;"));
        assertTrue(source.contains("GlStateManager.depthMask(true);"));
        assertTrue(source.contains("GlStateManager.disableBlend();"));
        assertTrue(source.contains("GlStateManager.matrixMode(GL11.GL_TEXTURE);"));
        assertTrue(source.contains("GlStateManager.loadIdentity();"));
        assertTrue(source.contains("((ShaderWorldRenderingPipeline) pipeline).syncProgram();"));
        assertTrue(source.contains("OculusRuntimeValidation.logLocalPlayerModelParts(player, getMainModel(), \"before-render-living-base\");"));
        assertTrue(source.contains("OculusRuntimeValidation.logLocalPlayerHandRender(player, stage);"));
    }

    @Test
    public void mixinConfigQueuesPlayerShaderStateMixin() throws IOException {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/oculus.mixins.json")), StandardCharsets.UTF_8);

        assertTrue(source.contains("\"pipeline.RenderPlayerShaderStateMixin\""));
    }
}
