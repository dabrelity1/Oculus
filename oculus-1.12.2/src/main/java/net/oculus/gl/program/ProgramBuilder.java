package net.oculus.gl.program;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntSupplier;

import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.image.ImageHolder;
import net.oculus.gl.shader.GlShader;
import net.oculus.gl.shader.ProgramCreator;
import net.oculus.gl.shader.ShaderType;
import net.oculus.gl.state.GameDataSuppliers;
import net.oculus.gl.state.MatrixState;
import net.oculus.gl.state.StateUpdateNotifiers;
import net.oculus.gl.state.ValueUpdateNotifier;
import net.oculus.layer.GbufferPrograms;
import net.oculus.gl.texture.InternalTextureFormat;
import net.minecraft.client.renderer.OpenGlHelper;
import net.oculus.pipeline.InputAvailability;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.texture.FallbackTextures;
import net.oculus.texture.pbr.PBRTextureManager;
import net.oculus.uniforms.SystemTimeUniforms;
import net.oculus.uniforms.BuiltinReplacementUniforms;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.CelestialUniforms;
import net.oculus.uniforms.CompatibilityUniforms;
import net.oculus.uniforms.CustomUniforms;
import net.oculus.uniforms.FrameUpdateNotifier;
import net.oculus.uniforms.GameplayUniforms;
import net.oculus.uniforms.IdMapUniforms;
import net.oculus.uniforms.ShadowUniforms;
import net.oculus.uniforms.SpecialEffectUniforms;
import net.oculus.uniforms.WorldInfoUniforms;
import net.oculus.uniforms.custom.CustomUniformExpressionManager;
import net.oculus.uniforms.transforms.SmoothedFloat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL42;

/**
 * A trimmed-down version of the Iris {@code ProgramBuilder}. It exposes the same entry
 * points but defers all real GL work so that the shader management pipeline can be
 * assembled step by step on 1.12.2.
 */
public final class ProgramBuilder implements ImageHolder {
    private static final Logger LOGGER = LogManager.getLogger(ProgramBuilder.class);
    private static final Set<Integer> WORLD_RESERVED_TEXTURE_UNITS = textureUnitSet(0, 1, 2);
    private static final float DEFAULT_WETNESS_HALF_LIFE = 600.0F;
    private static final float DEFAULT_DRYNESS_HALF_LIFE = 200.0F;

    static {
        GbufferPrograms.init();
    }

    private final String name;
    private final int program;
    private final boolean ownsProgramHandle;

    private final ProgramUniforms.Builder uniforms;
    private final ProgramSamplers.Builder samplers;
    private final ProgramImages.Builder images;
    private final CustomUniformExpressionManager customUniforms;
    private final InputAvailability availability;
    private final FrameUpdateNotifier frameUpdateNotifier;
    private final PackDirectives packDirectives;
    private Set<String> activeSamplerUniformNames;
    private Map<String, Integer> activeSamplerUniformTypes;
    private Set<String> activeImageUniformNames;

    private ProgramBuilder(String name, int program) {
        this(name, program, SamplerOverrideMap.empty(), CustomUniformExpressionManager.empty());
    }

    private ProgramBuilder(String name, int program, SamplerOverrideMap overrides) {
        this(name, program, overrides, CustomUniformExpressionManager.empty());
    }

    private ProgramBuilder(String name, int program, SamplerOverrideMap overrides,
                           CustomUniformExpressionManager customUniforms) {
        this(name, program, overrides, customUniforms, Collections.emptySet());
    }

    private ProgramBuilder(String name, int program, SamplerOverrideMap overrides,
                           CustomUniformExpressionManager customUniforms,
                           Set<Integer> reservedTextureUnits) {
        this(name, program, overrides, customUniforms, reservedTextureUnits, true);
    }

    private ProgramBuilder(String name, int program, SamplerOverrideMap overrides,
                           CustomUniformExpressionManager customUniforms,
                           Set<Integer> reservedTextureUnits,
                           boolean discoverActiveUniforms) {
        this(name, program, overrides, customUniforms, reservedTextureUnits, discoverActiveUniforms, null);
    }

    private ProgramBuilder(String name, int program, SamplerOverrideMap overrides,
                           CustomUniformExpressionManager customUniforms,
                           Set<Integer> reservedTextureUnits,
                           boolean discoverActiveUniforms,
                           InputAvailability availability) {
        this(name, program, overrides, customUniforms, reservedTextureUnits, discoverActiveUniforms, availability,
            null, null);
    }

    private ProgramBuilder(String name, int program, SamplerOverrideMap overrides,
                           CustomUniformExpressionManager customUniforms,
                           Set<Integer> reservedTextureUnits,
                           boolean discoverActiveUniforms,
                           InputAvailability availability,
                           FrameUpdateNotifier frameUpdateNotifier,
                           PackDirectives packDirectives) {
        this(name, program, overrides, customUniforms, reservedTextureUnits, discoverActiveUniforms, availability,
            frameUpdateNotifier, packDirectives, true);
    }

    private ProgramBuilder(String name, int program, SamplerOverrideMap overrides,
                           CustomUniformExpressionManager customUniforms,
                           Set<Integer> reservedTextureUnits,
                           boolean discoverActiveUniforms,
                           InputAvailability availability,
                           FrameUpdateNotifier frameUpdateNotifier,
                           PackDirectives packDirectives,
                           boolean ownsProgramHandle) {
        this.name = name;
        this.program = program;
        this.ownsProgramHandle = ownsProgramHandle;
        this.uniforms = ProgramUniforms.builder(name, program);
        this.samplers = ProgramSamplers.builder(name, program, overrides,
            reservedTextureUnitsForProgram(name, reservedTextureUnits));
        this.images = ProgramImages.builder(program);
        this.customUniforms = customUniforms == null ? CustomUniformExpressionManager.empty() : customUniforms;
        this.availability = availability;
        this.frameUpdateNotifier = frameUpdateNotifier;
        this.packDirectives = packDirectives;
        this.activeSamplerUniformNames = new HashSet<>();
        this.activeSamplerUniformTypes = new HashMap<>();
        this.activeImageUniformNames = new HashSet<>();

        if (discoverActiveUniforms) {
            discoverBuiltInUniforms();
        }
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
        return begin(name, vertexSource, geometrySource, fragmentSource, SamplerOverrideMap.empty());
    }

    public static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                       SamplerOverrideMap overrides) {
        return begin(name, vertexSource, geometrySource, fragmentSource, overrides, CustomUniformExpressionManager.empty());
    }

    public static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                       SamplerOverrideMap overrides,
                                       CustomUniformExpressionManager customUniforms) {
        return begin(name, vertexSource, geometrySource, fragmentSource, overrides, customUniforms, Collections.emptySet());
    }

    public static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                       SamplerOverrideMap overrides,
                                       CustomUniformExpressionManager customUniforms,
                                       Set<Integer> reservedTextureUnits) {
        return begin(name, vertexSource, geometrySource, fragmentSource, overrides, customUniforms,
            reservedTextureUnits, true);
    }

    public static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                       SamplerOverrideMap overrides,
                                       CustomUniformExpressionManager customUniforms,
                                       Set<Integer> reservedTextureUnits,
                                       InputAvailability availability) {
        return begin(name, vertexSource, geometrySource, fragmentSource, overrides, customUniforms,
            reservedTextureUnits, true, availability);
    }

    public static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                       SamplerOverrideMap overrides,
                                       CustomUniformExpressionManager customUniforms,
                                       Set<Integer> reservedTextureUnits,
                                       InputAvailability availability,
                                       FrameUpdateNotifier frameUpdateNotifier,
                                       PackDirectives packDirectives) {
        return begin(name, vertexSource, geometrySource, fragmentSource, overrides, customUniforms,
            reservedTextureUnits, true, availability, frameUpdateNotifier, packDirectives);
    }

    /**
     * Builds an internal program whose uniforms and samplers are registered explicitly by the caller.
     *
     * <p>Shader-pack programs use automatic active-uniform discovery so missing built-ins remain visible
     * in logs. Small Oculus-owned helper programs, matching the 1.16.5 builder contract, bind their
     * complete uniform surface directly after linking and should not emit unknown-uniform warnings before
     * those explicit bindings are attached.</p>
     */
    public static ProgramBuilder beginExplicit(String name, String vertexSource, String geometrySource,
                                               String fragmentSource) {
        return beginExplicit(name, vertexSource, geometrySource, fragmentSource, Collections.emptySet());
    }

    public static ProgramBuilder beginExplicit(String name, String vertexSource, String geometrySource,
                                               String fragmentSource, Set<Integer> reservedTextureUnits) {
        return begin(name, vertexSource, geometrySource, fragmentSource, SamplerOverrideMap.empty(),
            CustomUniformExpressionManager.empty(), reservedTextureUnits, false);
    }

    /**
     * Wraps an already-linked program so Oculus' uniform, sampler, and image binding system
     * can update it without taking ownership of the OpenGL program object.
     */
    public static ProgramBuilder wrapLinkedProgram(String name, int program,
                                                   CustomUniformExpressionManager customUniforms) {
        return new ProgramBuilder(name, program, SamplerOverrideMap.empty(), customUniforms,
            Collections.emptySet(), true, null, null, null, false);
    }

    public static ProgramBuilder wrapLinkedProgram(String name, int program,
                                                   CustomUniformExpressionManager customUniforms,
                                                   FrameUpdateNotifier frameUpdateNotifier,
                                                   PackDirectives packDirectives) {
        return new ProgramBuilder(name, program, SamplerOverrideMap.empty(), customUniforms,
            Collections.emptySet(), true, null, frameUpdateNotifier, packDirectives, false);
    }

    public static ProgramBuilder wrapLinkedProgram(String name, int program,
                                                   CustomUniformExpressionManager customUniforms,
                                                   InputAvailability availability,
                                                   FrameUpdateNotifier frameUpdateNotifier,
                                                   PackDirectives packDirectives) {
        return new ProgramBuilder(name, program, SamplerOverrideMap.empty(), customUniforms,
            Collections.emptySet(), true, availability, frameUpdateNotifier, packDirectives, false);
    }

    private static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                        SamplerOverrideMap overrides,
                                        CustomUniformExpressionManager customUniforms,
                                        Set<Integer> reservedTextureUnits,
                                        boolean discoverActiveUniforms) {
        return begin(name, vertexSource, geometrySource, fragmentSource, overrides, customUniforms,
            reservedTextureUnits, discoverActiveUniforms, null);
    }

    private static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                        SamplerOverrideMap overrides,
                                        CustomUniformExpressionManager customUniforms,
                                        Set<Integer> reservedTextureUnits,
                                        boolean discoverActiveUniforms,
                                        InputAvailability availability) {
        return begin(name, vertexSource, geometrySource, fragmentSource, overrides, customUniforms,
            reservedTextureUnits, discoverActiveUniforms, availability, null, null);
    }

    private static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                        SamplerOverrideMap overrides,
                                        CustomUniformExpressionManager customUniforms,
                                        Set<Integer> reservedTextureUnits,
                                        boolean discoverActiveUniforms,
                                        InputAvailability availability,
                                        FrameUpdateNotifier frameUpdateNotifier,
                                        PackDirectives packDirectives) {
        Objects.requireNonNull(name, "program name");

        GlShader vertex = null;
        GlShader geometry = null;
        GlShader fragment = null;
        int programId;
        try {
            vertex = buildShader(ShaderType.VERTEX, name + ".vsh", vertexSource);
            geometry = geometrySource != null ? buildShader(ShaderType.GEOMETRY, name + ".gsh", geometrySource) : null;
            fragment = buildShader(ShaderType.FRAGMENT, name + ".fsh", fragmentSource);

            if (geometry != null) {
                programId = ProgramCreator.create(name, vertex, geometry, fragment);
            } else {
                programId = ProgramCreator.create(name, vertex, fragment);
            }
        } finally {
            destroyShader(vertex, name);
            destroyShader(geometry, name);
            destroyShader(fragment, name);
        }

        return createOwningProgramBuilder(name, programId, overrides, customUniforms, reservedTextureUnits,
            discoverActiveUniforms, availability, frameUpdateNotifier, packDirectives);
    }

    public static ProgramBuilder beginCompute(String name, String source) {
        return beginCompute(name, source, SamplerOverrideMap.empty());
    }

    public static ProgramBuilder beginCompute(String name, String source, SamplerOverrideMap overrides) {
        return beginCompute(name, source, overrides, CustomUniformExpressionManager.empty());
    }

    public static ProgramBuilder beginCompute(String name, String source, SamplerOverrideMap overrides,
                                              CustomUniformExpressionManager customUniforms) {
        return beginCompute(name, source, overrides, customUniforms, Collections.emptySet());
    }

    public static ProgramBuilder beginCompute(String name, String source, SamplerOverrideMap overrides,
                                              CustomUniformExpressionManager customUniforms,
                                              Set<Integer> reservedTextureUnits) {
        return beginCompute(name, source, overrides, customUniforms, reservedTextureUnits, null, null);
    }

    public static ProgramBuilder beginCompute(String name, String source, SamplerOverrideMap overrides,
                                              CustomUniformExpressionManager customUniforms,
                                              Set<Integer> reservedTextureUnits,
                                              FrameUpdateNotifier frameUpdateNotifier,
                                              PackDirectives packDirectives) {
        Objects.requireNonNull(name, "program name");

        if (!OculusRenderSystem.supportsCompute()) {
            throw new IllegalStateException("Compute shaders are not supported by the active OpenGL context, "
                + "but shader program " + name + " defines a compute pass.");
        }

        GlShader compute = null;
        int programId;
        try {
            compute = buildShader(ShaderType.COMPUTE, name + ".csh", source);
            programId = ProgramCreator.create(name, compute);
        } finally {
            destroyShader(compute, name);
        }

        return createOwningProgramBuilder(name, programId, overrides, customUniforms, reservedTextureUnits,
            true, null, frameUpdateNotifier, packDirectives);
    }

    private static ProgramBuilder createOwningProgramBuilder(String name, int programId,
                                                             SamplerOverrideMap overrides,
                                                             CustomUniformExpressionManager customUniforms,
                                                             Set<Integer> reservedTextureUnits,
                                                             boolean discoverActiveUniforms,
                                                             InputAvailability availability,
                                                             FrameUpdateNotifier frameUpdateNotifier,
                                                             PackDirectives packDirectives) {
        try {
            return new ProgramBuilder(name, programId, overrides, customUniforms, reservedTextureUnits,
                discoverActiveUniforms, availability, frameUpdateNotifier, packDirectives);
        } catch (RuntimeException | Error exception) {
            deleteFailedProgramHandle(programId, name, exception, "builder construction");
            throw exception;
        }
    }

    private void deleteFailedProgramAfterBuildFailure(Throwable failure, String failurePhase) {
        if (!ownsProgramHandle) {
            return;
        }

        deleteFailedProgramHandle(program, name, failure, failurePhase);
    }

    private static void deleteFailedProgramHandle(int programId, String programName, Throwable failure,
                                                  String failurePhase) {
        if (programId == 0) {
            return;
        }

        try {
            OculusRenderSystem.glDeleteProgram(programId);
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
            LOGGER.debug("Failed to delete linked program {} after {} failed", programName, failurePhase,
                cleanupFailure);
        }
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
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

    private static void destroyShader(GlShader shader, String programName) {
        if (shader == null) {
            return;
        }

        try {
            shader.destroy();
        } catch (RuntimeException | Error exception) {
            LOGGER.debug("Failed to destroy compiled shader object for program {}", programName, exception);
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
            ((Buffer) lengthBuffer).clear();
            ((Buffer) sizeBuffer).clear();
            ((Buffer) typeBuffer).clear();
            ((Buffer) nameBuffer).clear();

            GL20.glGetActiveUniform(program, index, lengthBuffer, sizeBuffer, typeBuffer, nameBuffer);

            int nameLength = lengthBuffer.get(0);
            if (nameLength <= 0) {
                continue;
            }

            byte[] nameBytes = new byte[nameLength];
            ((Buffer) nameBuffer).position(0);
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
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;

        if (isSamplerType(glType)) {
            activeSamplerUniformNames.add(uniformName);
            activeSamplerUniformTypes.put(uniformName, glType);
            if ("u_BlockTex".equals(uniformName)) {
                samplers.addExternalSampler(0, uniformName);
                return;
            }
            if ("u_LightTex".equals(uniformName)) {
                samplers.addExternalSampler(getLightmapTextureUnit(), uniformName);
                return;
            }
            if (usesWorldLevelSamplers() && !hasTextureInput() && isUnavailableAlbedoFallbackSampler(uniformName)) {
                samplers.addDynamicSampler(FallbackTextures::getWhiteTexture,
                    "tex", "texture", "gtexture", "gcolor", "colortex0");
                return;
            }
            if (usesWorldLevelSamplers() && isWorldAlbedoSampler(uniformName)) {
                samplers.addExternalSampler(0, uniformName);
                return;
            }
            if (usesWorldLevelSamplers() && "lightmap".equals(uniformName)) {
                if (hasLightmapInput()) {
                    samplers.addExternalSampler(getLightmapTextureUnit(), uniformName);
                } else {
                    samplers.addDynamicSampler(FallbackTextures::getWhiteTexture, uniformName);
                }
                return;
            }
            if (usesWorldLevelSamplers() && "iris_overlay".equals(uniformName)) {
                if (availability != null && availability.overlay) {
                    samplers.addExternalSampler(getOverlayTextureUnit(), uniformName);
                } else {
                    samplers.addDynamicSampler(FallbackTextures::getWhiteTexture, uniformName);
                }
                return;
            }
            if (PBRTextureManager.isPbrSamplerName(uniformName)) {
                PBRTextureManager.INSTANCE.markPbrSamplerUsed();
            }
            if (!shouldAutoBindSampler(uniformName)) {
                return;
            }
            samplers.addSampler(uniformName);
            return;
        }

        if (isImageType(glType)) {
            activeImageUniformNames.add(uniformName);
            return;
        }

        if (customUniforms.addUniform(uniformName, uniforms)) {
            return;
        }

        switch (uniformName) {
            case "gbufferModelView":
                uniforms.addMatrix4(uniformName, state::getGbufferModelView);
                break;
            case "gbufferPreviousModelView":
                uniforms.addMatrix4(uniformName, state::getPreviousModelView);
                break;
            case "gbufferModelViewInverse":
                uniforms.addMatrix4(uniformName, state::getModelViewInverse);
                break;
            case "gbufferProjection":
                uniforms.addMatrix4(uniformName, state::getGbufferProjection);
                break;
            case "gbufferPreviousProjection":
                uniforms.addMatrix4(uniformName, state::getPreviousProjection);
                break;
            case "gbufferProjectionInverse":
                uniforms.addMatrix4(uniformName, state::getProjectionInverse);
                break;
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
                uniforms.addVec3(uniformName, state::getCameraPositionVec);
                break;
            case "previousCameraPosition":
                uniforms.addVec3(uniformName, state::getPreviousCameraPositionVec);
                break;
            case "cameraPositionInt":
                uniforms.addIntVec3(uniformName, state::getCameraPositionInt);
                break;
            case "previousCameraPositionInt":
                uniforms.addIntVec3(uniformName, state::getPreviousCameraPositionInt);
                break;
            case "cameraPositionFract":
                uniforms.addVec3(uniformName, state::getCameraPositionFract);
                break;
            case "previousCameraPositionFract":
                uniforms.addVec3(uniformName, state::getPreviousCameraPositionFract);
                break;
            case "fogColor":
            case "u_FogColor":
                if (glType == GL20.GL_FLOAT_VEC3) {
                    uniforms.addVec3(uniformName, state::getFogColor, state.getFogColorNotifier());
                } else {
                    uniforms.addVec4(uniformName, state::getFogColorVec4, state.getFogColorNotifier());
                }
                break;
            case "fogStart":
            case "u_FogStart":
            case "iris_FogStart":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.fogStart(),
                    StateUpdateNotifiers.fogStartNotifierWithToggle());
                break;
            case "fogEnd":
            case "u_FogEnd":
            case "iris_FogEnd":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.fogEnd(),
                    StateUpdateNotifiers.fogEndNotifierWithToggle());
                break;
            case "viewWidth":
            case "u_ViewWidth":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.viewWidth());
                break;
            case "viewHeight":
            case "u_ViewHeight":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.viewHeight());
                break;
            case "aspectRatio":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.aspectRatio());
                break;
            case "pixel_size_x":
                uniforms.addFloat(uniformName, CustomUniforms::getPixelSizeX);
                break;
            case "pixel_size_y":
                uniforms.addFloat(uniformName, CustomUniforms::getPixelSizeY);
                break;
            case "inv_aspect_ratio":
                uniforms.addFloat(uniformName, CustomUniforms::getInverseAspectRatio);
                break;
            case "pi":
                uniforms.addFloat(uniformName, () -> (float) Math.PI);
                break;
            case "near":
                uniforms.addFloat(uniformName, state::getNearPlane);
                break;
            case "far":
                uniforms.addFloat(uniformName, state::getFarPlane);
                break;
            case "eyeAltitude":
                uniforms.addFloat(uniformName, state::getEyeAltitude);
                break;
            case "frameCounter":
                addFrameCounterUniform(uniformName, glType);
                break;
            case "framemod2":
                addFrameModUniform(uniformName, glType, 2);
                break;
            case "framemod4":
                addFrameModUniform(uniformName, glType, 4);
                break;
            case "framemod8":
                addFrameMod8Uniform(uniformName, glType);
                break;
            case "frame_mod":
                uniforms.addInt(uniformName, CustomUniforms::getFrameMod);
                break;
            case "tickDelta":
                uniforms.addFloat(uniformName, state::getTickDelta);
                break;
            case "fogMode":
            case "u_FogMode":
                uniforms.addInt(uniformName, GameDataSuppliers.fogMode(),
                    StateUpdateNotifiers.fogModeNotifierWithToggle());
                break;
            case "fogDensity":
            case "u_FogDensity":
            case "iris_FogDensity":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.fogDensity(),
                    StateUpdateNotifiers.fogDensityNotifierWithToggle());
                break;
            case "iris_FogColor":
                uniforms.addVec4(uniformName, state::getFogColorVec4, state.getFogColorNotifier());
                break;
            case "sunAngle":
                uniforms.addFloat(uniformName, CelestialUniforms::getSunAngle);
                break;
            case "shadowAngle":
                uniforms.addFloat(uniformName, CelestialUniforms::getShadowAngle);
                break;
            case "sunPosition":
                uniforms.addVec3(uniformName, CelestialUniforms::getSunPosition);
                break;
            case "moonPosition":
                uniforms.addVec3(uniformName, CelestialUniforms::getMoonPosition);
                break;
            case "shadowLightPosition":
                uniforms.addVec3(uniformName, CelestialUniforms::getShadowLightPosition);
                break;
            case "upPosition":
                uniforms.addVec3(uniformName, CelestialUniforms::getUpPosition);
                break;
            case "shadowModelView":
                uniforms.addMatrix4(uniformName, ShadowUniforms::getShadowModelView);
                break;
            case "shadowModelViewInverse":
                uniforms.addMatrix4(uniformName, ShadowUniforms::getShadowModelViewInverse);
                break;
            case "shadowProjection":
            case "shadowProjectionMatrix":
                uniforms.addMatrix4(uniformName, ShadowUniforms::getShadowProjection);
                break;
            case "shadowProjectionInverse":
            case "shadowProjectionMatrixInverse":
                uniforms.addMatrix4(uniformName, ShadowUniforms::getShadowProjectionInverse);
                break;
            case "frameTime":
                uniforms.addFloat(uniformName, SystemTimeUniforms.TIMER::getLastFrameTime);
                break;
            case "frameTimeCounter":
                uniforms.addFloat(uniformName, SystemTimeUniforms.TIMER::getFrameTimeCounter);
                break;
            case "day_moment":
                uniforms.addFloat(uniformName, CustomUniforms::getDayMoment);
                break;
            case "day_mixer":
                uniforms.addFloat(uniformName, CustomUniforms::getDayMixer);
                break;
            case "night_mixer":
                uniforms.addFloat(uniformName, CustomUniforms::getNightMixer);
                break;
            case "vol_mixer":
                uniforms.addFloat(uniformName, CustomUniforms::getVolumeMixer);
                break;
            case "light_mix":
                uniforms.addFloat(uniformName, CustomUniforms::getLightMix);
                break;
            case "taa_offset":
                uniforms.addVec2(uniformName, CustomUniforms::getTaaOffset);
                break;
            case "dither_shift":
                uniforms.addFloat(uniformName, CustomUniforms::getDitherShift);
                break;
            case "fov_y_inv":
                uniforms.addFloat(uniformName, CustomUniforms::getFovYInverse);
                break;
            case "timeAngle":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getTimeAngle);
                break;
            case "timeBrightness":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getTimeBrightness);
                break;
            case "moonBrightness":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getMoonBrightness);
                break;
            case "shadowFade":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getShadowFade);
                break;
            case "shdFade":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getShdFade);
                break;
            case "blindFactor":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getBlindFactor);
                break;
            case "rainStrengthS":
                uniforms.addFloat(uniformName, sharedFrameSmoothedOrFallback(
                    15.0F, 15.0F, GameplayUniforms::getRainStrength, CompatibilityUniforms::getRainStrengthS));
                break;
            case "rainStrengthShiningStars":
                uniforms.addFloat(uniformName, sharedFrameSmoothedOrFallback(
                    10.0F, 11.0F, GameplayUniforms::getRainStrength,
                    CompatibilityUniforms::getRainStrengthShiningStars));
                break;
            case "rainStrengthS2":
                uniforms.addFloat(uniformName, sharedFrameSmoothedOrFallback(
                    70.0F, 1.0F, GameplayUniforms::getRainStrength, CompatibilityUniforms::getRainStrengthS2));
                break;
            case "inDry":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInDry);
                break;
            case "inRainy":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInRainy);
                break;
            case "inSnowy":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInSnowy);
                break;
            case "isDry":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getIsDry);
                break;
            case "isRainy":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getIsRainy);
                break;
            case "isSnowy":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getIsSnowy);
                break;
            case "isEyeInCave":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getIsEyeInCave);
                break;
            case "velocity":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getVelocity);
                break;
            case "starter":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getStarter);
                break;
            case "frameTimeSmooth":
                uniforms.addFloat(uniformName, sharedFrameSmoothedOrFallback(
                    5.0F, 5.0F, SystemTimeUniforms.TIMER::getLastFrameTime,
                    CompatibilityUniforms::getFrameTimeSmooth));
                break;
            case "eyeBrightnessM":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getEyeBrightnessMUniform);
                break;
            case "eyeBrightnessM2":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getEyeBrightnessM2);
                break;
            case "eyeBrightness":
                if (glType == GL20.GL_INT_VEC2) {
                    uniforms.addIntVec2(uniformName, GameplayUniforms::getEyeBrightnessInt);
                } else {
                    uniforms.addVec2(uniformName, GameplayUniforms::getEyeBrightness);
                }
                break;
            case "eyeBrightnessSmooth":
                if (glType == GL20.GL_INT_VEC2) {
                    uniforms.addIntVec2(uniformName, GameplayUniforms::getEyeBrightnessSmoothInt);
                } else {
                    uniforms.addVec2(uniformName, GameplayUniforms::getEyeBrightnessSmooth);
                }
                break;
            case "rainFactor":
                uniforms.addFloat(uniformName, sharedFrameSmoothedOrFallback(
                    15.0F, 15.0F, GameplayUniforms::getRainStrength, CompatibilityUniforms::getRainFactor));
                break;
            case "inSwamp":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInSwamp);
                break;
            case "inBasaltDeltas":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInBasaltDeltas);
                break;
            case "inCrimsonForest":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInCrimsonForest);
                break;
            case "inNetherWastes":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInNetherWastes);
                break;
            case "inSoulValley":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInSoulValley);
                break;
            case "inWarpedForest":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInWarpedForest);
                break;
            case "inPaleGarden":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getInPaleGarden);
                break;
            case "BiomeTemp":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getBiomeTemperature);
                break;
            case "day":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getDay);
                break;
            case "night":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getNight);
                break;
            case "dawnDusk":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getDawnDusk);
                break;
            case "isPrecipitationRain":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getIsPrecipitationRain);
                break;
            case "touchmybody":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getTouchMyBody);
                break;
            case "sneakSmooth":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getSneakSmooth);
                break;
            case "burningSmooth":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getBurningSmooth);
                break;
            case "effectStrength":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getEffectStrength);
                break;
            case "relativeEyePosition":
                uniforms.addVec3(uniformName, SpecialEffectUniforms::getRelativeEyePosition);
                break;
            case "lightningBoltPosition":
                uniforms.addVec4(uniformName, SpecialEffectUniforms::getLightningBoltPosition);
                break;
            case "worldTime":
                uniforms.addInt(uniformName, GameplayUniforms::getWorldTime);
                break;
            case "worldDay":
                uniforms.addInt(uniformName, GameplayUniforms::getWorldDay);
                break;
            case "moonPhase":
                uniforms.addInt(uniformName, GameplayUniforms::getMoonPhase);
                break;
            case "rainStrength":
                uniforms.addFloat(uniformName, GameplayUniforms::getRainStrength);
                break;
            case "wetness":
                uniforms.addFloat(uniformName, sharedFrameSmoothedOrFallback(
                    getWetnessHalfLife(), getDrynessHalfLife(), GameplayUniforms::getRainStrength,
                    GameplayUniforms::getWetness));
                break;
            case "thunderStrength":
                uniforms.addFloat(uniformName, GameplayUniforms::getThunderStrength);
                break;
            case "currentPlayerHealth":
                uniforms.addFloat(uniformName, GameplayUniforms::getCurrentPlayerHealth);
                break;
            case "maxPlayerHealth":
                uniforms.addFloat(uniformName, GameplayUniforms::getMaxPlayerHealth);
                break;
            case "currentPlayerHunger":
                uniforms.addFloat(uniformName, GameplayUniforms::getCurrentPlayerHunger);
                break;
            case "maxPlayerHunger":
                uniforms.addFloat(uniformName, GameplayUniforms::getMaxPlayerHunger);
                break;
            case "currentPlayerAir":
                uniforms.addFloat(uniformName, GameplayUniforms::getCurrentPlayerAir);
                break;
            case "maxPlayerAir":
                uniforms.addFloat(uniformName, GameplayUniforms::getMaxPlayerAir);
                break;
            case "skyColor":
                uniforms.addVec3(uniformName, GameplayUniforms::getSkyColor);
                break;
            case "hideGUI":
                uniforms.addInt(uniformName, GameplayUniforms::hideGui);
                break;
            case "firstPersonCamera":
                uniforms.addInt(uniformName, GameplayUniforms::isFirstPersonCamera);
                break;
            case "isEyeInWater":
                uniforms.addInt(uniformName, GameplayUniforms::isEyeInWater);
                break;
            case "blindness":
                uniforms.addFloat(uniformName, GameplayUniforms::getBlindness);
                break;
            case "nightVision":
                uniforms.addFloat(uniformName, GameplayUniforms::getNightVision);
                break;
            case "is_sneaking":
                uniforms.addInt(uniformName, GameplayUniforms::isSneaking);
                break;
            case "is_sprinting":
                uniforms.addInt(uniformName, GameplayUniforms::isSprinting);
                break;
            case "is_invisible":
                uniforms.addInt(uniformName, GameplayUniforms::isInvisible);
                break;
            case "is_burning":
                uniforms.addInt(uniformName, GameplayUniforms::isBurning);
                break;
            case "is_on_ground":
                uniforms.addInt(uniformName, GameplayUniforms::isOnGround);
                break;
            case "is_hurt":
                uniforms.addInt(uniformName, GameplayUniforms::isHurt);
                break;
            case "isSpectator":
                uniforms.addInt(uniformName, GameplayUniforms::isSpectator);
                break;
            case "screenBrightness":
                uniforms.addFloat(uniformName, GameplayUniforms::getScreenBrightness);
                break;
            case "eyePosition":
                uniforms.addVec3(uniformName, GameplayUniforms::getEyePosition);
                break;
            case "playerLookVector":
                uniforms.addVec3(uniformName, GameplayUniforms::getPlayerLookVector);
                break;
            case "playerBodyVector":
                uniforms.addVec3(uniformName, GameplayUniforms::getPlayerBodyVector);
                break;
            case "currentColorSpace":
                uniforms.addInt(uniformName, GameplayUniforms::getCurrentColorSpace);
                break;
            case "entityId":
                uniforms.addInt(uniformName, state::getCurrentEntity, state.getEntityIdNotifier());
                break;
            case "blockEntityId":
                uniforms.addInt(uniformName, state::getCurrentBlockEntity, state.getBlockEntityIdNotifier());
                break;
            case "heldItemId":
                uniforms.addInt(uniformName, customUniforms::getHeldItemIdMain);
                break;
            case "heldItemId2":
                uniforms.addInt(uniformName, customUniforms::getHeldItemIdOff);
                break;
            case "heldBlockLightValue":
                uniforms.addInt(uniformName, () -> IdMapUniforms.getHeldBlockLightValueMain(isOldHandLight()));
                break;
            case "heldBlockLightValue2":
                uniforms.addInt(uniformName, IdMapUniforms::getHeldBlockLightValueOff);
                break;
            case "currentRenderedItemId":
                uniforms.addInt(uniformName, customUniforms::getCurrentRenderedItemId,
                    IdMapUniforms.getCurrentRenderedItemIdNotifier());
                break;
            case "atlasSize":
                if (glType == GL20.GL_INT_VEC2) {
                    uniforms.addIntVec2(uniformName, GameplayUniforms::getAtlasSize,
                        StateUpdateNotifiers.bindTextureNotifier);
                } else {
                    uniforms.addVec2(uniformName, GameplayUniforms::getAtlasSizeFloat,
                        StateUpdateNotifiers.bindTextureNotifier);
                }
                break;
            case "gtextureSize":
                if (glType == GL20.GL_INT_VEC2) {
                    uniforms.addIntVec2(uniformName, GameplayUniforms::getGtextureSize,
                        StateUpdateNotifiers.bindTextureNotifier);
                } else {
                    uniforms.addVec2(uniformName, GameplayUniforms::getGtextureSizeFloat,
                        StateUpdateNotifiers.bindTextureNotifier);
                }
                break;
            case "blendFunc":
                if (glType == GL20.GL_INT_VEC4) {
                    uniforms.addIntVec4(uniformName, GameplayUniforms::getBlendFunc,
                        StateUpdateNotifiers.blendFuncNotifier);
                } else {
                    uniforms.addVec4(uniformName, GameplayUniforms::getBlendFuncFloat,
                        StateUpdateNotifiers.blendFuncNotifier);
                }
                break;
            case "entityColor":
            case "iris_entityColor":
                uniforms.addVec4(uniformName, GameplayUniforms::getEntityColor,
                    GameplayUniforms.getEntityColorNotifier());
                break;
            case "heavyFog":
                uniforms.addInt(uniformName, GameplayUniforms::isHeavyFog);
                break;
            case "playerMood":
                uniforms.addFloat(uniformName, GameplayUniforms::getPlayerMood);
                break;
            case "maxBlindnessDarkness":
                uniforms.addFloat(uniformName, GameplayUniforms::getMaxBlindnessDarkness);
                break;
            case "iris_ModelViewMatrix":
                uniforms.addMatrix4(uniformName, state::getGbufferModelView);
                break;
            case "iris_ProjectionMatrix":
                uniforms.addMatrix4(uniformName, state::getGbufferProjection);
                break;
            case "iris_ProjMat":
                uniforms.addMat4f(uniformName, MatrixState::updateProjectionMatrix);
                break;
            case "u_ModelViewProjectionMatrix":
            case "iris_ModelViewProjectionMatrix":
                uniforms.addMatrix4(uniformName, state::getModelViewProjection);
                break;
            case "iris_NormalMatrix":
                uniforms.addMatrix4(uniformName, state::getNormalMatrix);
                break;
            case "iris_LightmapTextureMatrix":
                uniforms.addMatrix4(uniformName, BuiltinReplacementUniforms::getLightmapTextureMatrix);
                break;
            case "iris_TextureMat":
                uniforms.addMat4f(uniformName, MatrixState::updateTextureMatrix);
                break;
            case "iris_ModelViewMat":
                uniforms.addMat4f(uniformName, MatrixState::updateModelViewMatrix);
                break;
            case "iris_ChunkOffset":
                uniforms.addVec3(uniformName, BuiltinReplacementUniforms::getChunkOffset);
                break;
            case "iris_ColorModulator":
                uniforms.addVec4(uniformName, BuiltinReplacementUniforms::getColorModulator,
                    BuiltinReplacementUniforms.getColorModulatorNotifier());
                break;
            case "iris_ModelOffset":
                uniforms.addFloat(uniformName, () -> 0.0F);
                break;
            case "u_ModelScale":
                if (glType == GL20.GL_FLOAT_VEC3) {
                    uniforms.addVec3(uniformName, GameplayUniforms::getTerrainModelScaleVec3);
                } else {
                    uniforms.addFloat(uniformName, GameplayUniforms::getTerrainModelScale);
                }
                break;
            case "u_TextureScale":
                if (glType == GL20.GL_FLOAT_VEC2) {
                    uniforms.addVec2(uniformName, GameplayUniforms::getTerrainTextureScaleVec2);
                } else {
                    uniforms.addFloat(uniformName, GameplayUniforms::getTerrainTextureScale);
                }
                break;
            case "iris_LineWidth":
                uniforms.addFloat(uniformName, () -> 1.0F);
                break;
            case "darknessFactor":
            case "darknessLightFactor":
                uniforms.addFloat(uniformName, () -> 0.0F);
                break;
            case "iris_CameraTranslation":
                uniforms.addVec3(uniformName, state::getCameraPositionVec);
                break;
            case "iris_ScreenSize":
                uniforms.addVec2(uniformName, GameplayUniforms::getScreenSize);
                break;
            case "renderStage":
                uniforms.addInt(uniformName,
                    () -> GbufferPrograms.getCurrentPhase().ordinal(),
                    StateUpdateNotifiers.phaseChangeNotifier);
                break;
            case "bedrockLevel":
                uniforms.addInt(uniformName, WorldInfoUniforms::getBedrockLevel);
                break;
            case "cloudHeight":
                uniforms.addFloat(uniformName, WorldInfoUniforms::getCloudHeight);
                break;
            case "heightLimit":
                uniforms.addInt(uniformName, WorldInfoUniforms::getHeightLimit);
                break;
            case "logicalHeightLimit":
                uniforms.addInt(uniformName, WorldInfoUniforms::getLogicalHeightLimit);
                break;
            case "hasCeiling":
                uniforms.addInt(uniformName, WorldInfoUniforms::hasCeiling);
                break;
            case "hasSkylight":
                uniforms.addInt(uniformName, WorldInfoUniforms::hasSkylight);
                break;
            case "ambientLight":
                uniforms.addFloat(uniformName, WorldInfoUniforms::getAmbientLight);
                break;
            case "centerDepthSmooth":
                break;
            default:
                LOGGER.warn("Unknown uniform {} in program {}, it will not be updated.", uniformName, this.name);
        }
    }

    private ProgramUniforms.FloatSupplier sharedFrameSmoothedOrFallback(float halfLifeUp,
                                                                        float halfLifeDown,
                                                                        net.oculus.uniforms.FloatSupplier raw,
                                                                        ProgramUniforms.FloatSupplier fallback) {
        if (frameUpdateNotifier == null) {
            return fallback;
        }

        SmoothedFloat smoothed = new SmoothedFloat(halfLifeUp, halfLifeDown, raw, frameUpdateNotifier);
        return smoothed::getAsFloat;
    }

    private float getWetnessHalfLife() {
        return packDirectives != null ? packDirectives.getWetnessHalfLife() : DEFAULT_WETNESS_HALF_LIFE;
    }

    private float getDrynessHalfLife() {
        return packDirectives != null ? packDirectives.getDrynessHalfLife() : DEFAULT_DRYNESS_HALF_LIFE;
    }

    private boolean isOldHandLight() {
        return packDirectives == null || packDirectives.isOldHandLight();
    }

    private void addFrameCounterUniform(String uniformName, int glType) {
        if (glType == GL11.GL_FLOAT) {
            uniforms.addFloat(uniformName, () -> (float) SystemTimeUniforms.COUNTER.getAsInt());
        } else {
            uniforms.addInt(uniformName, SystemTimeUniforms.COUNTER);
        }
    }

    private void addFrameMod8Uniform(String uniformName, int glType) {
        addFrameModUniform(uniformName, glType, 8);
    }

    private void addFrameModUniform(String uniformName, int glType, int modulus) {
        if (glType == GL11.GL_FLOAT) {
            uniforms.addFloat(uniformName, () -> (float) (SystemTimeUniforms.COUNTER.getAsInt() % modulus));
        } else {
            uniforms.addInt(uniformName, () -> SystemTimeUniforms.COUNTER.getAsInt() % modulus);
        }
    }

    private boolean usesWorldLevelSamplers() {
        return usesWorldLevelSamplers(name);
    }

    private static boolean usesWorldLevelSamplers(String programName) {
        String normalized = programName.toLowerCase(Locale.ROOT);
        return normalized.startsWith("gbuffers_")
            || normalized.equals("shadow")
            || normalized.startsWith("shadow_")
            || normalized.startsWith("shadow.");
    }

    private static Set<Integer> reservedTextureUnitsForProgram(String programName, Set<Integer> explicitReserved) {
        if (!usesWorldLevelSamplers(programName)) {
            return explicitReserved;
        }

        Set<Integer> reserved = new HashSet<>(WORLD_RESERVED_TEXTURE_UNITS);
        if (explicitReserved != null) {
            reserved.addAll(explicitReserved);
        }
        return reserved;
    }

    private static Set<Integer> textureUnitSet(int... units) {
        Set<Integer> set = new HashSet<>();
        for (int unit : units) {
            set.add(unit);
        }
        return Collections.unmodifiableSet(set);
    }

    private boolean shouldAutoBindSampler(String uniformName) {
        if (!usesWorldLevelSamplers()) {
            return true;
        }

        if (isLowRenderTargetSampler(uniformName)) {
            return false;
        }
        if ("gdepthtex".equals(uniformName) || "depthtex2".equals(uniformName)) {
            return false;
        }
        if (isShadowLevelProgram() && isWorldDepthSampler(uniformName)) {
            return false;
        }
        return true;
    }

    private boolean isShadowLevelProgram() {
        return isShadowLevelProgram(name);
    }

    private static boolean isShadowLevelProgram(String programName) {
        String normalized = programName.toLowerCase(Locale.ROOT);
        return normalized.equals("shadow")
            || normalized.startsWith("shadow_")
            || normalized.startsWith("shadow.");
    }

    private static boolean isLowRenderTargetSampler(String samplerName) {
        return "gcolor".equals(samplerName)
            || "gdepth".equals(samplerName)
            || "gnormal".equals(samplerName)
            || "composite".equals(samplerName)
            || "gaux0".equals(samplerName)
            || isColorTextureIndexInRange(samplerName, 0, 3);
    }

    private static boolean isWorldDepthSampler(String samplerName) {
        return "depthtex0".equals(samplerName)
            || "depthtex1".equals(samplerName);
    }

    private static boolean isColorTextureIndexInRange(String samplerName, int min, int max) {
        if (!samplerName.startsWith("colortex")) {
            return false;
        }
        try {
            int index = Integer.parseInt(samplerName.substring("colortex".length()));
            return index >= min && index <= max;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static boolean isWorldAlbedoSampler(String uniformName) {
        return "tex".equals(uniformName)
            || "texture".equals(uniformName)
            || "gtexture".equals(uniformName);
    }

    private static boolean isUnavailableAlbedoFallbackSampler(String uniformName) {
        return isWorldAlbedoSampler(uniformName)
            || "gcolor".equals(uniformName)
            || "colortex0".equals(uniformName);
    }

    private boolean hasTextureInput() {
        return availability == null || availability.texture;
    }

    private boolean hasLightmapInput() {
        return availability == null || availability.lightmap;
    }

    private static int getLightmapTextureUnit() {
        return Math.max(0, OpenGlHelper.lightmapTexUnit - OpenGlHelper.defaultTexUnit);
    }

    private static int getOverlayTextureUnit() {
        return Math.max(0, OpenGlHelper.GL_TEXTURE2 - OpenGlHelper.defaultTexUnit);
    }

    private static boolean isSamplerType(int glType) {
        switch (glType) {
            case GL20.GL_SAMPLER_1D:
            case GL20.GL_SAMPLER_2D:
            case GL20.GL_SAMPLER_3D:
            case GL20.GL_SAMPLER_CUBE:
            case GL20.GL_SAMPLER_1D_SHADOW:
            case GL20.GL_SAMPLER_2D_SHADOW:
            case GL30.GL_SAMPLER_BUFFER:
            case GL30.GL_SAMPLER_CUBE_SHADOW:
            case GL30.GL_INT_SAMPLER_1D:
            case GL30.GL_INT_SAMPLER_2D:
            case GL30.GL_INT_SAMPLER_3D:
            case GL30.GL_INT_SAMPLER_CUBE:
            case GL30.GL_INT_SAMPLER_2D_RECT:
            case GL30.GL_INT_SAMPLER_1D_ARRAY:
            case GL30.GL_INT_SAMPLER_2D_ARRAY:
            case GL30.GL_INT_SAMPLER_BUFFER:
            case GL30.GL_UNSIGNED_INT_SAMPLER_1D:
            case GL30.GL_UNSIGNED_INT_SAMPLER_2D:
            case GL30.GL_UNSIGNED_INT_SAMPLER_3D:
            case GL30.GL_UNSIGNED_INT_SAMPLER_CUBE:
            case GL30.GL_UNSIGNED_INT_SAMPLER_2D_RECT:
            case GL30.GL_UNSIGNED_INT_SAMPLER_1D_ARRAY:
            case GL30.GL_UNSIGNED_INT_SAMPLER_2D_ARRAY:
            case GL30.GL_UNSIGNED_INT_SAMPLER_BUFFER:
            case GL30.GL_SAMPLER_1D_ARRAY:
            case GL30.GL_SAMPLER_2D_ARRAY:
            case GL30.GL_SAMPLER_1D_ARRAY_SHADOW:
            case GL30.GL_SAMPLER_2D_ARRAY_SHADOW:
            case GL31.GL_SAMPLER_2D_RECT:
            case GL31.GL_SAMPLER_2D_RECT_SHADOW:
            case GL32.GL_SAMPLER_2D_MULTISAMPLE:
            case GL32.GL_INT_SAMPLER_2D_MULTISAMPLE:
            case GL32.GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE:
            case GL32.GL_SAMPLER_2D_MULTISAMPLE_ARRAY:
            case GL32.GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY:
            case GL32.GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY:
                return true;
            default:
                return false;
        }
    }

    private static boolean isImageType(int glType) {
        switch (glType) {
            case GL42.GL_IMAGE_1D:
            case GL42.GL_IMAGE_2D:
            case GL42.GL_IMAGE_3D:
            case GL42.GL_IMAGE_2D_RECT:
            case GL42.GL_IMAGE_CUBE:
            case GL42.GL_IMAGE_BUFFER:
            case GL42.GL_IMAGE_1D_ARRAY:
            case GL42.GL_IMAGE_2D_ARRAY:
            case GL42.GL_IMAGE_CUBE_MAP_ARRAY:
            case GL42.GL_IMAGE_2D_MULTISAMPLE:
            case GL42.GL_IMAGE_2D_MULTISAMPLE_ARRAY:
            case GL42.GL_INT_IMAGE_1D:
            case GL42.GL_INT_IMAGE_2D:
            case GL42.GL_INT_IMAGE_3D:
            case GL42.GL_INT_IMAGE_2D_RECT:
            case GL42.GL_INT_IMAGE_CUBE:
            case GL42.GL_INT_IMAGE_BUFFER:
            case GL42.GL_INT_IMAGE_1D_ARRAY:
            case GL42.GL_INT_IMAGE_2D_ARRAY:
            case GL42.GL_INT_IMAGE_CUBE_MAP_ARRAY:
            case GL42.GL_INT_IMAGE_2D_MULTISAMPLE:
            case GL42.GL_INT_IMAGE_2D_MULTISAMPLE_ARRAY:
            case GL42.GL_UNSIGNED_INT_IMAGE_1D:
            case GL42.GL_UNSIGNED_INT_IMAGE_2D:
            case GL42.GL_UNSIGNED_INT_IMAGE_3D:
            case GL42.GL_UNSIGNED_INT_IMAGE_2D_RECT:
            case GL42.GL_UNSIGNED_INT_IMAGE_CUBE:
            case GL42.GL_UNSIGNED_INT_IMAGE_BUFFER:
            case GL42.GL_UNSIGNED_INT_IMAGE_1D_ARRAY:
            case GL42.GL_UNSIGNED_INT_IMAGE_2D_ARRAY:
            case GL42.GL_UNSIGNED_INT_IMAGE_CUBE_MAP_ARRAY:
            case GL42.GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE:
            case GL42.GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY:
                return true;
            default:
                return false;
        }
    }

    public Program build() {
        try {
            return new Program(name, program, uniforms.build(), samplers.build(), images.build(), ownsProgramHandle);
        } catch (RuntimeException | Error exception) {
            deleteFailedProgramAfterBuildFailure(exception, "program wrapper construction");
            throw exception;
        }
    }

    public ComputeProgram buildCompute() {
        try {
            return new ComputeProgram(name, program, uniforms.build(), samplers.build(), images.build());
        } catch (RuntimeException | Error exception) {
            deleteFailedProgramAfterBuildFailure(exception, "compute wrapper construction");
            throw exception;
        }
    }

    // --- Sampler helpers --------------------------------------------------

    public void addExternalSampler(int textureUnit, String... names) {
        samplers.addExternalSampler(textureUnit, names);
    }

    public boolean hasSampler(String name) {
        return samplers.hasSampler(name);
    }

    public Set<String> getActiveSamplerUniformNames() {
        if (activeSamplerUniformNames == null || activeSamplerUniformNames.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(activeSamplerUniformNames);
    }

    public int getActiveSamplerUniformType(String name) {
        if (name == null || activeSamplerUniformTypes == null) {
            return 0;
        }
        Integer type = activeSamplerUniformTypes.get(name);
        return type == null ? 0 : type.intValue();
    }

    public boolean addDefaultSampler(IntSupplier sampler, String... names) {
        return samplers.addDefaultSampler(sampler, names);
    }

    public boolean addDefaultSampler(TextureBinding binding, String... names) {
        return samplers.addDefaultSampler(binding, names);
    }

    public boolean addDynamicSampler(IntSupplier sampler, String... names) {
        return samplers.addDynamicSampler(sampler, names);
    }

    public boolean addDynamicSampler(IntSupplier sampler, ValueUpdateNotifier notifier, String... names) {
        return samplers.addDynamicSampler(sampler, notifier, names);
    }

    public void overrideSamplerBinding(String samplerName, TextureBinding binding) {
        samplers.overrideBinding(samplerName, binding);
    }

    // --- Image helpers ----------------------------------------------------

    @Override
    public boolean hasImage(String name) {
        return images.hasImage(name);
    }

    @Override
    public Set<String> getActiveImageNames() {
        if (activeImageUniformNames == null || activeImageUniformNames.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(activeImageUniformNames);
    }

    @Override
    public void addTextureImage(IntSupplier textureId, InternalTextureFormat internalFormat, String name) {
        images.addTextureImage(textureId, internalFormat, name);
    }

    @Override
    public String toString() {
        return "ProgramBuilder{" +
                "name='" + name + '\'' +
                ", program=" + program +
                '}';
    }
}
