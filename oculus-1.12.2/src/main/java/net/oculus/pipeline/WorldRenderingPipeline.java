package net.oculus.pipeline;

import java.util.List;
import java.util.OptionalInt;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.FramebufferManager;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockRenderLayer;
import net.oculus.shaderpack.CloudSetting;
import net.oculus.shaderpack.ParticleRenderingOrder;
import net.oculus.uniforms.FrameUpdateNotifier;

public interface WorldRenderingPipeline {
    FrameUpdateNotifier NOOP_FRAME_UPDATE_NOTIFIER = new FrameUpdateNotifier();

    default void beginLevelRendering() {
    }

    default void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks) {
    }

    default void addDebugText(List<String> messages) {
    }

    default OptionalInt getForcedShadowRenderDistanceChunksForDisplay() {
        return OptionalInt.empty();
    }

    default WorldRenderingPhase getPhase() {
        return WorldRenderingPhase.NONE;
    }

    default void beginSodiumTerrainRendering() {
    }

    default void endSodiumTerrainRendering() {
    }

    default void setOverridePhase(WorldRenderingPhase phase) {
    }

    default void setPhase(WorldRenderingPhase phase) {
    }

    default void setInputs(InputAvailability availability) {
    }

    default void setSpecialCondition(SpecialCondition special) {
    }

    default void syncProgram() {
    }

    default RenderTargetStateListener getRenderTargetStateListener() {
        return RenderTargetStateListener.NOP;
    }

    default int getCurrentNormalTexture() {
        return 0;
    }

    default int getCurrentSpecularTexture() {
        return 0;
    }

    default void resetPbrTextureBindings() {
    }

    default void onBindTexture(int id) {
    }

    default void beginHand() {
    }

    default void beginTranslucents() {
    }

    default void finalizeLevelRendering() {
    }

    default void destroy() {
    }

    default FramebufferManager getFramebufferManager() {
        return null;
    }

    default SodiumTerrainPipeline getSodiumTerrainPipeline() {
        return SodiumTerrainPipeline.NULL_PIPELINE;
    }

    default FrameUpdateNotifier getFrameUpdateNotifier() {
        return NOOP_FRAME_UPDATE_NOTIFIER;
    }

    default boolean shouldDisableVanillaEntityShadows() {
        return false;
    }

    default boolean shouldDisableDirectionalShading() {
        return false;
    }

    default boolean shouldDisableFrustumCulling() {
        return false;
    }

    default boolean shouldDisableOcclusionCulling() {
        return false;
    }

    default boolean isRenderingShadowPass() {
        return false;
    }

    default boolean shouldRenderTerrainBackFaces(BlockRenderLayer layer) {
        return false;
    }

    default CloudSetting getCloudSetting() {
        return CloudSetting.DEFAULT;
    }

    default boolean shouldRenderUnderwaterOverlay() {
        return false;
    }

    default boolean shouldRenderVignette() {
        return false;
    }

    default boolean shouldRenderSun() {
        return true;
    }

    default boolean shouldRenderMoon() {
        return true;
    }

    default boolean shouldWriteRainAndSnowToDepthBuffer() {
        return false;
    }

    default boolean shouldWriteBeaconBeamToDepthBuffer() {
        return true;
    }

    default boolean shouldRenderParticlesBeforeDeferred() {
        return false;
    }

    default ParticleRenderingOrder getParticleRenderingOrder() {
        return ParticleRenderingOrder.BEFORE;
    }

    default boolean allowConcurrentCompute() {
        return false;
    }

    default float getSunPathRotation() {
        return 0.0F;
    }

    default void beginWorldRendering(float partialTicks) {
        beginLevelRendering();
    }

    default void afterCameraSetup(float partialTicks) {
    }

    default void endWorldRendering() {
        finalizeLevelRendering();
    }
}
