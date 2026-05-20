package net.oculus.compat.relictium;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Optional;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkShaderFogComponent;
import net.minecraft.util.ResourceLocation;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.program.Program;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.SodiumTerrainPipeline;
import net.oculus.pipeline.WorldRenderingPipeline;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class OculusRelictiumChunkProgramOverrides {
    private static final Logger LOGGER = LogManager.getLogger(OculusRelictiumChunkProgramOverrides.class);

    private final EnumMap<OculusTerrainPass, ChunkProgram> programs = new EnumMap<>(OculusTerrainPass.class);
    private int versionCounterForSodiumShaderReload = -1;
    private SodiumTerrainPipeline currentPipeline;
    private RenderDevice currentDevice;

    public void createShaders(SodiumTerrainPipeline sodiumTerrainPipeline, RenderDevice device) {
        rebuildShaders(sodiumTerrainPipeline, device,
            PipelineManager.INSTANCE.getVersionCounterForSodiumShaderReload());
    }

    public ChunkProgram getProgramOverride(RenderDevice device, BlockRenderPass pass) {
        WorldRenderingPipeline worldPipeline = PipelineManager.INSTANCE.getPipelineNullable();
        SodiumTerrainPipeline sodiumTerrainPipeline = worldPipeline == null
            ? null
            : worldPipeline.getSodiumTerrainPipeline();
        int version = PipelineManager.INSTANCE.getVersionCounterForSodiumShaderReload();

        if (versionCounterForSodiumShaderReload != version
            || currentPipeline != sodiumTerrainPipeline
            || currentDevice != device) {
            rebuildShaders(sodiumTerrainPipeline, device, version);
        }

        if (sodiumTerrainPipeline == null || !sodiumTerrainPipeline.isInitialized()) {
            OculusRuntimeValidation.logRelictiumTerrainOverrideLookup(
                "NONE",
                String.valueOf(pass),
                "pipeline not initialized"
            );
            return null;
        }

        OculusTerrainPass terrainPass;
        if (worldPipeline != null && worldPipeline.isRenderingShadowPass()) {
            if (!sodiumTerrainPipeline.hasShadowPass()) {
                throw new IllegalStateException("Shadow program requested, but the pack does not have a shadow pass?");
            }
            terrainPass = OculusTerrainPass.SHADOW;
        } else {
            terrainPass = OculusTerrainPass.fromBlockRenderPass(pass);
        }

        ChunkProgram program = programs.get(terrainPass);
        if (terrainPass == OculusTerrainPass.SHADOW && program == null) {
            throw new IllegalStateException("Shadow program requested, but the Relictium shadow override program "
                + "was not created. Check the shader compile log for the original shadow override failure.");
        }

        OculusRuntimeValidation.logRelictiumTerrainOverrideLookup(
            String.valueOf(terrainPass),
            String.valueOf(pass),
            program == null ? "missing override program" : "available " + program.getName()
        );
        return program;
    }

    public void deleteShaders() {
        try {
            for (ChunkProgram program : programs.values()) {
                if (program != null) {
                    try {
                        program.delete();
                    } catch (RuntimeException | Error exception) {
                        LOGGER.warn("Failed to delete Relictium terrain override program {}", program.getName(), exception);
                    }
                }
            }
        } finally {
            programs.clear();
            versionCounterForSodiumShaderReload = -1;
            currentPipeline = null;
            currentDevice = null;
        }
    }

    private void rebuildShaders(SodiumTerrainPipeline sodiumTerrainPipeline, RenderDevice device, int version) {
        deleteShaders();
        versionCounterForSodiumShaderReload = version;
        currentPipeline = sodiumTerrainPipeline;
        currentDevice = device;

        if (sodiumTerrainPipeline == null || !sodiumTerrainPipeline.isInitialized() || device == null) {
            return;
        }

        try {
            for (OculusTerrainPass pass : OculusTerrainPass.values()) {
                if (pass == OculusTerrainPass.SHADOW && !sodiumTerrainPipeline.hasShadowPass()) {
                    programs.put(pass, null);
                    continue;
                }

                programs.put(pass, createShader(device, pass, sodiumTerrainPipeline));
            }
        } catch (RuntimeException exception) {
            cleanupAfterRebuildFailure(exception);
            throw exception;
        } catch (Error error) {
            cleanupAfterRebuildFailure(error);
            throw error;
        }
    }

    private ChunkProgram createShader(RenderDevice device, OculusTerrainPass pass, SodiumTerrainPipeline pipeline) {
        String vertexSource = getVertexSource(pass, pipeline).orElse(null);
        String geometrySource = getGeometrySource(pass, pipeline).orElse(null);
        String fragmentSource = getFragmentSource(pass, pipeline).orElse(null);

        if (vertexSource == null || fragmentSource == null) {
            return null;
        }

        String programName = getProgramName(pass, pipeline).orElse(pass.getProgramName() + "_sodium");
        int handle = 0;
        Program bindings = null;
        ChunkProgram program = null;
        try {
            handle = OculusRelictiumProgramLinker.link(programName, vertexSource, geometrySource, fragmentSource);
            bindings = pipeline.buildProgramBindings(programName, handle, pass == OculusTerrainPass.SHADOW);
            ResourceLocation location = new ResourceLocation("oculus",
                "relictium-terrain-" + pass.name().toLowerCase(Locale.ROOT));
            program = new OculusRelictiumChunkProgram(device, location, handle,
                ChunkShaderFogComponent.None::new, bindings);
            LOGGER.info("Created Relictium terrain override {} from shader program {}", pass, programName);
            return program;
        } catch (RuntimeException exception) {
            cleanupFailedShader(program, bindings, handle, pass, exception);
            LOGGER.error("Failed to create Relictium terrain override for {}", pass, exception);
            return null;
        } catch (Error error) {
            cleanupFailedShader(program, bindings, handle, pass, error);
            throw error;
        }
    }

    private void cleanupAfterRebuildFailure(Throwable failure) {
        try {
            deleteShaders();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
        }
    }

    private static void cleanupFailedShader(ChunkProgram program,
                                            Program bindings,
                                            int handle,
                                            OculusTerrainPass pass,
                                            Throwable failure) {
        if (program != null) {
            try {
                program.delete();
            } catch (RuntimeException | Error cleanupFailure) {
                suppressCleanupFailure(failure, cleanupFailure);
                LOGGER.warn("Failed to delete constructed Relictium terrain override program {} after creation failed",
                    program.getName(), cleanupFailure);
            }
            return;
        }

        destroyFailedBindings(bindings, pass, failure);
        deleteFailedProgram(handle, pass, failure);
    }

    private static void destroyFailedBindings(Program bindings, OculusTerrainPass pass, Throwable failure) {
        if (bindings == null) {
            return;
        }

        try {
            bindings.destroy();
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
            LOGGER.warn("Failed to destroy Oculus binding wrapper after Relictium terrain override {} creation failed",
                pass, cleanupFailure);
        }
    }

    private static void deleteFailedProgram(int handle, OculusTerrainPass pass, Throwable failure) {
        if (handle == 0) {
            return;
        }

        try {
            OculusRenderSystem.glDeleteProgram(handle);
        } catch (RuntimeException | Error cleanupFailure) {
            suppressCleanupFailure(failure, cleanupFailure);
            LOGGER.warn("Failed to delete Relictium terrain override program handle {} after {} creation failed",
                handle, pass, cleanupFailure);
        }
    }

    private static void suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static Optional<String> getProgramName(OculusTerrainPass pass, SodiumTerrainPipeline pipeline) {
        switch (pass) {
            case SHADOW:
                return pipeline.getShadowProgramName();
            case GBUFFER_SOLID:
                return pipeline.getTerrainProgramName();
            case GBUFFER_TRANSLUCENT:
                return pipeline.getTranslucentProgramName();
            default:
                return Optional.empty();
        }
    }

    private static Optional<String> getVertexSource(OculusTerrainPass pass, SodiumTerrainPipeline pipeline) {
        switch (pass) {
            case SHADOW:
                return pipeline.getShadowVertexShaderSource();
            case GBUFFER_SOLID:
                return pipeline.getTerrainVertexShaderSource();
            case GBUFFER_TRANSLUCENT:
                return pipeline.getTranslucentVertexShaderSource();
            default:
                return Optional.empty();
        }
    }

    private static Optional<String> getGeometrySource(OculusTerrainPass pass, SodiumTerrainPipeline pipeline) {
        switch (pass) {
            case SHADOW:
                return pipeline.getShadowGeometryShaderSource();
            case GBUFFER_SOLID:
                return pipeline.getTerrainGeometryShaderSource();
            case GBUFFER_TRANSLUCENT:
                return pipeline.getTranslucentGeometryShaderSource();
            default:
                return Optional.empty();
        }
    }

    private static Optional<String> getFragmentSource(OculusTerrainPass pass, SodiumTerrainPipeline pipeline) {
        switch (pass) {
            case SHADOW:
                return pipeline.getShadowFragmentShaderSource();
            case GBUFFER_SOLID:
                return pipeline.getTerrainFragmentShaderSource();
            case GBUFFER_TRANSLUCENT:
                return pipeline.getTranslucentFragmentShaderSource();
            default:
                return Optional.empty();
        }
    }
}
