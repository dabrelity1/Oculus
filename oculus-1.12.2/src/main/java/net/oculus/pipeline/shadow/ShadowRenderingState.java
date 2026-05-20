package net.oculus.pipeline.shadow;

import java.util.Arrays;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

public final class ShadowRenderingState {
    private static final int MATRIX_SIZE = 16;
    private static final float[] shadowProjection = new float[MATRIX_SIZE];

    private static boolean shadowPassActive;
    private static boolean hasShadowProjection;
    private static boolean implicitShadowPass;
    private static boolean entityFilteringActive;
    private static boolean renderEntities;
    private static boolean renderPlayer;
    private static boolean renderBlockEntities;
    private static Entity playerEntity;

    private ShadowRenderingState() {
    }

    public static void beginShadowPass(float[] projectionMatrix) {
        if (projectionMatrix == null || projectionMatrix.length < MATRIX_SIZE) {
            throw new IllegalArgumentException("Shadow projection matrix must contain at least 16 values");
        }

        shadowPassActive = true;
        implicitShadowPass = false;
        hasShadowProjection = true;
        System.arraycopy(projectionMatrix, 0, shadowProjection, 0, MATRIX_SIZE);
    }

    public static void beginShadowPass() {
        shadowPassActive = true;
        implicitShadowPass = false;
        hasShadowProjection = false;
    }

    public static void endShadowPass() {
        endEntityFiltering();
        shadowPassActive = false;
        implicitShadowPass = false;
        hasShadowProjection = false;
        Arrays.fill(shadowProjection, 0.0F);
    }

    public static void begin(boolean entities, boolean player, boolean blockEntities, Entity cameraEntity) {
        if (!shadowPassActive) {
            shadowPassActive = true;
            implicitShadowPass = true;
        }

        beginEntityFiltering(entities, player, blockEntities, cameraEntity);
    }

    public static void beginEntityFiltering(boolean entities, boolean player, boolean blockEntities, Entity cameraEntity) {
        entityFilteringActive = true;
        renderEntities = entities;
        renderPlayer = player;
        renderBlockEntities = blockEntities;
        playerEntity = cameraEntity;
    }

    public static void end() {
        endEntityFiltering();

        if (implicitShadowPass) {
            shadowPassActive = false;
            implicitShadowPass = false;
            hasShadowProjection = false;
            Arrays.fill(shadowProjection, 0.0F);
        }
    }

    public static void endEntityFiltering() {
        entityFilteringActive = false;
        renderEntities = false;
        renderPlayer = false;
        renderBlockEntities = false;
        playerEntity = null;
    }

    public static boolean isActive() {
        return shadowPassActive;
    }

    public static boolean areShadowsCurrentlyBeingRendered() {
        return shadowPassActive;
    }

    public static float[] getShadowOrthoMatrix() {
        if (!shadowPassActive || !hasShadowProjection) {
            return null;
        }

        return Arrays.copyOf(shadowProjection, MATRIX_SIZE);
    }

    public static boolean shouldRenderEntity(Entity entity) {
        if (!entityFilteringActive || entity == null) {
            return true;
        }

        if (entity instanceof EntityPlayer && ((EntityPlayer) entity).isSpectator()) {
            return false;
        }

        if (renderEntities) {
            return true;
        }

        if (!renderPlayer || playerEntity == null) {
            return false;
        }

        return entity == playerEntity
            || playerEntity.isRidingOrBeingRiddenBy(entity)
            || entity.isRidingOrBeingRiddenBy(playerEntity);
    }

    public static boolean shouldRenderBlockEntities() {
        return !entityFilteringActive || renderBlockEntities;
    }
}
