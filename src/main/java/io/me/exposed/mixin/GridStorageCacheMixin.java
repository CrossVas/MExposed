package io.me.exposed.mixin;

import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.storage.cells.ICellProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.GridStorageCache;
import io.me.exposed.snapshot.ExposedSnapshot;
import io.me.exposed.snapshot.ExposedSnapshotHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** One snapshot per grid, shared by the item and fluid monitors. */
@Mixin(value = GridStorageCache.class, remap = false)
public abstract class GridStorageCacheMixin implements ExposedSnapshotHolder {

    @Unique
    private ExposedSnapshot mexposed$snapshot;

    @Override
    public ExposedSnapshot mexposed$snapshot() {
        if (mexposed$snapshot == null)
            mexposed$snapshot = new ExposedSnapshot();
        return mexposed$snapshot;
    }

    @Override
    public ExposedSnapshot mexposed$peekSnapshot() {
        return mexposed$snapshot;
    }

    @Unique
    private void mexposed$storageChanged() {
        if (mexposed$snapshot != null)
            mexposed$snapshot.notifyStorageChanged();
    }

    // Drives/cell workbench-style providers coming and going: the 1.16.5 counterpart of NetworkStorage mount/unmount.
    @Inject(method = "registerCellProvider", at = @At("RETURN"))
    private void mexposed$afterRegister(ICellProvider provider, CallbackInfo ci) {
        mexposed$storageChanged();
    }

    @Inject(method = "unregisterCellProvider", at = @At("RETURN"))
    private void mexposed$afterUnregister(ICellProvider provider, CallbackInfo ci) {
        mexposed$storageChanged();
    }

    @Inject(method = "cellUpdate", at = @At("RETURN"))
    private void mexposed$afterCellUpdate(MENetworkCellArrayUpdate event, CallbackInfo ci) {
        mexposed$storageChanged();
    }

    @Inject(method = "postAlterationOfStoredItems", at = @At("RETURN"))
    private void mexposed$afterAlteration(IStorageChannel<?> channel, Iterable<? extends IAEStack<?>> input, IActionSource src, CallbackInfo ci) {
        if (mexposed$snapshot != null)
            mexposed$snapshot.markDirty();
    }
}
