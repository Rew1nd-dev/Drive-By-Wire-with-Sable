package edn.stratodonut.drivebywire.client;

import edn.stratodonut.drivebywire.DriveByWireMod;
import edn.stratodonut.drivebywire.WireConfig;
import net.createmod.catnip.config.ui.BaseConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@EventBusSubscriber(modid = DriveByWireMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class WireConfigScreen {
    private WireConfigScreen() {}

    @SubscribeEvent
    public static void onClientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            BaseConfigScreen.setDefaultActionFor(DriveByWireMod.MOD_ID,
                screen -> screen.withSpecs(null, WireConfig.CONFIG_SPEC, null));
            ModList.get().getModContainerById(DriveByWireMod.MOD_ID).orElseThrow()
                .registerExtensionPoint(IConfigScreenFactory.class,
                    (container, parent) -> new BaseConfigScreen(parent, DriveByWireMod.MOD_ID));
        });
    }
}
