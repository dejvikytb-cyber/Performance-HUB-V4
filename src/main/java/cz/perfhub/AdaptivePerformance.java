package cz.perfhub;

import net.minecraft.client.Minecraft;

/** Conservative, opt-in controller. It changes distance only every five seconds. */
public final class AdaptivePerformance {
    private static int ticks;
    private static int lowFpsSamples;
    private static int highFpsSamples;

    private AdaptivePerformance() {}

    public static void tick(Minecraft client) {
        PerfConfig cfg = PerfConfig.get();
        if (!cfg.adaptivePerformance || client.level == null || client.player == null || client.screen != null) {
            ticks = 0;
            return;
        }
        if (++ticks < 100) return;
        ticks = 0;

        int fps = client.getFps();
        int current = client.options.renderDistance().get();
        int min = Math.max(2, Math.min(cfg.adaptiveMinRenderDistance, cfg.adaptiveMaxRenderDistance));
        int max = Math.max(min, Math.min(32, cfg.adaptiveMaxRenderDistance));
        int target = Math.max(30, Math.min(260, cfg.targetFps));

        if (fps < target - 20) {
            lowFpsSamples++;
            highFpsSamples = 0;
        } else if (fps > target + 20) {
            highFpsSamples++;
            lowFpsSamples = 0;
        } else {
            lowFpsSamples = Math.max(0, lowFpsSamples - 1);
            highFpsSamples = Math.max(0, highFpsSamples - 1);
        }

        int next = current;
        if (lowFpsSamples >= 2 && current > min) {
            next = current - 1;
            lowFpsSamples = 0;
        } else if (highFpsSamples >= 4 && current < max) {
            next = current + 1;
            highFpsSamples = 0;
        }

        if (next != current) {
            client.options.renderDistance().set(next);
            client.options.save();
            PerfHubClient.LOGGER.info("Adaptive performance: render distance {} -> {} (FPS {})", current, next, fps);
        }
    }
}
