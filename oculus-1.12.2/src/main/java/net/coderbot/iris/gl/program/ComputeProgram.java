package net.coderbot.iris.gl.program;

import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.IrisRenderSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Bare-bones compute program placeholder. Minecraft 1.12.2 cannot execute compute shaders, but
 * we keep the abstraction so future work can emulate or fall back appropriately.
 */
public final class ComputeProgram extends GlResource {
    private static final Logger LOGGER = LogManager.getLogger(ComputeProgram.class);

    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;
    private final String name;
    private final boolean computeSupported;

    public ComputeProgram(int handle, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images, String name) {
        super(handle);
        this.uniforms = uniforms;
        this.samplers = samplers;
        this.images = images;
        this.name = name;
        this.computeSupported = IrisRenderSystem.supportsCompute();

        if (!computeSupported) {
            LOGGER.warn("Compute program '{}' created on a platform without compute support. Calls to dispatch() will throw.", name);
        }
    }

    public void use() {
        IrisRenderSystem.useProgram(getGlId());
        uniforms.update();
        samplers.update();
        images.update();
    }

    public void dispatch(int workX, int workY, int workZ) {
        if (!computeSupported) {
            throw new UnsupportedOperationException("Compute shaders are not available on LWJGL 2 / Minecraft 1.12.2");
        }

        IrisRenderSystem.useProgram(getGlId());
        uniforms.update();
        samplers.update();
        images.update();
        IrisRenderSystem.dispatchCompute(workX, workY, workZ);
    }

    public static void unbind() {
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        IrisRenderSystem.unbindPrograms();
    }

    @Override
    protected void destroyInternal() {
        IrisRenderSystem.deleteProgram(getGlId());
    }

    @Deprecated
    public int getProgramId() {
        return getGlId();
    }

    public int getActiveImages() {
        return images.getActiveImages();
    }

    public String getName() {
        return name;
    }
}
