package cz.perfhub;

/**
 * Keeps CPU-side culling work inside a measured frame-time budget.
 * All world reads still happen on Minecraft's client thread; moving chunk/block queries
 * to arbitrary workers would violate thread-safety and can cause crashes.
 */
public final class AdaptiveWorkBudget {
    private static int entityBudget = -1;
    private static int blockEntityBudget = -1;
    private static long lastUpdateTick;

    private AdaptiveWorkBudget() {}

    public static void tick(long tick) {
        PerfConfig cfg = PerfConfig.get();
        if (entityBudget < 0) entityBudget = cfg.maxOcclusionChecksPerTick;
        if (blockEntityBudget < 0) blockEntityBudget = cfg.maxBlockEntityOcclusionChecksPerTick;
        if (tick - lastUpdateTick < 20) return;
        lastUpdateTick = tick;

        int entityMax = cfg.entityOcclusionCulling ? cfg.maxOcclusionChecksPerTick : 0;
        int blockEntityMax = cfg.blockEntityOcclusionCulling ? cfg.maxBlockEntityOcclusionChecksPerTick : 0;
        if (!cfg.adaptiveCullingBudget) {
            entityBudget = entityMax;
            blockEntityBudget = blockEntityMax;
            return;
        }

        boolean overloaded = PerformanceMetrics.isFrameTimeSlow(cfg.targetFps)
                || (cfg.lowMemoryMode && PerformanceMetrics.isHeapUnderPressure());
        boolean headroom = PerformanceMetrics.isFrameTimeHealthy(cfg.targetFps)
                && !PerformanceMetrics.isHeapUnderPressure();

        if (overloaded) {
            entityBudget = Math.max(entityMax == 0 ? 0 : 2, (int) (entityBudget * 0.72));
            blockEntityBudget = Math.max(blockEntityMax == 0 ? 0 : 1, (int) (blockEntityBudget * 0.72));
        } else if (headroom) {
            entityBudget = Math.min(entityMax, entityBudget + Math.max(1, entityMax / 16));
            blockEntityBudget = Math.min(blockEntityMax, blockEntityBudget + Math.max(1, blockEntityMax / 8));
        } else {
            entityBudget = Math.min(entityBudget, entityMax);
            blockEntityBudget = Math.min(blockEntityBudget, blockEntityMax);
        }
    }

    public static int entityBudget() {
        return Math.max(0, Math.min(PerfConfig.get().maxOcclusionChecksPerTick, entityBudget < 0
                ? PerfConfig.get().maxOcclusionChecksPerTick : entityBudget));
    }

    public static int blockEntityBudget() {
        return Math.max(0, Math.min(PerfConfig.get().maxBlockEntityOcclusionChecksPerTick, blockEntityBudget < 0
                ? PerfConfig.get().maxBlockEntityOcclusionChecksPerTick : blockEntityBudget));
    }

    public static void reset() {
        entityBudget = PerfConfig.get().maxOcclusionChecksPerTick;
        blockEntityBudget = PerfConfig.get().maxBlockEntityOcclusionChecksPerTick;
        lastUpdateTick = 0;
    }
}
