package net.oculus.mixin.pipeline;

import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.BlockRenderLayer;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.compat.relictium.OculusRelictiumSwappableChunkRenderManager;
import net.oculus.pipeline.shadow.ShadowRenderingState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SodiumWorldRenderer.class, remap = false)
public abstract class RelictiumSodiumWorldRendererShadowMixin {
    @Shadow
    private ChunkRenderManager<?> chunkRenderManager;

    @Shadow
    private double lastCameraX;

    @Shadow
    private double lastCameraY;

    @Shadow
    private double lastCameraZ;

    @Shadow
    private double lastCameraPitch;

    @Shadow
    private double lastCameraYaw;

    @Unique
    private boolean oculus$wasRenderingShadows;

    @Unique
    private double oculus$swapLastCameraX;

    @Unique
    private double oculus$swapLastCameraY;

    @Unique
    private double oculus$swapLastCameraZ;

    @Unique
    private double oculus$swapLastCameraPitch;

    @Unique
    private double oculus$swapLastCameraYaw;

    @Unique
    private void oculus$swapCachedCameraPositions() {
        double tmp;

        tmp = this.lastCameraX;
        this.lastCameraX = this.oculus$swapLastCameraX;
        this.oculus$swapLastCameraX = tmp;

        tmp = this.lastCameraY;
        this.lastCameraY = this.oculus$swapLastCameraY;
        this.oculus$swapLastCameraY = tmp;

        tmp = this.lastCameraZ;
        this.lastCameraZ = this.oculus$swapLastCameraZ;
        this.oculus$swapLastCameraZ = tmp;

        tmp = this.lastCameraPitch;
        this.lastCameraPitch = this.oculus$swapLastCameraPitch;
        this.oculus$swapLastCameraPitch = tmp;

        tmp = this.lastCameraYaw;
        this.lastCameraYaw = this.oculus$swapLastCameraYaw;
        this.oculus$swapLastCameraYaw = tmp;
    }

    @Unique
    private void oculus$ensureStateSwapped() {
        if (!this.oculus$wasRenderingShadows && ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            if (this.chunkRenderManager instanceof OculusRelictiumSwappableChunkRenderManager) {
                ((OculusRelictiumSwappableChunkRenderManager) this.chunkRenderManager).oculus$swapVisibilityState();
                oculus$swapCachedCameraPositions();
                OculusRuntimeValidation.logRelictiumShadowVisibility("swapped to shadow graph");
            }

            this.oculus$wasRenderingShadows = true;
        } else if (this.oculus$wasRenderingShadows && !ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            if (this.chunkRenderManager instanceof OculusRelictiumSwappableChunkRenderManager) {
                ((OculusRelictiumSwappableChunkRenderManager) this.chunkRenderManager).oculus$swapVisibilityState();
                oculus$swapCachedCameraPositions();
                OculusRuntimeValidation.logRelictiumShadowVisibility("restored camera graph");
            }

            this.oculus$wasRenderingShadows = false;
        }
    }

    @Inject(
        method = "scheduleTerrainUpdate()V",
        at = @At(
            value = "INVOKE",
            target = "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkRenderManager;markDirty()V"
        ),
        remap = false
    )
    private void oculus$ensureStateSwappedBeforeMarkDirty(CallbackInfo ci) {
        oculus$ensureStateSwapped();
    }

    @Inject(
        method = "updateChunks",
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/SodiumWorldRenderer;lastCameraX:D",
            ordinal = 0
        ),
        remap = false
    )
    private void oculus$ensureStateSwappedInUpdateChunks(Frustum frustum,
                                                         float partialTicks,
                                                         boolean forceVisibleGraphRebuild,
                                                         int frame,
                                                         boolean spectator,
                                                         CallbackInfo ci) {
        oculus$ensureStateSwapped();
    }

    @Redirect(
        method = "updateChunks",
        at = @At(
            value = "FIELD",
            target = "Lme/jellysquid/mods/sodium/client/render/SodiumWorldRenderer;lastCameraX:D",
            ordinal = 0
        ),
        remap = false
    )
    private double oculus$forceChunkGraphRebuildInShadowPass(SodiumWorldRenderer worldRenderer) {
        if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
            OculusRuntimeValidation.logRelictiumShadowVisibility("forced shadow graph rebuild");
            return Double.NaN;
        }

        return this.lastCameraX;
    }

    @Inject(method = "drawChunkLayer", at = @At("HEAD"), remap = false)
    private void oculus$beforeDrawChunkLayer(BlockRenderLayer layer,
                                             double x,
                                             double y,
                                             double z,
                                             CallbackInfo ci) {
        oculus$ensureStateSwapped();
    }
}
