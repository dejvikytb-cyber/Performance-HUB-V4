package cz.perfhub;

import org.lwjgl.opengl.GL11;

import java.util.Locale;

/** Jednoduchá detekce hardwaru (CPU vlákna, heap pro Javu, GPU řetězec) a doporučený profil. */
public final class Hardware {
    private static Hardware cached;

    public final int cores;
    public final long heapMb;
    public final String gpu;
    public final Profile recommended;

    private Hardware(int cores, long heapMb, String gpu, Profile recommended) {
        this.cores = cores;
        this.heapMb = heapMb;
        this.gpu = gpu;
        this.recommended = recommended;
    }

    public static synchronized Hardware get() {
        if (cached == null) cached = detect();
        return cached;
    }

    private static Hardware detect() {
        int cores = Runtime.getRuntime().availableProcessors();
        long heapMb = Runtime.getRuntime().maxMemory() / (1024L * 1024L);

        String gpu = "neznámé";
        try {
            String r = GL11.glGetString(GL11.GL_RENDERER);
            if (r != null && !r.isBlank()) gpu = r;
        } catch (Throwable ignored) {
            // GL kontext nemusí být dostupný - zůstane "neznámé"
        }

        String g = gpu.toLowerCase(Locale.ROOT);
        boolean software = g.contains("llvmpipe") || g.contains("software") || g.contains("swrast");
        boolean integrated = software
                || (g.contains("intel") && !g.contains("arc"))
                || g.contains("uhd") || g.contains("iris xe") || g.contains("vega 3")
                || g.contains("vega 6") || g.contains("vega 8") || g.contains("vega 11");
        boolean unknown = gpu.equals("neznámé");

        Profile rec;
        if (cores <= 4 || integrated || software) {
            rec = Profile.WEAK;
        } else if (cores >= 8 && heapMb >= 3500 && !unknown) {
            rec = Profile.STRONG;
        } else {
            rec = Profile.MEDIUM;
        }
        return new Hardware(cores, heapMb, gpu, rec);
    }
}
