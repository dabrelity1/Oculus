package net.oculus.pipeline.vertex;

import net.oculus.pipeline.math.Vector3f;
import net.oculus.pipeline.vertex.geometry.QuadView;
import net.oculus.pipeline.vertex.geometry.TriView;

public final class OculusNormalHelper {
    private OculusNormalHelper() {
    }

    public static int packNormal(float x, float y, float z, float w) {
        x = clamp(x, -1.0f, 1.0f);
        y = clamp(y, -1.0f, 1.0f);
        z = clamp(z, -1.0f, 1.0f);
        w = clamp(w, -1.0f, 1.0f);

        return ((int) (x * 127) & 0xFF)
                | (((int) (y * 127) & 0xFF) << 8)
                | (((int) (z * 127) & 0xFF) << 16)
                | (((int) (w * 127) & 0xFF) << 24);
    }

    public static int packNormal(Vector3f normal, float w) {
        return packNormal(normal.x, normal.y, normal.z, w);
    }

    public static void computeFaceNormal(Vector3f out, QuadView quad) {
        final float x0 = quad.x(0);
        final float y0 = quad.y(0);
        final float z0 = quad.z(0);
        final float x1 = quad.x(1);
        final float y1 = quad.y(1);
        final float z1 = quad.z(1);
        final float x2 = quad.x(2);
        final float y2 = quad.y(2);
        final float z2 = quad.z(2);
        final float x3 = quad.x(3);
        final float y3 = quad.y(3);
        final float z3 = quad.z(3);

        final float dx0 = x2 - x0;
        final float dy0 = y2 - y0;
        final float dz0 = z2 - z0;
        final float dx1 = x3 - x1;
        final float dy1 = y3 - y1;
        final float dz1 = z3 - z1;

        float normX = dy0 * dz1 - dz0 * dy1;
        float normY = dz0 * dx1 - dx0 * dz1;
        float normZ = dx0 * dy1 - dy0 * dx1;

        float length = (float) Math.sqrt(normX * normX + normY * normY + normZ * normZ);

        if (length != 0.0f) {
            normX /= length;
            normY /= length;
            normZ /= length;
        }

        out.set(normX, normY, normZ);
    }

    public static int computeTangent(float normalX, float normalY, float normalZ, TriView tri) {
        float x0 = tri.x(0);
        float y0 = tri.y(0);
        float z0 = tri.z(0);

        float x1 = tri.x(1);
        float y1 = tri.y(1);
        float z1 = tri.z(1);

        float x2 = tri.x(2);
        float y2 = tri.y(2);
        float z2 = tri.z(2);

        float edge1x = x1 - x0;
        float edge1y = y1 - y0;
        float edge1z = z1 - z0;

        float edge2x = x2 - x0;
        float edge2y = y2 - y0;
        float edge2z = z2 - z0;

        float u0 = tri.u(0);
        float v0 = tri.v(0);

        float u1 = tri.u(1);
        float v1 = tri.v(1);

        float u2 = tri.u(2);
        float v2 = tri.v(2);

        float deltaU1 = u1 - u0;
        float deltaV1 = v1 - v0;
        float deltaU2 = u2 - u0;
        float deltaV2 = v2 - v0;

        float denom = deltaU1 * deltaV2 - deltaU2 * deltaV1;
        float f = denom == 0.0f ? 1.0f : 1.0f / denom;

        float tangentX = f * (deltaV2 * edge1x - deltaV1 * edge2x);
        float tangentY = f * (deltaV2 * edge1y - deltaV1 * edge2y);
        float tangentZ = f * (deltaV2 * edge1z - deltaV1 * edge2z);
        float tangentLength = (float) Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);

        if (tangentLength != 0.0f) {
            tangentX /= tangentLength;
            tangentY /= tangentLength;
            tangentZ /= tangentLength;
        }

        float pBitangentX = tangentY * normalZ - tangentZ * normalY;
        float pBitangentY = tangentZ * normalX - tangentX * normalZ;
        float pBitangentZ = tangentX * normalY - tangentY * normalX;

        // Determine handedness by comparing predicted bitangent to the one derived from UVs.
        float bitangentX = f * (-deltaU2 * edge1x + deltaU1 * edge2x);
        float bitangentY = f * (-deltaU2 * edge1y + deltaU1 * edge2y);
        float bitangentZ = f * (-deltaU2 * edge1z + deltaU1 * edge2z);
        float bitangentLength = (float) Math.sqrt(bitangentX * bitangentX + bitangentY * bitangentY + bitangentZ * bitangentZ);

        if (bitangentLength != 0.0f) {
            bitangentX /= bitangentLength;
            bitangentY /= bitangentLength;
            bitangentZ /= bitangentLength;
        }

        float dot = bitangentX * pBitangentX + bitangentY * pBitangentY + bitangentZ * pBitangentZ;
        float handedness = dot < 0.0f ? -1.0f : 1.0f;

        return packNormal(tangentX, tangentY, tangentZ, handedness);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
