package net.oculus.shaderpack.texture;

import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.gl.texture.PixelFormat;
import net.oculus.gl.texture.PixelType;

public final class CustomImageData {
    private final String imageName;
    private final String samplerName;
    private final PixelFormat pixelFormat;
    private final InternalTextureFormat internalFormat;
    private final PixelType pixelType;
    private final boolean clearOnNewFrame;
    private final boolean relative;
    private final String widthExpression;
    private final String heightExpression;
    private final String depthExpression;

    public CustomImageData(String imageName,
                           String samplerName,
                           PixelFormat pixelFormat,
                           InternalTextureFormat internalFormat,
                           PixelType pixelType,
                           boolean clearOnNewFrame,
                           boolean relative,
                           String widthExpression,
                           String heightExpression,
                           String depthExpression) {
        this.imageName = imageName;
        this.samplerName = samplerName;
        this.pixelFormat = pixelFormat;
        this.internalFormat = internalFormat;
        this.pixelType = pixelType;
        this.clearOnNewFrame = clearOnNewFrame;
        this.relative = relative;
        this.widthExpression = widthExpression;
        this.heightExpression = heightExpression;
        this.depthExpression = depthExpression;
    }

    public String getImageName() {
        return imageName;
    }

    public String getSamplerName() {
        return samplerName;
    }

    public PixelFormat getPixelFormat() {
        return pixelFormat;
    }

    public InternalTextureFormat getInternalFormat() {
        return internalFormat;
    }

    public PixelType getPixelType() {
        return pixelType;
    }

    public boolean shouldClearOnNewFrame() {
        return clearOnNewFrame;
    }

    public boolean isRelative() {
        return relative;
    }

    public String getWidthExpression() {
        return widthExpression;
    }

    public String getHeightExpression() {
        return heightExpression;
    }

    public String getDepthExpression() {
        return depthExpression;
    }

    public boolean isThreeDimensional() {
        return depthExpression != null;
    }
}
