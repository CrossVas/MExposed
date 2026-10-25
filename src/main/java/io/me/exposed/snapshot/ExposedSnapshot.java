package io.me.exposed.snapshot;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.me.storage.NetworkStorage;
import io.me.exposed.MExposed;
import io.me.exposed.MExposedConfig;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.util.ArrayList;

/**
 * Slot-ordered view of a network's items and fluids.
 * <p>
 * Network inserts/extracts are applied to it as exact deltas (see NetworkStorageMixin), so it stays correct within a
 * tick without rescanning. It is fully resynced once per tick, which also catches changes AE2 cannot report
 * (storage-bus inventories, interfaces, direct cell access). Server thread only.
 * <p>
 * Emptied keys keep their slot (amount 0) until the next resync, so slot indices never shift within a tick.
 * Surviving keys keep their relative order across resyncs; new keys are appended.
 */
public final class ExposedSnapshot {

    private final ArrayList<AEKey> items = new ArrayList<>();
    private final ArrayList<AEKey> fluids = new ArrayList<>();
    private final Object2LongOpenHashMap<AEKey> amounts = new Object2LongOpenHashMap<>();


    private boolean dirty = true;
    private int inFlight;
    private Runnable changeListener;
    private boolean pendingNotify;
    private long tick = -1;

    public void markDirty() {
        dirty = true;
    }

    /** Set by the capability provider so consumers that cache the handler (e.g. Tom's Storage) re-query it. */
    public void setChangeListener(NetworkStorage storage, Runnable listener) {
        this.changeListener = listener;
        ExposedTicker.watch(storage);
    }

    /** The network's mounted storages changed: resync and notify consumers after the end-of-tick resync. */
    public void notifyStorageChanged() {
        dirty = true;
        pendingNotify = true;
    }

    public void ensureFresh(NetworkStorage storage) {
        long now = ExposedTicker.now();
        if (dirty || tick != now) rebuild(storage, now);
    }

    private void rebuild(NetworkStorage storage, long now) {
        if (inFlight > 0 && MExposedConfig.isDebug()) {
            MExposed.LOGGER.warn("[MExposed] snapshot rebuilt while {} network operation(s) in flight", inFlight);
        }

        Object2LongOpenHashMap<AEKey> fresh = new Object2LongOpenHashMap<>();
        for (Object2LongMap.Entry<AEKey> entry : storage.getAvailableStacks()) {
            AEKey key = entry.getKey();
            if (entry.getLongValue() > 0 && (key instanceof AEItemKey || key instanceof AEFluidKey)) {
                fresh.put(key, entry.getLongValue());
            }
        }

        ArrayList<AEKey> newItems = new ArrayList<>(fresh.size());
        ArrayList<AEKey> newFluids = new ArrayList<>();

        for (AEKey key : items) {
            if (fresh.containsKey(key)) newItems.add(key);
        }

        for (AEKey key : fluids) {
            if (fresh.containsKey(key)) newFluids.add(key);
        }

        for (AEKey key : fresh.keySet()) {
            if (amounts.containsKey(key)) continue;

            if (key instanceof AEItemKey) {
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

    /** Per-tick poll: resyncs, and notifies the listener if the stored keys changed or a mount/unmount happened this tick. */
    public void poll(NetworkStorage storage) {
        dirty = true;
        ensureFresh(storage);
        if (pendingNotify) {
            pendingNotify = false;
            fireChanged();
        }
    }

    /** Applies a confirmed network change; ignored while stale or dirty, since the next read resyncs anyway. */
    public void applyDelta(AEKey key, long delta) {
        if (dirty || tick != ExposedTicker.now() || delta == 0) return;
        if (!(key instanceof AEItemKey || key instanceof AEFluidKey)) return;

        boolean known = amounts.containsKey(key);
        long updated = Math.max(0L, amounts.getLong(key) + delta);
        if (!known) {
            if (updated <= 0) return;
            (key instanceof AEItemKey ? items : fluids).add(key);
        }
        amounts.put(key, updated);
    }

    public void enter() {
        inFlight++;
    }

    public void exit() {
        if (inFlight > 0) inFlight--;
    }

    /** Debug only: logs when the tracked amount of a key differs from the live network amount. */
    public void verify(NetworkStorage storage, AEKey key, String op, long requested, long moved) {
        if (dirty || tick != ExposedTicker.now() || !MExposedConfig.isDebug()) {
            return;
        }

        long live = storage.getAvailableStacks().get(key);
        long tracked = amounts.getLong(key);
        if (live != tracked) {
            MExposed.LOGGER.warn("[MExposed] MISMATCH after {} {} requested={} moved={} tracked={} live={} inFlight={}", op, key, requested, moved, tracked, live, inFlight);
        }
    }

    public int itemCount() {
        return items.size();
    }

    public int fluidCount() {
        return fluids.size();
    }

    public AEKey itemKey(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    public AEKey fluidKey(int index) {
        return index >= 0 && index < fluids.size() ? fluids.get(index) : null;
    }

    public long amount(AEKey key) {
        return amounts.getLong(key);
    }

    /** First fluid that currently has a positive amount, or null. */
    public AEKey firstAvailableFluid() {
        for (AEKey key : fluids) {
            if (amounts.getLong(key) > 0) {
                return key;
            }
        }
        return null;
    }
}
