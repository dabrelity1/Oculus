package net.oculus.pipeline.shadow;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;
import com.google.common.collect.ImmutableSet;
import net.minecraft.client.renderer.GlStateManager;
import net.oculus.gl.OculusRenderSystem;
import net.oculus.gl.texture.DepthBufferFormat;
import net.oculus.gl.texture.InternalTextureFormat;
import net.oculus.gl.texture.PixelType;
import net.oculus.gl.program.ProgramBuilder;
import net.oculus.gl.program.TextureBinding;
import net.oculus.gl.program.TextureBindingRegistry;
import net.oculus.rendertarget.DepthCopyStrategy;
import net.oculus.shaderpack.PackDirectives;
import net.oculus.shaderpack.PackShadowDirectives;
import net.oculus.shaderpack.ProgramLoadException;
import net.oculus.shaderpack.ShaderProperties;
import net.oculus.texture.TextureLifecycleTracker;
import net.oculus.util.Config;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBTextureSwizzle;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

public final class ShadowMap {
    private static final int DEPTH_TARGET_COUNT = 2;
    private static final int COLOR_TARGET_COUNT = PackShadowDirectives.MAX_SHADOW_COLOR_BUFFERS;
    private static final String[] DEPTH_BINDING_NAMES = {
        "shadow",
        "s_shadow",
        "shadowtex0",
        "watershadow",
        "oculus_shadow_depth"
    };
    private static final String[] DEPTH_HARDWARE_BINDING_NAMES = {
        "shadowtex0HW",
        "shadowtex0hw"
    };
    private static final String[] DEPTH_NO_TRANSLUCENTS_BINDING_NAMES = {
        "shadowtex1",
        "oculus_shadow_depth_notrans"
    };
    private static final String[] DEPTH_NO_TRANSLUCENTS_HARDWARE_BINDING_NAMES = {
        "shadowtex1HW",
        "shadowtex1hw"
    };
    private static final String[] COLOR0_BINDING_NAMES = {
        "shadowcolor",
        "s_shadowcolor",
        "shadowcolor0",
        "oculus_shadow_color"
    };
    private static final String[] COLOR1_BINDING_NAMES = {
        "shadowcolor1",
        "oculus_shadow_color1"
    };

    private final boolean enabled;
    private final int resolution;
    private final PackShadowDirectives shadowDirectives;
    private final int[] depthTextures;
    private final int[] colorTextures;
    private final int[] altColorTextures;
    private final boolean[] flippedColorTextures;
    private final List<Integer> buffersToBeCleared;
    private final DepthCopyStrategy depthCopyStrategy;
    private final List<GlFramebuffer> ownedFramebuffers;
    private GlFramebuffer depthSourceFramebuffer;
    private GlFramebuffer noTranslucentsDestFramebuffer;
    private TextureBinding registeredDepthBinding;
    private TextureBinding registeredDepthNoTranslucentsBinding;
    private TextureBinding registeredColor0Binding;
    private TextureBinding registeredColor1Binding;
    private boolean fullClearRequired = true;
    private boolean translucentDepthDirty;
    private boolean destroyed;

    public ShadowMap(PackDirectives directives, ShaderProperties properties, Config config) {
        this(directives, properties, config, false);
    }

    public ShadowMap(PackDirectives directives, ShaderProperties properties, Config config, boolean forceAllocation) {
        boolean renderEnabled = config.shadowsEnabled(directives);
        int configuredResolution = config.shadowResolution(directives);
        this.enabled = configuredResolution > 0 && (forceAllocation || renderEnabled);
        this.resolution = enabled ? configuredResolution : 0;
        this.shadowDirectives = directives.getShadowDirectives();
        this.depthTextures = new int[DEPTH_TARGET_COUNT];
        this.colorTextures = new int[COLOR_TARGET_COUNT];
        this.altColorTextures = new int[COLOR_TARGET_COUNT];
        this.flippedColorTextures = new boolean[COLOR_TARGET_COUNT];
        this.buffersToBeCleared = new ArrayList<>();
        this.depthCopyStrategy = DepthCopyStrategy.fastest(DepthBufferFormat.DEPTH);
        this.ownedFramebuffers = new ArrayList<>();

        for (int i = 0; i < shadowDirectives.getColorSamplingSettings().size(); i++) {
            PackShadowDirectives.SamplingSettings settings = shadowDirectives.getColorSamplingSettings().get(i);
            if (settings.shouldClear()) {
                buffersToBeCleared.add(i);
            }
        }

        if (enabled && resolution > 0) {
            try {
                allocateTextures();
                allocateCopyFramebuffers();
                registerBindings();
            } catch (RuntimeException | Error exception) {
                try {
                    destroy();
                } catch (RuntimeException | Error cleanupException) {
                    if (cleanupException != exception) {
                        exception.addSuppressed(cleanupException);
                    }
                }
                throw exception;
            }
        }
    }

    public static boolean usesShadowTargets(final ProgramBuilder builder) {
        if (builder == null) {
            return false;
        }

        return ShadowSamplerBindings.usesShadowTargets(createShadowResourceQuery(builder));
    }

    private static ShadowSamplerBindings.ResourceQuery createShadowResourceQuery(final ProgramBuilder builder) {
        return new ShadowSamplerBindings.ResourceQuery() {
            @Override
            public boolean hasSampler(String name) {
                return builder.hasSampler(name);
            }

            @Override
            public boolean hasImage(String name) {
                return builder.hasImage(name);
            }

            @Override
            public Set<String> getActiveSamplerNames() {
                return builder.getActiveSamplerUniformNames();
            }

            @Override
            public Set<String> getActiveImageNames() {
                return builder.getActiveImageNames();
            }
        };
    }

    public static ShadowMap requireShadowTargets(ProgramBuilder builder,
            Supplier<ShadowMap> shadowMapSupplier, String programName) {
        String safeProgramName = programName == null ? "<unknown>" : programName;
        ShadowSamplerBindings.ResourceQuery query = builder == null ? null : createShadowResourceQuery(builder);
        String unsupportedResource = ShadowSamplerBindings.findUnsupportedShadowResource(query);
        if (unsupportedResource != null) {
            throw new ProgramLoadException("Program " + safeProgramName
                + " references unsupported shadow resource " + unsupportedResource
                + ". Oculus 1.16.5 exposes two shadow depth targets and two shadow color targets; "
                + "select a shader-pack profile that does not require additional shadow targets.");
        }

        if (!ShadowSamplerBindings.usesShadowTargets(query)) {
            return null;
        }

        ShadowMap shadowMap = shadowMapSupplier == null ? null : shadowMapSupplier.get();
        if (shadowMap == null || !shadowMap.isEnabled()) {
            throw new ProgramLoadException("Program " + safeProgramName
                + " requires shadow samplers or images, but no allocated shadow render targets are available. "
                + "Enable shadow targets or select a shader-pack profile that does not reference shadow samplers/images.");
        }
        return shadowMap;
    }

    public boolean isFullClearRequired() {
        requireLive("read shadow full-clear state");
        return fullClearRequired;
    }

    public boolean consumeFullClearRequired() {
        requireLive("consume shadow full-clear state");
        boolean fullClear = fullClearRequired;
        fullClearRequired = false;
        return fullClear;
    }

    public boolean isEnabled() {
        requireLive("check shadow target allocation");
        return enabled;
    }

    public int getResolution() {
        requireLive("read shadow resolution");
        return resolution;
    }

    public int getDepthTexture() {
        requireLive("read shadow depth texture");
        return depthTextures[0];
    }

    public int getDepthTextureNoTranslucents() {
        requireLive("read no-translucents shadow depth texture");
        return depthTextures[1];
    }

    public int getColorTexture(int index) {
        requireLive("read shadow color texture");
        if (index < 0 || index >= colorTextures.length) {
            throw new IndexOutOfBoundsException("Shadow color texture index " + index);
        }
        return flippedColorTextures[index] ? altColorTextures[index] : colorTextures[index];
    }

    public int getMainColorTexture(int index) {
        requireLive("read main shadow color texture");
        if (index < 0 || index >= colorTextures.length) {
            throw new IndexOutOfBoundsException("Shadow color texture index " + index);
        }
        return colorTextures[index];
    }

    public int getAltColorTexture(int index) {
        requireLive("read alternate shadow color texture");
        if (index < 0 || index >= altColorTextures.length) {
            throw new IndexOutOfBoundsException("Shadow color texture index " + index);
        }
        return altColorTextures[index];
    }

    public boolean isColorTextureFlipped(int index) {
        requireLive("read shadow color flip state");
        if (index < 0 || index >= flippedColorTextures.length) {
            throw new IndexOutOfBoundsException("Shadow color texture index " + index);
        }
        return flippedColorTextures[index];
    }

    public void flipColorTexture(int index) {
        requireLive("flip shadow color texture");
        if (index < 0 || index >= flippedColorTextures.length) {
            throw new IndexOutOfBoundsException("Shadow color texture index " + index);
        }
        flippedColorTextures[index] = !flippedColorTextures[index];
    }

    public InternalTextureFormat getColorTextureFormat(int index) {
        requireLive("read shadow color texture format");
        if (index < 0 || index >= colorTextures.length) {
            throw new IndexOutOfBoundsException("Shadow color texture index " + index);
        }
        return shadowDirectives.getColorSamplingSettings().get(index).getFormat();
    }

    public int getColorTextureCount() {
        requireLive("read shadow color texture count");
        return colorTextures.length;
    }

    public boolean isHardwareFiltered(int index) {
        requireLive("read shadow depth filtering state");
        if (index < 0 || index >= depthTextures.length) {
            throw new IndexOutOfBoundsException("Shadow depth texture index " + index);
        }
        return shadowDirectives.getDepthSamplingSettings().get(index).hasHardwareFiltering();
    }

    public boolean applySamplerBindings(final ProgramBuilder builder, String programName) {
        requireLive("bind shadow samplers");
        if (builder == null) {
            return false;
        }

        String safeProgramName = programName == null ? "<unknown>" : programName;
        ShadowSamplerBindings.ResourceQuery query = createShadowResourceQuery(builder);
        String unsupportedResource = ShadowSamplerBindings.findUnsupportedShadowResource(query);
        if (unsupportedResource != null) {
            throw new ProgramLoadException("Program " + safeProgramName
                + " references unsupported shadow resource " + unsupportedResource
                + ". Oculus 1.16.5 exposes two shadow depth targets and two shadow color targets; "
                + "select a shader-pack profile that does not require additional shadow targets.");
        }

        if (!ShadowSamplerBindings.usesShadowTargets(query)) {
            return false;
        }

        if (!enabled) {
            throw new ProgramLoadException("Program " + safeProgramName
                + " requires shadow samplers or images, but no allocated shadow render targets are available. "
                + "Enable shadow targets or select a shader-pack profile that does not reference shadow samplers/images.");
        }

        final TextureBinding depthRaw = TextureBinding.texture2D(
            () -> depthTextures[0],
            texture -> configureDepthCompareMode(texture, false));
        final TextureBinding depthCompare = TextureBinding.texture2D(
            () -> depthTextures[0],
            texture -> configureDepthCompareMode(texture, true));
        final TextureBinding depthNoTranslucentsRaw = TextureBinding.texture2D(
            () -> depthTextures[1],
            texture -> configureDepthCompareMode(texture, false));
        final TextureBinding depthNoTranslucentsCompare = TextureBinding.texture2D(
            () -> depthTextures[1],
            texture -> configureDepthCompareMode(texture, true));
        final TextureBinding color0 = TextureBinding.texture2D(() -> getColorTexture(0));
        final TextureBinding color1 = TextureBinding.texture2D(() -> getColorTexture(1));

        return ShadowSamplerBindings.apply(new ShadowSamplerBindings.Binder() {
            @Override
            public boolean hasSampler(String name) {
                return builder.hasSampler(name);
            }

            @Override
            public void bind(String name, ShadowSamplerBindings.Target target) {
                builder.overrideSamplerBinding(name, bindingFor(
                    builder,
                    name,
                    target,
                    depthRaw,
                    depthCompare,
                    depthNoTranslucentsRaw,
                    depthNoTranslucentsCompare,
                    color0,
                    color1));
            }
        }, isHardwareFiltered(0), isHardwareFiltered(1));
    }

    public boolean applySamplerBindings(final ProgramBuilder builder) {
        return applySamplerBindings(builder, "<unknown>");
    }

    public void destroy() {
        if (destroyed) {
            return;
        }

        try {
            Throwable failure = null;
            failure = unregisterBindings(failure);
            failure = destroyOwnedFramebuffers(failure);

            for (int i = 0; i < depthTextures.length; i++) {
                failure = deleteTexture(failure, depthTextures[i]);
            }

            for (int i = 0; i < colorTextures.length; i++) {
                failure = deleteTexture(failure, colorTextures[i]);
                failure = deleteTexture(failure, altColorTextures[i]);
            }
            rethrowCleanupFailure(failure);
        } finally {
            clearRegisteredBindings();
            ownedFramebuffers.clear();
            depthSourceFramebuffer = null;
            noTranslucentsDestFramebuffer = null;
            for (int i = 0; i < depthTextures.length; i++) {
                depthTextures[i] = 0;
            }
            for (int i = 0; i < colorTextures.length; i++) {
                colorTextures[i] = 0;
                altColorTextures[i] = 0;
                flippedColorTextures[i] = false;
            }
            buffersToBeCleared.clear();
            fullClearRequired = false;
            translucentDepthDirty = false;
            destroyed = true;
        }
    }

    public ImmutableSet<Integer> snapshot() {
        requireLive("snapshot shadow color flip state");
        ImmutableSet.Builder<Integer> builder = ImmutableSet.builder();
        for (int i = 0; i < flippedColorTextures.length; i++) {
            if (flippedColorTextures[i]) {
                builder.add(i);
            }
        }
        return builder.build();
    }

    public List<Integer> getBuffersToBeCleared() {
        requireLive("read shadow clear buffers");
        return Collections.unmodifiableList(buffersToBeCleared);
    }

    private void allocateTextures() {
        for (int i = 0; i < depthTextures.length; i++) {
            PackShadowDirectives.DepthSamplingSettings settings = shadowDirectives.getDepthSamplingSettings().get(i);
            depthTextures[i] = createDepthTexture(resolution, settings);
        }
        for (int i = 0; i < colorTextures.length; i++) {
            PackShadowDirectives.SamplingSettings settings = shadowDirectives.getColorSamplingSettings().get(i);
            colorTextures[i] = createColorTexture(resolution, settings.getFormat(), settings);
            altColorTextures[i] = createColorTexture(resolution, settings.getFormat(), settings);
        }
    }

    private void allocateCopyFramebuffers() {
        depthSourceFramebuffer = createDepthCopyFramebuffer(depthTextures[0]);
        noTranslucentsDestFramebuffer = createDepthCopyFramebuffer(depthTextures[1]);
        translucentDepthDirty = true;
    }

    public GlFramebuffer createFramebufferWritingToMain(int[] drawBuffers) {
        requireLive("create main shadow framebuffer");
        return createFullFramebuffer(false, drawBuffers);
    }

    public GlFramebuffer createFramebufferWritingToAlt(int[] drawBuffers) {
        requireLive("create alternate shadow framebuffer");
        return createFullFramebuffer(true, drawBuffers);
    }

    public GlFramebuffer createShadowFramebuffer(Set<Integer> stageWritesToAlt, int[] drawBuffers) {
        requireLive("create shadow render framebuffer");
        requireAllocatedShadowTargets("Shadow render framebuffer");
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        return createColorFramebufferWithDepth(invert(stageWritesToAlt, drawBuffers), drawBuffers);
    }

    public GlFramebuffer getDepthSourceFramebuffer() {
        requireLive("read shadow depth source framebuffer");
        if (depthSourceFramebuffer == null) {
            throw new IllegalStateException("Shadow depth source framebuffer is not initialized");
        }
        return depthSourceFramebuffer;
    }

    public void destroyFramebuffer(GlFramebuffer framebuffer) {
        rethrowCleanupFailure(destroyFramebuffer(null, framebuffer));
    }

    private GlFramebuffer createFullFramebuffer(boolean clearsAlt, int[] drawBuffers) {
        requireAllocatedShadowTargets("Shadow clear framebuffer");
        if (drawBuffers.length == 0) {
            return createEmptyFramebuffer();
        }

        Set<Integer> stageWritesToMain = new HashSet<>();
        if (!clearsAlt) {
            for (int drawBuffer : drawBuffers) {
                stageWritesToMain.add(drawBuffer);
            }
        }

        return createColorFramebufferWithDepth(stageWritesToMain, drawBuffers);
    }

    private Set<Integer> invert(Set<Integer> base, int[] relevant) {
        Set<Integer> inverted = new HashSet<>();
        for (int buffer : relevant) {
            if (base == null || !base.contains(buffer)) {
                inverted.add(buffer);
            }
        }
        return inverted;
    }

    private GlFramebuffer createEmptyFramebuffer() {
        GlFramebuffer framebuffer = createOwnedFramebuffer();
        try {
            framebuffer.addDepthAttachment(requireValidShadowTexture(depthTextures[0],
                "Shadow empty framebuffer depth texture"));
            framebuffer.addColorAttachment(0, requireValidShadowTexture(colorTextures[0],
                "Shadow empty framebuffer color attachment"));
            framebuffer.noDrawBuffers();
            if (!framebuffer.isComplete()) {
                throw new IllegalStateException("Shadow empty framebuffer incomplete");
            }
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyFramebuffer(null, framebuffer);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }
    }

    private GlFramebuffer createColorFramebufferWithDepth(Set<Integer> stageWritesToMain, int[] drawBuffers) {
        GlFramebuffer framebuffer = createColorFramebuffer(stageWritesToMain, drawBuffers);
        try {
            framebuffer.addDepthAttachment(requireValidShadowTexture(depthTextures[0],
                "Shadow color framebuffer depth texture"));
            if (!framebuffer.isComplete()) {
                throw new IllegalStateException("Shadow color framebuffer incomplete after depth attachment");
            }
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyFramebuffer(null, framebuffer);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }
    }

    private GlFramebuffer createColorFramebuffer(Set<Integer> stageWritesToMain, int[] drawBuffers) {
        if (drawBuffers.length == 0) {
            throw new IllegalArgumentException("Framebuffer must have at least one shadow color buffer");
        }

        GlFramebuffer framebuffer = createOwnedFramebuffer();
        try {
            for (int i = 0; i < drawBuffers.length; i++) {
                int bufferIndex = drawBuffers[i];
                requireValidShadowColorBuffer(bufferIndex);
                int textureId = stageWritesToMain.contains(bufferIndex)
                    ? colorTextures[bufferIndex]
                    : altColorTextures[bufferIndex];
                framebuffer.addColorAttachment(i, requireValidShadowTexture(textureId,
                    "Shadow clear framebuffer color attachment"));
            }

            framebuffer.drawBuffers(logicalDrawBuffers(drawBuffers.length));
            framebuffer.readBuffer(0);

            if (!framebuffer.isComplete()) {
                throw new IllegalStateException("Shadow color framebuffer incomplete");
            }
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyFramebuffer(null, framebuffer);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }
    }

    private static int[] logicalDrawBuffers(int count) {
        int[] actualDrawBuffers = new int[count];
        for (int i = 0; i < count; i++) {
            actualDrawBuffers[i] = i;
        }
        return actualDrawBuffers;
    }

    private void requireAllocatedShadowTargets(String context) {
        if (!enabled || resolution <= 0) {
            throw new IllegalStateException(context + " requires allocated shadow targets");
        }
    }

    private void requireValidShadowColorBuffer(int index) {
        if (index < 0 || index >= colorTextures.length) {
            throw new IllegalStateException("Shadow color buffer " + index + " is not supported");
        }
    }

    private GlFramebuffer createDepthCopyFramebuffer(int depthTexture) {
        requireValidShadowTexture(depthTexture, "Shadow depth copy framebuffer depth texture");
        requireValidShadowTexture(colorTextures[0], "Shadow depth copy framebuffer color attachment");
        GlFramebuffer framebuffer = createOwnedFramebuffer();

        try {
            framebuffer.addDepthAttachment(depthTexture);
            framebuffer.addColorAttachment(0, colorTextures[0]);
            framebuffer.drawBuffers(logicalDrawBuffers(1));
            framebuffer.readBuffer(0);
            if (!framebuffer.isComplete()) {
                throw new IllegalStateException("Shadow depth copy framebuffer incomplete");
            }
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyFramebuffer(null, framebuffer);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }
    }

    private GlFramebuffer createOwnedFramebuffer() {
        GlFramebuffer framebuffer = new GlFramebuffer();
        try {
            ownedFramebuffers.add(framebuffer);
            return framebuffer;
        } catch (RuntimeException | Error exception) {
            Throwable failure = destroyFramebuffer(null, framebuffer);
            addSuppressedCleanupFailure(exception, failure);
            throw exception;
        }
    }

    private void registerBindings() {
        registeredDepthBinding = TextureBinding.texture2D(() -> depthTextures[0]);
        registeredDepthNoTranslucentsBinding = TextureBinding.texture2D(() -> depthTextures[1]);
        registeredColor0Binding = TextureBinding.texture2D(() -> getColorTexture(0));
        registeredColor1Binding = TextureBinding.texture2D(() -> getColorTexture(1));

        registerBindings(DEPTH_BINDING_NAMES, registeredDepthBinding);
        if (isHardwareFiltered(0)) {
            registerBindings(DEPTH_HARDWARE_BINDING_NAMES, registeredDepthBinding);
        }
        registerBindings(DEPTH_NO_TRANSLUCENTS_BINDING_NAMES, registeredDepthNoTranslucentsBinding);
        if (isHardwareFiltered(1)) {
            registerBindings(DEPTH_NO_TRANSLUCENTS_HARDWARE_BINDING_NAMES, registeredDepthNoTranslucentsBinding);
        }
        registerBindings(COLOR0_BINDING_NAMES, registeredColor0Binding);
        registerBindings(COLOR1_BINDING_NAMES, registeredColor1Binding);
    }

    private static void registerBindings(String[] names, TextureBinding binding) {
        for (String name : names) {
            TextureBindingRegistry.register(name, binding);
        }
    }

    private void unregisterBindings() {
        try {
            Throwable failure = unregisterBindings(null);
            rethrowCleanupFailure(failure);
        } finally {
            clearRegisteredBindings();
        }
    }

    private Throwable unregisterBindings(Throwable failure) {
        failure = unregisterBindings(failure, DEPTH_BINDING_NAMES, registeredDepthBinding);
        failure = unregisterBindings(failure, DEPTH_HARDWARE_BINDING_NAMES, registeredDepthBinding);
        failure = unregisterBindings(failure, DEPTH_NO_TRANSLUCENTS_BINDING_NAMES, registeredDepthNoTranslucentsBinding);
        failure = unregisterBindings(failure, DEPTH_NO_TRANSLUCENTS_HARDWARE_BINDING_NAMES,
            registeredDepthNoTranslucentsBinding);
        failure = unregisterBindings(failure, COLOR0_BINDING_NAMES, registeredColor0Binding);
        failure = unregisterBindings(failure, COLOR1_BINDING_NAMES, registeredColor1Binding);
        return failure;
    }

    private void clearRegisteredBindings() {
        registeredDepthBinding = null;
        registeredDepthNoTranslucentsBinding = null;
        registeredColor0Binding = null;
        registeredColor1Binding = null;
    }

    private static Throwable unregisterBindings(Throwable failure, String[] names, TextureBinding binding) {
        if (names == null || binding == null) {
            return failure;
        }
        for (String name : names) {
            failure = runCleanup(failure, () -> TextureBindingRegistry.unregister(name, binding));
        }
        return failure;
    }

    private static TextureBinding bindingFor(
            ProgramBuilder builder,
            String samplerName,
            ShadowSamplerBindings.Target target,
            TextureBinding depthRaw,
            TextureBinding depthCompare,
            TextureBinding depthNoTranslucentsRaw,
            TextureBinding depthNoTranslucentsCompare,
            TextureBinding color0,
            TextureBinding color1) {
        switch (target) {
            case DEPTH:
                return isShadowCompareSampler(builder, samplerName) ? depthCompare : depthRaw;
            case DEPTH_NO_TRANSLUCENTS:
                return isShadowCompareSampler(builder, samplerName)
                    ? depthNoTranslucentsCompare
                    : depthNoTranslucentsRaw;
            case COLOR0:
                return color0;
            case COLOR1:
                return color1;
            case UNBOUND:
            default:
                return TextureBinding.unbound();
        }
    }

    private static boolean isShadowCompareSampler(ProgramBuilder builder, String samplerName) {
        if (isHardwareShadowSamplerName(samplerName)) {
            return true;
        }
        return builder != null && builder.getActiveSamplerUniformType(samplerName) == GL20.GL_SAMPLER_2D_SHADOW;
    }

    private static boolean isHardwareShadowSamplerName(String samplerName) {
        return "shadowtex0HW".equals(samplerName)
            || "shadowtex0hw".equals(samplerName)
            || "shadowtex1HW".equals(samplerName)
            || "shadowtex1hw".equals(samplerName);
    }

    private static void configureDepthCompareMode(int texture, boolean compare) {
        OculusRenderSystem.texParameteri(
            texture,
            GL11.GL_TEXTURE_2D,
            GL14.GL_TEXTURE_COMPARE_MODE,
            compare ? GL14.GL_COMPARE_R_TO_TEXTURE : GL11.GL_NONE);
    }

    public void copyDepthToNoTranslucents() {
        requireLive("copy no-translucents shadow depth");
        if (!enabled) {
            return;
        }
        requireValidShadowTexture(depthTextures[0], "Shadow source depth texture");
        requireValidShadowTexture(depthTextures[1], "Shadow no-translucents depth texture");

        if (depthSourceFramebuffer == null || noTranslucentsDestFramebuffer == null) {
            throw new IllegalStateException("Shadow depth copy framebuffers are not initialized");
        }

        int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();
        int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();
        int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = 0;
        boolean restoreTexture = false;
        boolean copyCompleted = false;
        Throwable failure = null;
        try {
            OculusRenderSystem.restoreDefaultActiveTexture();
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            restoreTexture = true;
            boolean useBlit = translucentDepthDirty && OculusRenderSystem.supportsFramebufferBlit();
            if (useBlit) {
                OculusRenderSystem.blitFramebuffer(
                    depthSourceFramebuffer.getId(),
                    noTranslucentsDestFramebuffer.getId(),
                    0,
                    0,
                    resolution,
                    resolution,
                    0,
                    0,
                    resolution,
                    resolution,
                    GL11.GL_DEPTH_BUFFER_BIT,
                    GL11.GL_NEAREST);
            } else {
                depthCopyStrategy.copy(depthSourceFramebuffer, depthTextures[0], noTranslucentsDestFramebuffer,
                    depthTextures[1], resolution, resolution);
            }
            copyCompleted = true;
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (restoreTexture) {
                cleanupFailure = restoreDefaultTextureBinding(cleanupFailure, previousTexture);
            }
            try {
                OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,
                    previousDrawFramebuffer);
            } catch (RuntimeException | Error exception) {
                cleanupFailure = addCleanupFailure(cleanupFailure, exception);
            }
            try {
                OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
            } catch (RuntimeException | Error exception) {
                cleanupFailure = addCleanupFailure(cleanupFailure, exception);
            }
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }

        if (copyCompleted) {
            translucentDepthDirty = false;
        }
    }

    private static int createDepthTexture(int size, PackShadowDirectives.DepthSamplingSettings settings) {
        int texture = createTexture("shadow depth texture");
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                configureSampler(texture, settings);
                if (settings.hasHardwareFiltering()) {
                    OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE,
                        GL14.GL_COMPARE_R_TO_TEXTURE);
                }
                configureDepthSwizzle(texture);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                DepthBufferFormat format = DepthBufferFormat.DEPTH;
                OculusRenderSystem.texImage2D(texture, GL11.GL_TEXTURE_2D, 0, format.getGlInternalFormat(), size, size, 0,
                    format.getGlPixelFormat(), format.getGlPixelType(), null);
            });
            return texture;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, runCleanup(null, () -> deleteTexture(texture)));
            throw exception;
        }
    }

    private static int createColorTexture(int size, InternalTextureFormat format, PackShadowDirectives.SamplingSettings settings) {
        int texture = createTexture("shadow color texture");
        try {
            OculusRenderSystem.withDefaultTextureBindingRestored(() -> {
                configureSampler(texture, settings);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                OculusRenderSystem.texImage2D(
                    texture,
                    GL11.GL_TEXTURE_2D,
                    0,
                    format.getGlFormat(),
                    size,
                    size,
                    0,
                    format.getPixelFormat().getGlFormat(),
                    PixelType.UNSIGNED_BYTE.getGlFormat(),
                    null);
            });
            return texture;
        } catch (RuntimeException | Error exception) {
            addSuppressedCleanupFailure(exception, runCleanup(null, () -> deleteTexture(texture)));
            throw exception;
        }
    }

    private static int createTexture(String context) {
        int texture = GL11.glGenTextures();
        if (texture <= 0) {
            throw new IllegalStateException("Failed to create " + context);
        }
        return texture;
    }

    static int[] depthSwizzleRgba() {
        return new int[] { GL11.GL_RED, GL11.GL_RED, GL11.GL_RED, GL11.GL_ONE };
    }

    private static void configureDepthSwizzle(int texture) {
        int[] swizzle = depthSwizzleRgba();
        IntBuffer buffer = BufferUtils.createIntBuffer(swizzle.length);
        buffer.put(swizzle);
        buffer.flip();
        OculusRenderSystem.texParameter(texture, GL11.GL_TEXTURE_2D, ARBTextureSwizzle.GL_TEXTURE_SWIZZLE_RGBA, buffer);
    }

    private static void configureSampler(int texture, PackShadowDirectives.SamplingSettings settings) {
        int minFilter = baseMinFilter(settings.getNearest());
        int magFilter = minFilter;
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, minFilter);
        OculusRenderSystem.texParameteri(texture, GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, magFilter);
    }

    static int baseMinFilter(boolean nearest) {
        return nearest ? GL11.GL_NEAREST : GL11.GL_LINEAR;
    }

    static int mipmapMinFilter(boolean nearest) {
        return nearest ? GL11.GL_NEAREST_MIPMAP_NEAREST : GL11.GL_LINEAR_MIPMAP_LINEAR;
    }

    public void generateMipmaps() {
        requireLive("generate shadow mipmaps");
        if (!enabled) {
            return;
        }

        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int previousTexture = 0;
        boolean textureCaptured = false;
        Throwable failure = null;
        try {
            OculusRenderSystem.setActiveTextureUnit(GL13.GL_TEXTURE4);
            previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            textureCaptured = true;

            for (int i = 0; i < shadowDirectives.getDepthSamplingSettings().size(); i++) {
                PackShadowDirectives.DepthSamplingSettings settings = shadowDirectives.getDepthSamplingSettings().get(i);
                if (settings.getMipmap()) {
                    generateMipmap(depthTextures[i], settings);
                }
            }

            for (int i = 0; i < shadowDirectives.getColorSamplingSettings().size(); i++) {
                PackShadowDirectives.SamplingSettings settings = shadowDirectives.getColorSamplingSettings().get(i);
                if (settings.getMipmap()) {
                    generateMipmap(getColorTexture(i), settings);
                }
            }
        } catch (RuntimeException | Error exception) {
            failure = exception;
            throw exception;
        } finally {
            Throwable cleanupFailure = null;
            if (textureCaptured) {
                cleanupFailure = restoreTextureUnitBinding(cleanupFailure, GL13.GL_TEXTURE4, previousTexture);
            }
            try {
                OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);
            } catch (RuntimeException | Error exception) {
                cleanupFailure = addCleanupFailure(cleanupFailure, exception);
            }
            if (failure != null) {
                addSuppressedCleanupFailure(failure, cleanupFailure);
            } else {
                rethrowCleanupFailure(cleanupFailure);
            }
        }
    }

    private static void generateMipmap(int texture, PackShadowDirectives.SamplingSettings settings) {
        requireValidShadowTexture(texture, "Shadow mipmap texture");
        OculusRenderSystem.generateMipmaps(texture, GL11.GL_TEXTURE_2D);
        OculusRenderSystem.texParameteri(
            texture,
            GL11.GL_TEXTURE_2D,
            GL11.GL_TEXTURE_MIN_FILTER,
            mipmapMinFilter(settings.getNearest()));
    }

    private static int requireValidShadowTexture(int texture, String context) {
        if (texture <= 0) {
            throw new IllegalStateException(context + " is not allocated");
        }
        return texture;
    }

    private void requireLive(String operation) {
        if (destroyed) {
            throw new IllegalStateException("Cannot " + operation + " after shadow render targets were destroyed");
        }
    }

    private static void deleteTexture(int texture) {
        if (texture <= 0) {
            return;
        }

        Throwable failure = null;
        try {
            GL11.glDeleteTextures(texture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        } finally {
            try {
                TextureLifecycleTracker.onDeleteTexture(texture);
            } catch (RuntimeException | Error exception) {
                failure = addCleanupFailure(failure, exception);
            }
        }

        rethrowCleanupFailure(failure);
    }

    private static Throwable deleteTexture(Throwable failure, int texture) {
        if (texture <= 0) {
            return failure;
        }
        try {
            deleteTexture(texture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private Throwable destroyOwnedFramebuffers(Throwable failure) {
        for (GlFramebuffer framebuffer : new ArrayList<>(ownedFramebuffers)) {
            failure = destroyFramebuffer(failure, framebuffer);
        }
        return failure;
    }

    private Throwable destroyFramebuffer(Throwable failure, GlFramebuffer framebuffer) {
        if (framebuffer == null) {
            return failure;
        }
        try {
            framebuffer.destroy();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        } finally {
            ownedFramebuffers.remove(framebuffer);
        }
        return failure;
    }

    private static Throwable runCleanup(Throwable failure, Runnable cleanup) {
        if (cleanup == null) {
            return failure;
        }
        try {
            cleanup.run();
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable restoreDefaultTextureBinding(Throwable failure, int previousTexture) {
        return restoreTextureUnitBinding(failure, GL13.GL_TEXTURE0, previousTexture);
    }

    private static Throwable restoreTextureUnitBinding(Throwable failure, int textureUnit, int previousTexture) {
        try {
            OculusRenderSystem.setActiveTextureUnit(textureUnit);
            GlStateManager.bindTexture(previousTexture);
        } catch (RuntimeException | Error exception) {
            failure = addCleanupFailure(failure, exception);
        }
        return failure;
    }

    private static Throwable addCleanupFailure(Throwable failure, Throwable exception) {
        if (failure == null) {
            return exception;
        }
        if (exception != failure) {
            failure.addSuppressed(exception);
        }
        return failure;
    }

    private static void addSuppressedCleanupFailure(Throwable primary, Throwable cleanupFailure) {
        if (primary != null && cleanupFailure != null && cleanupFailure != primary) {
            primary.addSuppressed(cleanupFailure);
        }
    }

    private static void rethrowCleanupFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new RuntimeException(failure);
    }
}
