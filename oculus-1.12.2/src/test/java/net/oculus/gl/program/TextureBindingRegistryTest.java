package net.oculus.gl.program;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

public class TextureBindingRegistryTest {
    @After
    public void tearDown() {
        TextureBindingRegistry.clear();
    }

    @Test
    public void clearRemovesGlobalSamplerBindingsForReloads() {
        TextureBindingRegistry.register("CustomImageSampler", TextureBinding.texture2D(() -> 77));

        assertEquals(77, TextureBindingRegistry.resolve("CustomImageSampler").getTextureId());

        TextureBindingRegistry.clear();

        assertEquals(0, TextureBindingRegistry.resolve("CustomImageSampler").getTextureId());
    }

    @Test
    public void samplerNamesAreCaseSensitiveLikeGlUniformLookup() {
        TextureBindingRegistry.register("CustomImageSampler", TextureBinding.texture2D(() -> 77));

        assertEquals(77, TextureBindingRegistry.resolve("CustomImageSampler").getTextureId());
        assertEquals(0, TextureBindingRegistry.resolve("customimagesampler").getTextureId());
        assertEquals(0, TextureBindingRegistry.resolve("CUSTOMIMAGESAMPLER").getTextureId());
    }

    @Test
    public void unregisterRemovesOnlyNamedSamplerBinding() {
        TextureBindingRegistry.register("CustomImageSampler", TextureBinding.texture2D(() -> 77));
        TextureBindingRegistry.register("OtherSampler", TextureBinding.texture2D(() -> 88));

        TextureBindingRegistry.unregister("CustomImageSampler");

        assertEquals(0, TextureBindingRegistry.resolve("CustomImageSampler").getTextureId());
        assertEquals(88, TextureBindingRegistry.resolve("OtherSampler").getTextureId());
    }

    @Test
    public void unregisterWithBindingLeavesNewerBindingForSameSampler() {
        TextureBinding oldBinding = TextureBinding.texture2D(() -> 77);
        TextureBinding newBinding = TextureBinding.texture2D(() -> 88);

        TextureBindingRegistry.register("noisetex", oldBinding);
        TextureBindingRegistry.register("noisetex", newBinding);

        TextureBindingRegistry.unregister("noisetex", oldBinding);

        assertEquals(88, TextureBindingRegistry.resolve("noisetex").getTextureId());

        TextureBindingRegistry.unregister("noisetex", newBinding);

        assertEquals(0, TextureBindingRegistry.resolve("noisetex").getTextureId());
    }
}
