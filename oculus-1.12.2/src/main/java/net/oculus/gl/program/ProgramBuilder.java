package net.oculus.gl.program;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.shader.GlShader;
import net.oculus.gl.shader.ProgramCreator;
import net.oculus.gl.shader.ShaderType;
import net.oculus.gl.state.MatrixState;
import net.oculus.gl.state.GameDataSuppliers;
import net.oculus.shaderpack.ProgramLoadException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

/**
 * A trimmed-down version of the Iris {@code ProgramBuilder}. It exposes the same entry
 * points but defers all real GL work so that the shader management pipeline can be
 * assembled step by step on 1.12.2.
 */
public final class ProgramBuilder {
    private static final Logger LOGGER = LogManager.getLogger(ProgramBuilder.class);

    private final String name;
    private final int program;

    private final ProgramUniforms.Builder uniforms;
    private final ProgramSamplers.Builder samplers;
    private final ProgramImages.Builder images;

    private ProgramBuilder(String name, int program) {
        this.name = name;
        this.program = program;
        this.uniforms = ProgramUniforms.builder(name, program);
        this.samplers = ProgramSamplers.builder(name, program);
        this.images = ProgramImages.builder(program);

        discoverBuiltInUniforms();
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
        } catch (ProgramLoadException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ProgramLoadException("Failed to compile " + type + " shader for program " + name, ex);
        }
    }

    private void discoverBuiltInUniforms() {
        int uniformCount = OculusRenderSystem.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
        if (uniformCount <= 0) {
            return;
        }

        Set<String> processed = new HashSet<>();

        int maxNameLength = Math.max(32, OculusRenderSystem.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH));
        IntBuffer lengthBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer sizeBuffer = BufferUtils.createIntBuffer(1);
        IntBuffer typeBuffer = BufferUtils.createIntBuffer(1);
        ByteBuffer nameBuffer = BufferUtils.createByteBuffer(maxNameLength);

        for (int index = 0; index < uniformCount; index++) {
            lengthBuffer.clear();
            sizeBuffer.clear();
            typeBuffer.clear();
            nameBuffer.clear();

            GL20.glGetActiveUniform(program, index, lengthBuffer, sizeBuffer, typeBuffer, nameBuffer);

            int nameLength = lengthBuffer.get(0);
            if (nameLength <= 0) {
                continue;
            }

            byte[] nameBytes = new byte[nameLength];
            nameBuffer.position(0);
            nameBuffer.get(nameBytes, 0, nameLength);

            String rawName = new String(nameBytes, StandardCharsets.UTF_8);
            String uniformName = sanitizeUniformName(rawName);
            if (uniformName.isEmpty() || uniformName.startsWith("gl_")) {
                continue;
            }

            if (!processed.add(uniformName)) {
                continue;
            }

            int type = typeBuffer.get(0);
            handleUniform(uniformName, type);
        }
    }

    private static String sanitizeUniformName(String rawName) {
        int bracketIndex = rawName.indexOf('[');
        return bracketIndex >= 0 ? rawName.substring(0, bracketIndex) : rawName;
    }

    private void handleUniform(String uniformName, int glType) {
        if (isSamplerType(glType)) {
            samplers.addSampler(uniformName);
            return;
        }

        switch (uniformName) {
            case "modelViewMatrix":
            case "u_ModelViewMatrix":
                uniforms.addMat4f(uniformName, MatrixState::updateModelViewMatrix);
                break;
            case "projectionMatrix":
            case "u_ProjectionMatrix":
                uniforms.addMat4f(uniformName, MatrixState::updateProjectionMatrix);
                break;
            case "systemTime":
            case "u_Time":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.systemTime());
                break;
            case "cameraPosition":
            case "u_CameraPosition":
                uniforms.addVec3f(uniformName, GameDataSuppliers.cameraPosition());
                break;
            case "fogColor":
            case "u_FogColor":
                uniforms.addVec4f(uniformName, GameDataSuppliers.fogColor());
                break;
            case "fogStart":
            case "u_FogStart":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.fogStart());
                break;
            case "fogEnd":
            case "u_FogEnd":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.fogEnd());
                break;
            case "viewWidth":
            case "u_ViewWidth":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.viewWidth());
                break;
            case "viewHeight":
            case "u_ViewHeight":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.viewHeight());
                break;
            default:
                LOGGER.warn("Unknown uniform {} in program {}, it will not be updated.", uniformName, this.name);
        }
    }

    private static boolean isSamplerType(int glType) {
        switch (glType) {
            case GL20.GL_SAMPLER_2D:
            case GL20.GL_SAMPLER_2D_SHADOW:
                return true;
            default:
                return false;
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
