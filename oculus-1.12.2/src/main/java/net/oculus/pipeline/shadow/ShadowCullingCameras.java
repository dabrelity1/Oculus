package net.oculus.pipeline.shadow;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.util.math.AxisAlignedBB;
import net.oculus.gl.state.MatrixMath;

final class ShadowCullingCameras {
    private static final int MATRIX_SIZE = 16;
    private static final ClippingHelper UNUSED_CLIPPING_HELPER = new ClippingHelper();

    private ShadowCullingCameras() {
    }

    static ICamera nonCulling() {
        return new NonCullingCamera();
    }

    static ICamera cullEverything() {
        return new CullEverythingCamera();
    }

    static ICamera distance(double maxDistance) {
        return new DistanceCamera(maxDistance);
    }

    static ICamera advanced(float[] playerView, float[] playerProjection, float[] shadowLightVectorFromOrigin,
                            double maxDistance) {
        BoxCuller culler = maxDistance > 0.0D ? new BoxCuller(maxDistance) : null;
        return new AdvancedShadowCullingCamera(playerView, playerProjection, shadowLightVectorFromOrigin, culler);
    }

    static ICamera intersection(ICamera first, ICamera second) {
        return new CombiningCamera(first, second, true);
    }

    static ICamera union(ICamera first, ICamera second) {
        return new CombiningCamera(first, second, false);
    }

    private static final class NonCullingCamera extends Frustum {
        private NonCullingCamera() {
            super(UNUSED_CLIPPING_HELPER);
        }

        @Override
        public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
            return true;
        }

        @Override
        public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            return true;
        }

        @Override
        public void setPosition(double x, double y, double z) {
        }
    }

    private static final class CullEverythingCamera extends Frustum {
        private CullEverythingCamera() {
            super(UNUSED_CLIPPING_HELPER);
        }

        @Override
        public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
            return false;
        }

        @Override
        public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            return false;
        }

        @Override
        public void setPosition(double x, double y, double z) {
        }
    }

    private static final class DistanceCamera extends Frustum {
        private final BoxCuller culler;

        private DistanceCamera(double maxDistance) {
            super(UNUSED_CLIPPING_HELPER);
            this.culler = new BoxCuller(maxDistance);
        }

        @Override
        public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
            return !culler.isCulled(aabb);
        }

        @Override
        public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            return !culler.isCulled(minX, minY, minZ, maxX, maxY, maxZ);
        }

        @Override
        public void setPosition(double x, double y, double z) {
            culler.setPosition(x, y, z);
        }
    }

    private static final class CombiningCamera extends Frustum {
        private final ICamera first;
        private final ICamera second;
        private final boolean requireBoth;

        private CombiningCamera(ICamera first, ICamera second, boolean requireBoth) {
            super(UNUSED_CLIPPING_HELPER);
            this.first = first;
            this.second = second;
            this.requireBoth = requireBoth;
        }

        @Override
        public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
            boolean firstVisible = first.isBoundingBoxInFrustum(aabb);
            if (requireBoth && !firstVisible) {
                return false;
            }
            if (!requireBoth && firstVisible) {
                return true;
            }

            return second.isBoundingBoxInFrustum(aabb);
        }

        @Override
        public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            return isBoundingBoxInFrustum(new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ));
        }

        @Override
        public void setPosition(double x, double y, double z) {
            first.setPosition(x, y, z);
            second.setPosition(x, y, z);
        }
    }

    private static final class AdvancedShadowCullingCamera extends Frustum {
        private static final int MAX_CLIPPING_PLANES = 13;

        private final Plane[] planes = new Plane[MAX_CLIPPING_PLANES];
        private final Vector3 shadowLightVectorFromOrigin;
        private final BoxCuller culler;
        private int planeCount;
        private double x;
        private double y;
        private double z;

        private AdvancedShadowCullingCamera(float[] playerView, float[] playerProjection,
                                            float[] shadowLightVectorFromOrigin, BoxCuller culler) {
            super(UNUSED_CLIPPING_HELPER);
            requireMatrix(playerView, "player view");
            requireMatrix(playerProjection, "player projection");
            this.shadowLightVectorFromOrigin = Vector3.normalized(shadowLightVectorFromOrigin);
            this.culler = culler;

            Plane[] basePlanes = createBaseClippingPlanes(playerView, playerProjection);
            boolean[] isBack = addBackPlanes(basePlanes);
            addEdgePlanes(basePlanes, isBack);
        }

        @Override
        public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {
            if (culler != null && culler.isCulled(aabb)) {
                return false;
            }

            return isVisible(
                (float) (aabb.minX - x),
                (float) (aabb.minY - y),
                (float) (aabb.minZ - z),
                (float) (aabb.maxX - x),
                (float) (aabb.maxY - y),
                (float) (aabb.maxZ - z));
        }

        @Override
        public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            if (culler != null && culler.isCulled(minX, minY, minZ, maxX, maxY, maxZ)) {
                return false;
            }

            return isVisible(
                (float) (minX - x),
                (float) (minY - y),
                (float) (minZ - z),
                (float) (maxX - x),
                (float) (maxY - y),
                (float) (maxZ - z));
        }

        @Override
        public void setPosition(double x, double y, double z) {
            if (culler != null) {
                culler.setPosition(x, y, z);
            }
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private boolean[] addBackPlanes(Plane[] basePlanes) {
            boolean[] isBack = new boolean[basePlanes.length];
            for (int planeIndex = 0; planeIndex < basePlanes.length; planeIndex++) {
                Plane plane = basePlanes[planeIndex];
                float dot = plane.normal().dot(shadowLightVectorFromOrigin);
                boolean back = dot > 0.0F;
                boolean edge = dot == 0.0F;
                isBack[planeIndex] = back;
                if (back || edge) {
                    addPlane(plane);
                }
            }
            return isBack;
        }

        private void addEdgePlanes(Plane[] basePlanes, boolean[] isBack) {
            for (int planeIndex = 0; planeIndex < basePlanes.length; planeIndex++) {
                if (!isBack[planeIndex]) {
                    continue;
                }

                Plane plane = basePlanes[planeIndex];
                NeighboringPlaneSet neighbors = NeighboringPlaneSet.forPlane(planeIndex);
                if (!isBack[neighbors.plane0]) {
                    addEdgePlane(plane, basePlanes[neighbors.plane0]);
                }
                if (!isBack[neighbors.plane1]) {
                    addEdgePlane(plane, basePlanes[neighbors.plane1]);
                }
                if (!isBack[neighbors.plane2]) {
                    addEdgePlane(plane, basePlanes[neighbors.plane2]);
                }
                if (!isBack[neighbors.plane3]) {
                    addEdgePlane(plane, basePlanes[neighbors.plane3]);
                }
            }
        }

        private void addEdgePlane(Plane backPlane, Plane frontPlane) {
            Vector3 backNormal = backPlane.normal();
            Vector3 frontNormal = frontPlane.normal();
            Vector3 intersection = backNormal.cross(frontNormal);
            Vector3 edgeNormal = intersection.cross(shadowLightVectorFromOrigin);
            Vector3 ixb = intersection.cross(backNormal).multiply(-frontPlane.w);
            Vector3 fxi = frontNormal.cross(intersection).multiply(-backPlane.w);
            float intersectionLength = intersection.lengthSquared();
            if (intersectionLength == 0.0F) {
                throw new IllegalArgumentException("Advanced shadow culling produced parallel clipping planes");
            }
            Vector3 point = ixb.add(fxi).multiply(1.0F / intersectionLength);
            float w = -edgeNormal.dot(point);
            addPlane(new Plane(edgeNormal.x, edgeNormal.y, edgeNormal.z, w));
        }

        private void addPlane(Plane plane) {
            if (planeCount >= planes.length) {
                throw new IllegalStateException("Advanced shadow culling produced too many clipping planes");
            }
            planes[planeCount++] = plane;
        }

        private boolean isVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
            for (int i = 0; i < planeCount; i++) {
                Plane plane = planes[i];
                float outsideBoundX = plane.x < 0.0F ? minX : maxX;
                float outsideBoundY = plane.y < 0.0F ? minY : maxY;
                float outsideBoundZ = plane.z < 0.0F ? minZ : maxZ;

                if (plane.x * outsideBoundX + plane.y * outsideBoundY + plane.z * outsideBoundZ < -plane.w) {
                    return false;
                }
            }
            return true;
        }
    }

    private static final class BoxCuller {
        private final double maxDistance;

        private double minAllowedX;
        private double maxAllowedX;
        private double minAllowedY;
        private double maxAllowedY;
        private double minAllowedZ;
        private double maxAllowedZ;

        private BoxCuller(double maxDistance) {
            this.maxDistance = Math.max(0.0D, maxDistance);
        }

        private void setPosition(double cameraX, double cameraY, double cameraZ) {
            this.minAllowedX = cameraX - maxDistance;
            this.maxAllowedX = cameraX + maxDistance;
            this.minAllowedY = cameraY - maxDistance;
            this.maxAllowedY = cameraY + maxDistance;
            this.minAllowedZ = cameraZ - maxDistance;
            this.maxAllowedZ = cameraZ + maxDistance;
        }

        private boolean isCulled(AxisAlignedBB aabb) {
            return isCulled(aabb.minX, aabb.minY, aabb.minZ, aabb.maxX, aabb.maxY, aabb.maxZ);
        }

        private boolean isCulled(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
            return maxX < minAllowedX || minX > maxAllowedX
                || maxY < minAllowedY || minY > maxAllowedY
                || maxZ < minAllowedZ || minZ > maxAllowedZ;
        }
    }

    private static Plane[] createBaseClippingPlanes(float[] playerView, float[] playerProjection) {
        float[] transform = new float[MATRIX_SIZE];
        MatrixMath.multiply(playerProjection, playerView, transform);
        MatrixMath.transpose(transform, transform);
        return new Plane[] {
            transformPlane(transform, -1.0F, 0.0F, 0.0F),
            transformPlane(transform, 1.0F, 0.0F, 0.0F),
            transformPlane(transform, 0.0F, -1.0F, 0.0F),
            transformPlane(transform, 0.0F, 1.0F, 0.0F),
            transformPlane(transform, 0.0F, 0.0F, -1.0F),
            transformPlane(transform, 0.0F, 0.0F, 1.0F)
        };
    }

    private static Plane transformPlane(float[] transform, float x, float y, float z) {
        float w = 1.0F;
        float transformedX = transform[0] * x + transform[4] * y + transform[8] * z + transform[12] * w;
        float transformedY = transform[1] * x + transform[5] * y + transform[9] * z + transform[13] * w;
        float transformedZ = transform[2] * x + transform[6] * y + transform[10] * z + transform[14] * w;
        float transformedW = transform[3] * x + transform[7] * y + transform[11] * z + transform[15] * w;
        return Plane.normalized(transformedX, transformedY, transformedZ, transformedW);
    }

    private static void requireMatrix(float[] matrix, String name) {
        if (matrix == null || matrix.length < MATRIX_SIZE) {
            throw new IllegalArgumentException("Advanced shadow culling requires a 4x4 " + name + " matrix");
        }
    }

    private static final class Plane {
        private final float x;
        private final float y;
        private final float z;
        private final float w;

        private Plane(float x, float y, float z, float w) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.w = w;
        }

        private static Plane normalized(float x, float y, float z, float w) {
            float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
            if (length == 0.0F) {
                throw new IllegalArgumentException("Advanced shadow culling produced a zero-length clipping plane");
            }
            return new Plane(x / length, y / length, z / length, w / length);
        }

        private Vector3 normal() {
            return new Vector3(x, y, z);
        }
    }

    private static final class Vector3 {
        private final float x;
        private final float y;
        private final float z;

        private Vector3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        private static Vector3 normalized(float[] values) {
            if (values == null || values.length < 3) {
                throw new IllegalArgumentException("Advanced shadow culling requires a shadow light vector");
            }
            Vector3 vector = new Vector3(values[0], values[1], values[2]);
            float length = (float) Math.sqrt(vector.lengthSquared());
            if (length == 0.0F) {
                throw new IllegalArgumentException("Advanced shadow culling requires a non-zero shadow light vector");
            }
            return vector.multiply(1.0F / length);
        }

        private Vector3 cross(Vector3 other) {
            return new Vector3(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x);
        }

        private float dot(Vector3 other) {
            return x * other.x + y * other.y + z * other.z;
        }

        private Vector3 add(Vector3 other) {
            return new Vector3(x + other.x, y + other.y, z + other.z);
        }

        private Vector3 multiply(float scale) {
            return new Vector3(x * scale, y * scale, z * scale);
        }

        private float lengthSquared() {
            return x * x + y * y + z * z;
        }
    }

    private static final class NeighboringPlaneSet {
        private static final NeighboringPlaneSet FOR_PLUS_X = new NeighboringPlaneSet(2, 3, 4, 5);
        private static final NeighboringPlaneSet FOR_PLUS_Y = new NeighboringPlaneSet(0, 1, 4, 5);
        private static final NeighboringPlaneSet FOR_PLUS_Z = new NeighboringPlaneSet(0, 1, 2, 3);
        private static final NeighboringPlaneSet[] TABLE = new NeighboringPlaneSet[] {
            FOR_PLUS_X,
            FOR_PLUS_Y,
            FOR_PLUS_Z
        };

        private final int plane0;
        private final int plane1;
        private final int plane2;
        private final int plane3;

        private NeighboringPlaneSet(int plane0, int plane1, int plane2, int plane3) {
            this.plane0 = plane0;
            this.plane1 = plane1;
            this.plane2 = plane2;
            this.plane3 = plane3;
        }

        private static NeighboringPlaneSet forPlane(int planeIndex) {
            return TABLE[planeIndex >>> 1];
        }
    }
}
