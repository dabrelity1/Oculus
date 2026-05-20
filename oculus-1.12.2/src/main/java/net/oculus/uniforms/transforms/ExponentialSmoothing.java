package net.oculus.uniforms.transforms;

/**
 * Shared Iris-compatible exponential smoothing math.
 */
public final class ExponentialSmoothing {
    private static final double LN_OF_2 = Math.log(2.0);

    private ExponentialSmoothing() {
    }

    public static float decayFromHalfLifeSeconds(float halfLifeSeconds) {
        return (float) (1.0F / (halfLifeSeconds / LN_OF_2));
    }

    public static double decayFromHalfLifeSeconds(double halfLifeSeconds) {
        return 1.0 / (halfLifeSeconds / LN_OF_2);
    }
}
