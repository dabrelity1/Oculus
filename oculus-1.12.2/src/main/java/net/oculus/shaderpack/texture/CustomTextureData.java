package net.oculus.shaderpack.texture;

/**
 * Representation of a shader-pack provided custom texture. Only the metadata
 * required by the 1.12.2 runtime is modelled here; more advanced Iris formats
 * (raw uploads, 3D textures, etc.) can be introduced later as needed.
 */
public abstract class CustomTextureData {
    private CustomTextureData() {
    }

    public static final class PngData extends CustomTextureData {
        private final TextureFilteringData filteringData;
        private final byte[] content;

        public PngData(TextureFilteringData filteringData, byte[] content) {
            this.filteringData = filteringData;
            this.content = content;
        }

        public TextureFilteringData getFilteringData() {
            return filteringData;
        }

        public byte[] getContent() {
            return content;
        }
    }

    /** Marker indicating that the shader pack wants the vanilla lightmap. */
    public static final class LightmapMarker extends CustomTextureData {
        @Override
        public boolean equals(Object obj) {
            return obj instanceof LightmapMarker;
        }

        @Override
        public int hashCode() {
            return 9183;
        }
    }

    /** Reference to an in-game resource location (namespace:path). */
    public static final class ResourceData extends CustomTextureData {
        private final String namespace;
        private final String location;

        public ResourceData(String namespace, String location) {
            this.namespace = namespace;
            this.location = location;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getLocation() {
            return location;
        }
    }
}
