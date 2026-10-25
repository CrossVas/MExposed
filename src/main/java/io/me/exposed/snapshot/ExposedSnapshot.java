package io.me.exposed.snapshot;

import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IStorageChannel;
import appeng.api.storage.channels.IFluidStorageChannel;
import appeng.api.storage.channels.IItemStorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.core.Api;
import io.me.exposed.MExposed;
import io.me.exposed.MExposedConfig;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.ArrayDeque;
import java.util.ArrayList;

/**
 * Slot-ordered view of a network's items and fluids.
 * <p>
 * Network inserts/extracts are applied to it as exact deltas (see NetworkMonitorMixin), so it stays correct within a
 * tick without rescanning. It is fully resynced once per tick, which also catches changes AE2 cannot report
 * (storage-bus inventories, interfaces, direct cell access). Server thread only.
 * <p>
 * Emptied keys keep their slot (amount 0) until the next resync, so slot indices never shift within a tick.
 * Surviving keys keep their relative order across resyncs; new keys are appended.
 * <p>
 * Keys are private copies of AE2 stacks; their stack size is meaningless (AE2 stack equality ignores it).
 */
public final class ExposedSnapshot {

    private final ArrayList<IAEStack<?>> items = new ArrayList<>();
    private final ArrayList<IAEStack<?>> fluids = new ArrayList<>();
    private final Object2LongOpenHashMap<IAEStack<?>> amounts = new Object2LongOpenHashMap<>();
    private final ArrayDeque<Long> pending = new ArrayDeque<>();

    private boolean dirty = true;
    private Runnable changeListener;
    private boolean pendingNotify;
    private long tick = -1;

    public void markDirty() {
        dirty = true;
    }

    /** Set by the capability provider so consumers that cache the handler (e.g. Tom's Storage) re-query it. */
    public void setChangeListener(IStorageGrid grid, Runnable listener) {
        this.changeListener = listener;
        ExposedTicker.watch(grid);
    }

    /** The network's cell providers changed: resync and notify consumers after the end-of-tick resync. */
    public void notifyStorageChanged() {
        dirty = true;
        pendingNotify = true;
    }

    public void ensureFresh(IStorageGrid grid) {
        long now = ExposedTicker.now();
        if (dirty || tick != now) rebuild(grid, now);
    }

    private static Object2LongOpenHashMap<IAEStack<?>> scan(IStorageGrid grid) {
        Object2LongOpenHashMap<IAEStack<?>> out = new Object2LongOpenHashMap<>();
        collect(grid, Api.instance().storage().getStorageChannel(IItemStorageChannel.class), out);
        collect(grid, Api.instance().storage().getStorageChannel(IFluidStorageChannel.class), out);
        return out;
    }

    private static <T extends IAEStack<T>> void collect(IStorageGrid grid, IStorageChannel<T> channel, Object2LongOpenHashMap<IAEStack<?>> out) {
        IItemList<T> list = grid.getInventory(channel).getAvailableItems(channel.createList());
        for (T stack : list) {
            if (stack.getStackSize() > 0) out.put(stack.copy(), stack.getStackSize());
        }
    }

    private void rebuild(IStorageGrid grid, long now) {
        if (!pending.isEmpty() && MExposedConfig.isDebug()) {
            MExposed.LOGGER.warn("[MExposed] snapshot rebuilt while {} network operation(s) in flight", pending.size());
        }

        Object2LongOpenHashMap<IAEStack<?>> fresh = scan(grid);

        ArrayList<IAEStack<?>> newItems = new ArrayList<>(fresh.size());
        ArrayList<IAEStack<?>> newFluids = new ArrayList<>();

        for (IAEStack<?> key : items) {
            if (fresh.containsKey(key)) newItems.add(key);
        }

        for (IAEStack<?> key : fluids) {
            if (fresh.containsKey(key)) newFluids.add(key);
        }

        for (IAEStack<?> key : fresh.keySet()) {
            if (amounts.containsKey(key)) continue;

            if (key instanceof IAEItemStack) {
                newItems.add(key);
            } else newFluids.add(key);
        }

        boolean changed = !items.equals(newItems) || !fluids.equals(newFluids) || amounts.size() != fresh.size();
        items.clear();
        items.addAll(newItems);
        fluids.clear();
        fluids.addAll(newFluids);
        amounts.clear();
        amounts.putAll(fresh);

        dirty = false;
        tick = now;
        if (changed) fireChanged();
    }

    private void fireChanged() {
        if (changeListener != null) changeListener.run();
    }

    /** Per-tick poll: resyncs, and notifies the listener if the stored keys changed or a cell provider changed this tick. */
    public void poll(IStorageGrid grid) {
        dirty = true;
        ensureFresh(grid);
        if (pendingNotify) {
            pendingNotify = false;
            fireChanged();
        }
    }

    /** Applies a confirmed network change; ignored while stale or dirty, since the next read resyncs anyway. */
    public void applyDelta(IAEStack<?> key, long delta) {
        if (dirty || tick != ExposedTicker.now() || delta == 0) return;
        if (!(key instanceof IAEItemStack || key instanceof IAEFluidStack)) return;

        boolean known = amounts.containsKey(key);
        long updated = Math.max(0L, amounts.getLong(key) + delta);
        if (!known) {
            if (updated <= 0) return;
            key = key.copy();
            (key instanceof IAEItemStack ? items : fluids).add(key);
        }
        amounts.put(key, updated);
    }

    /** Called before a MODULATE operation with the requested amount; pair with {@link #exit(long)}. */
    public void enter(long requested) {
        pending.push(requested);
    }

    /** Returns the amount passed to the matching {@link #enter(long)}. */
    public long exit(long fallback) {
        Long requested = pending.poll();
        return requested != null ? requested : fallback;
    }

    /** Debug only: logs when the tracked amount of a key differs from the live network amount. */
    public void verify(IStorageGrid grid, IAEStack<?> key, String op, long requested, long moved) {
        if (dirty || tick != ExposedTicker.now() || !MExposedConfig.isDebug()) {
            return;
        }

        long live = scan(grid).getLong(key);
        long tracked = amounts.getLong(key);
        if (live != tracked) {
            MExposed.LOGGER.warn("[MExposed] MISMATCH after {} {} requested={} moved={} tracked={} live={} inFlight={}", op, key, requested, moved, tracked, live, pending.size());
        }
    }

    public int itemCount() {
        return items.size();
    }

    public int fluidCount() {
        return fluids.size();
    }

    public IAEItemStack itemKey(int index) {
        return index >= 0 && index < items.size() ? (IAEItemStack) items.get(index) : null;
    }

    public IAEFluidStack fluidKey(int index) {
        return index >= 0 && index < fluids.size() ? (IAEFluidStack) fluids.get(index) : null;
    }

    public long amount(IAEStack<?> key) {
        return amounts.getLong(key);
    }

    /** First fluid that currently has a positive amount, or null. */
    public IAEFluidStack firstAvailableFluid() {
        for (IAEStack<?> key : fluids) {
            if (amounts.getLong(key) > 0) {
                return (IAEFluidStack) key;
            }
        }
        return null;
    }
}
