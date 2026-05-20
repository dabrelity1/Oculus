package net.oculus.gl.state;

/**
 * Global hooks that shader uniforms use to listen for GL state changes. Values
 * are wired up by the renderer when that state mutates (fog, blending, etc.).
 */
public final class StateUpdateNotifiers {
    private static final FanOutValueUpdateNotifier blendFuncNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier bindTextureNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier fogToggleNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier fogModeNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier fogStartNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier fogEndNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier fogDensityNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier normalTextureChangeNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier specularTextureChangeNotifierImpl = new FanOutValueUpdateNotifier();
    private static final FanOutValueUpdateNotifier phaseChangeNotifierImpl = new FanOutValueUpdateNotifier();

    private StateUpdateNotifiers() {
    }

    public static final ValueUpdateNotifier fogToggleNotifier = fogToggleNotifierImpl;
    public static final ValueUpdateNotifier fogModeNotifier = fogModeNotifierImpl;
    public static final ValueUpdateNotifier fogStartNotifier = fogStartNotifierImpl;
    public static final ValueUpdateNotifier fogEndNotifier = fogEndNotifierImpl;
    public static final ValueUpdateNotifier fogDensityNotifier = fogDensityNotifierImpl;
    public static final ValueUpdateNotifier blendFuncNotifier = blendFuncNotifierImpl;
    public static final ValueUpdateNotifier bindTextureNotifier = bindTextureNotifierImpl;
    public static final ValueUpdateNotifier normalTextureChangeNotifier = normalTextureChangeNotifierImpl;
    public static final ValueUpdateNotifier specularTextureChangeNotifier = specularTextureChangeNotifierImpl;
    public static final ValueUpdateNotifier phaseChangeNotifier = phaseChangeNotifierImpl;

    public static ValueUpdateNotifier fogModeNotifierWithToggle() {
        return withFogToggle(fogModeNotifier);
    }

    public static ValueUpdateNotifier fogStartNotifierWithToggle() {
        return withFogToggle(fogStartNotifier);
    }

    public static ValueUpdateNotifier fogEndNotifierWithToggle() {
        return withFogToggle(fogEndNotifier);
    }

    public static ValueUpdateNotifier fogDensityNotifierWithToggle() {
        return withFogToggle(fogDensityNotifier);
    }

    public static void notifyBlendFuncChanged() {
        blendFuncNotifierImpl.notifyListeners();
    }

    public static void notifyTextureBindingChanged() {
        bindTextureNotifierImpl.notifyListeners();
    }

    public static void notifyNormalTextureChanged() {
        normalTextureChangeNotifierImpl.notifyListeners();
    }

    public static void notifySpecularTextureChanged() {
        specularTextureChangeNotifierImpl.notifyListeners();
    }

    public static void notifyFogToggled() {
        fogToggleNotifierImpl.notifyListeners();
    }

    public static void notifyFogModeChanged() {
        fogModeNotifierImpl.notifyListeners();
    }

    public static void notifyFogStartChanged() {
        fogStartNotifierImpl.notifyListeners();
    }

    public static void notifyFogEndChanged() {
        fogEndNotifierImpl.notifyListeners();
    }

    public static void notifyFogDensityChanged() {
        fogDensityNotifierImpl.notifyListeners();
    }

    public static void notifyPhaseChanged() {
        phaseChangeNotifierImpl.notifyListeners();
    }

    private static ValueUpdateNotifier withFogToggle(ValueUpdateNotifier notifier) {
        return new ValueUpdateNotifier() {
            @Override
            public void setListener(Runnable listener) {
                Throwable failure = null;
                failure = runNotifierOperation(failure, () -> fogToggleNotifier.setListener(listener));
                failure = runNotifierOperation(failure, () -> notifier.setListener(listener));
                rethrowNotifierFailure(failure);
            }

            @Override
            public void removeListener(Runnable listener) {
                Throwable failure = null;
                failure = runNotifierOperation(failure, () -> fogToggleNotifier.removeListener(listener));
                failure = runNotifierOperation(failure, () -> notifier.removeListener(listener));
                rethrowNotifierFailure(failure);
            }
        };
    }

    private static Throwable runNotifierOperation(Throwable failure, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException | Error exception) {
            if (failure == null) {
                return exception;
            }
            suppressNotifierFailure(failure, exception);
        }
        return failure;
    }

    private static void suppressNotifierFailure(Throwable failure, Throwable exception) {
        if (exception != failure) {
            failure.addSuppressed(exception);
        }
    }

    private static void rethrowNotifierFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IllegalStateException("Unexpected state notifier failure", failure);
    }
}
