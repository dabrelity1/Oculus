package net.oculus.texture.pbr;

import net.minecraft.client.renderer.texture.AbstractTexture;

public interface PBRTextureHolder {
    AbstractTexture getNormalTexture();

    AbstractTexture getSpecularTexture();
}
