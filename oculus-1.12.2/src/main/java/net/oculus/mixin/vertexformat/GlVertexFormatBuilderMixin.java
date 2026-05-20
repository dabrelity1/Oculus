package net.oculus.mixin.vertexformat;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttribute;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeFormat;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import net.oculus.pipeline.vertex.OculusChunkMeshAttributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.EnumMap;

/**
 * Allows Sodium's {@link GlVertexFormat.Builder} to tolerate Oculus-specific attributes when other vertex types
 * do not provide explicit definitions for them. This mirrors the behaviour of the modern Iris integration and
 * prevents legacy vertex formats from failing validation after we extend {@link me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute}.
 */
@Mixin(value = GlVertexFormat.Builder.class, remap = false)
public class GlVertexFormatBuilderMixin {
    private static final GlVertexAttribute OCULUS_EMPTY_ATTRIBUTE = new GlVertexAttribute(GlVertexAttributeFormat.FLOAT, 0, false, 0, 0);

    @Redirect(method = "build", at = @At(value = "INVOKE", target = "java/util/EnumMap.get(Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object oculus$allowMissingExtendedAttributes(EnumMap<?, ?> map, Object key) {
        Object value = map.get(key);
        if (value == null && isOculusAttribute(key)) {
            return OCULUS_EMPTY_ATTRIBUTE;
        }
        return value;
    }

    private static boolean isOculusAttribute(Object key) {
        return key == OculusChunkMeshAttributes.NORMAL
                || key == OculusChunkMeshAttributes.TANGENT
                || key == OculusChunkMeshAttributes.MID_UV
                || key == OculusChunkMeshAttributes.MATERIAL
                || key == OculusChunkMeshAttributes.MID_BLOCK;
    }
}
