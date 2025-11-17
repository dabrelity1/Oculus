package com.github.zsoltmolnarr.oculus.client.render.gl.program;

import net.minecraft.util.ResourceLocation;

/**
 * Predefined small shader programs used internally by the framebuffer pipeline.
 */
public final class InternalPrograms {
    public static final ProgramDefinition PASSTHROUGH = ProgramDefinition.fromResources(
        "internal/passthrough",
        new ResourceLocation("oculus", "shaders/internal/passthrough.vsh"),
        new ResourceLocation("oculus", "shaders/internal/passthrough.fsh")
    );

    private InternalPrograms() {
    }
}
