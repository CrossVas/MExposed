package io.me.exposed.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.storage.IStorageGrid;
import appeng.tile.grid.AENetworkPowerTileEntity;
import appeng.tile.networking.ControllerTileEntity;
import io.me.exposed.ExposedInvHandler;
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
    private LazyOptional<ExposedInvHandler> exposedCap = LazyOptional.empty();

    public ControllerTileEntityMixin(TileEntityType<?> tileEntityTypeIn) {
        super(tileEntityTypeIn);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        IGridNode node = getActionableNode();
        if (node != null && node.getGrid() != null && (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || cap == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY)) {
            if (!exposedCap.isPresent()) {
                IStorageGrid storageGrid = node.getGrid().getCache(IStorageGrid.class);
                exposedCap = LazyOptional.of(() -> new ExposedInvHandler(storageGrid));
            }
            return exposedCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void invalidateCaps() {
        super.invalidateCaps();
        exposedCap.invalidate();
    }
}
