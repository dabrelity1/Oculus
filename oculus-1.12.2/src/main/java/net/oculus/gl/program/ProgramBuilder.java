package net.oculus.gl.program;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
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
import net.oculus.layer.GbufferPrograms;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.uniforms.SystemTimeUniforms;
import net.oculus.uniforms.CapturedRenderingState;
import net.oculus.uniforms.CelestialUniforms;
import net.oculus.uniforms.CompatibilityUniforms;
import net.oculus.uniforms.CustomUniforms;
import net.oculus.uniforms.GameplayUniforms;
import net.oculus.uniforms.ShadowUniforms;
import net.oculus.uniforms.SpecialEffectUniforms;
import net.oculus.uniforms.WorldInfoUniforms;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * A trimmed-down version of the Iris {@code ProgramBuilder}. It exposes the same entry
 * points but defers all real GL work so that the shader management pipeline can be
 * assembled step by step on 1.12.2.
 */
public final class ProgramBuilder implements ImageHolder {
    private static final Logger LOGGER = LogManager.getLogger(ProgramBuilder.class);

    static {
        GbufferPrograms.init();
    }

    private final String name;
    private final int program;

    private final ProgramUniforms.Builder uniforms;
    private final ProgramSamplers.Builder samplers;
    private final ProgramImages.Builder images;

    private ProgramBuilder(String name, int program) {
        this(name, program, SamplerOverrideMap.empty());
    }

    private ProgramBuilder(String name, int program, SamplerOverrideMap overrides) {
        this.name = name;
        this.program = program;
        this.uniforms = ProgramUniforms.builder(name, program);
        this.samplers = ProgramSamplers.builder(name, program, overrides);
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
        return begin(name, vertexSource, geometrySource, fragmentSource, SamplerOverrideMap.empty());
    }

    public static ProgramBuilder begin(String name, String vertexSource, String geometrySource, String fragmentSource,
                                       SamplerOverrideMap overrides) {
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

        return new ProgramBuilder(name, programId, overrides);
    }

    public static ProgramBuilder beginCompute(String name, String source) {
        return beginCompute(name, source, SamplerOverrideMap.empty());
    }

    public static ProgramBuilder beginCompute(String name, String source, SamplerOverrideMap overrides) {
        Objects.requireNonNull(name, "program name");

        GlShader compute = buildShader(ShaderType.COMPUTE, name + ".csh", source);
        int programId = ProgramCreator.create(name, compute);
        compute.destroy();

        return new ProgramBuilder(name, programId, overrides);
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
        CapturedRenderingState state = CapturedRenderingState.INSTANCE;

        if (isSamplerType(glType)) {
            samplers.addSampler(uniformName);
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
            case "fogColor":
            case "u_FogColor":
                if (glType == GL20.GL_FLOAT_VEC3) {
                    uniforms.addVec3(uniformName, state::getFogColor);
                } else {
                    uniforms.addVec4(uniformName, state::getFogColorVec4);
                }
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
                uniforms.addInt(uniformName, GameDataSuppliers.fogMode());
                break;
            case "fogDensity":
            case "u_FogDensity":
            case "iris_FogDensity":
                uniforms.addFloatSupplier(uniformName, GameDataSuppliers.fogDensity());
                break;
            case "iris_FogColor":
                uniforms.addVec4(uniformName, state::getFogColorVec4);
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
            case "shdFade":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getShadowFade);
                break;
            case "blindFactor":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getBlindFactor);
                break;
            case "rainStrengthS":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getRainStrengthS);
                break;
            case "rainStrengthShiningStars":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getRainStrengthShiningStars);
                break;
            case "rainStrengthS2":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getRainStrengthS2);
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
                uniforms.addFloat(uniformName, CompatibilityUniforms::getFrameTimeSmooth);
                break;
            case "eyeBrightnessM":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getEyeBrightnessMUniform);
                break;
            case "eyeBrightnessM2":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getEyeBrightnessM2);
                break;
            case "eyeBrightness":
                uniforms.addVec2(uniformName, GameplayUniforms::getEyeBrightness);
                break;
            case "eyeBrightnessSmooth":
                uniforms.addVec2(uniformName, GameplayUniforms::getEyeBrightnessSmooth);
                break;
            case "rainFactor":
                uniforms.addFloat(uniformName, CompatibilityUniforms::getRainFactor);
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
                uniforms.addFloat(uniformName, GameplayUniforms::getWetness);
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
            case "entityId":
                uniforms.addInt(uniformName, state::getCurrentEntity);
                break;
            case "blockEntityId":
                uniforms.addInt(uniformName, state::getCurrentBlockEntity);
                break;
            case "iris_ModelViewMatrix":
                uniforms.addMatrix4(uniformName, state::getGbufferModelView);
                break;
            case "iris_ProjectionMatrix":
            case "iris_ProjMat":
                uniforms.addMatrix4(uniformName, state::getGbufferProjection);
                break;
            case "u_ModelViewProjectionMatrix":
            case "iris_ModelViewProjectionMatrix":
                uniforms.addMatrix4(uniformName, state::getModelViewProjection);
                break;
            case "iris_NormalMatrix":
                uniforms.addMatrix4(uniformName, state::getModelViewInverse);
                break;
            case "iris_ModelOffset":
                uniforms.addFloat(uniformName, () -> 0.0F);
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
            default:
                LOGGER.warn("Unknown uniform {} in program {}, it will not be updated.", uniformName, this.name);
        }
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
        samplers.addExternalSampler(textureUnit, names);
    }

    public boolean hasSampler(String name) {
        return samplers.hasSampler(name);
    }

    public boolean addDefaultSampler(IntSupplier sampler, String... names) {
        return samplers.addDefaultSampler(sampler, names);
    }

    public boolean addDynamicSampler(IntSupplier sampler, String... names) {
        return samplers.addDynamicSampler(sampler, names);
    }

    public boolean addDynamicSampler(IntSupplier sampler, Runnable notifier, String... names) {
        return samplers.addDynamicSampler(sampler, notifier, names);
    }

    // --- Image helpers ----------------------------------------------------

    @Override
    public boolean hasImage(String name) {
        return images.hasImage(name);
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
