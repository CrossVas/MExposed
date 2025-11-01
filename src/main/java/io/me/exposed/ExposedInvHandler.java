package io.me.exposed;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.me.storage.NetworkStorage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.EmptyFluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ExposedInvHandler implements IItemHandler, IFluidHandler {

    NetworkStorage storage;

    public ExposedInvHandler(NetworkStorage storage) {
        this.storage = storage;
    }

    List<AEFluidKey> getFluidKeys() {
        return storage.getAvailableStacks().keySet().stream().filter(aeKey -> aeKey instanceof AEFluidKey).map(aeKey -> ((AEFluidKey) aeKey)).toList();
    }

    List<AEItemKey> getItemKeys() {
        return storage.getAvailableStacks().keySet().stream().filter(aeKey -> aeKey instanceof AEItemKey).map(aeKey -> ((AEItemKey) aeKey)).toList();
    }

    @Override
    public int getTanks() {
        return getFluidKeys().size();
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        if (tank >= getFluidKeys().size())
            return FluidStack.EMPTY;

        return getFluidKeys().get(tank).toStack(((int) storage.extract(getFluidKeys().get(tank), Integer.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty())));
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack fluidStack) {
        return storage.insert(AEFluidKey.of(fluidStack), fluidStack.getAmount(), Actionable.SIMULATE, IActionSource.empty()) > 0;
    }

    @Override
    public int fill(FluidStack fluidStack, FluidAction fluidAction) {
        if (MExposedConfig.COMMON.readOnly.get()) return 0;
        return (int) storage.insert(AEFluidKey.of(fluidStack), fluidStack.getAmount(), fluidAction == FluidAction.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE, IActionSource.empty());
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack fluidStack, FluidAction fluidAction) {
        FluidStack copy = fluidStack.copy();
        if (copy.getFluid() instanceof EmptyFluid) return FluidStack.EMPTY;

        copy.setAmount(((int) storage.extract(AEFluidKey.of(fluidStack), fluidStack.getAmount(), fluidAction == FluidAction.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE, IActionSource.empty())));
        return copy.getAmount() > 0 ? copy : FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int amount, FluidAction fluidAction) {
        return drain(getFluidInTank(0), fluidAction);
    }

    @Override
    public int getSlots() {
        return getItemKeys().size() + 16;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot >= getItemKeys().size())
            return ItemStack.EMPTY;

        return getItemKeys().get(slot).toStack(((int) storage.extract(getItemKeys().get(slot), Integer.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty())));
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        ItemStack copy = stack.copy();
        if (MExposedConfig.COMMON.readOnly.get()) return copy;
        copy.setCount(copy.getCount() - (int) storage.insert(AEItemKey.of(stack), stack.getCount(), simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty()));
        return copy;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemStack copy = getStackInSlot(slot).copy();
        AEItemKey key = AEItemKey.of(copy);
        if (key == null) return ItemStack.EMPTY;

        copy.setCount((int) storage.extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, IActionSource.empty()));
        return copy;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return insertItem(slot, stack, true).getCount() == 0;
    }
}
