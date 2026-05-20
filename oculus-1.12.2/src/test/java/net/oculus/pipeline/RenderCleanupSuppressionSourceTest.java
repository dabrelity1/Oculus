package net.oculus.pipeline;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class RenderCleanupSuppressionSourceTest {
    private static final String[] AGGREGATING_CLEANUP_FILES = {
        "src/main/java/net/oculus/pipeline/ClearPass.java",
        "src/main/java/net/oculus/pipeline/ClearPassCreator.java",
        "src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java",
        "src/main/java/net/oculus/rendertarget/RenderTargets.java",
        "src/main/java/net/oculus/rendertarget/DepthTexture.java",
        "src/main/java/net/oculus/postprocess/CenterDepthSampler.java",
        "src/main/java/net/oculus/postprocess/CompositeRenderer.java",
        "src/main/java/net/oculus/postprocess/FinalPassRenderer.java",
        "src/main/java/net/oculus/postprocess/FullScreenQuadRenderer.java",
        "src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java",
        "src/main/java/net/oculus/pipeline/shadow/ShadowMap.java",
        "src/main/java/net/oculus/pipeline/framebuffer/FramebufferManager.java",
        "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer.java",
        "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java",
        "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/RenderTarget.java"
    };

    @Test
    public void renderCleanupAggregatorsAvoidJavaSelfSuppression() throws Exception {
        for (String path : AGGREGATING_CLEANUP_FILES) {
            String source = read(path);
            String addCleanupFailure = method(source, "private static Throwable addCleanupFailure");
            String addSuppressedCleanupFailure = method(source, "private static void addSuppressedCleanupFailure");

            assertTrue(path + " must keep the first cleanup failure as primary",
                addCleanupFailure.contains("if (failure == null)"));
            assertTrue(path + " must not add a throwable as suppressed onto itself",
                addCleanupFailure.contains("if (exception != failure)"));
            assertTrue(path + " must still preserve distinct cleanup failures as suppressed context",
                addCleanupFailure.contains("failure.addSuppressed(exception);"));

            assertTrue(path + " must ignore null cleanup failures",
                addSuppressedCleanupFailure.contains("primary != null && cleanupFailure != null"));
            assertTrue(path + " must not suppress a primary failure onto itself",
                addSuppressedCleanupFailure.contains("cleanupFailure != primary"));
            assertTrue(path + " must still preserve distinct post-failure cleanup context",
                addSuppressedCleanupFailure.contains("primary.addSuppressed(cleanupFailure);"));
        }
    }

    @Test
    public void directConstructorCleanupSuppressionAvoidsSelfSuppression() throws Exception {
        assertSelfSuppressionGuard(
            "src/main/java/net/oculus/rendertarget/RenderTargets.java",
            "cleanupException",
            "exception");
        assertSelfSuppressionGuard(
            "src/main/java/net/oculus/postprocess/CompositeRenderer.java",
            "cleanupException",
            "exception");
        assertSelfSuppressionGuard(
            "src/main/java/net/oculus/pipeline/shadow/ShadowMap.java",
            "cleanupException",
            "exception");
        assertSelfSuppressionGuard(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java",
            "cleanupException",
            "exception");
        assertSelfSuppressionGuard(
            "src/main/java/com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/FramebufferManager.java",
            "rollbackException",
            "exception");
    }

    private static void assertSelfSuppressionGuard(String path, String suppressed, String primary) throws Exception {
        String source = read(path);
        String guard = "if (" + suppressed + " != " + primary + ") {";
        String suppress = primary + ".addSuppressed(" + suppressed + ");";
        int guardIndex = source.indexOf(guard);
        int suppressIndex = source.indexOf(suppress, guardIndex);

        assertTrue(path + " must guard " + suppress + " against Java self-suppression",
            guardIndex >= 0 && suppressIndex > guardIndex);
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing helper " + signature, start >= 0);

        int brace = source.indexOf('{', start);
        assertTrue("Missing helper body " + signature, brace >= 0);

        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }

        throw new AssertionError("Unterminated helper " + signature);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
