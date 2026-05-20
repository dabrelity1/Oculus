package net.oculus.texture.pbr;

import net.minecraft.util.ResourceLocation;

/**
 * Physical-material texture suffixes used by Iris/Oculus shader packs.
 */
public enum PBRType {
    NORMAL("_n", 0x7F7FFFFF),
    SPECULAR("_s", 0x00000000);

    private static final PBRType[] VALUES = values();

    private final String suffix;
    private final int defaultValue;

    PBRType(String suffix, int defaultValue) {
        this.suffix = suffix;
        this.defaultValue = defaultValue;
    }

    public String getSuffix() {
        return suffix;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public ResourceLocation appendToFileLocation(ResourceLocation location) {
        String path = location.getPath();
        int extensionIndex = extensionIndex(path);
        String newPath;
        if (extensionIndex >= 0) {
            newPath = path.substring(0, extensionIndex) + suffix + path.substring(extensionIndex);
        } else {
            newPath = path + suffix;
        }
        return new ResourceLocation(location.getNamespace(), newPath);
    }

    public static PBRType fromFileLocation(String location) {
        if (location == null) {
            return null;
        }

        for (PBRType type : VALUES) {
            if (location.endsWith(type.getSuffix())) {
                return type;
            }
        }
        return null;
    }

    private static int extensionIndex(String path) {
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        return dot > slash ? dot : -1;
    }
}
