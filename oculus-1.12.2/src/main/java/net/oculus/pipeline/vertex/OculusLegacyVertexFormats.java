package net.oculus.pipeline.vertex;

import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;

/**
 * Real 1.12.2 vertex formats used when the legacy BufferBuilder path needs Iris/Oculus attributes.
 */
public final class OculusLegacyVertexFormats {
    public static final VertexFormatElement ENTITY_ELEMENT =
        new VertexFormatElement(11, VertexFormatElement.EnumType.SHORT, VertexFormatElement.EnumUsage.GENERIC, 2);
    public static final VertexFormatElement MID_TEXTURE_ELEMENT =
        new VertexFormatElement(12, VertexFormatElement.EnumType.FLOAT, VertexFormatElement.EnumUsage.GENERIC, 2);
    public static final VertexFormatElement TANGENT_ELEMENT =
        new VertexFormatElement(13, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.GENERIC, 4);
    public static final VertexFormatElement MID_BLOCK_ELEMENT =
        new VertexFormatElement(14, VertexFormatElement.EnumType.BYTE, VertexFormatElement.EnumUsage.GENERIC, 4);

    public static final VertexFormat TERRAIN = new VertexFormat()
        .addElement(DefaultVertexFormats.POSITION_3F)
        .addElement(DefaultVertexFormats.COLOR_4UB)
        .addElement(DefaultVertexFormats.TEX_2F)
        .addElement(DefaultVertexFormats.TEX_2S)
        .addElement(DefaultVertexFormats.NORMAL_3B)
        .addElement(DefaultVertexFormats.PADDING_1B)
        .addElement(ENTITY_ELEMENT)
        .addElement(MID_TEXTURE_ELEMENT)
        .addElement(TANGENT_ELEMENT)
        .addElement(MID_BLOCK_ELEMENT);

    public static final VertexFormat ITEM = new VertexFormat()
        .addElement(DefaultVertexFormats.POSITION_3F)
        .addElement(DefaultVertexFormats.COLOR_4UB)
        .addElement(DefaultVertexFormats.TEX_2F)
        .addElement(DefaultVertexFormats.NORMAL_3B)
        .addElement(DefaultVertexFormats.PADDING_1B)
        .addElement(MID_TEXTURE_ELEMENT)
        .addElement(TANGENT_ELEMENT);

    public static final VertexFormat OLD_MODEL = new VertexFormat()
        .addElement(DefaultVertexFormats.POSITION_3F)
        .addElement(DefaultVertexFormats.TEX_2F)
        .addElement(DefaultVertexFormats.NORMAL_3B)
        .addElement(DefaultVertexFormats.PADDING_1B)
        .addElement(MID_TEXTURE_ELEMENT)
        .addElement(TANGENT_ELEMENT);

    public static final VertexFormat POSITION_TEX_LMAP_COLOR = new VertexFormat()
        .addElement(DefaultVertexFormats.POSITION_3F)
        .addElement(DefaultVertexFormats.TEX_2F)
        .addElement(DefaultVertexFormats.TEX_2S)
        .addElement(DefaultVertexFormats.COLOR_4UB)
        .addElement(DefaultVertexFormats.NORMAL_3B)
        .addElement(DefaultVertexFormats.PADDING_1B)
        .addElement(MID_TEXTURE_ELEMENT)
        .addElement(TANGENT_ELEMENT);

    public static final VertexFormat POSITION_TEX_COLOR_NORMAL = new VertexFormat()
        .addElement(DefaultVertexFormats.POSITION_3F)
        .addElement(DefaultVertexFormats.TEX_2F)
        .addElement(DefaultVertexFormats.COLOR_4UB)
        .addElement(DefaultVertexFormats.NORMAL_3B)
        .addElement(DefaultVertexFormats.PADDING_1B)
        .addElement(MID_TEXTURE_ELEMENT)
        .addElement(TANGENT_ELEMENT);

    private OculusLegacyVertexFormats() {
    }

    public static Layout getExtendedLayout(VertexFormat format) {
        if (format == DefaultVertexFormats.BLOCK || format == TERRAIN) {
            return Layout.TERRAIN;
        }
        if (format == DefaultVertexFormats.ITEM || format == ITEM) {
            return Layout.ITEM;
        }
        if (format == DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL
            || format == DefaultVertexFormats.POSITION_TEX_NORMAL
            || format == OLD_MODEL) {
            return Layout.OLD_MODEL;
        }
        if (format == DefaultVertexFormats.POSITION_TEX_LMAP_COLOR || format == POSITION_TEX_LMAP_COLOR) {
            return Layout.POSITION_TEX_LMAP_COLOR;
        }
        if (format == DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL || format == POSITION_TEX_COLOR_NORMAL) {
            return Layout.POSITION_TEX_COLOR_NORMAL;
        }

        return null;
    }

    public enum Layout {
        TERRAIN(OculusLegacyVertexFormats.TERRAIN, DefaultVertexFormats.BLOCK, 52, 28,
            0, 16, 20, 28, 32, 36, 40, 44, 48, true),
        ITEM(OculusLegacyVertexFormats.ITEM, DefaultVertexFormats.ITEM, 40, 28,
            0, 16, 20, 24, -1, 28, 32, 36, -1, false),
        OLD_MODEL(OculusLegacyVertexFormats.OLD_MODEL, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL, 36, 24,
            0, 12, 16, 20, -1, 24, 28, 32, -1, false),
        POSITION_TEX_LMAP_COLOR(OculusLegacyVertexFormats.POSITION_TEX_LMAP_COLOR,
            DefaultVertexFormats.POSITION_TEX_LMAP_COLOR, 44, 28,
            0, 12, 16, 28, -1, 32, 36, 40, -1, false),
        POSITION_TEX_COLOR_NORMAL(OculusLegacyVertexFormats.POSITION_TEX_COLOR_NORMAL,
            DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL, 40, 28,
            0, 12, 16, 24, -1, 28, 32, 36, -1, false);

        public final VertexFormat format;
        public final VertexFormat baseFormat;
        public final int stride;
        public final int baseStride;
        public final int positionOffset;
        public final int uOffset;
        public final int vOffset;
        public final int normalOffset;
        public final int entityOffset;
        public final int midUOffset;
        public final int midVOffset;
        public final int tangentOffset;
        public final int midBlockOffset;
        public final boolean terrain;

        Layout(VertexFormat format, VertexFormat baseFormat, int stride, int baseStride,
               int positionOffset, int uOffset, int vOffset, int normalOffset,
               int entityOffset, int midUOffset, int midVOffset, int tangentOffset,
               int midBlockOffset, boolean terrain) {
            this.format = format;
            this.baseFormat = baseFormat;
            this.stride = stride;
            this.baseStride = baseStride;
            this.positionOffset = positionOffset;
            this.uOffset = uOffset;
            this.vOffset = vOffset;
            this.normalOffset = normalOffset;
            this.entityOffset = entityOffset;
            this.midUOffset = midUOffset;
            this.midVOffset = midVOffset;
            this.tangentOffset = tangentOffset;
            this.midBlockOffset = midBlockOffset;
            this.terrain = terrain;
        }

        public int extendedInts() {
            return stride / Integer.BYTES;
        }

        public int baseInts() {
            return baseStride / Integer.BYTES;
        }
    }
}
