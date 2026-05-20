package net.oculus.compat.relictium;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class OculusRelictiumChunkProgramSourceTest {
    @Test
    public void setupRestoresRelictiumOwnedUniformsAndUploadsIrisMatricesAfterOculusBindings() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgram.java");
        String setup = methodBody(source, "public void setup(float modelScale, float textureScale)");

        int tryBlock = setup.indexOf("try {");
        int bindUniforms = setup.indexOf("oculusProgram.bindUniforms();");
        int superSetup = setup.indexOf("super.setup(modelScale, textureScale);");
        int bindSamplers = setup.indexOf("oculusProgram.bindSamplers();");
        int bindImages = setup.indexOf("oculusProgram.bindImages();");
        int uploadMatrices = setup.indexOf("uploadIrisMatrices();");

        assertTrue("Wrapped setup must run inside a cleanup guard",
            tryBlock >= 0 && bindUniforms > tryBlock);
        assertTrue("Oculus uniforms must update before Relictium restores u_ModelViewProjectionMatrix and scale uniforms",
            bindUniforms >= 0 && bindUniforms < superSetup);
        assertTrue("Oculus samplers must still update for wrapped terrain programs",
            bindSamplers > superSetup);
        assertTrue("Oculus images must update explicitly after samplers like the 1.16.5 IrisChunkProgram path",
            bindImages > bindSamplers);
        assertTrue("Iris model/normal matrices must be uploaded after all wrapped binding updates",
            uploadMatrices > bindImages);

        assertTrue(source.contains("findUniformLocation(handle, \"iris_ModelViewMatrix\")"));
        assertTrue(source.contains("findUniformLocation(handle, \"iris_ProjectionMatrix\")"));
        assertTrue(source.contains("findUniformLocation(handle, \"u_ModelViewProjectionMatrix\")"));
        assertTrue(source.contains("findUniformLocation(handle, \"iris_ModelViewProjectionMatrix\")"));
        assertTrue(source.contains("findUniformLocation(handle, \"iris_NormalMatrix\")"));
        assertTrue(source.contains("MatrixState.updateModelViewMatrix()"));
        assertTrue(source.contains("MatrixState.updateProjectionMatrix()"));
        assertTrue(source.contains("MatrixMath.multiply(currentProjection, currentModelView, currentModelViewProjection);"));
        assertTrue(source.contains("MatrixMath.transpose(currentModelViewInverse, currentNormalMatrix);"));
        assertTrue(source.contains("uploadMatrix(irisProjectionMatrixLocation, currentProjection);"));
        assertTrue(source.contains("uploadMatrix(uModelViewProjectionMatrixLocation, currentModelViewProjection);"));
        assertTrue(source.contains("GL20.glUniformMatrix4(location, false, matrixBuffer);"));
    }

    @Test
    public void irisMatricesUseActiveGlStateSoShadowTerrainMatchesShadowProjection() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgram.java");
        String upload = methodBody(source, "private void uploadIrisMatrices()");

        assertTrue(source.contains("import net.oculus.gl.state.MatrixMath;"));
        assertTrue(source.contains("import net.oculus.gl.state.MatrixState;"));
        assertTrue(source.contains("private final float[] currentModelView = MatrixMath.createIdentity();"));
        assertTrue(source.contains("private final float[] currentProjection = MatrixMath.createIdentity();"));
        assertTrue(source.contains("private final float[] currentModelViewProjection = MatrixMath.createIdentity();"));
        assertTrue(source.contains("private final float[] currentNormalMatrix = MatrixMath.createIdentity();"));
        assertTrue("Relictium terrain wrapper must not force camera gbuffer matrices during shadow rendering",
            !source.contains("CapturedRenderingState.INSTANCE.getGbufferModelView()"));

        int guard = upload.indexOf("if (irisModelViewMatrixLocation < 0");
        int modelView = upload.indexOf("MatrixMath.copyFromBuffer(MatrixState.updateModelViewMatrix(), currentModelView);", guard);
        int projection = upload.indexOf("MatrixMath.copyFromBuffer(MatrixState.updateProjectionMatrix(), currentProjection);", modelView);
        int modelViewProjection = upload.indexOf("MatrixMath.multiply(currentProjection, currentModelView, currentModelViewProjection);", projection);
        int invert = upload.indexOf("MatrixMath.invert(currentModelView, currentModelViewInverse);", modelViewProjection);
        int normal = upload.indexOf("MatrixMath.transpose(currentModelViewInverse, currentNormalMatrix);", invert);
        int uploadModelView = upload.indexOf("uploadMatrix(irisModelViewMatrixLocation, currentModelView);", normal);
        int uploadProjection = upload.indexOf("uploadMatrix(irisProjectionMatrixLocation, currentProjection);", uploadModelView);
        int uploadModelViewProjection = upload.indexOf("uploadMatrix(uModelViewProjectionMatrixLocation, currentModelViewProjection);", uploadProjection);
        int uploadIrisModelViewProjection = upload.indexOf("uploadMatrix(irisModelViewProjectionMatrixLocation, currentModelViewProjection);", uploadModelViewProjection);
        int uploadNormal = upload.indexOf("uploadMatrix(irisNormalMatrixLocation, currentNormalMatrix);", uploadIrisModelViewProjection);

        assertTrue("Active model/projection matrices must be captured before wrapper Iris uniform upload",
            guard >= 0 && modelView > guard && projection > modelView);
        assertTrue("MVP matrix must be derived from the same active model and projection snapshot",
            modelViewProjection > projection);
        assertTrue("Normal matrix must be derived from the same active model-view matrix",
            invert > modelViewProjection && normal > invert);
        assertTrue("Model, projection, MVP, and normal Iris uniforms must all use the active GL state snapshot",
            uploadModelView > normal
                && uploadProjection > uploadModelView
                && uploadModelViewProjection > uploadProjection
                && uploadIrisModelViewProjection > uploadModelViewProjection
                && uploadNormal > uploadIrisModelViewProjection);
    }

    @Test
    public void setupCleansOculusBindingsIfRelictiumOrWrappedBindingSetupFails() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgram.java");
        String program = read("src/main/java/net/oculus/gl/program/Program.java");
        String setup = methodBody(source, "public void setup(float modelScale, float textureScale)");
        String cleanup = methodBody(source, "private void cleanupAfterSetupFailure(Throwable failure)");

        int uploadMatrices = setup.indexOf("uploadIrisMatrices();");
        int runtimeCatch = setup.indexOf("catch (RuntimeException exception)", uploadMatrices);
        int runtimeCleanup = setup.indexOf("cleanupAfterSetupFailure(exception);", runtimeCatch);
        int runtimeRethrow = setup.indexOf("throw exception;", runtimeCleanup);
        int errorCatch = setup.indexOf("catch (Error error)", runtimeRethrow);
        int errorCleanup = setup.indexOf("cleanupAfterSetupFailure(error);", errorCatch);
        int errorRethrow = setup.indexOf("throw error;", errorCleanup);
        int guard = cleanup.indexOf("if (oculusProgram != null)");
        int cleanupCall = cleanup.indexOf("Program.cleanupAfterActivationFailure(failure);", guard);

        assertTrue(uploadMatrices >= 0);
        assertTrue(runtimeCatch > uploadMatrices);
        assertTrue(runtimeCleanup > runtimeCatch);
        assertTrue(runtimeRethrow > runtimeCleanup);
        assertTrue(errorCatch > runtimeRethrow);
        assertTrue(errorCleanup > errorCatch);
        assertTrue(errorRethrow > errorCleanup);
        assertTrue("Only wrapped Oculus programs own active binding cleanup here",
            guard >= 0 && cleanupCall > guard);
        assertTrue("Relictium wrapper must be able to reuse the shared Program activation cleanup",
            program.contains("public static void cleanupAfterActivationFailure(Throwable failure)"));
    }

    @Test
    public void programKeepsSamplerAndImageUpdatesSeparatedForWrappedRelictiumPrograms() throws Exception {
        String source = read("src/main/java/net/oculus/gl/program/Program.java");

        String bindSamplers = methodBody(source, "public void bindSamplers()");
        String bindImages = methodBody(source, "public void bindImages()");

        assertTrue(bindSamplers.contains("samplers.update();"));
        assertTrue(bindImages.contains("images.update();"));
        assertTrue("Sampler helper should not hide image binding updates for Relictium wrappers",
            !bindSamplers.contains("images.update();"));
    }

    @Test
    public void wrappedProgramBuilderDoesNotOwnRelictiumLinkedProgramHandle() throws Exception {
        String program = read("src/main/java/net/oculus/gl/program/Program.java");
        String builder = read("src/main/java/net/oculus/gl/program/ProgramBuilder.java");
        String destroy = methodBody(program, "protected void destroyInternal()");
        String build = methodBody(builder, "public Program build()");

        int clearUniforms = destroy.indexOf("ProgramUniforms.clearActiveUniforms(uniforms)");
        int clearSamplers = destroy.indexOf("ProgramSamplers.clearActiveSamplers(samplers)", clearUniforms);
        int clearImages = destroy.indexOf("ProgramImages.clearActiveImages(images)", clearSamplers);
        int ownershipCheck = destroy.indexOf("if (ownsProgramHandle)", clearImages);
        int deleteProgram = destroy.indexOf("OculusRenderSystem.glDeleteProgram(getGlId())", ownershipCheck);

        assertTrue("Program must keep explicit GL handle ownership state",
            program.contains("private final boolean ownsProgramHandle;"));
        assertTrue("Destroy must clear uniform/sampler/image active state before considering GL deletion",
            clearUniforms >= 0 && clearSamplers > clearUniforms && clearImages > clearSamplers);
        assertTrue("Non-owning Relictium wrappers must skip GL program deletion",
            ownershipCheck > clearImages && deleteProgram > ownershipCheck);
        assertTrue("ProgramBuilder.build must pass ownership into the Program wrapper",
            build.contains("images.build(), ownsProgramHandle"));
        assertTrue("Relictium linked program wrappers must opt out of GL handle ownership",
            builder.contains("Collections.emptySet(), true, null, null, null, false"));
        assertTrue("Relictium linked program wrappers with notifiers must also opt out of GL handle ownership",
            builder.contains("Collections.emptySet(), true, null, frameUpdateNotifier, packDirectives, false"));
    }

    @Test
    public void deleteDestroysOculusWrapperBeforeRelictiumDeletesProgramHandle() throws Exception {
        String source = read("src/main/java/net/oculus/compat/relictium/OculusRelictiumChunkProgram.java");
        String delete = methodBody(source, "public void delete()");

        int failure = delete.indexOf("Throwable failure = null;");
        int destroyWrapper = delete.indexOf("oculusProgram.destroy();", failure);
        int catchWrapperFailure = delete.indexOf("catch (RuntimeException | Error exception)", destroyWrapper);
        int superDelete = delete.indexOf("super.delete();", catchWrapperFailure);
        int addSuppressed = delete.indexOf("suppressCleanupFailure(exception, failure);", superDelete);
        int throwSuperFailure = delete.indexOf("throw exception;", addSuppressed);
        int throwWrapperFailure = delete.indexOf("throwUnchecked(failure);", throwSuperFailure);

        assertTrue("Relictium delete must release Oculus uniform/sampler/image state first",
            destroyWrapper > failure);
        assertTrue("Oculus cleanup failures must not prevent Relictium from deleting the linked handle",
            superDelete > catchWrapperFailure);
        assertTrue("If both cleanup paths fail, the Oculus cleanup failure should remain attached",
            addSuppressed > superDelete && throwSuperFailure > addSuppressed);
        assertTrue("A standalone Oculus cleanup failure should be surfaced after Relictium cleanup runs",
            throwWrapperFailure > throwSuperFailure);
    }

    @Test
    public void deleteCleanupSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("same chunk program cleanup failure");

        suppressCleanupFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void deleteCleanupSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("chunk program delete failure");
        RuntimeException cleanupFailure = new RuntimeException("chunk program cleanup failure");

        suppressCleanupFailure(failure, cleanupFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(cleanupFailure, failure.getSuppressed()[0]);
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) throws Exception {
        Method method = OculusRelictiumChunkProgram.class.getDeclaredMethod(
            "suppressCleanupFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, cleanupFailure);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("Missing method " + signature, start >= 0);
        int bodyStart = source.indexOf('{', start);
        assertTrue("Missing method body for " + signature, bodyStart >= 0);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char ch = source.charAt(i);
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart, i + 1);
                }
            }
        }

        throw new AssertionError("Unclosed method body for " + signature);
    }
}
