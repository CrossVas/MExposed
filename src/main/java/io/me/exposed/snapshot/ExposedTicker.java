package io.me.exposed.snapshot;

import appeng.me.storage.NetworkStorage;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@EventBusSubscriber
public final class ExposedTicker {

    private static long tick;
    private static final Set<NetworkStorage> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void watch(NetworkStorage storage) {
        WATCHED.add(storage);
    }

    private ExposedTicker() {
    }

    public static long now() {
        return tick;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Pre event) {
        tick++;
    }

    @SubscribeEvent
    public static void onServerTickPost(ServerTickEvent.Post event) {
        for (NetworkStorage storage : new ArrayList<>(WATCHED))
            ((ExposedSnapshotHolder) storage).mexposed$snapshot().poll(storage);
    }
}
