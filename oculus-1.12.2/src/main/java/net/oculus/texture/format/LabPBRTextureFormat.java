package net.oculus.texture.format;

import java.util.Objects;

import net.oculus.texture.mipmap.ChannelMipmapGenerator;
import net.oculus.texture.mipmap.CustomMipmapGenerator;
import net.oculus.texture.mipmap.DiscreteBlendFunction;
import net.oculus.texture.mipmap.LinearBlendFunction;
import net.oculus.texture.pbr.PBRType;

public final class LabPBRTextureFormat implements TextureFormat {
    public static final ChannelMipmapGenerator SPECULAR_MIPMAP_GENERATOR = new ChannelMipmapGenerator(
        LinearBlendFunction.INSTANCE,
        new DiscreteBlendFunction(v -> v < 230 ? 0 : v - 229),
        new DiscreteBlendFunction(v -> v < 65 ? 0 : 1),
        new DiscreteBlendFunction(v -> v < 255 ? 0 : 1));

    private final String name;
    private final String version;

    public LabPBRTextureFormat(String name, String version) {
        this.name = name;
        this.version = version;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean canInterpolateValues(PBRType pbrType) {
        return pbrType != PBRType.SPECULAR;
    }

    @Override
    public CustomMipmapGenerator getMipmapGenerator(PBRType pbrType) {
        return pbrType == PBRType.SPECULAR ? SPECULAR_MIPMAP_GENERATOR : null;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof LabPBRTextureFormat)) {
            return false;
        }
        LabPBRTextureFormat other = (LabPBRTextureFormat) obj;
        return Objects.equals(name, other.name) && Objects.equals(version, other.version);
    }
}
