package cz.perfhub.mixin;

import cz.perfhub.BlockEntityCulling;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.21.11 extracts block entities into render states before submission. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void performancehub$distanceCullBlockEntity(BlockEntity blockEntity, float tickProgress,
                                                         ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
                                                         CallbackInfoReturnable<?> cir) {
        if (BlockEntityCulling.shouldCull(blockEntity)) cir.setReturnValue(null);
    }
}
