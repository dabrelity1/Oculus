package com.mojang.blaze3d.vertex;

/**
 * Simplified version of the default vertex elements used by newer Minecraft versions.
 */
public final class DefaultVertexFormat {
    public static final VertexFormatElement ELEMENT_POSITION =
            new VertexFormatElement(0, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.POSITION, 3);
    public static final VertexFormatElement ELEMENT_COLOR =
            new VertexFormatElement(1, VertexFormatElement.Type.UNSIGNED_BYTE, VertexFormatElement.Usage.COLOR, 4);
    public static final VertexFormatElement ELEMENT_UV0 =
            new VertexFormatElement(2, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_UV1 =
            new VertexFormatElement(3, VertexFormatElement.Type.FLOAT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_UV2 =
            new VertexFormatElement(4, VertexFormatElement.Type.SHORT, VertexFormatElement.Usage.UV, 2);
    public static final VertexFormatElement ELEMENT_NORMAL =
            new VertexFormatElement(5, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.NORMAL, 3);
    public static final VertexFormatElement ELEMENT_PADDING =
            new VertexFormatElement(6, VertexFormatElement.Type.BYTE, VertexFormatElement.Usage.PADDING, 1);

    private DefaultVertexFormat() {
    }
}
