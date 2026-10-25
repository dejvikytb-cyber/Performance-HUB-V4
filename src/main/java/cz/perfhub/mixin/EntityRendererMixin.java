package cz.perfhub.mixin;

import cz.perfhub.OcclusionCulling;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    private void performancehub$occlusionCull(Entity entity, Frustum frustum,
                                              double x, double y, double z,
                                              CallbackInfoReturnable<Boolean> cir) {
        // Let vanilla's frustum and entity-specific visibility checks run first.
        if (cir.getReturnValueZ() && OcclusionCulling.shouldCull(entity)) cir.setReturnValue(false);
    }
}
