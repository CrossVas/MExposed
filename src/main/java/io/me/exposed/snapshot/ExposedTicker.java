package io.me.exposed.snapshot;

import appeng.me.storage.NetworkStorage;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

@Mod.EventBusSubscriber
public final class ExposedTicker {

    private static long tick;
    private static final Set<NetworkStorage> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void watch(NetworkStorage storage) {
        WATCHED.add(storage);
    }

    private ExposedTicker() {}

    public static long now() {
        return tick;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            tick++;
        }
    }

    @SubscribeEvent
    public static void onServerTickPost(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            for (NetworkStorage storage : new ArrayList<>(WATCHED)) {
                ((ExposedSnapshotHolder) storage).mexposed$snapshot().poll(storage);
            }
        }
    }
}
