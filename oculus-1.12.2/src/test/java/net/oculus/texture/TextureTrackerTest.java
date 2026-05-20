package net.oculus.texture;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.io.IOException;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;
import org.junit.After;
import org.junit.Test;

public class TextureTrackerTest {
    @After
    public void tearDown() {
        TextureTracker.INSTANCE.clearForTesting();
    }

    @Test
    public void tracksPositiveAbstractTextureIds() {
        TestTexture texture = new TestTexture();

        TextureTracker.INSTANCE.trackTexture(91, texture);

        assertSame(texture, TextureTracker.INSTANCE.getTexture(91));
    }

    @Test
    public void ignoresInvalidTextureIds() {
        TestTexture texture = new TestTexture();

        TextureTracker.INSTANCE.trackTexture(0, texture);
        TextureTracker.INSTANCE.trackTexture(-1, texture);

        assertNull(TextureTracker.INSTANCE.getTexture(0));
        assertNull(TextureTracker.INSTANCE.getTexture(-1));
    }

    @Test
    public void lifecycleDeleteRemovesTrackedTexture() {
        TestTexture texture = new TestTexture();
        TextureTracker.INSTANCE.trackTexture(92, texture);

        TextureLifecycleTracker.onDeleteTexture(92);

        assertNull(TextureTracker.INSTANCE.getTexture(92));
    }

    private static final class TestTexture extends AbstractTexture {
        @Override
        public void loadTexture(IResourceManager resourceManager) throws IOException {
        }
    }
}
