package io.me.exposed.mixin;

import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.grid.AENetworkPowerBlockEntity;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.storage.NetworkStorage;
import io.me.exposed.ExposedInvHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ControllerBlockEntity.class)
public abstract class ControllerBlockEntityMixin extends AENetworkPowerBlockEntity {

    @Unique
    private LazyOptional<ExposedInvHandler> exposedCap = LazyOptional.empty();

    public ControllerBlockEntityMixin(BlockEntityType<?> blockEntityType, BlockPos pos, BlockState blockState) {
        super(blockEntityType, pos, blockState);
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        IManagedGridNode node = getMainNode();
        if (node != null && node.getGrid() != null && (cap == ForgeCapabilities.ITEM_HANDLER || cap == ForgeCapabilities.FLUID_HANDLER)) {
            if (!exposedCap.isPresent()) {
                exposedCap = LazyOptional.of(() -> new ExposedInvHandler(((NetworkStorage) node.getGrid().getStorageService().getInventory())));
            }
            return exposedCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        exposedCap.invalidate();
    }
}
