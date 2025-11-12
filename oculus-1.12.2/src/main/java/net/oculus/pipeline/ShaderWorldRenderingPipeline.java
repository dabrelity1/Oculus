package net.oculus.pipeline;

import java.util.Objects;

import com.mojang.blaze3d.systems.RenderSystem;

import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ProgramSet;

/**
 * Skeleton port of the 1.16.5 deferred shader pipeline. The current
 * implementation merely bridges the lifecycle hooks so that surrounding systems
 * can compile and be exercised before the full renderer is brought over.
 */
public final class ShaderWorldRenderingPipeline implements WorldRenderingPipeline {
    private final ProgramSet programs;
    private final PackDirectives directives;
    private boolean prepared;

    public ShaderWorldRenderingPipeline(ProgramSet programs) {
        this.programs = Objects.requireNonNull(programs, "programs");
        this.directives = programs.getPackDirectives();
        RenderSystem.initializeShaderPipeline();
    }

    @Override
    public void beginWorldRendering(float partialTicks) {
        prepared = true;
        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void endWorldRendering() {
        if (!prepared) {
            return;
        }

        RenderSystem.disableBlend();
        RenderSystem.popMatrix();
        RenderSystem.resetTextureBindings();
        prepared = false;
    }

    @Override
    public void destroy() {
        RenderSystem.releaseShaderPipeline();
    }

    public ProgramSet getPrograms() {
        return programs;
    }

    public PackDirectives getPackDirectives() {
        return directives;
    }
}
