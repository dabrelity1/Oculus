package net.oculus.texture.mipmap;

public abstract class AbstractMipmapGenerator implements CustomMipmapGenerator {
    @Override
    public int[][] generateMipLevels(int[] image, int width, int height, int mipLevel) {
        int[][] images = new int[mipLevel + 1][];
        images[0] = image;

        int previousWidth = width;
        int previousHeight = height;
        for (int level = 1; level <= mipLevel; level++) {
            int[] previous = images[level - 1];
            int levelWidth = Math.max(1, previousWidth >> 1);
            int levelHeight = Math.max(1, previousHeight >> 1);
            int[] mipmap = new int[levelWidth * levelHeight];

            for (int y = 0; y < levelHeight; y++) {
                for (int x = 0; x < levelWidth; x++) {
                    int x0 = Math.min(previousWidth - 1, x * 2);
                    int y0 = Math.min(previousHeight - 1, y * 2);
                    int x1 = Math.min(previousWidth - 1, x0 + 1);
                    int y1 = Math.min(previousHeight - 1, y0 + 1);

                    mipmap[y * levelWidth + x] = blend(
                        previous[y0 * previousWidth + x0],
                        previous[y0 * previousWidth + x1],
                        previous[y1 * previousWidth + x0],
                        previous[y1 * previousWidth + x1]);
                }
            }

            images[level] = mipmap;
            previousWidth = levelWidth;
            previousHeight = levelHeight;
        }

        return images;
    }

    public abstract int blend(int c0, int c1, int c2, int c3);
}
