package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class BlockRenderLayerOverrideMixinSourceTest {
    @Test
    public void interceptsForge112BlockRenderLayerHooks() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/java/net/oculus/mixin/pipeline/BlockRenderLayerOverrideMixin.java")),
            StandardCharsets.UTF_8);

        assertTrue(source.contains("@Mixin(Block.class)"));
        assertTrue(source.contains("method = \"canRenderInLayer\""));
        assertTrue(source.contains("remap = false"));
        assertTrue(source.contains("method = \"getRenderLayer\""));
        assertTrue(source.contains("BlockRenderingSettings.INSTANCE.getRenderLayerOverride"));
        assertTrue(source.contains("cir.setReturnValue(override == layer);"));
        assertTrue(source.contains("cir.setReturnValue(override);"));
    }

    @Test
    public void mixinConfigQueuesRenderLayerOverrideMixin() throws Exception {
        String source = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/oculus.mixins.json")), StandardCharsets.UTF_8);

        assertTrue(source.contains("\"pipeline.BlockRenderLayerOverrideMixin\""));
    }
}
