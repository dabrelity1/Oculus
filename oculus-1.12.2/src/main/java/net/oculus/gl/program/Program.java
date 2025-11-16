package net.oculus.gl.program;

import net.oculus.gl.GlResource;
import net.oculus.gl.OculusRenderSystem;

/**
 * Minimal shader program wrapper that mirrors the responsibilities of the 1.16.5 version.
 * The real OpenGL operations are deferred until the rendering backend is wired up.
 */
public class Program extends GlResource {
    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;

    Program(int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
        super(program);
        this.uniforms = uniforms;
        this.samplers = samplers;
        this.images = images;
    }

    public void use() {
        OculusRenderSystem.glUseProgram(getGlId());
        uniforms.update();
        samplers.update();
        images.update();
    }

    public static void unbind() {
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        OculusRenderSystem.glUseProgram(0);
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
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        OculusRenderSystem.glDeleteProgram(getGlId());
    }

    public int getProgramId() {
        return getGlId();
    }

    public int getActiveImages() {
        return images.getActiveImages();
    }
}
