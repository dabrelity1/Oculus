package net.oculus.gl.program;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ComputeProgramSourceTest {
    @Test
    public void dispatchPublishesBindingsAndPreDispatchBarrierBeforeComputeWork() throws Exception {
        String source = read("src/main/java/net/oculus/gl/program/ComputeProgram.java");
        String program = read("src/main/java/net/oculus/gl/program/Program.java");
        String dispatch = methodBody(source, "public void dispatch(float width, float height)");
        String activateBody = methodBody(program, "protected final void activate()");
        String concurrent = methodBody(source, "private static boolean isConcurrentComputeAllowed()");

        int supportGuard = dispatch.indexOf("if (!OculusRenderSystem.supportsCompute())");
        int tryBlock = dispatch.indexOf("try {", supportGuard);
        int activate = dispatch.indexOf("activate();", tryBlock);
        int concurrentGuard = dispatch.indexOf("if (!isConcurrentComputeAllowed())", activate);
        int barrier = dispatch.indexOf("OculusRenderSystem.memoryBarrier(PRE_DISPATCH_BARRIER);",
            concurrentGuard);
        int workGroups = dispatch.indexOf("Vector3i workGroups = getWorkGroups(width, height);", barrier);
        int dispatchCompute = dispatch.indexOf("OculusRenderSystem.dispatchCompute(workGroups);", workGroups);
        int runtimeCatch = dispatch.indexOf("catch (RuntimeException exception)", dispatchCompute);
        int runtimeCleanup = dispatch.indexOf("Program.cleanupAfterActivationFailure(exception);", runtimeCatch);
        int runtimeRethrow = dispatch.indexOf("throw exception;", runtimeCleanup);
        int errorCatch = dispatch.indexOf("catch (Error error)", runtimeRethrow);
        int errorCleanup = dispatch.indexOf("Program.cleanupAfterActivationFailure(error);", errorCatch);
        int errorRethrow = dispatch.indexOf("throw error;", errorCleanup);
        int useProgram = activateBody.indexOf("OculusRenderSystem.glUseProgram(getGlId());");
        int uniforms = activateBody.indexOf("uniforms.update();", useProgram);
        int samplers = activateBody.indexOf("samplers.update();", uniforms);
        int images = activateBody.indexOf("images.update();", samplers);

        assertTrue(supportGuard >= 0);
        assertTrue(tryBlock > supportGuard);
        assertTrue(activate > tryBlock);
        assertTrue("Program activation must publish uniforms, samplers, and images before compute barriers",
            useProgram >= 0 && uniforms > useProgram && samplers > uniforms && images > samplers);
        assertTrue("Compute dispatch must make prior image writes visible before dispatching work",
            barrier > concurrentGuard);
        assertTrue(workGroups > barrier);
        assertTrue(dispatchCompute > workGroups);
        assertTrue(runtimeCatch > dispatchCompute);
        assertTrue(runtimeCleanup > runtimeCatch);
        assertTrue(runtimeRethrow > runtimeCleanup);
        assertTrue(errorCatch > runtimeRethrow);
        assertTrue(errorCleanup > errorCatch);
        assertTrue(errorRethrow > errorCleanup);
        assertTrue(source.contains("private static final int PRE_DISPATCH_BARRIER = GL42.GL_TEXTURE_FETCH_BARRIER_BIT"));
        assertTrue(source.contains("| GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;"));
        assertTrue(concurrent.contains("WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();"));
        assertTrue(concurrent.contains("return pipeline != null && pipeline.allowConcurrentCompute();"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method signature: " + signature);
        }
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(brace, i + 1);
                }
            }
        }
        throw new AssertionError("Could not isolate method body for: " + signature);
    }
}
