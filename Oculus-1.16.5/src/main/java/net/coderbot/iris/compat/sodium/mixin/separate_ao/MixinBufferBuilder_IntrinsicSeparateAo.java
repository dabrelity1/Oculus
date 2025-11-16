package net.coderbot.iris.compat.sodium.mixin.separate_ao;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;
import com.mojang.math.Vector4f;
import me.jellysquid.mods.sodium.client.model.quad.ModelQuadView;
import me.jellysquid.mods.sodium.client.model.vertex.VanillaVertexTypes;
import me.jellysquid.mods.sodium.client.model.vertex.VertexDrain;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.QuadVertexSink;
import me.jellysquid.mods.sodium.client.util.color.ColorABGR;
import me.jellysquid.mods.sodium.client.util.color.ColorU8;
import me.jellysquid.mods.sodium.client.util.math.MatrixUtil;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.Arrays;

@Mixin(value = BufferBuilder.class, priority = 1010)
public abstract class MixinBufferBuilder_IntrinsicSeparateAo extends DefaultedVertexConsumer {
    @Shadow
    private boolean fastFormat;

    @Override
    public void putBulkData(PoseStack.Pose matrixEntry, BakedQuad quad, float[] brightnesses, float red, float green,
                            float blue, int[] lights, int overlay, boolean useQuadColorData) {
        if (!this.fastFormat) {
            if (BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()) {
                brightnesses = new float[brightnesses.length];
                Arrays.fill(brightnesses, 1.0f);
            }

            super.putBulkData(matrixEntry, quad, brightnesses, red, green, blue, lights, overlay, useQuadColorData);

            return;
        }

        if (this.defaultColorSet) {
            throw new IllegalStateException();
        }

        ModelQuadView quadView = (ModelQuadView) quad;

        Matrix4f positionMatrix = matrixEntry.pose();
        Matrix3f normalMatrix = matrixEntry.normal();

        int norm = MatrixUtil.computeNormal(normalMatrix, quad.getDirection());

        QuadVertexSink drain = VertexDrain.of(this)
                .createSink(VanillaVertexTypes.QUADS);
        drain.ensureCapacity(4);

        for (int i = 0; i < 4; i++) {
            float x = quadView.getX(i);
            float y = quadView.getY(i);
            float z = quadView.getZ(i);

            float fR;
            float fG;
            float fB;

            float brightness = brightnesses[i];
            float alpha = 1.0F;

            if (BlockRenderingSettings.INSTANCE.shouldUseSeparateAo()) {
                alpha = brightness;
                if (useQuadColorData) {
                    int color = quadView.getColor(i);

                    float oR = ColorU8.normalize(ColorABGR.unpackRed(color));
                    float oG = ColorU8.normalize(ColorABGR.unpackGreen(color));
                    float oB = ColorU8.normalize(ColorABGR.unpackBlue(color));

                    fR = oR * red;
                    fG = oG * green;
                    fB = oB * blue;
                } else {
                    fR = red;
                    fG = green;
                    fB = blue;
                }
            } else {
                if (useQuadColorData) {
                    int color = quadView.getColor(i);

                    float oR = ColorU8.normalize(ColorABGR.unpackRed(color));
                    float oG = ColorU8.normalize(ColorABGR.unpackGreen(color));
                    float oB = ColorU8.normalize(ColorABGR.unpackBlue(color));

                    fR = oR * brightness * red;
                    fG = oG * brightness * green;
                    fB = oB * brightness * blue;
                } else {
                    fR = brightness * red;
                    fG = brightness * green;
                    fB = brightness * blue;
                }
            }

            float u = quadView.getTexU(i);
            float v = quadView.getTexV(i);

            int color = ColorABGR.pack(fR, fG, fB, alpha);

            Vector4f pos = new Vector4f(x, y, z, 1.0F);
            pos.transform(positionMatrix);

            drain.writeQuad(pos.x(), pos.y(), pos.z(), color, u, v, lights[i], overlay, norm);
        }

        drain.flush();
    }
}