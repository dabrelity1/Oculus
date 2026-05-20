package net.oculus.mixin.pipeline;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;
import me.jellysquid.mods.sodium.client.render.chunk.backends.multidraw.MultidrawChunkRenderBackend;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import net.oculus.pipeline.OculusVertexBindingHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MultidrawChunkRenderBackend.class, remap = false)
public abstract class MultidrawChunkRenderBackendMixin {
    @Redirect(method = "createRegionTessellation", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/device/CommandList;createTessellation(Lme/jellysquid/mods/sodium/client/gl/tessellation/GlPrimitiveType;[Lme/jellysquid/mods/sodium/client/gl/tessellation/TessellationBinding;)Lme/jellysquid/mods/sodium/client/gl/tessellation/GlTessellation;"), remap = false)
    private GlTessellation oculus$augmentMultidrawBindings(CommandList commandList, GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
        GlVertexFormat<ChunkMeshAttribute> format = ((MultidrawChunkRenderBackend) (Object) this).getVertexType().getCustomVertexFormat();
        TessellationBinding[] augmented = oculus$augmentBindings(format, bindings);
        return commandList.createTessellation(primitiveType, augmented);
    }

    private static TessellationBinding[] oculus$augmentBindings(GlVertexFormat<ChunkMeshAttribute> format, TessellationBinding[] bindings) {
        TessellationBinding[] augmented = new TessellationBinding[bindings.length];

        for (int i = 0; i < bindings.length; i++) {
            TessellationBinding binding = bindings[i];
            if (binding.isInstanced()) {
                augmented[i] = binding;
                continue;
            }

            GlVertexAttributeBinding[] augmentedAttributes = OculusVertexBindingHelper.createAugmentedBindings(format, binding.getAttributeBindings());
            augmented[i] = new TessellationBinding(binding.getBuffer(), augmentedAttributes, binding.isInstanced());
        }

        return augmented;
    }
}
