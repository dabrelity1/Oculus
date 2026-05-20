package net.oculus.samplers;

import java.util.Collections;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import net.oculus.gl.image.ImageHolder;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.shaderpack.IrisLimits;

/**
 * Mirrors the Iris image-uniform binding helpers for render targets and shadow color
 * targets. Image bindings follow buffer flips, just like their paired sampler bindings.
 */
public final class IrisImages {
    private static final int FIRST_UNSUPPORTED_RENDER_TARGET_IMAGE = IrisLimits.MAX_COLOR_BUFFERS;
    private static final int MAX_UNSUPPORTED_RENDER_TARGET_IMAGE_SCAN = 99;
    private static final int FIRST_UNSUPPORTED_SHADOW_IMAGE = 2;
    private static final int MAX_UNSUPPORTED_SHADOW_IMAGE_SCAN = 99;

    private IrisImages() {
    }

    public static void addRenderTargetImages(ImageHolder images,
                                             Supplier<? extends Set<Integer>> flipped,
                                             RenderTargets renderTargets) {
        if (images == null) {
            return;
        }
        if (renderTargets == null) {
            failIfProgramReferencesMissingRenderTargetImages(images, 0);
            return;
        }

        Supplier<? extends Set<Integer>> safeFlipped = flipped == null ? Collections::emptySet : flipped;
        int renderTargetCount = renderTargets.getRenderTargetCount();
        for (int i = 0; i < renderTargetCount; i++) {
            String name = "colorimg" + i;
            RenderTarget target = renderTargets.get(i);
            if (target == null) {
                if (images.hasImage(name)) {
                    throw new IllegalStateException("Render target colortex" + i + " is not configured");
                }
                continue;
            }

            final int index = i;
            IntSupplier textureId = () -> {
                RenderTarget currentTarget = renderTargets.get(index);
                if (currentTarget == null) {
                    throw new IllegalStateException("Render target colortex" + index + " is not configured");
                }

                Set<Integer> flippedBuffers = safeFlipped.get();
                if (flippedBuffers != null && flippedBuffers.contains(index)) {
                    return currentTarget.getAltTexture();
                }
                return currentTarget.getMainTexture();
            };

            InternalTextureFormat internalFormat = target.getInternalFormat();
            images.addTextureImage(textureId, internalFormat, name);
        }

        failIfProgramReferencesMissingRenderTargetImages(images, renderTargetCount);
    }

    private static void failIfProgramReferencesMissingRenderTargetImages(ImageHolder images, int renderTargetCount) {
        for (int i = renderTargetCount; i < IrisLimits.MAX_COLOR_BUFFERS; i++) {
            if (images.hasImage("colorimg" + i)) {
                throw new IllegalStateException("Render target colortex" + i + " is not configured");
            }
        }
        failIfProgramReferencesUnsupportedRenderTargetImages(images);
    }

    private static void failIfProgramReferencesUnsupportedRenderTargetImages(ImageHolder images) {
        String unsupportedResource = findUnsupportedRenderTargetImage(images);
        if (unsupportedResource != null) {
            throw new IllegalStateException("Render target " + unsupportedResource
                + " is not supported; Oculus 1.16.5 exposes up to "
                + IrisLimits.MAX_COLOR_BUFFERS + " color render targets.");
        }
    }

    private static String findUnsupportedRenderTargetImage(ImageHolder images) {
        for (String name : images.getActiveImageNames()) {
            int index = colorImageIndex(name);
            if (index >= IrisLimits.MAX_COLOR_BUFFERS) {
                return "colortex" + index;
            }
        }

        for (int i = FIRST_UNSUPPORTED_RENDER_TARGET_IMAGE; i <= MAX_UNSUPPORTED_RENDER_TARGET_IMAGE_SCAN; i++) {
            if (images.hasImage("colorimg" + i)) {
                return "colortex" + i;
            }
        }
        return null;
    }

    private static int colorImageIndex(String name) {
        if (name == null || !name.startsWith("colorimg") || name.length() == "colorimg".length()) {
            return -1;
        }
        long value = 0L;
        for (int i = "colorimg".length(); i < name.length(); i++) {
            char character = name.charAt(i);
            if (character < '0' || character > '9') {
                return -1;
            }
            value = value * 10 + character - '0';
            if (value > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) value;
    }

    public static boolean hasShadowImages(ImageHolder images) {
        return images != null && (images.hasImage("shadowcolorimg0")
            || images.hasImage("shadowcolorimg1")
            || findUnsupportedShadowImage(images) != null);
    }

    public static void addShadowColorImages(ImageHolder images, ShadowMap shadowMap) {
        if (images == null) {
            return;
        }

        failIfProgramReferencesUnsupportedShadowImages(images);

        if (shadowMap == null) {
            if (hasShadowImages(images)) {
                throw new IllegalStateException("Shadow color images require configured shadow targets");
            }
            return;
        }

        for (int i = 0; i < shadowMap.getColorTextureCount(); i++) {
            final int index = i;
            images.addTextureImage(
                () -> shadowMap.getColorTexture(index),
                shadowMap.getColorTextureFormat(index),
                "shadowcolorimg" + index
            );
        }
    }

    private static void failIfProgramReferencesUnsupportedShadowImages(ImageHolder images) {
        String unsupportedResource = findUnsupportedShadowImage(images);
        if (unsupportedResource != null) {
            throw new IllegalStateException("Shadow color image " + unsupportedResource
                + " is not supported; Oculus 1.16.5 exposes two shadow color targets.");
        }
    }

    private static String findUnsupportedShadowImage(ImageHolder images) {
        for (String name : images.getActiveImageNames()) {
            int index = shadowImageIndex(name);
            if (index >= FIRST_UNSUPPORTED_SHADOW_IMAGE) {
                return name;
            }
        }

        for (int i = FIRST_UNSUPPORTED_SHADOW_IMAGE; i <= MAX_UNSUPPORTED_SHADOW_IMAGE_SCAN; i++) {
            String name = "shadowcolorimg" + i;
            if (images.hasImage(name)) {
                return name;
            }
        }
        return null;
    }

    private static int shadowImageIndex(String name) {
        if (name == null || !name.startsWith("shadowcolorimg") || name.length() == "shadowcolorimg".length()) {
            return -1;
        }
        long value = 0L;
        for (int i = "shadowcolorimg".length(); i < name.length(); i++) {
            char character = name.charAt(i);
            if (character < '0' || character > '9') {
                return -1;
            }
            value = value * 10 + character - '0';
            if (value > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) value;
    }
}
