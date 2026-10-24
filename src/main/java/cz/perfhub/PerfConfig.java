package cz.perfhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/** Local, validated settings for the performance controller. */
public final class PerfConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static PerfConfig instance = new PerfConfig();

    // General / compatibility
    public boolean autoProfileOnFirstRun = false;
    public boolean firstRunDone = false;
    public boolean performanceHud = true;
    public boolean showFrameGraph = true;
    public boolean showCullingCounters = true;
    public int hudRefreshMs = 200;
    public int hudScalePercent = 100;
    public boolean showSystemMetrics = true;

    // Windows / low-memory controls. These reduce the mod's own cache and cosmetic-work footprint;
    // they do not pretend to change the JVM heap limit after launch.
    public boolean lowMemoryMode = true;
    public int heapPressurePercent = 85;
    public int maxEntityCacheEntries = 2048;
    public int maxBlockEntityCacheEntries = 1024;
    public boolean adaptiveCullingBudget = true;

    // Real render-path culling. Players are deliberately excluded from entity occlusion culling.
    public boolean entityOcclusionCulling = true;
    public int occlusionMaxDistance = 64;
    public int occlusionCacheTicks = 6;
    public int maxOcclusionChecksPerTick = 32;

    // Block entities: distance culling is separate from entity occlusion culling.
    public boolean blockEntityDistanceCulling = true;
    public int blockEntityRenderDistance = 48;
    public boolean blockEntityOcclusionCulling = true;
    public int blockEntityOcclusionCacheTicks = 6;
    public int maxBlockEntityOcclusionChecksPerTick = 8;

    // Particle admission culling. This reduces particle creation, ticking and later rendering.
    public boolean particleDistanceCulling = true;
    public int maxParticleDistance = 48;
    public boolean particleBudgetEnabled = true;
    public int maxParticleSpawnsPerTick = 360;
    public boolean prioritizeNearbyParticles = true;
    public int nearbyParticleDistance = 14;
    public int nearbyParticleReserve = 90;
    public boolean adaptiveParticleBudget = true;
    public int minAdaptiveParticleBudget = 100;
    public int maxAdaptiveParticleBudget = 600;

    // Optional adaptive vanilla controls, kept separate from render-path optimizations.
    public boolean adaptivePerformance = false;
    public int targetFps = 120;
    public int adaptiveMinRenderDistance = 6;
    public int adaptiveMaxRenderDistance = 12;

    /** Backup of user settings before a built-in profile first changes them. */
    public Snapshot original;

    public static final class Snapshot {
        public int renderDistance, simulationDistance, fpsLimit, mipmaps, biomeBlend;
        public String particles, graphics, clouds;
        public double entityDistance, textBackground;
        public boolean entityShadows, vsync;
        public boolean ambientOcclusion, improvedTransparency, vignette, cutoutLeaves;
    }

    public static PerfConfig get() { return instance; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("performancehub.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                PerfConfig loaded = GSON.fromJson(Files.readString(p), PerfConfig.class);
                if (loaded != null) instance = loaded;
            }
            instance.sanitize();
        } catch (Exception e) {
            PerfHubClient.LOGGER.warn("Konfiguraci se nepodařilo načíst, použiji výchozí hodnoty.", e);
            instance = new PerfConfig();
        }
    }

    private void sanitize() {
        hudRefreshMs = clamp(hudRefreshMs, 100, 1000);
        hudScalePercent = clamp(hudScalePercent, 75, 125);
        heapPressurePercent = clamp(heapPressurePercent, 65, 98);
        maxEntityCacheEntries = clamp(maxEntityCacheEntries, 256, 8192);
        maxBlockEntityCacheEntries = clamp(maxBlockEntityCacheEntries, 128, 4096);
        occlusionMaxDistance = clamp(occlusionMaxDistance, 8, 128);
        occlusionCacheTicks = clamp(occlusionCacheTicks, 1, 20);
        maxOcclusionChecksPerTick = clamp(maxOcclusionChecksPerTick, 0, 256);
        blockEntityRenderDistance = clamp(blockEntityRenderDistance, 8, 128);
        blockEntityOcclusionCacheTicks = clamp(blockEntityOcclusionCacheTicks, 1, 20);
        maxBlockEntityOcclusionChecksPerTick = clamp(maxBlockEntityOcclusionChecksPerTick, 0, 128);
        maxParticleDistance = clamp(maxParticleDistance, 8, 128);
        maxParticleSpawnsPerTick = clamp(maxParticleSpawnsPerTick, 50, 10000);
        nearbyParticleDistance = clamp(nearbyParticleDistance, 4, 48);
        nearbyParticleReserve = clamp(nearbyParticleReserve, 0, 1000);
        minAdaptiveParticleBudget = clamp(minAdaptiveParticleBudget, 50, 5000);
        maxAdaptiveParticleBudget = clamp(maxAdaptiveParticleBudget, minAdaptiveParticleBudget, 10000);
        targetFps = clamp(targetFps, 30, 360);
        adaptiveMinRenderDistance = clamp(adaptiveMinRenderDistance, 2, 32);
        adaptiveMaxRenderDistance = clamp(adaptiveMaxRenderDistance, adaptiveMinRenderDistance, 32);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public static void save() {
        try {
            instance.sanitize();
            Path p = path();
            Files.createDirectories(p.getParent());
            Files.writeString(p, GSON.toJson(instance));
        } catch (Exception e) {
            PerfHubClient.LOGGER.warn("Konfiguraci se nepodařilo uložit.", e);
        }
    }
}
