package dev.fnf.terralatitude.mixin;

import dev.fnf.terralatitude.IntegratedClimate;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs in injector pass 900, before NT's cancellable HEAD callback in pass 1000. */
@Mixin(value = Climate.Sampler.class, priority = 2000, remap = false)
public abstract class IntegratedSamplerMixin {
    @Inject(method = "sample(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;",
            at = @At("HEAD"), order = 900, cancellable = true, require = 1, allow = 1, remap = false)
    private void terraLatitude$sample(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        var result = IntegratedClimate.evaluate((Climate.Sampler) (Object) this, x, y, z);
        if (result != null) cir.setReturnValue(result.point());
    }
}
