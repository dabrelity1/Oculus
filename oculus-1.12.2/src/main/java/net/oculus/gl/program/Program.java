package net.oculus.gl.program;

import net.oculus.gl.GlResource;
import net.oculus.gl.OculusRenderSystem;

/**
 * Shader program wrapper that handles uniform and sampler binding.
 */
public class Program extends GlResource {
    private final String name;
    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;
    private final boolean ownsProgramHandle;

    Program(String name, int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
        this(name, program, uniforms, samplers, images, true);
    }

    Program(String name, int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images,
            boolean ownsProgramHandle) {
        super(program);
        this.name = name;
        this.uniforms = uniforms;
        this.samplers = samplers;
        this.images = images;
        this.ownsProgramHandle = ownsProgramHandle;
    }

    public String getName() {
        return name;
    }

    public void use() {
        try {
            activate();
        } catch (RuntimeException exception) {
            cleanupAfterActivationFailure(exception);
            throw exception;
        } catch (Error error) {
            cleanupAfterActivationFailure(error);
            throw error;
        }
    }

    protected final void activate() {
        OculusRenderSystem.glUseProgram(getGlId());
        uniforms.update();
        samplers.update();
        images.update();
    }

    public void bindUniforms() {
        uniforms.update();
    }

    public void bindSamplers() {
        samplers.update();
    }

    public void bindImages() {
        images.update();
    }

    public static void unbind() {
        clearActiveBindingsAndProgram();
    }

    static void clearActiveBindingsAndProgram() {
        Throwable failure = null;
        failure = runCleanup(failure, ProgramUniforms::clearActiveUniforms);
        failure = runCleanup(failure, ProgramSamplers::clearActiveSamplers);
        failure = runCleanup(failure, ProgramImages::clearActiveImages);
        failure = runCleanup(failure, () -> OculusRenderSystem.glUseProgram(0));
        rethrowCleanupFailure(failure);
    }

    public static void cleanupAfterActivationFailure(Throwable failure) {
        try {
            clearActiveBindingsAndProgram();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
        }
    }

    protected ProgramUniforms getUniforms() {
        return uniforms;
    }

    protected ProgramSamplers getSamplers() {
        return samplers;
    }

    protected ProgramImages getImages() {
        return images;
    }

    @Override
    protected void destroyInternal() {
        Throwable failure = null;
        failure = runCleanup(failure, () -> ProgramUniforms.clearActiveUniforms(uniforms));
        failure = runCleanup(failure, () -> ProgramSamplers.clearActiveSamplers(samplers));
        failure = runCleanup(failure, () -> ProgramImages.clearActiveImages(images));
        if (ownsProgramHandle) {
            failure = runCleanup(failure, () -> OculusRenderSystem.glDeleteProgram(getGlId()));
        }
        rethrowCleanupFailure(failure);
    }

    public int getProgramId() {
        return getGlId();
    }

    public int getActiveImages() {
        return images.getActiveImages();
    }

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            if (failure != null) {
                suppressCleanupFailure(failure, exception);
                return failure;
            }
            return exception;
        }
        return failure;
    }

    private static void rethrowCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
