package com.github.zsoltmolnarr.oculus.client.render.gl.program;

import net.oculus.gl.shader.GlShader;
import net.oculus.gl.shader.ShaderType;

/**
 * Thin wrapper around the existing {@link GlShader} that exposes a controlled API for the
 * client rendering pipeline.
 */
final class Shader implements AutoCloseable {
    private final GlShader delegate;

    private Shader(GlShader delegate) {
        this.delegate = delegate;
    }

    static Shader compile(ShaderType type, String name, String source) {
        return new Shader(new GlShader(type, name, source));
    }

    GlShader unwrap() {
        return delegate;
    }

    @Override
    public void close() {
        delegate.destroy();
    }
}
