package net.oculus.pipeline.shadow;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ShadowRendererBytecodeTest {
    private static final String SHADOW_RENDERER = "net/oculus/pipeline/shadow/ShadowRenderer";
    private static final String SHADOW_MAP = "net/oculus/pipeline/shadow/ShadowMap";
    private static final String GL_FRAMEBUFFER = "com/github/zsoltmolnarr/oculus/client/render/gl/framebuffer/GlFramebuffer";
    private static final String RENDER_GLOBAL = "net/minecraft/client/renderer/RenderGlobal";
    private static final String RENDER_MANAGER = "net/minecraft/client/renderer/entity/RenderManager";
    private static final String OCULUS_RENDER_SYSTEM = "net/oculus/gl/OculusRenderSystem";
    private static final String TEXTURE_MANAGER = "net/minecraft/client/renderer/texture/TextureManager";
    private static final String RESOURCE_LOCATION = "net/minecraft/util/ResourceLocation";
    private static final String MINECRAFT_FORGE_CLIENT = "net/minecraftforge/client/MinecraftForgeClient";
    private static final String GBUFFER_PROGRAMS = "net/oculus/layer/GbufferPrograms";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL_STATE_MANAGER = "net/minecraft/client/renderer/GlStateManager";
    private static final String ENTITY = "net/minecraft/entity/Entity";
    private static final String I_CAMERA = "net/minecraft/client/renderer/culling/ICamera";
    private static final String BLOCK_RENDER_LAYER = "net/minecraft/util/BlockRenderLayer";
    private static final String SETUP_TERRAIN_DESC = "(L" + ENTITY + ";DL" + I_CAMERA + ";IZ)V";
    private static final String RENDER_SHADOW_BLOCK_LAYER_DESC =
        "(L" + RENDER_GLOBAL + ";L" + BLOCK_RENDER_LAYER + ";FL" + ENTITY + ";)V";
    private static final String LIST_DESC = "Ljava/util/List;";

    @Test
    public void renderShadowsForcesTerrainGraphDirtyBeforeShadowSetup() throws Exception {
        ClassReader reader = classReader(SHADOW_RENDERER);
        final boolean[] foundRenderShadows = {false};
        final int[] instructionIndex = {0};
        final int[] firstPreSetupDirtyCall = {-1};
        final int[] setupTerrainCall = {-1};
        final int[] dirtyCalls = {0};
        final int[] cleanupDirtyCalls = {0};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("renderShadows".equals(name)) {
                    assertTrue("Unexpected ShadowRenderer.renderShadows descriptor: " + descriptor,
                        ("(L" + RENDER_GLOBAL + ";L" + ENTITY + ";F)V").equals(descriptor));
                    foundRenderShadows[0] = true;

                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            instructionIndex[0]++;
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && RENDER_GLOBAL.equals(owner)
                                && "setDisplayListEntitiesDirty".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                dirtyCalls[0]++;
                                if (setupTerrainCall[0] < 0 && firstPreSetupDirtyCall[0] < 0) {
                                    firstPreSetupDirtyCall[0] = instructionIndex[0];
                                }
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && RENDER_GLOBAL.equals(owner)
                                && "setupTerrain".equals(methodName)
                                && SETUP_TERRAIN_DESC.equals(methodDescriptor)) {
                                setupTerrainCall[0] = instructionIndex[0];
                            }
                        }
                    };
                }

                if ("restoreShadowTerrainState".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && RENDER_GLOBAL.equals(owner)
                                && "setDisplayListEntitiesDirty".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                cleanupDirtyCalls[0]++;
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        assertTrue("ShadowRenderer.renderShadows(RenderGlobal, Entity, float) was not found", foundRenderShadows[0]);
        assertTrue("Shadow setup must call RenderGlobal.setupTerrain with the 1.12 terrain graph", setupTerrainCall[0] > 0);
        assertTrue("Shadow setup must force RenderGlobal.displayListEntitiesDirty before setupTerrain",
            firstPreSetupDirtyCall[0] > 0 && firstPreSetupDirtyCall[0] < setupTerrainCall[0]);
        assertTrue("Expected pre-shadow dirty mark in renderShadows", dirtyCalls[0] >= 1);
        assertTrue("Expected post-shadow dirty mark in guarded cleanup", cleanupDirtyCalls[0] > 0);
    }

    @Test
    public void renderShadowsUsesReferenceShadowTerrainLayerOrder() throws Exception {
        ClassReader reader = classReader(SHADOW_RENDERER);
        final boolean[] foundRenderShadows = {false};
        final String[] pendingLayer = {null};
        final List<String> layerOrder = new ArrayList<>();

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"renderShadows".equals(name)) {
                    return null;
                }

                assertTrue("Unexpected ShadowRenderer.renderShadows descriptor: " + descriptor,
                    ("(L" + RENDER_GLOBAL + ";L" + ENTITY + ";F)V").equals(descriptor));
                foundRenderShadows[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName,
                                               String fieldDescriptor) {
                        if (opcode == Opcodes.GETSTATIC
                            && BLOCK_RENDER_LAYER.equals(owner)
                            && ("L" + BLOCK_RENDER_LAYER + ";").equals(fieldDescriptor)) {
                            pendingLayer[0] = fieldName;
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC
                            && SHADOW_RENDERER.equals(owner)
                            && "renderShadowBlockLayer".equals(methodName)
                            && RENDER_SHADOW_BLOCK_LAYER_DESC.equals(methodDescriptor)) {
                            assertNotNull("Shadow block-layer render call must be preceded by a BlockRenderLayer",
                                pendingLayer[0]);
                            layerOrder.add(pendingLayer[0]);
                            pendingLayer[0] = null;
                        }
                    }
                };
            }
        }, 0);

        assertTrue("ShadowRenderer.renderShadows(RenderGlobal, Entity, float) was not found", foundRenderShadows[0]);
        assertEquals("Shadow terrain layer order must match the 1.16.5 ShadowRenderer reference",
            Arrays.asList("SOLID", "CUTOUT", "CUTOUT_MIPPED", "TRANSLUCENT"), layerOrder);
    }

    @Test
    public void renderShadowsBindsBlockAtlasOnDefaultTextureUnit() throws Exception {
        ClassReader reader = classReader(SHADOW_RENDERER);
        final boolean[] foundRenderShadows = {false};
        final int[] instructionIndex = {0};
        final int[] firstRestoreDefaultActiveTextureCall = {-1};
        final int[] firstBlockAtlasBindCall = {-1};
        final int[] secondRestoreDefaultActiveTextureCall = {-1};
        final int[] secondBlockAtlasBindCall = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"renderShadows".equals(name)) {
                    return null;
                }

                assertTrue("Unexpected ShadowRenderer.renderShadows descriptor: " + descriptor,
                    ("(L" + RENDER_GLOBAL + ";L" + ENTITY + ";F)V").equals(descriptor));
                foundRenderShadows[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "restoreDefaultActiveTexture".equals(methodName)
                            && "()V".equals(methodDescriptor)) {
                            if (firstRestoreDefaultActiveTextureCall[0] < 0) {
                                firstRestoreDefaultActiveTextureCall[0] = instructionIndex[0];
                            } else if (firstBlockAtlasBindCall[0] > firstRestoreDefaultActiveTextureCall[0]
                                && secondRestoreDefaultActiveTextureCall[0] < 0) {
                                secondRestoreDefaultActiveTextureCall[0] = instructionIndex[0];
                            }
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && TEXTURE_MANAGER.equals(owner)
                            && "bindTexture".equals(methodName)
                            && ("(L" + RESOURCE_LOCATION + ";)V").equals(methodDescriptor)
                            && firstRestoreDefaultActiveTextureCall[0] > 0) {
                            if (firstBlockAtlasBindCall[0] < 0) {
                                firstBlockAtlasBindCall[0] = instructionIndex[0];
                            } else if (secondRestoreDefaultActiveTextureCall[0] > firstBlockAtlasBindCall[0]
                                && secondBlockAtlasBindCall[0] < 0) {
                                secondBlockAtlasBindCall[0] = instructionIndex[0];
                            }
                        }
	                    }
	                };
            }
        }, 0);

        assertTrue("ShadowRenderer.renderShadows(RenderGlobal, Entity, float) was not found", foundRenderShadows[0]);
        assertTrue("Shadow terrain must restore the default texture unit before binding the block atlas",
            firstRestoreDefaultActiveTextureCall[0] > 0);
        assertTrue("Shadow terrain block atlas must be bound after restoring the default texture unit",
            firstBlockAtlasBindCall[0] > firstRestoreDefaultActiveTextureCall[0]);
        assertTrue("Translucent shadow terrain must restore the default texture unit after entity/depth-copy work",
            secondRestoreDefaultActiveTextureCall[0] > firstBlockAtlasBindCall[0]);
        assertTrue("Translucent shadow terrain must rebind the block atlas after restoring the default texture unit",
            secondBlockAtlasBindCall[0] > secondRestoreDefaultActiveTextureCall[0]);
    }

    @Test
    public void prepareRenderTargetsStartsOnDefaultTextureUnit() throws Exception {
        ClassReader reader = classReader(SHADOW_RENDERER);
        final boolean[] foundPrepareRenderTargets = {false};
        final int[] instructionIndex = {0};
        final int[] restoreDefaultActiveTextureCall = {-1};
        final int[] getDepthSourceFramebufferCall = {-1};
        final int[] firstFramebufferBindCall = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"prepareRenderTargets".equals(name)) {
                    return null;
                }

                assertTrue("Unexpected ShadowRenderer.prepareRenderTargets descriptor: " + descriptor,
                    "()V".equals(descriptor));
                foundPrepareRenderTargets[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "restoreDefaultActiveTexture".equals(methodName)
                            && "()V".equals(methodDescriptor)) {
                            restoreDefaultActiveTextureCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && SHADOW_MAP.equals(owner)
                            && "getDepthSourceFramebuffer".equals(methodName)
                            && ("()L" + GL_FRAMEBUFFER + ";").equals(methodDescriptor)) {
                            getDepthSourceFramebufferCall[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && GL_FRAMEBUFFER.equals(owner)
                            && "bind".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && firstFramebufferBindCall[0] < 0) {
                            firstFramebufferBindCall[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, 0);

        assertTrue("ShadowRenderer.prepareRenderTargets() was not found", foundPrepareRenderTargets[0]);
        assertTrue("Shadow target preparation must restore the default texture unit before framebuffer work",
            restoreDefaultActiveTextureCall[0] > 0);
        assertTrue("Shadow target preparation must fetch the owned depth-source framebuffer after setting texture unit 0",
            getDepthSourceFramebufferCall[0] > restoreDefaultActiveTextureCall[0]);
        assertTrue("Shadow target preparation must bind the owned depth-source framebuffer",
            firstFramebufferBindCall[0] > getDepthSourceFramebufferCall[0]);
    }

    @Test
    public void renderShadowsRestoresLegacyDepthStateAfterShadowPass() throws Exception {
        ClassReader reader = classReader(SHADOW_RENDERER);
        final boolean[] foundRenderShadows = {false};
        final boolean[] foundRestoreShadowDrawState = {false};
        final boolean[] foundRestoreDepthState = {false};
        final int[] instructionIndex = {0};
        final int[] drawStateInstructionIndex = {0};
        final int[] depthStateInstructionIndex = {0};
        final int[] firstSetupEnableDepth = {-1};
        final int[] glIsEnabledBeforeSetupDepth = {0};
        final int[] glGetBooleanBeforeSetupDepth = {0};
        final int[] glGetIntegerBeforeSetupDepth = {0};
        final int[] getFramebufferBeforeSetupDepth = {0};
        final int[] getReadFramebufferBeforeSetupDepth = {0};
        final int[] getDrawFramebufferBeforeSetupDepth = {0};
        final int[] generateMipmapsCall = {-1};
        final int[] restoreAfterShadowRenderCall = {-1};
        final int[] restoreDepthStateCall = {-1};
        final int[] restoreLightingStateCall = {-1};
        final int[] restoreShadowBindingsCall = {-1};
        final int[] restoreDepthEnableOrDisable = {-1};
        final int[] restoreDepthFunc = {-1};
        final int[] restoreDepthMask = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("renderShadows".equals(name)) {
                    assertTrue("Unexpected ShadowRenderer.renderShadows descriptor: " + descriptor,
                        ("(L" + RENDER_GLOBAL + ";L" + ENTITY + ";F)V").equals(descriptor));
                    foundRenderShadows[0] = true;

                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            instructionIndex[0]++;
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "enableDepth".equals(methodName)
                                && "()V".equals(methodDescriptor)
                                && firstSetupEnableDepth[0] < 0) {
                                firstSetupEnableDepth[0] = instructionIndex[0];
                            }
                            if (firstSetupEnableDepth[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && GL11.equals(owner)
                                && "glIsEnabled".equals(methodName)
                                && "(I)Z".equals(methodDescriptor)) {
                                glIsEnabledBeforeSetupDepth[0]++;
                            }
                            if (firstSetupEnableDepth[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && GL11.equals(owner)
                                && "glGetBoolean".equals(methodName)
                                && "(I)Z".equals(methodDescriptor)) {
                                glGetBooleanBeforeSetupDepth[0]++;
                            }
                            if (firstSetupEnableDepth[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && GL11.equals(owner)
                                && "glGetInteger".equals(methodName)
                                && "(I)I".equals(methodDescriptor)) {
                                glGetIntegerBeforeSetupDepth[0]++;
                            }
                            if (firstSetupEnableDepth[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && OCULUS_RENDER_SYSTEM.equals(owner)
                                && "getFramebufferBinding".equals(methodName)
                                && "()I".equals(methodDescriptor)) {
                                getFramebufferBeforeSetupDepth[0]++;
                            }
                            if (firstSetupEnableDepth[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && OCULUS_RENDER_SYSTEM.equals(owner)
                                && "getReadFramebufferBinding".equals(methodName)
                                && "()I".equals(methodDescriptor)) {
                                getReadFramebufferBeforeSetupDepth[0]++;
                            }
                            if (firstSetupEnableDepth[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && OCULUS_RENDER_SYSTEM.equals(owner)
                                && "getDrawFramebufferBinding".equals(methodName)
                                && "()I".equals(methodDescriptor)) {
                                getDrawFramebufferBeforeSetupDepth[0]++;
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && SHADOW_MAP.equals(owner)
                                && "generateMipmaps".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                generateMipmapsCall[0] = instructionIndex[0];
                            }
                            if (generateMipmapsCall[0] > 0
                                && opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreAfterShadowRender".equals(methodName)) {
                                restoreAfterShadowRenderCall[0] = instructionIndex[0];
                            }
                        }
                    };
                }

                if ("restoreShadowDrawState".equals(name)) {
                    foundRestoreShadowDrawState[0] = true;
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            drawStateInstructionIndex[0]++;
                            if (opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreDepthState".equals(methodName)) {
                                restoreDepthStateCall[0] = drawStateInstructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreLightingState".equals(methodName)) {
                                restoreLightingStateCall[0] = drawStateInstructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreShadowBindings".equals(methodName)) {
                                restoreShadowBindingsCall[0] = drawStateInstructionIndex[0];
                            }
                        }
                    };
                }

                if ("restoreDepthState".equals(name)) {
                    foundRestoreDepthState[0] = true;
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            depthStateInstructionIndex[0]++;
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && ("enableDepth".equals(methodName) || "disableDepth".equals(methodName))
                                && "()V".equals(methodDescriptor)) {
                                restoreDepthEnableOrDisable[0] = depthStateInstructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "depthFunc".equals(methodName)
                                && "(I)V".equals(methodDescriptor)) {
                                restoreDepthFunc[0] = depthStateInstructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "depthMask".equals(methodName)
                                && "(Z)V".equals(methodDescriptor)) {
                                restoreDepthMask[0] = depthStateInstructionIndex[0];
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        assertTrue("ShadowRenderer.renderShadows(RenderGlobal, Entity, float) was not found", foundRenderShadows[0]);
        assertTrue("Shadow pass must save cull, blend, and depth-test enablement before mutating depth state",
            glIsEnabledBeforeSetupDepth[0] >= 3);
        assertTrue("Shadow pass must save the previous depth write mask before mutating depth state",
            glGetBooleanBeforeSetupDepth[0] > 0);
        assertTrue("Shadow pass must save the previous depth func before forcing the shadow depth func",
            glGetIntegerBeforeSetupDepth[0] > 0);
        assertTrue("Shadow pass must save the incoming combined framebuffer binding before shadow rendering",
            getFramebufferBeforeSetupDepth[0] > 0);
        assertTrue("Shadow pass must save the incoming read framebuffer binding before shadow rendering",
            getReadFramebufferBeforeSetupDepth[0] > 0);
        assertTrue("Shadow pass must save the incoming draw framebuffer binding before shadow rendering",
            getDrawFramebufferBeforeSetupDepth[0] > 0);
        assertTrue("Shadow pass must generate shadow mipmaps before final GL-state restore",
            generateMipmapsCall[0] > firstSetupEnableDepth[0]);
        assertTrue("Shadow pass must enter guarded cleanup after generating shadow mipmaps",
            restoreAfterShadowRenderCall[0] > generateMipmapsCall[0]);
        assertTrue("ShadowRenderer.restoreShadowDrawState(...) was not found", foundRestoreShadowDrawState[0]);
        assertTrue("ShadowRenderer.restoreDepthState(...) was not found", foundRestoreDepthState[0]);
        assertTrue("Shadow draw cleanup must restore depth before lighting and framebuffer state",
            restoreDepthStateCall[0] > 0
                && restoreLightingStateCall[0] > restoreDepthStateCall[0]
                && restoreShadowBindingsCall[0] > restoreLightingStateCall[0]);
        assertTrue("Shadow pass must restore depth-test enablement after shadow work",
            restoreDepthEnableOrDisable[0] > 0);
        assertTrue("Shadow pass must restore the previous depth func after shadow work",
            restoreDepthFunc[0] > restoreDepthEnableOrDisable[0]);
        assertTrue("Shadow pass must restore the previous depth write mask after shadow work",
            restoreDepthMask[0] > restoreDepthFunc[0]);
    }

    @Test
    public void renderShadowsRestoresLegacyAlphaStateAfterShadowPass() throws Exception {
        ClassReader reader = classReader(SHADOW_RENDERER);
        final boolean[] foundRenderShadows = {false};
        final boolean[] foundRestoreAlphaState = {false};
        final int[] instructionIndex = {0};
        final int[] drawStateInstructionIndex = {0};
        final int[] firstAlphaFuncMutation = {-1};
        final int[] glGetFloatBeforeAlphaMutation = {0};
        final int[] generateMipmapsCall = {-1};
        final int[] restoreAfterShadowRenderCall = {-1};
        final int[] restoreAlphaStateCall = {-1};
        final int[] restoreDepthStateCall = {-1};
        final boolean[] restoreHelperEnablesAlpha = {false};
        final boolean[] restoreHelperDisablesAlpha = {false};
        final boolean[] restoreHelperRestoresAlphaFunc = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("renderShadows".equals(name)) {
                    assertTrue("Unexpected ShadowRenderer.renderShadows descriptor: " + descriptor,
                        ("(L" + RENDER_GLOBAL + ";L" + ENTITY + ";F)V").equals(descriptor));
                    foundRenderShadows[0] = true;

                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            instructionIndex[0]++;
                            if (firstAlphaFuncMutation[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && GL11.equals(owner)
                                && "glGetFloat".equals(methodName)
                                && "(I)F".equals(methodDescriptor)) {
                                glGetFloatBeforeAlphaMutation[0]++;
                            }
                            if (firstAlphaFuncMutation[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "alphaFunc".equals(methodName)
                                && "(IF)V".equals(methodDescriptor)) {
                                firstAlphaFuncMutation[0] = instructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && SHADOW_MAP.equals(owner)
                                && "generateMipmaps".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                generateMipmapsCall[0] = instructionIndex[0];
                            }
                            if (generateMipmapsCall[0] > 0
                                && opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreAfterShadowRender".equals(methodName)) {
                                restoreAfterShadowRenderCall[0] = instructionIndex[0];
                            }
                        }
                    };
                }

                if ("restoreShadowDrawState".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            drawStateInstructionIndex[0]++;
                            if (opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreAlphaState".equals(methodName)
                                && "(ZIF)V".equals(methodDescriptor)) {
                                restoreAlphaStateCall[0] = drawStateInstructionIndex[0];
                            }
                            if (restoreAlphaStateCall[0] > 0
                                && opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreDepthState".equals(methodName)) {
                                restoreDepthStateCall[0] = drawStateInstructionIndex[0];
                            }
                        }
                    };
                }

                if ("restoreAlphaState".equals(name)) {
                    assertTrue("Unexpected ShadowRenderer.restoreAlphaState descriptor: " + descriptor,
                        "(ZIF)V".equals(descriptor));
                    foundRestoreAlphaState[0] = true;

                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "enableAlpha".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                restoreHelperEnablesAlpha[0] = true;
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "disableAlpha".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                restoreHelperDisablesAlpha[0] = true;
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "alphaFunc".equals(methodName)
                                && "(IF)V".equals(methodDescriptor)) {
                                restoreHelperRestoresAlphaFunc[0] = true;
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        assertTrue("ShadowRenderer.renderShadows(RenderGlobal, Entity, float) was not found", foundRenderShadows[0]);
        assertTrue("Shadow pass must save the previous alpha reference before mutating alpha state",
            glGetFloatBeforeAlphaMutation[0] > 0 && firstAlphaFuncMutation[0] > 0);
        assertTrue("Shadow pass must generate shadow mipmaps before final alpha-state restore",
            generateMipmapsCall[0] > firstAlphaFuncMutation[0]);
        assertTrue("Shadow pass must enter guarded cleanup after generating shadow mipmaps",
            restoreAfterShadowRenderCall[0] > generateMipmapsCall[0]);
        assertTrue("Shadow pass must restore alpha state after shadow work",
            restoreAlphaStateCall[0] > 0);
        assertTrue("Shadow pass must restore alpha state before guarded depth-state cleanup",
            restoreDepthStateCall[0] > restoreAlphaStateCall[0]);
        assertTrue("ShadowRenderer.restoreAlphaState(boolean, int, float) was not found", foundRestoreAlphaState[0]);
        assertTrue("Alpha restore helper must restore enabled alpha-test state", restoreHelperEnablesAlpha[0]);
        assertTrue("Alpha restore helper must restore disabled alpha-test state", restoreHelperDisablesAlpha[0]);
        assertTrue("Alpha restore helper must restore the previous alpha func/reference",
            restoreHelperRestoresAlphaFunc[0]);
    }

    @Test
    public void renderShadowsRestoresLegacyBlendFactorsAfterShadowPass() throws Exception {
        ClassReader reader = classReader(SHADOW_RENDERER);
        final boolean[] foundRenderShadows = {false};
        final boolean[] foundRestoreBlendState = {false};
        final int[] instructionIndex = {0};
        final int[] drawStateInstructionIndex = {0};
        final int[] firstBlendFuncMutation = {-1};
        final int[] glGetIntegerBeforeBlendMutation = {0};
        final int[] generateMipmapsCall = {-1};
        final int[] restoreAfterShadowRenderCall = {-1};
        final int[] restoreBlendStateCall = {-1};
        final int[] restoreAlphaStateCall = {-1};
        final boolean[] restoreHelperEnablesBlend = {false};
        final boolean[] restoreHelperDisablesBlend = {false};
        final boolean[] restoreHelperRestoresBlendFunc = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("renderShadows".equals(name)) {
                    assertTrue("Unexpected ShadowRenderer.renderShadows descriptor: " + descriptor,
                        ("(L" + RENDER_GLOBAL + ";L" + ENTITY + ";F)V").equals(descriptor));
                    foundRenderShadows[0] = true;

                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            instructionIndex[0]++;
                            if (firstBlendFuncMutation[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && GL11.equals(owner)
                                && "glGetInteger".equals(methodName)
                                && "(I)I".equals(methodDescriptor)) {
                                glGetIntegerBeforeBlendMutation[0]++;
                            }
                            if (firstBlendFuncMutation[0] < 0
                                && opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "tryBlendFuncSeparate".equals(methodName)
                                && "(IIII)V".equals(methodDescriptor)) {
                                firstBlendFuncMutation[0] = instructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && SHADOW_MAP.equals(owner)
                                && "generateMipmaps".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                generateMipmapsCall[0] = instructionIndex[0];
                            }
                            if (generateMipmapsCall[0] > 0
                                && opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreAfterShadowRender".equals(methodName)) {
                                restoreAfterShadowRenderCall[0] = instructionIndex[0];
                            }
                        }
                    };
                }

                if ("restoreShadowDrawState".equals(name)) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            drawStateInstructionIndex[0]++;
                            if (opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreBlendState".equals(methodName)
                                && "(ZIIII)V".equals(methodDescriptor)) {
                                restoreBlendStateCall[0] = drawStateInstructionIndex[0];
                            }
                            if (restoreBlendStateCall[0] > 0
                                && opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "restoreAlphaState".equals(methodName)
                                && "(ZIF)V".equals(methodDescriptor)) {
                                restoreAlphaStateCall[0] = drawStateInstructionIndex[0];
                            }
                        }
                    };
                }

                if ("restoreBlendState".equals(name)) {
                    assertTrue("Unexpected ShadowRenderer.restoreBlendState descriptor: " + descriptor,
                        "(ZIIII)V".equals(descriptor));
                    foundRestoreBlendState[0] = true;

                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "enableBlend".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                restoreHelperEnablesBlend[0] = true;
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "disableBlend".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                restoreHelperDisablesBlend[0] = true;
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && GL_STATE_MANAGER.equals(owner)
                                && "tryBlendFuncSeparate".equals(methodName)
                                && "(IIII)V".equals(methodDescriptor)) {
                                restoreHelperRestoresBlendFunc[0] = true;
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        assertTrue("ShadowRenderer.renderShadows(RenderGlobal, Entity, float) was not found", foundRenderShadows[0]);
        assertTrue("Shadow pass must save the previous blend factors before mutating blend state",
            glGetIntegerBeforeBlendMutation[0] >= 4 && firstBlendFuncMutation[0] > 0);
        assertTrue("Shadow pass must generate shadow mipmaps before final blend-state restore",
            generateMipmapsCall[0] > firstBlendFuncMutation[0]);
        assertTrue("Shadow pass must enter guarded cleanup after generating shadow mipmaps",
            restoreAfterShadowRenderCall[0] > generateMipmapsCall[0]);
        assertTrue("Shadow pass must restore blend factors after shadow work",
            restoreBlendStateCall[0] > 0);
        assertTrue("Shadow pass must restore blend state before alpha state",
            restoreAlphaStateCall[0] > restoreBlendStateCall[0]);
        assertTrue("ShadowRenderer.restoreBlendState(boolean, int, int, int, int) was not found",
            foundRestoreBlendState[0]);
        assertTrue("Blend restore helper must restore enabled blend state", restoreHelperEnablesBlend[0]);
        assertTrue("Blend restore helper must restore disabled blend state", restoreHelperDisablesBlend[0]);
        assertTrue("Blend restore helper must restore previous blend factors", restoreHelperRestoresBlendFunc[0]);
    }

    @Test
    public void directPlayerShadowHelperUsesRenderManagerAndReferenceEntityOrder() throws Exception {
        ClassReader reader = classReader(SHADOW_RENDERER);
        final boolean[] foundDirectPlayerHelper = {false};
        final boolean[] foundEntityRenderHelper = {false};
        final int[] instructionIndex = {0};
        final int[] shouldRenderCall = {-1};
        final int[] cacheActiveRenderInfoCall = {-1};
        final int[] setRenderPositionCall = {-1};
        final int[] beginEntitiesCall = {-1};
        final int[] getPassengersCall = {-1};
        final int[] getRidingEntityCall = {-1};
        final List<Integer> renderHelperCalls = new ArrayList<>();
        final int[] getRenderPassCall = {-1};
        final int[] renderEntityStaticCall = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("renderShadowPlayerEntity".equals(name)) {
                    assertTrue("Unexpected renderShadowPlayerEntity descriptor: " + descriptor,
                        ("(Lnet/minecraft/client/Minecraft;L" + I_CAMERA + ";L" + ENTITY + ";DDDF)V")
                            .equals(descriptor));
                    foundDirectPlayerHelper[0] = true;
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            instructionIndex[0]++;
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && RENDER_MANAGER.equals(owner)
                                && "shouldRender".equals(methodName)
                                && ("(L" + ENTITY + ";L" + I_CAMERA + ";DDD)Z").equals(methodDescriptor)) {
                                shouldRenderCall[0] = instructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && RENDER_MANAGER.equals(owner)
                                && "cacheActiveRenderInfo".equals(methodName)) {
                                cacheActiveRenderInfoCall[0] = instructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && RENDER_MANAGER.equals(owner)
                                && "setRenderPosition".equals(methodName)
                                && "(DDD)V".equals(methodDescriptor)) {
                                setRenderPositionCall[0] = instructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && GBUFFER_PROGRAMS.equals(owner)
                                && "beginEntities".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                beginEntitiesCall[0] = instructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && ENTITY.equals(owner)
                                && "getPassengers".equals(methodName)
                                && "()Ljava/util/List;".equals(methodDescriptor)) {
                                getPassengersCall[0] = instructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && ENTITY.equals(owner)
                                && "getRidingEntity".equals(methodName)
                                && ("()L" + ENTITY + ";").equals(methodDescriptor)) {
                                getRidingEntityCall[0] = instructionIndex[0];
                            }
                            if (opcode == Opcodes.INVOKESTATIC
                                && SHADOW_RENDERER.equals(owner)
                                && "renderShadowEntityIfRenderable".equals(methodName)
                                && ("(L" + RENDER_MANAGER + ";L" + ENTITY + ";F)V").equals(methodDescriptor)) {
                                renderHelperCalls.add(instructionIndex[0]);
                            }
                        }
                    };
                }

                if ("renderShadowEntityIfRenderable".equals(name)) {
                    assertTrue("Unexpected renderShadowEntityIfRenderable descriptor: " + descriptor,
                        ("(L" + RENDER_MANAGER + ";L" + ENTITY + ";F)V").equals(descriptor));
                    foundEntityRenderHelper[0] = true;
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKESTATIC
                                && MINECRAFT_FORGE_CLIENT.equals(owner)
                                && "getRenderPass".equals(methodName)
                                && "()I".equals(methodDescriptor)) {
                                getRenderPassCall[0]++;
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && RENDER_MANAGER.equals(owner)
                                && "renderEntityStatic".equals(methodName)
                                && ("(L" + ENTITY + ";FZ)V").equals(methodDescriptor)) {
                                renderEntityStaticCall[0]++;
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        assertTrue("ShadowRenderer.renderShadowPlayerEntity(...) was not found", foundDirectPlayerHelper[0]);
        assertTrue("Direct player shadows must cull via RenderManager before preparing render state",
            shouldRenderCall[0] > 0);
        assertTrue("Direct player shadows must prepare RenderManager state before entering entity phase",
            cacheActiveRenderInfoCall[0] > shouldRenderCall[0]
                && setRenderPositionCall[0] > cacheActiveRenderInfoCall[0]
                && beginEntitiesCall[0] > setRenderPositionCall[0]);
        assertEquals("Direct player shadows must render passenger, vehicle, then player", 3,
            renderHelperCalls.size());
        assertTrue("Passenger render must follow passenger enumeration",
            getPassengersCall[0] > beginEntitiesCall[0] && renderHelperCalls.get(0) > getPassengersCall[0]);
        assertTrue("Vehicle render must follow vehicle lookup and precede player render",
            getRidingEntityCall[0] > renderHelperCalls.get(0)
                && renderHelperCalls.get(1) > getRidingEntityCall[0]
                && renderHelperCalls.get(2) > renderHelperCalls.get(1));
        assertTrue("ShadowRenderer.renderShadowEntityIfRenderable(...) was not found", foundEntityRenderHelper[0]);
        assertTrue("1.12 direct shadow entity renders must honor the active Forge render pass",
            getRenderPassCall[0] >= 0);
        assertTrue("Direct shadow entity helper must call RenderManager.renderEntityStatic",
            renderEntityStaticCall[0] >= 0);
    }

    @Test
    public void renderGlobalStillRebuildsTerrainGraphFromDisplayListDirtyFlag() throws Exception {
        ClassReader reader = classReader(RENDER_GLOBAL);
        final boolean[] hasDisplayListDirtyField = {false};
        final boolean[] hasRenderInfosField = {false};
        final boolean[] foundSetDisplayListEntitiesDirty = {false};
        final boolean[] foundSetupTerrain = {false};
        final int[] dirtyFlagStoresInSetter = {0};
        final int[] dirtyFlagReadsInSetup = {0};
        final int[] dirtyFlagStoresInSetup = {0};
        final int[] renderInfosStoresInSetup = {0};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                if ("displayListEntitiesDirty".equals(name) && "Z".equals(descriptor)) {
                    hasDisplayListDirtyField[0] = true;
                }
                if ("renderInfos".equals(name) && LIST_DESC.equals(descriptor)) {
                    hasRenderInfosField[0] = true;
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("setDisplayListEntitiesDirty".equals(name)) {
                    assertTrue("Unexpected RenderGlobal.setDisplayListEntitiesDirty descriptor: " + descriptor,
                        "()V".equals(descriptor));
                    foundSetDisplayListEntitiesDirty[0] = true;
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fieldName,
                                                   String fieldDescriptor) {
                            if (opcode == Opcodes.PUTFIELD
                                && RENDER_GLOBAL.equals(owner)
                                && "displayListEntitiesDirty".equals(fieldName)
                                && "Z".equals(fieldDescriptor)) {
                                dirtyFlagStoresInSetter[0]++;
                            }
                        }
                    };
                }

                if ("setupTerrain".equals(name)) {
                    assertTrue("Unexpected RenderGlobal.setupTerrain descriptor: " + descriptor,
                        SETUP_TERRAIN_DESC.equals(descriptor));
                    foundSetupTerrain[0] = true;
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fieldName,
                                                   String fieldDescriptor) {
                            if (!RENDER_GLOBAL.equals(owner)) {
                                return;
                            }
                            if (opcode == Opcodes.GETFIELD
                                && "displayListEntitiesDirty".equals(fieldName)
                                && "Z".equals(fieldDescriptor)) {
                                dirtyFlagReadsInSetup[0]++;
                            }
                            if (opcode == Opcodes.PUTFIELD
                                && "displayListEntitiesDirty".equals(fieldName)
                                && "Z".equals(fieldDescriptor)) {
                                dirtyFlagStoresInSetup[0]++;
                            }
                            if (opcode == Opcodes.PUTFIELD
                                && "renderInfos".equals(fieldName)
                                && LIST_DESC.equals(fieldDescriptor)) {
                                renderInfosStoresInSetup[0]++;
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        assertTrue("RenderGlobal.displayListEntitiesDirty field changed", hasDisplayListDirtyField[0]);
        assertTrue("RenderGlobal.renderInfos field changed", hasRenderInfosField[0]);
        assertTrue("RenderGlobal.setDisplayListEntitiesDirty() was not found", foundSetDisplayListEntitiesDirty[0]);
        assertTrue("RenderGlobal.setupTerrain(...) was not found", foundSetupTerrain[0]);
        assertTrue("setDisplayListEntitiesDirty() must write displayListEntitiesDirty",
            dirtyFlagStoresInSetter[0] > 0);
        assertTrue("setupTerrain(...) must read displayListEntitiesDirty",
            dirtyFlagReadsInSetup[0] > 0);
        assertTrue("setupTerrain(...) must update displayListEntitiesDirty",
            dirtyFlagStoresInSetup[0] > 0);
        assertTrue("setupTerrain(...) must rebuild renderInfos from terrain visibility traversal",
            renderInfosStoresInSetup[0] > 0);
    }

    private static ClassReader classReader(String internalName) throws IOException {
        return new ClassReader(readClassBytes(internalName));
    }

    private static byte[] readClassBytes(String internalName) throws IOException {
        String resource = internalName + ".class";
        InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        assertNotNull("Missing class resource on test classpath: " + resource, stream);

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            stream.close();
        }
    }
}
