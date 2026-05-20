package net.oculus.util;

import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.PackShadowDirectives;
import net.oculus.shaderpack.ShaderProperties;

/**
 * Configuration facade used by the shader pipeline setup code.
 */
public final class Config {
    private static final Config INSTANCE = new Config();

    private Config() {
    }

    public static Config get() {
        return INSTANCE;
    }

    public boolean shadowsEnabled(PackDirectives directives) {
        if (directives == null) {
            return false;
        }

        PackShadowDirectives shadowDirectives = directives.getShadowDirectives();
        return shadowDirectives != null
            && shadowDirectives.isShadowEnabled().orElse(true)
            && shadowDirectives.getResolution() > 0;
    }

    public int shadowResolution(PackDirectives directives) {
        if (directives == null || directives.getShadowDirectives() == null) {
            return 0;
        }

        return Math.max(1, directives.getShadowDirectives().getResolution());
    }

    public int gbufferTargetCount(PackDirectives directives) {
        return Math.max(1, directives.getRenderTargetDirectives().getRenderTargetSettings().size());
    }

    public boolean terrainFramebuffersEnabled(ShaderProperties properties) {
        return properties != null;
    }
}
