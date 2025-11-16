package net.oculus.uniforms;

import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Iris-exclusive helper uniforms for lightning effects and relative eye offsets.
 */
public final class SpecialEffectUniforms {
    private static final Minecraft MC = Minecraft.getMinecraft();

    private static final float[] RELATIVE_EYE = new float[3];
    private static final float[] LIGHTNING_BOLT = new float[4];

    private SpecialEffectUniforms() {
    }

    public static float[] getRelativeEyePosition() {
        Arrays.fill(RELATIVE_EYE, 0.0f);

        Entity camera = MC.getRenderViewEntity();
        double[] unshifted = CapturedRenderingState.INSTANCE.getUnshiftedCameraPosition();
        if (camera == null || unshifted == null) {
            return RELATIVE_EYE;
        }

        Vec3d eyes = camera.getPositionEyes(CapturedRenderingState.INSTANCE.getTickDelta());
    RELATIVE_EYE[0] = (float) (unshifted[0] - eyes.x);
    RELATIVE_EYE[1] = (float) (unshifted[1] - eyes.y);
    RELATIVE_EYE[2] = (float) (unshifted[2] - eyes.z);
        return RELATIVE_EYE;
    }

    public static float[] getLightningBoltPosition() {
        Arrays.fill(LIGHTNING_BOLT, 0.0f);

        World world = MC.world;
        if (world == null) {
            return LIGHTNING_BOLT;
        }

        double[] unshifted = CapturedRenderingState.INSTANCE.getUnshiftedCameraPosition();
        if (unshifted == null) {
            return LIGHTNING_BOLT;
        }

        float partialTicks = CapturedRenderingState.INSTANCE.getTickDelta();
        for (Entity entity : world.weatherEffects) {
            if (entity instanceof EntityLightningBolt) {
                Vec3d position = new Vec3d(
                        entity.prevPosX + (entity.posX - entity.prevPosX) * partialTicks,
                        entity.prevPosY + (entity.posY - entity.prevPosY) * partialTicks,
                        entity.prevPosZ + (entity.posZ - entity.prevPosZ) * partialTicks);

                LIGHTNING_BOLT[0] = (float) (position.x - unshifted[0]);
                LIGHTNING_BOLT[1] = (float) (position.y - unshifted[1]);
                LIGHTNING_BOLT[2] = (float) (position.z - unshifted[2]);
                LIGHTNING_BOLT[3] = 1.0f;
                break;
            }
        }

        return LIGHTNING_BOLT;
    }
}
