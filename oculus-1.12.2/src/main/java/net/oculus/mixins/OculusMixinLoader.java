package net.oculus.mixins;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.oculus.Oculus;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.Name("Oculus")
public final class OculusMixinLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
        Oculus.LOGGER.info("MixinBooter requested Oculus early mixin configuration");
        return Collections.singletonList("oculus.mixins.json");
    }

    @Override
    public boolean shouldMixinConfigQueue(String mixinConfig) {
        final boolean shouldQueue = "oculus.mixins.json".equals(mixinConfig);
        Oculus.LOGGER.info("Evaluating whether to queue {}: {}", mixinConfig, shouldQueue);
        return shouldQueue;
    }

    @Override
    public void onMixinConfigQueued(String mixinConfig) {
        Oculus.LOGGER.info("Mixin configuration queued: {}", mixinConfig);
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
