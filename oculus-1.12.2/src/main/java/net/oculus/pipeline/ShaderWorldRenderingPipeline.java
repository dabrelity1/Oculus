package net.oculus.pipeline;

import java.nio.IntBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.Framebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.FramebufferManager;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import com.github.zsoltmolnarr.oculus.client.render.gl.program.InternalPrograms;
import com.github.zsoltmolnarr.oculus.client.render.gl.program.ProgramManager;
import net.oculus.gl.program.Program;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.oculus.Oculus;
import net.oculus.pipeline.context.ObjectContext;
import net.oculus.shader.ShaderLoader;
import net.oculus.shaderpack.CloudSetting;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.PackShadowDirectives;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ProgramSource;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.FrameUpdateNotifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Shader-driven world renderer that compiles and uses shader programs from shader packs.
 * This pipeline intercepts Minecraft's rendering and applies custom shader programs.
 */
public final class ShaderWorldRenderingPipeline implements WorldRenderingPipeline {
    private static final Logger LOGGER = LogManager.getLogger(ShaderWorldRenderingPipeline.class);

    private final ShaderPack pack;
    private final ProgramSet programSet;
    private final ShaderProperties shaderProperties;
    private final PackDirectives directives;
    private final PackRenderTargetDirectives renderTargetDirectives;
    private final PackShadowDirectives shadowDirectives;
    private final FramebufferManager framebufferManager;
    private final ProgramManager programManager = new ProgramManager();
    private final ShaderLoader shaderLoader;
    private Framebuffer gbufferFramebuffer;
    private final int[] gbufferDrawBuffers;
    private boolean framebuffersInitialized;
    private boolean gbufferBound;
    private boolean shadersCompiled;
    private com.github.zsoltmolnarr.oculus.client.render.gl.program.Program passthroughProgram;

    // Active shader program for current render phase
    private Program activeGbufferProgram;
    private String activeGbufferProgramName;

    private final FrameUpdateNotifier frameUpdateNotifier = new FrameUpdateNotifier();
    private final RenderTargetStateListener renderTargetStateListener = RenderTargetStateListener.NOP;
    private final SodiumTerrainPipeline sodiumTerrainPipeline = SodiumTerrainPipeline.NULL_PIPELINE;
    
    // Shadow rendering
    private net.oculus.pipeline.shadow.ShadowRenderer shadowRenderer;
    private net.oculus.pipeline.shadow.ShadowMap shadowMap;
    
    // Post-processing renderers
    private net.oculus.postprocess.CompositeRenderer compositeRenderer;
    private net.oculus.postprocess.FinalPassRenderer finalPassRenderer;
    private net.oculus.postprocess.BufferFlipper bufferFlipper;
    private net.oculus.rendertarget.RenderTargets renderTargets;

    private final CloudSetting cloudSetting;
    private final boolean renderUnderwaterOverlay;
    private final boolean renderVignette;
    private final boolean renderSun;
    private final boolean renderMoon;
    private final boolean writeRainAndSnowToDepthBuffer;
    private final boolean renderParticlesBeforeDeferred;
    private final boolean allowConcurrentCompute;
    private final boolean oldLighting;
    private final OptionalInt forcedShadowDistanceChunks;

    private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
    private WorldRenderingPhase overridePhase;
    private InputAvailability inputs = new InputAvailability(false, false, false);
    private SpecialCondition specialCondition;
    private boolean prepared;
    private boolean setupLogged;
    private int currentNormalTexture;
    private int currentSpecularTexture;

    public ShaderWorldRenderingPipeline(ShaderPack pack, ProgramSet programSet, ShaderProperties shaderProperties) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.programSet = Objects.requireNonNull(programSet, "programSet");
        this.shaderProperties = Objects.requireNonNull(shaderProperties, "shaderProperties");
        this.directives = Objects.requireNonNull(programSet.getPackDirectives(), "directives");
        this.renderTargetDirectives = directives.getRenderTargetDirectives();
        this.shadowDirectives = directives.getShadowDirectives();
    this.framebufferManager = new FramebufferManager(this.directives);
    this.gbufferDrawBuffers = resolveDrawBuffers(this.renderTargetDirectives);
    
        // Create shader loader and compile programs
        this.shaderLoader = new ShaderLoader(programSet);

        this.cloudSetting = directives.getCloudSetting();
        this.renderUnderwaterOverlay = directives.underwaterOverlay();
        this.renderVignette = directives.vignette();
        this.renderSun = directives.shouldRenderSun();
        this.renderMoon = directives.shouldRenderMoon();
        this.writeRainAndSnowToDepthBuffer = directives.rainDepth();
        this.renderParticlesBeforeDeferred = directives.areParticlesBeforeDeferred();
        this.allowConcurrentCompute = directives.getConcurrentCompute();
        this.oldLighting = directives.isOldLighting();
        this.forcedShadowDistanceChunks = resolveForcedShadowDistance(shadowDirectives);

        // Initialize shadow rendering if the shader pack uses shadows
        initializeShadowRenderer();

        LOGGER.info("Initialized shader pipeline for pack {}", pack.getName());
    }
    
    private void initializeShadowRenderer() {
        if (shadowDirectives == null || shadowDirectives.getResolution() <= 0) {
            this.shadowMap = null;
            this.shadowRenderer = null;
            return;
        }
        
        // Create shadow map
        this.shadowMap = new net.oculus.pipeline.shadow.ShadowMap(
            directives, shaderProperties, net.oculus.util.Config.get());
        
        if (!shadowMap.isEnabled()) {
            this.shadowRenderer = null;
            return;
        }
        
        // Get shadow program source
        ProgramSource shadowSource = programSet.getShadow().orElse(null);
        
        // Create shadow renderer
        this.shadowRenderer = new net.oculus.pipeline.shadow.ShadowRenderer(
            directives, shadowDirectives, shadowSource, shadowMap);
        
        LOGGER.info("Shadow rendering enabled: {}x{}", 
            shadowDirectives.getResolution(), shadowDirectives.getResolution());
    }

    private OptionalInt resolveForcedShadowDistance(PackShadowDirectives shadowDirectives) {
        if (shadowDirectives == null || !shadowDirectives.isDistanceRenderMulExplicit()) {
            return OptionalInt.empty();
        }

        float mul = shadowDirectives.getDistanceRenderMul();
        if (mul < 0.0F) {
            return OptionalInt.of(-1);
        }

        float distance = shadowDirectives.getDistance();
        int chunks = (int) Math.ceil((distance * mul) / 16.0F);
        return OptionalInt.of(chunks);
    }

    private static int[] resolveDrawBuffers(PackRenderTargetDirectives renderTargetDirectives) {
        int[] resolved = renderTargetDirectives.getRenderTargetSettings().keySet().stream()
            .sorted()
            .mapToInt(Integer::intValue)
            .toArray();

        return resolved.length == 0 ? new int[] {0} : resolved;
    }

    private void ensureSetup() {
        if (setupLogged) {
            return;
        }

        LOGGER.info("Preparing shader pipeline for {}", pack.getName());
        PackRenderTargetDirectives targets = renderTargetDirectives;
        LOGGER.debug("Render target directives: {} entries", targets.getRenderTargetSettings().size());
        setupLogged = true;
    }
    
    private void ensureShadersCompiled() {
        if (shadersCompiled) {
            return;
        }
        
        try {
            ObjectContext context = ObjectContext.forPipeline(programSet, directives);
            shaderLoader.initialize(context);
            shadersCompiled = true;
            LOGGER.info("Shader programs compiled for {}", pack.getName());
            
            // Log which programs were loaded
            Map<String, Program> programs = shaderLoader.getPrograms();
            LOGGER.info("Loaded {} shader programs: {}", programs.size(), programs.keySet());
        } catch (Exception ex) {
            LOGGER.error("Failed to compile shaders for pack {}", pack.getName(), ex);
            shadersCompiled = false;
        }
    }

    @Override
    public void beginWorldRendering(float partialTicks) {
        // Update captured rendering state
        CapturedRenderingState.INSTANCE.beginFrame(partialTicks);
        
        beginLevelRendering();
        bindGbufferFramebuffer();
        
        // Apply terrain shader if available
        bindTerrainProgram();
    }

    @Override
    public void beginLevelRendering() {
        ensureSetup();
        ensureShadersCompiled();
        prepared = true;
        frameUpdateNotifier.onNewFrame();
    }
    
    /**
     * Binds the appropriate shader program for terrain rendering.
     */
    private void bindTerrainProgram() {
        if (!shadersCompiled || shaderLoader == null) {
            return;
        }
        
        // Check if any programs were actually compiled
        Map<String, Program> programs = shaderLoader.getPrograms();
        if (programs == null || programs.isEmpty()) {
            return;
        }
        
        // Try to find the best terrain shader
        Program terrainProgram = shaderLoader.getProgram("gbuffers_terrain");
        if (terrainProgram == null) {
            terrainProgram = shaderLoader.getProgram("gbuffers_textured_lit");
        }
        if (terrainProgram == null) {
            terrainProgram = shaderLoader.getProgram("gbuffers_textured");
        }
        if (terrainProgram == null) {
            terrainProgram = shaderLoader.getProgram("gbuffers_basic");
        }
        
        if (terrainProgram != null) {
            activeGbufferProgram = terrainProgram;
            activeGbufferProgramName = terrainProgram.getName();
            try {
                terrainProgram.use();
                terrainProgram.bindUniforms();
                terrainProgram.bindSamplers();
            } catch (Exception ex) {
                LOGGER.warn("Failed to bind terrain program: {}", ex.getMessage());
                activeGbufferProgram = null;
                activeGbufferProgramName = null;
            }
        }
    }
    
    /**
     * Unbinds any active shader program.
     */
    private void unbindProgram() {
        if (activeGbufferProgram != null) {
            Program.unbind();
            activeGbufferProgram = null;
            activeGbufferProgramName = null;
        }
    }

    @Override
    public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity) {
        if (shadowRenderer != null) {
            shadowRenderer.renderShadows(renderGlobal, cameraEntity);
        }
    }

    @Override
    public void addDebugText(List<String> messages) {
        messages.add("Oculus shader pipeline active for " + pack.getName());
        messages.add("Phase: " + getPhase());
        if (activeGbufferProgramName != null) {
            messages.add("Active program: " + activeGbufferProgramName);
        }
        if (shadersCompiled) {
            messages.add("Programs loaded: " + shaderLoader.getPrograms().size());
        }
        if (shadowRenderer != null) {
            shadowRenderer.addDebugText(messages);
        } else {
            messages.add("[Oculus] Shadow Maps: not used by shader pack");
        }
    }

    @Override
    public OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
        return forcedShadowDistanceChunks;
    }

    @Override
    public WorldRenderingPhase getPhase() {
        return overridePhase != null ? overridePhase : phase;
    }

    @Override
    public void beginSodiumTerrainRendering() {
        // TODO: Integrate Sodium terrain once the renderer is available on 1.12.2
    }

    @Override
    public void endSodiumTerrainRendering() {
        // TODO: Integrate Sodium terrain once the renderer is available on 1.12.2
    }

    @Override
    public void setOverridePhase(WorldRenderingPhase phase) {
        this.overridePhase = phase;
    }

    @Override
    public void setPhase(WorldRenderingPhase phase) {
        this.phase = phase == null ? WorldRenderingPhase.NONE : phase;
    }

    @Override
    public void setInputs(InputAvailability availability) {
        this.inputs = availability == null ? new InputAvailability(false, false, false) : availability;
    }

    @Override
    public void setSpecialCondition(SpecialCondition special) {
        this.specialCondition = special;
    }

    @Override
    public void syncProgram() {
        LOGGER.debug("Shader pipeline program sync requested (stub)");
    }

    @Override
    public RenderTargetStateListener getRenderTargetStateListener() {
        return renderTargetStateListener;
    }

    @Override
    public int getCurrentNormalTexture() {
        return currentNormalTexture;
    }

    @Override
    public int getCurrentSpecularTexture() {
        return currentSpecularTexture;
    }

    @Override
    public void onBindTexture(int id) {
        // TODO: Track texture bindings once the pipeline drives GL state
    }

    @Override
    public void beginHand() {
        LOGGER.debug("Shader pipeline beginHand stub invoked");
    }

    @Override
    public void beginTranslucents() {
        LOGGER.debug("Shader pipeline beginTranslucents stub invoked");
    }

    @Override
    public void finalizeLevelRendering() {
        if (!prepared) {
            return;
        }

        // Unbind any active shader programs before composite pass
        unbindProgram();
        
        runCompositePass();
        prepared = false;
        gbufferBound = false;
        phase = WorldRenderingPhase.NONE;
        overridePhase = null;
    }

    @Override
    public void destroy() {
        // Destroy shader loader and compiled programs
        if (shaderLoader != null) {
            shaderLoader.destroy();
        }
        
        framebufferManager.destroy();
    programManager.destroyAll();
    passthroughProgram = null;
        gbufferFramebuffer = null;
        framebuffersInitialized = false;
        gbufferBound = false;
        shadersCompiled = false;
        prepared = false;
        setupLogged = false;
        currentNormalTexture = 0;
        currentSpecularTexture = 0;
        activeGbufferProgram = null;
        activeGbufferProgramName = null;
        LOGGER.info("Destroyed shader pipeline for {}", pack.getName());
    }

    @Override
    public SodiumTerrainPipeline getSodiumTerrainPipeline() {
        return sodiumTerrainPipeline;
    }

    @Override
    public FrameUpdateNotifier getFrameUpdateNotifier() {
        return frameUpdateNotifier;
    }

    @Override
    public boolean shouldDisableVanillaEntityShadows() {
        return true;
    }

    @Override
    public boolean shouldDisableDirectionalShading() {
        return oldLighting;
    }

    @Override
    public CloudSetting getCloudSetting() {
        return cloudSetting;
    }

    @Override
    public boolean shouldRenderUnderwaterOverlay() {
        return renderUnderwaterOverlay;
    }

    @Override
    public boolean shouldRenderVignette() {
        return renderVignette;
    }

    @Override
    public boolean shouldRenderSun() {
        return renderSun;
    }

    @Override
    public boolean shouldRenderMoon() {
        return renderMoon;
    }

    @Override
    public boolean shouldWriteRainAndSnowToDepthBuffer() {
        return writeRainAndSnowToDepthBuffer;
    }

    @Override
    public boolean shouldRenderParticlesBeforeDeferred() {
        return renderParticlesBeforeDeferred;
    }

    @Override
    public boolean allowConcurrentCompute() {
        return allowConcurrentCompute;
    }

    @Override
    public float getSunPathRotation() {
        return directives.getSunPathRotation();
    }

    @Override
    public FramebufferManager getFramebufferManager() {
        return framebufferManager;
    }

    private void bindGbufferFramebuffer() {
        // Check if shaders are compiled - only bind if we have working shaders
        if (!shadersCompiled || shaderLoader == null || shaderLoader.getPrograms().isEmpty()) {
            // No shaders compiled - render to default framebuffer (vanilla behavior)
            gbufferBound = false;
            return;
        }
        
        ensureFramebufferManagerInitialized();
        ensureFramebufferDimensionsUpToDate();
        
        Framebuffer framebuffer = getOrCreateGbufferFramebuffer();
        framebuffer.bind();
        
        // Set up draw buffers
        if (gbufferDrawBuffers.length > 0) {
            int[] glBuffers = convertToGLDrawBuffers(gbufferDrawBuffers);
            IntBuffer drawBufferBuffer = BufferUtils.createIntBuffer(glBuffers.length);
            drawBufferBuffer.put(glBuffers);
            drawBufferBuffer.flip();
            GL20.glDrawBuffers(drawBufferBuffer);
        }
        
        // Clear the g-buffer
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        
        gbufferBound = true;
        LOGGER.debug("Bound g-buffer framebuffer for {}", pack.getName());
    }
    
    private int[] convertToGLDrawBuffers(int[] bufferIndices) {
        int[] glBuffers = new int[bufferIndices.length];
        for (int i = 0; i < bufferIndices.length; i++) {
            glBuffers[i] = GL30.GL_COLOR_ATTACHMENT0 + bufferIndices[i];
        }
        return glBuffers;
    }

    private void ensureFramebufferManagerInitialized() {
        if (framebuffersInitialized) {
            return;
        }
        framebufferManager.initialize();
        framebuffersInitialized = true;
    }

    private void ensureFramebufferDimensionsUpToDate() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        int displayWidth = Math.max(1, minecraft.displayWidth);
        int displayHeight = Math.max(1, minecraft.displayHeight);
        framebufferManager.resizeIfNeeded(displayWidth, displayHeight);
    }

    private Framebuffer getOrCreateGbufferFramebuffer() {
        if (gbufferFramebuffer == null) {
            gbufferFramebuffer = framebufferManager.createFramebuffer(false, gbufferDrawBuffers);
        }
        return gbufferFramebuffer;
    }

    private void runCompositePass() {
        if (!gbufferBound) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            gbufferBound = false;
            return;
        }

        int viewportWidth = Math.max(1, minecraft.displayWidth);
        int viewportHeight = Math.max(1, minecraft.displayHeight);

        // Get the color texture from the g-buffer
        RenderTarget colorTarget = null;
        int colorTexture = 0;
        
        if (gbufferDrawBuffers.length > 0) {
            colorTarget = framebufferManager.getRenderTarget(gbufferDrawBuffers[0]);
            if (colorTarget != null) {
                colorTexture = colorTarget.getMainTexture();
            }
        }

        // TODO: Run composite shader passes here if the shader pack defines them
        // For now, we just blit the g-buffer color to the screen

        // Bind to screen framebuffer
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);

        // Disable depth testing for fullscreen quad
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableBlend();

        // Bind the g-buffer color texture
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);

        // Use passthrough program to blit texture to screen
        com.github.zsoltmolnarr.oculus.client.render.gl.program.Program passthrough = getPassthroughProgram();
        if (passthrough != null) {
            passthrough.use();
            passthrough.uniform("u_ColorTexture").setInt(0);
        } else {
            // Fallback: use fixed function pipeline
            GL20.glUseProgram(0);
            GlStateManager.enableTexture2D();
        }

        // Draw fullscreen quad
        drawFullscreenQuad();

        // Cleanup
        if (passthrough != null) {
            com.github.zsoltmolnarr.oculus.client.render.gl.program.Program.unbind();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        // Restore state
        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);
        GlStateManager.enableBlend();

        gbufferBound = false;
    }

    private com.github.zsoltmolnarr.oculus.client.render.gl.program.Program getPassthroughProgram() {
        if (passthroughProgram == null) {
            passthroughProgram = programManager.getOrCreate(InternalPrograms.PASSTHROUGH);
        }
        return passthroughProgram;
    }

    private static void drawFullscreenQuad() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder builder = tessellator.getBuffer();
        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
        builder.pos(-1.0D, -1.0D, 0.0D).tex(0.0D, 0.0D).endVertex();
        builder.pos(1.0D, -1.0D, 0.0D).tex(1.0D, 0.0D).endVertex();
        builder.pos(1.0D, 1.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
        builder.pos(-1.0D, 1.0D, 0.0D).tex(0.0D, 1.0D).endVertex();
        tessellator.draw();
    }
}
