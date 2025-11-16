package net.oculus.gl.state;

/**
 * Global hooks that shader uniforms use to listen for GL state changes. Values
 * are wired up by the renderer when that state mutates (fog, blending, etc.).
 */
public final class StateUpdateNotifiers {
    private StateUpdateNotifiers() {
    }

    public static ValueUpdateNotifier fogToggleNotifier;
    public static ValueUpdateNotifier fogModeNotifier;
    public static ValueUpdateNotifier fogStartNotifier;
    public static ValueUpdateNotifier fogEndNotifier;
    public static ValueUpdateNotifier fogDensityNotifier;
    public static ValueUpdateNotifier blendFuncNotifier;
    public static ValueUpdateNotifier bindTextureNotifier;
    public static ValueUpdateNotifier normalTextureChangeNotifier;
    public static ValueUpdateNotifier specularTextureChangeNotifier;
    public static ValueUpdateNotifier phaseChangeNotifier;
}
