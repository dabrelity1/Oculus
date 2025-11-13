package net.oculus.util;

import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackRenderTargetDirectives;
import net.oculus.shaderpack.ShaderProperties;

/**
 * Minimal configuration facade used by the shader pipeline setup code. The real
 * implementation in modern Iris exposes a wide range of toggles; this stub keeps
 * just enough structure so the high-level pipeline code can be ported without
 * collapsing.
 */
public final class Config {
    private static final Config INSTANCE = new Config();

    private Config() {
    }

    public static Config get() {
        return INSTANCE;
    }

    public boolean shadowsEnabled(PackDirectives directives) {
        return !directives.getRenderTargetDirectives().getRenderTargetSettings().isEmpty();
    }

    public int shadowResolution(PackDirectives directives) {
        PackRenderTargetDirectives renderTargets = directives.getRenderTargetDirectives();
        int candidate = renderTargets.getRenderTargetSettings().size() * 256;
        return Math.max(candidate, 512);
    }

    public int gbufferTargetCount(PackDirectives directives) {
        return Math.max(1, directives.getRenderTargetDirectives().getRenderTargetSettings().size());
    }

    public boolean terrainFramebuffersEnabled(ShaderProperties properties) {
        return properties != null;
    }
}
