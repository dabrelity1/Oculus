package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class WorldRendererMixinSourceTest {
    @Test
    public void translucentBoundaryRendersShaderHandBeforeDeferredPass() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/WorldRendererMixin.java");

        int branch = source.indexOf("if (layer == BlockRenderLayer.TRANSLUCENT)");
        int renderHand = source.indexOf("oculus$renderShaderHandBeforeTranslucents(partialTicks, pass, pipeline);", branch);
        int beginTranslucents = source.indexOf("pipeline.beginTranslucents();", renderHand);

        assertTrue(branch >= 0);
        assertTrue("Early hand rendering should invoke EntityRenderer.renderHand once and let its mixin begin the hand phase",
            renderHand > branch);
        assertTrue(beginTranslucents > renderHand);
        assertTrue("RenderGlobal must not call beginHand directly or the EntityRenderer.renderHand mixin will double-run it",
            source.indexOf("pipeline.beginHand();", branch) < 0);
    }

    @Test
    public void earlyShaderHandPreservesWorldMatricesAndSkipsShadowPass() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/WorldRendererMixin.java");
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(config.contains("\"pipeline.EntityRendererAccessor\""));
        assertTrue(source.contains("pipeline instanceof ShaderWorldRenderingPipeline"));
        assertTrue(source.contains("pipeline.isRenderingShadowPass()"));
        assertTrue(source.contains("renderer instanceof EntityRendererAccessor"));
        assertTrue(source.contains("int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);"));
        assertTrue(source.contains("GlStateManager.matrixMode(GL11.GL_PROJECTION);"));
        assertTrue(source.contains("GlStateManager.matrixMode(GL11.GL_MODELVIEW);"));
        assertTrue(source.contains("((EntityRendererAccessor) renderer).oculus$invokeRenderHand((float) partialTicks, pass);"));
        assertTrue(source.contains("GlStateManager.matrixMode(previousMatrixMode);"));
    }

    @Test
    public void shaderBlockEntityPassRestoresOpaqueDepthWritingState() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/WorldRendererMixin.java");

        assertTrue(source.contains("oculus$applyBlockEntityDrawState();"));
        assertTrue(source.contains("oculus$closeBlockEntityPhase();"));
        assertTrue(source.contains("oculus$restoreBlockEntityDrawState();"));
        int beginPhase = source.indexOf("private void oculus$beginBlockEntities");
        int beginVanillaBatch = source.indexOf("private void oculus$beginVanillaBlockEntityBatch");
        int applyState = source.indexOf("oculus$applyBlockEntityDrawState();", beginVanillaBatch);

        assertTrue(beginPhase >= 0);
        assertTrue(beginVanillaBatch > beginPhase);
        assertTrue("Vanilla block-entity draw-state forcing must start at preDrawBatch so Relictium cancel paths do not leak it",
            applyState > beginVanillaBatch);
        assertTrue(source.indexOf("MinecraftForgeClient.getRenderPass()") < 0);
        assertTrue(source.contains("oculus$previousBlockEntityDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);"));
        assertTrue(source.contains("oculus$previousBlockEntityBlend = GL11.glIsEnabled(GL11.GL_BLEND);"));
        assertTrue(source.contains("GlStateManager.depthMask(true);"));
        assertTrue(source.contains("GlStateManager.disableBlend();"));
        assertTrue(source.contains("GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);"));
    }

    @Test
    public void relictiumTileEntityRendererOwnsCancelledBlockEntityScope() throws Exception {
        String source = read("src/main/java/net/oculus/mixin/pipeline/RelictiumTileEntityRenderMixin.java");
        String config = read("src/main/resources/oculus.mixins.json");

        assertTrue(config.contains("\"pipeline.RelictiumTileEntityRenderMixin\""));
        assertTrue(source.contains("@Mixin(value = SodiumWorldRenderer.class, remap = false)"));
        assertTrue(source.contains("method = \"renderTileEntities\""));
        assertTrue(source.contains("pipeline.setPhase(WorldRenderingPhase.BLOCK_ENTITIES);"));
        assertTrue(source.contains("pipeline.setPhase(WorldRenderingPhase.NONE);"));
        int begin = source.indexOf("private void oculus$beginRelictiumTileEntities");
        int apply = source.indexOf("GlStateManager.disableBlend();", begin);
        int end = source.indexOf("private void oculus$endRelictiumTileEntities");
        int restore = source.indexOf("oculus$restoreRelictiumTileEntityDrawState();", end);

        assertTrue(begin >= 0);
        assertTrue("Relictium tile entities must render with opaque depth-writing block-entity state",
            apply > begin);
        assertTrue("Relictium tile entities must close their shader phase even though vanilla renderEntities is cancelled",
            restore > end);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
