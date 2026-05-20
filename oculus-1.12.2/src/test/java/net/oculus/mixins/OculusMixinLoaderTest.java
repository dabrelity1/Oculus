package net.oculus.mixins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Collections;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.junit.Test;
import zone.rong.mixinbooter.IEarlyMixinLoader;
import zone.rong.mixinbooter.ILateMixinLoader;

public class OculusMixinLoaderTest {
    @Test
    public void queuesOculusMixinConfigThroughEarlyCoremodPath() {
        OculusMixinLoader loader = new OculusMixinLoader();

        assertTrue(loader instanceof IFMLLoadingPlugin);
        assertTrue(loader instanceof IEarlyMixinLoader);
        assertFalse((Object) loader instanceof ILateMixinLoader);
        assertEquals(Collections.singletonList("oculus.mixins.json"), loader.getMixinConfigs());
        assertTrue(loader.shouldMixinConfigQueue("oculus.mixins.json"));
        assertFalse(loader.shouldMixinConfigQueue("other.mixins.json"));
    }

    @Test
    public void hasNoLegacyAsmTransformerPayload() {
        OculusMixinLoader loader = new OculusMixinLoader();

        assertNull(loader.getASMTransformerClass());
        assertNull(loader.getModContainerClass());
        assertNull(loader.getSetupClass());
        assertNull(loader.getAccessTransformerClass());
    }
}
