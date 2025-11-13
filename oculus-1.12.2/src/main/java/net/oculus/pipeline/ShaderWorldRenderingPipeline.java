package net.oculus.pipeline;

import java.util.Map;
import java.util.Objects;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mojang.blaze3d.systems.RenderSystem;

import net.oculus.pipeline.context.ObjectContext;
import net.oculus.pipeline.framebuffer.FramebufferManager;
import net.oculus.pipeline.gterrain.GlobalTerrainFramebuffers;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.shader.ShaderLoader;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.ProgramSet;
import net.oculus.shaderpack.ShaderPack;
import net.oculus.shaderpack.ShaderProperties;
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
    private final ShaderLoader shaderLoader;

    private FramebufferManager framebufferManager;
    private GlobalTerrainFramebuffers terrainFramebuffers;
    private ShadowMap shadowMap;

    private boolean setupComplete;
    private boolean prepared;

    public ShaderWorldRenderingPipeline(ShaderPack pack, ProgramSet programSet, ShaderProperties shaderProperties) {
        this.pack = Objects.requireNonNull(pack, "pack");
        this.programSet = Objects.requireNonNull(programSet, "programSet");
        this.shaderProperties = Objects.requireNonNull(shaderProperties, "shaderProperties");
        this.directives = Objects.requireNonNull(programSet.getPackDirectives(), "directives");
        this.config = Config.get();
        this.objectContext = new ObjectContext(pack, shaderProperties);
        this.shaderLoader = new ShaderLoader(programSet);
        RenderSystem.initializeShaderPipeline();
    }

    public void setup() {
        if (setupComplete) {
            return;
        }

        LOGGER.info("Setting up shader pipeline for pack {}", pack.getName());

        shaderLoader.initialize(objectContext);

        framebufferManager = new FramebufferManager(directives, shaderProperties, config);
        framebufferManager.prepareGbuffers();

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
        if (terrainFramebuffers != null) {
            terrainFramebuffers.destroy();
            terrainFramebuffers = null;
        }

        if (framebufferManager != null) {
            framebufferManager.destroy();
            framebufferManager = null;
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
}
