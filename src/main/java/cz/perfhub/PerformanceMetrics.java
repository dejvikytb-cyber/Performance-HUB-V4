package cz.perfhub;

import net.minecraft.client.Minecraft;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Low-overhead client-side frame/tick telemetry; no server TPS claims. */
public final class PerformanceMetrics {
    private static final int FRAME_CAPACITY = 120;
    private static final int TICK_CAPACITY = 40;
    private static final double[] frameSamples = new double[FRAME_CAPACITY];
    private static final double[] tickSamples = new double[TICK_CAPACITY];
    private static int frameCursor, frameCount, tickCursor, tickCount;
    private static long lastFrameNanos, tickStartNanos, tickId;
    private static long nextDisplayRefreshNanos, lastGcSampleNanos;
    private static long gcCollectionCount = -1;
    private static double lastFrameMs, lastTickMs, p95FrameMs, averageFrameMs, p95TickMs;
    private static long usedHeapMb, maxHeapMb;
    private static String[] displayLines = {"PERFORMANCE HUB / 2.0", "Waiting for world...", "", "", "", ""};
    private static double[] cachedGraph = new double[0];

    private PerformanceMetrics() {}

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

    /** Called once at the head of the game render. Display strings and graph history are refreshed on a timer. */
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

        int interval = PerfConfig.get().hudRefreshMs;
        if (now >= nextDisplayRefreshNanos) {
            nextDisplayRefreshNanos = now + interval * 1_000_000L;
            refreshPercentiles();
            refreshMemoryAndGc(now);
            refreshDisplayLines();
        }
    }

    private static void refreshPercentiles() {
        if (frameCount == 0) return;
        double[] graphCopy = new double[frameCount];
        for (int i = 0; i < frameCount; i++) {
            int index = (frameCursor - frameCount + i + FRAME_CAPACITY) % FRAME_CAPACITY;
            graphCopy[i] = frameSamples[index];
        }
        final int graphBuckets = Math.min(52, graphCopy.length);
        double[] downsampled = new double[graphBuckets];
        for (int bucket = 0; bucket < graphBuckets; bucket++) {
            int from = bucket * graphCopy.length / graphBuckets;
            int to = Math.max(from + 1, (bucket + 1) * graphCopy.length / graphBuckets);
            double worst = 0.0;
            for (int i = from; i < Math.min(to, graphCopy.length); i++) worst = Math.max(worst, graphCopy[i]);
            downsampled[bucket] = worst;
        }
        cachedGraph = downsampled;
        double[] copy = graphCopy.clone();
        Arrays.sort(copy);
        int p95Index = Math.min(copy.length - 1, (int) Math.ceil(copy.length * 0.95) - 1);
        p95FrameMs = copy[p95Index];
        double sum = 0;
        for (double value : copy) sum += value;
        averageFrameMs = sum / copy.length;

        if (tickCount > 0) {
            double[] ticks = new double[tickCount];
            for (int i = 0; i < tickCount; i++) {
                int index = (tickCursor - tickCount + i + TICK_CAPACITY) % TICK_CAPACITY;
                ticks[i] = tickSamples[index];
            }
            Arrays.sort(ticks);
            p95TickMs = ticks[Math.min(ticks.length - 1, (int) Math.ceil(ticks.length * 0.95) - 1)];
        }
    }

    private static void refreshMemoryAndGc(long now) {
        Runtime r = Runtime.getRuntime();
        usedHeapMb = (r.totalMemory() - r.freeMemory()) / (1024L * 1024L);
        maxHeapMb = r.maxMemory() / (1024L * 1024L);
        if (now - lastGcSampleNanos > 1_000_000_000L) {
            lastGcSampleNanos = now;
            long count = 0;
            boolean any = false;
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                if (bean.getCollectionCount() >= 0) { count += bean.getCollectionCount(); any = true; }
            }
            gcCollectionCount = any ? count : -1;
        }
    }

    private static void refreshDisplayLines() {
        Minecraft mc = Minecraft.getInstance();
        int fps = mc.getFps();
        int p95Fps = p95FrameMs <= 0.1 ? 999 : (int) Math.round(1000.0 / p95FrameMs);
        String frame = String.format(java.util.Locale.ROOT, "Frame  %.1f ms  |  P95 %.1f ms", lastFrameMs, p95FrameMs);
        String tick = String.format(java.util.Locale.ROOT, "Client tick  %.1f ms  |  P95 %.1f ms (50 ms budget)", lastTickMs, p95TickMs);
        String memory = "Java heap  " + usedHeapMb + " / " + maxHeapMb + " MB"
                + (gcCollectionCount >= 0 ? "  |  GC " + gcCollectionCount : "");
        String culling = "Cull entity " + OcclusionCulling.lastTickCulledEntities()
                + "  |  BE distance " + BlockEntityCulling.lastTickCulled()
                + "  |  particle drop " + ParticleBudget.lastTickDropped();
        displayLines = new String[] {
                "PERFORMANCE HUB  /  2.0",
                "FPS " + fps + "  |  P95 frame ~" + p95Fps + " FPS  |  " + String.format(java.util.Locale.ROOT, "avg %.1f ms", averageFrameMs),
                frame,
                tick,
                memory,
                culling
        };
    }

    public static String[] displayLines() { return displayLines; }
    public static double lastFrameMs() { return lastFrameMs; }
    public static double lastTickMs() { return lastTickMs; }
    public static double p95FrameMs() { return p95FrameMs; }
    public static double averageFrameMs() { return averageFrameMs; }
    public static double[] frameHistory() { return cachedGraph; }

    public static boolean isFrameTimeHealthy(int targetFps) {
        double targetMs = 1000.0 / Math.max(30, targetFps);
        return frameCount >= 20 && p95FrameMs > 0 && p95FrameMs < targetMs * 0.85;
    }

    public static boolean isFrameTimeSlow(int targetFps) {
        double targetMs = 1000.0 / Math.max(30, targetFps);
        return frameCount >= 20 && p95FrameMs > targetMs * 1.25;
    }
}
