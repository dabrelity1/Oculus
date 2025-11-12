package com.mojang.blaze3d.vertex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Lightweight placeholder for the modern VertexFormat class used by Iris/Oculus.
 */
public class VertexFormat {
    private final List<VertexFormatElement> elements;

    public VertexFormat(List<VertexFormatElement> elements) {
        this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
    }

    public List<VertexFormatElement> getElements() {
        return elements;
    }

    public int getVertexSize() {
        int size = 0;

        for (VertexFormatElement element : elements) {
            size += element.getByteSize();
        }

        return size;
    }
}
