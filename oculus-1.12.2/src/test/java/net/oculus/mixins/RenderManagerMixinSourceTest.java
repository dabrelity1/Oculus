package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RenderManagerMixinSourceTest {
    @Test
    public void suppressesVanillaEntityShadowsWhenShaderShadowPipelineOwnsThem() throws Exception {
        String mixin = read("src/main/java/net/oculus/mixin/pipeline/RenderManagerMixin.java");
        String reference = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/mixin/MixinEntityRenderDispatcher.java");
        String config = read("src/main/resources/oculus.mixins.json");
        String redirect = methodBody(mixin, "private void oculus$maybeSuppressVanillaEntityShadow");

        assertTrue(reference.contains("pipeline.shouldDisableVanillaEntityShadows()"));
        assertTrue(mixin.contains("@Mixin(RenderManager.class)"));
        assertTrue(mixin.contains("@Redirect("));
        assertTrue(mixin.contains("@Shadow\n    private boolean renderShadow;"));
        assertTrue(mixin.contains("method = \"renderEntity(Lnet/minecraft/entity/Entity;DDDFFZ)V\""));
        assertTrue(mixin.contains(
            "target = \"Lnet/minecraft/client/renderer/entity/Render;doRenderShadowAndFire(Lnet/minecraft/entity/Entity;DDDFF)V\""));
        assertTrue(redirect.contains("WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();"));
        int disabledGuard = redirect.indexOf("if (pipeline != null && pipeline.shouldDisableVanillaEntityShadows())");
        int saveShadowFlag = redirect.indexOf("boolean previousRenderShadow = this.renderShadow;", disabledGuard);
        int suppressShadowFlag = redirect.indexOf("this.renderShadow = false;", saveShadowFlag);
        int tryBlock = redirect.indexOf("try {", suppressShadowFlag);
        int disabledCall = redirect.indexOf(
            "renderer.doRenderShadowAndFire(entity, x, y, z, entityYaw, partialTicks);", tryBlock);
        int finallyBlock = redirect.indexOf("} finally {", disabledCall);
        int restoreShadowFlag = redirect.indexOf("this.renderShadow = previousRenderShadow;", finallyBlock);
        int disabledReturn = redirect.indexOf("return;", restoreShadowFlag);
        int normalCall = redirect.indexOf(
            "renderer.doRenderShadowAndFire(entity, x, y, z, entityYaw, partialTicks);", disabledReturn);

        assertTrue(disabledGuard >= 0);
        assertTrue("Vanilla shadow suppression must preserve fire rendering by still invoking doRenderShadowAndFire",
            saveShadowFlag > disabledGuard && suppressShadowFlag > saveShadowFlag && tryBlock > suppressShadowFlag
                && disabledCall > tryBlock);
        assertTrue("The RenderManager shadow flag must be restored even when fire rendering throws",
            finallyBlock > disabledCall && restoreShadowFlag > finallyBlock && disabledReturn > restoreShadowFlag);
        assertTrue("Normal vanilla shadow/fire rendering must continue when shader shadows are inactive",
            normalCall > disabledReturn);
        assertTrue(config.contains("\"pipeline.RenderManagerMixin\""));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }

        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Unable to read method body for " + signature);
    }
}
