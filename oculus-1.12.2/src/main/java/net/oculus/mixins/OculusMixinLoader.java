package net.oculus.mixins;

import java.util.Collections;
import java.util.List;

import net.oculus.Oculus;
import zone.rong.mixinbooter.ILateMixinLoader;
import zone.rong.mixinbooter.MixinLoader;

@MixinLoader
public final class OculusMixinLoader implements ILateMixinLoader {
    @Override
    public List<String> getMixinConfigs() {
    Oculus.LOGGER.info("MixinBooter requested Oculus mixin configuration");
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
}
