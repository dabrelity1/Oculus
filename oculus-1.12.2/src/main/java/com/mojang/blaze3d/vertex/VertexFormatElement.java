package com.mojang.blaze3d.vertex;

/**
 * Modern-name compatibility element used by the Oculus terrain vertex-format bridge.
 */
public class VertexFormatElement {
    private final int index;
    private final Type type;
    private final Usage usage;
    private final int componentCount;
    private final int byteSize;

    public VertexFormatElement(int index, Type type, Usage usage, int componentCount) {
        this.index = index;
        this.type = type;
        this.usage = usage;
        this.componentCount = componentCount;
        this.byteSize = componentCount * type.getPrimitiveSize();
    }

    public int getIndex() {
        return index;
    }

    public Type getType() {
        return type;
    }

    public Usage getUsage() {
        return usage;
    }

    public int getComponentCount() {
        return componentCount;
    }

    public int getByteSize() {
        return byteSize;
    }

    public enum Type {
        FLOAT(4),
        UNSIGNED_BYTE(1),
        UNSIGNED_SHORT(2),
        BYTE(1),
        SHORT(2);

        private final int primitiveSize;

        Type(int primitiveSize) {
            this.primitiveSize = primitiveSize;
        }

        int getPrimitiveSize() {
            return primitiveSize;
        }
    }

    public enum Usage {
        POSITION,
        COLOR,
        UV,
        NORMAL,
        PADDING,
        GENERIC
    }
}
