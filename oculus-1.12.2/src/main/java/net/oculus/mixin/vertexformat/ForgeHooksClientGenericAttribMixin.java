package net.oculus.mixin.vertexformat;

import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(value = ForgeHooksClient.class, remap = false)
public abstract class ForgeHooksClientGenericAttribMixin {
    @Inject(method = "preDraw", at = @At("HEAD"), cancellable = true)
    private static void oculus$preDrawGeneric(VertexFormatElement.EnumUsage usage, VertexFormat format,
                                              int element, int stride, ByteBuffer buffer, CallbackInfo ci) {
        if (usage != VertexFormatElement.EnumUsage.GENERIC) {
            return;
        }

        VertexFormatElement attribute = format.getElement(element);
        int index = attribute.getIndex();
        boolean normalized = index == 13 && attribute.getType() == VertexFormatElement.EnumType.BYTE;

        buffer.position(format.getOffset(element));
        GL20.glEnableVertexAttribArray(index);
        GL20.glVertexAttribPointer(index, attribute.getElementCount(),
            attribute.getType().getGlConstant(), normalized, stride, buffer);
        ci.cancel();
    }

    @Inject(method = "postDraw", at = @At("HEAD"), cancellable = true)
    private static void oculus$postDrawGeneric(VertexFormatElement.EnumUsage usage, VertexFormat format,
                                               int element, int stride, ByteBuffer buffer, CallbackInfo ci) {
        if (usage != VertexFormatElement.EnumUsage.GENERIC) {
            return;
        }

        GL20.glDisableVertexAttribArray(format.getElement(element).getIndex());
        ci.cancel();
    }
}
