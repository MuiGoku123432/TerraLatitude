package dev.fnf.terralatitude.mixin;

import etcodehome.freeterraforged.world.worldgen.cell.climate.ClimateModule;
import etcodehome.freeterraforged.world.worldgen.noise.module.Noise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to the same immutable regional noise graphs FTF evaluates. */
@Mixin(value = ClimateModule.class, remap = false)
public interface ClimateModuleAccessor {
    @Accessor("temperature") Noise terralatitude$temperature();
    @Accessor("moisture") Noise terralatitude$moisture();
    @Accessor("warpX") Noise terralatitude$warpX();
    @Accessor("warpZ") Noise terralatitude$warpZ();
    @Accessor("warpStrength") float terralatitude$warpStrength();
    @Accessor("biomeFreq") float terralatitude$biomeFrequency();
}
