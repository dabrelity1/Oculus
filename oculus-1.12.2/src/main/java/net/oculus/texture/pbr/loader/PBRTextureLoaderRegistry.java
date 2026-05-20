package net.oculus.texture.pbr.loader;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureMap;

public final class PBRTextureLoaderRegistry {
    public static final PBRTextureLoaderRegistry INSTANCE = new PBRTextureLoaderRegistry();

    static {
        INSTANCE.register(SimpleTexture.class, new SimplePBRLoader());
        INSTANCE.register(TextureMap.class, new AtlasPBRLoader());
    }

    private final Map<Class<?>, PBRTextureLoader<?>> loaderMap = new HashMap<>();

    private PBRTextureLoaderRegistry() {
    }

    public <T extends AbstractTexture> void register(Class<? extends T> clazz, PBRTextureLoader<T> loader) {
        loaderMap.put(clazz, loader);
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractTexture> PBRTextureLoader<T> getLoader(Class<? extends T> clazz) {
        Class<?> current = clazz;
        while (current != null && AbstractTexture.class.isAssignableFrom(current)) {
            PBRTextureLoader<?> loader = loaderMap.get(current);
            if (loader != null) {
                return (PBRTextureLoader<T>) loader;
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
