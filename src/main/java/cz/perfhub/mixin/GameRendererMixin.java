package cz.perfhub.mixin;

import cz.perfhub.PerformanceMetrics;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void performancehub$sampleFrameTime(DeltaTracker deltaTracker, boolean tick, CallbackInfo ci) {
        PerformanceMetrics.onFrame();
    }
}
