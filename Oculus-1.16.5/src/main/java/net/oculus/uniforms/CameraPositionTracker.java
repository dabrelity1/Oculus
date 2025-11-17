package net.oculus.uniforms;

import java.util.Arrays;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Tracks camera position with automatic precision-preserving coordinate shifting.
 * 
 * <p>This class solves the floating-point precision problem that occurs when rendering
 * at large world coordinates (e.g., millions of blocks from origin). It maintains
 * a shifted coordinate system where the camera position stays within a reasonable range
 * (±30,000 blocks) while the {@code shift} vector tracks the accumulated offset.</p>
 * 
 * <p><b>Coordinate Shifting Algorithm:</b></p>
 * <ul>
 *   <li>If camera X or Z exceeds ±30,000 blocks, shift coordinates back toward origin</li>
 *   <li>Shifts are applied in 30,000-block increments to minimize jitter</li>
 *   <li>If player teleports >1,000 blocks, immediately re-center coordinates</li>
 *   <li>Shifts are applied to both current and previous positions to maintain deltas</li>
 * </ul>
 * 
 * <p>This approach maintains sub-block precision even at extreme coordinates while
 * keeping shader uniform values in a numerically stable range.</p>
 * 
 * @see CapturedRenderingState#beginFrame(float)
 */
final class CameraPositionTracker {
    private static final double WALK_RANGE = 30000.0;
    private static final double TP_RANGE = 1000.0;
    private static final float NEAR_PLANE = 0.05F;

    private final double[] current = new double[3];
    private final double[] previous = new double[3];
    private final double[] shift = new double[3];
    private final double[] lastUnshifted = new double[3];

    private float farPlane = 0.0F;

    void update(float partialTicks) {
        System.arraycopy(current, 0, previous, 0, current.length);

        Vec3d unshifted = getUnshiftedCameraPosition(partialTicks);
        if (unshifted == null) {
            Arrays.fill(current, 0.0);
            farPlane = 0.0F;
            return;
        }

        current[0] = unshifted.x + shift[0];
        current[1] = unshifted.y + shift[1];
        current[2] = unshifted.z + shift[2];

        updateShift(unshifted);
        lastUnshifted[0] = unshifted.x;
        lastUnshifted[1] = unshifted.y;
        lastUnshifted[2] = unshifted.z;
        farPlane = computeFarPlane();
    }

    double[] getCurrent() {
        return current;
    }

    double[] getPrevious() {
        return previous;
    }

    double[] getLastUnshifted() {
        return lastUnshifted;
    }

    float getNearPlane() {
        return NEAR_PLANE;
    }

    float getFarPlane() {
        return farPlane;
    }

    private Vec3d getUnshiftedCameraPosition(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) {
            return null;
        }

        Entity entity = mc.getRenderViewEntity();
        if (entity == null) {
            return null;
        }

        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;
        return new Vec3d(x, y, z);
    }

    private void updateShift(Vec3d currentUnshifted) {
        double deltaX = getShiftAmount(currentUnshifted.x, lastUnshifted[0]);
        double deltaZ = getShiftAmount(currentUnshifted.z, lastUnshifted[2]);

        if (deltaX != 0.0 || deltaZ != 0.0) {
            shift[0] += deltaX;
            shift[2] += deltaZ;
            current[0] += deltaX;
            current[2] += deltaZ;
            previous[0] += deltaX;
            previous[2] += deltaZ;
        }
    }

    private double getShiftAmount(double value, double previousValue) {
        if (Math.abs(value) > WALK_RANGE || Math.abs(value - previousValue) > TP_RANGE) {
            return -(value - (value % WALK_RANGE));
        }

        return 0.0;
    }

    private float computeFarPlane() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.gameSettings == null) {
            return 0.0F;
        }

        return mc.gameSettings.renderDistanceChunks * 16.0F;
    }
}