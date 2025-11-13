package net.oculus.pipeline.framebuffer;

import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.util.Config;

/**
 * Coordinates the lifecycle of core framebuffers used by the shader pipeline.
 * The 1.12.2 backport keeps the bookkeeping surface so higher level systems can
 * be translated while postponing any real OpenGL interaction.
 */
public final class FramebufferManager {
    private final PackDirectives directives;
    private final ShaderProperties properties;
    private final Config config;

    private ShadowMap shadowMap;
    private int gbufferCount;

    public FramebufferManager(PackDirectives directives, ShaderProperties properties, Config config) {
        this.directives = directives;
        this.properties = properties;
        this.config = config;
    }

    public void prepareGbuffers() {
        PackRenderTargetDirectives renderTargets = directives.getRenderTargetDirectives();
        this.gbufferCount = Math.max(1, renderTargets.getRenderTargetSettings().size());
    }

    public int getGbufferCount() {
        return gbufferCount;
    }

    public void attachShadowMap(ShadowMap shadowMap) {
        this.shadowMap = shadowMap;
    }

    public ShadowMap getShadowMap() {
        return shadowMap;
    }

    public boolean hasShadowMap() {
        return shadowMap != null && shadowMap.isEnabled();
    }

    public void destroy() {
        if (shadowMap != null) {
            shadowMap.destroy();
            shadowMap = null;
        }
    }
}
