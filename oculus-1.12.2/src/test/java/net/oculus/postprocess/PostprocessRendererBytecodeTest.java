package net.oculus.postprocess;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class PostprocessRendererBytecodeTest {
    private static final String COMPOSITE_RENDERER = "net/oculus/postprocess/CompositeRenderer";
    private static final String FINAL_PASS_RENDERER = "net/oculus/postprocess/FinalPassRenderer";
    private static final String FULL_SCREEN_QUAD_RENDERER = "net/oculus/postprocess/FullScreenQuadRenderer";
    private static final String OCULUS_RENDER_SYSTEM = "net/oculus/gl/OculusRenderSystem";
    private static final String PROGRAM = "net/oculus/gl/program/Program";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL_STATE_MANAGER = "net/minecraft/client/renderer/GlStateManager";

    @Test
    public void compositeRenderAllDispatchesComputesBeforeBarrierUnbindAndRasterPass() throws Exception {
        ClassReader reader = classReader(COMPOSITE_RENDERER);
        final boolean[] foundRenderAll = {false};
        final int[] instructionIndex = {0};
        final int[] disableBlend = {-1};
        final int[] disableAlpha = {-1};
        final int[] forceColorMask = {-1};
        final int[] quadBegin = {-1};
        final int[] dispatchComputes = {-1};
        final int[] memoryBarrier = {-1};
        final int[] programUnbind = {-1};
        final int[] renderPass = {-1};
        final int[] quadEnd = {-1};
        final int[] restoreAfterRenderAll = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"renderAll".equals(name)) {
                    return null;
                }

                assertTrue("Unexpected CompositeRenderer.renderAll descriptor: " + descriptor,
                    "()V".equals(descriptor));
                foundRenderAll[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL_STATE_MANAGER.equals(owner)
                            && "disableBlend".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && disableBlend[0] < 0) {
                            disableBlend[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL_STATE_MANAGER.equals(owner)
                            && "disableAlpha".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && disableAlpha[0] < 0) {
                            disableAlpha[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL_STATE_MANAGER.equals(owner)
                            && "colorMask".equals(methodName)
                            && "(ZZZZ)V".equals(methodDescriptor)
                            && forceColorMask[0] < 0) {
                            forceColorMask[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && FULL_SCREEN_QUAD_RENDERER.equals(owner)
                            && "begin".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && quadBegin[0] < 0) {
                            quadBegin[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                            && COMPOSITE_RENDERER.equals(owner)
                            && "dispatchComputes".equals(methodName)
                            && dispatchComputes[0] < 0) {
                            dispatchComputes[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "memoryBarrier".equals(methodName)
                            && "(I)V".equals(methodDescriptor)
                            && memoryBarrier[0] < 0) {
                            memoryBarrier[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && PROGRAM.equals(owner)
                            && "unbind".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && programUnbind[0] < 0) {
                            programUnbind[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                            && COMPOSITE_RENDERER.equals(owner)
                            && "renderPass".equals(methodName)
                            && renderPass[0] < 0) {
                            renderPass[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && FULL_SCREEN_QUAD_RENDERER.equals(owner)
                            && "end".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && quadEnd[0] < 0) {
                            quadEnd[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                            && COMPOSITE_RENDERER.equals(owner)
                            && "restoreAfterRenderAll".equals(methodName)
                            && restoreAfterRenderAll[0] < 0) {
                            restoreAfterRenderAll[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, 0);

        assertTrue("CompositeRenderer.renderAll() was not found", foundRenderAll[0]);
        assertTrue("Composite render must disable blend before alpha and color-mask setup",
            disableBlend[0] > 0 && disableAlpha[0] > disableBlend[0] && forceColorMask[0] > disableAlpha[0]);
        assertTrue("Composite render must begin the fullscreen quad before compute dispatch",
            quadBegin[0] > forceColorMask[0] && dispatchComputes[0] > quadBegin[0]);
        assertTrue("Composite compute dispatch must be followed by the compute memory barrier",
            memoryBarrier[0] > dispatchComputes[0]);
        assertTrue("Composite compute work must unbind the GL program before raster fallback decisions",
            programUnbind[0] > memoryBarrier[0]);
        assertTrue("Composite raster passes must run only after compute barrier and Program.unbind",
            renderPass[0] > programUnbind[0]);
        assertTrue("Composite cleanup must end fullscreen rendering before restoring caller state",
            quadEnd[0] > renderPass[0] && restoreAfterRenderAll[0] > quadEnd[0]);
    }

    @Test
    public void finalPassRenderRunsSourceFaithfulOrderingBeforeCleanup() throws Exception {
        ClassReader reader = classReader(FINAL_PASS_RENDERER);
        final boolean[] foundRender = {false};
        final int[] instructionIndex = {0};
        final int[] depthMaskSave = {0};
        final int[] blendAlphaSaves = {0};
        final int[] activeTextureSave = {-1};
        final int[] colorMaskSave = {-1};
        final int[] forceDepthMaskFalse = {-1};
        final int[] disableBlend = {-1};
        final int[] disableAlpha = {-1};
        final int[] forceColorMask = {-1};
        final int[] dispatchComputes = {-1};
        final int[] memoryBarrier = {-1};
        final int[] programUse = {-1};
        final int[] renderQuad = {-1};
        final int[] unconditionalRestoreDefaultTexture = {-1};
        final int[] resetMipmaps = {-1};
        final int[] runSwapPasses = {-1};
        final int[] restoreAfterRender = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"render".equals(name)) {
                    return null;
                }

                assertTrue("Unexpected FinalPassRenderer.render descriptor: " + descriptor,
                    "()V".equals(descriptor));
                foundRender[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (forceDepthMaskFalse[0] < 0
                            && opcode == Opcodes.INVOKESTATIC
                            && GL11.equals(owner)
                            && "glGetBoolean".equals(methodName)
                            && "(I)Z".equals(methodDescriptor)) {
                            depthMaskSave[0]++;
                        }
                        if (forceDepthMaskFalse[0] < 0
                            && opcode == Opcodes.INVOKESTATIC
                            && GL11.equals(owner)
                            && "glIsEnabled".equals(methodName)
                            && "(I)Z".equals(methodDescriptor)) {
                            blendAlphaSaves[0]++;
                        }
                        if (forceDepthMaskFalse[0] < 0
                            && opcode == Opcodes.INVOKESTATIC
                            && GL11.equals(owner)
                            && "glGetInteger".equals(methodName)
                            && "(I)I".equals(methodDescriptor)) {
                            activeTextureSave[0] = instructionIndex[0];
                        }
                        if (forceDepthMaskFalse[0] < 0
                            && opcode == Opcodes.INVOKESTATIC
                            && GL11.equals(owner)
                            && "glGetBoolean".equals(methodName)
                            && "(ILjava/nio/ByteBuffer;)V".equals(methodDescriptor)) {
                            colorMaskSave[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL_STATE_MANAGER.equals(owner)
                            && "depthMask".equals(methodName)
                            && "(Z)V".equals(methodDescriptor)
                            && forceDepthMaskFalse[0] < 0) {
                            forceDepthMaskFalse[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL_STATE_MANAGER.equals(owner)
                            && "disableBlend".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && disableBlend[0] < 0) {
                            disableBlend[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL_STATE_MANAGER.equals(owner)
                            && "disableAlpha".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && disableAlpha[0] < 0) {
                            disableAlpha[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && GL_STATE_MANAGER.equals(owner)
                            && "colorMask".equals(methodName)
                            && "(ZZZZ)V".equals(methodDescriptor)
                            && forceColorMask[0] < 0) {
                            forceColorMask[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                            && FINAL_PASS_RENDERER.equals(owner)
                            && "dispatchComputes".equals(methodName)
                            && dispatchComputes[0] < 0) {
                            dispatchComputes[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "memoryBarrier".equals(methodName)
                            && "(I)V".equals(methodDescriptor)
                            && memoryBarrier[0] < 0) {
                            memoryBarrier[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && PROGRAM.equals(owner)
                            && "use".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && programUse[0] < 0) {
                            programUse[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && FULL_SCREEN_QUAD_RENDERER.equals(owner)
                            && "renderQuad".equals(methodName)
                            && "()V".equals(methodDescriptor)
                            && renderQuad[0] < 0) {
                            renderQuad[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESTATIC
                            && OCULUS_RENDER_SYSTEM.equals(owner)
                            && "restoreDefaultActiveTexture".equals(methodName)
                            && "()V".equals(methodDescriptor)) {
                            unconditionalRestoreDefaultTexture[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                            && FINAL_PASS_RENDERER.equals(owner)
                            && "resetRenderTargetMipmaps".equals(methodName)) {
                            resetMipmaps[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                            && FINAL_PASS_RENDERER.equals(owner)
                            && "runSwapPasses".equals(methodName)) {
                            runSwapPasses[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKESPECIAL
                            && FINAL_PASS_RENDERER.equals(owner)
                            && "restoreAfterRender".equals(methodName)
                            && restoreAfterRender[0] < 0) {
                            restoreAfterRender[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, 0);

        assertTrue("FinalPassRenderer.render() was not found", foundRender[0]);
        assertTrue("Final pass must save the previous depth write mask before forcing draw state",
            depthMaskSave[0] > 0);
        assertTrue("Final pass must save blend and alpha enablement before forcing draw state",
            blendAlphaSaves[0] >= 2);
        assertTrue("Final pass must save active texture and color mask before forcing draw state",
            activeTextureSave[0] > 0 && colorMaskSave[0] > activeTextureSave[0]);
        assertTrue("Final pass must force depth, blend, alpha, and color-mask state before rendering",
            forceDepthMaskFalse[0] > colorMaskSave[0]
                && disableBlend[0] > forceDepthMaskFalse[0]
                && disableAlpha[0] > disableBlend[0]
                && forceColorMask[0] > disableAlpha[0]);
        assertTrue("Final pass compute dispatch must precede the unconditional final-pass memory barrier",
            dispatchComputes[0] > forceColorMask[0] && memoryBarrier[0] > dispatchComputes[0]);
        assertTrue("Final pass shader draw must happen after the compute memory barrier",
            programUse[0] > memoryBarrier[0] && renderQuad[0] > programUse[0]);
        assertTrue("Final pass must restore texture unit 0 before mipmap reset and swap copies",
            unconditionalRestoreDefaultTexture[0] > renderQuad[0]
                && resetMipmaps[0] > unconditionalRestoreDefaultTexture[0]
                && runSwapPasses[0] > resetMipmaps[0]);
        assertTrue("Final pass cleanup must run after final render-target reset and swap copies",
            restoreAfterRender[0] > runSwapPasses[0]);
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
