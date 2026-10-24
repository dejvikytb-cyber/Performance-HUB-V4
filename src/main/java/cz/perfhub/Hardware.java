package cz.perfhub;

import org.lwjgl.opengl.GL11;

import java.lang.management.ManagementFactory;
import java.util.Locale;

/** Runtime hardware snapshot. Physical RAM is measured separately from the Java heap. */
public final class Hardware {
    private static Hardware cached;

    public final int cores;
    public final long heapMb;
    public final long physicalRamMb;
    public final String os;
    public final String gpu;
    public final Profile recommended;

    private Hardware(int cores, long heapMb, long physicalRamMb, String os, String gpu, Profile recommended) {
        this.cores = cores;
        this.heapMb = heapMb;
        this.physicalRamMb = physicalRamMb;
        this.os = os;
        this.gpu = gpu;
        this.recommended = recommended;
    }

    public static synchronized Hardware get() {
        if (cached == null) cached = detect();
        return cached;
    }

    private static Hardware detect() {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);
        long physicalRamMb = -1;
        try {
            var bean = ManagementFactory.getOperatingSystemMXBean();
            if (bean instanceof com.sun.management.OperatingSystemMXBean extended) {
                physicalRamMb = extended.getTotalMemorySize() / (1024L * 1024L);
            }
        } catch (Throwable ignored) { }

        String os = System.getProperty("os.name", "unknown");
        String gpu = "unknown (open a world to detect)";
        try {
            String r = GL11.glGetString(GL11.GL_RENDERER);
            if (r != null && !r.isBlank()) gpu = r;
        } catch (Throwable ignored) { /* OpenGL context is not necessarily ready in menus. */ }

        String g = gpu.toLowerCase(Locale.ROOT);
        boolean software = g.contains("llvmpipe") || g.contains("software") || g.contains("swrast");
        boolean integrated = software || (g.contains("intel") && !g.contains("arc"))
                || g.contains("uhd") || g.contains("iris xe") || g.contains("vega 3")
                || g.contains("vega 6") || g.contains("vega 8") || g.contains("vega 11");
        boolean lowRam = physicalRamMb > 0 && physicalRamMb <= 8192;
        Profile rec;
        if (cores <= 4 || lowRam || integrated || software || heapMb <= 2048) {
            rec = Profile.WEAK;
        } else if (cores >= 8 && physicalRamMb >= 16384 && heapMb >= 4096 && !gpu.startsWith("unknown")) {
            rec = Profile.STRONG;
        } else {
            rec = Profile.MEDIUM;
        }
        return new Hardware(cores, heapMb, physicalRamMb, os, gpu, rec);
    }

    public boolean isWindows() { return os.toLowerCase(Locale.ROOT).contains("windows"); }
}
