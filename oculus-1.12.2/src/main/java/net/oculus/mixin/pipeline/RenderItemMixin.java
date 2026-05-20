package net.oculus.mixin.pipeline;

import net.minecraft.client.renderer.RenderItem;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.ItemStack;
import net.oculus.client.OculusRuntimeValidation;
import net.oculus.uniforms.IdMapUniforms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderItem.class)
public abstract class RenderItemMixin {
    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
        at = @At("HEAD"))
    private void oculus$captureRenderedItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        oculus$drainItemCheckpoint("renderItem.itemModel.head", stack);
        IdMapUniforms.setCurrentRenderedItem(stack);
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
        at = @At("RETURN"))
    private void oculus$clearRenderedItem(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        oculus$drainItemCheckpoint("renderItem.itemModel.return", stack);
        IdMapUniforms.clearCurrentRenderedItem();
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderItem;renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/item/ItemStack;)V",
            shift = At.Shift.AFTER))
    private void oculus$afterItemModelDraw(ItemStack stack, IBakedModel model, CallbackInfo ci) {
        oculus$drainItemCheckpoint("renderItem.itemModel.afterRenderModel", stack);
    }

    @Inject(method = "renderItemModel(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
        at = @At("HEAD"))
    private void oculus$beforeItemModel(ItemStack stack, IBakedModel model, ItemCameraTransforms.TransformType transform,
                                        boolean leftHanded, CallbackInfo ci) {
        oculus$drainItemCheckpoint("renderItem.renderItemModel.head", stack);
    }

    @Inject(method = "renderItemModel(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderItem;renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
            shift = At.Shift.AFTER))
    private void oculus$afterBaseItemModel(ItemStack stack, IBakedModel model, ItemCameraTransforms.TransformType transform,
                                           boolean leftHanded, CallbackInfo ci) {
        oculus$drainItemCheckpoint("renderItem.renderItemModel.afterBaseModel", stack);
    }

    @Inject(method = "renderItemModel(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/IBakedModel;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;Z)V",
        at = @At("RETURN"))
    private void oculus$afterItemModel(ItemStack stack, IBakedModel model, ItemCameraTransforms.TransformType transform,
                                       boolean leftHanded, CallbackInfo ci) {
        oculus$drainItemCheckpoint("renderItem.renderItemModel.return", stack);
    }

    @Inject(method = "renderEffect(Lnet/minecraft/client/renderer/block/model/IBakedModel;)V",
        at = @At("RETURN"))
    private void oculus$afterItemGlint(IBakedModel model, CallbackInfo ci) {
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint("renderItem.renderEffect.return");
    }

    @Inject(method = "renderModel(Lnet/minecraft/client/renderer/block/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Tessellator;draw()V",
            shift = At.Shift.AFTER))
    private void oculus$afterTessellatorDraw(IBakedModel model, int color, ItemStack stack, CallbackInfo ci) {
        oculus$drainItemCheckpoint("renderItem.renderModel.afterTessellatorDraw", stack);
    }

    private static void oculus$drainItemCheckpoint(String checkpoint, ItemStack stack) {
        String itemName = stack.isEmpty() || stack.getItem().getRegistryName() == null
            ? "empty"
            : stack.getItem().getRegistryName().toString();
        OculusRuntimeValidation.drainGlErrorsAtCheckpoint(checkpoint + "." + itemName);
    }
}
