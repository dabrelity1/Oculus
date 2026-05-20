package net.oculus.pipeline.shadow;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class ShadowRendererSourceTest {
    @Test
    public void rootShadowProgramIsOwnedByWorldPipelineNotShadowRenderer() throws Exception {
        String shadowRenderer = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String worldPipeline = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String shaderLoader = read("src/main/java/net/oculus/shader/ShaderLoader.java");
        String renderBody = shadowRenderer.substring(
            shadowRenderer.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            shadowRenderer.indexOf("private static void restoreDefaultTextureBinding"));

        assertFalse(shadowRenderer.contains("private final Program shadowProgram;"));
        assertFalse(shadowRenderer.contains("Failed to compile shadow program"));
        assertFalse(shadowRenderer.contains("Shadow program compiled"));
        assertFalse(shadowRenderer.contains("shadowProgram.use();"));
        assertFalse(shadowRenderer.contains("shadowProgram.destroy();"));
        assertFalse(renderBody.contains("Program.unbind();"));
        assertTrue(shaderLoader.contains("addOptional(programSet.getShadow(), sources);"));

        int shadowFlag = worldPipeline.indexOf("isRenderingShadow = true;");
        int bindFramebuffer = worldPipeline.indexOf("shadowRenderer.bindFramebufferForShadowPass();", shadowFlag);
        int syncProgram = worldPipeline.indexOf("syncProgram();", bindFramebuffer);
        int renderShadows = worldPipeline.indexOf("shadowRenderer.renderShadows(renderGlobal, cameraEntity, partialTicks);",
            syncProgram);
        assertTrue(shadowFlag >= 0);
        assertTrue("The shadow framebuffer must be selected before the root shadow program is used",
            bindFramebuffer > shadowFlag && syncProgram > bindFramebuffer);
        assertTrue(renderShadows > syncProgram);
    }

    @Test
    public void shadowProgramFramebufferBindHappensBeforeProgramUseLikeReferencePassUse() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String worldPipeline = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String bindHelper = methodBody(source, "public void bindFramebufferForShadowPass");
        String renderShadows = methodBody(worldPipeline,
            "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        String bindWorldFramebuffer = methodBody(worldPipeline,
            "private void bindWorldFramebufferForProgram");

        int failDestroyed = bindHelper.indexOf("failIfDestroyed(\"bind shadow pass framebuffer\");");
        int enabledGuard = bindHelper.indexOf("if (!shadowMap.isEnabled() || resolution <= 0)", failDestroyed);
        int bind = bindHelper.indexOf("shadowFramebuffer.bind();", enabledGuard);
        int viewport = bindHelper.indexOf("GL11.glViewport(0, 0, resolution, resolution);", bind);

        int shadowFlag = renderShadows.indexOf("isRenderingShadow = true;");
        int shouldRenderThisFrame = renderShadows.indexOf("if (shadowRenderer.shouldRenderThisFrame())", shadowFlag);
        int bindBeforeSync = renderShadows.indexOf("shadowRenderer.bindFramebufferForShadowPass();",
            shouldRenderThisFrame);
        int sync = renderShadows.indexOf("syncProgram();", bindBeforeSync);
        int ssboBind = renderShadows.indexOf("shaderStorageBufferManager.bindAll();", sync);
        int render = renderShadows.indexOf("shadowRenderer.renderShadows(renderGlobal, cameraEntity, partialTicks);",
            ssboBind);
        int shadowGuard = bindWorldFramebuffer.indexOf("if (isRenderingShadow) {");
        int helperBind = bindWorldFramebuffer.indexOf(".bindFramebufferForShadowPass();", shadowGuard);
        int helperReturn = bindWorldFramebuffer.indexOf("return;", helperBind);

        assertTrue("Shadow framebuffer bind helper must reject destroyed renderers",
            failDestroyed >= 0);
        assertTrue("Shadow framebuffer bind helper must no-op for unavailable targets",
            enabledGuard > failDestroyed);
        assertTrue("Shadow framebuffer bind helper must select the shadow framebuffer before viewport setup",
            bind > enabledGuard && viewport > bind);
        assertTrue("Pipeline shadow sync must skip root shadow setup when the renderer would early-return like 1.16.5",
            shadowFlag >= 0 && shouldRenderThisFrame > shadowFlag && bindBeforeSync > shouldRenderThisFrame);
        assertTrue("Pipeline shadow sync must bind the shadow framebuffer before using the root shadow program",
            sync > bindBeforeSync);
        assertTrue("Every shadow program sync must reselect the shadow framebuffer like 1.16.5 Pass.use()",
            shadowGuard >= 0 && helperBind > shadowGuard && helperReturn > helperBind);
        assertTrue("SSBO refresh and shadow rendering must remain after root shadow program sync",
            ssboBind > sync && render > ssboBind);
    }

    @Test
    public void shadowComputeCompilationStillFailsFastAndUsesOculusBindings() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String samplers = read("src/main/java/net/oculus/samplers/IrisSamplers.java");

        assertTrue(source.contains(
            "ProgramBuilder.beginCompute(source.getName(), computeSource, null,"));
        assertTrue(samplers.contains(
            "public static final ImmutableSet<Integer> WORLD_RESERVED_TEXTURE_UNITS = ImmutableSet.of(0, 1, 2);"));
        assertTrue(source.contains(
            "customUniforms, IrisSamplers.WORLD_RESERVED_TEXTURE_UNITS, frameUpdateNotifier, directives);"));
        assertFalse(source.contains("private static final ImmutableSet<Integer> WORLD_RESERVED_TEXTURE_UNITS"));
        assertFalse("Shadow compute programs must reserve world texture units like 1.16.5",
            source.contains("customUniforms, Collections.emptySet(), frameUpdateNotifier, directives);"));
        assertTrue(source.contains(
            "applyCustomBindings(source.getName(), builder, this::currentShadowComputeFlippedBuffers);"));
        assertTrue(source.contains("throw new ProgramLoadException(\"Failed to compile shadow compute shader \""));
        assertFalse(source.contains("LOGGER.warn(\"Failed to compile shadow compute shader"));
        assertTrue(source.contains(
            "IrisSamplers.addRenderTargetSamplerBindings(builder, flippedBuffers, renderTargets, false);"));
        assertTrue(source.contains("IrisImages.addRenderTargetImages(builder, flippedBuffers, renderTargets);"));
        assertTrue(source.contains(
            "IrisSamplers.addLevelSamplerBindings(builder, levelSamplerPipeline, new InputAvailability(true, true, false));"));
        assertTrue(source.contains("bindNoiseSampler(builder);"));
        assertTrue(source.contains("shadowMap.applySamplerBindings(builder, programName);"));
        assertTrue(source.contains("IrisImages.addShadowColorImages(builder, shadowMap);"));
        assertTrue(source.contains(
            "customTextureManager.applyCustomSamplers(programName, builder.samplers(),"));
        assertTrue(source.contains("currentFlippedBuffers(flippedBuffers));"));
        assertTrue(source.contains("builder.overrideSamplerBinding(\"noisetex\", TextureBinding.texture2D(noiseTexture));"));
    }

    @Test
    public void shadowComputeBindingsMirrorReferenceOrdering() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String bindings = source.substring(
            source.indexOf("private void applyCustomBindings("),
            source.indexOf("private String applyStandardDefines"));

        int renderTargets = bindings.indexOf("RenderTargets renderTargets = renderTargetsSupplier == null");
        int samplerBindings = bindings.indexOf(
            "IrisSamplers.addRenderTargetSamplerBindings(builder, flippedBuffers, renderTargets, false);",
            renderTargets);
        int imageBindings = bindings.indexOf(
            "IrisImages.addRenderTargetImages(builder, flippedBuffers, renderTargets);",
            samplerBindings);
        int levelSamplerBindings = bindings.indexOf(
            "IrisSamplers.addLevelSamplerBindings(builder, levelSamplerPipeline, new InputAvailability(true, true, false));",
            imageBindings);
        int noiseBinding = bindings.indexOf("bindNoiseSampler(builder);", levelSamplerBindings);
        int shadowSamplerBindings = bindings.indexOf("shadowMap.applySamplerBindings(builder, programName);", noiseBinding);
        int shadowImageBindings = bindings.indexOf("IrisImages.addShadowColorImages(builder, shadowMap);",
            shadowSamplerBindings);
        int customTextureBindings = bindings.indexOf(
            "customTextureManager.applyCustomSamplers(programName, builder.samplers(),",
            shadowImageBindings);
        int customImageBindings = bindings.indexOf("customImageManager.applyToProgram(builder);",
            customTextureBindings);

        assertTrue("Shadow compute setup must fetch render targets before binding resources", renderTargets >= 0);
        assertTrue("Shadow computes must explicitly bind render target samplers before images like 1.16.5",
            samplerBindings > renderTargets && imageBindings > samplerBindings);
        assertFalse("Shadow compute render-target bindings must call the fail-fast helpers even when targets are absent",
            bindings.substring(renderTargets, levelSamplerBindings).contains("if (renderTargets != null)"));
        assertTrue("Shadow computes must bind world-level samplers after render-target resources like 1.16.5",
            levelSamplerBindings > imageBindings);
        assertTrue("Shadow computes must bind noisetex before shadow samplers like 1.16.5",
            noiseBinding > levelSamplerBindings && shadowSamplerBindings > noiseBinding);
        assertTrue("Shadow sampler and image bindings must follow noisetex before custom sampler overrides",
            shadowImageBindings > shadowSamplerBindings && customTextureBindings > shadowImageBindings);
        assertTrue("Custom images should be applied after sampler overrides",
            customImageBindings > customTextureBindings);
    }

    @Test
    public void rootShadowAndShadowCompositeComputesUseStageSpecificFlipSuppliers() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String constructor = source.substring(
            source.indexOf("public ShadowRenderer(PackDirectives directives,"),
            source.indexOf("public void setUsesImages"));
        String compileShadow = methodBody(source, "private ComputeProgram[] compileShadowComputes");
        String compileCompositeGroup = methodBody(source, "private ComputeProgram[] compileShadowCompositeComputeGroup");
        String helpers = source.substring(
            source.indexOf("private Set<Integer> currentShadowComputeFlippedBuffers()"),
            source.indexOf("private void bindNoiseSampler"));

        assertTrue(source.contains("private final Supplier<? extends Set<Integer>> shadowComputeFlippedBuffersSupplier;"));
        assertTrue(source.contains("private final Supplier<? extends Set<Integer>> shadowCompositeFlippedBuffersSupplier;"));
        assertTrue(constructor.contains("Supplier<? extends Set<Integer>> shadowComputeFlippedBuffersSupplier,"));
        assertTrue(constructor.contains("Supplier<? extends Set<Integer>> shadowCompositeFlippedBuffersSupplier,"));
        assertTrue(constructor.contains("this.shadowComputeFlippedBuffersSupplier = shadowComputeFlippedBuffersSupplier;"));
        assertTrue(constructor.contains("this.shadowCompositeFlippedBuffersSupplier = shadowCompositeFlippedBuffersSupplier;"));
        assertTrue("Root shadow computes run during target preparation and must not inherit post-prepare flips",
            compileShadow.contains(
                "applyCustomBindings(source.getName(), builder, this::currentShadowComputeFlippedBuffers);"));
        assertTrue("Compute-only shadowcomp passes run after shadow geometry and use the shadow-stage read buffers",
            compileCompositeGroup.contains(
                "applyCustomBindings(source.getName(), builder, this::currentShadowCompositeFlippedBuffers);"));
        assertTrue(helpers.contains("return currentFlippedBuffers(shadowComputeFlippedBuffersSupplier);"));
        assertTrue(helpers.contains("return currentFlippedBuffers(shadowCompositeFlippedBuffersSupplier);"));
        assertTrue(helpers.contains(
            "private Set<Integer> currentFlippedBuffers(Supplier<? extends Set<Integer>> flippedBuffersSupplier)"));
        assertTrue(helpers.contains("return flippedBuffers == null ? Collections.emptySet() : flippedBuffers;"));
    }

    @Test
    public void shadowComputeCompilationDestroysPartialProgramsOnFailure() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String compileComputes = source.substring(
            source.indexOf("private ComputeProgram[] compileShadowComputes"),
            source.indexOf("private void dispatchShadowComputes"));

        int programsArray = compileComputes.indexOf("ComputeProgram[] programs = new ComputeProgram[sources.length];");
        int outerTry = compileComputes.indexOf("try {", programsArray);
        int innerTry = compileComputes.indexOf("try {", outerTry + 1);
        int buildCompute = compileComputes.indexOf("ComputeProgram program = builder.buildCompute();", innerTry);
        int assignProgram = compileComputes.indexOf("programs[i] = program;", buildCompute);
        int setWorkgroups = compileComputes.indexOf(
            "program.setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());", assignProgram);
        int failFastWrap = compileComputes.indexOf(
            "throw new ProgramLoadException(\"Failed to compile shadow compute shader \"", setWorkgroups);
        int cleanup = compileComputes.indexOf("} catch (RuntimeException | Error exception) {", failFastWrap);
        int destroyPrograms = compileComputes.indexOf(
            "addSuppressedCleanupFailure(exception, destroyComputePrograms(null, programs));", cleanup);
        int rethrow = compileComputes.indexOf("throw exception;", destroyPrograms);

        assertTrue(programsArray >= 0);
        assertTrue(outerTry > programsArray);
        assertTrue(innerTry > outerTry);
        assertTrue(buildCompute > innerTry);
        assertTrue(assignProgram > buildCompute);
        assertTrue(setWorkgroups > assignProgram);
        assertTrue(failFastWrap > setWorkgroups);
        assertTrue(cleanup > failFastWrap);
        assertTrue("Shadow compute cleanup failures must be suppressed onto the original compile failure",
            destroyPrograms > cleanup);
        assertTrue(rethrow > destroyPrograms);
    }

    @Test
    public void shadowCompositeComputePassesCompileDispatchAndCleanUpExplicitly() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String constructorBody = source.substring(
            source.indexOf("ComputeProgram[] compiledShadowComputes = compileShadowComputes(shadowComputeSources);"),
            source.indexOf("copyMatrix(ShadowUniforms.getShadowRenderProjection()"));
        String renderBody = methodBody(source,
            "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        String dispatchComposite = methodBody(source, "private void dispatchShadowCompositeComputes()");
        String compileComposite = methodBody(source, "private ComputeProgram[][] compileShadowCompositeComputes");
        String compileGroup = methodBody(source, "private ComputeProgram[] compileShadowCompositeComputeGroup");
        String dispatchGroup = methodBody(source, "private boolean dispatchComputeGroup");
        String destroyBody = source.substring(
            source.indexOf("public void destroy()"),
            source.indexOf("private static void destroyComputePrograms"));
        String destroyHelpers = source.substring(source.indexOf("private static Throwable destroyComputePrograms"));

        assertTrue(source.contains("private final ComputeProgram[][] shadowCompositeComputes;"));
        assertTrue(source.contains("ComputeSource[][] shadowCompositeComputeSources,"));
        assertTrue(source.contains("private static final int COMPUTE_BARRIER = GL42.GL_TEXTURE_FETCH_BARRIER_BIT"));
        assertTrue(source.contains("| GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;"));

        int rootCompile = constructorBody.indexOf(
            "ComputeProgram[] compiledShadowComputes = compileShadowComputes(shadowComputeSources);");
        int compositeLocal = constructorBody.indexOf(
            "ComputeProgram[][] compiledShadowCompositeComputes = new ComputeProgram[0][];", rootCompile);
        int tryBlock = constructorBody.indexOf("try {", compositeLocal);
        int compositeCompile = constructorBody.indexOf(
            "compiledShadowCompositeComputes = compileShadowCompositeComputes(shadowCompositeComputeSources);",
            tryBlock);
        int createFramebuffer = constructorBody.indexOf(
            "createdShadowFramebuffer = shadowMap.createShadowFramebuffer(shadowMap.snapshot(), shadowDrawBuffers());",
            compositeCompile);
        int catchBlock = constructorBody.indexOf("catch (RuntimeException | Error exception)", createFramebuffer);
        int destroyComposite = constructorBody.indexOf(
            "failure = destroyComputeProgramGroups(failure, compiledShadowCompositeComputes);", catchBlock);
        int destroyRoot = constructorBody.indexOf(
            "failure = destroyComputePrograms(failure, compiledShadowComputes);", destroyComposite);
        int assignRoot = constructorBody.indexOf("this.shadowComputes = compiledShadowComputes;", destroyRoot);
        int assignComposite = constructorBody.indexOf(
            "this.shadowCompositeComputes = compiledShadowCompositeComputes;", assignRoot);

        assertTrue("Shadow composite computes must compile after root shadow computes and before framebuffer setup",
            rootCompile >= 0 && compositeLocal > rootCompile && compositeCompile > tryBlock
                && createFramebuffer > compositeCompile);
        assertTrue("Constructor setup failures must destroy compiled shadowcomp compute groups before root computes",
            destroyComposite > catchBlock && destroyRoot > destroyComposite);
        assertTrue("ShadowRenderer must publish shadowcomp compute ownership only after setup succeeds",
            assignRoot > destroyRoot && assignComposite > assignRoot);

        assertTrue(compileComposite.contains("programs[i] = compileShadowCompositeComputeGroup(sourceGroups[i]);"));
        assertTrue(compileComposite.contains(
            "addSuppressedCleanupFailure(exception, destroyComputeProgramGroups(null, programs));"));
        assertTrue(compileGroup.contains(
            "throw new ProgramLoadException(\"Failed to compile shadow composite compute shader \""));
        assertTrue(compileGroup.contains(
            "ProgramBuilder.beginCompute(source.getName(), computeSource, null,"));
        assertTrue(compileGroup.contains(
            "applyCustomBindings(source.getName(), builder, this::currentShadowCompositeFlippedBuffers);"));
        assertTrue(compileGroup.contains(
            "program.setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups());"));

        int depthCopy = renderBody.indexOf("shadowMap.copyDepthToNoTranslucents();");
        int translucentBranch = renderBody.indexOf("if (shouldRenderTranslucent)", depthCopy);
        int dispatchShadowComp = renderBody.indexOf("dispatchShadowCompositeComputes();", translucentBranch);
        int mipmaps = renderBody.indexOf("shadowMap.generateMipmaps();", dispatchShadowComp);
        assertTrue("Shadow composite compute must run after shadow geometry and before shadow mipmap generation",
            depthCopy >= 0 && dispatchShadowComp > translucentBranch && mipmaps > dispatchShadowComp);

        assertTrue(dispatchComposite.contains(
            "boolean ranCompute = dispatchComputeGroup(computes);"));
        assertTrue(dispatchComposite.contains("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);"));
        assertTrue(dispatchComposite.contains("Program.unbind();"));
        assertTrue(dispatchGroup.contains("compute.dispatch(resolution, resolution);"));
        assertTrue(dispatchGroup.contains("ranCompute = true;"));

        assertTrue(destroyBody.contains("failure = destroyComputeProgramGroups(failure, shadowCompositeComputes);"));
        assertTrue(destroyHelpers.contains("private static Throwable destroyComputeProgramGroups"));
        assertTrue(destroyHelpers.contains("failure = destroyComputePrograms(failure, group);"));
    }

    @Test
    public void shadowFramebufferIsOwnedByShadowMapLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String shadowMap = read("src/main/java/net/oculus/pipeline/shadow/ShadowMap.java");
        String constructorBody = source.substring(
            source.indexOf("ComputeProgram[] compiledShadowComputes = compileShadowComputes(shadowComputeSources);"),
            source.indexOf("this.shadowComputes = compiledShadowComputes;"));

        int compiledComputes = constructorBody.indexOf(
            "ComputeProgram[] compiledShadowComputes = compileShadowComputes(shadowComputeSources);");
        int localFramebuffer = constructorBody.indexOf("GlFramebuffer createdShadowFramebuffer = null;",
            compiledComputes);
        int createFramebuffer = constructorBody.indexOf(
            "createdShadowFramebuffer = shadowMap.createShadowFramebuffer(shadowMap.snapshot(), shadowDrawBuffers());",
            localFramebuffer);
        int createRegular = constructorBody.indexOf(
            "createdShadowClearPasses = ClearPassCreator.createShadowClearPasses(shadowMap, false, shadowDirectives);",
            createFramebuffer);
        int createFull = constructorBody.indexOf(
            "createdShadowClearPassesFull = ClearPassCreator.createShadowClearPasses(shadowMap, true, shadowDirectives);",
            createRegular);
        int catchBlock = constructorBody.indexOf("catch (RuntimeException | Error exception)", createFull);
        int destroyRegular = constructorBody.indexOf(
            "failure = destroyClearPassFramebuffers(failure, createdShadowClearPasses);", catchBlock);
        int destroyFull = constructorBody.indexOf(
            "failure = destroyClearPassFramebuffers(failure, createdShadowClearPassesFull);", destroyRegular);
        int destroyFramebuffer = constructorBody.indexOf(
            "failure = destroyShadowFramebuffer(failure, createdShadowFramebuffer);", destroyFull);
        int destroyComputes = constructorBody.indexOf(
            "failure = destroyComputePrograms(failure, compiledShadowComputes);", destroyFramebuffer);
        int suppress = constructorBody.indexOf("addSuppressedCleanupFailure(exception, failure);", destroyComputes);
        int rethrow = constructorBody.indexOf("throw exception;", suppress);
        int assignFramebuffer = constructorBody.indexOf("this.shadowFramebuffer = createdShadowFramebuffer;",
            rethrow);

        assertTrue(source.contains("import com.github.zsoltmolnarr.oculus.client.render.gl.framebuffer.GlFramebuffer;"));
        assertTrue(source.contains("private final GlFramebuffer shadowFramebuffer;"));
        assertTrue("Shadow computes must compile before shadow framebuffer creation",
            compiledComputes >= 0 && localFramebuffer > compiledComputes && createFramebuffer > localFramebuffer);
        assertTrue("ShadowRenderer must ask ShadowMap to create the source-style root shadow framebuffer",
            createFramebuffer > localFramebuffer);
        assertTrue("Clear passes must be created after the root shadow framebuffer is owned by ShadowMap helpers",
            createRegular > createFramebuffer && createFull > createRegular);
        assertTrue("Constructor setup failures must destroy partial clear-pass framebuffers first",
            catchBlock > createFull && destroyRegular > catchBlock && destroyFull > destroyRegular);
        assertTrue("Constructor setup failures must destroy the created root shadow framebuffer and compiled computes",
            destroyFramebuffer > destroyFull && destroyComputes > destroyFramebuffer);
        assertTrue("Constructor cleanup failures must be suppressed onto the original setup failure",
            suppress > destroyComputes && rethrow > suppress);
        assertTrue("The ShadowRenderer field must only take ownership after setup succeeds",
            assignFramebuffer > rethrow);
        assertTrue(source.contains("private Throwable destroyShadowFramebuffer(Throwable failure, GlFramebuffer framebuffer)"));
        assertTrue(source.contains("shadowMap.destroyFramebuffer(framebuffer)"));
        assertTrue(shadowMap.contains("public GlFramebuffer createShadowFramebuffer(Set<Integer> stageWritesToAlt, int[] drawBuffers)"));
        assertTrue(shadowMap.contains("return createColorFramebufferWithDepth(invert(stageWritesToAlt, drawBuffers), drawBuffers);"));
        assertTrue(shadowMap.contains("private Set<Integer> invert(Set<Integer> base, int[] relevant)"));
        assertFalse("ShadowRenderer must not manually allocate the root shadow framebuffer",
            source.contains("OpenGlHelper.glGenFramebuffers"));
        assertFalse("ShadowRenderer must not manually attach shadow framebuffer textures",
            source.contains("OpenGlHelper.glFramebufferTexture2D"));
        assertFalse("ShadowRenderer must not bypass GlFramebuffer completeness checks",
            source.contains("OpenGlHelper.glCheckFramebufferStatus"));
        assertFalse("ShadowRenderer must not directly delete the root shadow framebuffer",
            source.contains("OpenGlHelper.glDeleteFramebuffers"));
        assertFalse(source.contains("private final int framebufferId;"));
        assertFalse(source.contains("LOGGER.warn(\"Shadow framebuffer incomplete"));
    }

    @Test
    public void noProgramShadowFallbackDrawsOnlyFirstColorBufferLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String constructorBody = source.substring(
            source.indexOf("this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities();"),
            source.indexOf("ComputeProgram[] compiledShadowComputes = compileShadowComputes"));
        String shadowDrawBuffers = source.substring(
            source.indexOf("private int[] shadowDrawBuffers()"),
            source.indexOf("private ICamera createShadowCamera"));

        int renderBlockEntities = constructorBody.indexOf(
            "this.shouldRenderBlockEntities = shadowDirectives.shouldRenderBlockEntities();");
        int hasProgram = constructorBody.indexOf(
            "this.hasShadowProgram = shadowSource != null && shadowSource.isValid();", renderBlockEntities);
        int voxelization = constructorBody.indexOf(
            "this.packHasVoxelization = shadowSource != null && shadowSource.getGeometrySource().isPresent();",
            hasProgram);

        int helper = shadowDrawBuffers.indexOf("private int[] shadowDrawBuffers()");
        int chooseBuffers = shadowDrawBuffers.indexOf(
            "return hasShadowProgram ? new int[] { 0, 1 } : new int[] { 0 };",
            helper);

        assertTrue(source.contains("private final boolean hasShadowProgram;"));
        assertTrue("ShadowRenderer must record whether the root shadow raster program exists",
            hasProgram > renderBlockEntities);
        assertTrue("Voxelization detection should remain independent from the no-program fallback draw-buffer choice",
            voxelization > hasProgram);
        assertTrue("No-program shadow fallback must use only color attachment 0, matching the 1.16.5 default shadow pass",
            chooseBuffers > helper);
        assertTrue("Shadow framebuffer creation must consume the same draw-buffer helper",
            source.contains("shadowMap.createShadowFramebuffer(shadowMap.snapshot(), shadowDrawBuffers());"));
    }

    @Test
    public void validRootShadowProgramsDoNotGetNoSourceFallbackBlendOff() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String worldPipeline = read("src/main/java/net/oculus/pipeline/ShaderWorldRenderingPipeline.java");
        String programId = read("src/main/java/net/oculus/shaderpack/loading/ProgramId.java");
        String renderBody = methodBody(source,
            "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        String bindProgram = methodBody(worldPipeline, "private void bindProgram");
        String fallbackHelper = methodBody(worldPipeline, "private void applyNoProgramShadowFallbackRenderState");

        int setupState = renderBody.indexOf("GlStateManager.disableCull();");
        int colorMask = renderBody.indexOf("GlStateManager.colorMask(true, true, true, true);", setupState);
        int translucentBlend = renderBody.indexOf("GlStateManager.enableBlend();", colorMask);
        String rootSetup = renderBody.substring(setupState, translucentBlend);
        int programNullBranch = bindProgram.indexOf("if (program == null)");
        int restoreOverrides = bindProgram.indexOf("restoreRenderStateOverrides();", programNullBranch);
        int fallbackCall = bindProgram.indexOf("applyNoProgramShadowFallbackRenderState(resolved);",
            restoreOverrides);
        int branchReturn = bindProgram.indexOf("return;", fallbackCall);

        assertTrue("The root shadow ProgramId should match 1.16.5 and not force blend off for valid shadow sources",
            programId.contains("Shadow(ProgramGroup.Shadow, \"\"),"));
        assertFalse(programId.contains("Shadow(ProgramGroup.Shadow, \"\", BlendModeOverride.OFF)"));
        assertTrue("Shadow setup should leave no-source fallback blend ownership to the world pipeline pass",
            setupState >= 0 && colorMask > setupState);
        assertFalse("Valid root shadow raster programs should keep their ProgramDirectives/default blend state",
            rootSetup.contains("GlStateManager.disableBlend();"));
        assertFalse("ShadowRenderer must not plain-disable blend for the no-source fallback; that would not lock later translucent blend toggles like 1.16.5 Pass.use()",
            source.contains("applyNoProgramShadowFallbackBlendState"));
        assertTrue("The no-source shadow fallback must apply the BlendModeOverride.OFF pass state after restoring the previous pass",
            programNullBranch >= 0 && restoreOverrides > programNullBranch && fallbackCall > restoreOverrides
                && branchReturn > fallbackCall);
        assertTrue("Only the no-source root shadow fallback should force BlendModeOverride.OFF",
            fallbackHelper.contains("isRenderingShadow && resolved != null"));
        assertTrue(fallbackHelper.contains("resolved.source == null && resolved.id == ProgramId.Shadow"));
        assertTrue(fallbackHelper.contains("BlendModeOverride.OFF.apply();"));
    }

    @Test
    public void shadowTargetPreparationUsesPersistentClearPassFramebuffersLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String constructorBody = source.substring(
            source.indexOf("ComputeProgram[] compiledShadowComputes = compileShadowComputes(shadowComputeSources);"),
            source.indexOf("copyMatrix(ShadowUniforms.getShadowRenderProjection()"));
        String clearShadowColorBuffers = source.substring(
            source.indexOf("private void clearShadowColorBuffers()"),
            source.indexOf("private int[] shadowDrawBuffers()"));
        String destroyBody = source.substring(
            source.indexOf("public void destroy()"),
            source.indexOf("private static void destroyComputePrograms"));

        assertTrue(source.contains("private final List<ClearPass> shadowClearPasses;"));
        assertTrue(source.contains("private final List<ClearPass> shadowClearPassesFull;"));
        assertTrue(source.contains("private static final Vector4f SHADOW_CLEAR_DEFAULT"));

        int createFramebuffer = constructorBody.indexOf(
            "createdShadowFramebuffer = shadowMap.createShadowFramebuffer(shadowMap.snapshot(), shadowDrawBuffers());");
        int createRegular = constructorBody.indexOf(
            "createdShadowClearPasses = ClearPassCreator.createShadowClearPasses(shadowMap, false, shadowDirectives);",
            createFramebuffer);
        int createFull = constructorBody.indexOf(
            "createdShadowClearPassesFull = ClearPassCreator.createShadowClearPasses(shadowMap, true, shadowDirectives);",
            createRegular);
        int assignFramebuffer = constructorBody.indexOf("this.shadowFramebuffer = createdShadowFramebuffer;",
            createFull);
        int assignRegular = constructorBody.indexOf("this.shadowClearPasses = createdShadowClearPasses;",
            assignFramebuffer);
        int assignFull = constructorBody.indexOf("this.shadowClearPassesFull = createdShadowClearPassesFull;",
            assignRegular);
        int assignComputes = constructorBody.indexOf("this.shadowComputes = compiledShadowComputes;", assignFull);

        assertTrue("ShadowRenderer must create the root framebuffer before persistent clear-pass framebuffers",
            createFramebuffer >= 0 && createRegular > createFramebuffer);
        assertTrue("ShadowRenderer must create persistent full shadow clear passes after regular passes",
            createFull > createRegular);
        assertTrue("ShadowRenderer must take ownership only after both clear-pass lists are created",
            assignFramebuffer > createFull && assignRegular > assignFramebuffer
                && assignFull > assignRegular && assignComputes > assignFull);

        int choosePasses = clearShadowColorBuffers.indexOf(
            "List<ClearPass> passes = shadowMap.consumeFullClearRequired() ? shadowClearPassesFull : shadowClearPasses;");
        int loopPasses = clearShadowColorBuffers.indexOf("for (ClearPass clearPass : passes)", choosePasses);
        int execute = clearShadowColorBuffers.indexOf("clearPass.execute(SHADOW_CLEAR_DEFAULT);", loopPasses);

        assertTrue("Shadow target preparation must consume the full-clear bit before choosing clear passes",
            choosePasses >= 0);
        assertTrue("Shadow target preparation must execute persistent clear passes instead of temporary texture clears",
            loopPasses > choosePasses && execute > loopPasses);
        assertFalse("ShadowRenderer must not mutate the last bound clear-pass framebuffer draw-buffer state",
            clearShadowColorBuffers.contains("restoreShadowDrawBuffers();"));
        assertFalse(clearShadowColorBuffers.contains("shadowMap.clearColorBuffersForRender();"));

        assertTrue(destroyBody.contains("destroyClearPassFramebuffers(failure, shadowClearPasses);"));
        assertTrue(destroyBody.contains("destroyClearPassFramebuffers(failure, shadowClearPassesFull);"));
    }

    @Test
    public void shadowConstructorCleansCompiledComputesIfFramebufferSetupFails() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String constructorBody = source.substring(
            source.indexOf("ComputeProgram[] compiledShadowComputes = compileShadowComputes(shadowComputeSources);"),
            source.indexOf("copyMatrix(ShadowUniforms.getShadowRenderProjection()"));
        String destroyBody = source.substring(
            source.indexOf("private static void destroyComputePrograms"),
            source.lastIndexOf("\n}"));

        int compileComputes = constructorBody.indexOf(
            "ComputeProgram[] compiledShadowComputes = compileShadowComputes(shadowComputeSources);");
        int framebufferLocal = constructorBody.indexOf("GlFramebuffer createdShadowFramebuffer = null;",
            compileComputes);
        int createFramebuffer = constructorBody.indexOf(
            "createdShadowFramebuffer = shadowMap.createShadowFramebuffer(shadowMap.snapshot(), shadowDrawBuffers());",
            framebufferLocal);
        int cleanup = constructorBody.indexOf("catch (RuntimeException | Error exception)", createFramebuffer);
        int destroyFramebuffer = constructorBody.indexOf(
            "failure = destroyShadowFramebuffer(failure, createdShadowFramebuffer);", cleanup);
        int destroyComputes = constructorBody.indexOf("failure = destroyComputePrograms(failure, compiledShadowComputes);",
            destroyFramebuffer);
        int suppressed = constructorBody.indexOf("addSuppressedCleanupFailure(exception, failure);", destroyComputes);
        int rethrow = constructorBody.indexOf("throw exception;", suppressed);
        int assignComputes = constructorBody.indexOf("this.shadowComputes = compiledShadowComputes;",
            rethrow);

        assertTrue("Shadow computes must compile before framebuffer allocation like the 1.16.5 pipeline",
            compileComputes >= 0 && framebufferLocal > compileComputes && createFramebuffer > framebufferLocal);
        assertTrue("Failed shadow framebuffer setup must destroy the created framebuffer wrapper through ShadowMap",
            destroyFramebuffer > cleanup);
        assertTrue("Failed shadow framebuffer setup must destroy already compiled shadow computes",
            destroyComputes > destroyFramebuffer);
        assertTrue("Failed shadow framebuffer setup must preserve the original failure when cleanup also fails",
            suppressed > destroyComputes && rethrow > suppressed);
        assertTrue("The ShadowRenderer field must only take ownership after framebuffer setup succeeds",
            assignComputes > rethrow);
        assertTrue(destroyBody.contains("if (computes == null)"));
        assertTrue(destroyBody.contains("for (ComputeProgram compute : computes)"));
        assertTrue(destroyBody.contains("compute.destroy();"));
    }

    @Test
    public void shadowConstructorCleansResourcesIfClearPassSetupFails() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String constructorBody = source.substring(
            source.indexOf("List<ClearPass> createdShadowClearPasses = Collections.emptyList();"),
            source.indexOf("copyMatrix(ShadowUniforms.getShadowRenderProjection()"));

        int createRegular = constructorBody.indexOf(
            "createdShadowClearPasses = ClearPassCreator.createShadowClearPasses(shadowMap, false, shadowDirectives);");
        int createFull = constructorBody.indexOf(
            "createdShadowClearPassesFull = ClearPassCreator.createShadowClearPasses(shadowMap, true, shadowDirectives);",
            createRegular);
        int catchBlock = constructorBody.indexOf("catch (RuntimeException | Error exception)", createFull);
        int failureLocal = constructorBody.indexOf("Throwable failure = null;", catchBlock);
        int destroyRegular = constructorBody.indexOf(
            "failure = destroyClearPassFramebuffers(failure, createdShadowClearPasses);", failureLocal);
        int destroyFull = constructorBody.indexOf(
            "failure = destroyClearPassFramebuffers(failure, createdShadowClearPassesFull);", destroyRegular);
        int destroyFramebuffer = constructorBody.indexOf(
            "failure = destroyShadowFramebuffer(failure, createdShadowFramebuffer);",
            destroyFull);
        int destroyComputes = constructorBody.indexOf(
            "failure = destroyComputePrograms(failure, compiledShadowComputes);", destroyFramebuffer);
        int suppress = constructorBody.indexOf("addSuppressedCleanupFailure(exception, failure);", destroyComputes);
        int rethrow = constructorBody.indexOf("throw exception;", suppress);

        assertTrue("Shadow clear-pass creation must be guarded after shadow framebuffer setup", createRegular >= 0);
        assertTrue("Full clear-pass creation must run after regular clear-pass creation", createFull > createRegular);
        assertTrue("Clear-pass setup failures must preserve the primary failure", catchBlock > createFull);
        assertTrue("Partial regular clear-pass framebuffers must be destroyed first",
            failureLocal > catchBlock && destroyRegular > failureLocal);
        assertTrue("Partial full clear-pass framebuffers must be destroyed after regular passes",
            destroyFull > destroyRegular);
        assertTrue("ShadowRenderer must clean the root framebuffer and compiled computes if clear-pass setup fails",
            destroyFramebuffer > destroyFull && destroyComputes > destroyFramebuffer);
        assertTrue("Clear-pass setup cleanup failures must be suppressed onto the original failure",
            suppress > destroyComputes && rethrow > suppress);
    }

    @Test
    public void shadowTargetPreparationRunsOutsideShadowTerrainRendering() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");

        int prepare = source.indexOf("public void prepareRenderTargets()");
        int render = source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        int compile = source.indexOf("private ComputeProgram[] compileShadowComputes", render);

        assertTrue(prepare >= 0);
        assertTrue(render > prepare);
        assertTrue(compile > render);

        String prepareBody = source.substring(prepare, render);
        assertTrue(prepareBody.contains("GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);"));
        assertTrue(prepareBody.contains("dispatchShadowComputes()"));
        assertTrue(source.contains("private static final int COMPUTE_BARRIER"));
        assertFalse(prepareBody.contains("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);"));
        assertTrue(prepareBody.contains("clearShadowColorBuffers();"));

        String renderBody = source.substring(render, compile);
        assertFalse(renderBody.contains("dispatchShadowComputes()"));
        assertFalse(renderBody.contains("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);"));
        assertFalse(renderBody.contains("clearShadowColorBuffers();"));
        assertFalse(renderBody.contains("GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);"));
    }

    @Test
    public void shadowTargetPreparationRestoresDepthMaskAfterDepthClear() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String prepareBody = source.substring(
            source.indexOf("public void prepareRenderTargets()"),
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"));

        int depthMaskLocal = prepareBody.indexOf("boolean previousDepthMask = false;");
        int depthMaskCapturedFlag = prepareBody.indexOf("boolean depthMaskCaptured = false;", depthMaskLocal);
        int saveDepthMask = prepareBody.indexOf("previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);",
            depthMaskCapturedFlag);
        int markDepthMaskCaptured = prepareBody.indexOf("depthMaskCaptured = true;", saveDepthMask);
        int forceDepthMask = prepareBody.indexOf("GlStateManager.depthMask(true);", markDepthMaskCaptured);
        int clearDepth = prepareBody.indexOf("GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);", forceDepthMask);
        int finallyBlock = prepareBody.indexOf("finally {", clearDepth);
        int cleanupFailure = prepareBody.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int restoreDepthMaskGuard = prepareBody.indexOf("if (depthMaskCaptured)", cleanupFailure);
        int restoreDepthMask = prepareBody.indexOf("GlStateManager.depthMask(capturedPreviousDepthMask)",
            restoreDepthMaskGuard);
        int suppress = prepareBody.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", restoreDepthMask);
        int rethrowCleanupOnly = prepareBody.indexOf("rethrowCleanupFailure(cleanupFailure);", suppress);

        assertTrue("Shadow target preparation must initialize depth-mask capture state before guarded setup",
            depthMaskLocal >= 0 && depthMaskCapturedFlag > depthMaskLocal);
        assertTrue("Shadow target preparation must save the incoming depth write mask", saveDepthMask > depthMaskCapturedFlag);
        assertTrue("Shadow target preparation must mark depth-mask capture only after reading it",
            markDepthMaskCaptured > saveDepthMask);
        assertTrue("Shadow target preparation must force depth writes only after saving the old mask",
            forceDepthMask > markDepthMaskCaptured);
        assertTrue("Shadow target preparation must clear depth with writes enabled", clearDepth > forceDepthMask);
        assertTrue("Shadow target preparation must restore the previous depth mask in the cleanup path",
            cleanupFailure > finallyBlock && restoreDepthMaskGuard > cleanupFailure
                && restoreDepthMask > restoreDepthMaskGuard);
        assertTrue("Shadow target preparation cleanup failures must suppress onto primary failures or rethrow alone",
            suppress > restoreDepthMask && rethrowCleanupOnly > suppress);
    }

    @Test
    public void shadowTargetPreparationRestoresTextureAndProgramStateAfterComputeDispatch() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String prepareBody = source.substring(
            source.indexOf("public void prepareRenderTargets()"),
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"));

        int activeTextureLocal = prepareBody.indexOf("int previousActiveTexture = 0;");
        int textureLocal = prepareBody.indexOf("int previousTexture = 0;", activeTextureLocal);
        int textureStateCapturedFlag = prepareBody.indexOf("boolean textureStateCaptured = false;", textureLocal);
        int saveActiveTexture = prepareBody.indexOf("previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);",
            textureStateCapturedFlag);
        int defaultTextureUnit = prepareBody.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();",
            saveActiveTexture);
        int saveTexture = prepareBody.indexOf("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            defaultTextureUnit);
        int markTextureCaptured = prepareBody.indexOf("textureStateCaptured = true;", saveTexture);
        int dispatchComputes = prepareBody.indexOf("dispatchShadowComputes();", saveTexture);
        int clearColors = prepareBody.indexOf("clearShadowColorBuffers();", dispatchComputes);
        int finallyBlock = prepareBody.indexOf("finally {", clearColors);
        int cleanupFailure = prepareBody.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int unbindProgram = prepareBody.indexOf("Program.unbind()", cleanupFailure);
        int textureRestoreGuard = prepareBody.indexOf("if (textureStateCaptured)", unbindProgram);
        int restoreTexture = prepareBody.indexOf(
            "restoreDefaultTextureBinding(capturedPreviousTexture, capturedPreviousActiveTexture)", textureRestoreGuard);
        int restoreFramebuffers = prepareBody.indexOf(
            "OculusRenderSystem.restoreFramebufferBindings(capturedPreviousFramebuffer,",
            restoreTexture);

        assertTrue("Shadow target preparation must initialize texture capture locals before guarded setup",
            activeTextureLocal >= 0 && textureLocal > activeTextureLocal
                && textureStateCapturedFlag > textureLocal);
        assertTrue("Shadow target preparation must save the caller's active texture unit",
            saveActiveTexture > textureStateCapturedFlag);
        assertTrue("Shadow target preparation must switch to the default unit before saving its texture binding",
            defaultTextureUnit > saveActiveTexture && saveTexture > defaultTextureUnit);
        assertTrue("Shadow target preparation must mark texture capture only after saving the default unit binding",
            markTextureCaptured > saveTexture);
        assertTrue("Shadow compute dispatch must run before preparation cleanup",
            dispatchComputes > saveTexture && clearColors > dispatchComputes);
        assertTrue("Shadow target preparation must unbind the compute program/samplers in the cleanup path",
            cleanupFailure > finallyBlock && unbindProgram > cleanupFailure);
        assertTrue("Shadow target preparation must restore the default texture binding and caller active unit",
            textureRestoreGuard > unbindProgram && restoreTexture > textureRestoreGuard);
        assertTrue("Texture state should be restored before leaving the shadow framebuffer binding cleanup",
            restoreFramebuffers > restoreTexture);
        assertTrue(source.contains("import net.oculus.gl.program.Program;"));
    }

    @Test
    public void rootShadowComputePreparationDoesNotAddPostDispatchBarrierBeforeClears() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String prepareBody = source.substring(
            source.indexOf("public void prepareRenderTargets()"),
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"));
        String dispatchShadowComputes = methodBody(source, "private void dispatchShadowComputes()");

        int dispatchComputes = prepareBody.indexOf("dispatchShadowComputes();");
        int clearColors = prepareBody.indexOf("clearShadowColorBuffers();", dispatchComputes);
        String dispatchToClear = prepareBody.substring(dispatchComputes, clearColors);
        int cleanup = prepareBody.indexOf("Throwable cleanupFailure = null;", clearColors);
        int cleanupUnbind = prepareBody.indexOf("Program.unbind()", cleanup);

        assertTrue("Root shadow computes must dispatch before shadow color clears like DeferredWorldRenderingPipeline",
            dispatchComputes >= 0 && clearColors > dispatchComputes);
        assertFalse("1.16.5 does not issue a renderer-level post-dispatch barrier before root shadow color clears",
            dispatchToClear.contains("OculusRenderSystem.memoryBarrier(COMPUTE_BARRIER);"));
        assertFalse("Root shadow preparation should leave program cleanup to the outer cleanup path",
            dispatchToClear.contains("Program.unbind();"));
        assertTrue("Root shadow compute dispatch should stay a simple preparation step, unlike shadowcomp passes",
            dispatchShadowComputes.contains("compute.dispatch(resolution, resolution);"));
        assertFalse("Root shadow compute dispatch must not copy the shadowcomp post-dispatch barrier path",
            dispatchShadowComputes.contains("OculusRenderSystem.memoryBarrier"));
        assertFalse("Root shadow compute dispatch must not copy the shadowcomp immediate unbind path",
            dispatchShadowComputes.contains("Program.unbind()"));
        assertTrue("Shadow target preparation still unbinds active compute state during final cleanup",
            cleanupUnbind > cleanup);
    }

    @Test
    public void destroyedShadowRendererFailsClearlyOnRuntimeEntryPoints() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String prepareBody = source.substring(
            source.indexOf("public void prepareRenderTargets()"),
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"));
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private static Throwable restoreAfterShadowRender"));
        String shouldRenderBody = methodBody(source, "public boolean shouldRenderThisFrame");
        String shouldRenderHelper = methodBody(source, "private boolean shouldRenderThisFrame");
        String failHelper = methodBody(source, "private void failIfDestroyed");

        int prepareFail = prepareBody.indexOf("failIfDestroyed(\"prepare shadow render targets\");");
        int prepareDisabledGuard = prepareBody.indexOf("if (!shadowMap.isEnabled() || resolution <= 0)", prepareFail);
        int renderFail = renderBody.indexOf("failIfDestroyed(\"render shadows\");");
        int renderMinecraft = renderBody.indexOf("Minecraft mc = Minecraft.getMinecraft();", renderFail);
        int renderPreflight = renderBody.indexOf("if (!shouldRenderThisFrame(mc))", renderMinecraft);
        int shouldRenderFail = shouldRenderBody.indexOf("failIfDestroyed(\"check shadow render availability\");");
        int shouldRenderCall = shouldRenderBody.indexOf(
            "return shouldRenderThisFrame(Minecraft.getMinecraft());", shouldRenderFail);
        int preflightDisabledGuard = shouldRenderHelper.indexOf("if (!shadowMap.isEnabled() || resolution <= 0)");

        assertTrue("Shadow target preparation must fail clearly after destroy before disabled-shadow early exits",
            prepareFail >= 0 && prepareDisabledGuard > prepareFail);
        assertTrue("Shadow rendering must fail clearly after destroy before frame preflight early exits",
            renderFail >= 0 && renderMinecraft > renderFail && renderPreflight > renderMinecraft);
        assertTrue("External shadow preflight must also fail clearly after destroy before disabled-shadow early exits",
            shouldRenderFail >= 0 && shouldRenderCall > shouldRenderFail && preflightDisabledGuard >= 0);
        assertTrue(failHelper.contains("if (destroyed)"));
        assertTrue(failHelper.contains(
            "throw new IllegalStateException(\"Cannot \" + operation + \" after the shadow renderer was destroyed\");"));
        assertFalse(prepareBody.contains("destroyed || !shadowMap.isEnabled()"));
        assertFalse(renderBody.contains("destroyed || !shadowMap.isEnabled()"));
    }

    @Test
    public void shadowRenderingStateBeginsInsideFailureGuardedRenderBlock() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = methodBody(source,
            "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        String restoreBindings = methodBody(source, "private static void restoreShadowBindings");

        int failureLocal = renderBody.indexOf("Throwable failure = null;");
        int tryBlock = renderBody.indexOf("try {", failureLocal);
        int beginShadowPass = renderBody.indexOf("ShadowRenderingState.beginShadowPass(shadowProjection);", tryBlock);
        int bindFramebuffer = renderBody.indexOf("shadowFramebuffer.bind();", beginShadowPass);
        int finallyBlock = renderBody.indexOf("finally {", bindFramebuffer);
        int cleanup = renderBody.indexOf("Throwable cleanupFailure = restoreAfterShadowRender(", finallyBlock);

        assertTrue("Shadow rendering state must be entered inside the guarded render block",
            failureLocal >= 0 && tryBlock > failureLocal && beginShadowPass > tryBlock);
        assertTrue("The shadow state marker must be active before framebuffer or viewport mutations",
            bindFramebuffer > beginShadowPass);
        assertTrue("The guarded cleanup path must cover failures after shadow state is entered",
            finallyBlock > bindFramebuffer && cleanup > finallyBlock);
        assertTrue("Shadow render cleanup must leave the global shadow-rendering state",
            restoreBindings.contains("ShadowRenderingState.endShadowPass();"));
        assertFalse("Shadow rendering state must not be entered before the guarded try block",
            renderBody.substring(failureLocal, tryBlock).contains("ShadowRenderingState.beginShadowPass"));
    }

    @Test
    public void shadowTargetPreparationRestoresActiveTextureIfDefaultUnitSwitchFailsBeforeBindingCapture() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String prepareBody = source.substring(
            source.indexOf("public void prepareRenderTargets()"),
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"));

        int activeTextureLocal = prepareBody.indexOf("int previousActiveTexture = 0;");
        int activeTextureCapturedFlag = prepareBody.indexOf("boolean activeTextureCaptured = false;",
            activeTextureLocal);
        int textureStateCapturedFlag = prepareBody.indexOf("boolean textureStateCaptured = false;",
            activeTextureCapturedFlag);
        int saveActiveTexture = prepareBody.indexOf(
            "previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);", textureStateCapturedFlag);
        int markActiveTextureCaptured = prepareBody.indexOf("activeTextureCaptured = true;", saveActiveTexture);
        int defaultTextureUnit = prepareBody.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();",
            markActiveTextureCaptured);
        int saveTexture = prepareBody.indexOf("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            defaultTextureUnit);
        int markTextureCaptured = prepareBody.indexOf("textureStateCaptured = true;", saveTexture);
        int finallyBlock = prepareBody.indexOf("finally {", markTextureCaptured);
        int textureStateGuard = prepareBody.indexOf("if (textureStateCaptured)", finallyBlock);
        int restoreBindingAndUnit = prepareBody.indexOf(
            "restoreDefaultTextureBinding(capturedPreviousTexture, capturedPreviousActiveTexture)",
            textureStateGuard);
        int activeOnlyBranch = prepareBody.indexOf("} else if (activeTextureCaptured)", restoreBindingAndUnit);
        int activeOnlyCapture = prepareBody.indexOf("final int capturedPreviousActiveTexture = previousActiveTexture;",
            activeOnlyBranch);
        int activeOnlyRestore = prepareBody.indexOf(
            "OculusRenderSystem.setActiveTextureUnit(capturedPreviousActiveTexture)", activeOnlyCapture);
        int framebufferRestore = prepareBody.indexOf("if (framebufferBindingsCaptured)", activeOnlyRestore);

        assertTrue("Shadow target preparation must track active texture capture separately from texture binding capture",
            activeTextureLocal >= 0 && activeTextureCapturedFlag > activeTextureLocal
                && textureStateCapturedFlag > activeTextureCapturedFlag);
        assertTrue("Active texture capture must be marked before switching to the default unit",
            saveActiveTexture > textureStateCapturedFlag
                && markActiveTextureCaptured > saveActiveTexture
                && defaultTextureUnit > markActiveTextureCaptured);
        assertTrue("Texture binding capture must remain after the default-unit switch succeeds",
            saveTexture > defaultTextureUnit && markTextureCaptured > saveTexture);
        assertTrue("Normal cleanup must still restore default texture binding and caller active unit together",
            textureStateGuard > finallyBlock && restoreBindingAndUnit > textureStateGuard);
        assertTrue("Partial default-unit setup failure must restore the caller active unit before framebuffer cleanup",
            activeOnlyBranch > restoreBindingAndUnit && activeOnlyCapture > activeOnlyBranch
                && activeOnlyRestore > activeOnlyCapture && framebufferRestore > activeOnlyRestore);
    }

    @Test
    public void shadowTargetPreparationRestoresFramebufferAndDepthMaskEvenIfCleanupThrows() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String prepareBody = source.substring(
            source.indexOf("public void prepareRenderTargets()"),
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"));

        int clearColors = prepareBody.indexOf("clearShadowColorBuffers();");
        int outerFinally = prepareBody.indexOf("finally {", clearColors);
        int cleanupFailure = prepareBody.indexOf("Throwable cleanupFailure = null;", outerFinally);
        int unbindProgram = prepareBody.indexOf("Program.unbind()", cleanupFailure);
        int restoreTextureGuard = prepareBody.indexOf("if (textureStateCaptured)", unbindProgram);
        int restoreTextureCleanup = prepareBody.indexOf("cleanupFailure = runCleanup(cleanupFailure,",
            restoreTextureGuard);
        int restoreTexture = prepareBody.indexOf(
            "restoreDefaultTextureBinding(capturedPreviousTexture, capturedPreviousActiveTexture)",
            restoreTextureCleanup);
        int restoreFramebufferGuard = prepareBody.indexOf("if (framebufferBindingsCaptured)", restoreTexture);
        int restoreFramebufferCleanup = prepareBody.indexOf("cleanupFailure = runCleanup(cleanupFailure,",
            restoreFramebufferGuard);
        int restoreFramebuffers = prepareBody.indexOf(
            "OculusRenderSystem.restoreFramebufferBindings(capturedPreviousFramebuffer,",
            restoreFramebufferCleanup);
        int restoreViewportGuard = prepareBody.indexOf("if (viewportCaptured)", restoreFramebuffers);
        int restoreViewport = prepareBody.indexOf("GL11.glViewport(", restoreViewportGuard);
        int restoreDepthMaskGuard = prepareBody.indexOf("if (depthMaskCaptured)", restoreViewport);
        int restoreDepthMaskCleanup = prepareBody.indexOf("cleanupFailure = runCleanup(cleanupFailure,",
            restoreDepthMaskGuard);
        int restoreDepthMask = prepareBody.indexOf("GlStateManager.depthMask(capturedPreviousDepthMask)",
            restoreDepthMaskCleanup);
        int suppress = prepareBody.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", restoreDepthMask);
        int rethrowCleanupOnly = prepareBody.indexOf("rethrowCleanupFailure(cleanupFailure);", suppress);

        assertTrue("Shadow target preparation cleanup must start after color clear work", outerFinally > clearColors);
        assertTrue("Program cleanup must be attempted first",
            cleanupFailure > outerFinally && unbindProgram > cleanupFailure);
        assertTrue("Texture restore must be recorded after program cleanup",
            restoreTextureGuard > unbindProgram && restoreTextureCleanup > restoreTextureGuard
                && restoreTexture > restoreTextureCleanup);
        assertTrue("Framebuffer and viewport restore must run after texture restoration is recorded",
            restoreFramebufferGuard > restoreTexture && restoreFramebufferCleanup > restoreFramebufferGuard
                && restoreFramebuffers > restoreFramebufferCleanup && restoreViewportGuard > restoreFramebuffers
                && restoreViewport > restoreViewportGuard);
        assertTrue("Depth-mask restore must run after framebuffer or viewport restoration is recorded",
            restoreDepthMaskGuard > restoreViewport && restoreDepthMaskCleanup > restoreDepthMaskGuard
                && restoreDepthMask > restoreDepthMaskCleanup);
        assertTrue("Preparation cleanup must preserve primary failures while reporting cleanup-only failures",
            suppress > restoreDepthMask && rethrowCleanupOnly > suppress);
    }

    @Test
    public void shadowRenderPathsRestoreReadDrawFramebufferBindings() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String prepareBody = source.substring(
            source.indexOf("public void prepareRenderTargets()"),
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"));
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private ComputeProgram[] compileShadowComputes"));

        assertFramebufferBindingsRestored(prepareBody, "Shadow target preparation",
            "shadowMap.getDepthSourceFramebuffer().bind();");
        assertFramebufferBindingsRestored(renderBody, "Shadow render", "shadowFramebuffer.bind();");
        assertFalse(source.contains("GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)"));
    }

    @Test
    public void shadowRenderRestoresDefaultTextureBindingAndCallerActiveUnit() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private static void restoreDefaultTextureBinding"));
        String restoreHelper = source.substring(
            source.indexOf("private static void restoreDefaultTextureBinding"),
            source.indexOf("private static void restoreLightingState"));
        int captureHelperStart = source.indexOf("private static int captureDefaultTextureBinding");
        String captureHelper = source.substring(
            captureHelperStart,
            source.indexOf("private static void restoreLight", captureHelperStart));

        int previousDepthFunc = renderBody.indexOf("int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);");
        int saveActiveTexture = renderBody.indexOf("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);",
            previousDepthFunc);
        int saveTexture = renderBody.indexOf("int previousTexture = captureDefaultTextureBinding(previousActiveTexture);",
            saveActiveTexture);
        int bindAtlasDefaultUnit = renderBody.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();",
            saveTexture);
        int bindAtlas = renderBody.indexOf("mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);",
            bindAtlasDefaultUnit);
        int copyDepth = renderBody.indexOf("shadowMap.copyDepthToNoTranslucents();", bindAtlas);
        int rebindAtlasDefaultUnit = renderBody.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();",
            copyDepth);
        int rebindAtlas = renderBody.indexOf("mc.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);",
            rebindAtlasDefaultUnit);
        int translucentBranch = renderBody.indexOf("if (shouldRenderTranslucent)", copyDepth);
        int translucentAlpha = renderBody.indexOf("GlStateManager.enableAlpha();", translucentBranch);
        int restoreLightingState = renderBody.indexOf(
            "restoreLightingState(lightingWasEnabled, light0WasEnabled, light1WasEnabled,", translucentAlpha);
        int restoreTexture = renderBody.indexOf(
            "restoreDefaultTextureBinding(previousTexture, previousActiveTexture);", restoreLightingState);
        int restoreFramebuffers = renderBody.indexOf(
            "OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,",
            restoreTexture);

        assertTrue("Shadow render must save texture state after the rest of the draw state",
            previousDepthFunc >= 0 && saveActiveTexture > previousDepthFunc);
        assertTrue("Shadow render must capture the default-unit texture binding through guarded cleanup",
            saveTexture > saveActiveTexture);
        assertTrue("Shadow terrain rendering must bind the block atlas on the default texture unit",
            bindAtlasDefaultUnit > saveTexture && bindAtlas > bindAtlasDefaultUnit);
        assertTrue("Shadow translucent terrain must rebind the block atlas after entity/depth-copy work",
            copyDepth > bindAtlas && rebindAtlasDefaultUnit > copyDepth && rebindAtlas > rebindAtlasDefaultUnit
                && translucentAlpha > rebindAtlas);
        assertTrue("Shadow render cleanup must restore default-unit texture state after RenderHelper mutations",
            restoreTexture > restoreLightingState);
        assertTrue("Shadow render cleanup must restore texture state before framebuffer/viewport state",
            restoreFramebuffers > restoreTexture);

        int captureTry = captureHelper.indexOf("try {");
        int captureDefaultUnit = captureHelper.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();", captureTry);
        int captureTexture = captureHelper.indexOf("return GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            captureDefaultUnit);
        int captureCatch = captureHelper.indexOf("catch (RuntimeException | Error exception)", captureTexture);
        int captureRecord = captureHelper.indexOf("failure = exception;", captureCatch);
        int captureFinally = captureHelper.indexOf("finally {", captureRecord);
        int captureRestoreActive = captureHelper.indexOf(
            "OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)", captureFinally);
        int captureSuppress = captureHelper.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);",
            captureRestoreActive);
        int captureRethrowCleanup = captureHelper.indexOf("rethrowCleanupFailure(cleanupFailure);",
            captureSuppress);
        assertTrue("Default texture capture must switch to unit 0 only inside a guarded helper",
            captureTry >= 0 && captureDefaultUnit > captureTry && captureTexture > captureDefaultUnit);
        assertTrue("Default texture capture must preserve primary capture failures",
            captureCatch > captureTexture && captureRecord > captureCatch);
        assertTrue("Default texture capture must restore caller active unit from the finally path",
            captureFinally > captureRecord && captureRestoreActive > captureFinally);
        assertTrue("Default texture capture cleanup failures must suppress onto primary failures or rethrow alone",
            captureSuppress > captureRestoreActive && captureRethrowCleanup > captureSuppress);

        int helperTry = restoreHelper.indexOf("try {");
        int helperDefaultUnit = restoreHelper.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();", helperTry);
        int helperBindTexture = restoreHelper.indexOf("GlStateManager.bindTexture(previousTexture);", helperDefaultUnit);
        int helperBindTextureCatch = restoreHelper.indexOf("failure = addCleanupFailure(failure, exception);",
            helperBindTexture);
        int helperRestoreActive = restoreHelper.indexOf("OculusRenderSystem.setActiveTextureUnit(previousActiveTexture);",
            helperBindTextureCatch);
        int helperRethrow = restoreHelper.indexOf("rethrowCleanupFailure(failure);", helperRestoreActive);
        assertTrue("Shadow texture restore helper must attempt default-unit texture restore first",
            helperTry >= 0 && helperDefaultUnit > helperTry && helperBindTexture > helperDefaultUnit);
        assertTrue("Shadow texture restore helper must restore caller active unit even if texture restore fails",
            helperBindTextureCatch > helperBindTexture && helperRestoreActive > helperBindTextureCatch);
        assertTrue("Shadow texture restore helper must rethrow aggregated cleanup failures after all attempts",
            helperRethrow > helperRestoreActive);
    }

    @Test
    public void validationDepthReadbackRestoresTextureBindingThroughCleanupHelper() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String validation = source.substring(
            source.indexOf("private void validateShadowDepthOutput()"),
            source.indexOf("private static int drainGlErrors()"));

        assertTrue(validation.contains("int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);"));
        assertTrue(validation.contains("int previousTexture = 0;"));
        assertTrue(validation.contains("boolean textureCaptured = false;"));
        assertTrue(validation.contains("OculusRenderSystem.restoreDefaultActiveTexture();"));
        assertTrue(validation.contains("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);"));
        assertTrue(validation.contains("textureCaptured = true;"));
        assertTrue(validation.contains("GlStateManager.bindTexture(shadowMap.getDepthTexture());"));
        assertTrue(validation.contains("GL11.glGetTexImage(GL11.GL_TEXTURE_2D"));
        int failureLocal = validation.indexOf("Throwable failure = null;");
        int guardedTry = validation.indexOf("try {", failureLocal);
        int restoreDefault = validation.indexOf("OculusRenderSystem.restoreDefaultActiveTexture();", guardedTry);
        int saveTexture = validation.indexOf("previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);",
            restoreDefault);
        int markTextureCaptured = validation.indexOf("textureCaptured = true;", saveTexture);
        int readback = validation.indexOf("GL11.glGetTexImage(GL11.GL_TEXTURE_2D", markTextureCaptured);
        int catchPrimary = validation.indexOf("catch (RuntimeException | Error exception)", readback);
        int recordPrimary = validation.indexOf("failure = exception;", catchPrimary);
        int finallyBlock = validation.indexOf("finally {", recordPrimary);
        int cleanupLocal = validation.indexOf("Throwable cleanupFailure = null;", finallyBlock);
        int textureRestoreGuard = validation.indexOf("if (textureCaptured)", cleanupLocal);
        int capturedTexture = validation.indexOf("final int capturedPreviousTexture = previousTexture;",
            textureRestoreGuard);
        int capturedActiveTexture = validation.indexOf(
            "final int capturedPreviousActiveTexture = previousActiveTexture;", capturedTexture);
        int restoreTexture = validation.indexOf(
            "restoreDefaultTextureBinding(capturedPreviousTexture, capturedPreviousActiveTexture)",
            capturedActiveTexture);
        int activeFallback = validation.indexOf("else {", restoreTexture);
        int restoreActiveTexture = validation.indexOf(
            "OculusRenderSystem.setActiveTextureUnit(previousActiveTexture)", activeFallback);
        int suppressCleanup = validation.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);",
            restoreActiveTexture);
        int rethrowCleanupOnly = validation.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressCleanup);

        assertTrue("Shadow depth validation must guard default-unit switching and binding capture",
            failureLocal >= 0 && guardedTry > failureLocal && restoreDefault > guardedTry
                && saveTexture > restoreDefault && markTextureCaptured > saveTexture);
        assertTrue("Shadow depth validation must preserve the original readback failure before cleanup",
            readback > markTextureCaptured && catchPrimary > readback && recordPrimary > catchPrimary);
        assertTrue("Shadow depth validation must restore previous texture binding from cleanup after capture",
            finallyBlock > recordPrimary && cleanupLocal > finallyBlock
                && textureRestoreGuard > cleanupLocal && capturedTexture > textureRestoreGuard
                && capturedActiveTexture > capturedTexture && restoreTexture > capturedActiveTexture);
        assertTrue("Shadow depth validation must use the active-unit fallback only when texture capture did not complete",
            activeFallback > restoreTexture && restoreActiveTexture > activeFallback);
        assertTrue("Shadow depth validation cleanup failures must suppress onto primary failures or rethrow alone",
            suppressCleanup > restoreActiveTexture && rethrowCleanupOnly > suppressCleanup);
        assertFalse(validation.contains("GL11.glBindTexture(GL11.GL_TEXTURE_2D"));
        assertFalse(validation.contains("() -> GlStateManager.bindTexture(capturedPreviousTexture)"));
    }

    @Test
    public void shadowMatrixStackCleanupReturnsToModelViewModeLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String cleanup = source.substring(
            source.indexOf("if (modelViewPushed)"),
            source.indexOf("if (cullWasEnabled)"));

        int popModelView = cleanup.indexOf("GlStateManager.popMatrix();");
        int projectionMode = cleanup.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);", popModelView);
        int popProjection = cleanup.indexOf("GlStateManager.popMatrix();", projectionMode);
        int restoreModelView = cleanup.indexOf("GlStateManager.matrixMode(GL11.GL_MODELVIEW);", popProjection);

        assertTrue("Shadow cleanup must pop the model-view matrix before restoring projection",
            popModelView >= 0);
        assertTrue("Shadow cleanup must switch to projection before popping the shadow projection matrix",
            projectionMode > popModelView);
        assertTrue("Shadow cleanup must pop the shadow projection matrix",
            popProjection > projectionMode);
        assertTrue("Shadow cleanup must return the active matrix mode to model-view like IrisRenderSystem.restoreProjectionMatrix",
            restoreModelView > popProjection);
    }

    @Test
    public void shadowMatrixStackCleanupRestoresModelViewWhenProjectionSelectionFailsBeforePush() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String render = methodBody(source,
            "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        String restoreAfter = methodBody(source, "private static Throwable restoreAfterShadowRender");
        String terrainCleanup = methodBody(source, "private static Throwable restoreShadowTerrainState");

        int projectionModeFlag = render.indexOf("boolean projectionModeSelected = false;");
        int projectionPushedFlag = render.indexOf("boolean projectionPushed = false;", projectionModeFlag);
        int modelViewFlag = render.indexOf("boolean modelViewPushed = false;", projectionPushedFlag);
        int tryBlock = render.indexOf("try {", modelViewFlag);
        int projectionMode = render.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);", tryBlock);
        int markProjectionMode = render.indexOf("projectionModeSelected = true;", projectionMode);
        int projectionPush = render.indexOf("GlStateManager.pushMatrix();", markProjectionMode);
        int markProjectionPushed = render.indexOf("projectionPushed = true;", projectionPush);
        int cleanupCall = render.indexOf("Throwable cleanupFailure = restoreAfterShadowRender(", markProjectionPushed);
        int cleanupArg = render.indexOf("projectionModeSelected,", cleanupCall);

        assertTrue("Shadow render must track projection matrix-mode selection separately from stack push",
            projectionModeFlag >= 0 && projectionPushedFlag > projectionModeFlag && modelViewFlag > projectionPushedFlag);
        assertTrue("Projection mode must be marked selected immediately after the matrix-mode call succeeds",
            tryBlock > modelViewFlag && projectionMode > tryBlock && markProjectionMode > projectionMode);
        assertTrue("Projection stack push must remain tracked separately from projection-mode selection",
            projectionPush > markProjectionMode && markProjectionPushed > projectionPush);
        assertTrue("Shadow cleanup must receive the projection-mode flag from renderShadows",
            cleanupCall > markProjectionPushed && cleanupArg > cleanupCall);

        int restoreParameter = source.indexOf("boolean projectionModeSelected,\n            boolean projectionPushed,");
        int terrainCleanupCall = restoreAfter.indexOf(
            "restoreShadowTerrainState(failure, renderGlobal, modelViewPushed, projectionModeSelected,");
        int terrainParameter = source.indexOf("boolean projectionModeSelected,\n            boolean projectionPushed) {");
        int projectionPushedBranch = terrainCleanup.indexOf("if (projectionPushed)");
        int restoreAfterProjectionPop = terrainCleanup.indexOf(
            "GlStateManager.matrixMode(GL11.GL_MODELVIEW);", projectionPushedBranch);
        int projectionModeOnlyBranch = terrainCleanup.indexOf("} else if (projectionModeSelected)",
            restoreAfterProjectionPop);
        int restoreWithoutProjectionPush = terrainCleanup.indexOf(
            "GlStateManager.matrixMode(GL11.GL_MODELVIEW);", projectionModeOnlyBranch);

        assertTrue("restoreAfterShadowRender must accept the projection-mode flag",
            restoreParameter >= 0);
        assertTrue("restoreAfterShadowRender must pass the projection-mode flag to matrix cleanup",
            terrainCleanupCall >= 0);
        assertTrue("Shadow terrain cleanup must accept the projection-mode flag",
            terrainParameter >= 0);
        assertTrue("Normal projection cleanup must still return to model-view after popping projection",
            restoreAfterProjectionPop > projectionPushedBranch);
        assertTrue("Shadow cleanup must restore model-view even if projection push never completed",
            projectionModeOnlyBranch > restoreAfterProjectionPop
                && restoreWithoutProjectionPush > projectionModeOnlyBranch);
    }

    @Test
    public void shadowRenderRestoresLegacyLightingStateAfterRenderHelperMutations() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private static void restoreLightingState"));
        String restoreLighting = source.substring(
            source.indexOf("private static void restoreLightingState"),
            source.indexOf("private ComputeProgram[] compileShadowComputes"));

        int saveLighting = renderBody.indexOf("boolean lightingWasEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);");
        int saveLight0 = renderBody.indexOf("boolean light0WasEnabled = GL11.glIsEnabled(GL11.GL_LIGHT0);",
            saveLighting);
        int saveLight1 = renderBody.indexOf("boolean light1WasEnabled = GL11.glIsEnabled(GL11.GL_LIGHT1);",
            saveLight0);
        int saveColorMaterial = renderBody.indexOf(
            "boolean colorMaterialWasEnabled = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL);", saveLight1);
        int disableLighting = renderBody.indexOf("RenderHelper.disableStandardItemLighting();", saveColorMaterial);
        int finallyBlock = renderBody.indexOf("finally {", disableLighting);
        int restoreDepthState = renderBody.indexOf(
            "restoreDepthState(depthWasEnabled, previousDepthFunc, depthMaskWasEnabled);", finallyBlock);
        int restoreLightingState = renderBody.indexOf(
            "restoreLightingState(lightingWasEnabled, light0WasEnabled, light1WasEnabled,", restoreDepthState);
        int restoreFramebuffer = renderBody.indexOf(
            "OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,",
            restoreLightingState);

        assertTrue("Shadow render must save the incoming legacy lighting capability",
            saveLighting >= 0);
        assertTrue("Shadow render must save light 0 after global lighting", saveLight0 > saveLighting);
        assertTrue("Shadow render must save light 1 after light 0", saveLight1 > saveLight0);
        assertTrue("Shadow render must save color material state before RenderHelper mutates it",
            saveColorMaterial > saveLight1 && disableLighting > saveColorMaterial);
        assertTrue("Shadow render must restore lighting state after the guarded depth-state helper",
            restoreLightingState > restoreDepthState);
        assertTrue("Lighting state must be restored before framebuffer/viewport cleanup leaves the shadow pass",
            restoreFramebuffer > restoreLightingState);

        assertTrue(restoreLighting.contains("restoreLight(0, light0WasEnabled);"));
        assertTrue(restoreLighting.contains("restoreLight(1, light1WasEnabled);"));
        assertTrue(restoreLighting.contains("GlStateManager.enableColorMaterial();"));
        assertTrue(restoreLighting.contains("GlStateManager.disableColorMaterial();"));
        assertTrue(restoreLighting.contains("GlStateManager.enableLighting();"));
        assertTrue(restoreLighting.contains("GlStateManager.disableLighting();"));
        assertTrue(restoreLighting.contains("GlStateManager.enableLight(light);"));
        assertTrue(restoreLighting.contains("GlStateManager.disableLight(light);"));
    }

    @Test
    public void shadowRenderAppliesAndRestoresLegacyAlphaStateLikeVanillaTerrain() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private static void restoreAlphaState"));
        String restoreAlpha = source.substring(
            source.indexOf("private static void restoreAlphaState"),
            source.indexOf("private static void restoreDefaultTextureBinding"));

        int saveBlend = renderBody.indexOf("boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);");
        int saveAlpha = renderBody.indexOf("boolean alphaWasEnabled = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);",
            saveBlend);
        int saveDepthFunc = renderBody.indexOf("int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);",
            saveAlpha);
        int saveAlphaFunc = renderBody.indexOf("int previousAlphaFunc = GL11.glGetInteger(GL11.GL_ALPHA_TEST_FUNC);",
            saveDepthFunc);
        int saveAlphaReference = renderBody.indexOf("float previousAlphaReference = GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF);",
            saveAlphaFunc);
        int terrainAlphaFunc = renderBody.indexOf("GlStateManager.alphaFunc(GL11.GL_GREATER, 0.5F);",
            saveAlphaReference);
        int disableAlphaForSolid = renderBody.indexOf("GlStateManager.disableAlpha();", terrainAlphaFunc);
        int solidLayer = renderBody.indexOf("BlockRenderLayer.SOLID", disableAlphaForSolid);
        int enableAlphaForCutouts = renderBody.indexOf("GlStateManager.enableAlpha();", solidLayer);
        int cutoutLayer = renderBody.indexOf("BlockRenderLayer.CUTOUT", enableAlphaForCutouts);
        int cutoutMippedLayer = renderBody.indexOf("BlockRenderLayer.CUTOUT_MIPPED", cutoutLayer);
        int translucentAlphaFunc = renderBody.indexOf("GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1F);",
            cutoutMippedLayer);
        int copyDepth = renderBody.indexOf("shadowMap.copyDepthToNoTranslucents();", translucentAlphaFunc);
        int translucentBranch = renderBody.indexOf("if (shouldRenderTranslucent)", copyDepth);
        int enableAlphaForTranslucent = renderBody.indexOf("GlStateManager.enableAlpha();", translucentBranch);
        int enableBlendForTranslucent = renderBody.indexOf("GlStateManager.enableBlend();", enableAlphaForTranslucent);
        int restoreBlend = renderBody.indexOf("restoreBlendState(blendWasEnabled,", enableBlendForTranslucent);
        int restoreAlphaCall = renderBody.indexOf(
            "restoreAlphaState(alphaWasEnabled, previousAlphaFunc, previousAlphaReference);", restoreBlend);
        int restoreDepth = renderBody.indexOf("if (depthWasEnabled)", restoreAlphaCall);

        assertTrue("Shadow render must save incoming alpha-test enablement with the rest of the draw state",
            saveAlpha > saveBlend);
        assertTrue("Shadow render must save the incoming alpha function and reference before mutating terrain alpha",
            saveAlphaFunc > saveDepthFunc && saveAlphaReference > saveAlphaFunc);
        assertTrue("Shadow terrain must use vanilla's 0.5 alpha cutoff before cutout terrain",
            terrainAlphaFunc > saveAlphaReference);
        assertTrue("Shadow terrain must disable alpha for the solid layer like vanilla 1.12 terrain",
            disableAlphaForSolid > terrainAlphaFunc && solidLayer > disableAlphaForSolid);
        assertTrue("Shadow terrain must re-enable alpha before cutout layers",
            enableAlphaForCutouts > solidLayer && cutoutLayer > enableAlphaForCutouts
                && cutoutMippedLayer > cutoutLayer);
        assertTrue("Shadow render must switch to vanilla's 0.1 alpha cutoff before entities/translucent terrain",
            translucentAlphaFunc > cutoutMippedLayer && copyDepth > translucentAlphaFunc);
        assertTrue("Shadow translucent terrain must explicitly re-enable alpha before blending",
            enableAlphaForTranslucent > translucentBranch && enableBlendForTranslucent > enableAlphaForTranslucent);
        assertTrue("Shadow cleanup must restore alpha state after blend state and before depth state",
            restoreAlphaCall > restoreBlend && restoreDepth > restoreAlphaCall);

        assertTrue(restoreAlpha.contains("GlStateManager.enableAlpha();"));
        assertTrue(restoreAlpha.contains("GlStateManager.disableAlpha();"));
        assertTrue(restoreAlpha.contains("GlStateManager.alphaFunc(previousAlphaFunc, previousAlphaReference);"));
    }

    @Test
    public void shadowRenderAppliesAndRestoresVanillaTranslucentBlendState() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private static void restoreBlendState"));
        String restoreBlend = source.substring(
            source.indexOf("private static void restoreBlendState"),
            source.indexOf("private static void restoreColorMask"));

        int saveBlendEnabled = renderBody.indexOf("boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);");
        int saveSrcRgb = renderBody.indexOf("int previousBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);",
            saveBlendEnabled);
        int saveDstRgb = renderBody.indexOf("int previousBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);",
            saveSrcRgb);
        int saveSrcAlpha = renderBody.indexOf("int previousBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);",
            saveDstRgb);
        int saveDstAlpha = renderBody.indexOf("int previousBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);",
            saveSrcAlpha);
        int translucentBranch = renderBody.indexOf("if (shouldRenderTranslucent)", saveDstAlpha);
        int enableBlend = renderBody.indexOf("GlStateManager.enableBlend();", translucentBranch);
        int vanillaBlend = renderBody.indexOf("GlStateManager.tryBlendFuncSeparate(\n"
            + "                    GL11.GL_SRC_ALPHA,\n"
            + "                    GL11.GL_ONE_MINUS_SRC_ALPHA,\n"
            + "                    GL11.GL_ONE,\n"
            + "                    GL11.GL_ZERO);", enableBlend);
        int translucentLayer = renderBody.indexOf("BlockRenderLayer.TRANSLUCENT", vanillaBlend);
        int restoreBlendCall = renderBody.indexOf(
            "restoreBlendState(blendWasEnabled, previousBlendSrcRgb, previousBlendDstRgb,", translucentLayer);
        int restoreAlpha = renderBody.indexOf(
            "restoreAlphaState(alphaWasEnabled, previousAlphaFunc, previousAlphaReference);", restoreBlendCall);

        assertTrue("Shadow render must save incoming blend enablement before mutating blend factors",
            saveBlendEnabled >= 0);
        assertTrue("Shadow render must save all incoming blend factors",
            saveSrcRgb > saveBlendEnabled && saveDstRgb > saveSrcRgb
                && saveSrcAlpha > saveDstRgb && saveDstAlpha > saveSrcAlpha);
        assertTrue("Shadow translucent terrain must use vanilla 1.12 translucent blend factors",
            vanillaBlend > enableBlend && translucentLayer > vanillaBlend);
        assertTrue("Shadow cleanup must restore blend factors before alpha/depth cleanup",
            restoreBlendCall > translucentLayer && restoreAlpha > restoreBlendCall);
        assertTrue(restoreBlend.contains("GlStateManager.enableBlend();"));
        assertTrue(restoreBlend.contains("GlStateManager.disableBlend();"));
        assertTrue(restoreBlend.contains("GlStateManager.tryBlendFuncSeparate(\n"
            + "                previousBlendSrcRgb,\n"
            + "                previousBlendDstRgb,\n"
            + "                previousBlendSrcAlpha,\n"
            + "                previousBlendDstAlpha);"));
    }

    @Test
    public void shadowRenderForcesAndRestoresColorMaskForShadowColorOutputs() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private static void restoreColorMask"));
        String restoreColorMask = source.substring(
            source.indexOf("private static void restoreColorMask"),
            source.indexOf("private static void restoreAlphaState"));

        int saveViewport = renderBody.indexOf("IntBuffer previousViewport = BufferUtils.createIntBuffer(16);");
        int saveColorMask = renderBody.indexOf("ByteBuffer previousColorMask = BufferUtils.createByteBuffer(16);",
            saveViewport);
        int readViewport = renderBody.indexOf("GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);",
            saveColorMask);
        int readColorMask = renderBody.indexOf("GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, previousColorMask);",
            readViewport);
        int disableCull = renderBody.indexOf("GlStateManager.disableCull();", readColorMask);
        int forceColorMask = renderBody.indexOf("GlStateManager.colorMask(true, true, true, true);", disableCull);
        int restoreAlpha = renderBody.indexOf(
            "restoreAlphaState(alphaWasEnabled, previousAlphaFunc, previousAlphaReference);", forceColorMask);
        int restoreColorMaskCall = renderBody.indexOf("restoreColorMask(previousColorMask);", restoreAlpha);
        int restoreDepth = renderBody.indexOf("if (depthWasEnabled)", restoreColorMaskCall);

        assertTrue("Shadow render must allocate color-mask storage alongside viewport state", saveColorMask > saveViewport);
        assertTrue("Shadow render must read the incoming color mask before mutating draw state",
            readColorMask > readViewport);
        assertTrue("Shadow render must force full color writes while rendering shadow color outputs",
            forceColorMask > disableCull);
        assertTrue("Shadow cleanup must restore the caller color mask before restoring depth state",
            restoreColorMaskCall > restoreAlpha && restoreDepth > restoreColorMaskCall);
        assertTrue(restoreColorMask.contains("previousColorMask.get(0) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(1) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(2) != 0"));
        assertTrue(restoreColorMask.contains("previousColorMask.get(3) != 0"));
    }

    @Test
    public void shadowRenderAppliesAndRestoresLegacyTerrainShadeModel() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private static void restoreBlendState"));

        int saveDepthFunc = renderBody.indexOf("int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);");
        int saveShadeModel = renderBody.indexOf("int previousShadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL);",
            saveDepthFunc);
        int disableCull = renderBody.indexOf("GlStateManager.disableCull();", saveShadeModel);
        int initialFlat = renderBody.indexOf("GlStateManager.shadeModel(GL11.GL_FLAT);", disableCull);
        int solidLayer = renderBody.indexOf("BlockRenderLayer.SOLID", initialFlat);
        int cutoutMippedLayer = renderBody.indexOf("BlockRenderLayer.CUTOUT_MIPPED", solidLayer);
        int flatBeforeEntities = renderBody.indexOf("GlStateManager.shadeModel(GL11.GL_FLAT);", cutoutMippedLayer);
        int translucentBranch = renderBody.indexOf("if (shouldRenderTranslucent)", flatBeforeEntities);
        int smoothBeforeTranslucent = renderBody.indexOf("GlStateManager.shadeModel(GL11.GL_SMOOTH);", translucentBranch);
        int translucentLayer = renderBody.indexOf("BlockRenderLayer.TRANSLUCENT", smoothBeforeTranslucent);
        int restoreColorMask = renderBody.indexOf("restoreColorMask(previousColorMask);", translucentLayer);
        int restoreShadeModel = renderBody.indexOf("GlStateManager.shadeModel(previousShadeModel);", restoreColorMask);
        int restoreDepth = renderBody.indexOf("if (depthWasEnabled)", restoreShadeModel);

        assertTrue("Shadow render must save the incoming shade model before terrain setup",
            saveShadeModel > saveDepthFunc);
        assertTrue("Shadow render must force flat shading before opaque/cutout terrain",
            initialFlat > disableCull && solidLayer > initialFlat);
        assertTrue("Shadow render must return to flat shading before entity and depth-copy work",
            flatBeforeEntities > cutoutMippedLayer);
        assertTrue("Shadow translucent terrain must use vanilla smooth shading",
            smoothBeforeTranslucent > translucentBranch && translucentLayer > smoothBeforeTranslucent);
        assertTrue("Shadow cleanup must restore the caller shade model before depth-state cleanup",
            restoreShadeModel > restoreColorMask && restoreDepth > restoreShadeModel);
    }

    @Test
    public void shadowEntitySetupCleanupIsGuardedAndFailurePreserving() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        int entityStart = source.indexOf("if (shouldRenderEntities || shouldRenderPlayer || shouldRenderBlockEntities)");
        String entityBlock = source.substring(entityStart,
            source.indexOf("shadowMap.copyDepthToNoTranslucents();", entityStart));

        int lightingFlag = entityBlock.indexOf("boolean entityLightingCleanupRequired = false;");
        int renderPassFlag = entityBlock.indexOf("boolean entityRenderPassCleanupRequired = false;", lightingFlag);
        int filteringFlag = entityBlock.indexOf("boolean entityFilteringCleanupRequired = false;", renderPassFlag);
        int entityFailure = entityBlock.indexOf("Throwable entityFailure = null;", filteringFlag);
        int tryStart = entityBlock.indexOf("try {", entityFailure);

        int markLighting = entityBlock.indexOf("entityLightingCleanupRequired = true;", tryStart);
        int enableLighting = entityBlock.indexOf("RenderHelper.enableStandardItemLighting();", markLighting);
        int markRenderPass = entityBlock.indexOf("entityRenderPassCleanupRequired = true;", enableLighting);
        int setRenderPass = entityBlock.indexOf("ForgeHooksClient.setRenderPass(0);", markRenderPass);
        int markFiltering = entityBlock.indexOf("entityFilteringCleanupRequired = true;", setRenderPass);
        int beginFiltering = entityBlock.indexOf("ShadowRenderingState.beginEntityFiltering(", markFiltering);
        int renderEntities = entityBlock.indexOf("renderGlobal.renderEntities(", beginFiltering);

        int catchBlock = entityBlock.indexOf("} catch (RuntimeException | Error exception) {", renderEntities);
        int recordFailure = entityBlock.indexOf("entityFailure = exception;", catchBlock);
        int rethrowEntity = entityBlock.indexOf("throw exception;", recordFailure);
        int finallyBlock = entityBlock.indexOf("} finally {", rethrowEntity);
        int cleanupFailure = entityBlock.indexOf("Throwable entityCleanupFailure = null;", finallyBlock);

        int cleanupFilteringGuard = entityBlock.indexOf("if (entityFilteringCleanupRequired)", cleanupFailure);
        int cleanupFiltering = entityBlock.indexOf(
            "entityCleanupFailure = runCleanup(entityCleanupFailure, ShadowRenderingState::endEntityFiltering);",
            cleanupFilteringGuard);
        int cleanupRenderPassGuard = entityBlock.indexOf("if (entityRenderPassCleanupRequired)", cleanupFiltering);
        int cleanupRenderPass = entityBlock.indexOf(
            "entityCleanupFailure = runCleanup(entityCleanupFailure, () -> ForgeHooksClient.setRenderPass(-1));",
            cleanupRenderPassGuard);
        int cleanupLightingGuard = entityBlock.indexOf("if (entityLightingCleanupRequired)", cleanupRenderPass);
        int cleanupLighting = entityBlock.indexOf(
            "entityCleanupFailure = runCleanup(entityCleanupFailure, RenderHelper::disableStandardItemLighting);",
            cleanupLightingGuard);
        int suppressCleanup = entityBlock.indexOf(
            "addSuppressedCleanupFailure(entityFailure, entityCleanupFailure);", cleanupLighting);
        int rethrowCleanupOnly = entityBlock.indexOf("rethrowCleanupFailure(entityCleanupFailure);", suppressCleanup);

        assertTrue("Entity shadow setup must declare cleanup flags before mutating Forge or lighting state",
            lightingFlag >= 0 && renderPassFlag > lightingFlag && filteringFlag > renderPassFlag
                && entityFailure > filteringFlag && tryStart > entityFailure);
        assertTrue("Entity shadow setup must mark each cleanup as required before the matching state mutation",
            markLighting > tryStart && enableLighting > markLighting
                && markRenderPass > enableLighting && setRenderPass > markRenderPass
                && markFiltering > setRenderPass && beginFiltering > markFiltering
                && renderEntities > beginFiltering);
        assertTrue("Entity render failures must be preserved before cleanup runs",
            catchBlock > renderEntities && recordFailure > catchBlock
                && rethrowEntity > recordFailure && finallyBlock > rethrowEntity);
        assertTrue("Entity cleanup must run filtering, render-pass, then lighting restoration through runCleanup",
            cleanupFailure > finallyBlock
                && cleanupFilteringGuard > cleanupFailure && cleanupFiltering > cleanupFilteringGuard
                && cleanupRenderPassGuard > cleanupFiltering && cleanupRenderPass > cleanupRenderPassGuard
                && cleanupLightingGuard > cleanupRenderPass && cleanupLighting > cleanupLightingGuard);
        assertTrue("Entity cleanup failures must be suppressed onto primary failures or rethrown alone",
            suppressCleanup > cleanupLighting && rethrowCleanupOnly > suppressCleanup);
    }

    @Test
    public void shadowAllEntityRenderingBypassesVanillaFirstPersonSkipWithDirectPlayerRender() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        int entityStart = source.indexOf("if (shouldRenderEntities || shouldRenderPlayer || shouldRenderBlockEntities)");
        String entityBlock = source.substring(entityStart,
            source.indexOf("shadowMap.copyDepthToNoTranslucents();", entityStart));

        int shadowPlayer = entityBlock.indexOf("Entity shadowPlayer = mc.player != null ? mc.player : cameraEntity;");
        int directFirstPersonFlag = entityBlock.indexOf("boolean renderFirstPersonPlayerDirectly =", shadowPlayer);
        int directFirstPersonHelper = entityBlock.indexOf(
            "shouldRenderEntities && shouldRenderFirstPersonPlayerDirectly(mc, shadowPlayer);",
            directFirstPersonFlag);
        int vanillaElse = entityBlock.indexOf("} else {", directFirstPersonHelper);
        int fullFiltering = entityBlock.indexOf("ShadowRenderingState.beginEntityFiltering(", vanillaElse);
        int renderGlobal = entityBlock.indexOf(
            "renderGlobal.renderEntities(cameraEntity, entityShadowCamera, partialTicks);", fullFiltering);
        int directBranch = entityBlock.indexOf("if (renderFirstPersonPlayerDirectly)", renderGlobal);
        int directFiltering = entityBlock.indexOf(
            "ShadowRenderingState.beginEntityFiltering(false, true, false, shadowPlayer);", directBranch);
        int directRender = entityBlock.indexOf(
            "renderShadowPlayerEntity(mc, entityShadowCamera, shadowPlayer, cameraX, cameraY, cameraZ,",
            directFiltering);

        int cleanup = entityBlock.indexOf("Throwable entityCleanupFailure = null;", directRender);
        int cleanupFiltering = entityBlock.indexOf("if (entityFilteringCleanupRequired)", cleanup);

        String helper = source.substring(
            source.indexOf("private static boolean shouldRenderFirstPersonPlayerDirectly"),
            source.indexOf("private static Throwable restoreAfterShadowRender"));

        assertTrue("Shadow entity rendering must select the real player entity before vanilla RenderGlobal runs",
            shadowPlayer >= 0 && directFirstPersonFlag > shadowPlayer && directFirstPersonHelper > directFirstPersonFlag);
        assertTrue("Full entity shadow rendering must explicitly draw the first-person local player after vanilla traversal",
            vanillaElse > directFirstPersonHelper && fullFiltering > vanillaElse && renderGlobal > fullFiltering
                && directBranch > renderGlobal && directFiltering > directBranch && directRender > directFiltering);
        assertTrue("The direct first-person draw must complete before shadow entity filtering is torn down",
            cleanup > directRender && cleanupFiltering > cleanup);
        assertTrue("The direct first-person draw must only apply to the real local player",
            helper.contains("mc.player == null || shadowPlayer != mc.player")
                && helper.contains("mc.gameSettings.thirdPersonView == 0")
                && helper.contains("mc.getRenderViewEntity() == shadowPlayer")
                && helper.contains("!((EntityPlayer) shadowPlayer).isPlayerSleeping()"));
    }

    @Test
    public void shadowPlayerOnlyUsesDirectRenderBeforeOptionalBlockEntityFallback() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        int entityStart = source.indexOf("if (shouldRenderEntities || shouldRenderPlayer || shouldRenderBlockEntities)");
        String entityBlock = source.substring(entityStart,
            source.indexOf("shadowMap.copyDepthToNoTranslucents();", entityStart));

        int directFlag = entityBlock.indexOf("boolean renderPlayerDirectly = shouldRenderPlayer && !shouldRenderEntities;");
        int markFiltering = entityBlock.indexOf("entityFilteringCleanupRequired = true;", directFlag);
        int directBranch = entityBlock.indexOf("if (renderPlayerDirectly)", markFiltering);
        int directFiltering = entityBlock.indexOf(
            "ShadowRenderingState.beginEntityFiltering(false, true, false, shadowPlayer);", directBranch);
        int directRender = entityBlock.indexOf(
            "renderShadowPlayerEntity(mc, entityShadowCamera, shadowPlayer, cameraX, cameraY, cameraZ,",
            directFiltering);
        int blockFallback = entityBlock.indexOf("if (shouldRenderBlockEntities)", directRender);
        int blockFiltering = entityBlock.indexOf(
            "ShadowRenderingState.beginEntityFiltering(false, false, true, shadowPlayer);", blockFallback);
        int blockRenderGlobal = entityBlock.indexOf(
            "renderGlobal.renderEntities(cameraEntity, entityShadowCamera, partialTicks);", blockFiltering);
        int vanillaElse = entityBlock.indexOf("} else {", blockRenderGlobal);
        int fullFiltering = entityBlock.indexOf("ShadowRenderingState.beginEntityFiltering(", vanillaElse);
        int fullRenderGlobal = entityBlock.indexOf(
            "renderGlobal.renderEntities(cameraEntity, entityShadowCamera, partialTicks);", fullFiltering);

        assertTrue("shadowPlayer=true with shadowEntities=false must use a direct player path like 1.16.5",
            directFlag >= 0 && directBranch > markFiltering && directFiltering > directBranch
                && directRender > directFiltering);
        assertTrue("Block entities must remain on the vanilla RenderGlobal path after the direct player draw",
            blockFallback > directRender && blockFiltering > blockFallback && blockRenderGlobal > blockFiltering);
        assertTrue("Full entity shadows must keep the vanilla traversal path",
            vanillaElse > blockRenderGlobal && fullFiltering > vanillaElse && fullRenderGlobal > fullFiltering);
    }

    @Test
    public void shadowDirectPlayerHelperMirrorsReferencePassengerVehiclePlayerOrdering() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String helper = source.substring(
            source.indexOf("private static void renderShadowPlayerEntity"),
            source.indexOf("private static void renderShadowEntityIfRenderable"));

        int importRenderManager = source.indexOf("import net.minecraft.client.renderer.entity.RenderManager;");
        int importGbufferPrograms = source.indexOf("import net.oculus.layer.GbufferPrograms;");
        int spectatorSkip = helper.indexOf("player instanceof EntityPlayer && ((EntityPlayer) player).isSpectator()");
        int renderManager = helper.indexOf("RenderManager renderManager = mc.getRenderManager();", spectatorSkip);
        int shouldRender = helper.indexOf(
            "renderManager.shouldRender(player, entityShadowCamera, cameraX, cameraY, cameraZ)", renderManager);
        int cacheInfo = helper.indexOf("renderManager.cacheActiveRenderInfo(", shouldRender);
        int setRenderPosition = helper.indexOf("renderManager.setRenderPosition(", cacheInfo);
        int beginEntities = helper.indexOf("GbufferPrograms.beginEntities();", setRenderPosition);
        int enableLightmap = helper.indexOf("mc.entityRenderer.enableLightmap();", beginEntities);
        int passengersLoop = helper.indexOf("for (Entity passenger : player.getPassengers())", enableLightmap);
        int renderPassenger = helper.indexOf(
            "renderShadowEntityIfRenderable(renderManager, passenger, partialTicks);", passengersLoop);
        int vehicle = helper.indexOf("Entity vehicle = player.getRidingEntity();", renderPassenger);
        int renderVehicle = helper.indexOf(
            "renderShadowEntityIfRenderable(renderManager, vehicle, partialTicks);", vehicle);
        int renderPlayer = helper.indexOf(
            "renderShadowEntityIfRenderable(renderManager, player, partialTicks);", renderVehicle);
        int disableLightmap = helper.indexOf("mc.entityRenderer.disableLightmap()", renderPlayer);
        int endEntities = helper.indexOf("GbufferPrograms::endEntities", disableLightmap);
        int suppress = helper.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", endEntities);
        int renderPassGuard = source.indexOf(
            "entity != null && entity.shouldRenderInPass(MinecraftForgeClient.getRenderPass())",
            source.indexOf("private static void renderShadowEntityIfRenderable"));

        assertTrue(importRenderManager >= 0 && importGbufferPrograms > importRenderManager);
        assertTrue("Direct player shadows must cull and skip spectator players before drawing",
            spectatorSkip >= 0 && renderManager > spectatorSkip && shouldRender > renderManager);
        assertTrue("Direct player shadows must prepare RenderManager camera state before entity phase rendering",
            cacheInfo > shouldRender && setRenderPosition > cacheInfo && beginEntities > setRenderPosition
                && enableLightmap > beginEntities);
        assertTrue("Direct player shadows must render passengers, vehicle, then player like 1.16.5",
            passengersLoop > enableLightmap && renderPassenger > passengersLoop
                && vehicle > renderPassenger && renderVehicle > vehicle && renderPlayer > renderVehicle);
        assertTrue("Direct player shadow cleanup must disable lightmap and leave the entity phase",
            disableLightmap > renderPlayer && endEntities > disableLightmap && suppress > endEntities);
        assertTrue("Direct player helper must honor the active Forge render pass on 1.12",
            renderPassGuard >= 0);
    }

    @Test
    public void shadowDepthCopyTranslucentAndMipmapOrderingMatchesReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = source.substring(
            source.indexOf("public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)"),
            source.indexOf("private static Throwable restoreAfterShadowRender"));

        int terrainBranch = renderBody.indexOf("if (shouldRenderTerrain)");
        int solidLayer = renderBody.indexOf("BlockRenderLayer.SOLID", terrainBranch);
        int cutoutLayer = renderBody.indexOf("BlockRenderLayer.CUTOUT", solidLayer);
        int cutoutMippedLayer = renderBody.indexOf("BlockRenderLayer.CUTOUT_MIPPED", cutoutLayer);
        int entityBranch = renderBody.indexOf("if (shouldRenderEntities || shouldRenderPlayer || shouldRenderBlockEntities)",
            cutoutMippedLayer);
        int copyDepth = renderBody.indexOf("shadowMap.copyDepthToNoTranslucents();", entityBranch);
        int translucentBranch = renderBody.indexOf("if (shouldRenderTranslucent)", copyDepth);
        int translucentLayer = renderBody.indexOf("BlockRenderLayer.TRANSLUCENT", translucentBranch);
        int generateMipmaps = renderBody.indexOf("shadowMap.generateMipmaps();", translucentLayer);
        int validateDepth = renderBody.indexOf("validateShadowDepthOutput();", generateMipmaps);
        int dumpDepth = renderBody.indexOf("dumpShadowDepthForValidation();", validateDepth);
        int cleanup = renderBody.indexOf("finally {", dumpDepth);

        assertTrue("Shadow terrain must render opaque/cutout layers before entity depth capture",
            terrainBranch >= 0 && solidLayer > terrainBranch && cutoutLayer > solidLayer
                && cutoutMippedLayer > cutoutLayer && entityBranch > cutoutMippedLayer);
        assertTrue("Shadow depthtex1 must capture after entities and before translucent terrain",
            copyDepth > entityBranch && translucentBranch > copyDepth);
        assertTrue("Shadow translucent terrain must render before shadow mipmap generation",
            translucentLayer > translucentBranch && generateMipmaps > translucentLayer);
        assertTrue("Shadow depth validation, late dump, and final cleanup must run after mipmaps are generated",
            validateDepth > generateMipmaps && dumpDepth > validateDepth && cleanup > dumpDepth);
    }

    @Test
    public void validationDumpsBothShadowDepthTargetsAtConfiguredRuntimeDumpTick() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String dump = methodBody(source, "private void dumpShadowDepthForValidation");

        assertTrue(dump.contains("OculusRuntimeValidation.dumpDepthTexture("));
        assertTrue(dump.contains("\"shadow-depth-post-render\""));
        assertTrue(dump.contains("shadowMap.getDepthTexture(),"));
        assertTrue(dump.contains("\"shadow-depth-notrans-post-render\""));
        assertTrue(dump.contains("shadowMap.getDepthTextureNoTranslucents(),"));
        assertTrue(dump.contains("resolution,"));
    }

    @Test
    public void shadowRenderCleanupKeepsRestoringStateIfEarlierCleanupThrows() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String cleanup = source.substring(
            source.indexOf("shadowMap.generateMipmaps();"),
            source.indexOf("private static void restoreBlendState"));

        int renderCatch = cleanup.indexOf("} catch (RuntimeException | Error exception) {");
        int recordPrimary = cleanup.indexOf("failure = exception;", renderCatch);
        int cleanupFailure = cleanup.indexOf("Throwable cleanupFailure = restoreAfterShadowRender(", recordPrimary);
        int suppressCleanup = cleanup.indexOf("addSuppressedCleanupFailure(failure, cleanupFailure);", cleanupFailure);
        int rethrowCleanupOnly = cleanup.indexOf("rethrowCleanupFailure(cleanupFailure);", suppressCleanup);
        assertTrue("Shadow render must preserve the original render failure before cleanup aggregation",
            renderCatch > 0 && recordPrimary > renderCatch && cleanupFailure > recordPrimary);
        assertTrue("Shadow render must suppress cleanup failures onto primary failures or rethrow cleanup-only failures",
            suppressCleanup > cleanupFailure && rethrowCleanupOnly > suppressCleanup);

        assertCleanupStepRecorded(cleanup,
            "renderGlobal.setDisplayListEntitiesDirty();",
            "ForgeHooksClient.setRenderPass(-1);");
        assertCleanupStepRecorded(cleanup,
            "ForgeHooksClient.setRenderPass(-1);",
            "if (modelViewPushed)");
        assertCleanupStepRecorded(cleanup,
            "GlStateManager.popMatrix();",
            "if (projectionPushed)");

        int projectionMode = cleanup.indexOf("GlStateManager.matrixMode(GL11.GL_PROJECTION);");
        int projectionPop = cleanup.indexOf("GlStateManager.popMatrix();", projectionMode);
        int projectionPopCatch = cleanup.indexOf("failure = addCleanupFailure(failure, exception);", projectionPop);
        int returnModelViewMode = cleanup.indexOf(
            "GlStateManager.matrixMode(GL11.GL_MODELVIEW);", projectionPopCatch);
        assertTrue("Projection cleanup must return to model-view mode even if projection pop fails",
            projectionMode >= 0 && projectionPop > projectionMode
                && projectionPopCatch > projectionPop && returnModelViewMode > projectionPopCatch);

        assertCleanupStepRecorded(cleanup,
            "if (cullWasEnabled)",
            "restoreBlendState(blendWasEnabled, previousBlendSrcRgb, previousBlendDstRgb,");
        assertCleanupStepRecorded(cleanup,
            "restoreBlendState(blendWasEnabled, previousBlendSrcRgb, previousBlendDstRgb,",
            "restoreAlphaState(alphaWasEnabled, previousAlphaFunc, previousAlphaReference);");
        assertCleanupStepRecorded(cleanup,
            "restoreAlphaState(alphaWasEnabled, previousAlphaFunc, previousAlphaReference);",
            "restoreColorMask(previousColorMask);");
        assertCleanupStepRecorded(cleanup,
            "restoreColorMask(previousColorMask);",
            "GlStateManager.shadeModel(previousShadeModel);");
        assertCleanupStepRecorded(cleanup,
            "GlStateManager.shadeModel(previousShadeModel);",
            "if (depthWasEnabled)");
        assertCleanupStepRecorded(cleanup,
            "if (depthWasEnabled)",
            "GlStateManager.depthFunc(previousDepthFunc);");
        assertCleanupStepRecorded(cleanup,
            "GlStateManager.depthFunc(previousDepthFunc);",
            "GlStateManager.depthMask(depthMaskWasEnabled);");
        assertCleanupStepRecorded(cleanup,
            "restoreDepthState(depthWasEnabled, previousDepthFunc, depthMaskWasEnabled);",
            "restoreLightingState(lightingWasEnabled, light0WasEnabled, light1WasEnabled,");
        assertCleanupStepRecorded(cleanup,
            "restoreLightingState(lightingWasEnabled, light0WasEnabled, light1WasEnabled,",
            "restoreDefaultTextureBinding(previousTexture, previousActiveTexture);");
        assertCleanupStepRecorded(cleanup,
            "restoreDefaultTextureBinding(previousTexture, previousActiveTexture);",
            "OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,");
        assertCleanupStepRecorded(cleanup,
            "OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,",
            "GL11.glViewport(");
        assertCleanupStepRecorded(cleanup,
            "GL11.glViewport(",
            "ShadowRenderingState.endShadowPass();");
    }

    @Test
    public void shadowCameraSetupUsesInterpolatedCameraAndRestoresChunkCullingState() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String renderBody = methodBody(source,
            "public void renderShadows(RenderGlobal renderGlobal, Entity cameraEntity, float partialTicks)");
        String createEntityCamera = methodBody(source, "private ICamera createEntityShadowCamera");
        String createCamera = methodBody(source,
            "private ICamera createShadowCamera(\n"
                + "            Minecraft mc,\n"
                + "            double cameraX,\n"
                + "            double cameraY,\n"
                + "            double cameraZ,\n"
                + "            float renderMultiplier)");

        int matrixCopy = renderBody.indexOf(
            "copyMatrix(ShadowUniforms.getShadowModelViewInverse(), shadowModelViewInverse);");
        int cameraX = renderBody.indexOf(
            "double cameraX = cameraEntity.lastTickPosX + (cameraEntity.posX - cameraEntity.lastTickPosX) * partialTicks;",
            matrixCopy);
        int cameraY = renderBody.indexOf(
            "double cameraY = cameraEntity.lastTickPosY + (cameraEntity.posY - cameraEntity.lastTickPosY) * partialTicks;",
            cameraX);
        int cameraZ = renderBody.indexOf(
            "double cameraZ = cameraEntity.lastTickPosZ + (cameraEntity.posZ - cameraEntity.lastTickPosZ) * partialTicks;",
            cameraY);
        int terrainCamera = renderBody.indexOf(
            "ICamera shadowCamera = createShadowCamera(mc, cameraX, cameraY, cameraZ);", cameraZ);
        int captureChunkCulling = renderBody.indexOf("boolean previousRenderChunksMany = mc.renderChunksMany;",
            terrainCamera);
        int disableChunkCulling = renderBody.indexOf("mc.renderChunksMany = false;", captureChunkCulling);
        int terrainTry = renderBody.indexOf("try {", disableChunkCulling);
        int markTerrainDirty = renderBody.indexOf("renderGlobal.setDisplayListEntitiesDirty();", terrainTry);
        int setupTerrain = renderBody.indexOf(
            "renderGlobal.setupTerrain(cameraEntity, partialTicks, shadowCamera, shadowFrameCounter++, false);",
            markTerrainDirty);
        int restoreChunkCulling = renderBody.indexOf("mc.renderChunksMany = previousRenderChunksMany;", setupTerrain);
        int entityCamera = renderBody.indexOf(
            "ICamera entityShadowCamera = createEntityShadowCamera(mc, shadowCamera, cameraX, cameraY, cameraZ);",
            restoreChunkCulling);

        assertTrue("Shadow camera setup must use the interpolated frame camera after shadow matrix capture",
            matrixCopy >= 0 && cameraX > matrixCopy && cameraY > cameraX && cameraZ > cameraY);
        assertTrue("Shadow terrain culling must build the terrain camera from the interpolated camera position",
            terrainCamera > cameraZ);
        assertTrue("Shadow terrain setup must disable chunk culling only after saving the caller state",
            captureChunkCulling > terrainCamera && disableChunkCulling > captureChunkCulling);
        assertTrue("Shadow terrain setup must dirty terrain display lists before vanilla setupTerrain traversal",
            terrainTry > disableChunkCulling && markTerrainDirty > terrainTry && setupTerrain > markTerrainDirty);
        assertTrue("Shadow terrain setup must restore 1.12 chunk-culling state before entity camera setup",
            restoreChunkCulling > setupTerrain && entityCamera > restoreChunkCulling);

        int switchCulling = createCamera.indexOf("switch (shadowCullingMode)");
        int disabledCamera = createCamera.indexOf("case DISABLED:", switchCulling);
        int reversedCamera = createCamera.indexOf("case REVERSED:", disabledCamera);
        int defaultCamera = createCamera.indexOf("case DEFAULT:", reversedCamera);
        int setCameraPosition = createCamera.indexOf("camera.setPosition(cameraX, cameraY, cameraZ);", defaultCamera);
        int returnCamera = createCamera.indexOf("return camera;", setCameraPosition);

        assertTrue("Shadow camera creation must choose the culling implementation before preparing the camera position",
            switchCulling >= 0 && disabledCamera > switchCulling
                && reversedCamera > disabledCamera && defaultCamera > reversedCamera);
        assertTrue("Every shadow culling camera must be prepared with the interpolated camera position",
            setCameraPosition > defaultCamera && returnCamera > setCameraPosition);

        int reuseTerrainCamera = createEntityCamera.indexOf(
            "if (usesTerrainCameraForEntityShadows(entityShadowDistanceMultiplier))");
        int returnTerrainCamera = createEntityCamera.indexOf("return terrainShadowCamera;", reuseTerrainCamera);
        int createSeparateCamera = createEntityCamera.indexOf("return createShadowCamera(", returnTerrainCamera);
        int entityMultiplier = createEntityCamera.indexOf(
            "entityShadowRenderMultiplier(shadowDistanceRenderMultiplier, entityShadowDistanceMultiplier)",
            createSeparateCamera);

        assertTrue("Entity shadows must reuse the terrain camera when the entity multiplier matches the terrain pass",
            reuseTerrainCamera >= 0 && returnTerrainCamera > reuseTerrainCamera);
        assertTrue("Entity shadows must create a separately scaled camera when the entity multiplier differs",
            createSeparateCamera > returnTerrainCamera && entityMultiplier > createSeparateCamera);
        assertTrue(source.contains("return entityShadowDistanceMultiplier == 1.0F || entityShadowDistanceMultiplier < 0.0F;"));
    }

    @Test
    public void voxelizingShadowProgramsRelaxDefaultFrustumCullingLikeReference() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String cameras = read("src/main/java/net/oculus/pipeline/shadow/ShadowCullingCameras.java");
        String standardCamera = methodBody(source, "private ICamera createStandardCullingCamera");
        String distanceOnlyCamera = methodBody(source, "private ICamera createDistanceOnlyCamera");

        assertTrue(source.contains("private boolean packHasVoxelization;"));
        assertTrue(source.contains("ProgramSource shadowSource"));
        assertTrue(source.contains("this.packHasVoxelization = shadowSource != null && shadowSource.getGeometrySource().isPresent();"));
        assertTrue(source.contains("public void setUsesImages(boolean usesImages)"));
        assertTrue(source.contains("packHasVoxelization = packHasVoxelization || usesImages;"));
        assertTrue(source.contains("private boolean shouldUseDistanceOnlyCullingForVoxelization()"));
        assertTrue(source.contains("return packHasVoxelization && shadowCullingMode == ShadowCullingMode.DEFAULT;"));
        assertTrue(source.contains("camera = shouldUseDistanceOnlyCullingForVoxelization()\n"
            + "                    ? createDistanceOnlyCamera(mc, renderMultiplier)\n"
            + "                    : createStandardCullingCamera(mc, 0.0D, renderMultiplier);"));
        assertTrue("Enabled/default culling with zero distance must cull all geometry like the 1.16.5 path",
            standardCamera.contains("if (distance == 0.0D) {\n"
                + "            return ShadowCullingCameras.cullEverything();\n"
                + "        }"));
        assertTrue("Enabled/default culling must use the 1.16.5 advanced shadow-caster frustum inputs",
            standardCamera.contains("ShadowCullingCameras.advanced(")
                && standardCamera.contains("CapturedRenderingState.INSTANCE.getGbufferModelView()")
                && standardCamera.contains("CapturedRenderingState.INSTANCE.getGbufferProjection()")
                && standardCamera.contains("CelestialUniforms.getShadowLightPositionInWorldSpace()"));
        assertFalse("Enabled/default culling must not fall back to a vanilla camera frustum for shadow casters",
            standardCamera.contains("new Frustum()"));
        assertTrue("Distance-only fallback for disabled/voxelizing packs must keep non-culling behavior at zero distance",
            distanceOnlyCamera.contains("return ShadowCullingCameras.nonCulling();"));
        assertTrue(cameras.contains("static ICamera cullEverything()"));
        assertTrue(cameras.contains("private static final ClippingHelper UNUSED_CLIPPING_HELPER = new ClippingHelper();"));
        assertTrue(cameras.contains("import net.minecraft.client.renderer.culling.Frustum;"));
        assertTrue(cameras.contains("super(UNUSED_CLIPPING_HELPER);"));
        assertTrue(cameras.contains("private static final class CullEverythingCamera extends Frustum"));
        assertTrue(cameras.contains("private static final class DistanceCamera extends Frustum"));
        assertTrue(cameras.contains("private static final class CombiningCamera extends Frustum"));
        assertTrue(cameras.contains("static ICamera advanced(float[] playerView, float[] playerProjection, float[] shadowLightVectorFromOrigin,"));
        assertTrue(cameras.contains("private static final class AdvancedShadowCullingCamera extends Frustum"));
        assertTrue(cameras.contains("public boolean isBoxInFrustum("));
        assertTrue(cameras.contains("createBaseClippingPlanes(playerView, playerProjection)"));
        assertTrue(cameras.contains("addEdgePlane(plane, basePlanes[neighbors.plane0]);"));
        assertTrue(cameras.contains("public boolean isBoundingBoxInFrustum(AxisAlignedBB aabb) {\n"
            + "            return false;\n"
            + "        }"));
    }

    @Test
    public void shadowRendererDestroyAggregatesComputeAndFramebufferCleanupFailures() throws Exception {
        String source = read("src/main/java/net/oculus/pipeline/shadow/ShadowRenderer.java");
        String destroy = source.substring(
            source.indexOf("public void destroy()"),
            source.indexOf("private static void destroyComputePrograms"));
        String helpers = source.substring(source.indexOf("private static Throwable destroyComputePrograms"));

        int failureLocal = destroy.indexOf("Throwable failure = null;");
        int destroyComputes = destroy.indexOf("failure = destroyComputePrograms(failure, shadowComputes);",
            failureLocal);
        int destroyCompositeComputes = destroy.indexOf(
            "failure = destroyComputeProgramGroups(failure, shadowCompositeComputes);", destroyComputes);
        int destroyClearPasses = destroy.indexOf("failure = destroyClearPassFramebuffers(failure, shadowClearPasses);",
            destroyCompositeComputes);
        int destroyFullClearPasses = destroy.indexOf(
            "failure = destroyClearPassFramebuffers(failure, shadowClearPassesFull);", destroyClearPasses);
        int destroyFramebuffer = destroy.indexOf("failure = destroyShadowFramebuffer(failure, shadowFramebuffer);",
            destroyFullClearPasses);
        int clearPassHelper = helpers.indexOf("for (ClearPass clearPass : clearPasses)");
        int clearPassDestroy = helpers.indexOf("shadowMap.destroyFramebuffer(clearPass.getFramebuffer())",
            clearPassHelper);
        int clearPassCatch = helpers.indexOf("failure = addCleanupFailure(failure, exception);",
            clearPassDestroy);
        int framebufferHelper = helpers.indexOf(
            "private Throwable destroyShadowFramebuffer(Throwable failure, GlFramebuffer framebuffer)");
        int framebufferGuard = helpers.indexOf("if (framebuffer == null)", framebufferHelper);
        int framebufferDestroy = helpers.indexOf("shadowMap.destroyFramebuffer(framebuffer)", framebufferGuard);
        int rethrow = destroy.indexOf("rethrowCleanupFailure(failure);", destroyFramebuffer);
        int finallyBlock = destroy.indexOf("} finally {", rethrow);
        int markDestroyed = destroy.indexOf("destroyed = true;", finallyBlock);

        int computeLoop = helpers.indexOf("for (ComputeProgram compute : computes)");
        int computeDestroy = helpers.indexOf("compute.destroy();", computeLoop);
        int computeCatch = helpers.indexOf("failure = addCleanupFailure(failure, exception);", computeDestroy);

        assertTrue(failureLocal >= 0);
        assertTrue("Shadow destroy must attempt compute cleanup before clear-pass framebuffer deletion",
            destroyComputes > failureLocal && destroyCompositeComputes > destroyComputes
                && destroyClearPasses > destroyCompositeComputes
                && destroyFullClearPasses > destroyClearPasses);
        assertTrue("Shadow destroy must delete clear-pass framebuffers before the root shadow framebuffer",
            destroyFramebuffer > destroyFullClearPasses);
        assertTrue("Shadow clear-pass cleanup must keep iterating while recording failures",
            clearPassHelper >= 0 && clearPassDestroy > clearPassHelper && clearPassCatch > clearPassDestroy);
        assertTrue("Shadow destroy must rethrow only after framebuffer cleanup has been attempted",
            framebufferHelper >= 0 && framebufferGuard > framebufferHelper
                && framebufferDestroy > framebufferGuard && rethrow > destroyFramebuffer);
        assertTrue("Shadow renderer must mark destroyed even when owned cleanup reports a failure",
            finallyBlock > rethrow && markDestroyed > finallyBlock);
        assertTrue("Shadow compute cleanup must keep iterating while recording failures",
            computeLoop >= 0 && computeDestroy > computeLoop && computeCatch > computeDestroy);
        assertTrue(helpers.contains("failure.addSuppressed(exception);"));
        assertTrue(helpers.contains("throw (RuntimeException) failure;"));
        assertTrue(helpers.contains("throw (Error) failure;"));
    }

    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        if (start < 0) {
            throw new AssertionError("Missing method " + signature);
        }

        int openBrace = source.indexOf('{', start);
        int depth = 0;
        for (int i = openBrace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(openBrace + 1, i);
                }
            }
        }

        throw new AssertionError("Unable to read method body for " + signature);
    }

    private static void assertCleanupStepRecorded(String cleanup, String previousStep, String nextStep) {
        int previous = cleanup.indexOf(previousStep);
        int catchBlock = cleanup.indexOf("catch (RuntimeException | Error exception)", previous + previousStep.length());
        int recordedFailure = cleanup.indexOf("failure = addCleanupFailure(failure, exception);", catchBlock);
        int next = cleanup.indexOf(nextStep, recordedFailure);

        assertTrue("Expected cleanup step: " + previousStep, previous >= 0);
        assertTrue("Expected cleanup failure recording after cleanup step: " + previousStep,
            catchBlock > previous && recordedFailure > catchBlock);
        assertTrue("Expected cleanup step to run after failure recording: " + nextStep, next > recordedFailure);
    }

    private static void assertFramebufferBindingsRestored(String body, String context, String expectedBind) {
        int saveFramebuffer = body.indexOf("int previousFramebuffer = OculusRenderSystem.getFramebufferBinding();");
        if (saveFramebuffer < 0) {
            saveFramebuffer = body.indexOf("previousFramebuffer = OculusRenderSystem.getFramebufferBinding();");
        }
        int saveReadFramebuffer = body.indexOf("int previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();",
            saveFramebuffer);
        if (saveReadFramebuffer < 0) {
            saveReadFramebuffer = body.indexOf("previousReadFramebuffer = OculusRenderSystem.getReadFramebufferBinding();",
                saveFramebuffer);
        }
        int saveDrawFramebuffer = body.indexOf("int previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();",
            saveReadFramebuffer);
        if (saveDrawFramebuffer < 0) {
            saveDrawFramebuffer = body.indexOf("previousDrawFramebuffer = OculusRenderSystem.getDrawFramebufferBinding();",
                saveReadFramebuffer);
        }
        int bindShadowFramebuffer = body.indexOf(expectedBind, saveDrawFramebuffer);
        int restoreFramebuffer = body.indexOf(
            "OculusRenderSystem.restoreFramebufferBindings(previousFramebuffer, previousReadFramebuffer,",
            bindShadowFramebuffer);
        if (restoreFramebuffer < 0) {
            restoreFramebuffer = body.indexOf(
                "OculusRenderSystem.restoreFramebufferBindings(capturedPreviousFramebuffer,",
                bindShadowFramebuffer);
        }

        assertTrue(context + " must save the incoming combined framebuffer binding", saveFramebuffer >= 0);
        assertTrue(context + " must save the incoming read framebuffer binding",
            saveReadFramebuffer > saveFramebuffer);
        assertTrue(context + " must save the incoming draw framebuffer binding",
            saveDrawFramebuffer > saveReadFramebuffer);
        assertTrue(context + " must bind the shadow framebuffer after saving incoming bindings",
            bindShadowFramebuffer > saveDrawFramebuffer);
        assertTrue(context + " must restore read/draw framebuffer bindings in cleanup",
            restoreFramebuffer > bindShadowFramebuffer);
    }
}
