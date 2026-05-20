package net.oculus.mixins;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class AbstractTextureTextureTrackerMixinSourceTest {
    @Test
    public void abstractTextureIdsAreTrackedAtGenerationLikeReferenceTextureTracker() throws Exception {
        String mixin = read("src/main/java/net/oculus/mixin/pipeline/AbstractTextureTextureTrackerMixin.java");
        String lifecycleTracker = read("src/main/java/net/oculus/texture/TextureLifecycleTracker.java");
        String pbrManager = read("src/main/java/net/oculus/texture/pbr/PBRTextureManager.java");
        String config = read("src/main/resources/oculus.mixins.json");
        String reference = read("../Oculus-1.16.5/src/main/java/net/coderbot/iris/mixin/texture/MixinAbstractTexture.java");

        assertTrue(reference.contains("TextureTracker.INSTANCE.trackTexture(id, (AbstractTexture) (Object) this);"));
        assertTrue(mixin.contains("@Mixin(AbstractTexture.class)"));
        assertTrue(mixin.contains("method = \"getGlTextureId()I\""));
        assertTrue(mixin.contains("opcode = Opcodes.PUTFIELD"));
        assertTrue(mixin.contains("shift = At.Shift.AFTER"));
        assertTrue(mixin.contains("TextureTracker.INSTANCE.trackTexture(glTextureId, (AbstractTexture) (Object) this);"));
        assertTrue(lifecycleTracker.contains("TextureTracker.INSTANCE.onDeleteTexture(textureId);"));
        assertTrue(pbrManager.contains("TextureTracker.INSTANCE.getTexture(id);"));
        assertTrue(config.contains("\"pipeline.AbstractTextureTextureTrackerMixin\""));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
