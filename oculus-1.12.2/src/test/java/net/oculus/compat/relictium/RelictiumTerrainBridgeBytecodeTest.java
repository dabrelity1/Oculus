package net.oculus.compat.relictium;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class RelictiumTerrainBridgeBytecodeTest {
    private static final String CHUNK_RENDER_MANAGER =
        "me/jellysquid/mods/sodium/client/render/chunk/ChunkRenderManager";
    private static final String CHUNK_RENDER_REBUILD_TASK =
        "me/jellysquid/mods/sodium/client/render/chunk/tasks/ChunkRenderRebuildTask";
    private static final String CHUNK_BUILD_BUFFERS =
        "me/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildBuffers";
    private static final String SODIUM_WORLD_RENDERER =
        "me/jellysquid/mods/sodium/client/render/SodiumWorldRenderer";
    private static final String CHUNK_RENDER_BACKEND =
        "me/jellysquid/mods/sodium/client/render/chunk/ChunkRenderBackend";
    private static final String CHUNK_RENDER_SHADER_BACKEND =
        "me/jellysquid/mods/sodium/client/render/chunk/shader/ChunkRenderShaderBackend";
    private static final String CHUNK_RENDER_CONTAINER =
        "me/jellysquid/mods/sodium/client/render/chunk/ChunkRenderContainer";
    private static final String CHUNK_PROGRAM =
        "me/jellysquid/mods/sodium/client/render/chunk/shader/ChunkProgram";
    private static final String DEFAULT_MODEL_VERTEX_FORMATS =
        "me/jellysquid/mods/sodium/client/render/chunk/format/DefaultModelVertexFormats";
    private static final String BLOCK_RENDER_PASS =
        "me/jellysquid/mods/sodium/client/render/chunk/passes/BlockRenderPass";
    private static final String BLOCK_RENDER_PASS_MANAGER =
        "me/jellysquid/mods/sodium/client/render/chunk/passes/BlockRenderPassManager";
    private static final String SODIUM_GAME_OPTIONS =
        "me/jellysquid/mods/sodium/client/gui/SodiumGameOptions";
    private static final String FRUSTUM_EXTENDED =
        "me/jellysquid/mods/sodium/client/util/math/FrustumExtended";
    private static final String RENDER_DEVICE =
        "me/jellysquid/mods/sodium/client/gl/device/RenderDevice";
    private static final String CHUNK_VERTEX_TYPE =
        "me/jellysquid/mods/sodium/client/model/vertex/type/ChunkVertexType";
    private static final String BLOCK_RENDERER =
        "me/jellysquid/mods/sodium/client/render/pipeline/BlockRenderer";
    private static final String FLUID_RENDERER =
        "me/jellysquid/mods/sodium/client/render/pipeline/FluidRenderer";
    private static final String RESOURCE_LOCATION =
        "net/minecraft/util/ResourceLocation";
    private static final String SHADER_TYPE =
        "me/jellysquid/mods/sodium/client/gl/shader/ShaderType";
    private static final String SHADER_BINDING_POINTS =
        "me/jellysquid/mods/sodium/client/render/chunk/shader/ChunkShaderBindingPoints";
    private static final String FRUSTUM =
        "net/minecraft/client/renderer/culling/Frustum";
    private static final String BLOCK_RENDER_LAYER =
        "net/minecraft/util/BlockRenderLayer";

    private static final String CHUNK_PROGRAM_DESC = "L" + CHUNK_PROGRAM + ";";
    private static final String CHUNK_RENDER_MANAGER_DESC = "L" + CHUNK_RENDER_MANAGER + ";";
    private static final String CHUNK_VERTEX_TYPE_DESC = "L" + CHUNK_VERTEX_TYPE + ";";
    private static final String OBJECT_ARRAY_FIFO_QUEUE_DESC =
        "Lit/unimi/dsi/fastutil/objects/ObjectArrayFIFOQueue;";
    private static final String OBJECT_LIST_DESC = "Lit/unimi/dsi/fastutil/objects/ObjectList;";
    private static final String CHUNK_RENDER_LIST_ARRAY_DESC =
        "[Lme/jellysquid/mods/sodium/client/render/chunk/lists/ChunkRenderList;";

    @Test
    public void chunkRenderRebuildTaskInitializesBuffersBeforeTerrainRenderCalls() throws Exception {
        ClassReader reader = classReader(CHUNK_RENDER_REBUILD_TASK);
        final boolean[] foundPerformBuild = {false};
        final int[] instructionIndex = {0};
        final int[] initIndex = {-1};
        final int[] blockRenderIndex = {-1};
        final int[] fluidRenderIndex = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"performBuild".equals(name)) {
                    return null;
                }

                assertEquals("(Lme/jellysquid/mods/sodium/client/render/pipeline/context/ChunkRenderCacheLocal;"
                    + "L" + CHUNK_BUILD_BUFFERS + ";"
                    + "Lme/jellysquid/mods/sodium/client/util/task/CancellationSource;)"
                    + "Lme/jellysquid/mods/sodium/client/render/chunk/compile/ChunkBuildResult;",
                    descriptor);
                foundPerformBuild[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && CHUNK_BUILD_BUFFERS.equals(owner)
                            && "init".equals(methodName)
                            && "(Lme/jellysquid/mods/sodium/client/render/chunk/data/ChunkRenderData$Builder;)V"
                                .equals(methodDescriptor)) {
                            initIndex[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && BLOCK_RENDERER.equals(owner)
                            && "renderModel".equals(methodName)
                            && ("(Lnet/minecraft/world/IBlockAccess;"
                                + "Lnet/minecraft/block/state/IBlockState;"
                                + "Lnet/minecraft/util/math/BlockPos;"
                                + "Lnet/minecraft/client/renderer/block/model/IBakedModel;"
                                + "Lme/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuffers;"
                                + "ZJ)Z").equals(methodDescriptor)) {
                            blockRenderIndex[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && FLUID_RENDERER.equals(owner)
                            && "render".equals(methodName)
                            && ("(Lnet/minecraft/world/IBlockAccess;"
                                + "Lnet/minecraft/block/state/IBlockState;"
                                + "Lnet/minecraft/util/math/BlockPos;"
                                + "Lme/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuffers;)Z")
                                    .equals(methodDescriptor)) {
                            fluidRenderIndex[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, 0);

        assertTrue("Relictium ChunkRenderRebuildTask.performBuild descriptor changed", foundPerformBuild[0]);
        assertTrue("ChunkBuildBuffers.init must still run before block terrain rendering",
            initIndex[0] > 0 && initIndex[0] < blockRenderIndex[0]);
        assertTrue("ChunkBuildBuffers.init must still run before fluid terrain rendering",
            initIndex[0] > 0 && initIndex[0] < fluidRenderIndex[0]);
    }

    @Test
    public void chunkRenderManagerRenderLayerStillBracketsBackendRendering() throws Exception {
        ClassReader reader = classReader(CHUNK_RENDER_MANAGER);
        final boolean[] foundRenderLayer = {false};
        final int[] instructionIndex = {0};
        final int[] beginCalls = {0};
        final int[] beginIndex = {-1};
        final int[] renderCalls = {0};
        final int[] renderIndex = {-1};
        final int[] endCalls = {0};
        final int[] endIndex = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"renderLayer".equals(name)) {
                    return null;
                }

                assertEquals("(L" + BLOCK_RENDER_PASS + ";DDD)V", descriptor);
                foundRenderLayer[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKEINTERFACE
                            && CHUNK_RENDER_BACKEND.equals(owner)
                            && "begin".equals(methodName)
                            && "()V".equals(methodDescriptor)) {
                            beginCalls[0]++;
                            beginIndex[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEINTERFACE
                            && CHUNK_RENDER_BACKEND.equals(owner)
                            && "render".equals(methodName)
                            && ("(Lme/jellysquid/mods/sodium/client/gl/device/CommandList;"
                                + "Lme/jellysquid/mods/sodium/client/render/chunk/lists/ChunkRenderListIterator;"
                                + "Lme/jellysquid/mods/sodium/client/render/chunk/ChunkCameraContext;)V")
                                    .equals(methodDescriptor)) {
                            renderCalls[0]++;
                            renderIndex[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEINTERFACE
                            && CHUNK_RENDER_BACKEND.equals(owner)
                            && "end".equals(methodName)
                            && "()V".equals(methodDescriptor)) {
                            endCalls[0]++;
                            endIndex[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, 0);

        assertTrue("Relictium ChunkRenderManager.renderLayer descriptor changed", foundRenderLayer[0]);
        assertEquals("Expected one ChunkRenderBackend.begin() call for the pass redirect", 1, beginCalls[0]);
        assertEquals("Expected one ChunkRenderBackend.render(...) call for exception cleanup", 1, renderCalls[0]);
        assertEquals("Expected one ChunkRenderBackend.end() call for terrain scope cleanup", 1, endCalls[0]);
        assertTrue("ChunkRenderBackend.render(...) must run after begin()",
            beginIndex[0] > 0 && beginIndex[0] < renderIndex[0]);
        assertTrue("ChunkRenderBackend.end() must run after begin() in renderLayer",
            beginIndex[0] > 0 && beginIndex[0] < endIndex[0]);
        assertTrue("ChunkRenderBackend.end() must run after render(...) in renderLayer",
            renderIndex[0] > 0 && renderIndex[0] < endIndex[0]);
    }

    @Test
    public void chunkRenderShaderBackendBeginStillStoresActiveProgramBeforeBinding() throws Exception {
        ClassReader reader = classReader(CHUNK_RENDER_SHADER_BACKEND);
        final boolean[] hasActiveProgramField = {false};
        final boolean[] hasCreateShaders = {false};
        final boolean[] hasBegin = {false};
        final boolean[] hasEnd = {false};
        final boolean[] hasDelete = {false};
        final int[] instructionIndex = {0};
        final int[] activeProgramStores = {0};
        final int[] activeProgramStoreIndex = {-1};
        final int[] bindIndex = {-1};
        final int[] setupIndex = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                if ("activeProgram".equals(name) && CHUNK_PROGRAM_DESC.equals(descriptor)) {
                    hasActiveProgramField[0] = true;
                }
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("createShaders".equals(name)) {
                    assertEquals("(L" + RENDER_DEVICE + ";)V", descriptor);
                    hasCreateShaders[0] = true;
                    return null;
                }
                if ("end".equals(name)) {
                    assertEquals("()V", descriptor);
                    hasEnd[0] = true;
                    return null;
                }
                if ("delete".equals(name)) {
                    assertEquals("()V", descriptor);
                    hasDelete[0] = true;
                    return null;
                }
                if (!"begin".equals(name)) {
                    return null;
                }

                assertEquals("()V", descriptor);
                hasBegin[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName,
                                               String fieldDescriptor) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.PUTFIELD
                            && CHUNK_RENDER_SHADER_BACKEND.equals(owner)
                            && "activeProgram".equals(fieldName)
                            && CHUNK_PROGRAM_DESC.equals(fieldDescriptor)) {
                            activeProgramStores[0]++;
                            activeProgramStoreIndex[0] = instructionIndex[0];
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && CHUNK_PROGRAM.equals(owner)
                            && "bind".equals(methodName)
                            && "()V".equals(methodDescriptor)) {
                            bindIndex[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.INVOKEVIRTUAL
                            && CHUNK_PROGRAM.equals(owner)
                            && "setup".equals(methodName)
                            && "(FF)V".equals(methodDescriptor)) {
                            setupIndex[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, 0);

        assertTrue("Relictium activeProgram field changed", hasActiveProgramField[0]);
        assertTrue("Relictium createShaders(RenderDevice) changed", hasCreateShaders[0]);
        assertTrue("Relictium begin() changed", hasBegin[0]);
        assertTrue("Relictium end() changed", hasEnd[0]);
        assertTrue("Relictium delete() changed", hasDelete[0]);
        assertEquals("Expected one activeProgram write in begin()", 1, activeProgramStores[0]);
        assertTrue("activeProgram must be stored before ChunkProgram.bind()",
            activeProgramStoreIndex[0] > 0 && activeProgramStoreIndex[0] < bindIndex[0]);
        assertTrue("ChunkProgram.bind() must run before setup(float,float)",
            bindIndex[0] > 0 && bindIndex[0] < setupIndex[0]);
    }

    @Test
    public void chunkProgramAndPassApiStillMatchOverrideBridge() throws Exception {
        ClassReader programReader = classReader(CHUNK_PROGRAM);
        final Set<String> programMethods = new HashSet<>();

        programReader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                programMethods.add(name + descriptor);
                return null;
            }
        }, 0);

        assertTrue(programMethods.contains("<init>(L" + RENDER_DEVICE + ";L" + RESOURCE_LOCATION
            + ";ILjava/util/function/Function;)V"));
        assertTrue(programMethods.contains("setup(FF)V"));

        ClassReader passReader = classReader(BLOCK_RENDER_PASS);
        final boolean[] hasIsTranslucent = {false};
        passReader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("isTranslucent".equals(name) && "()Z".equals(descriptor)) {
                    hasIsTranslucent[0] = true;
                }
                return null;
            }
        }, 0);
        assertTrue("BlockRenderPass.isTranslucent() is required for pass selection", hasIsTranslucent[0]);
    }

    @Test
    public void relictiumShaderTypeStillHasNoGeometryStage() throws Exception {
        Class<?> shaderType = Class.forName(SHADER_TYPE.replace('/', '.'));
        Object[] constants = shaderType.getEnumConstants();
        assertNotNull(constants);

        for (Object constant : constants) {
            assertFalse("Relictium gained a GEOMETRY shader type; re-evaluate OculusRelictiumProgramLinker",
                "GEOMETRY".equals(((Enum<?>) constant).name()));
        }
    }

    @Test
    public void chunkShaderBindingPointsStillReserveExpectedBaseAttributes() throws Exception {
        ClassReader reader = classReader(SHADER_BINDING_POINTS);
        final Set<String> fields = new HashSet<>();

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                fields.add(name + descriptor);
                return null;
            }
        }, 0);

        String pointDescriptor = "Lme/jellysquid/mods/sodium/client/gl/shader/ShaderBindingPoint;";
        assertTrue(fields.contains("POSITION" + pointDescriptor));
        assertTrue(fields.contains("COLOR" + pointDescriptor));
        assertTrue(fields.contains("TEX_COORD" + pointDescriptor));
        assertTrue(fields.contains("LIGHT_COORD" + pointDescriptor));
        assertTrue(fields.contains("MODEL_OFFSET" + pointDescriptor));
    }

    @Test
    public void chunkRenderShaderBackendConstructorStillUsesChunkVertexType() throws Exception {
        ClassReader reader = classReader(CHUNK_RENDER_SHADER_BACKEND);
        final boolean[] hasConstructor = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if ("<init>".equals(name) && ("(L" + CHUNK_VERTEX_TYPE + ";)V").equals(descriptor)) {
                    hasConstructor[0] = true;
                }
                return null;
            }
        }, 0);

        assertTrue("Relictium ChunkRenderShaderBackend constructor changed", hasConstructor[0]);
    }

    @Test
    public void chunkRenderManagerStillExposesShadowVisibilityHooks() throws Exception {
        ClassReader reader = classReader(CHUNK_RENDER_MANAGER);
        final Set<String> fields = new HashSet<>();
        final Set<String> methods = new HashSet<>();
        final boolean[] addChunkCallsCanRebuild = {false};
        final boolean[] resetReadsRebuildQueue = {false};
        final boolean[] resetReadsImportantRebuildQueue = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                fields.add(name + descriptor);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                methods.add(name + descriptor);

                if ("addChunk".equals(name)
                    && ("(L" + CHUNK_RENDER_CONTAINER + ";)V").equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && CHUNK_RENDER_CONTAINER.equals(owner)
                                && "canRebuild".equals(methodName)
                                && "()Z".equals(methodDescriptor)) {
                                addChunkCallsCanRebuild[0] = true;
                            }
                        }
                    };
                }

                if ("reset".equals(name) && "()V".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fieldName,
                                                   String fieldDescriptor) {
                            if (opcode == Opcodes.GETFIELD
                                && CHUNK_RENDER_MANAGER.equals(owner)
                                && OBJECT_ARRAY_FIFO_QUEUE_DESC.equals(fieldDescriptor)) {
                                if ("rebuildQueue".equals(fieldName)) {
                                    resetReadsRebuildQueue[0] = true;
                                }
                                if ("importantRebuildQueue".equals(fieldName)) {
                                    resetReadsImportantRebuildQueue[0] = true;
                                }
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        assertTrue(fields.contains("importantRebuildQueue" + OBJECT_ARRAY_FIFO_QUEUE_DESC));
        assertTrue(fields.contains("rebuildQueue" + OBJECT_ARRAY_FIFO_QUEUE_DESC));
        assertTrue(fields.contains("chunkRenderLists" + CHUNK_RENDER_LIST_ARRAY_DESC));
        assertTrue(fields.contains("tickableChunks" + OBJECT_LIST_DESC));
        assertTrue(fields.contains("visibleBlockEntities" + OBJECT_LIST_DESC));
        assertTrue(fields.contains("dirtyZ"));
        assertTrue(fields.contains("visibleChunkCountI"));

        assertTrue(methods.contains("<init>(L" + SODIUM_WORLD_RENDERER + ";L" + CHUNK_RENDER_BACKEND
            + ";L" + BLOCK_RENDER_PASS_MANAGER + ";Lnet/minecraft/client/multiplayer/WorldClient;I)V"));
        assertTrue(methods.contains("addChunk(L" + CHUNK_RENDER_CONTAINER + ";)V"));
        assertTrue(methods.contains("computeVisibleFaces(L" + CHUNK_RENDER_CONTAINER + ";)I"));
        assertTrue(methods.contains("reset()V"));
        assertTrue(methods.contains("updateChunks()V"));
        assertTrue(methods.contains("update(FL" + FRUSTUM_EXTENDED + ";IZ)V"));

        assertTrue("Relictium ChunkRenderManager.addChunk must still gate queueing through canRebuild()",
            addChunkCallsCanRebuild[0]);
        assertTrue("Relictium ChunkRenderManager.reset must still read rebuildQueue for the shadow queue redirect",
            resetReadsRebuildQueue[0]);
        assertTrue("Relictium ChunkRenderManager.reset must still read importantRebuildQueue for the shadow queue redirect",
            resetReadsImportantRebuildQueue[0]);
    }

    @Test
    public void sodiumWorldRendererStillSupportsShadowVisibilitySwapPoints() throws Exception {
        ClassReader reader = classReader(SODIUM_WORLD_RENDERER);
        final Set<String> fields = new HashSet<>();
        final Set<String> methods = new HashSet<>();
        final boolean[] scheduleTerrainUpdateMarksDirty = {false};
        final int[] updateChunksLastCameraXReads = {0};
        final boolean[] updateChunksMarksDirty = {false};
        final boolean[] updateChunksRunsChunkUpdates = {false};
        final boolean[] updateChunksRebuildsVisibilityGraph = {false};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                           String signature, Object value) {
                fields.add(name + descriptor);
                return null;
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                methods.add(name + descriptor);

                if ("scheduleTerrainUpdate".equals(name) && "()V".equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && CHUNK_RENDER_MANAGER.equals(owner)
                                && "markDirty".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                scheduleTerrainUpdateMarksDirty[0] = true;
                            }
                        }
                    };
                }

                if ("updateChunks".equals(name)
                    && ("(L" + FRUSTUM + ";FZIZ)V").equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM5) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fieldName,
                                                   String fieldDescriptor) {
                            if (opcode == Opcodes.GETFIELD
                                && SODIUM_WORLD_RENDERER.equals(owner)
                                && "lastCameraX".equals(fieldName)
                                && "D".equals(fieldDescriptor)) {
                                updateChunksLastCameraXReads[0]++;
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String methodName,
                                                    String methodDescriptor, boolean isInterface) {
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && CHUNK_RENDER_MANAGER.equals(owner)
                                && "markDirty".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                updateChunksMarksDirty[0] = true;
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && CHUNK_RENDER_MANAGER.equals(owner)
                                && "updateChunks".equals(methodName)
                                && "()V".equals(methodDescriptor)) {
                                updateChunksRunsChunkUpdates[0] = true;
                            }
                            if (opcode == Opcodes.INVOKEVIRTUAL
                                && CHUNK_RENDER_MANAGER.equals(owner)
                                && "update".equals(methodName)
                                && ("(FL" + FRUSTUM_EXTENDED + ";IZ)V").equals(methodDescriptor)) {
                                updateChunksRebuildsVisibilityGraph[0] = true;
                            }
                        }
                    };
                }

                return null;
            }
        }, 0);

        assertTrue(fields.contains("chunkRenderManager" + CHUNK_RENDER_MANAGER_DESC));
        assertTrue(fields.contains("lastCameraXD"));
        assertTrue(fields.contains("lastCameraYD"));
        assertTrue(fields.contains("lastCameraZD"));
        assertTrue(fields.contains("lastCameraPitchD"));
        assertTrue(fields.contains("lastCameraYawD"));

        assertTrue(methods.contains("scheduleTerrainUpdate()V"));
        assertTrue(methods.contains("updateChunks(L" + FRUSTUM + ";FZIZ)V"));
        assertTrue(methods.contains("drawChunkLayer(L" + BLOCK_RENDER_LAYER + ";DDD)V"));

        assertTrue("Relictium SodiumWorldRenderer.scheduleTerrainUpdate must still call ChunkRenderManager.markDirty()",
            scheduleTerrainUpdateMarksDirty[0]);
        assertTrue("Relictium SodiumWorldRenderer.updateChunks must still read lastCameraX for the shadow graph redirect",
            updateChunksLastCameraXReads[0] > 0);
        assertTrue("Relictium SodiumWorldRenderer.updateChunks must still mark the graph dirty when the camera changes",
            updateChunksMarksDirty[0]);
        assertTrue("Relictium SodiumWorldRenderer.updateChunks must still drain chunk rebuild work before visibility rebuild",
            updateChunksRunsChunkUpdates[0]);
        assertTrue("Relictium SodiumWorldRenderer.updateChunks must still call ChunkRenderManager.update(...) for visibility graph rebuilds",
            updateChunksRebuildsVisibilityGraph[0]);
    }

    @Test
    public void sodiumWorldRendererStillSelectsVertexFormatBeforeBackendCreation() throws Exception {
        ClassReader reader = classReader(SODIUM_WORLD_RENDERER);
        final boolean[] foundInitRenderer = {false};
        final int[] instructionIndex = {0};
        final int[] hfpVertexFormatReads = {0};
        final int[] hfpVertexFormatReadIndex = {-1};
        final int[] sfpVertexFormatReads = {0};
        final int[] sfpVertexFormatReadIndex = {-1};
        final int[] backendCreationCalls = {0};
        final int[] backendCreationIndex = {-1};

        reader.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                             String signature, String[] exceptions) {
                if (!"initRenderer".equals(name)) {
                    return null;
                }

                assertEquals("()V", descriptor);
                foundInitRenderer[0] = true;

                return new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fieldName,
                                               String fieldDescriptor) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.GETSTATIC
                            && DEFAULT_MODEL_VERTEX_FORMATS.equals(owner)
                            && "MODEL_VERTEX_HFP".equals(fieldName)) {
                            hfpVertexFormatReads[0]++;
                            hfpVertexFormatReadIndex[0] = instructionIndex[0];
                        }
                        if (opcode == Opcodes.GETSTATIC
                            && DEFAULT_MODEL_VERTEX_FORMATS.equals(owner)
                            && "MODEL_VERTEX_SFP".equals(fieldName)) {
                            sfpVertexFormatReads[0]++;
                            sfpVertexFormatReadIndex[0] = instructionIndex[0];
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String methodName,
                                                String methodDescriptor, boolean isInterface) {
                        instructionIndex[0]++;
                        if (opcode == Opcodes.INVOKESTATIC
                            && SODIUM_WORLD_RENDERER.equals(owner)
                            && "createChunkRenderBackend".equals(methodName)
                            && ("(L" + RENDER_DEVICE + ";L" + SODIUM_GAME_OPTIONS + ";"
                                + CHUNK_VERTEX_TYPE_DESC + ")L" + CHUNK_RENDER_BACKEND + ";")
                                    .equals(methodDescriptor)) {
                            backendCreationCalls[0]++;
                            backendCreationIndex[0] = instructionIndex[0];
                        }
                    }
                };
            }
        }, 0);

        assertTrue("Relictium SodiumWorldRenderer.initRenderer changed", foundInitRenderer[0]);
        assertEquals("Expected compact HFP terrain vertex-format selection", 1, hfpVertexFormatReads[0]);
        assertEquals("Expected fallback SFP terrain vertex-format selection", 1, sfpVertexFormatReads[0]);
        assertEquals("Expected one backend creation call for the vertex-format redirect", 1, backendCreationCalls[0]);
        assertTrue("HFP vertex-format selection must happen before backend creation",
            hfpVertexFormatReadIndex[0] > 0 && hfpVertexFormatReadIndex[0] < backendCreationIndex[0]);
        assertTrue("SFP vertex-format selection must happen before backend creation",
            sfpVertexFormatReadIndex[0] > 0 && sfpVertexFormatReadIndex[0] < backendCreationIndex[0]);
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
