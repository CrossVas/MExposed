package io.me.exposed;

import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.storage.NetworkStorage;
import com.mojang.logging.LogUtils;
import io.me.exposed.snapshot.ExposedSnapshotHolder;
import io.me.exposed.snapshot.SnapshotInvHandler;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(MExposed.ID)
public class MExposed {
    public static final String ID = "mexposed";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MExposed() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MExposedConfig.COMMON_SPEC);
    }

    public static SnapshotInvHandler createHandler(ControllerBlockEntity be) {
        if (be.getMainNode().getGrid() == null)
            return null;

        NetworkStorage storage = (NetworkStorage) be.getMainNode().getGrid().getStorageService().getInventory();
        SnapshotInvHandler handler = new SnapshotInvHandler(storage);
        Level level = be.getLevel();
        if (level != null && !level.isClientSide())
            ((ExposedSnapshotHolder) storage).mexposed$snapshot().setChangeListener(storage, level::invalidateCaps);
        return handler;
    }
}
