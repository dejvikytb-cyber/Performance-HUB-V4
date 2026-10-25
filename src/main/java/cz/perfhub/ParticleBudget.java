package cz.perfhub;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.world.phys.Vec3;
import cz.perfhub.mixin.ParticleAccessor;

/** Rejects optional particles before they enter the particle engine. */
public final class ParticleBudget {
    private static long tick = Long.MIN_VALUE;
    private static int accepted, distantAccepted, nearbyAccepted, dropped, distanceDropped;
    private static int previousAccepted, previousDropped, previousDistanceDropped;
    private static int effectiveBudget = -1;
    private static int lastConfiguredBudget = -1;
    private static long lastAdaptiveUpdate;
    private static long adaptiveWindowAccepted, adaptiveWindowDropped;
    private static int adaptiveWindowTicks;

    private ParticleBudget() {}

    public static void beginTick(long tickId) {
        if (tick != Long.MIN_VALUE) {
            previousAccepted = accepted;
            previousDropped = dropped;
            previousDistanceDropped = distanceDropped;
        }
        tick = tickId;
        accepted = distantAccepted = nearbyAccepted = dropped = distanceDropped = 0;
        PerfConfig cfg = PerfConfig.get();
        if (effectiveBudget < 0 || lastConfiguredBudget != cfg.maxParticleSpawnsPerTick) {
            effectiveBudget = cfg.maxParticleSpawnsPerTick;
            lastConfiguredBudget = cfg.maxParticleSpawnsPerTick;
        }
    }

    public static boolean allow(Particle particle) {
        PerfConfig cfg = PerfConfig.get();
        Minecraft mc = Minecraft.getInstance();
        if (particle == null || mc.player == null || mc.level == null) return true;

        ParticleAccessor access = (ParticleAccessor) (Object) particle;
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        double dx = access.performancehub$getX() - camera.x;
        double dy = access.performancehub$getY() - camera.y;
        double dz = access.performancehub$getZ() - camera.z;
        double distanceSq = dx * dx + dy * dy + dz * dz;

        if (cfg.particleDistanceCulling && distanceSq > (double) cfg.maxParticleDistance * cfg.maxParticleDistance) {
            dropped++;
            distanceDropped++;
            return false;
        }
        if (!cfg.particleBudgetEnabled) return true;

        int configuredCap = Math.max(50, cfg.maxParticleSpawnsPerTick);
        int budget = Math.max(50, cfg.adaptiveParticleBudget
                ? Math.min(effectiveBudget, configuredCap)
                : configuredCap);
        boolean nearby = distanceSq <= (double) cfg.nearbyParticleDistance * cfg.nearbyParticleDistance;
        int reserve = cfg.prioritizeNearbyParticles ? Math.min(cfg.nearbyParticleReserve, budget / 2) : 0;

        if (nearby) {
            if (accepted >= budget) {
                dropped++;
                return false;
            }
            accepted++;
            nearbyAccepted++;
            return true;
        }

        // Distant particles may only use the non-reserved part of the budget.
        int distantLimit = Math.max(0, budget - reserve);
        if (accepted >= budget || distantAccepted >= distantLimit) {
            dropped++;
            return false;
        }
        accepted++;
        distantAccepted++;
        return true;
    }

    public static void endTick(long tickId) {
        PerfConfig cfg = PerfConfig.get();
        if (!cfg.adaptiveParticleBudget || !cfg.particleBudgetEnabled || Minecraft.getInstance().level == null) return;
        adaptiveWindowAccepted += accepted;
        adaptiveWindowDropped += Math.max(0, dropped - distanceDropped);
        adaptiveWindowTicks++;
        if (tickId - lastAdaptiveUpdate < 20) return;
        lastAdaptiveUpdate = tickId;
        int adaptiveCeiling = Math.max(50, Math.min(cfg.maxAdaptiveParticleBudget, cfg.maxParticleSpawnsPerTick));
        int adaptiveFloor = Math.min(cfg.minAdaptiveParticleBudget, adaptiveCeiling);
        effectiveBudget = Math.max(adaptiveFloor, Math.min(adaptiveCeiling, effectiveBudget));

        boolean particlePressure = adaptiveWindowDropped > 0
                || adaptiveWindowAccepted >= (long) effectiveBudget * Math.max(1, adaptiveWindowTicks) * 0.8;
        if (PerformanceMetrics.isFrameTimeSlow(cfg.targetFps) && particlePressure) {
            effectiveBudget = Math.max(adaptiveFloor, (int) (effectiveBudget * 0.80));
        } else if (PerformanceMetrics.isFrameTimeHealthy(cfg.targetFps) && adaptiveWindowDropped == 0) {
            effectiveBudget = Math.min(adaptiveCeiling, effectiveBudget + Math.max(10, effectiveBudget / 20));
        }
        effectiveBudget = Math.max(adaptiveFloor, Math.min(adaptiveCeiling, effectiveBudget));
        adaptiveWindowAccepted = adaptiveWindowDropped = 0;
        adaptiveWindowTicks = 0;
    }

    public static void reset() { effectiveBudget = PerfConfig.get().maxParticleSpawnsPerTick; lastConfiguredBudget = PerfConfig.get().maxParticleSpawnsPerTick; accepted = distantAccepted = nearbyAccepted = dropped = distanceDropped = 0; previousAccepted = previousDropped = previousDistanceDropped = 0; adaptiveWindowAccepted = adaptiveWindowDropped = 0; adaptiveWindowTicks = 0; }

    public static int lastTickAccepted() { return previousAccepted; }
    public static int lastTickDropped() { return previousDropped; }
    public static int lastTickDistanceDropped() { return previousDistanceDropped; }
    public static int effectiveBudget() { return effectiveBudget < 0 ? PerfConfig.get().maxParticleSpawnsPerTick : effectiveBudget; }
}
