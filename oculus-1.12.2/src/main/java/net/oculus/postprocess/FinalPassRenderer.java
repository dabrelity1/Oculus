package net.oculus.postprocess;

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
 * Renders the final composite pass that outputs to the screen.
 * This takes the processed G-buffer contents and writes them to framebuffer 0.
 * 
 * The final pass is special because:
 * - It always renders to the default framebuffer (screen)
 * - It's the last shader pass before the frame is displayed
 * - It typically applies tone mapping, gamma correction, and other final effects
 * 
 * Port from Iris 1.16.5 FinalPassRenderer.
 */
public class FinalPassRenderer {
    private static final Logger LOGGER = LogManager.getLogger(FinalPassRenderer.class);
    
    private final RenderTargets renderTargets;
    private final Program program;
    private final int[] drawBuffers;
    private final IntSupplier noiseTexture;
    private final FrameUpdateNotifier updateNotifier;
    private final CenterDepthSampler centerDepthSampler;
    private final BufferFlipper bufferFlipper;
    private final Set<Integer> flippedBuffers;
    private final float viewportScale;
    private boolean destroyed = false;
    
    /**
     * Creates a FinalPassRenderer from a program source.
     * 
     * @param packDirectives The shader pack directives
     * @param source The final pass program source (final.vsh/fsh)
     * @param renderTargets The render targets manager
     * @param noiseTexture Supplier for noise texture ID
     * @param updateNotifier Frame update notifier for uniforms
     * @param centerDepthSampler Center depth sampler
     * @param bufferFlipper Buffer flip state tracker
     * @param flippedBuffers Set of currently flipped buffer indices
     */
    public FinalPassRenderer(PackDirectives packDirectives,
                            ProgramSource source,
                            RenderTargets renderTargets,
                            IntSupplier noiseTexture,
                            FrameUpdateNotifier updateNotifier,
                            CenterDepthSampler centerDepthSampler,
                            BufferFlipper bufferFlipper,
                            Set<Integer> flippedBuffers) {
        this.renderTargets = renderTargets;
        this.noiseTexture = noiseTexture;
        this.updateNotifier = updateNotifier;
        this.centerDepthSampler = centerDepthSampler;
        this.bufferFlipper = bufferFlipper;
        this.flippedBuffers = flippedBuffers;
        
        if (source == null || !source.isValid()) {
            LOGGER.info("No final pass source provided, using passthrough");
            this.program = null;
            this.drawBuffers = new int[0];
            this.viewportScale = 1.0f;
            return;
        }
        
        ProgramDirectives directives = source.getDirectives();
        this.viewportScale = directives.getViewportScale();
        this.drawBuffers = directives.getDrawBuffers();
        
        String vertexSource = source.getVertexSource().orElse(null);
        String fragmentSource = source.getFragmentSource().orElse(null);
        String geometrySource = source.getGeometrySource().orElse(null);
        
        if (vertexSource == null || fragmentSource == null) {
            LOGGER.warn("Final pass missing vertex or fragment source, using passthrough");
            this.program = null;
            return;
        }
        
        // Patch shaders for compatibility
        vertexSource = patchFinalShader(vertexSource, true);
        fragmentSource = patchFinalShader(fragmentSource, false);
        if (geometrySource != null) {
            geometrySource = patchFinalShader(geometrySource, false);
        }
        
        Program compiledProgram = null;
        try {
            ProgramBuilder builder = ProgramBuilder.begin(source.getName(), vertexSource, geometrySource, fragmentSource, null);
            compiledProgram = builder.build();
            LOGGER.info("Final pass shader compiled successfully: {}", source.getName());
        } catch (RuntimeException e) {
            LOGGER.error("Final pass shader compilation failed for {}: {}", source.getName(), e.getMessage());
            LOGGER.debug("Compilation error:", e);
        }
        
        this.program = compiledProgram;
    }
    
    /**
     * Patches a final shader for 1.12.2 compatibility.
     */
    private String patchFinalShader(String source, boolean isVertex) {
        if (source == null) return null;
        
        // Ensure minimum version
        if (!source.contains("#version")) {
            source = "#version 120\n" + source;
        }
        
        // Patch attribute/varying for old GLSL versions
        if (source.contains("#version 1")) { // version 1xx
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
     * Renders the final pass to the screen.
     * 
     * This method:
     * 1. Binds the default framebuffer (screen)
     * 2. Sets viewport to screen size
     * 3. Binds render target textures as samplers
     * 4. Runs the final pass shader
     * 5. Draws fullscreen quad
     */
    public void render() {
        if (destroyed) {
            return;
        }
        
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        
        int viewportWidth = Math.max(1, minecraft.displayWidth);
        int viewportHeight = Math.max(1, minecraft.displayHeight);
        
        // Apply viewport scale
        int scaledWidth = (int) (viewportWidth * viewportScale);
        int scaledHeight = (int) (viewportHeight * viewportScale);
        
        // Bind to the default framebuffer (screen)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, scaledWidth, scaledHeight);
        
        // Disable depth testing for fullscreen quad
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        
        // Bind render target textures
        bindRenderTargetTextures();
        
        if (program != null) {
            // Use the final pass shader
            program.use();
            
            // Draw fullscreen quad
            FullScreenQuadRenderer.INSTANCE.begin();
            FullScreenQuadRenderer.INSTANCE.renderQuad();
            FullScreenQuadRenderer.INSTANCE.end();
            
            // Unbind program
            Program.unbind();
        } else {
            // Fallback: blit colortex0 directly using fixed function
            renderPassthrough();
        }
        
        // Cleanup
        unbindRenderTargetTextures();
        
        ProgramUniforms.clearActiveUniforms();
        ProgramSamplers.clearActiveSamplers();
        
        // Restore state
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableBlend();
        GlStateManager.enableAlpha();
    }
    
    /**
     * Binds render target textures to texture units for sampling.
     */
    private void bindRenderTargetTextures() {
        // Bind colortex0-7 to texture units 0-7
        for (int i = 0; i < 8; i++) {
            RenderTarget target = renderTargets.get(i);
            if (target != null) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
                int texture = flippedBuffers.contains(i) ? target.getAltTexture() : target.getMainTexture();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            }
        }
        
        // Bind depthtex0 to texture unit 8
        int depthTexture = renderTargets.getCurrentDepthTexture();
        if (depthTexture > 0) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + 8);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
        }
        
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }
    
    /**
     * Unbinds all render target textures.
     */
    private void unbindRenderTargetTextures() {
        for (int i = 0; i < 16; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        }
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
    }
    
    /**
     * Passthrough rendering using fixed-function pipeline.
     * Simply blits colortex0 to the screen.
     */
    private void renderPassthrough() {
        GL20.glUseProgram(0);
        
        // Bind colortex0
        RenderTarget colorTarget = renderTargets.get(0);
        if (colorTarget == null) {
            return;
        }
        
        int texture = flippedBuffers.contains(0) ? colorTarget.getAltTexture() : colorTarget.getMainTexture();
        
        GlStateManager.enableTexture2D();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        
        // Set up orthographic projection for screen quad
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0, 1, 0, 1, -1, 1);
        
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        
        // Draw textured quad
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1);
        GL11.glEnd();
        
        // Restore matrices
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }
    
    /**
     * Returns true if a custom final pass shader is being used.
     */
    public boolean hasCustomShader() {
        return program != null;
    }
    
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        
        if (program != null) {
            program.destroy();
        }
    }
}
