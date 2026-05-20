package net.oculus.pipeline.shadow;

import java.util.Collections;
import java.util.Set;

/**
 * Mirrors the shadow target trigger and sampler binding rules from the 1.16.5
 * Iris/Oculus {@code IrisSamplers} and {@code IrisImages} paths without
 * depending on OpenGL.
 */
final class ShadowSamplerBindings {
    private static final int FIRST_UNSUPPORTED_SHADOW_TARGET = 2;
    private static final int MAX_UNSUPPORTED_SHADOW_TARGET_SCAN = 99;

    private ShadowSamplerBindings() {
    }

    enum Target {
        DEPTH,
        DEPTH_NO_TRANSLUCENTS,
        COLOR0,
        COLOR1,
        UNBOUND
    }

    interface Binder {
        boolean hasSampler(String name);

        void bind(String name, Target target);
    }

    interface ResourceQuery {
        boolean hasSampler(String name);

        boolean hasImage(String name);

        default Set<String> getActiveSamplerNames() {
            return Collections.emptySet();
        }

        default Set<String> getActiveImageNames() {
            return Collections.emptySet();
        }
    }

    static String findUnsupportedShadowResource(ResourceQuery query) {
        if (query == null) {
            return null;
        }

        String resource = findUnsupportedActiveShadowSampler(query);
        if (resource != null) {
            return resource;
        }

        resource = findUnsupportedActiveShadowImage(query);
        if (resource != null) {
            return resource;
        }

        resource = findUnsupportedShadowSampler(query, "shadowtex", "");
        if (resource != null) {
            return resource;
        }

        resource = findUnsupportedShadowSampler(query, "shadowtex", "HW");
        if (resource != null) {
            return resource;
        }

        resource = findUnsupportedShadowSampler(query, "shadowtex", "hw");
        if (resource != null) {
            return resource;
        }

        resource = findUnsupportedShadowSampler(query, "shadowcolor", "");
        if (resource != null) {
            return resource;
        }

        resource = findUnsupportedShadowSampler(query, "oculus_shadow_color", "");
        if (resource != null) {
            return resource;
        }

        return findUnsupportedShadowImage(query, "shadowcolorimg");
    }

    private static String findUnsupportedActiveShadowSampler(ResourceQuery query) {
        for (String name : query.getActiveSamplerNames()) {
            if (isUnsupportedIndexedShadowSampler(name)) {
                return name;
            }
        }
        return null;
    }

    private static String findUnsupportedActiveShadowImage(ResourceQuery query) {
        for (String name : query.getActiveImageNames()) {
            if (shadowIndexAfterPrefix(name, "shadowcolorimg", "") >= FIRST_UNSUPPORTED_SHADOW_TARGET) {
                return name;
            }
        }
        return null;
    }

    private static boolean isUnsupportedIndexedShadowSampler(String name) {
        return shadowIndexAfterPrefix(name, "shadowtex", "") >= FIRST_UNSUPPORTED_SHADOW_TARGET
            || shadowIndexAfterPrefix(name, "shadowtex", "HW") >= FIRST_UNSUPPORTED_SHADOW_TARGET
            || shadowIndexAfterPrefix(name, "shadowtex", "hw") >= FIRST_UNSUPPORTED_SHADOW_TARGET
            || shadowIndexAfterPrefix(name, "shadowcolor", "") >= FIRST_UNSUPPORTED_SHADOW_TARGET
            || shadowIndexAfterPrefix(name, "oculus_shadow_color", "") >= FIRST_UNSUPPORTED_SHADOW_TARGET
            || shadowIndexAfterPrefix(name, "shadowcolorimg", "") >= FIRST_UNSUPPORTED_SHADOW_TARGET;
    }

    private static int shadowIndexAfterPrefix(String name, String prefix, String suffix) {
        if (name == null || !name.startsWith(prefix) || !name.endsWith(suffix)) {
            return -1;
        }
        int start = prefix.length();
        int end = name.length() - suffix.length();
        if (start >= end) {
            return -1;
        }
        long value = 0L;
        for (int i = start; i < end; i++) {
            char character = name.charAt(i);
            if (character < '0' || character > '9') {
                return -1;
            }
            value = value * 10 + character - '0';
            if (value > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) value;
    }

    private static String findUnsupportedShadowSampler(ResourceQuery query, String prefix, String suffix) {
        for (int index = FIRST_UNSUPPORTED_SHADOW_TARGET; index <= MAX_UNSUPPORTED_SHADOW_TARGET_SCAN; index++) {
            String name = prefix + index + suffix;
            if (query.hasSampler(name)) {
                return name;
            }
        }
        return null;
    }

    private static String findUnsupportedShadowImage(ResourceQuery query, String prefix) {
        for (int index = FIRST_UNSUPPORTED_SHADOW_TARGET; index <= MAX_UNSUPPORTED_SHADOW_TARGET_SCAN; index++) {
            String name = prefix + index;
            if (query.hasSampler(name) || query.hasImage(name)) {
                return name;
            }
        }
        return null;
    }

    static boolean usesShadowTargets(ResourceQuery query) {
        if (query == null) {
            return false;
        }

        return findUnsupportedShadowResource(query) != null
            || query.hasSampler("shadowtex0")
            || query.hasSampler("shadowtex0HW")
            || query.hasSampler("shadowtex0hw")
            || query.hasSampler("shadowtex1")
            || query.hasSampler("shadowtex1HW")
            || query.hasSampler("shadowtex1hw")
            || query.hasSampler("shadow")
            || query.hasSampler("s_shadow")
            || query.hasSampler("watershadow")
            || query.hasSampler("shadowcolor")
            || query.hasSampler("s_shadowcolor")
            || query.hasSampler("shadowcolor0")
            || query.hasSampler("shadowcolor1")
            || query.hasSampler("oculus_shadow_depth")
            || query.hasSampler("oculus_shadow_depth_notrans")
            || query.hasSampler("oculus_shadow_color")
            || query.hasSampler("oculus_shadow_color1")
            || query.hasSampler("shadowcolorimg0")
            || query.hasSampler("shadowcolorimg1")
            || query.hasImage("shadowcolorimg0")
            || query.hasImage("shadowcolorimg1");
    }

    static boolean apply(Binder binder, boolean hardwareFiltered0, boolean hardwareFiltered1) {
        boolean waterShadowEnabled = binder.hasSampler("watershadow");
        boolean usesShadows;

        if (waterShadowEnabled) {
            usesShadows = true;
            bindIfPresent(binder, "shadowtex0", Target.DEPTH);
            bindIfPresent(binder, "oculus_shadow_depth", Target.DEPTH);
            bindIfPresent(binder, "watershadow", Target.DEPTH);
            bindIfPresent(binder, "shadowtex1", Target.DEPTH_NO_TRANSLUCENTS);
            bindIfPresent(binder, "oculus_shadow_depth_notrans", Target.DEPTH_NO_TRANSLUCENTS);
            bindIfPresent(binder, "shadow", Target.DEPTH_NO_TRANSLUCENTS);
            bindIfPresent(binder, "s_shadow", Target.DEPTH_NO_TRANSLUCENTS);
        } else {
            usesShadows = bindIfPresent(binder, "shadowtex0", Target.DEPTH);
            usesShadows |= bindIfPresent(binder, "oculus_shadow_depth", Target.DEPTH);
            usesShadows |= bindIfPresent(binder, "shadow", Target.DEPTH);
            usesShadows |= bindIfPresent(binder, "s_shadow", Target.DEPTH);
            usesShadows |= bindIfPresent(binder, "shadowtex1", Target.DEPTH_NO_TRANSLUCENTS);
            usesShadows |= bindIfPresent(binder, "oculus_shadow_depth_notrans", Target.DEPTH_NO_TRANSLUCENTS);
        }

        bindIfPresent(binder, "shadowcolor", Target.COLOR0);
        bindIfPresent(binder, "s_shadowcolor", Target.COLOR0);
        bindIfPresent(binder, "shadowcolor0", Target.COLOR0);
        bindIfPresent(binder, "shadowcolor1", Target.COLOR1);
        bindIfPresent(binder, "oculus_shadow_color", Target.COLOR0);
        bindIfPresent(binder, "oculus_shadow_color1", Target.COLOR1);

        bindHardwareSampler(binder, "shadowtex0HW", "shadowtex0hw", hardwareFiltered0, Target.DEPTH);
        bindHardwareSampler(
            binder,
            "shadowtex1HW",
            "shadowtex1hw",
            hardwareFiltered1,
            Target.DEPTH_NO_TRANSLUCENTS);

        return usesShadows;
    }

    private static boolean bindIfPresent(Binder binder, String name, Target target) {
        if (!binder.hasSampler(name)) {
            return false;
        }
        binder.bind(name, target);
        return true;
    }

    private static void bindHardwareSampler(
            Binder binder,
            String canonicalName,
            String lowercaseName,
            boolean hardwareFiltered,
            Target filteredTarget) {
        Target target = hardwareFiltered ? filteredTarget : Target.UNBOUND;
        bindIfPresent(binder, canonicalName, target);
        bindIfPresent(binder, lowercaseName, target);
    }
}
