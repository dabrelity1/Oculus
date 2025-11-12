package net.oculus.pipeline.vertex;

import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import net.minecraftforge.common.util.EnumHelper;

/**
 * Provides access to the extended chunk mesh attributes required by the Oculus terrain pipeline.
 * <p>
 * Sodium's default {@link ChunkMeshAttribute} enum only exposes position, color, texture, and light attributes.
 * The shader stack used by Oculus needs additional per-vertex properties such as normals, tangents, and
 * block metadata. We lazily inject those enum constants using Forge's {@link EnumHelper} so that the rest of the
 * renderer can reference them just like the built-in attributes.
 */
public final class OculusChunkMeshAttributes {
    public static final ChunkMeshAttribute NORMAL = ensure("NORMAL");
    public static final ChunkMeshAttribute TANGENT = ensure("TANGENT");
    public static final ChunkMeshAttribute MID_UV = ensure("MID_UV");
    public static final ChunkMeshAttribute MATERIAL = ensure("MATERIAL");
    public static final ChunkMeshAttribute MID_BLOCK = ensure("MID_BLOCK");

    private OculusChunkMeshAttributes() {
    }

    private static ChunkMeshAttribute ensure(String name) {
        for (ChunkMeshAttribute attribute : ChunkMeshAttribute.values()) {
            if (attribute.name().equals(name)) {
                return attribute;
            }
        }

        return EnumHelper.addEnum(ChunkMeshAttribute.class, name, new Class[0], new Object[0]);
    }
}
