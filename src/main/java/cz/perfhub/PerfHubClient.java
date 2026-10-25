package cz.perfhub;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PerfHubClient implements ClientModInitializer {
    public static final String MOD_ID = "performancehub";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static KeyMapping openKey;
    private static KeyMapping hudKey;
    private static ClientLevel previousLevel;

    @Override
    public void onInitializeClient() {
        PerfConfig.load();
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.performancehub.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        hudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.performancehub.toggle_hud", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, category));

        PerformanceHud.register();
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> PerfApply.onStartup());

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            PerformanceMetrics.beginClientTick();
            ParticleBudget.beginTick(PerformanceMetrics.tickId());
            OcclusionCulling.beginTick(PerformanceMetrics.tickId());
            BlockEntityCulling.beginTick(PerformanceMetrics.tickId());
            if (client.level != previousLevel) {
                OcclusionCulling.clear();
                BlockEntityCulling.clear();
                previousLevel = client.level;
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PerformanceMetrics.endClientTick();
            ParticleBudget.endTick(PerformanceMetrics.tickId());
            while (openKey.consumeClick()) {
                if (client.screen == null) client.setScreen(new PerfScreen(null));
            }
            while (hudKey.consumeClick()) {
                PerfConfig.get().performanceHud = !PerfConfig.get().performanceHud;
                PerfConfig.save();
            }
            AdaptivePerformance.tick(client);
        });

        LOGGER.info("Performance Hub 2.0 initialized: entity occlusion, block-entity range and particle admission culling enabled by config.");
    }
}
