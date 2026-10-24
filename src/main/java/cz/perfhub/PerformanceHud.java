package cz.perfhub;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

/** Cached diagnostics overlay; string formatting is throttled instead of done every rendered frame. */
public final class PerformanceHud {
    private static final int X = 7;
    private static final int Y = 7;
    private static final int WIDTH = 310;
    private static final int ACCENT = 0xFF66F0B2;
    private static final int TEXT = 0xFFE4EAF2;
    private static final int MUTED = 0xFFA5AFBE;
    private static final int GRAPH_SEGMENTS = 52;
    private static String[] cachedSourceLines;
    private static String[] cachedFittedLines = new String[0];
    private static int cachedFitWidth = -1;
    private static int cachedScalePercent = -1;
    private static boolean cachedCullingState;
    private static boolean cachedSystemState;

    private PerformanceHud() {}

    public static void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(PerfHubClient.MOD_ID, "performance_hud"),
                (graphics, tickCounter) -> render(graphics));
    }

    private static void render(GuiGraphics graphics) {
        PerfConfig cfg = PerfConfig.get();
        Minecraft client = Minecraft.getInstance();
        if (!cfg.performanceHud || client.level == null) return;

        String[] lines = PerformanceMetrics.displayLines();
        int scalePercent = cfg.hudScalePercent;
        graphics.pose().pushPose();
        float scale = scalePercent / 100.0f;
        graphics.pose().scale(scale, scale, 1.0f);

        int x = Math.round(X / scale);
        int y = Math.round(Y / scale);
        int width = Math.round(WIDTH / scale);
        int fitWidth = width - 16;
        if (lines != cachedSourceLines || fitWidth != cachedFitWidth || scalePercent != cachedScalePercent
                || cachedCullingState != cfg.showCullingCounters || cachedSystemState != cfg.showSystemMetrics) {
            cachedSourceLines = lines;
            cachedFitWidth = fitWidth;
            cachedScalePercent = scalePercent;
            cachedCullingState = cfg.showCullingCounters;
            cachedSystemState = cfg.showSystemMetrics;
            cachedFittedLines = new String[lines.length];
            for (int i = 0; i < lines.length; i++) cachedFittedLines[i] = fit(client, lines[i], fitWidth);
        }
        int rowHeight = 11;
        int textRows = lines.length;
        int graphHeight = cfg.showFrameGraph ? 25 : 0;
        int height = 12 + textRows * rowHeight + graphHeight + 5;

        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xB8000000);
        graphics.fill(x, y, x + width, y + height, 0xE910141B);
        graphics.fill(x, y, x + 2, y + height, ACCENT);
        graphics.fill(x + 2, y, x + width, y + 1, 0xFF28333F);

        graphics.drawString(client.font, cachedFittedLines[0], x + 8, y + 5, ACCENT, false);
        for (int i = 1; i < textRows; i++) {
            String line = cachedFittedLines[Math.min(i, cachedFittedLines.length - 1)];
            int color = i == 1 ? fpsColor(client.getFps()) : i == 2 ? 0xFFCCD7E4 : MUTED;
            graphics.drawString(client.font, line, x + 8,
                    y + 17 + (i - 1) * rowHeight, color, false);
        }

        if (cfg.showFrameGraph) {
            int graphTop = y + 17 + (textRows - 1) * rowHeight + 3;
            int graphBottom = graphTop + 21;
            graphics.fill(x + 8, graphTop, x + width - 7, graphBottom, 0xFF101820);
            double[] history = PerformanceMetrics.frameHistory(); // pre-cached at the metrics refresh rate
            if (PerformanceMetrics.frameHistoryCount() > 0) {
                int availableWidth = width - 16;
                int segments = Math.min(GRAPH_SEGMENTS, PerformanceMetrics.frameHistoryCount());
                for (int i = 0; i < segments; i++) {
                    double ms = Math.max(1.0, Math.min(50.0, history[i]));
                    int barHeight = Math.max(1, (int) Math.round(ms / 50.0 * 19.0));
                    int bx1 = x + 8 + i * availableWidth / segments;
                    int bx2 = x + 8 + (i + 1) * availableWidth / segments;
                    int color = ms > 33.3 ? 0xFFFF6B75 : ms > 16.7 ? 0xFFFFC857 : ACCENT;
                    graphics.fill(bx1, graphBottom - barHeight, Math.max(bx1 + 1, bx2), graphBottom, color);
                }
            }
        }
        graphics.pose().popPose();
    }

    private static int fpsColor(int fps) {
        return fps < 45 ? 0xFFFF6B75 : fps < 90 ? 0xFFFFC857 : ACCENT;
    }

    private static String fit(Minecraft client, String text, int maxWidth) {
        String s = text;
        while (client.font.width(s) > maxWidth && s.length() > 4) s = s.substring(0, s.length() - 1);
        return s;
    }
}
