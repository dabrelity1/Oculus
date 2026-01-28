package net.oculus.postprocess;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.ProgramSamplers;
import net.oculus.gl.program.ProgramUniforms;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ProgramDirectives;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.uniforms.FrameUpdateNotifier;

/**
 * Renders composite shader passes. Composite passes are post-processing effects
 * that operate on the G-buffer contents after world rendering is complete.
 * 
 * Each pass has:
 * - A shader program compiled from composite/deferred shader sources
 * - A framebuffer to render to
 * - Draw buffers specifying which color attachments to write
 * - Viewport dimensions and scale
 * 
 * Port from Iris 1.16.5 CompositeRenderer.
 */
public class CompositeRenderer {
    private static final Logger LOGGER = LogManager.getLogger(CompositeRenderer.class);
    
    private final RenderTargets renderTargets;
    private final List<Pass> passes;
    private final FrameUpdateNotifier updateNotifier;
    private final BufferFlipper bufferFlipper;
    private final IntSupplier noiseTexture;
    private final CenterDepthSampler centerDepthSampler;
    private boolean destroyed = false;
    
    /**
     * Creates a CompositeRenderer with the given shader sources.
     * 
     * @param packDirectives The shader pack directives
     * @param sources Array of composite program sources (composite, composite1, etc.)
     * @param renderTargets The render targets manager
     * @param noiseTexture Supplier for noise texture ID
     * @param updateNotifier Frame update notifier for uniforms
     * @param centerDepthSampler Center depth sampler
     * @param bufferFlipper Buffer flip state tracker
     */
    public CompositeRenderer(PackDirectives packDirectives,
                            ProgramSource[] sources,
                            RenderTargets renderTargets,
                            IntSupplier noiseTexture,
                            FrameUpdateNotifier updateNotifier,
                            CenterDepthSampler centerDepthSampler,
                            BufferFlipper bufferFlipper) {
        this.renderTargets = renderTargets;
        this.passes = new ArrayList<>();
        this.updateNotifier = updateNotifier;
        this.noiseTexture = noiseTexture;
        this.centerDepthSampler = centerDepthSampler;
        this.bufferFlipper = bufferFlipper;
        
        if (sources == null || sources.length == 0) {
            LOGGER.info("No composite sources provided");
            return;
        }
        
        for (int i = 0; i < sources.length; i++) {
            ProgramSource source = sources[i];
            if (source == null || !source.isValid()) {
                continue;
            }
            
            try {
                Pass pass = createPass(source, i);
                if (pass != null) {
                    passes.add(pass);
                    LOGGER.debug("Created composite pass {} from source {}", i, source.getName());
                }
            } catch (Exception ex) {
                LOGGER.warn("Failed to create composite pass {} from source {}: {}", 
                    i, source.getName(), ex.getMessage());
                LOGGER.debug("Composite pass creation stacktrace:", ex);
            }
        }
        
        LOGGER.info("Created {} composite pass(es)", passes.size());
    }
    
    private Pass createPass(ProgramSource source, int index) {
        ProgramDirectives directives = source.getDirectives();
        
        String vertexSource = source.getVertexSource().orElse(null);
        String fragmentSource = source.getFragmentSource().orElse(null);
        String geometrySource = source.getGeometrySource().orElse(null);
        
        if (vertexSource == null || fragmentSource == null) {
            LOGGER.warn("Composite pass {} missing vertex or fragment source", index);
            return null;
        }
        
        // Apply shader patches for compatibility
        vertexSource = patchCompositeShader(vertexSource, true);
        fragmentSource = patchCompositeShader(fragmentSource, false);
        if (geometrySource != null) {
            geometrySource = patchCompositeShader(geometrySource, false);
        }
        
        ProgramBuilder builder;
        try {
            builder = ProgramBuilder.begin(source.getName(), vertexSource, geometrySource, fragmentSource, null);
        } catch (RuntimeException e) {
            LOGGER.error("Composite shader compilation failed for {}", source.getName(), e);
            throw e;
        }
        
        // Build the program
        Program program = builder.build();
        
        // Get draw buffers from directives
        int[] drawBuffers = directives.getDrawBuffers();
        if (drawBuffers == null || drawBuffers.length == 0) {
            drawBuffers = new int[] { 0 }; // Default to colortex0
        }
        
        // Calculate viewport dimensions
        int viewWidth = 0;
        int viewHeight = 0;
        for (int buffer : drawBuffers) {
            RenderTarget target = renderTargets.get(buffer);
            if (target != null) {
                viewWidth = target.getWidth();
                viewHeight = target.getHeight();
                break;
            }
        }
        
        if (viewWidth == 0 || viewHeight == 0) {
            Minecraft mc = Minecraft.getMinecraft();
            viewWidth = Math.max(1, mc.displayWidth);
            viewHeight = Math.max(1, mc.displayHeight);
        }
        
        // Create framebuffer for this pass
        Set<Integer> flippedBuffers = bufferFlipper.snapshot();
        GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(flippedBuffers, drawBuffers);
        
        // Create the pass
        Pass pass = new Pass();
        pass.program = program;
        pass.framebuffer = framebuffer;
        pass.drawBuffers = drawBuffers;
        pass.viewWidth = viewWidth;
        pass.viewHeight = viewHeight;
        pass.viewportScale = directives.getViewportScale();
        
        // Flip buffers that this pass writes to
        for (int buffer : drawBuffers) {
            bufferFlipper.flip(buffer);
        }
        
        return pass;
    }
    
    /**
     * Patches a composite shader for 1.12.2 compatibility.
     */
    private String patchCompositeShader(String source, boolean isVertex) {
        if (source == null) return null;
        
        // Ensure minimum version
        if (!source.contains("#version")) {
            source = "#version 120\n" + source;
        }
        
        // Patch attribute/varying for old GLSL versions
        if (source.contains("#version 1")) { // version 1xx
            // In GLSL 120, use varying instead of in/out
            if (isVertex) {
                source = source.replace("in vec", "attribute vec");
                source = source.replace("out vec", "varying vec");
                source = source.replace("out float", "varying float");
            } else {
                source = source.replace("in vec", "varying vec");
                source = source.replace("in float", "varying float");
            }
        }
        
        return source;
    }
    
    /**
     * Renders all composite passes in sequence.
     */
    public void renderAll() {
        if (destroyed || passes.isEmpty()) {
            return;
        }
        
        // Disable blending and alpha test for fullscreen passes
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        
        FullScreenQuadRenderer.INSTANCE.begin();
        
        for (Pass pass : passes) {
            renderPass(pass);
        }
        
        FullScreenQuadRenderer.INSTANCE.end();
        
        // Reset viewport to main framebuffer
        Minecraft mc = Minecraft.getMinecraft();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
        
        // Cleanup
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        GL20.glUseProgram(0);
        
        // Unbind all textures
        for (int i = 0; i < 16; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }
    
    private void renderPass(Pass pass) {
        // Set viewport
        int scaledWidth = (int) (pass.viewWidth * pass.viewportScale);
        int scaledHeight = (int) (pass.viewHeight * pass.viewportScale);
        GL11.glViewport(0, 0, scaledWidth, scaledHeight);
        
        // Bind framebuffer
        pass.framebuffer.bind();
        
        // Set draw buffers
        if (pass.drawBuffers.length > 0) {
            int[] glBuffers = new int[pass.drawBuffers.length];
            for (int i = 0; i < pass.drawBuffers.length; i++) {
                glBuffers[i] = GL30.GL_COLOR_ATTACHMENT0 + pass.drawBuffers[i];
            }
            java.nio.IntBuffer buf = BufferUtils.createIntBuffer(glBuffers.length);
            buf.put(glBuffers);
            buf.flip();
            GL20.glDrawBuffers(buf);
        }
        
        // Use program
        pass.program.use();
        
        // Draw fullscreen quad
        FullScreenQuadRenderer.INSTANCE.renderQuad();
    }
    
    /**
     * Recalculates pass dimensions after a resize.
     */
    public void recalculateSizes() {
        for (Pass pass : passes) {
            int viewWidth = 0;
            int viewHeight = 0;
            for (int buffer : pass.drawBuffers) {
                RenderTarget target = renderTargets.get(buffer);
                if (target != null) {
                    viewWidth = target.getWidth();
                    viewHeight = target.getHeight();
                    break;
                }
            }
            
            if (viewWidth > 0 && viewHeight > 0) {
                pass.viewWidth = viewWidth;
                pass.viewHeight = viewHeight;
            }
            
            // Recreate framebuffer
            if (pass.framebuffer != null) {
                pass.framebuffer.destroy();
            }
            pass.framebuffer = renderTargets.createColorFramebuffer(bufferFlipper.snapshot(), pass.drawBuffers);
        }
    }
    
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        
        for (Pass pass : passes) {
            pass.destroy();
        }
        passes.clear();
    }
    
    public int getPassCount() {
        return passes.size();
    }
    
    /**
     * A single composite pass containing program, framebuffer, and rendering parameters.
     */
    private static class Pass {
        Program program;
        GlFramebuffer framebuffer;
        int[] drawBuffers;
        int viewWidth;
        int viewHeight;
        float viewportScale = 1.0f;
        
        void destroy() {
            if (program != null) {
                program.destroy();
                program = null;
            }
            if (framebuffer != null) {
                framebuffer.destroy();
                framebuffer = null;
            }
        }
    }
}
