package io.me.exposed.snapshot;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.me.storage.NetworkStorage;
import io.me.exposed.MExposedConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;

public class SnapshotInvHandler implements IItemHandler, IFluidHandler {

    private static final int EXTRA_SLOTS = 16;

    private final NetworkStorage storage;
    private final ExposedSnapshot snapshot;

    public SnapshotInvHandler(NetworkStorage storage) {
        this.storage = storage;
        this.snapshot = ((ExposedSnapshotHolder) storage).mexposed$snapshot();
    }

    private ExposedSnapshot view() {
        snapshot.ensureFresh(storage);
        return snapshot;
    }

    // Slot/tank counts start every scan, so they resync; this catches changes AE2 can't report (e.g. a removed storage-bus chest) within the same tick.
    private ExposedSnapshot freshView() {
        snapshot.markDirty();
        return view();
    }

    private static int safeAmount(long amount) {
        return (int) Mth.clamp(amount, 0L, Integer.MAX_VALUE);
    }

    private static Actionable mode(boolean execute) {
        return execute ? Actionable.MODULATE : Actionable.SIMULATE;
    }

    @Override
    public int getTanks() {
        return freshView().fluidCount();
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
        ExposedSnapshot view = view();
        AEKey key = view.fluidKey(tank);
        if (key == null)
            return FluidStack.EMPTY;

        int amount = safeAmount(view.amount(key));
        return amount > 0 ? ((AEFluidKey) key).toStack(amount) : FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack fluidStack) {
        if (fluidStack.isEmpty() || MExposedConfig.COMMON.readOnly.get())
            return false;

        return storage.insert(AEFluidKey.of(fluidStack), fluidStack.getAmount(), Actionable.SIMULATE, IActionSource.empty()) > 0;
    }

    @Override
    public int fill(@NotNull FluidStack fluidStack, @NotNull FluidAction fluidAction) {
        if (fluidStack.isEmpty() || MExposedConfig.COMMON.readOnly.get())
            return 0;

        return safeAmount(storage.insert(AEFluidKey.of(fluidStack), fluidStack.getAmount(), mode(fluidAction.execute()), IActionSource.empty()));
    }

    @NotNull
    @Override
    public FluidStack drain(@NotNull FluidStack fluidStack, @NotNull FluidAction fluidAction) {
        if (fluidStack.isEmpty())
            return FluidStack.EMPTY;

        int drained = safeAmount(storage.extract(AEFluidKey.of(fluidStack), fluidStack.getAmount(), mode(fluidAction.execute()), IActionSource.empty()));
        if (drained > 0) {
            FluidStack copy = fluidStack.copy();
            copy.setAmount(drained);
            return copy;
        } else return FluidStack.EMPTY;
    }

    @NotNull
    @Override
    public FluidStack drain(int amount, @NotNull FluidAction fluidAction) {
        if (amount <= 0)
            return FluidStack.EMPTY;

        AEKey key = view().firstAvailableFluid();
        if (key == null)
            return FluidStack.EMPTY;

        int drained = safeAmount(storage.extract(key, amount, mode(fluidAction.execute()), IActionSource.empty()));
        return drained > 0 ? ((AEFluidKey) key).toStack(drained) : FluidStack.EMPTY;
    }

    @Override
    public int getSlots() {
        return freshView().itemCount() + EXTRA_SLOTS;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        ExposedSnapshot view = view();
        AEKey key = view.itemKey(slot);
        if (key == null)
            return ItemStack.EMPTY;

        int amount = safeAmount(view.amount(key));
        return amount > 0 ? ((AEItemKey) key).toStack(amount) : ItemStack.EMPTY;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || MExposedConfig.COMMON.readOnly.get())
            return stack;

        int inserted = safeAmount(storage.insert(AEItemKey.of(stack), stack.getCount(), mode(!simulate), IActionSource.empty()));
        if (inserted <= 0)
            return stack;

        ItemStack remainder = stack.copy();
        remainder.shrink(inserted);
        return remainder;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0)
            return ItemStack.EMPTY;

        AEKey key = view().itemKey(slot);
        if (key == null)
            return ItemStack.EMPTY;

        AEItemKey itemKey = (AEItemKey) key;
        int extracted = safeAmount(storage.extract(itemKey, Math.min(amount, itemKey.getMaxStackSize()), mode(!simulate), IActionSource.empty()));
        return extracted > 0 ? itemKey.toStack(extracted) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        if (stack.isEmpty() || MExposedConfig.COMMON.readOnly.get())
            return false;

        return storage.insert(AEItemKey.of(stack), stack.getCount(), Actionable.SIMULATE, IActionSource.empty()) > 0;
    }
}
