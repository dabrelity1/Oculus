package net.oculus.texture.pbr.loader;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;

public interface PBRTextureLoader<T extends AbstractTexture> {
    void load(T texture, IResourceManager resourceManager, PBRTextureConsumer pbrTextureConsumer);

    interface PBRTextureConsumer {
        void acceptNormalTexture(AbstractTexture texture);

        void acceptSpecularTexture(AbstractTexture texture);
    }
}
