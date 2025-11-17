package com.github.zsoltmolnarr.oculus.client.render.gl.program;

import net.oculus.gl.OculusRenderSystem;

/**
 * Minimal uniform wrapper.
 */
public final class Uniform {
    private final Program program;
    private final String name;

    Uniform(Program program, String name) {
        this.program = program;
        this.name = name;
    }

    public void setInt(int value) {
        program.setUniform1i(name, value);
    }
}
