package io.me.exposed;

import appeng.core.definitions.AEBlockEntities;
import appeng.me.storage.NetworkStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(MExposed.ID)
public class MExposed {
    public static final String ID = "mexposed";

    public MExposed(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, MExposedConfig.COMMON_SPEC);
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    ConfigurationScreen::new);
        }
    }

    @EventBusSubscriber
    public static class ModBusEvents {
        @SubscribeEvent
        public static void registerCapabilitiesEvent(RegisterCapabilitiesEvent event) {
            registerCapabilities(event);
        }
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                AEBlockEntities.CONTROLLER.get(),
                (be, context) -> {
                    if (be.getMainNode().getGrid() != null)
                        return new ExposedInvHandler((NetworkStorage) be.getMainNode().getGrid().getStorageService().getInventory());
                    return null;
                }
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                AEBlockEntities.CONTROLLER.get(),
                (be, context) -> {
                    if (be.getMainNode().getGrid() != null)
                        return new ExposedInvHandler((NetworkStorage) be.getMainNode().getGrid().getStorageService().getInventory());
                    return null;
                }
        );
    }
}
