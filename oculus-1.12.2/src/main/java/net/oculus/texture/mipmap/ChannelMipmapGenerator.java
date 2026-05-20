package net.oculus.texture.mipmap;

public class ChannelMipmapGenerator extends AbstractMipmapGenerator {
    private final BlendFunction redFunc;
    private final BlendFunction greenFunc;
    private final BlendFunction blueFunc;
    private final BlendFunction alphaFunc;

    public ChannelMipmapGenerator(
        BlendFunction redFunc,
        BlendFunction greenFunc,
        BlendFunction blueFunc,
        BlendFunction alphaFunc) {
        this.redFunc = redFunc;
        this.greenFunc = greenFunc;
        this.blueFunc = blueFunc;
        this.alphaFunc = alphaFunc;
    }

    @Override
    public int blend(int c0, int c1, int c2, int c3) {
        int alpha = alphaFunc.blend(alpha(c0), alpha(c1), alpha(c2), alpha(c3));
        int red = redFunc.blend(red(c0), red(c1), red(c2), red(c3));
        int green = greenFunc.blend(green(c0), green(c1), green(c2), green(c3));
        int blue = blueFunc.blend(blue(c0), blue(c1), blue(c2), blue(c3));

        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    private static int alpha(int color) {
        return color >>> 24 & 0xFF;
    }

    private static int red(int color) {
        return color >>> 16 & 0xFF;
    }

    private static int green(int color) {
        return color >>> 8 & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    public interface BlendFunction {
        int blend(int v0, int v1, int v2, int v3);
    }
}
