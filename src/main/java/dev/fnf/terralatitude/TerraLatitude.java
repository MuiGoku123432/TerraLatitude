package dev.fnf.terralatitude;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(TerraLatitude.MOD_ID)
public final class TerraLatitude {
    public static final String MOD_ID = "terralatitude";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public TerraLatitude(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, IntegrationConfig.SPEC, "terralatitude-common.toml");
        modBus.addListener(this::loading);
        modBus.addListener(this::reloading);
        NeoForge.EVENT_BUS.addListener(WorldProfiles::starting);
        NeoForge.EVENT_BUS.addListener(WorldProfiles::stopped);
        NeoForge.EVENT_BUS.addListener(ClimateCommands::register);
        LOGGER.info("TerraLatitude integrated climate registered (experimental 0.2.0). Saved world profiles control generation.");
    }
    private void loading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == IntegrationConfig.SPEC) IntegrationConfig.refresh();
    }
    private void reloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == IntegrationConfig.SPEC) IntegrationConfig.refresh();
    }
}
