package net.oculus.pipeline;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import net.oculus.Oculus;
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
                maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.BLOCK_ID, OculusChunkMeshAttributes.MATERIAL);
                maybeAddAttribute(bindings, format, OculusChunkShaderBindingPoints.MID_BLOCK, OculusChunkMeshAttributes.MID_BLOCK);

                return bindings.toArray(new GlVertexAttributeBinding[0]);
        }

        private static void maybeAddAttribute(List<GlVertexAttributeBinding> bindings,
                                                                                  GlVertexFormat<ChunkMeshAttribute> format,
                                                                                  me.jellysquid.mods.sodium.client.gl.shader.ShaderBindingPoint bindingPoint,
                                                                                  ChunkMeshAttribute attributeId) {
                try {
                        bindings.add(new GlVertexAttributeBinding(bindingPoint, format.getAttribute(attributeId)));
                } catch (RuntimeException missingAttribute) {
                        // Sodium's default terrain format only knows about its original four attributes. If that
                        // format is still active we skip the additional bindings so terrain rendering continues.
                        if (Oculus.LOGGER.isDebugEnabled()) {
                                Oculus.LOGGER.debug("Skipping Oculus vertex attribute {}: {}", attributeId,
                                                missingAttribute.toString());
                        }
                }
        }
}
