package net.oculus.texture.pbr.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import net.oculus.texture.pbr.PBRType;
import org.junit.Assume;
import org.junit.Test;
import org.lwjgl.LWJGLException;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.Pbuffer;
import org.lwjgl.opengl.PixelFormat;

public class SimplePBRLoaderGlTest {
    private static final String ENABLE_PROPERTY = "oculus.tests.pbrGl";
    private static final ResourceLocation BASE_TEXTURE =
        new ResourceLocation("minecraft", "textures/blocks/dirt.png");
    private static final ResourceLocation NORMAL_TEXTURE =
        PBRType.NORMAL.appendToFileLocation(BASE_TEXTURE);
    private static final ResourceLocation SPECULAR_TEXTURE =
        PBRType.SPECULAR.appendToFileLocation(BASE_TEXTURE);

    @Test
    public void loadsAndUploadsSimpleNormalAndSpecularCompanionsWithOpenGl() throws Exception {
        Assume.assumeTrue("Set -Doculus.tests.pbrGl=true to run the SimplePBRLoader OpenGL upload harness",
            Boolean.getBoolean(ENABLE_PROPERTY));

        Pbuffer pbuffer = createOpenGlContextOrSkip();
        CapturingConsumer consumer = new CapturingConsumer();
        try {
            new SimplePBRLoader().load(
                new SimpleTexture(BASE_TEXTURE),
                new PngResourceManager(),
                consumer);

            assertNotNull(consumer.normalTexture);
            assertNotNull(consumer.specularTexture);
            assertTrue(consumer.normalTexture.getGlTextureId() > 0);
            assertTrue(consumer.specularTexture.getGlTextureId() > 0);
            assertTextureSize(consumer.normalTexture, 1, 1);
            assertTextureSize(consumer.specularTexture, 1, 1);
        } finally {
            if (consumer.normalTexture != null) {
                consumer.normalTexture.deleteGlTexture();
            }
            if (consumer.specularTexture != null) {
                consumer.specularTexture.deleteGlTexture();
            }
            pbuffer.destroy();
        }
    }

    private static Pbuffer createOpenGlContextOrSkip() {
        try {
            Assume.assumeTrue("Pbuffers are not supported by the local LWJGL/OpenGL environment",
                (Pbuffer.getCapabilities() & Pbuffer.PBUFFER_SUPPORTED) != 0);
            Pbuffer pbuffer = new Pbuffer(16, 16, new PixelFormat(), null);
            pbuffer.makeCurrent();
            return pbuffer;
        } catch (LWJGLException | LinkageError | RuntimeException exception) {
            Assume.assumeNoException("No local OpenGL context is available for PBR texture upload", exception);
            throw new AssertionError("JUnit assumption should have skipped the test", exception);
        }
    }

    private static void assertTextureSize(AbstractTexture texture, int width, int height) {
        int previousTextureBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture.getGlTextureId());
            assertEquals(width, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH));
            assertEquals(height, GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT));
        } finally {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureBinding);
        }
    }

    private static final class CapturingConsumer implements PBRTextureLoader.PBRTextureConsumer {
        private AbstractTexture normalTexture;
        private AbstractTexture specularTexture;

        @Override
        public void acceptNormalTexture(AbstractTexture texture) {
            normalTexture = texture;
        }

        @Override
        public void acceptSpecularTexture(AbstractTexture texture) {
            specularTexture = texture;
        }
    }

    private static final class PngResourceManager implements IResourceManager {
        private final byte[] normalPng = createPng(0xFF7F7FFF);
        private final byte[] specularPng = createPng(0xFF402008);

        @Override
        public Set<String> getResourceDomains() {
            return Collections.singleton("minecraft");
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            if (NORMAL_TEXTURE.equals(location)) {
                return new PngResource(location, normalPng);
            }
            if (SPECULAR_TEXTURE.equals(location)) {
                return new PngResource(location, specularPng);
            }
            throw new FileNotFoundException(location.toString());
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            return Arrays.asList(getResource(location));
        }

        private static byte[] createPng(int argb) {
            try {
                BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, argb);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                return output.toByteArray();
            } catch (IOException exception) {
                throw new AssertionError("Failed to create in-memory PNG", exception);
            }
        }
    }

    private static final class PngResource implements IResource {
        private final ResourceLocation location;
        private final byte[] png;

        private PngResource(ResourceLocation location, byte[] png) {
            this.location = location;
            this.png = png;
        }

        @Override
        public ResourceLocation getResourceLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(png);
        }

        @Override
        public boolean hasMetadata() {
            return false;
        }

        @Override
        public <T extends IMetadataSection> T getMetadata(String sectionName) {
            return null;
        }

        @Override
        public String getResourcePackName() {
            return "Oculus Simple PBR GL Test";
        }

        @Override
        public void close() {
        }
    }
}
