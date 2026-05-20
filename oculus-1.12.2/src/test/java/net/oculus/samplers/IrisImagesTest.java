package net.oculus.samplers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.concurrent.atomic.AtomicReference;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.RenderTarget;
import net.oculus.gl.image.ImageHolder;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.pipeline.shadow.ShadowMap;
import net.oculus.rendertarget.RenderTargets;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.util.Config;
import org.junit.Test;
import sun.misc.Unsafe;

public class IrisImagesTest {
    @Test
    public void shadowColorImagesBindFromPresentShadowTargetMetadataEvenWhenRendererDisabled() {
        ShaderProperties properties = new ShaderProperties("shadow.enabled=false\n");
        PackDirectives directives = new PackDirectives(properties);
        ShadowMap shadowMap = new ShadowMap(directives, properties, Config.get(), false);
        CapturingImageHolder images = new CapturingImageHolder();

        assertFalse(shadowMap.isEnabled());

        IrisImages.addShadowColorImages(images, shadowMap);

        assertEquals(2, images.names.size());
        assertEquals("shadowcolorimg0", images.names.get(0));
        assertEquals("shadowcolorimg1", images.names.get(1));
        assertEquals(InternalTextureFormat.RGBA, images.formats.get(0));
        assertEquals(InternalTextureFormat.RGBA, images.formats.get(1));
    }

    @Test
    public void shadowColorImageSuppliersFollowFlippedShadowTargetState() throws Exception {
        ShaderProperties properties = new ShaderProperties("shadow.enabled=false\n");
        PackDirectives directives = new PackDirectives(properties);
        ShadowMap shadowMap = new ShadowMap(directives, properties, Config.get(), false);
        CapturingImageHolder images = new CapturingImageHolder();

        setTextureIds(shadowMap, "colorTextures", 101, 102);
        setTextureIds(shadowMap, "altColorTextures", 201, 202);

        IrisImages.addShadowColorImages(images, shadowMap);

        assertEquals(101, images.textureIds.get(0).getAsInt());
        assertEquals(102, images.textureIds.get(1).getAsInt());

        shadowMap.flipColorTexture(0);
        shadowMap.flipColorTexture(1);

        assertEquals(201, images.textureIds.get(0).getAsInt());
        assertEquals(202, images.textureIds.get(1).getAsInt());
    }

    @Test
    public void renderTargetImageSuppliersLookUpCurrentTargetWhenUpdated() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201, InternalTextureFormat.RGBA8)
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        AtomicReference<Set<Integer>> flipped = new AtomicReference<>(Collections.emptySet());
        CapturingImageHolder images = new CapturingImageHolder("colorimg0");

        IrisImages.addRenderTargetImages(images, flipped::get, renderTargets);

        assertEquals(1, images.names.size());
        assertEquals("colorimg0", images.names.get(0));
        assertEquals(InternalTextureFormat.RGBA8, images.formats.get(0));
        assertEquals(101, images.textureIds.get(0).getAsInt());

        flipped.set(Collections.singleton(0));
        assertEquals(201, images.textureIds.get(0).getAsInt());

        targets[0] = fakeRenderTarget(102, 202, InternalTextureFormat.RGBA16);

        flipped.set(Collections.emptySet());
        assertEquals(102, images.textureIds.get(0).getAsInt());
        flipped.set(Collections.singleton(0));
        assertEquals(202, images.textureIds.get(0).getAsInt());
    }

    @Test
    public void renderTargetImageSuppliersFailFastIfRegisteredTargetDisappears() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201, InternalTextureFormat.RGBA8)
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        CapturingImageHolder images = new CapturingImageHolder("colorimg0");

        IrisImages.addRenderTargetImages(images, Collections::emptySet, renderTargets);

        targets[0] = null;
        try {
            images.textureIds.get(0).getAsInt();
        } catch (IllegalStateException ex) {
            assertEquals("Render target colortex0 is not configured", ex.getMessage());
            return;
        }

        throw new AssertionError("Expected disappeared render target image binding to fail fast");
    }

    @Test
    public void renderTargetImagesFailFastWhenProgramReferencesUnconfiguredSparseTarget() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201, InternalTextureFormat.RGBA8),
            null
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        CapturingImageHolder images = new CapturingImageHolder("colorimg0", "colorimg1");

        try {
            IrisImages.addRenderTargetImages(images, Collections::emptySet, renderTargets);
        } catch (IllegalStateException ex) {
            assertEquals("Render target colortex1 is not configured", ex.getMessage());
            assertEquals(1, images.names.size());
            assertEquals("colorimg0", images.names.get(0));
            return;
        }

        throw new AssertionError("Expected unconfigured render target image binding to fail fast");
    }

    @Test
    public void renderTargetImagesFailFastWhenProgramReferencesImageBeyondConfiguredTargetCount() throws Exception {
        RenderTarget[] targets = new RenderTarget[] {
            fakeRenderTarget(101, 201, InternalTextureFormat.RGBA8)
        };
        RenderTargets renderTargets = fakeRenderTargets(targets);
        CapturingImageHolder images = new CapturingImageHolder("colorimg0", "colorimg2");

        try {
            IrisImages.addRenderTargetImages(images, Collections::emptySet, renderTargets);
        } catch (IllegalStateException ex) {
            assertEquals("Render target colortex2 is not configured", ex.getMessage());
            assertEquals(1, images.names.size());
            assertEquals("colorimg0", images.names.get(0));
            return;
        }

        throw new AssertionError("Expected out-of-range render target image binding to fail fast");
    }

    @Test
    public void renderTargetImagesFailFastWhenProgramReferencesUnsupportedImageIndex() throws Exception {
        String[] unsupportedImages = {
            "colorimg16",
            "colorimg99",
            "colorimg100"
        };

        for (String unsupportedImage : unsupportedImages) {
            RenderTarget[] targets = new RenderTarget[] {
                fakeRenderTarget(101, 201, InternalTextureFormat.RGBA8)
            };
            RenderTargets renderTargets = fakeRenderTargets(targets);
            CapturingImageHolder images = new CapturingImageHolder("colorimg0", unsupportedImage);

            try {
                IrisImages.addRenderTargetImages(images, Collections::emptySet, renderTargets);
            } catch (IllegalStateException ex) {
                String index = unsupportedImage.endsWith("100")
                    ? "100"
                    : unsupportedImage.endsWith("99") ? "99" : "16";
                assertEquals("Render target colortex" + index
                    + " is not supported; Oculus 1.16.5 exposes up to 16 color render targets.",
                    ex.getMessage());
                assertEquals(1, images.names.size());
                assertEquals("colorimg0", images.names.get(0));
                continue;
            }

            throw new AssertionError("Expected unsupported render target image " + unsupportedImage
                + " to fail fast");
        }
    }

    @Test
    public void renderTargetImagesFailFastWhenRenderTargetsAreUnavailable() {
        CapturingImageHolder missingColor = new CapturingImageHolder("colorimg0");
        boolean missingFailed = false;
        try {
            IrisImages.addRenderTargetImages(missingColor, Collections::emptySet, null);
        } catch (IllegalStateException ex) {
            missingFailed = true;
            assertEquals("Render target colortex0 is not configured", ex.getMessage());
            assertEquals(0, missingColor.names.size());
        }
        if (!missingFailed) {
            throw new AssertionError("Expected unavailable render targets to fail for active colorimg0");
        }

        CapturingImageHolder unsupportedColor = new CapturingImageHolder("colorimg16");
        boolean unsupportedFailed = false;
        try {
            IrisImages.addRenderTargetImages(unsupportedColor, Collections::emptySet, null);
        } catch (IllegalStateException ex) {
            unsupportedFailed = true;
            assertEquals("Render target colortex16"
                    + " is not supported; Oculus 1.16.5 exposes up to 16 color render targets.",
                ex.getMessage());
            assertEquals(0, unsupportedColor.names.size());
        }
        if (!unsupportedFailed) {
            throw new AssertionError("Expected unavailable render targets to fail for active render-target images");
        }
    }

    @Test
    public void shadowColorImagesFailFastWhenShadowTargetsAreUnavailable() {
        CapturingImageHolder noShadowImages = new CapturingImageHolder();
        IrisImages.addShadowColorImages(noShadowImages, null);
        assertEquals(0, noShadowImages.names.size());

        CapturingImageHolder shadowImages = new CapturingImageHolder("shadowcolorimg0");
        try {
            IrisImages.addShadowColorImages(shadowImages, null);
        } catch (IllegalStateException ex) {
            assertEquals("Shadow color images require configured shadow targets", ex.getMessage());
            assertEquals(0, shadowImages.names.size());
            return;
        }

        throw new AssertionError("Expected unavailable shadow targets to fail for active shadow images");
    }

    @Test
    public void shadowColorImagesFailFastWhenProgramReferencesUnsupportedImageIndex() {
        String[] unsupportedImages = {
            "shadowcolorimg2",
            "shadowcolorimg99",
            "shadowcolorimg100"
        };
        ShaderProperties properties = new ShaderProperties("shadow.enabled=false\n");
        PackDirectives directives = new PackDirectives(properties);
        ShadowMap shadowMap = new ShadowMap(directives, properties, Config.get(), false);

        for (String unsupportedImage : unsupportedImages) {
            CapturingImageHolder images = new CapturingImageHolder(unsupportedImage);
            try {
                IrisImages.addShadowColorImages(images, null);
            } catch (IllegalStateException ex) {
                assertEquals("Shadow color image " + unsupportedImage
                    + " is not supported; Oculus 1.16.5 exposes two shadow color targets.",
                    ex.getMessage());
                assertEquals(0, images.names.size());
                continue;
            }

            throw new AssertionError("Expected unsupported shadow color image " + unsupportedImage
                + " to fail fast");
        }

        CapturingImageHolder images = new CapturingImageHolder("shadowcolorimg100");
        try {
            IrisImages.addShadowColorImages(images, shadowMap);
        } catch (IllegalStateException ex) {
            assertEquals("Shadow color image shadowcolorimg100"
                + " is not supported; Oculus 1.16.5 exposes two shadow color targets.",
                ex.getMessage());
            assertEquals(0, images.names.size());
            return;
        }

        throw new AssertionError("Expected unsupported shadow image binding to fail before registration");
    }

    private static final class CapturingImageHolder implements ImageHolder {
        private final List<String> names = new ArrayList<>();
        private final List<InternalTextureFormat> formats = new ArrayList<>();
        private final List<IntSupplier> textureIds = new ArrayList<>();
        private final Set<String> activeImages;

        private CapturingImageHolder(String... activeImages) {
            this.activeImages = new HashSet<>(Arrays.asList(activeImages));
        }

        @Override
        public boolean hasImage(String name) {
            return activeImages.contains(name);
        }

        @Override
        public Set<String> getActiveImageNames() {
            return activeImages;
        }

        @Override
        public void addTextureImage(IntSupplier textureId, InternalTextureFormat internalFormat, String name) {
            textureIds.add(textureId);
            names.add(name);
            formats.add(internalFormat);
        }
    }

    private static void setTextureIds(ShadowMap shadowMap, String fieldName, int first, int second) throws Exception {
        Field field = ShadowMap.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        int[] textures = (int[]) field.get(shadowMap);
        textures[0] = first;
        textures[1] = second;
    }

    private static RenderTargets fakeRenderTargets(RenderTarget[] targets) throws Exception {
        RenderTargets renderTargets = allocate(RenderTargets.class);
        setField(renderTargets, "targets", targets);
        return renderTargets;
    }

    private static RenderTarget fakeRenderTarget(int mainTexture, int altTexture,
                                                 InternalTextureFormat format) throws Exception {
        RenderTarget target = allocate(RenderTarget.class);
        setField(target, "valid", true);
        setField(target, "mainTexture", mainTexture);
        setField(target, "altTexture", altTexture);
        setField(target, "internalFormat", format);
        return target;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }
}
