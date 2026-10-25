package io.me.exposed.snapshot;

import appeng.api.config.Actionable;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.Api;
import appeng.fluids.util.AEFluidStack;
import appeng.me.helpers.BaseActionSource;
import appeng.util.item.AEItemStack;
import io.me.exposed.MExposedConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

public class SnapshotInvHandler implements IItemHandler, IFluidHandler {

    private static final int EXTRA_SLOTS = 16;

    private final IStorageGrid grid;
    private final IMEMonitor<IAEItemStack> itemStorage;
    private final IMEMonitor<IAEFluidStack> fluidStorage;
    private final ExposedSnapshot snapshot;

    public SnapshotInvHandler(IStorageGrid grid) {
        this.grid = grid;
        this.itemStorage = grid.getInventory(Api.instance().storage().getStorageChannel(IItemStorageChannel.class));
        this.fluidStorage = grid.getInventory(Api.instance().storage().getStorageChannel(IFluidStorageChannel.class));
        this.snapshot = ((ExposedSnapshotHolder) grid).mexposed$snapshot();
    }

    public IStorageGrid getGrid() {
        return grid;
    }

    private ExposedSnapshot view() {
        snapshot.ensureFresh(grid);
        return snapshot;
    }

    // Slot/tank counts start every scan, so they resync; this catches changes AE2 can't report (e.g. a removed storage-bus chest) within the same tick.
    private ExposedSnapshot freshView() {
        snapshot.markDirty();
        return view();
    }

    private static int safeAmount(long amount) {
        return (int) MathHelper.clamp(amount, 0L, Integer.MAX_VALUE);
    }

    private static Actionable mode(boolean execute) {
        return execute ? Actionable.MODULATE : Actionable.SIMULATE;
    }

    // AE2 returns the leftover (null = everything accepted), so the inserted amount is requested - leftover.
    private static long inserted(IAEStack<?> request, IAEStack<?> leftover) {
        return leftover == null ? request.getStackSize() : request.getStackSize() - leftover.getStackSize();
    }

    private static long extracted(IAEStack<?> result) {
        return result == null ? 0L : result.getStackSize();
    }

    @Override
    public int getTanks() {
        return freshView().fluidCount();
    }

    @Nonnull
    @Override
    public FluidStack getFluidInTank(int tank) {
        ExposedSnapshot view = view();
        IAEFluidStack key = view.fluidKey(tank);
        if (key == null)
            return FluidStack.EMPTY;

        int amount = safeAmount(view.amount(key));
        return amount > 0 ? key.copy().setStackSize(amount).getFluidStack() : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @Nonnull FluidStack fluidStack) {
        if (fluidStack.isEmpty() || MExposedConfig.COMMON.readOnly.get())
            return false;

        IAEFluidStack request = AEFluidStack.fromFluidStack(fluidStack);
        return request != null && inserted(request, fluidStorage.injectItems(request.copy(), Actionable.SIMULATE, new BaseActionSource())) > 0;
    }

    @Override
    public int fill(FluidStack fluidStack, FluidAction fluidAction) {
        if (fluidStack.isEmpty() || MExposedConfig.COMMON.readOnly.get())
            return 0;

        IAEFluidStack request = AEFluidStack.fromFluidStack(fluidStack);
        if (request == null)
            return 0;

        IAEFluidStack leftover = fluidStorage.injectItems(request.copy(), mode(fluidAction.execute()), new BaseActionSource());
        return safeAmount(inserted(request, leftover));
    }

    @Nonnull
    @Override
    public FluidStack drain(FluidStack fluidStack, FluidAction fluidAction) {
        if (fluidStack.isEmpty())
            return FluidStack.EMPTY;

        IAEFluidStack request = AEFluidStack.fromFluidStack(fluidStack);
        if (request == null)
            return FluidStack.EMPTY;

        int drained = safeAmount(extracted(fluidStorage.extractItems(request, mode(fluidAction.execute()), new BaseActionSource())));
        if (drained > 0) {
            FluidStack copy = fluidStack.copy();
            copy.setAmount(drained);
            return copy;
        } else return FluidStack.EMPTY;
    }

    @Nonnull
    @Override
    public FluidStack drain(int amount, FluidAction fluidAction) {
        if (amount <= 0)
            return FluidStack.EMPTY;

        IAEFluidStack key = view().firstAvailableFluid();
        if (key == null)
            return FluidStack.EMPTY;

        IAEFluidStack extracted = fluidStorage.extractItems(key.copy().setStackSize(amount), mode(fluidAction.execute()), new BaseActionSource());
        int drained = safeAmount(extracted(extracted));
        return drained > 0 ? extracted.copy().setStackSize(drained).getFluidStack() : FluidStack.EMPTY;
    }

    @Override
    public int getSlots() {
        return freshView().itemCount() + EXTRA_SLOTS;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        ExposedSnapshot view = view();
        IAEItemStack key = view.itemKey(slot);
        if (key == null)
            return ItemStack.EMPTY;

        int amount = safeAmount(view.amount(key));
        return amount > 0 ? key.copy().setStackSize(amount).createItemStack() : ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || MExposedConfig.COMMON.readOnly.get())
            return stack;

        IAEItemStack request = AEItemStack.fromItemStack(stack);
        if (request == null)
            return stack;

        IAEItemStack leftover = itemStorage.injectItems(request.copy(), mode(!simulate), new BaseActionSource());
        int inserted = safeAmount(inserted(request, leftover));
        if (inserted <= 0)
            return stack;

        ItemStack remainder = stack.copy();
        remainder.shrink(inserted);
        return remainder;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0)
            return ItemStack.EMPTY;

        IAEItemStack key = view().itemKey(slot);
        if (key == null)
            return ItemStack.EMPTY;

        IAEItemStack request = key.copy().setStackSize(Math.min(amount, key.getDefinition().getMaxStackSize()));
        IAEItemStack result = itemStorage.extractItems(request, mode(!simulate), new BaseActionSource());
        int extracted = safeAmount(extracted(result));
        return extracted > 0 ? result.copy().setStackSize(extracted).createItemStack() : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        if (stack.isEmpty() || MExposedConfig.COMMON.readOnly.get())
            return false;

        IAEItemStack request = AEItemStack.fromItemStack(stack);
        return request != null && inserted(request, itemStorage.injectItems(request.copy(), Actionable.SIMULATE, new BaseActionSource())) > 0;
    }
}
