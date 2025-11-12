package net.oculus.gl.program;

import java.util.Objects;
import java.util.function.IntSupplier;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.shader.GlShader;
import net.oculus.gl.shader.ProgramCreator;
import net.oculus.gl.shader.ShaderType;

/**
 * A trimmed-down version of the Iris {@code ProgramBuilder}. It exposes the same entry
 * points but defers all real GL work so that the shader management pipeline can be
 * assembled step by step on 1.12.2.
 */
public final class ProgramBuilder {
    private final String name;
    private final int program;

    private final ProgramUniforms.Builder uniforms;
    private final ProgramSamplers.Builder samplers;
    private final ProgramImages.Builder images;

    private ProgramBuilder(String name, int program) {
        this.name = name;
        this.program = program;
        this.uniforms = ProgramUniforms.builder(name, program);
        this.samplers = ProgramSamplers.builder(program);
        this.images = ProgramImages.builder(program);
    }

    public void bindAttributeLocation(int index, String attribute) {
        OculusRenderSystem.bindAttributeLocation(program, index, attribute);
    }

    public ProgramUniforms.Builder uniforms() {
        return uniforms;
    }

    public ProgramSamplers.Builder samplers() {
        return samplers;
    }

    public ProgramImages.Builder images() {
        return images;
    }

    public static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource) {
        Objects.requireNonNull(name, "program name");

        GlShader vertex = buildShader(ShaderType.VERTEX, name + ".vsh", vertexSource);
        GlShader geometry = geometrySource != null ? buildShader(ShaderType.GEOMETRY, name + ".gsh", geometrySource) : null;
        GlShader fragment = buildShader(ShaderType.FRAGMENT, name + ".fsh", fragmentSource);

        int programId;
        if (geometry != null) {
            programId = ProgramCreator.create(name, vertex, geometry, fragment);
        } else {
            programId = ProgramCreator.create(name, vertex, fragment);
        }

        vertex.destroy();
        if (geometry != null) {
            geometry.destroy();
        }
        fragment.destroy();

        return new ProgramBuilder(name, programId);
    }

    public static ProgramBuilder beginCompute(String name, String source) {
        Objects.requireNonNull(name, "program name");

        GlShader compute = buildShader(ShaderType.COMPUTE, name + ".csh", source);
        int programId = ProgramCreator.create(name, compute);
        compute.destroy();

        return new ProgramBuilder(name, programId);
    }

    private static GlShader buildShader(ShaderType type, String name, String source) {
        try {
            return new GlShader(type, name, source);
        } catch (RuntimeException ex) {
            throw new RuntimeException("Failed to compile " + type + " shader for program " + name, ex);
        }
    }

    public Program build() {
        return new Program(program, uniforms.build(), samplers.build(), images.build());
    }

    public ComputeProgram buildCompute() {
        return new ComputeProgram(program, uniforms.build(), samplers.build(), images.build());
    }

    // --- Sampler helpers --------------------------------------------------

    public void addExternalSampler(int textureUnit, String... names) {
        // No sampler state yet; placeholder for parity with 1.16.5.
    }

    public boolean hasSampler(String name) {
        return false;
    }

    public boolean addDefaultSampler(IntSupplier sampler, String... names) {
        return false;
    }

    public boolean addDynamicSampler(IntSupplier sampler, String... names) {
        return false;
    }

    public boolean addDynamicSampler(IntSupplier sampler, Runnable notifier, String... names) {
        return addDynamicSampler(sampler, names);
    }

    // --- Image helpers ----------------------------------------------------

    public boolean hasImage(String name) {
        return false;
    }

    public void addTextureImage(IntSupplier textureId, Object internalFormat, String name) {
        // Placeholder until image bindings are wired up.
    }

    @Override
    public String toString() {
        return "ProgramBuilder{" +
                "name='" + name + '\'' +
                ", program=" + program +
                '}';
    }
}
