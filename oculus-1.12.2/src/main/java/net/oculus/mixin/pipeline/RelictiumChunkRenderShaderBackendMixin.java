package net.oculus.mixin.pipeline;

import me.jellysquid.mods.sodium.client.gl.device.RenderDevice;
import me.jellysquid.mods.sodium.client.model.vertex.type.ChunkVertexType;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkProgram;
import me.jellysquid.mods.sodium.client.render.chunk.shader.ChunkRenderShaderBackend;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.compat.relictium.OculusRelictiumChunkProgramOverrides;
import net.oculus.compat.relictium.OculusRelictiumChunkRenderBackendExt;
import net.oculus.compat.relictium.OculusTerrainPass;
import net.oculus.gl.program.Program;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.SodiumTerrainPipeline;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChunkRenderShaderBackend.class, remap = false)
public abstract class RelictiumChunkRenderShaderBackendMixin implements OculusRelictiumChunkRenderBackendExt {
    @Shadow
    protected ChunkProgram activeProgram;

    @Unique
    private OculusRelictiumChunkProgramOverrides oculus$programOverrides;

    @Unique
    private RenderDevice oculus$device;

    @Unique
    private ChunkProgram oculus$overrideProgram;

    @Unique
    private boolean oculus$sodiumTerrainScope;

    @Unique
    private WorldRenderingPhase oculus$previousPhase = WorldRenderingPhase.NONE;

    @Unique
    private BlockRenderPass oculus$currentBlockRenderPass;

    @Unique
    private OculusTerrainPass oculus$currentTerrainPass;

    @Unique
    private WorldRenderingPhase oculus$currentPhase = WorldRenderingPhase.NONE;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void oculus$initProgramOverrides(ChunkVertexType vertexType, CallbackInfo ci) {
        this.oculus$programOverrides = new OculusRelictiumChunkProgramOverrides();
    }

    @Inject(method = "createShaders", at = @At("HEAD"), remap = false)
    private void oculus$createTerrainOverrides(RenderDevice device, CallbackInfo ci) {
        this.oculus$device = device;
        ensureProgramOverrides();

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        SodiumTerrainPipeline sodiumTerrainPipeline = pipeline == null
            ? SodiumTerrainPipeline.NULL_PIPELINE
            : pipeline.getSodiumTerrainPipeline();
        this.oculus$programOverrides.createShaders(sodiumTerrainPipeline, device);
    }

    @Override
    public void oculus$begin(BlockRenderPass pass) {
        ensureProgramOverrides();
        this.oculus$overrideProgram = this.oculus$programOverrides.getProgramOverride(this.oculus$device, pass);

        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        this.oculus$currentBlockRenderPass = pass;
        this.oculus$currentTerrainPass = pipeline != null && pipeline.isRenderingShadowPass()
            ? OculusTerrainPass.SHADOW
            : OculusTerrainPass.fromBlockRenderPass(pass);
        this.oculus$currentPhase = OculusTerrainPass.phaseFromBlockRenderPass(pass);

        try {
            if (pipeline != null) {
                this.oculus$previousPhase = pipeline.getPhase();
                this.oculus$sodiumTerrainScope = true;
                pipeline.setPhase(this.oculus$currentPhase);
                pipeline.beginSodiumTerrainRendering();
            }

            ((ChunkRenderShaderBackend<?>) (Object) this).begin();
        } catch (RuntimeException exception) {
            oculus$cleanupTerrainScopeAfterBeginFailure(exception);
            throw exception;
        } catch (Error error) {
            oculus$cleanupTerrainScopeAfterBeginFailure(error);
            throw error;
        }
    }

    @Override
    public void oculus$end() {
        oculus$endTerrainScope();
    }

    @Inject(
        method = "begin",
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/shader/ChunkRenderShaderBackend;activeProgram:Lme/jellysquid/mods/sodium/client/render/chunk/shader/ChunkProgram;",
            opcode = Opcodes.PUTFIELD,
            shift = At.Shift.AFTER
        ),
        remap = false
    )
    private void oculus$swapTerrainOverride(CallbackInfo ci) {
        if (this.oculus$overrideProgram != null) {
            this.activeProgram = this.oculus$overrideProgram;
            OculusRuntimeValidation.logRelictiumTerrainOverrideSelected(
                String.valueOf(this.oculus$currentTerrainPass),
                String.valueOf(this.oculus$currentBlockRenderPass),
                String.valueOf(this.oculus$currentPhase),
                String.valueOf(this.activeProgram.getName())
            );
        }
    }

    @Inject(method = "end", at = @At("RETURN"), remap = false)
    private void oculus$endTerrainOverride(CallbackInfo ci) {
        oculus$end();
    }

    @Inject(method = "delete", at = @At("HEAD"), remap = false)
    private void oculus$deleteTerrainOverrides(CallbackInfo ci) {
        if (this.oculus$programOverrides != null) {
            this.oculus$programOverrides.deleteShaders();
        }
    }

    @Unique
    private void ensureProgramOverrides() {
        if (this.oculus$programOverrides == null) {
            this.oculus$programOverrides = new OculusRelictiumChunkProgramOverrides();
        }
    }

    @Unique
    private void oculus$endTerrainScope() {
        boolean hadScope = this.oculus$sodiumTerrainScope;
        this.oculus$overrideProgram = null;
        this.oculus$currentBlockRenderPass = null;
        this.oculus$currentTerrainPass = null;
        this.oculus$currentPhase = WorldRenderingPhase.NONE;
        this.oculus$sodiumTerrainScope = false;

        if (!hadScope) {
            this.oculus$previousPhase = WorldRenderingPhase.NONE;
            return;
        }

        Throwable failure = null;
        try {
            Program.unbind();
        } catch (RuntimeException | Error exception) {
            failure = oculus$collectCleanupFailure(failure, exception);
        }

        WorldRenderingPipeline pipeline = null;
        try {
            pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        } catch (RuntimeException | Error exception) {
            failure = oculus$collectCleanupFailure(failure, exception);
        }

        if (pipeline != null) {
            try {
                pipeline.endSodiumTerrainRendering();
            } catch (RuntimeException | Error exception) {
                failure = oculus$collectCleanupFailure(failure, exception);
            }

            try {
                pipeline.setPhase(this.oculus$previousPhase);
            } catch (RuntimeException | Error exception) {
                failure = oculus$collectCleanupFailure(failure, exception);
            }
        }

        this.oculus$previousPhase = WorldRenderingPhase.NONE;
        oculus$rethrowCleanupFailure(failure);
    }

    @Unique
    private void oculus$cleanupTerrainScopeAfterBeginFailure(Throwable failure) {
        try {
            oculus$endTerrainScope();
        } catch (RuntimeException | Error cleanupFailure) {
            oculus$suppressCleanupFailure(failure, cleanupFailure);
        }
    }

    @Unique
    private static Throwable oculus$collectCleanupFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }

        oculus$suppressCleanupFailure(failure, exception);
        return failure;
    }

    @Unique
    private static void oculus$suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (cleanupFailure != null && cleanupFailure != failure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    @Unique
    private static void oculus$rethrowCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException(failure);
    }
}
