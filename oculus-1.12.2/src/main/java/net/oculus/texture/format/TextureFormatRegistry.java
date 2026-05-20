package net.oculus.texture.format;

import java.util.HashMap;
import java.util.Map;

public final class TextureFormatRegistry {
    public static final TextureFormatRegistry INSTANCE = new TextureFormatRegistry();

    static {
        INSTANCE.register("lab-pbr", LabPBRTextureFormat::new);
    }

    private final Map<String, TextureFormat.Factory> factoryMap = new HashMap<>();

    private TextureFormatRegistry() {
    }

    public void register(String name, TextureFormat.Factory factory) {
        factoryMap.put(name, factory);
    }

    public TextureFormat.Factory getFactory(String name) {
        return factoryMap.get(name);
    }
}
