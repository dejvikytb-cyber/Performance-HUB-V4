package cz.perfhub.mixin;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("x") double performancehub$getX();
    @Accessor("y") double performancehub$getY();
    @Accessor("z") double performancehub$getZ();
}
