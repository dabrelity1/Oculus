package net.oculus.pipeline;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttribute;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import net.oculus.pipeline.vertex.OculusChunkMeshAttributes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility for augmenting Sodium's default terrain bindings with the additional Oculus attributes.
 */
public final class OculusVertexBindingHelper {
        private OculusVertexBindingHelper() {
        }

        public static GlVertexAttributeBinding[] createAugmentedBindings(GlVertexFormat<ChunkMeshAttribute> format,
                                                                                                                                          GlVertexAttributeBinding[] defaultBindings) {
                List<GlVertexAttributeBinding> bindings = new ArrayList<>(defaultBindings.length + 5);
                bindings.addAll(Arrays.asList(defaultBindings));

                maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.NORMAL, OculusChunkMeshAttributes.NORMAL);
                maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.TANGENT, OculusChunkMeshAttributes.TANGENT);
                maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.MID_UV, OculusChunkMeshAttributes.MID_UV);
                maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.MATERIAL, OculusChunkMeshAttributes.MATERIAL);
                maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.MID_BLOCK, OculusChunkMeshAttributes.MID_BLOCK);

                return bindings.toArray(new GlVertexAttributeBinding[0]);
        }

        private static void maybeAddAttribute(List<GlVertexAttributeBinding> bindings,
                                                                                  GlVertexFormat<ChunkMeshAttribute> format,
                                                                                  me.jellysquid.mods.sodium.client.gl.shader.ShaderBindingPoint bindingPoint,
                                                                                  ChunkMeshAttribute attributeId) {
                try {
                        GlVertexAttribute attribute = format.getAttribute(attributeId);
                        bindings.add(new GlVertexAttributeBinding(bindingPoint, attribute));
                } catch (NullPointerException ignored) {
                        // Format does not provide this attribute; skip silently so non-Oculus formats continue to work.
                }
        }
}
