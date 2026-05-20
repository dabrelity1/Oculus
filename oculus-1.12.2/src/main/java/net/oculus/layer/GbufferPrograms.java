package net.oculus.layer;

import net.oculus.gl.state.StateUpdateNotifiers;
import net.oculus.pipeline.PipelineManager;
import net.oculus.pipeline.SpecialCondition;
import net.oculus.pipeline.WorldRenderingPhase;
import net.oculus.pipeline.WorldRenderingPipeline;

/**
 * Tracks world render phases for compatibility with OptiFine shader packs.
 */
public final class GbufferPrograms {
    private static boolean entities;
    private static boolean blockEntities;
    private static boolean outlines;

    private GbufferPrograms() {
    }

    private static void checkReentrancy() {
        if (entities || blockEntities || outlines) {
            throw new IllegalStateException("GbufferPrograms entered an invalid state. entities=" + entities
                + " blockEntities=" + blockEntities + " outlines=" + outlines);
        }
    }

    public static void beginEntities() {
        checkReentrancy();
        setPhase(WorldRenderingPhase.ENTITIES);
        entities = true;
    }

    public static void endEntities() {
        if (!entities) {
            throw new IllegalStateException("endEntities called before beginEntities");
        }

        setPhase(WorldRenderingPhase.NONE);
        entities = false;
    }

    public static void beginBlockEntities() {
        checkReentrancy();
        setPhase(WorldRenderingPhase.BLOCK_ENTITIES);
        blockEntities = true;
    }

    public static void endBlockEntities() {
        if (!blockEntities) {
            throw new IllegalStateException("endBlockEntities called before beginBlockEntities");
        }

        setPhase(WorldRenderingPhase.NONE);
        blockEntities = false;
    }

    public static void beginOutlines() {
        checkReentrancy();
        setPhase(WorldRenderingPhase.OUTLINE);
        outlines = true;
    }

    public static void endOutlines() {
        if (!outlines) {
            throw new IllegalStateException("endOutlines called before beginOutlines");
        }

        setPhase(WorldRenderingPhase.NONE);
        outlines = false;
    }

    public static void setOverridePhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setOverridePhase(phase);
        }
    }

    private static void setPhase(WorldRenderingPhase phase) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setPhase(phase);
        }
    }

    public static WorldRenderingPhase getCurrentPhase() {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            return pipeline.getPhase();
        }
        return WorldRenderingPhase.NONE;
    }

    public static void runPhaseChangeNotifier() {
        StateUpdateNotifiers.notifyPhaseChanged();
    }

    public static void setupSpecialRenderCondition(SpecialCondition special) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setSpecialCondition(special);
        }
    }

    public static void teardownSpecialRenderCondition(SpecialCondition special) {
        WorldRenderingPipeline pipeline = PipelineManager.INSTANCE.getPipelineNullable();
        if (pipeline != null) {
            pipeline.setSpecialCondition(null);
        }
    }

    public static void init() {
        // trigger static initialiser
    }

}
