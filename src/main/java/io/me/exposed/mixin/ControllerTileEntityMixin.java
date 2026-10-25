package io.me.exposed.mixin;

import appeng.api.networking.storage.IStorageGrid;
import appeng.tile.grid.AENetworkPowerTileEntity;
import appeng.tile.networking.ControllerTileEntity;
import io.me.exposed.MExposed;
import io.me.exposed.snapshot.SnapshotInvHandler;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.Direction;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mixin(ControllerTileEntity.class)
public abstract class ControllerTileEntityMixin extends AENetworkPowerTileEntity {

    @Unique
    private LazyOptional<SnapshotInvHandler> exposedCap = LazyOptional.empty();
    @Unique
    private IStorageGrid exposedGrid;

    public ControllerTileEntityMixin(TileEntityType<?> tileEntityTypeIn) {
        super(tileEntityTypeIn);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            IStorageGrid storage = MExposed.getStorageGrid((ControllerTileEntity) (Object) this);
            if (storage != null) {
                // The grid object is replaced on split/merge, so a handler bound to the old one must go.
                if (exposedCap.isPresent() && exposedGrid != storage) {
                    exposedCap.invalidate();
                }
                if (!exposedCap.isPresent()) {
                    exposedGrid = storage;
                    exposedCap = LazyOptional.of(() -> MExposed.createHandler((ControllerTileEntity) (Object) this, storage, this::mexposed$dropCap));
                }
                return exposedCap.cast();
            }
        }
        return super.getCapability(cap, side);
    }

    // Invalidating fires the LazyOptional listeners, which makes consumers that cached it re-query.
    @Unique
    private void mexposed$dropCap() {
        exposedCap.invalidate();
    }

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();
        exposedCap.invalidate();
    }
}
