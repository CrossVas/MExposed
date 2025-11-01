package io.me.exposed;

import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.Api;
import appeng.fluids.util.AEFluidStack;
import appeng.me.helpers.BaseActionSource;
import appeng.util.item.AEItemStack;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

public class ExposedInvHandler implements IItemHandler, IFluidHandler {

    private final IMEInventoryHandler<IAEItemStack> itemStorage;
    private final IMEInventoryHandler<IAEFluidStack> fluidStorage;

    private final IStorageChannel<IAEItemStack> itemChannel;
    private final IStorageChannel<IAEFluidStack> fluidChannel;

    public ExposedInvHandler(IStorageGrid storage) {
        this.itemChannel = Api.instance().storage().getStorageChannel(IItemStorageChannel.class);
        this.fluidChannel = Api.instance().storage().getStorageChannel(IFluidStorageChannel.class);
        this.itemStorage = storage.getInventory(itemChannel);
        this.fluidStorage = storage.getInventory(fluidChannel);
    }

    // Items
    private List<IAEItemStack> getItemStacks() {
        IItemList<IAEItemStack> list = itemChannel.createList();
        itemStorage.getAvailableItems(list);
        List<IAEItemStack> stacks = new ArrayList<>();
        for (IAEItemStack s : list) stacks.add(s.copy());
        return stacks;
    }

    // Fluids
    private List<IAEFluidStack> getFluidStacks() {
        IItemList<IAEFluidStack> list = fluidChannel.createList();
        fluidStorage.getAvailableItems(list);
        List<IAEFluidStack> stacks = new ArrayList<>();
        for (IAEFluidStack s : list) stacks.add(s.copy());
        return stacks;
    }

    @Override
    public int getSlots() {
        return getItemStacks().size() + 16;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        List<IAEItemStack> stacks = getItemStacks();
        if (slot >= stacks.size()) return ItemStack.EMPTY;
        IAEItemStack aeStack = itemStorage.extractItems(stacks.get(slot), Actionable.SIMULATE, new BaseActionSource());
        return stacks.get(slot).setStackSize(aeStack.getStackSize()).createItemStack();
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack copy = stack.copy();
        if (MExposedConfig.COMMON.readOnly.get()) return copy;
        IAEItemStack toInject = AEItemStack.fromItemStack(stack);
        if (toInject == null) return stack;

        IAEItemStack leftover = itemStorage.injectItems(toInject, simulate ? Actionable.SIMULATE : Actionable.MODULATE, new BaseActionSource());

        if (leftover == null) {
            return ItemStack.EMPTY;
        } else {
            copy.setCount((int) leftover.getStackSize());
            return copy;
        }
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0) return ItemStack.EMPTY;

        List<IAEItemStack> stacks = getItemStacks();
        if (slot < 0 || slot >= stacks.size()) return ItemStack.EMPTY;

        IAEItemStack toExtract = stacks.get(slot).copy();
        toExtract.setStackSize(amount);

        IAEItemStack extracted = itemStorage.extractItems(toExtract, simulate ? Actionable.SIMULATE : Actionable.MODULATE, new BaseActionSource());
        if (extracted == null || extracted.getStackSize() <= 0) return ItemStack.EMPTY;

        ItemStack result = extracted.createItemStack();
        long count = Math.min(extracted.getStackSize(), Integer.MAX_VALUE);
        result.setCount((int) count);
        return result;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return insertItem(slot, stack, true).getCount() == 0;
    }

    @Override
    public int getTanks() {
        return getFluidStacks().size();
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        List<IAEFluidStack> fluids = getFluidStacks();
        if (tank >= fluids.size()) return FluidStack.EMPTY;
        IAEFluidStack aeFluidStack = fluidStorage.extractItems(fluids.get(tank), Actionable.SIMULATE, new BaseActionSource());
        return aeFluidStack.getFluidStack();
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack stack) {
        IAEFluidStack aeFluidStack = fluidStorage.injectItems(getFluidStacks().get(tank), Actionable.SIMULATE, new BaseActionSource());
        return aeFluidStack.getStackSize() > 0;
    }

    @Override
    public int fill(FluidStack stack, FluidAction action) {
        if (stack.isEmpty() || MExposedConfig.COMMON.readOnly.get()) return 0;

        IAEFluidStack toInsert = AEFluidStack.fromFluidStack(stack);
        if (toInsert == null) return 0;

        IAEFluidStack leftover = fluidStorage.injectItems(
                toInsert.copy(),
                action == FluidAction.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE,
                new BaseActionSource());

        long inserted = (leftover == null) ? toInsert.getStackSize() : (toInsert.getStackSize() - leftover.getStackSize());
        return (int) Math.min(inserted, Integer.MAX_VALUE);
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) return FluidStack.EMPTY;

        IAEFluidStack request = AEFluidStack.fromFluidStack(resource);
        if (request == null) return FluidStack.EMPTY;

        IAEFluidStack extracted = fluidStorage.extractItems(
                request.copy(),
                action == FluidAction.EXECUTE ? Actionable.MODULATE : Actionable.SIMULATE,
                new BaseActionSource());

        if (extracted == null || extracted.getStackSize() <= 0) return FluidStack.EMPTY;

        FluidStack out = extracted.getFluidStack().copy();
        long count = Math.min(extracted.getStackSize(), Integer.MAX_VALUE);
        out.setAmount((int) count);
        return out;
    }

    @Nonnull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return drain(getFluidInTank(0), action);
    }
}