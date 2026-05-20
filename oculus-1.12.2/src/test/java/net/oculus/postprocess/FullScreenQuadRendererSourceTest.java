package net.oculus.postprocess;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class FullScreenQuadRendererSourceTest {
    @Test
    public void fullscreenQuadUsesOnlyProjectionAndModelViewState() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java");
        String begin = methodBody(source, "public void begin()");

        int projectionMode = begin.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);");
        int pushProjection = begin.indexOf("GlStateManager.pushMatrix();", projectionMode);
        int loadProjectionIdentity = begin.indexOf("GlStateManager.loadIdentity();", pushProjection);
        int ortho = begin.indexOf("GlStateManager.ortho(-1.0, 1.0, -1.0, 1.0, -1.0, 1.0);",
            loadProjectionIdentity);
        int modelViewMode = begin.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW);", ortho);
        int pushModelView = begin.indexOf("GlStateManager.pushMatrix();", modelViewMode);
        int loadModelViewIdentity = begin.indexOf("GlStateManager.loadIdentity();", pushModelView);
        int disableDepth = begin.indexOf("GlStateManager.disableDepth();", loadModelViewIdentity);

        assertTrue("Fullscreen begin must configure a projection/model-view fullscreen transform",
            projectionMode >= 0 && pushProjection > projectionMode && loadProjectionIdentity > pushProjection
                && ortho > loadProjectionIdentity && modelViewMode > ortho && pushModelView > modelViewMode
                && loadModelViewIdentity > pushModelView);
        assertTrue("Fullscreen begin must disable depth testing for postprocess passes",
            disableDepth > loadModelViewIdentity);
        assertTrue("Fullscreen quad helper must not own depth write-mask state",
            !source.contains("GlStateManager.depthMask("));
        assertTrue("Fullscreen quad setup should not mutate the texture matrix on LWJGL2",
            !source.contains("GL_TEXTURE") && !source.contains("GL_TEXTURE_MATRIX")
                && !source.contains("glLoadMatrix") && !source.contains("glGetFloat"));
    }

    @Test
    public void endRestoresProjectionModelViewAndDepthState() throws Exception {
        String source = read("src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java");
        String end = methodBody(source, "public static void end()");

        int projectionMode = end.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);");
        int popProjection = end.indexOf("GlStateManager.popMatrix();", projectionMode);
        int modelViewMode = end.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW);", popProjection);
        int popModelView = end.indexOf("GlStateManager.popMatrix();", modelViewMode);
        int enableDepth = end.indexOf("GlStateManager.enableDepth();", popModelView);

        assertTrue("Fullscreen end must restore projection/model-view stacks before depth state",
            projectionMode >= 0 && popProjection > projectionMode && modelViewMode > popProjection
                && popModelView > modelViewMode && enableDepth > popModelView);
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
