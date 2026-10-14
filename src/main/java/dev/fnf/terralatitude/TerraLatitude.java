package dev.fnf.terralatitude;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TerraLatitude.MOD_ID)
public final class TerraLatitude {
    public static final String MOD_ID = "terralatitude";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public TerraLatitude(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AltitudeConfig.SPEC, "terralatitude-common.toml");
        modBus.addListener(this::onConfigLoading);
        modBus.addListener(this::onConfigReloading);
        LOGGER.info("TerraLatitude altitude compatibility registered (experimental biome placement).");
    }

    private void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == AltitudeConfig.SPEC) AltitudeConfig.refresh();
    }

    private void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == AltitudeConfig.SPEC) AltitudeConfig.refresh();
    }
}
