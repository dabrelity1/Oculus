package net.oculus.pipeline;

import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.blaze3d.systems.RenderSystem;

import net.oculus.gl.program.SamplerOverrideProvider;
import net.oculus.gl.state.GameDataSuppliers;
import net.minecraft.client.Minecraft;
import net.oculus.layer.GbufferPrograms;
import net.oculus.pipeline.compute.ComputeDispatchManager;
import net.oculus.pipeline.context.ObjectContext;
import net.oculus.pipeline.sampler.SamplerOverrideConfigurator;
import net.oculus.pipeline.texture.CustomTextureManager;
import net.oculus.pipeline.framebuffer.FramebufferManager;
import net.oculus.pipeline.gterrain.GlobalTerrainFramebuffers;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.shader.ShaderLoader;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.CelestialUniforms;
import net.oculus.uniforms.CompatibilityUniforms;
import net.oculus.uniforms.GameplayUniforms;
import net.oculus.uniforms.ShadowUniforms;
import net.oculus.uniforms.SystemTimeUniforms;
import net.oculus.util.Config;

/**
 * High-level orchestration for the shader-driven world renderer. This mirrors
 * the structure of the 1.16.5 implementation by wiring together the shader
 * loader, framebuffer manager, and terrain buffers while deferring low-level GL
 * work to future tasks.
 */
public final class ShaderWorldRenderingPipeline implements WorldRenderingPipeline {
    private static final Logger LOGGER = LogManager.getLogger(ShaderWorldRenderingPipeline.class);

    private final ShaderPack pack;
    private final ProgramSet programSet;
    private final ShaderProperties shaderProperties;
    private final PackDirectives directives;
    private final Config config;
    private final ObjectContext objectContext;
    private final SamplerOverrideProvider samplerOverrideProvider;
    private final ShaderLoader shaderLoader;
    private final CustomTextureManager customTextureManager;

    private FramebufferManager framebufferManager;
    private GlobalTerrainFramebuffers terrainFramebuffers;
    private ShadowMap shadowMap;
    private ComputeDispatchManager computeDispatchManager;

    private boolean setupComplete;
    private boolean prepared;
    private WorldRenderingPhase phase = WorldRenderingPhase.NONE;
    private WorldRenderingPhase overridePhase;

    public ShaderWorldRenderingPipeline(ShaderPack pack, ProgramSet programSet, ShaderProperties shaderProperties) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.programSet = Objects.requireNonNull(programSet, "programSet");
        this.shaderProperties = Objects.requireNonNull(shaderProperties, "shaderProperties");
        this.directives = Objects.requireNonNull(programSet.getPackDirectives(), "directives");
        this.config = Config.get();
        this.objectContext = new ObjectContext(pack, shaderProperties);
        this.customTextureManager = CustomTextureManager.fromShaderPack(pack);
        SamplerOverrideConfigurator samplerConfigurator = SamplerOverrideConfigurator.create(directives);
        this.samplerOverrideProvider = samplerConfigurator.buildProvider();
        this.shaderLoader = new ShaderLoader(programSet, samplerOverrideProvider, customTextureManager);
        CelestialUniforms.configure(this.directives);
        ShadowUniforms.configure(this.directives);
        GbufferPrograms.init();
        RenderSystem.initializeShaderPipeline();
    }

    public void setup() {
        if (setupComplete) {
            return;
        }

        LOGGER.info("Setting up shader pipeline for pack {}", pack.getName());

        customTextureManager.initialize();
        shaderLoader.initialize(objectContext);
        computeDispatchManager = new ComputeDispatchManager(programSet, samplerOverrideProvider);

        framebufferManager = new FramebufferManager(directives, shaderProperties, config);
        framebufferManager.prepareGbuffers();
        customTextureManager.applyGlobalOverrides();

        shadowMap = new ShadowMap(directives, shaderProperties, config);
        framebufferManager.attachShadowMap(shadowMap);

        terrainFramebuffers = new GlobalTerrainFramebuffers(framebufferManager, directives);
        if (config.terrainFramebuffersEnabled(shaderProperties)) {
            terrainFramebuffers.initialize();
        }

        PackRenderTargetDirectives renderTargets = directives.getRenderTargetDirectives();
        Map<Integer, ?> settings = renderTargets.getRenderTargetSettings();
        LOGGER.debug("Configured {} gbuffer targets for pack {}", settings.size(), pack.getName());

        if (framebufferManager.hasShadowMap()) {
            LOGGER.debug("Shadow map enabled at {}x{} resolution", shadowMap.getResolution(), shadowMap.getResolution());
        } else {
            LOGGER.debug("Shadow map disabled by pack directives");
        }

        setupComplete = true;
    }

    @Override
    public void beginWorldRendering(float partialTicks) {
        setup();
        GameDataSuppliers.setPartialTicks(partialTicks);
        SystemTimeUniforms.COUNTER.beginFrame();
        SystemTimeUniforms.TIMER.beginFrame(System.nanoTime());
        CapturedRenderingState.INSTANCE.beginFrame(partialTicks);
        CompatibilityUniforms.onFrameStart();
    GameplayUniforms.onFrameStart();
        prepared = true;
        RenderSystem.pushMatrix();
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        dispatchComputeStages();
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
    public void setPhase(WorldRenderingPhase phase) {
        this.phase = phase == null ? WorldRenderingPhase.NONE : phase;
        GbufferPrograms.runPhaseChangeNotifier();
    }

    @Override
    public void setOverridePhase(WorldRenderingPhase phase) {
        this.overridePhase = phase;
        GbufferPrograms.runPhaseChangeNotifier();
    }

    @Override
    public WorldRenderingPhase getPhase() {
        return overridePhase != null ? overridePhase : phase;
    }

    @Override
    public void destroy() {
        if (terrainFramebuffers != null) {
            terrainFramebuffers.destroy();
            terrainFramebuffers = null;
        }

        if (framebufferManager != null) {
            framebufferManager.destroy();
            framebufferManager = null;
        }

        if (computeDispatchManager != null) {
            computeDispatchManager.destroy();
            computeDispatchManager = null;
        }

        if (customTextureManager != null) {
            customTextureManager.destroy();
        }

        shaderLoader.destroy();
        RenderSystem.releaseShaderPipeline();
        setupComplete = false;
    }

    public ShaderPack getPack() {
        return pack;
    }

    public ProgramSet getProgramSet() {
        return programSet;
    }

    public PackDirectives getPackDirectives() {
        return directives;
    }

    private void dispatchComputeStages() {
        if (computeDispatchManager == null || !computeDispatchManager.hasComputes()) {
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        int width = minecraft != null ? minecraft.displayWidth : 0;
        int height = minecraft != null ? minecraft.displayHeight : 0;
        int shadowResolution = shadowMap != null && shadowMap.isEnabled() ? shadowMap.getResolution() : 0;

        computeDispatchManager.dispatchFrame(width, height, shadowResolution);
    }
}
