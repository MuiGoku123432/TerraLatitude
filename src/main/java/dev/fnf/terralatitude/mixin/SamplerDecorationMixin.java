package dev.fnf.terralatitude.mixin;

import dev.fnf.terralatitude.AltitudeBridge;
import net.minecraft.world.level.biome.Climate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Runs outside Natural Temperature's cancellable sampler injection. */
@Mixin(targets = "etcodehome.freeterraforged.world.worldgen.runtime.WorldgenPlans$SamplerDecoration", remap = false)
public abstract class SamplerDecorationMixin {
    @Inject(
            method = "sample(Lnet/minecraft/world/level/biome/Climate$Sampler;III)Lnet/minecraft/world/level/biome/Climate$TargetPoint;",
            at = @At("RETURN"), cancellable = true, remap = false, require = 1, allow = 1
    )
    private void terraLatitude$adjust(Climate.Sampler sampler, int quartX, int quartY, int quartZ,
                                    CallbackInfoReturnable<Climate.TargetPoint> cir) {
        Climate.TargetPoint original = cir.getReturnValue();
        Climate.TargetPoint adjusted = AltitudeBridge.adjust(sampler, quartX, quartY, quartZ, original);
        if (adjusted != original) cir.setReturnValue(adjusted);
    }
}
