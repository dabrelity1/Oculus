package net.oculus.gl.program;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central registry mapping sampler uniform names to {@link TextureBinding}
 * instances. The shader pipeline can register suppliers for render targets,
 * depth buffers, or any other runtime texture and the sampler binding code
 * simply looks them up by name when uniforms are initialized.
 */
public final class TextureBindingRegistry {
    private static final Map<String, TextureBinding> BINDINGS = new ConcurrentHashMap<>();

    private static final Supplier<TextureBinding> DEFAULT_BINDING = TextureBinding::unbound;

    private TextureBindingRegistry() {
    }

    public static void register(String samplerName, TextureBinding binding) {
        if (samplerName == null || binding == null) {
            return;
        }
        BINDINGS.put(samplerName, binding);
    }

    public static TextureBinding resolve(String samplerName) {
        if (samplerName == null) {
            return DEFAULT_BINDING.get();
        }
        return BINDINGS.getOrDefault(samplerName, DEFAULT_BINDING.get());
    }

    public static void unregister(String samplerName) {
        if (samplerName == null) {
            return;
        }
        BINDINGS.remove(samplerName);
    }

    public static void unregister(String samplerName, TextureBinding binding) {
        if (samplerName == null || binding == null) {
            return;
        }
        BINDINGS.remove(samplerName, binding);
    }

    public static void clear() {
        BINDINGS.clear();
    }
}
