package net.oculus.mixin.pipeline;

import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexAttributeBinding;
import me.jellysquid.mods.sodium.client.gl.attribute.GlVertexFormat;
import me.jellysquid.mods.sodium.client.gl.device.CommandList;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlPrimitiveType;
import me.jellysquid.mods.sodium.client.gl.tessellation.GlTessellation;
import me.jellysquid.mods.sodium.client.gl.tessellation.TessellationBinding;
import me.jellysquid.mods.sodium.client.gl.buffer.GlMutableBuffer;
import me.jellysquid.mods.sodium.client.gl.buffer.VertexData;
import me.jellysquid.mods.sodium.client.render.chunk.backends.oneshot.ChunkOneshotGraphicsState;
import me.jellysquid.mods.sodium.client.render.chunk.data.ChunkMeshData;
import me.jellysquid.mods.sodium.client.render.chunk.format.ChunkMeshAttribute;
import net.oculus.pipeline.OculusVertexBindingHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(value = ChunkOneshotGraphicsState.class, remap = false)
public abstract class ChunkOneshotGraphicsStateMixin {
    @Unique
    private GlVertexFormat<ChunkMeshAttribute> oculus$currentFormat;

    @Inject(method = "upload", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/device/CommandList;createTessellation(Lme/jellysquid/mods/sodium/client/gl/tessellation/GlPrimitiveType;[Lme/jellysquid/mods/sodium/client/gl/tessellation/TessellationBinding;)Lme/jellysquid/mods/sodium/client/gl/tessellation/GlTessellation;", shift = At.Shift.BEFORE), remap = false, locals = LocalCapture.CAPTURE_FAILHARD)
    private void oculus$captureVertexFormat(CommandList commandList, ChunkMeshData meshData, CallbackInfo ci, VertexData vertexData, GlVertexFormat<ChunkMeshAttribute> vertexFormat, TessellationBinding[] bindings, GlMutableBuffer vertexBuffer) {
        this.oculus$currentFormat = vertexFormat;
    }

    @Redirect(method = "upload", at = @At(value = "INVOKE", target = "Lme/jellysquid/mods/sodium/client/gl/device/CommandList;createTessellation(Lme/jellysquid/mods/sodium/client/gl/tessellation/GlPrimitiveType;[Lme/jellysquid/mods/sodium/client/gl/tessellation/TessellationBinding;)Lme/jellysquid/mods/sodium/client/gl/tessellation/GlTessellation;"), remap = false)
    private GlTessellation oculus$augmentOneshotBindings(CommandList commandList, GlPrimitiveType primitiveType, TessellationBinding[] bindings) {
        GlVertexFormat<ChunkMeshAttribute> format = this.oculus$currentFormat;

        if (format == null) {
            return commandList.createTessellation(primitiveType, bindings);
        }

        TessellationBinding[] augmented = oculus$augmentBindings(format, bindings);
        return commandList.createTessellation(primitiveType, augmented);
    }

    private static TessellationBinding[] oculus$augmentBindings(GlVertexFormat<ChunkMeshAttribute> format, TessellationBinding[] bindings) {
        TessellationBinding[] augmented = new TessellationBinding[bindings.length];

        for (int i = 0; i < bindings.length; i++) {
            TessellationBinding binding = bindings[i];
            GlVertexAttributeBinding[] augmentedAttributes = OculusVertexBindingHelper.createAugmentedBindings(format, binding.getAttributeBindings());
            augmented[i] = new TessellationBinding(binding.getBuffer(), augmentedAttributes, binding.isInstanced());
        }

        return augmented;
    }
}
