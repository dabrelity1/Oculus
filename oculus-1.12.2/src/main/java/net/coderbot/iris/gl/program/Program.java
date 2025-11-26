package net.coderbot.iris.gl.program;

import java.util.Locale;

import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.shader.Shader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class Program extends GlResource {
    private static final Logger LOGGER = LogManager.getLogger(Program.class);

    private final ProgramUniforms uniforms;
    private final ProgramSamplers samplers;
    private final ProgramImages images;
    private final String name;

    public Program(int handle, ProgramUniforms uniforms, ProgramSamplers samplers, ProgramImages images, String name) {
        super(handle);
        this.uniforms = uniforms;
        this.samplers = samplers;
        this.images = images;
        this.name = name;
    }

    public static Program link(String name, Shader... shaders) {
        int handle = IrisRenderSystem.createProgram();

        for (Shader shader : shaders) {
            IrisRenderSystem.attachShader(handle, shader.getHandle());
        }

        IrisRenderSystem.linkProgram(handle);

        String log = IrisRenderSystem.getProgramInfoLog(handle).trim();
        if (!log.isEmpty()) {
            LOGGER.warn("Program link log for {}: {}", name, log);
        }

        int status = IrisRenderSystem.getProgramParameter(handle, GL20.GL_LINK_STATUS);
        if (status != GL11.GL_TRUE) {
            IrisRenderSystem.deleteProgram(handle);
            throw new IllegalStateException("Failed to link shader program '" + name + "'.");
        }

        for (Shader shader : shaders) {
            IrisRenderSystem.detachShader(handle, shader.getHandle());
        }

        return new Program(handle, ProgramUniforms.noop(), ProgramSamplers.noop(), ProgramImages.noop(), name);
    }

    public void use() {
        IrisRenderSystem.useProgram(getGlId());
        uniforms.update();
        samplers.update();
        images.update();
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
