package net.oculus.mixin.pipeline;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.nio.IntBuffer;
import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkCameraContext;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderContainer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import me.jellysquid.mods.sodium.client.render.chunk.cull.ChunkFaceFlags;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderListIterator;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPass;
import me.jellysquid.mods.sodium.client.render.chunk.passes.BlockRenderPassManager;
import me.jellysquid.mods.sodium.client.util.math.FrustumExtended;
import net.oculus.compat.relictium.OculusRelictiumChunkRenderBackendExt;
import net.oculus.compat.relictium.OculusRelictiumSwappableChunkRenderManager;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.ShaderWorldRenderingPipeline;
import net.oculus.pipeline.shadow.ShadowRenderingState;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ChunkRenderManager.class, remap = false)
public abstract class RelictiumChunkRenderManagerMixin implements OculusRelictiumSwappableChunkRenderManager {
    @Shadow
    @Final
    private ObjectArrayFIFOQueue<ChunkRenderContainer<?>> importantRebuildQueue;

    @Shadow
    @Final
    private ObjectArrayFIFOQueue<ChunkRenderContainer<?>> rebuildQueue;

    @Shadow
    @Final
    @Mutable
    private ChunkRenderList<?>[] chunkRenderLists;

    @Shadow
    @Final
    @Mutable
    private ObjectList<ChunkRenderContainer<?>> tickableChunks;

    @Shadow
    @Final
    @Mutable
    private ObjectList<TileEntity> visibleBlockEntities;

    @Shadow
    private boolean dirty;

    @Shadow
    private int visibleChunkCount;

    @Unique
    private ChunkRenderList<?>[] oculus$chunkRenderListsSwap;

    @Unique
    private ObjectList<ChunkRenderContainer<?>> oculus$tickableChunksSwap;

    @Unique
    private ObjectList<TileEntity> oculus$visibleBlockEntitiesSwap;

    @Unique
    private int oculus$visibleChunkCountSwap;

    @Unique
    private boolean oculus$dirtySwap;

    @Unique
    private static final ObjectArrayFIFOQueue<?> OCULUS_EMPTY_QUEUE = new ObjectArrayFIFOQueue<Object>();

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void oculus$initShadowVisibilitySwap(SodiumWorldRenderer renderer,
                                                 ChunkRenderBackend<?> backend,
                                                 BlockRenderPassManager renderPassManager,
                                                 WorldClient world,
                                                 int renderDistance,
                                                 CallbackInfo ci) {
        this.oculus$chunkRenderListsSwap = new ChunkRenderList[BlockRenderPass.COUNT];
        this.oculus$tickableChunksSwap = new ObjectArrayList<ChunkRenderContainer<?>>();
        this.oculus$visibleBlockEntitiesSwap = new ObjectArrayList<TileEntity>();

        for (int i = 0; i < this.oculus$chunkRenderListsSwap.length; i++) {
            this.oculus$chunkRenderListsSwap[i] = new ChunkRenderList();
        }

        this.oculus$dirtySwap = true;
    }

    @Override
    public void oculus$swapVisibilityState() {
        ChunkRenderList<?>[] chunkRenderListsTmp = this.chunkRenderLists;
        this.chunkRenderLists = this.oculus$chunkRenderListsSwap;
        this.oculus$chunkRenderListsSwap = chunkRenderListsTmp;

        ObjectList<ChunkRenderContainer<?>> tickableChunksTmp = this.tickableChunks;
        this.tickableChunks = this.oculus$tickableChunksSwap;
        this.oculus$tickableChunksSwap = tickableChunksTmp;

        ObjectList<TileEntity> visibleBlockEntitiesTmp = this.visibleBlockEntities;
        this.visibleBlockEntities = this.oculus$visibleBlockEntitiesSwap;
        this.oculus$visibleBlockEntitiesSwap = visibleBlockEntitiesTmp;

        int visibleChunkCountTmp = this.visibleChunkCount;
        this.visibleChunkCount = this.oculus$visibleChunkCountSwap;
        this.oculus$visibleChunkCountSwap = visibleChunkCountTmp;

        boolean dirtyTmp = this.dirty;
        this.dirty = this.oculus$dirtySwap;
        this.oculus$dirtySwap = dirtyTmp;
    }

    @Redirect(
        method = "addChunk",
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderContainer;canRebuild()Z"
        ),
        remap = false
    )
    private boolean oculus$skipRebuildEnqueueingInShadowPass(ChunkRenderContainer<?> render) {
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return false;
        }

        return render.canRebuild();
    }

    @Inject(method = "computeVisibleFaces", at = @At("HEAD"), cancellable = true, remap = false)
    private void oculus$disableBlockFaceCullingInShadowPass(ChunkRenderContainer<?> render,
                                                            CallbackInfoReturnable<Integer> cir) {
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            cir.setReturnValue(ChunkFaceFlags.ALL);
        }
    }

    @Redirect(
        method = "setup(F)V",
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/gui/SodiumGameOptions$AdvancedSettings;useFogOcclusion:Z"
        ),
        remap = false
    )
    private boolean oculus$disableFogOcclusionWhenShaderPackIsActive(SodiumGameOptions.AdvancedSettings settings) {
        if (PipelineManager.INSTANCE.getPipelineNullable() instanceof ShaderWorldRenderingPipeline) {
            return false;
        }

        return settings.useFogOcclusion;
    }

    @Redirect(
        method = "reset()V",
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderManager;rebuildQueue:Lit/unimi/dsi/fastutil/objects/ObjectArrayFIFOQueue;"
        ),
        remap = false
    )
    private ObjectArrayFIFOQueue<?> oculus$preserveNormalRebuildQueueInShadowPass(ChunkRenderManager<?> manager) {
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return OCULUS_EMPTY_QUEUE;
        }

        return this.rebuildQueue;
    }

    @Redirect(
        method = "reset()V",
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderManager;importantRebuildQueue:Lit/unimi/dsi/fastutil/objects/ObjectArrayFIFOQueue;"
        ),
        remap = false
    )
    private ObjectArrayFIFOQueue<?> oculus$preserveNormalImportantRebuildQueueInShadowPass(ChunkRenderManager<?> manager) {
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            return OCULUS_EMPTY_QUEUE;
        }

        return this.importantRebuildQueue;
    }

    @Inject(method = "updateChunks()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void oculus$preventChunkRebuildsInShadowPass(CallbackInfo ci) {
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            ci.cancel();
        }
    }

    @Inject(
        method = "update(FLme/jellysquid/mods/sodium/client/util/math/FrustumExtended;IZ)V",
        at = @At("RETURN"),
        remap = false
    )
    private void oculus$logShadowVisibleChunkCount(float partialTicks,
                                                   FrustumExtended frustum,
                                                   int frame,
                                                   boolean spectator,
                                                   CallbackInfo ci) {
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            net.oculus.client.OculusRuntimeValidation.logRelictiumShadowVisibility(
                "shadow visible chunks=" + this.visibleChunkCount);
        }
    }

    @Redirect(
        method = "renderLayer",
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderBackend;begin()V"
        ),
        remap = false
    )
    private void oculus$beginBackendForPass(ChunkRenderBackend<?> backend,
                                            BlockRenderPass pass,
                                            double x,
                                            double y,
                                            double z) {
        if (backend instanceof OculusRelictiumChunkRenderBackendExt) {
            ((OculusRelictiumChunkRenderBackendExt) backend).oculus$begin(pass);
            return;
        }

        backend.begin();
    }

    @Redirect(
        method = "renderLayer",
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderBackend;render(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;Lme/jellysquid/mods/sodium/client/render/chunk/lists/ChunkRenderListIterator;Lme/jellysquid/mods/sodium/client/render/chunk/ChunkCameraContext;)V"
        ),
        remap = false
    )
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void oculus$renderBackendWithScopeCleanup(ChunkRenderBackend<?> backend,
                                                      CommandList commandList,
                                                      ChunkRenderListIterator<?> iterator,
                                                      ChunkCameraContext cameraContext,
                                                      BlockRenderPass pass,
                                                      double x,
                                                      double y,
                                                      double z) {
        oculus$logShadowTerrainDraw(iterator, cameraContext, pass, x, y, z);
        try {
            ((ChunkRenderBackend) backend).render(commandList, (ChunkRenderListIterator) iterator, cameraContext);
        } catch (RuntimeException exception) {
            oculus$endBackendAfterRenderFailure(backend, exception);
            throw exception;
        } catch (Error error) {
            oculus$endBackendAfterRenderFailure(backend, error);
            throw error;
        }
    }

    @Unique
    private static void oculus$logShadowTerrainDraw(ChunkRenderListIterator<?> iterator,
                                                    ChunkCameraContext cameraContext,
                                                    BlockRenderPass pass,
                                                    double x,
                                                    double y,
                                                    double z) {
        if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered() || !OculusRuntimeValidation.isEnabled()) {
            return;
        }

        boolean hasNext = iterator != null && iterator.hasNext();
        Object graphicsState = hasNext ? iterator.getGraphicsState() : null;
        int visibleFaces = hasNext ? iterator.getVisibleFaces() : 0;
        int chunkX = 0;
        int chunkY = 0;
        int chunkZ = 0;
        float modelOffsetX = Float.NaN;
        float modelOffsetY = Float.NaN;
        float modelOffsetZ = Float.NaN;
        if (graphicsState instanceof ChunkGraphicsState && cameraContext != null) {
            ChunkGraphicsState chunkGraphicsState = (ChunkGraphicsState) graphicsState;
            chunkX = chunkGraphicsState.getX();
            chunkY = chunkGraphicsState.getY();
            chunkZ = chunkGraphicsState.getZ();
            modelOffsetX = cameraContext.getChunkModelOffset(chunkX, cameraContext.blockOriginX, cameraContext.originX);
            modelOffsetY = cameraContext.getChunkModelOffset(chunkY, cameraContext.blockOriginY, cameraContext.originY);
            modelOffsetZ = cameraContext.getChunkModelOffset(chunkZ, cameraContext.blockOriginZ, cameraContext.originZ);
        }
        IntBuffer viewport = BufferUtils.createIntBuffer(16);
        GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);

        OculusRuntimeValidation.logRelictiumShadowTerrainDraw(
            String.valueOf(pass),
            hasNext,
            graphicsState == null ? "null" : graphicsState.getClass().getName(),
            visibleFaces,
            chunkX,
            chunkY,
            chunkZ,
            modelOffsetX,
            modelOffsetY,
            modelOffsetZ,
            x,
            y,
            z,
            OculusRenderSystem.getFramebufferBinding(),
            GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
            GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
            GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
            GL11.glIsEnabled(GL11.GL_BLEND),
            viewport.get(2),
            viewport.get(3));
    }

    @Redirect(
        method = "renderLayer",
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderBackend;end()V"
        ),
        remap = false
    )
    private void oculus$endBackendWithScopeCleanup(ChunkRenderBackend<?> backend,
                                                   BlockRenderPass pass,
                                                   double x,
                                                   double y,
                                                   double z) {
        oculus$endBackendWithCollectedScopeCleanup(backend);
    }

    private static void oculus$endBackendAfterRenderFailure(ChunkRenderBackend<?> backend, Throwable failure) {
        try {
            oculus$endBackendWithCollectedScopeCleanup(backend);
        } catch (RuntimeException | Error cleanupFailure) {
            oculus$suppressCleanupFailure(failure, cleanupFailure);
        }
    }

    private static void oculus$cleanupTerrainScope(ChunkRenderBackend<?> backend) {
        if (backend instanceof OculusRelictiumChunkRenderBackendExt) {
            ((OculusRelictiumChunkRenderBackendExt) backend).oculus$end();
        }
    }

    private static void oculus$endBackendWithCollectedScopeCleanup(ChunkRenderBackend<?> backend) {
        Throwable failure = null;

        try {
            backend.end();
        } catch (RuntimeException | Error exception) {
            failure = oculus$collectCleanupFailure(failure, exception);
        }

        try {
            oculus$cleanupTerrainScope(backend);
        } catch (RuntimeException | Error exception) {
            failure = oculus$collectCleanupFailure(failure, exception);
        }

        oculus$rethrowCleanupFailure(failure);
    }

    private static Throwable oculus$collectCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (failure == null) {
            return cleanupFailure;
        }

        oculus$suppressCleanupFailure(failure, cleanupFailure);
        return failure;
    }

    private static void oculus$suppressCleanupFailure(Throwable failure, Throwable cleanupFailure) {
        if (failure != cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static void oculus$rethrowCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }

        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }

        throw (Error) failure;
    }
}
