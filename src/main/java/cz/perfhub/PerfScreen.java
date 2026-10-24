package cz.perfhub;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Dark, compact control console for render culling, particle budgets, telemetry and adaptive controls. */
public final class PerfScreen extends Screen {
    private enum Tab {
        OVERVIEW("Overview", "SYSTEM SNAPSHOT"),
        RENDER("Render", "RENDER PIPELINE"),
        CULLING("Culling", "VISIBILITY FILTERS"),
        PARTICLES("Particles", "PARTICLE SCHEDULER"),
        TICK("Adaptive", "FRAME / TICK CONTROL"),
        HUD("HUD", "DIAGNOSTICS OVERLAY"),
        ADVANCED("Advanced", "ADVANCED CONTROLS"),
        SYSTEM("System", "WINDOWS / MEMORY / THREADS");

        final String label;
        final String subtitle;
        Tab(String label, String subtitle) { this.label = label; this.subtitle = subtitle; }
    }

    private static final int BG = 0xF00B0F14;
    private static final int PANEL = 0xFF111821;
    private static final int CARD = 0xFF151F2A;
    private static final int BORDER = 0xFF283544;
    private static final int ACCENT = 0xFF66F0B2;
    private static final int TEXT = 0xFFE5ECF4;
    private static final int MUTED = 0xFF91A0B2;

    private final Screen parent;
    private Tab tab = Tab.OVERVIEW;
    private String status = "Ready · settings are local";
    private int left, top, panelW, panelH, sideW, contentX, contentW;
    private final List<Row> rows = new ArrayList<>();

    private record Row(int y, String title, String description) {}

    public PerfScreen(Screen parent) {
        super(Component.literal("Performance Hub V4"));
        this.parent = parent;
    }

    @Override protected void init() { rebuild(); }

    private void rebuild() {
        clearWidgets();
        rows.clear();
        panelW = Math.min(760, Math.max(520, width - 16));
        panelH = Math.min(440, Math.max(350, height - 16));
        if (panelW > width - 8) panelW = width - 8;
        if (panelH > height - 8) panelH = height - 8;
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        sideW = 142;
        contentX = left + sideW + 18;
        contentW = panelW - sideW - 30;

        for (Tab t : Tab.values()) {
            int tabY = top + 74 + t.ordinal() * 31;
            addRenderableWidget(Button.builder(Component.literal(t.label), b -> {
                tab = t;
                status = "";
                rebuild();
            }).bounds(left + 10, tabY, sideW - 20, 25).build());
        }

        int rowY = top + 88;
        switch (tab) {
            case OVERVIEW -> buildOverview(rowY);
            case RENDER -> buildRender(rowY);
            case CULLING -> buildCulling(rowY);
            case PARTICLES -> buildParticles(rowY);
            case TICK -> buildAdaptive(rowY);
            case HUD -> buildHud(rowY);
            case ADVANCED -> buildAdvanced(rowY);
            case SYSTEM -> buildSystem(rowY);
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left + panelW - 108, top + panelH - 34, 94, 24).build());
    }

    private void addRow(int y, String title, String description, String value, Runnable action) {
        int rowHeight = 38;
        rows.add(new Row(y, title, description));
        int buttonWidth = Math.min(138, Math.max(105, contentW / 3));
        addRenderableWidget(Button.builder(Component.literal(value), b -> {
            try {
                action.run();
                PerfConfig.save();
                status = "Applied · " + title;
            } catch (Throwable t) {
                PerfHubClient.LOGGER.warn("Nastavení Performance Hubu se nepodařilo změnit: {}", title, t);
                status = "Operation failed · check latest.log";
            }
            rebuild();
        }).bounds(contentX + contentW - buttonWidth, y + 6, buttonWidth, 25).build());
    }

    private void addInfoRow(int y, String title, String description, String detail) {
        rows.add(new Row(y, title, description + " · " + detail));
    }

    private void buildOverview(int y) {
        Hardware hw = Hardware.get();
        addRow(y, "Competitive", "Aggressive local culling + low render pressure", "Apply profile", () -> applyProfile(Profile.WEAK)); y += 40;
        addRow(y, "Balanced", "Performance first, without pushing every limit", "Apply profile", () -> applyProfile(Profile.MEDIUM)); y += 40;
        addRow(y, "Visual", "Higher particle and block-entity budgets", "Apply profile", () -> applyProfile(Profile.STRONG)); y += 40;
        addRow(y, "Recommended for this PC", hw.cores + " logical threads · Java heap " + hw.heapMb + " MB", hw.recommended.label, () -> applyProfile(hw.recommended)); y += 40;
        addRow(y, "Restore vanilla settings", "Restore the snapshot captured before the first profile", "Restore", () -> {
            if (!PerfApply.restoreOriginal()) status = "No vanilla snapshot has been captured yet";
        });
    }

    private void buildRender(int y) {
        PerfConfig c = PerfConfig.get();
        addRow(y, "Entity occlusion culling", "Ray checks only entities vanilla already considers in-frustum", onOff(c.entityOcclusionCulling), () -> c.entityOcclusionCulling = !c.entityOcclusionCulling); y += 40;
        addRow(y, "Occlusion range", "Entities farther away are not ray-tested", c.occlusionMaxDistance + " blocks", () -> c.occlusionMaxDistance = next(c.occlusionMaxDistance, 24, 32, 48, 64, 80, 96)); y += 40;
        addRow(y, "Block-entity distance culling", "Skip rendering distant chests, signs and other block entities", onOff(c.blockEntityDistanceCulling), () -> c.blockEntityDistanceCulling = !c.blockEntityDistanceCulling); y += 40;
        addRow(y, "Block-entity range", "0 is not used; use 128 to effectively disable this cap", c.blockEntityRenderDistance + " blocks", () -> c.blockEntityRenderDistance = next(c.blockEntityRenderDistance, 16, 24, 32, 40, 48, 64, 80, 96, 128)); y += 40;
        addRow(y, "Adaptive render distance", "Optional vanilla setting controller; does not change server simulation", onOff(c.adaptivePerformance), () -> c.adaptivePerformance = !c.adaptivePerformance); y += 40;
        addRow(y, "Adaptive FPS target", "Used by the optional distance / particle controllers", c.targetFps + " FPS", () -> c.targetFps = next(c.targetFps, 60, 90, 120, 144, 165, 240, 300));
    }

    private void buildCulling(int y) {
        PerfConfig c = PerfConfig.get();
        addRow(y, "Entity occlusion", "Three sight lines; opaque blocks only; players are never hidden", onOff(c.entityOcclusionCulling), () -> c.entityOcclusionCulling = !c.entityOcclusionCulling); y += 40;
        addRow(y, "Check budget / tick", "Hard cap on entity raycast groups to protect CPU frame time", c.maxOcclusionChecksPerTick + " checks", () -> c.maxOcclusionChecksPerTick = next(c.maxOcclusionChecksPerTick, 0, 8, 16, 24, 32, 48, 64, 96, 128)); y += 40;
        addRow(y, "Visibility cache", "Reuse conservative visibility result before raycasting again", c.occlusionCacheTicks + " ticks", () -> c.occlusionCacheTicks = next(c.occlusionCacheTicks, 1, 2, 4, 6, 8, 12, 16)); y += 40;
        addRow(y, "Max raycast distance", "Very distant entities are skipped by the occlusion tester", c.occlusionMaxDistance + " blocks", () -> c.occlusionMaxDistance = next(c.occlusionMaxDistance, 16, 24, 32, 48, 64, 80, 96, 128)); y += 40;
        addRow(y, "Block-entity distance cap", "Conservative distance filter; no BE ticking or block logic is skipped", onOff(c.blockEntityDistanceCulling), () -> c.blockEntityDistanceCulling = !c.blockEntityDistanceCulling); y += 40;
        addRow(y, "BE render distance", "Reduce this for dense storage rooms or modded bases", c.blockEntityRenderDistance + " blocks", () -> c.blockEntityRenderDistance = next(c.blockEntityRenderDistance, 16, 24, 32, 40, 48, 64, 80, 96, 128)); y += 40;
        addRow(y, "BE occlusion culling", "Skip hidden BE render states behind opaque blocks; budgeted + cached", onOff(c.blockEntityOcclusionCulling), () -> c.blockEntityOcclusionCulling = !c.blockEntityOcclusionCulling);
    }

    private void buildParticles(int y) {
        PerfConfig c = PerfConfig.get();
        addRow(y, "Particle admission budget", "Reject excess particles before they enter the engine", onOff(c.particleBudgetEnabled), () -> c.particleBudgetEnabled = !c.particleBudgetEnabled); y += 40;
        addRow(y, "Max admitted per tick", "An admission cap also reduces subsequent particle ticking", c.maxParticleSpawnsPerTick + " / tick", () -> c.maxParticleSpawnsPerTick = next(c.maxParticleSpawnsPerTick, 100, 180, 240, 360, 500, 700, 1000, 1500, 2500)); y += 40;
        addRow(y, "Particle distance culling", "Discard particle spawns too far from the camera", onOff(c.particleDistanceCulling), () -> c.particleDistanceCulling = !c.particleDistanceCulling); y += 40;
        addRow(y, "Max particle distance", "Visual trade-off: distant cosmetic trails disappear", c.maxParticleDistance + " blocks", () -> c.maxParticleDistance = next(c.maxParticleDistance, 16, 24, 32, 40, 48, 64, 80, 96, 128)); y += 40;
        addRow(y, "Prioritize nearby particles", "Distant particles cannot consume the nearby reserve", onOff(c.prioritizeNearbyParticles), () -> c.prioritizeNearbyParticles = !c.prioritizeNearbyParticles); y += 40;
        addRow(y, "Adaptive particle budget", "Slow P95 frames + particle pressure lower the budget gradually", onOff(c.adaptiveParticleBudget), () -> c.adaptiveParticleBudget = !c.adaptiveParticleBudget);
    }

    private void buildAdaptive(int y) {
        PerfConfig c = PerfConfig.get();
        addRow(y, "Adaptive particle budget", "Only adjusts when measured frame time and particle pressure justify it", onOff(c.adaptiveParticleBudget), () -> c.adaptiveParticleBudget = !c.adaptiveParticleBudget); y += 40;
        addRow(y, "Adaptive render distance", "Vanilla view distance is changed slowly, not every frame", onOff(c.adaptivePerformance), () -> c.adaptivePerformance = !c.adaptivePerformance); y += 40;
        addRow(y, "Target FPS", "P95 frame time compared against this frame budget", c.targetFps + " FPS", () -> c.targetFps = next(c.targetFps, 60, 90, 120, 144, 165, 240, 300)); y += 40;
        addRow(y, "Minimum view distance", "Adaptive mode will not go below this", c.adaptiveMinRenderDistance + " chunks", () -> c.adaptiveMinRenderDistance = next(c.adaptiveMinRenderDistance, 2, 4, 6, 8, 10, 12)); y += 40;
        addRow(y, "Maximum view distance", "Adaptive mode will not go above this", c.adaptiveMaxRenderDistance + " chunks", () -> c.adaptiveMaxRenderDistance = next(c.adaptiveMaxRenderDistance, 6, 8, 10, 12, 16, 20, 24)); y += 40;
        addRow(y, "Apply target controls", "Save controller settings now; changes persist in config", "Save", PerfConfig::save);
    }

    private void buildHud(int y) {
        PerfConfig c = PerfConfig.get();
        addRow(y, "Performance HUD", "Toggle with F8 at any time", onOff(c.performanceHud), () -> c.performanceHud = !c.performanceHud); y += 40;
        addRow(y, "Frame-time graph", "Tiny cached graph; green <16.7 ms, amber <33.3 ms, red above", onOff(c.showFrameGraph), () -> c.showFrameGraph = !c.showFrameGraph); y += 40;
        addRow(y, "Culling counters", "Show recent entity, block entity and particle drops", onOff(c.showCullingCounters), () -> c.showCullingCounters = !c.showCullingCounters); y += 40;
        addRow(y, "HUD refresh rate", "String formatting is throttled; draw calls stay lightweight", c.hudRefreshMs + " ms", () -> c.hudRefreshMs = next(c.hudRefreshMs, 100, 150, 200, 300, 500, 750, 1000)); y += 40;
        addRow(y, "HUD scale", "Adjust the diagnostics overlay size", c.hudScalePercent + "%", () -> c.hudScalePercent = next(c.hudScalePercent, 75, 85, 90, 100, 110, 120, 125)); y += 40;
        addRow(y, "Toggle HUD now", "The keybind is also available outside this menu", c.performanceHud ? "Disable" : "Enable", () -> c.performanceHud = !c.performanceHud);
    }

    private void buildSystem(int y) {
        PerfConfig c = PerfConfig.get();
        Hardware hw = Hardware.get();
        addRow(y, "Low-memory mode", "Shorter-lived culling caches and particle pressure backoff", onOff(c.lowMemoryMode), () -> c.lowMemoryMode = !c.lowMemoryMode); y += 40;
        addRow(y, "Adaptive culling budget", "Raises checks when frame-time has headroom; backs off under load", onOff(c.adaptiveCullingBudget), () -> { c.adaptiveCullingBudget = !c.adaptiveCullingBudget; AdaptiveWorkBudget.reset(); }); y += 40;
        addRow(y, "System telemetry", "One-second Windows/OS CPU and physical-memory sampling", onOff(c.showSystemMetrics), () -> c.showSystemMetrics = !c.showSystemMetrics); y += 40;
        addRow(y, "Heap pressure threshold", "Back off cosmetic particle work near this Java heap usage", c.heapPressurePercent + "%", () -> c.heapPressurePercent = next(c.heapPressurePercent, 70, 75, 80, 85, 90, 95)); y += 40;
        addRow(y, "Entity cache limit", "Bound visibility-cache memory; lower values trade a few more ray tests", c.maxEntityCacheEntries + " entries", () -> c.maxEntityCacheEntries = next(c.maxEntityCacheEntries, 512, 1024, 2048, 4096, 8192)); y += 40;
        addRow(y, "Block-entity cache limit", "Bound block-entity visibility cache memory", c.maxBlockEntityCacheEntries + " entries", () -> c.maxBlockEntityCacheEntries = next(c.maxBlockEntityCacheEntries, 256, 512, 1024, 2048, 4096)); y += 40;
        addInfoRow(y, "Runtime / thread budget", hw.os + " · " + hw.cores + " logical threads · " + (hw.physicalRamMb > 0 ? hw.physicalRamMb + " MB physical RAM" : "physical RAM unavailable"), "Minecraft controls its own worker pools; unsafe world/OpenGL calls are never forced onto background threads");
    }

    private void buildAdvanced(int y) {
        PerfConfig c = PerfConfig.get();
        addRow(y, "Automatic profile on first start", "Disabled by default so the mod never changes vanilla options silently", onOff(c.autoProfileOnFirstRun), () -> c.autoProfileOnFirstRun = !c.autoProfileOnFirstRun); y += 40;
        addRow(y, "Clear entity visibility cache", "Useful after changing render mods or diagnosing odd visibility", "Clear cache", OcclusionCulling::clear); y += 40;
        addRow(y, "Reset particle budget", "Reinitializes the adaptive budget from its configured cap", "Reset budget", ParticleBudget::reset); y += 40;
        addRow(y, "Adaptive minimum budget", "Lowest particle admission limit during slow scenes", c.minAdaptiveParticleBudget + " / tick", () -> c.minAdaptiveParticleBudget = next(c.minAdaptiveParticleBudget, 50, 75, 100, 125, 150, 200, 250, 300, 400)); y += 40;
        addRow(y, "Adaptive maximum budget", "Adaptive ceiling, still bounded by Max admitted per tick", c.maxAdaptiveParticleBudget + " / tick", () -> c.maxAdaptiveParticleBudget = next(c.maxAdaptiveParticleBudget, 250, 360, 500, 600, 800, 1000, 1500, 2500)); y += 40;
        addRow(y, "BE occlusion ray budget", "Upper bound on block-entity occlusion tests per client tick", c.maxBlockEntityOcclusionChecksPerTick + " checks", () -> c.maxBlockEntityOcclusionChecksPerTick = next(c.maxBlockEntityOcclusionChecksPerTick, 0, 4, 8, 12, 16, 24, 32)); y += 40;
        addRow(y, "Restore vanilla settings", "Restores the snapshot captured by the first applied profile", "Restore", () -> {
            if (!PerfApply.restoreOriginal()) status = "No vanilla snapshot has been captured yet";
        });
    }

    private void applyProfile(Profile profile) {
        PerfApply.apply(profile);
        PerfApply.applyDeep(profile);
    }

    private static String onOff(boolean b) { return b ? "ON" : "OFF"; }
    private static int next(int current, int... values) {
        for (int i = 0; i < values.length; i++) if (values[i] == current) return values[(i + 1) % values.length];
        return values[0];
    }

    private String fit(String text, int maxWidth) {
        String out = text;
        while (font.width(out) > maxWidth && out.length() > 5) out = out.substring(0, out.length() - 1);
        return out;
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 2, top - 2, left + panelW + 2, top + panelH + 2, 0xFF05070A);
        g.fill(left, top, left + panelW, top + panelH, BG);
        g.fill(left, top, left + panelW, top + 2, ACCENT);
        g.fill(left, top + 2, left + sideW, top + panelH, PANEL);
        g.fill(left + sideW, top + 2, left + sideW + 1, top + panelH, BORDER);
        g.fill(left + sideW + 1, top + 60, left + panelW - 1, top + 61, BORDER);
        g.fill(left + 10, top + 44, left + sideW - 10, top + 45, BORDER);
        g.drawString(font, "PERFORMANCE HUB", left + 13, top + 14, TEXT, false);
        g.drawString(font, "V4  /  RENDER ENGINE", left + 13, top + 29, ACCENT, false);
        g.drawString(font, "CONTROL MODULES", left + 13, top + 53, MUTED, false);

        int activeY = top + 74 + tab.ordinal() * 31;
        g.fill(left + 7, activeY, left + sideW - 7, activeY + 25, 0xFF1B302B);
        g.fill(left + 7, activeY, left + 9, activeY + 25, ACCENT);

        g.drawString(font, tab.subtitle, contentX, top + 17, ACCENT, false);
        g.drawString(font, "Local client-side controls · no packets or gameplay tick suppression", contentX, top + 35, MUTED, false);

        for (Row row : rows) {
            g.fill(contentX, row.y, contentX + contentW, row.y + 37, CARD);
            g.fill(contentX, row.y, contentX + 1, row.y + 37, BORDER);
            g.drawString(font, fit(row.title, contentW - 155), contentX + 9, row.y + 7, TEXT, false);
            g.drawString(font, fit(row.description, contentW - 155), contentX + 9, row.y + 22, MUTED, false);
        }

        String footer = status.isEmpty() ? "Changes apply locally and are saved to config" : status;
        g.drawString(font, fit(footer, panelW - 145), left + 12, top + panelH - 23,
                status.startsWith("Operation failed") ? 0xFFFF7A86 : ACCENT, false);

        if (tab == Tab.OVERVIEW) {
            Hardware hw = Hardware.get();
            String info = "GPU: " + hw.gpu;
            g.drawString(font, fit(info, sideW - 20), left + 13, top + panelH - 58, MUTED, false);
            String ram = "RAM: " + (hw.physicalRamMb > 0 ? hw.physicalRamMb + " MB" : "n/a");
            g.drawString(font, fit(ram, sideW - 20), left + 13, top + panelH - 47, MUTED, false);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() {
        PerfConfig.save();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
