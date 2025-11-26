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

    Program(String name, int program, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images) {
        super(program);
        this.name = name;
        this.uniforms = uniforms;
        this.samplers = samplers;
        this.images = images;
    }

    public String getName() {
        return name;
    }

    public void use() {
        OculusRenderSystem.glUseProgram(getGlId());
    }
    
    public void bindUniforms() {
        uniforms.update();
    }
    
    public void bindSamplers() {
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
