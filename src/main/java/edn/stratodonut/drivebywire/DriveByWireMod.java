package edn.stratodonut.drivebywire;

import com.mojang.logging.LogUtils;
import edn.stratodonut.drivebywire.network.WirePackets;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(DriveByWireMod.MOD_ID)
public class DriveByWireMod {
    public static final String MOD_ID = "drivebywire";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DriveByWireMod(final IEventBus modEventBus, final ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, WireConfig.CONFIG_SPEC);
        WireBlocks.register(modEventBus);
        WireBlockEntities.register(modEventBus);
        WireItems.register(modEventBus);
        WireCreativeTabs.register(modEventBus);
        WireSounds.register(modEventBus);
        modEventBus.addListener(WirePackets::register);

        NeoForge.EVENT_BUS.addListener(WireCommonEvents::onLevelTick);
        NeoForge.EVENT_BUS.addListener(WireCommonEvents::onNeighborNotify);
    }

    public static ResourceLocation asResource(final String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
