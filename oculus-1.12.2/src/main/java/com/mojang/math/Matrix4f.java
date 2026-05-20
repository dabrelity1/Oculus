package com.mojang.math;

import java.util.Arrays;

/**
 * Modern-name compatibility value type for copied code that only needs a 16-float
 * matrix container. Active shader uniform matrix math lives under net.oculus.
 */
public class Matrix4f {
    private final float[] values = new float[16];

    public Matrix4f() {
        setIdentity();
    }

    public static Matrix4f createIdentity() {
        return new Matrix4f();
    }

    public void setIdentity() {
        Arrays.fill(values, 0.0F);
        values[0] = values[5] = values[10] = values[15] = 1.0F;
    }

    public float[] getValues() {
        return values.clone();
    }
}
