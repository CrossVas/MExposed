package io.me.exposed;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.tile.networking.ControllerTileEntity;
import io.me.exposed.snapshot.ExposedSnapshotHolder;
import io.me.exposed.snapshot.SnapshotInvHandler;
import net.minecraft.world.World;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(MExposed.ID)
public class MExposed {
    public static final String ID = "mexposed";
    public static final Logger LOGGER = LogManager.getLogger(ID);

    public MExposed() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MExposedConfig.COMMON_SPEC);
    }

    /** The storage grid the controller currently belongs to, or null if it has none yet. */
    public static IStorageGrid getStorageGrid(ControllerTileEntity be) {
        IGridNode node = be.getActionableNode();
        IGrid grid = node != null ? node.getGrid() : null;
        return grid != null ? grid.getCache(IStorageGrid.class) : null;
    }

    /** @param onChange invoked (server side) when the exposed inventory changed shape, so cached capabilities get dropped. */
    public static SnapshotInvHandler createHandler(ControllerTileEntity be, IStorageGrid storage, Runnable onChange) {
        SnapshotInvHandler handler = new SnapshotInvHandler(storage);
        World level = be.getLevel();
        if (level != null && !level.isClientSide())
            ((ExposedSnapshotHolder) storage).mexposed$snapshot().setChangeListener(storage, onChange);
        return handler;
    }
}
