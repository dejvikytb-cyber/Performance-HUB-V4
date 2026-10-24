package cz.perfhub;

import net.minecraft.client.Minecraft;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.List;

/** Low-allocation client-side telemetry. Never reports remote-server TPS. */
public final class PerformanceMetrics {
    private static final int FRAME_CAPACITY = 120;
    private static final int TICK_CAPACITY = 40;
    private static final int GRAPH_CAPACITY = 52;
    private static final double[] frameSamples = new double[FRAME_CAPACITY];
    private static final double[] tickSamples = new double[TICK_CAPACITY];
    private static final double[] frameSortScratch = new double[FRAME_CAPACITY];
    private static final double[] tickSortScratch = new double[TICK_CAPACITY];
    private static final double[] cachedGraph = new double[GRAPH_CAPACITY];
    private static final List<GarbageCollectorMXBean> GC_BEANS = ManagementFactory.getGarbageCollectorMXBeans();
    private static final com.sun.management.OperatingSystemMXBean OS_BEAN = osBean();

    private static int frameCursor, frameCount, tickCursor, tickCount, graphCount;
    private static long lastFrameNanos, tickStartNanos, tickId;
    private static long nextDisplayRefreshNanos, lastGcSampleNanos;
    private static long gcCollectionCount = -1;
    private static double lastFrameMs, lastTickMs, p95FrameMs, averageFrameMs, p95TickMs;
    private static long usedHeapMb, maxHeapMb, totalSystemRamMb = -1, usedSystemRamMb = -1;
    private static double processCpuPercent = -1, systemCpuPercent = -1;
    private static boolean heapPressure;
    private static String[] displayLines = {"PERFORMANCE HUB / V4", "Waiting for world...", "", "", "", ""};

    private PerformanceMetrics() {}

    private static com.sun.management.OperatingSystemMXBean osBean() {
        try {
            var bean = ManagementFactory.getOperatingSystemMXBean();
            return bean instanceof com.sun.management.OperatingSystemMXBean extended ? extended : null;
        } catch (Throwable ignored) { return null; }
    }

    public static void beginClientTick() {
        tickId++;
        tickStartNanos = System.nanoTime();
    }

    public static void endClientTick() {
        if (tickStartNanos == 0) return;
        lastTickMs = (System.nanoTime() - tickStartNanos) / 1_000_000.0;
        tickSamples[tickCursor] = lastTickMs;
        tickCursor = (tickCursor + 1) % TICK_CAPACITY;
        tickCount = Math.min(TICK_CAPACITY, tickCount + 1);
        tickStartNanos = 0;
    }

    public static long tickId() { return tickId; }

    /** Called at render head. Expensive calculations and text construction are throttled. */
    public static void onFrame() {
        long now = System.nanoTime();
        if (lastFrameNanos != 0) {
            double delta = Math.min(1000.0, (now - lastFrameNanos) / 1_000_000.0);
            if (delta > 0.0) {
                lastFrameMs = delta;
                frameSamples[frameCursor] = delta;
                frameCursor = (frameCursor + 1) % FRAME_CAPACITY;
                frameCount = Math.min(FRAME_CAPACITY, frameCount + 1);
            }
        }
        lastFrameNanos = now;

        long intervalNanos = PerfConfig.get().hudRefreshMs * 1_000_000L;
        if (now >= nextDisplayRefreshNanos) {
            nextDisplayRefreshNanos = now + intervalNanos;
            refreshPercentiles();
            refreshMemoryAndGc(now);
            refreshDisplayLines();
        }
    }

    /** Uses fixed reusable scratch arrays instead of allocating several short-lived arrays every refresh. */
    private static void refreshPercentiles() {
        if (frameCount > 0) {
            double sum = 0;
            int graphBuckets = Math.min(GRAPH_CAPACITY, frameCount);
            graphCount = graphBuckets;
            for (int bucket = 0; bucket < graphBuckets; bucket++) {
                int from = bucket * frameCount / graphBuckets;
                int to = Math.max(from + 1, (bucket + 1) * frameCount / graphBuckets);
                double worst = 0.0;
                for (int i = from; i < Math.min(to, frameCount); i++) {
                    int sampleIndex = (frameCursor - frameCount + i + FRAME_CAPACITY) % FRAME_CAPACITY;
                    double value = frameSamples[sampleIndex];
                    sum += value;
                    if (value > worst) worst = value;
                }
                cachedGraph[bucket] = worst;
            }
            for (int i = 0; i < frameCount; i++) {
                int index = (frameCursor - frameCount + i + FRAME_CAPACITY) % FRAME_CAPACITY;
                frameSortScratch[i] = frameSamples[index];
            }
            Arrays.sort(frameSortScratch, 0, frameCount);
            p95FrameMs = frameSortScratch[Math.min(frameCount - 1, (int) Math.ceil(frameCount * 0.95) - 1)];
            averageFrameMs = sum / frameCount;
        } else {
            graphCount = 0;
            p95FrameMs = 0;
            averageFrameMs = 0;
        }

        if (tickCount > 0) {
            for (int i = 0; i < tickCount; i++) {
                int index = (tickCursor - tickCount + i + TICK_CAPACITY) % TICK_CAPACITY;
                tickSortScratch[i] = tickSamples[index];
            }
            Arrays.sort(tickSortScratch, 0, tickCount);
            p95TickMs = tickSortScratch[Math.min(tickCount - 1, (int) Math.ceil(tickCount * 0.95) - 1)];
        }
    }

    private static void refreshMemoryAndGc(long now) {
        Runtime runtime = Runtime.getRuntime();
        usedHeapMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L);
        maxHeapMb = runtime.maxMemory() / (1024L * 1024L);
        double heapUse = maxHeapMb <= 0 ? 0 : (double) usedHeapMb / maxHeapMb;
        PerfConfig cfg = PerfConfig.get();
        if (!heapPressure && heapUse >= cfg.heapPressurePercent / 100.0) heapPressure = true;
        else if (heapPressure && heapUse <= Math.max(0.40, (cfg.heapPressurePercent - 12) / 100.0)) heapPressure = false;

        if (now - lastGcSampleNanos >= 1_000_000_000L) {
            lastGcSampleNanos = now;
            long count = 0;
            boolean any = false;
            for (GarbageCollectorMXBean bean : GC_BEANS) {
                if (bean.getCollectionCount() >= 0) { count += bean.getCollectionCount(); any = true; }
            }
            gcCollectionCount = any ? count : -1;
            if (OS_BEAN != null) {
                try {
                    totalSystemRamMb = OS_BEAN.getTotalMemorySize() / (1024L * 1024L);
                    long free = OS_BEAN.getFreeMemorySize() / (1024L * 1024L);
                    usedSystemRamMb = Math.max(0, totalSystemRamMb - free);
                    double proc = OS_BEAN.getProcessCpuLoad();
                    double sys = OS_BEAN.getCpuLoad();
                    processCpuPercent = proc < 0 ? -1 : Math.min(100.0, proc * 100.0);
                    systemCpuPercent = sys < 0 ? -1 : Math.min(100.0, sys * 100.0);
                } catch (Throwable ignored) {
                    processCpuPercent = systemCpuPercent = -1;
                    totalSystemRamMb = usedSystemRamMb = -1;
                }
            }
        }
    }

    private static void refreshDisplayLines() {
        Minecraft mc = Minecraft.getInstance();
        PerfConfig cfg = PerfConfig.get();
        int fps = mc.getFps();
        int p95Fps = p95FrameMs <= 0.1 ? 999 : (int) Math.round(1000.0 / p95FrameMs);
        String frame = "Frame " + oneDecimal(lastFrameMs) + " ms | P95 " + oneDecimal(p95FrameMs) + " ms";
        String tick = "Client tick " + oneDecimal(lastTickMs) + " ms | P95 " + oneDecimal(p95TickMs) + " ms";
        String memory = "Java heap " + usedHeapMb + "/" + maxHeapMb + " MB"
                + (gcCollectionCount >= 0 ? " | GC " + gcCollectionCount : "")
                + (heapPressure ? " | PRESSURE" : "");
        String system = "CPU proc " + fmt(processCpuPercent) + "% | sys " + fmt(systemCpuPercent)
                + "% | " + Runtime.getRuntime().availableProcessors() + "T";
        if (totalSystemRamMb > 0) system += " | RAM " + oneDecimal(usedSystemRamMb / 1024.0)
                + "/" + oneDecimal(totalSystemRamMb / 1024.0) + " GB";
        String culling = "Entity " + OcclusionCulling.lastTickCulledEntities()
                + " | BE " + BlockEntityCulling.lastTickCulled()
                + " | particles dropped " + ParticleBudget.lastTickDropped();
        String fpsLine = "FPS " + fps + " | P95 ~" + p95Fps + " FPS | avg " + oneDecimal(averageFrameMs) + " ms";
        if (cfg.showSystemMetrics && cfg.showCullingCounters) {
            displayLines = new String[] {"PERFORMANCE HUB / V4", fpsLine, frame, tick, memory, system, culling};
        } else if (cfg.showSystemMetrics) {
            displayLines = new String[] {"PERFORMANCE HUB / V4", fpsLine, frame, tick, memory, system};
        } else if (cfg.showCullingCounters) {
            displayLines = new String[] {"PERFORMANCE HUB / V4", fpsLine, frame, tick, memory, culling};
        } else {
            displayLines = new String[] {"PERFORMANCE HUB / V4", fpsLine, frame, tick, memory};
        }
    }

    private static String fmt(double value) { return value < 0 ? "n/a" : Long.toString(Math.round(value)); }

    private static String oneDecimal(double value) {
        if (!Double.isFinite(value)) return "n/a";
        long tenths = Math.round(value * 10.0);
        return (tenths / 10) + "." + Math.abs(tenths % 10);
    }

    public static String[] displayLines() { return displayLines; }
    public static double lastFrameMs() { return lastFrameMs; }
    public static double lastTickMs() { return lastTickMs; }
    public static double p95FrameMs() { return p95FrameMs; }
    public static double averageFrameMs() { return averageFrameMs; }
    public static double[] frameHistory() { return cachedGraph; }
    public static int frameHistoryCount() { return graphCount; }
    public static long usedHeapMb() { return usedHeapMb; }
    public static long maxHeapMb() { return maxHeapMb; }
    public static long usedSystemRamMb() { return usedSystemRamMb; }
    public static long totalSystemRamMb() { return totalSystemRamMb; }
    public static double processCpuPercent() { return processCpuPercent; }
    public static double systemCpuPercent() { return systemCpuPercent; }
    public static boolean isHeapUnderPressure() { return heapPressure; }

    public static boolean isFrameTimeHealthy(int targetFps) {
        double targetMs = 1000.0 / Math.max(30, targetFps);
        return frameCount >= 20 && p95FrameMs > 0 && p95FrameMs < targetMs * 0.85;
    }

    public static boolean isFrameTimeSlow(int targetFps) {
        double targetMs = 1000.0 / Math.max(30, targetFps);
        return frameCount >= 20 && p95FrameMs > targetMs * 1.25;
    }
}
