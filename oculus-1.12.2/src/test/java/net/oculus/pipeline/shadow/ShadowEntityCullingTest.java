package net.oculus.pipeline.shadow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.util.math.AxisAlignedBB;
import net.oculus.gl.state.MatrixMath;
import org.junit.Test;

public class ShadowEntityCullingTest {
    @Test
    public void entityShadowDistanceMulOneReusesTerrainCamera() {
        assertTrue(ShadowRenderer.usesTerrainCameraForEntityShadows(1.0F));
    }

    @Test
    public void negativeEntityShadowDistanceMulReusesTerrainCamera() {
        assertTrue(ShadowRenderer.usesTerrainCameraForEntityShadows(-1.0F));
    }

    @Test
    public void positiveEntityShadowDistanceMulCreatesEntitySpecificCamera() {
        assertFalse(ShadowRenderer.usesTerrainCameraForEntityShadows(0.125F));
    }

    @Test
    public void entityShadowDistanceMulScalesTerrainRenderMultiplier() {
        assertEquals(0.125F, ShadowRenderer.entityShadowRenderMultiplier(1.0F, 0.125F), 0.0F);
        assertEquals(0.25F, ShadowRenderer.entityShadowRenderMultiplier(2.0F, 0.125F), 0.0F);
    }

    @Test
    public void negativeShadowDistanceRenderMulUsesReferenceDefaultUserDistance() {
        assertEquals(32.0D * 16.0D, ShadowRenderer.shadowRenderDistanceBlocks(160.0F, -1.0F), 0.0D);
    }

    @Test
    public void negativeShadowDistanceRenderMulUsesConfiguredUserDistance() {
        assertEquals(12.0D * 16.0D,
            ShadowRenderer.shadowRenderDistanceBlocks(160.0F, -1.0F, 12), 0.0D);
    }

    @Test
    public void defaultShadowDistanceRenderMulSkipsWhenUserDistanceIsZeroLikeReference() {
        assertTrue(ShadowRenderer.shouldSkipShadowRenderingForDistance(160.0F, -1.0F, false, 0));
        assertFalse(ShadowRenderer.shouldSkipShadowRenderingForDistance(160.0F, -1.0F, false, 32));
    }

    @Test
    public void explicitNegativeShadowDistanceRenderMulDoesNotUseUserDistanceForRenderSkip() {
        assertFalse(ShadowRenderer.shouldSkipShadowRenderingForDistance(160.0F, -1.0F, true, 0));
    }

    @Test
    public void enabledCullingZeroDistanceCanCullAllShadowGeometryLikeReference() {
        ICamera camera = ShadowCullingCameras.cullEverything();
        AxisAlignedBB box = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

        assertTrue(camera instanceof Frustum);
        assertFalse(camera.isBoundingBoxInFrustum(box));
        camera.setPosition(100.0D, 64.0D, -100.0D);
        assertFalse(camera.isBoundingBoxInFrustum(box));
    }

    @Test
    public void explicitForcedZeroShadowDistanceSkipsLikeReferenceDisplayOverride() {
        assertTrue(ShadowRenderer.shouldSkipShadowRenderingForDistance(160.0F, 0.0F, true, 32));
    }

    @Test
    public void negativeShadowDistanceRenderMulDisablesDistanceCullingWhenPackDisablesCulling() {
        assertEquals(-160.0D,
            ShadowRenderer.disabledCullingShadowRenderDistanceBlocks(160.0F, -1.0F), 0.0D);
    }

    @Test
    public void distanceOnlyCullingIncludesNormalRenderDistanceBoundaryLikeReference() {
        assertTrue(ShadowRenderer.shouldApplyDistanceOnlyCull(160.0D, 160.0D));
    }

    @Test
    public void distanceOnlyCullingDisablesWhenPackDistanceExceedsNormalRenderDistanceLikeReference() {
        assertFalse(ShadowRenderer.shouldApplyDistanceOnlyCull(320.0D, 160.0D));
    }

    @Test
    public void distanceOnlyCullingDisablesForZeroOrNegativePackDistanceLikeReference() {
        assertFalse(ShadowRenderer.shouldApplyDistanceOnlyCull(0.0D, 160.0D));
        assertFalse(ShadowRenderer.shouldApplyDistanceOnlyCull(-1.0D, 160.0D));
    }

    @Test
    public void distanceCameraUsesInclusiveBoxAroundPreparedCameraPosition() {
        ICamera camera = ShadowCullingCameras.distance(10.0D);
        camera.setPosition(5.0D, 5.0D, 5.0D);

        assertTrue(camera instanceof Frustum);
        assertTrue(camera.isBoundingBoxInFrustum(new AxisAlignedBB(15.0D, 5.0D, 5.0D, 16.0D, 6.0D, 6.0D)));
        assertTrue(camera.isBoundingBoxInFrustum(new AxisAlignedBB(-6.0D, 5.0D, 5.0D, -5.0D, 6.0D, 6.0D)));
        assertFalse(camera.isBoundingBoxInFrustum(new AxisAlignedBB(16.1D, 5.0D, 5.0D, 17.0D, 6.0D, 6.0D)));
    }

    @Test
    public void advancedShadowCameraKeepsReferenceDistanceBoxCull() {
        ICamera camera = ShadowCullingCameras.advanced(
            MatrixMath.createIdentity(),
            MatrixMath.createIdentity(),
            new float[] {0.0F, 0.0F, 1.0F},
            10.0D);
        camera.setPosition(0.0D, 0.0D, 0.0D);

        assertTrue(camera instanceof Frustum);
        assertTrue(camera.isBoundingBoxInFrustum(new AxisAlignedBB(-0.25D, -0.25D, -0.25D, 0.25D, 0.25D, 0.25D)));
        assertFalse(camera.isBoundingBoxInFrustum(new AxisAlignedBB(11.1D, 0.0D, 0.0D, 12.0D, 1.0D, 1.0D)));
    }

    @Test
    public void nonNegativeShadowDistanceRenderMulScalesPackDistance() {
        assertEquals(320.0D, ShadowRenderer.shadowRenderDistanceBlocks(160.0F, 2.0F), 0.0D);
        assertEquals(0.0D, ShadowRenderer.shadowRenderDistanceBlocks(160.0F, 0.0F), 0.0D);
        assertEquals(320.0D,
            ShadowRenderer.disabledCullingShadowRenderDistanceBlocks(160.0F, 2.0F), 0.0D);
    }
}
