package io.me.exposed;

import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.core.definitions.AEBlockEntities;
import io.me.exposed.snapshot.ExposedSnapshotHolder;
import io.me.exposed.snapshot.SnapshotInvHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import org.slf4j.Logger;
import net.minecraft.world.level.Level;
import appeng.me.storage.NetworkStorage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
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
    public static final Logger LOGGER = LogUtils.getLogger();

    public MExposed(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerCapabilities);
        modContainer.registerConfig(ModConfig.Type.COMMON, MExposedConfig.COMMON_SPEC);
        if (FMLLoader.getDist() == Dist.CLIENT) {
            modContainer.registerExtensionPoint(
                    IConfigScreenFactory.class,
                    ConfigurationScreen::new);
        }
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                AEBlockEntities.CONTROLLER.get(),
                (be, context) -> createHandler(be)
        );
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                AEBlockEntities.CONTROLLER.get(),
                (be, context) -> createHandler(be)
        );
    }

    private static SnapshotInvHandler createHandler(ControllerBlockEntity be) {
        if (be.getMainNode().getGrid() == null)
            return null;

        NetworkStorage storage = (NetworkStorage) be.getMainNode().getGrid().getStorageService().getInventory();
        SnapshotInvHandler handler = new SnapshotInvHandler(storage);
        Level level = be.getLevel();
        BlockPos pos = be.getBlockPos();
        if (level != null && !level.isClientSide())
            ((ExposedSnapshotHolder) storage).mexposed$snapshot().setChangeListener(storage, () -> level.invalidateCapabilities(pos));
        return handler;
    }
}
