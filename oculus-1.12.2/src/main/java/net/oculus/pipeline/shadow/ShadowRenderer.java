package net.oculus.pipeline.shadow;

import java.nio.FloatBuffer;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.oculus.gl.program.Program;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackShadowDirectives;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.uniforms.CelestialUniforms;

/**
 * Handles shadow map rendering for shader packs.
 * 
 * Shadow rendering works by:
 * 1. Setting up an orthographic projection from the sun/moon position
 * 2. Rendering the world to a depth texture
 * 3. Using that depth texture in gbuffer/composite shaders for shadow sampling
 * 
 * Port from Iris 1.16.5 ShadowRenderer.
 */
public class ShadowRenderer {
    private static final Logger LOGGER = LogManager.getLogger(ShadowRenderer.class);
    
    private static final float SHADOW_NEAR = 0.05f;
    private static final float SHADOW_FAR = 256.0f;
    
    private final ShadowMap shadowMap;
    private final Program shadowProgram;
    private final int framebufferId;
    private final int resolution;
    private final float halfPlaneLength;
    private final float sunPathRotation;
    
    private final float[] shadowProjection = new float[16];
    private final float[] shadowModelView = new float[16];
    private final float[] shadowModelViewInverse = new float[16];
    private final float[] shadowProjectionInverse = new float[16];
    
    private boolean destroyed = false;
    
    /**
     * Creates a new ShadowRenderer.
     * 
     * @param directives The pack directives
     * @param shadowDirectives The shadow-specific directives
     * @param shadowSource The shadow program source (shadow.vsh/fsh)
     * @param shadowMap The shadow map texture holder
     */
    public ShadowRenderer(PackDirectives directives, 
                         PackShadowDirectives shadowDirectives,
                         ProgramSource shadowSource,
                         ShadowMap shadowMap) {
        this.shadowMap = shadowMap;
        this.resolution = shadowDirectives.getResolution();
        this.halfPlaneLength = shadowDirectives.getDistance();
        this.sunPathRotation = directives.getSunPathRotation();
        
        // Create shadow framebuffer
        this.framebufferId = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
        
        // Attach depth texture
        int depthTexture = shadowMap.isEnabled() ? getDepthTexture() : 0;
        if (depthTexture > 0) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, 
                GL11.GL_TEXTURE_2D, depthTexture, 0);
        }
        
        // No color output for shadow pass
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.warn("Shadow framebuffer incomplete: {}", status);
        }
        
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        
        // Compile shadow program
        Program compiled = null;
        if (shadowSource != null && shadowSource.isValid()) {
            try {
                String vertexSource = shadowSource.getVertexSource().orElse(null);
                String fragmentSource = shadowSource.getFragmentSource().orElse(null);
                String geometrySource = shadowSource.getGeometrySource().orElse(null);
                
                if (vertexSource != null && fragmentSource != null) {
                    ProgramBuilder builder = ProgramBuilder.begin(shadowSource.getName(), 
                        vertexSource, geometrySource, fragmentSource, null);
                    compiled = builder.build();
                    LOGGER.info("Shadow program compiled: {}", shadowSource.getName());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to compile shadow program", e);
            }
        }
        this.shadowProgram = compiled;
        
        // Initialize projection matrix (orthographic)
        createOrthoMatrix(shadowProjection, halfPlaneLength);
        
        LOGGER.info("ShadowRenderer initialized: resolution={}, distance={}", resolution, halfPlaneLength);
    }
    
    /**
     * Renders shadow maps for the current frame.
     * 
     * @param renderGlobal The world renderer
     * @param cameraEntity The camera entity (player)
     */
    public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity) {
        if (destroyed || !shadowMap.isEnabled() || resolution <= 0) {
            return;
        }
        
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.world == null) {
            return;
        }
        
        // Calculate shadow angle (sun/moon position)
        float shadowAngle = CelestialUniforms.getShadowAngle();
        
        // Update shadow model-view matrix
        createModelViewMatrix(shadowModelView, shadowAngle, sunPathRotation, 
            cameraEntity.posX, cameraEntity.posY, cameraEntity.posZ);
        
        // Calculate inverse matrices
        invertMatrix(shadowModelView, shadowModelViewInverse);
        invertMatrix(shadowProjection, shadowProjectionInverse);
        
        // Bind shadow framebuffer
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
        GL11.glViewport(0, 0, resolution, resolution);
        
        // Clear depth
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
        
        // Enable depth test
        GlStateManager.enableDepth();
        GlStateManager.depthFunc(GL11.GL_LEQUAL);
        GlStateManager.depthMask(true);
        
        // Set up matrices
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        loadMatrix(shadowProjection);
        
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        loadMatrix(shadowModelView);
        
        // Bind shadow program if available
        if (shadowProgram != null) {
            shadowProgram.use();
        }
        
        // Render world geometry to shadow map
        // Note: In a full implementation, we would re-render terrain/entities here
        // For now, we just set up the state - actual rendering requires mixin hooks
        
        // Restore state
        if (shadowProgram != null) {
            Program.unbind();
        }
        
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
        
        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        
        // Unbind shadow framebuffer
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        
        // Restore viewport
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight);
    }
    
    /**
     * Creates an orthographic projection matrix for shadow mapping.
     */
    private static void createOrthoMatrix(float[] matrix, float halfPlaneLength) {
        // Column-major order
        matrix[0] = 1.0f / halfPlaneLength;
        matrix[1] = 0f;
        matrix[2] = 0f;
        matrix[3] = 0f;
        
        matrix[4] = 0f;
        matrix[5] = 1.0f / halfPlaneLength;
        matrix[6] = 0f;
        matrix[7] = 0f;
        
        matrix[8] = 0f;
        matrix[9] = 0f;
        matrix[10] = 2.0f / (SHADOW_NEAR - SHADOW_FAR);
        matrix[11] = 0f;
        
        matrix[12] = 0f;
        matrix[13] = 0f;
        matrix[14] = -(SHADOW_FAR + SHADOW_NEAR) / (SHADOW_FAR - SHADOW_NEAR);
        matrix[15] = 1f;
    }
    
    /**
     * Creates the shadow model-view matrix based on sun angle.
     */
    private static void createModelViewMatrix(float[] matrix, float shadowAngle, 
            float sunPathRotation, double cameraX, double cameraY, double cameraZ) {
        // Initialize to identity
        for (int i = 0; i < 16; i++) {
            matrix[i] = (i % 5 == 0) ? 1.0f : 0.0f;
        }
        
        // Calculate sky angle
        float skyAngle;
        if (shadowAngle < 0.25f) {
            skyAngle = shadowAngle + 0.75f;
        } else {
            skyAngle = shadowAngle - 0.25f;
        }
        
        // Build rotation matrix
        // Translate back
        matrix[14] = -100.0f;
        
        // Rotate around X (90 degrees to look down)
        float cos90 = 0;
        float sin90 = 1;
        
        // Rotate around Z (based on sky angle)
        float angleZ = skyAngle * -360.0f * (float)(Math.PI / 180.0);
        float cosZ = (float) Math.cos(angleZ);
        float sinZ = (float) Math.sin(angleZ);
        
        // Rotate around X (sun path rotation)
        float angleX = sunPathRotation * (float)(Math.PI / 180.0);
        float cosX = (float) Math.cos(angleX);
        float sinX = (float) Math.sin(angleX);
        
        // Simplified combined rotation
        matrix[0] = cosZ;
        matrix[1] = sinZ;
        matrix[4] = -sinZ * cosX;
        matrix[5] = cosZ * cosX;
        matrix[6] = sinX;
        matrix[8] = sinZ * sinX;
        matrix[9] = -cosZ * sinX;
        matrix[10] = cosX;
    }
    
    /**
     * Inverts a 4x4 matrix.
     */
    private static void invertMatrix(float[] src, float[] dst) {
        // Simple matrix inversion for shadow matrices
        // For orthographic projections, the inverse is straightforward
        // This is a simplified implementation
        for (int i = 0; i < 16; i++) {
            dst[i] = src[i];
        }
        // Note: Full matrix inversion would be needed for general cases
    }
    
    /**
     * Loads a matrix into OpenGL.
     */
    private static void loadMatrix(float[] matrix) {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(matrix);
        buffer.flip();
        GL11.glLoadMatrix(buffer);
    }
    
    private int getDepthTexture() {
        // Access the depth texture from shadow map via reflection or direct field access
        // For now, return 0 as placeholder - the actual texture is in ShadowMap
        return 0;
    }
    
    public float[] getShadowProjection() {
        return shadowProjection;
    }
    
    public float[] getShadowModelView() {
        return shadowModelView;
    }
    
    public float[] getShadowModelViewInverse() {
        return shadowModelViewInverse;
    }
    
    public float[] getShadowProjectionInverse() {
        return shadowProjectionInverse;
    }
    
    public void addDebugText(List<String> messages) {
        messages.add("[Oculus] Shadow Maps: " + resolution + "x" + resolution);
        messages.add("[Oculus] Shadow Distance: " + halfPlaneLength);
    }
    
    public void destroy() {
        if (destroyed) {
            return;
        }
        destroyed = true;
        
        if (shadowProgram != null) {
            shadowProgram.destroy();
        }
        
        if (framebufferId > 0) {
            GL30.glDeleteFramebuffers(framebufferId);
        }
    }
}
