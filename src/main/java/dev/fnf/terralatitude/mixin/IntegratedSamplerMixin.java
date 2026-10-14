package dev.fnf.terralatitude.mixin;

import dev.fnf.terralatitude.IntegratedClimate;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Cancels only owned integrated samples before NT's default-priority sampler injection. */
@Mixin(value = Climate.Sampler.class, priority = 2000, remap = false)
public abstract class IntegratedSamplerMixin {
    @Inject(method = "sample(III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;",
            at = @At("HEAD"), cancellable = true, require = 1, allow = 1, remap = false)
    private void terraLatitude$sample(int x, int y, int z, CallbackInfoReturnable<Climate.TargetPoint> cir) {
        var result = IntegratedClimate.evaluate((Climate.Sampler) (Object) this, x, y, z);
        if (result != null) cir.setReturnValue(result.point());
    }
}
