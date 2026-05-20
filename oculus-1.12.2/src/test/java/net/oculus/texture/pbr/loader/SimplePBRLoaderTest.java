package net.oculus.texture.pbr.loader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipError;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import net.oculus.texture.pbr.PBRType;
import org.junit.Assume;
import org.junit.Test;

import javax.imageio.ImageIO;

public class SimplePBRLoaderTest {
    private static final ResourceLocation BASE_TEXTURE =
        new ResourceLocation("minecraft", "textures/blocks/dirt.png");
    private static final ResourceLocation NORMAL_TEXTURE =
        PBRType.NORMAL.appendToFileLocation(BASE_TEXTURE);
    private static final ResourceLocation SPECULAR_TEXTURE =
        PBRType.SPECULAR.appendToFileLocation(BASE_TEXTURE);

    @Test
    public void ioLoadFailureDeletesTextureAndReturnsNull() {
        TestSimpleTexture texture = new TestSimpleTexture(NORMAL_TEXTURE);
        texture.ioFailure = new IOException("missing companion");
        TestLoader loader = new TestLoader(texture);

        assertNull(loader.createPBRTexture(BASE_TEXTURE, null, PBRType.NORMAL));

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void ioLoadFailureSwallowsCleanupRuntimeFailureAndReturnsNull() {
        TestSimpleTexture texture = new TestSimpleTexture(NORMAL_TEXTURE);
        texture.ioFailure = new IOException("missing companion");
        texture.deleteFailure = new IllegalStateException("delete failed");
        TestLoader loader = new TestLoader(texture);

        assertNull(loader.createPBRTexture(BASE_TEXTURE, null, PBRType.NORMAL));

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void ioLoadFailureSwallowsCleanupErrorAndReturnsNull() {
        TestSimpleTexture texture = new TestSimpleTexture(NORMAL_TEXTURE);
        texture.ioFailure = new IOException("missing companion");
        texture.deleteFailure = new AssertionError("delete failed");
        TestLoader loader = new TestLoader(texture);

        assertNull(loader.createPBRTexture(BASE_TEXTURE, null, PBRType.NORMAL));

        assertEquals(1, texture.deleteCalls);
        assertEquals(1, texture.ioFailure.getSuppressed().length);
        assertSame(texture.deleteFailure, texture.ioFailure.getSuppressed()[0]);
    }

    @Test
    public void corruptZipLoadFailureDeletesTextureAndReturnsNull() {
        TestSimpleTexture texture = new TestSimpleTexture(NORMAL_TEXTURE);
        ZipError loadFailure = new ZipError("corrupt companion zip");
        texture.errorFailure = loadFailure;
        TestLoader loader = new TestLoader(texture);

        assertNull(loader.createPBRTexture(BASE_TEXTURE, null, PBRType.NORMAL));

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void runtimeLoadFailureDeletesTextureAndRethrowsOriginalFailure() {
        TestSimpleTexture texture = new TestSimpleTexture(NORMAL_TEXTURE);
        IllegalStateException loadFailure = new IllegalStateException("upload failed");
        texture.runtimeFailure = loadFailure;
        TestLoader loader = new TestLoader(texture);

        try {
            loader.createPBRTexture(BASE_TEXTURE, null, PBRType.NORMAL);
            fail("Expected runtime PBR load failure");
        } catch (IllegalStateException exception) {
            assertSame(loadFailure, exception);
        }

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void runtimeLoadFailureKeepsCleanupFailureSuppressedOnOriginalFailure() {
        TestSimpleTexture texture = new TestSimpleTexture(NORMAL_TEXTURE);
        IllegalStateException loadFailure = new IllegalStateException("upload failed");
        IllegalStateException deleteFailure = new IllegalStateException("delete failed");
        texture.runtimeFailure = loadFailure;
        texture.deleteFailure = deleteFailure;
        TestLoader loader = new TestLoader(texture);

        try {
            loader.createPBRTexture(BASE_TEXTURE, null, PBRType.NORMAL);
            fail("Expected runtime PBR load failure");
        } catch (IllegalStateException exception) {
            assertSame(loadFailure, exception);
            assertEquals(1, exception.getSuppressed().length);
            assertSame(deleteFailure, exception.getSuppressed()[0]);
        }

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void runtimeLoadFailureIgnoresSameCleanupThrowable() {
        TestSimpleTexture texture = new TestSimpleTexture(NORMAL_TEXTURE);
        IllegalStateException sharedFailure = new IllegalStateException("shared failure");
        texture.runtimeFailure = sharedFailure;
        texture.deleteFailure = sharedFailure;
        TestLoader loader = new TestLoader(texture);

        try {
            loader.createPBRTexture(BASE_TEXTURE, null, PBRType.NORMAL);
            fail("Expected runtime PBR load failure");
        } catch (IllegalStateException exception) {
            assertSame(sharedFailure, exception);
            assertEquals(0, exception.getSuppressed().length);
        }

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void nonZipErrorLoadFailureKeepsCleanupErrorSuppressedOnOriginalFailure() {
        TestSimpleTexture texture = new TestSimpleTexture(NORMAL_TEXTURE);
        AssertionError loadFailure = new AssertionError("upload failed");
        AssertionError deleteFailure = new AssertionError("delete failed");
        texture.errorFailure = loadFailure;
        texture.deleteFailure = deleteFailure;
        TestLoader loader = new TestLoader(texture);

        try {
            loader.createPBRTexture(BASE_TEXTURE, null, PBRType.NORMAL);
            fail("Expected error PBR load failure");
        } catch (AssertionError error) {
            assertSame(loadFailure, error);
            assertEquals(1, error.getSuppressed().length);
            assertSame(deleteFailure, error.getSuppressed()[0]);
        }

        assertEquals(1, texture.deleteCalls);
    }

    @Test
    public void loadCreatesBothCompanionsBeforeHandingOffLikeReference() {
        TestSimpleTexture baseTexture = new TestSimpleTexture(BASE_TEXTURE);
        TestSimpleTexture normalTexture = new TestSimpleTexture(NORMAL_TEXTURE);
        TestSimpleTexture specularTexture = new TestSimpleTexture(SPECULAR_TEXTURE);
        List<String> events = new ArrayList<>();
        CapturingConsumer consumer = new CapturingConsumer(events);
        SimplePBRLoader loader = new SimplePBRLoader() {
            @Override
            protected AbstractTexture createPBRTexture(ResourceLocation imageLocation,
                                                       IResourceManager resourceManager,
                                                       PBRType pbrType) {
                assertEquals(BASE_TEXTURE, imageLocation);
                if (pbrType == PBRType.NORMAL) {
                    events.add("createNormal");
                    return normalTexture;
                }
                if (pbrType == PBRType.SPECULAR) {
                    events.add("createSpecular");
                    return specularTexture;
                }
                return null;
            }
        };

        loader.load(baseTexture, null, consumer);

        assertSame(normalTexture, consumer.normalTexture);
        assertSame(specularTexture, consumer.specularTexture);
        assertEquals(Arrays.asList("createNormal", "createSpecular", "acceptNormal", "acceptSpecular"), events);
    }

    @Test
    public void localPbrValidationResourcePackLoadsSimpleCompanionsThroughLoaderPath() throws Exception {
        Path packRoot = Paths.get("run/resourcepacks/Oculus-PBR-Validation");
        Assume.assumeTrue("Oculus-PBR-Validation resource pack is not present", Files.isDirectory(packRoot));

        FolderResourceManager resourceManager = new FolderResourceManager(packRoot);
        List<ResourceLocation> loadedTextures = new ArrayList<>();
        CapturingConsumer consumer = new CapturingConsumer();
        SimplePBRLoader loader = new SimplePBRLoader() {
            @Override
            protected SimpleTexture createSimpleTexture(ResourceLocation pbrImageLocation) {
                return new ResourceReadingSimpleTexture(pbrImageLocation, loadedTextures);
            }
        };

        loader.load(new TestSimpleTexture(BASE_TEXTURE), resourceManager, consumer);

        assertNotNull(consumer.normalTexture);
        assertNotNull(consumer.specularTexture);
        assertEquals(Arrays.asList(NORMAL_TEXTURE, SPECULAR_TEXTURE), loadedTextures);
    }

    @Test
    public void specularRuntimeFailureClosesUnacceptedNormalTextureBeforeRethrowing() {
        TestSimpleTexture baseTexture = new TestSimpleTexture(BASE_TEXTURE);
        TestSimpleTexture normalTexture = new TestSimpleTexture(NORMAL_TEXTURE);
        IllegalStateException specularFailure = new IllegalStateException("specular upload failed");
        CapturingConsumer consumer = new CapturingConsumer();
        SimplePBRLoader loader = new SimplePBRLoader() {
            @Override
            protected AbstractTexture createPBRTexture(ResourceLocation imageLocation,
                                                       IResourceManager resourceManager,
                                                       PBRType pbrType) {
                assertEquals(BASE_TEXTURE, imageLocation);
                if (pbrType == PBRType.NORMAL) {
                    return normalTexture;
                }
                if (pbrType == PBRType.SPECULAR) {
                    throw specularFailure;
                }
                return null;
            }
        };

        try {
            loader.load(baseTexture, null, consumer);
            fail("Expected specular PBR load failure");
        } catch (IllegalStateException exception) {
            assertSame(specularFailure, exception);
        }

        assertNull(consumer.normalTexture);
        assertNull(consumer.specularTexture);
        assertEquals(1, normalTexture.deleteCalls);
    }

    @Test
    public void specularRuntimeFailureIgnoresSameUnacceptedNormalCleanupThrowable() {
        TestSimpleTexture baseTexture = new TestSimpleTexture(BASE_TEXTURE);
        TestSimpleTexture normalTexture = new TestSimpleTexture(NORMAL_TEXTURE);
        IllegalStateException sharedFailure = new IllegalStateException("shared failure");
        normalTexture.deleteFailure = sharedFailure;
        CapturingConsumer consumer = new CapturingConsumer();
        SimplePBRLoader loader = new SimplePBRLoader() {
            @Override
            protected AbstractTexture createPBRTexture(ResourceLocation imageLocation,
                                                       IResourceManager resourceManager,
                                                       PBRType pbrType) {
                assertEquals(BASE_TEXTURE, imageLocation);
                if (pbrType == PBRType.NORMAL) {
                    return normalTexture;
                }
                if (pbrType == PBRType.SPECULAR) {
                    throw sharedFailure;
                }
                return null;
            }
        };

        try {
            loader.load(baseTexture, null, consumer);
            fail("Expected specular PBR load failure");
        } catch (IllegalStateException exception) {
            assertSame(sharedFailure, exception);
            assertEquals(0, exception.getSuppressed().length);
        }

        assertNull(consumer.normalTexture);
        assertNull(consumer.specularTexture);
        assertEquals(1, normalTexture.deleteCalls);
    }

    private static final class TestLoader extends SimplePBRLoader {
        private final TestSimpleTexture texture;

        private TestLoader(TestSimpleTexture texture) {
            this.texture = texture;
        }

        @Override
        protected SimpleTexture createSimpleTexture(ResourceLocation pbrImageLocation) {
            assertEquals(NORMAL_TEXTURE, pbrImageLocation);
            return texture;
        }
    }

    private static final class TestSimpleTexture extends SimpleTexture {
        private IOException ioFailure;
        private RuntimeException runtimeFailure;
        private Error errorFailure;
        private Throwable deleteFailure;
        private int deleteCalls;

        private TestSimpleTexture(ResourceLocation textureLocation) {
            super(textureLocation);
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) throws IOException {
            if (ioFailure != null) {
                throw ioFailure;
            }
            if (runtimeFailure != null) {
                throw runtimeFailure;
            }
            if (errorFailure != null) {
                throw errorFailure;
            }
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

    private static final class ResourceReadingSimpleTexture extends SimpleTexture {
        private final ResourceLocation location;
        private final List<ResourceLocation> loadedTextures;

        private ResourceReadingSimpleTexture(ResourceLocation location, List<ResourceLocation> loadedTextures) {
            super(location);
            this.location = location;
            this.loadedTextures = loadedTextures;
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) throws IOException {
            IResource resource = resourceManager.getResource(location);
            BufferedImage image;
            try (InputStream input = resource.getInputStream()) {
                image = ImageIO.read(input);
            }

            assertNotNull("Expected PBR image " + location, image);
            assertEquals(16, image.getWidth());
            assertEquals(16, image.getHeight());
            loadedTextures.add(location);
        }

        @Override
        public int getGlTextureId() {
            return 42 + loadedTextures.size();
        }
    }

    private static final class CapturingConsumer implements PBRTextureLoader.PBRTextureConsumer {
        private final List<String> events;
        private AbstractTexture normalTexture;
        private AbstractTexture specularTexture;

        private CapturingConsumer() {
            this(null);
        }

        private CapturingConsumer(List<String> events) {
            this.events = events;
        }

        @Override
        public void acceptNormalTexture(AbstractTexture texture) {
            if (events != null) {
                events.add("acceptNormal");
            }
            normalTexture = texture;
        }

        @Override
        public void acceptSpecularTexture(AbstractTexture texture) {
            if (events != null) {
                events.add("acceptSpecular");
            }
            specularTexture = texture;
        }
    }

    private static final class FolderResourceManager implements IResourceManager {
        private final Path packRoot;

        private FolderResourceManager(Path packRoot) {
            this.packRoot = packRoot;
        }

        @Override
        public Set<String> getResourceDomains() {
            return Collections.singleton("minecraft");
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            Path path = packRoot.resolve("assets")
                .resolve(location.getNamespace())
                .resolve(location.getPath());
            if (!Files.isRegularFile(path)) {
                throw new FileNotFoundException(location.toString());
            }
            return new FileResource(location, Files.readAllBytes(path));
        }

        @Override
        public List<IResource> getAllResources(ResourceLocation location) throws IOException {
            return Collections.singletonList(getResource(location));
        }
    }

    private static final class FileResource implements IResource {
        private final ResourceLocation location;
        private final byte[] content;

        private FileResource(ResourceLocation location, byte[] content) {
            this.location = location;
            this.content = content;
        }

        @Override
        public ResourceLocation getResourceLocation() {
            return location;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
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
            return "Oculus-PBR-Validation";
        }

        @Override
        public void close() {
        }
    }
}
