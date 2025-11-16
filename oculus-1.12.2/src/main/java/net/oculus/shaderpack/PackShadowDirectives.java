package net.oculus.shaderpack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.shaderpack.directives.DirectiveHolder;
import net.oculus.vendored.joml.Vector4f;

/**
 * Shadow-specific directives used to configure map resolution, filtering, and
 * color buffers.
 */
public final class PackShadowDirectives {
    public static final int MAX_SHADOW_COLOR_BUFFERS = 2;

    private final OptionalBoolean shadowEnabled;
    private int resolution = 1024;
    private Float fov = null;
    private float distance = 160.0f;
    private float distanceRenderMul = -1.0f;
    private float entityShadowDistanceMul = 1.0f;
    private boolean explicitRenderDistance = false;
    private float intervalSize = 2.0f;

    private final boolean shouldRenderTerrain;
    private final boolean shouldRenderTranslucent;
    private final boolean shouldRenderEntities;
    private final boolean shouldRenderPlayer;
    private final boolean shouldRenderBlockEntities;
    private final OptionalBoolean cullingState;

    private final List<DepthSamplingSettings> depthSamplingSettings = new ArrayList<>();
    private final List<SamplingSettings> colorSamplingSettings = new ArrayList<>();

    public PackShadowDirectives(ShaderProperties properties) {
        this.shouldRenderTerrain = properties.getShadowTerrain().orElse(true);
        this.shouldRenderTranslucent = properties.getShadowTranslucent().orElse(true);
        this.shouldRenderEntities = properties.getShadowEntities().orElse(true);
        this.shouldRenderPlayer = properties.getShadowPlayer().orElse(false);
        this.shouldRenderBlockEntities = properties.getShadowBlockEntities().orElse(true);
        this.cullingState = properties.getShadowCulling();
        this.shadowEnabled = properties.getShadowEnabled();

        depthSamplingSettings.add(new DepthSamplingSettings());
        depthSamplingSettings.add(new DepthSamplingSettings());

        for (int i = 0; i < MAX_SHADOW_COLOR_BUFFERS; i++) {
            colorSamplingSettings.add(new SamplingSettings());
        }
    }

    public PackShadowDirectives(PackShadowDirectives other) {
        this.shadowEnabled = other.shadowEnabled;
        this.resolution = other.resolution;
        this.fov = other.fov;
        this.distance = other.distance;
        this.distanceRenderMul = other.distanceRenderMul;
        this.entityShadowDistanceMul = other.entityShadowDistanceMul;
        this.explicitRenderDistance = other.explicitRenderDistance;
        this.intervalSize = other.intervalSize;
        this.shouldRenderTerrain = other.shouldRenderTerrain;
        this.shouldRenderTranslucent = other.shouldRenderTranslucent;
        this.shouldRenderEntities = other.shouldRenderEntities;
        this.shouldRenderPlayer = other.shouldRenderPlayer;
        this.shouldRenderBlockEntities = other.shouldRenderBlockEntities;
        this.cullingState = other.cullingState;
        this.depthSamplingSettings.addAll(other.depthSamplingSettings);
        this.colorSamplingSettings.addAll(other.colorSamplingSettings);
    }

    public OptionalBoolean isShadowEnabled() {
        return shadowEnabled;
    }

    public int getResolution() {
        return resolution;
    }

    public Float getFov() {
        return fov;
    }

    public float getDistance() {
        return distance;
    }

    public float getDistanceRenderMul() {
        return distanceRenderMul;
    }

    public float getEntityShadowDistanceMul() {
        return entityShadowDistanceMul;
    }

    public boolean isDistanceRenderMulExplicit() {
        return explicitRenderDistance;
    }

    public float getIntervalSize() {
        return intervalSize;
    }

    public boolean shouldRenderTerrain() {
        return shouldRenderTerrain;
    }

    public boolean shouldRenderTranslucent() {
        return shouldRenderTranslucent;
    }

    public boolean shouldRenderEntities() {
        return shouldRenderEntities;
    }

    public boolean shouldRenderPlayer() {
        return shouldRenderPlayer;
    }

    public boolean shouldRenderBlockEntities() {
        return shouldRenderBlockEntities;
    }

    public OptionalBoolean getCullingState() {
        return cullingState;
    }

    public List<DepthSamplingSettings> getDepthSamplingSettings() {
        return Collections.unmodifiableList(depthSamplingSettings);
    }

    public List<SamplingSettings> getColorSamplingSettings() {
        return Collections.unmodifiableList(colorSamplingSettings);
    }

    public void acceptDirectives(DirectiveHolder directives) {
        directives.acceptCommentIntDirective("SHADOWRES", value -> this.resolution = value);
        directives.acceptConstIntDirective("shadowMapResolution", value -> this.resolution = value);

        directives.acceptCommentFloatDirective("SHADOWFOV", value -> this.fov = value);
        directives.acceptConstFloatDirective("shadowMapFov", value -> this.fov = value);

        directives.acceptCommentFloatDirective("SHADOWHPL", value -> this.distance = value);
        directives.acceptConstFloatDirective("shadowDistance", value -> this.distance = value);

        directives.acceptConstFloatDirective("entityShadowDistanceMul", value -> this.entityShadowDistanceMul = value);

        directives.acceptConstFloatDirective("shadowDistanceRenderMul", value -> {
            this.distanceRenderMul = value;
            this.explicitRenderDistance = true;
        });

        directives.acceptConstFloatDirective("shadowIntervalSize", value -> this.intervalSize = value);

        acceptHardwareFilteringSettings(directives, depthSamplingSettings);
        acceptDepthMipmapSettings(directives, depthSamplingSettings);
        acceptColorMipmapSettings(directives, colorSamplingSettings);
        acceptDepthFilteringSettings(directives, depthSamplingSettings);
        acceptColorFilteringSettings(directives, colorSamplingSettings);
        acceptBufferDirectives(directives, colorSamplingSettings);
    }

    private static void acceptHardwareFilteringSettings(DirectiveHolder directives, List<DepthSamplingSettings> samplers) {
        directives.acceptConstBooleanDirective("shadowHardwareFiltering", value -> samplers.forEach(s -> s.setHardwareFiltering(value)));
        for (int i = 0; i < samplers.size(); i++) {
            int index = i;
            directives.acceptConstBooleanDirective("shadowHardwareFiltering" + i, value -> samplers.get(index).setHardwareFiltering(value));
        }
    }

    private static void acceptDepthMipmapSettings(DirectiveHolder directives, List<DepthSamplingSettings> samplers) {
        directives.acceptConstBooleanDirective("generateShadowMipmap", value -> samplers.forEach(s -> s.setMipmap(value)));
        if (!samplers.isEmpty()) {
            directives.acceptConstBooleanDirective("shadowtexMipmap", value -> samplers.get(0).setMipmap(value));
        }
        for (int i = 0; i < samplers.size(); i++) {
            int index = i;
            directives.acceptConstBooleanDirective("shadowtex" + i + "Mipmap", value -> samplers.get(index).setMipmap(value));
        }
    }

    private static void acceptColorMipmapSettings(DirectiveHolder directives, List<SamplingSettings> samplers) {
        directives.acceptConstBooleanDirective("generateShadowColorMipmap", value -> samplers.forEach(s -> s.setMipmap(value)));
        for (int i = 0; i < samplers.size(); i++) {
            int index = i;
            Consumer<Boolean> consumer = value -> samplers.get(index).setMipmap(value);
            directives.acceptConstBooleanDirective("shadowcolor" + i + "Mipmap", consumer);
            directives.acceptConstBooleanDirective("shadowColor" + i + "Mipmap", consumer);
        }
    }

    private static void acceptDepthFilteringSettings(DirectiveHolder directives, List<DepthSamplingSettings> samplers) {
        if (!samplers.isEmpty()) {
            directives.acceptConstBooleanDirective("shadowtexNearest", value -> samplers.get(0).setNearest(value));
        }
        for (int i = 0; i < samplers.size(); i++) {
            int index = i;
            directives.acceptConstBooleanDirective("shadowtex" + i + "Nearest", value -> samplers.get(index).setNearest(value));
            directives.acceptConstBooleanDirective("shadow" + i + "MinMagNearest", value -> samplers.get(index).setNearest(value));
        }
    }

    private static void acceptColorFilteringSettings(DirectiveHolder directives, List<SamplingSettings> samplers) {
        for (int i = 0; i < samplers.size(); i++) {
            int index = i;
            Consumer<Boolean> consumer = value -> samplers.get(index).setNearest(value);
            directives.acceptConstBooleanDirective("shadowcolor" + i + "Nearest", consumer);
            directives.acceptConstBooleanDirective("shadowColor" + i + "Nearest", consumer);
            directives.acceptConstBooleanDirective("shadowColor" + i + "MinMagNearest", consumer);
        }
    }

    private static void acceptBufferDirectives(DirectiveHolder directives, List<SamplingSettings> samplers) {
        for (int i = 0; i < samplers.size(); i++) {
            int index = i;
            String buffer = "shadowcolor" + i;
            directives.acceptConstStringDirective(buffer + "Format", value -> {
                Optional<InternalTextureFormat> format = InternalTextureFormat.fromString(value);
                format.ifPresent(fmt -> samplers.get(index).setFormat(fmt));
            });
            directives.acceptConstBooleanDirective(buffer + "Clear", value -> samplers.get(index).setClear(value));
            directives.acceptConstVec4Directive(buffer + "ClearColor", value -> samplers.get(index).setClearColor(value));
        }
    }

    public static class SamplingSettings {
        private boolean mipmap = false;
        private boolean nearest = false;
        private boolean clear = true;
        private Vector4f clearColor = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);
        private InternalTextureFormat format = InternalTextureFormat.RGBA;

        public boolean getMipmap() {
            return mipmap;
        }

        public boolean getNearest() {
            return nearest;
        }

        public boolean shouldClear() {
            return clear;
        }

        public Vector4f getClearColor() {
            return clearColor;
        }

        public InternalTextureFormat getFormat() {
            return format;
        }

        protected void setMipmap(boolean mipmap) {
            this.mipmap = mipmap;
        }

        protected void setNearest(boolean nearest) {
            this.nearest = nearest;
        }

        protected void setClear(boolean clear) {
            this.clear = clear;
        }

        protected void setClearColor(Vector4f clearColor) {
            this.clearColor = clearColor;
        }

        protected void setFormat(InternalTextureFormat format) {
            this.format = format;
        }
    }

    public static final class DepthSamplingSettings extends SamplingSettings {
        private boolean hardwareFiltering = false;

        public boolean hasHardwareFiltering() {
            return hardwareFiltering;
        }

        private void setHardwareFiltering(boolean hardwareFiltering) {
            this.hardwareFiltering = hardwareFiltering;
        }
    }
}
