package net.oculus.texture.pbr.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.data.AnimationMetadataSection;
import net.minecraft.util.ResourceLocation;
import net.oculus.texture.pbr.PBRAtlasTexture;
import net.oculus.texture.pbr.PBRType;
import org.junit.Test;

public class AtlasPBRLoaderTest {
    @Test
    public void pbrImageLocationMatchesTextureMapResourcePathThenAddsSuffix() {
        assertEquals(
            new ResourceLocation("minecraft", "textures/blocks/stone_n.png"),
            AtlasPBRLoader.getPbrImageLocation("textures", new ResourceLocation("minecraft", "blocks/stone"), PBRType.NORMAL));
        assertEquals(
            new ResourceLocation("modid", "textures/items/tool_s.png"),
            AtlasPBRLoader.getPbrImageLocation("textures", new ResourceLocation("modid", "items/tool"), PBRType.SPECULAR));
    }

    @Test
    public void metadataWithoutExplicitSizeUsesSquareFramesLikeModernAtlasLoader() {
        AnimationMetadataSection metadata = new AnimationMetadataSection(Collections.emptyList(), -1, -1, 1, false);

        AtlasPBRLoader.FrameSize size = AtlasPBRLoader.getFrameSize(16, 64, metadata);

        assertEquals(16, size.width);
        assertEquals(16, size.height);
    }

    @Test
    public void integerUpscaleUsesNearestNeighborPixels() {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0x80402010);

        BufferedImage scaled = AtlasPBRLoader.scaleImage(image, 2, 2);

        assertEquals(0x80402010, scaled.getRGB(0, 0));
        assertEquals(0x80402010, scaled.getRGB(1, 0));
        assertEquals(0x80402010, scaled.getRGB(0, 1));
        assertEquals(0x80402010, scaled.getRGB(1, 1));
    }

    @Test
    public void corruptZipAtlasPbrSpritesAreTreatedAsUnloadableCompanions() throws Exception {
        String source = source();
        String body = methodBody(source,
            "PBRAtlasSprite createPBRSprite(");

        int resourceRead = body.indexOf("resourceManager.getResource(pbrImageLocation)");
        int ioCatch = body.indexOf("catch (IOException exception)", resourceRead);
        int zipCatch = body.indexOf("catch (ZipError exception)", ioCatch);
        int warn = body.indexOf("Unable to load PBR texture", zipCatch);
        int fallback = body.indexOf("return null;", warn);

        assertTrue(resourceRead >= 0);
        assertTrue(ioCatch > resourceRead);
        assertTrue(zipCatch > ioCatch);
        assertTrue(warn > zipCatch);
        assertTrue(fallback > warn);
    }

    @Test
    public void atlasSizeQueryRestoreFailureKeepsRecoverableFallback() throws Exception {
        String source = source();
        String sizeBody = methodBody(source, "private static int[] getAtlasSize(");
        String restoreBody = methodBody(source,
            "private static void restorePreviousTextureBinding(int previousTextureBinding, Throwable queryFailure,");

        int previousBinding = sizeBody.indexOf("int previousTextureBinding = GL11.glGetInteger");
        int queryFailureLocal = sizeBody.indexOf("Throwable queryFailure = null;", previousBinding);
        int recoverableLocal = sizeBody.indexOf("boolean recoverableQueryFailure = false;", queryFailureLocal);
        int bind = sizeBody.indexOf("GlStateManager.bindTexture(atlas.getGlTextureId());", recoverableLocal);
        int widthQuery = sizeBody.indexOf("GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH)", bind);
        int runtimeCatch = sizeBody.indexOf("catch (RuntimeException exception)", widthQuery);
        int runtimeCapture = sizeBody.indexOf("queryFailure = exception;", runtimeCatch);
        int runtimeRecoverable = sizeBody.indexOf("recoverableQueryFailure = true;", runtimeCapture);
        int warning = sizeBody.indexOf("falling back to sprite extents", runtimeRecoverable);
        int errorCatch = sizeBody.indexOf("catch (Error error)", warning);
        int errorCapture = sizeBody.indexOf("queryFailure = error;", errorCatch);
        int errorRethrow = sizeBody.indexOf("throw error;", errorCapture);
        int restore = sizeBody.indexOf(
            "restorePreviousTextureBinding(previousTextureBinding, queryFailure, recoverableQueryFailure);",
            errorRethrow);
        int fallbackLoop = sizeBody.indexOf("for (TextureAtlasSprite sprite : sprites)", restore);

        int restoreBind = restoreBody.indexOf("GlStateManager.bindTexture(previousTextureBinding);");
        int restoreCatch = restoreBody.indexOf("catch (RuntimeException | Error restoreFailure)", restoreBind);
        int queryFailureGuard = restoreBody.indexOf("if (queryFailure != null)", restoreCatch);
        int suppress = restoreBody.indexOf("suppressRestoreFailure(queryFailure, restoreFailure);", queryFailureGuard);
        int recoverableGuard = restoreBody.indexOf("if (recoverableQueryFailure)", suppress);
        int keepFallback = restoreBody.indexOf("return;", recoverableGuard);
        int failFast = restoreBody.indexOf("throw restoreFailure;", keepFallback);

        assertTrue(previousBinding >= 0);
        assertTrue(queryFailureLocal > previousBinding);
        assertTrue(recoverableLocal > queryFailureLocal);
        assertTrue(bind > recoverableLocal);
        assertTrue(widthQuery > bind);
        assertTrue(runtimeCatch > widthQuery);
        assertTrue(runtimeCapture > runtimeCatch);
        assertTrue(runtimeRecoverable > runtimeCapture);
        assertTrue(warning > runtimeRecoverable);
        assertTrue(errorCatch > warning);
        assertTrue(errorCapture > errorCatch);
        assertTrue(errorRethrow > errorCapture);
        assertTrue(restore > errorRethrow);
        assertTrue(fallbackLoop > restore);

        assertTrue(restoreBind >= 0);
        assertTrue(restoreCatch > restoreBind);
        assertTrue(queryFailureGuard > restoreCatch);
        assertTrue(suppress > queryFailureGuard);
        assertTrue(recoverableGuard > suppress);
        assertTrue(keepFallback > recoverableGuard);
        assertTrue(failFast > keepFallback);
    }

    @Test
    public void atlasSizeRestoreSuppressionIgnoresSameThrowable() throws Exception {
        RuntimeException failure = new RuntimeException("query");

        suppressRestoreFailure(failure, failure);

        assertEquals(0, failure.getSuppressed().length);
    }

    @Test
    public void atlasSizeRestoreSuppressionKeepsDistinctFailureContext() throws Exception {
        RuntimeException failure = new RuntimeException("query");
        RuntimeException restoreFailure = new RuntimeException("restore");

        suppressRestoreFailure(failure, restoreFailure);

        assertEquals(1, failure.getSuppressed().length);
        assertSame(restoreFailure, failure.getSuppressed()[0]);
    }

    @Test
    public void uploadedNormalAtlasIsClosedWhenConsumerRejectsOwnership() {
        AtlasPBRLoader loader = new AtlasPBRLoader();
        TrackingAtlasTexture texture = new TrackingAtlasTexture(PBRType.NORMAL);
        RuntimeException consumerFailure = new RuntimeException("normal consumer failed");
        RuntimeException deleteFailure = new RuntimeException("normal delete failed");
        texture.deleteFailure = deleteFailure;

        try {
            loader.uploadAndAcceptAtlas(texture, 16, 16, 0, PBRType.NORMAL,
                throwingConsumer(consumerFailure, null), "textures");
        } catch (RuntimeException exception) {
            assertSame(consumerFailure, exception);
            assertEquals(1, texture.uploadCalls);
            assertEquals(1, texture.deleteCalls);
            assertEquals(1, exception.getSuppressed().length);
            assertSame(deleteFailure, exception.getSuppressed()[0]);
            return;
        }

        throw new AssertionError("Expected normal atlas consumer failure to propagate");
    }

    @Test
    public void uploadedSpecularAtlasIsClosedWhenConsumerRejectsOwnership() {
        AtlasPBRLoader loader = new AtlasPBRLoader();
        TrackingAtlasTexture texture = new TrackingAtlasTexture(PBRType.SPECULAR);
        RuntimeException consumerFailure = new RuntimeException("specular consumer failed");

        try {
            loader.uploadAndAcceptAtlas(texture, 16, 16, 0, PBRType.SPECULAR,
                throwingConsumer(null, consumerFailure), "textures");
        } catch (RuntimeException exception) {
            assertSame(consumerFailure, exception);
            assertEquals(1, texture.uploadCalls);
            assertEquals(1, texture.deleteCalls);
            assertEquals(0, exception.getSuppressed().length);
            return;
        }

        throw new AssertionError("Expected specular atlas consumer failure to propagate");
    }

    @Test
    public void failedAtlasUploadIsNotOfferedToConsumerOrClosedTwice() {
        AtlasPBRLoader loader = new AtlasPBRLoader();
        TrackingAtlasTexture texture = new TrackingAtlasTexture(PBRType.NORMAL);
        texture.uploadResult = false;
        CountingConsumer consumer = new CountingConsumer();

        loader.uploadAndAcceptAtlas(texture, 16, 16, 0, PBRType.NORMAL, consumer, "textures");

        assertEquals(1, texture.uploadCalls);
        assertEquals(0, texture.deleteCalls);
        assertEquals(0, consumer.normalAccepts);
        assertEquals(0, consumer.specularAccepts);
    }

    private static void suppressRestoreFailure(Throwable failure, Throwable restoreFailure) throws Exception {
        Method method = AtlasPBRLoader.class.getDeclaredMethod(
            "suppressRestoreFailure", Throwable.class, Throwable.class);
        method.setAccessible(true);
        method.invoke(null, failure, restoreFailure);
    }

    private static String source() throws Exception {
        return new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/texture/pbr/loader/AtlasPBRLoader.java")), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signaturePrefix) {
        int start = source.indexOf(signaturePrefix);
        if (start < 0) {
            throw new AssertionError("Missing method starting with " + signaturePrefix);
        }

        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Could not parse method body for " + signaturePrefix);
    }

    private static PBRTextureLoader.PBRTextureConsumer throwingConsumer(
        RuntimeException normalFailure,
        RuntimeException specularFailure) {
        return new PBRTextureLoader.PBRTextureConsumer() {
            @Override
            public void acceptNormalTexture(AbstractTexture texture) {
                if (normalFailure != null) {
                    throw normalFailure;
                }
            }

            @Override
            public void acceptSpecularTexture(AbstractTexture texture) {
                if (specularFailure != null) {
                    throw specularFailure;
                }
            }
        };
    }

    private static final class CountingConsumer implements PBRTextureLoader.PBRTextureConsumer {
        private int normalAccepts;
        private int specularAccepts;

        @Override
        public void acceptNormalTexture(AbstractTexture texture) {
            normalAccepts++;
        }

        @Override
        public void acceptSpecularTexture(AbstractTexture texture) {
            specularAccepts++;
        }
    }

    private static final class TrackingAtlasTexture extends PBRAtlasTexture {
        private boolean uploadResult = true;
        private Throwable deleteFailure;
        private int uploadCalls;
        private int deleteCalls;

        private TrackingAtlasTexture(PBRType type) {
            super(null, type);
        }

        @Override
        public boolean hasSprites() {
            return true;
        }

        @Override
        public boolean tryUpload(int atlasWidth, int atlasHeight, int mipLevel) {
            uploadCalls++;
            return uploadResult;
        }

        @Override
        public void deleteGlTexture() {
            deleteCalls++;
            if (deleteFailure instanceof RuntimeException) {
                throw (RuntimeException) deleteFailure;
            }
            if (deleteFailure instanceof Error) {
                throw (Error) deleteFailure;
            }
        }
    }
}
