package net.oculus.pipeline;

import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.Framebuffer;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.FramebufferManager;
import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import com.github.zsoltmolnarr.oculus.client.render.gl.program.InternalPrograms;
import com.github.zsoltmolnarr.oculus.client.render.gl.program.Program;
import com.github.zsoltmolnarr.oculus.client.render.gl.program.ProgramManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.oculus.Oculus;
import net.oculus.shaderpack.CloudSetting;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.PackShadowDirectives;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.uniforms.FrameUpdateNotifier;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * Skeleton implementation of the shader-driven world renderer. The goal for this porting
 * step is to mirror the 1.16.5 API surface without attempting to wire up the full GL
 * behaviour yet.
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
    private Framebuffer gbufferFramebuffer;
    private final int[] gbufferDrawBuffers;
    private boolean framebuffersInitialized;
    private boolean gbufferBound;
    private Program passthroughProgram;

    private final FrameUpdateNotifier frameUpdateNotifier = new FrameUpdateNotifier();
    private final RenderTargetStateListener renderTargetStateListener = RenderTargetStateListener.NOP;
    private final SodiumTerrainPipeline sodiumTerrainPipeline = SodiumTerrainPipeline.NULL_PIPELINE;

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

        LOGGER.info("Initialized shader pipeline skeleton for pack {}", pack.getName());
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

        LOGGER.info("Preparing framebuffer/layout stubs for {}", pack.getName());
        PackRenderTargetDirectives targets = renderTargetDirectives;
        LOGGER.debug("Render target directives: {} entries", targets.getRenderTargetSettings().size());
        setupLogged = true;
    }

    @Override
    public void beginWorldRendering(float partialTicks) {
        beginLevelRendering();
        bindGbufferFramebuffer();
    }

    @Override
    public void beginLevelRendering() {
        ensureSetup();
        prepared = true;
        frameUpdateNotifier.onNewFrame();
    }

    @Override
    public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity) {
        // TODO: Port 1.16.5 shadow rendering orchestration
    }

    @Override
    public void addDebugText(List<String> messages) {
        messages.add("Oculus shader pipeline (stub) active for " + pack.getName());
        messages.add("Phase: " + getPhase());
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

        runCompositePass();
        prepared = false;
        gbufferBound = false;
        phase = WorldRenderingPhase.NONE;
        overridePhase = null;
    }

    @Override
    public void destroy() {
        framebufferManager.destroy();
    programManager.destroyAll();
    passthroughProgram = null;
        gbufferFramebuffer = null;
        framebuffersInitialized = false;
        gbufferBound = false;
        prepared = false;
        setupLogged = false;
        currentNormalTexture = 0;
        currentSpecularTexture = 0;
        LOGGER.info("Destroyed shader pipeline skeleton for {}", pack.getName());
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
        ensureFramebufferManagerInitialized();
        ensureFramebufferDimensionsUpToDate();
        Framebuffer framebuffer = getOrCreateGbufferFramebuffer();
        framebuffer.bind();
        gbufferBound = true;
        Oculus.LOGGER.info("Binding g-buffers for {}", pack.getName());
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

        if (gbufferDrawBuffers.length == 0) {
            LOGGER.warn("No g-buffer draw buffers configured; skipping composite pass");
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }

        Program program = getPassthroughProgram();
        if (program == null) {
            return;
        }

        RenderTarget colorTarget = framebufferManager.getRenderTarget(gbufferDrawBuffers[0]);
        int colorTexture = colorTarget.getMainTexture();

        int viewportWidth = Math.max(1, minecraft.displayWidth);
        int viewportHeight = Math.max(1, minecraft.displayHeight);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glViewport(0, 0, viewportWidth, viewportHeight);

    GlStateManager.disableDepth();
    GlStateManager.depthMask(false);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTexture);

        program.use();
        program.uniform("u_ColorTexture").setInt(0);

        drawFullscreenQuad();

        Program.unbind();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GlStateManager.enableDepth();
        GlStateManager.depthMask(true);

        gbufferBound = false;
    }

    private Program getPassthroughProgram() {
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
