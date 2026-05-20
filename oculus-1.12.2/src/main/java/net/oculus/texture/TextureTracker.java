package net.oculus.texture;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.renderer.texture.AbstractTexture;

public final class TextureTracker {
    public static final TextureTracker INSTANCE = new TextureTracker();

    private final Map<Integer, AbstractTexture> textures = new HashMap<>();

    private TextureTracker() {
    }

    public synchronized void trackTexture(int id, AbstractTexture texture) {
        if (id <= 0 || texture == null) {
            return;
        }

        textures.put(id, texture);
    }

    @Nullable
    public synchronized AbstractTexture getTexture(int id) {
        return textures.get(id);
    }

    public synchronized void onDeleteTexture(int id) {
        if (id <= 0) {
            return;
        }

        textures.remove(id);
    }

    synchronized void clearForTesting() {
        textures.clear();
    }
}
