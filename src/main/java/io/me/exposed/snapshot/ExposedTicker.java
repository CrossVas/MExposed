package io.me.exposed.snapshot;

import appeng.api.networking.storage.IStorageGrid;
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
    private static final Set<IStorageGrid> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());

    public static void watch(IStorageGrid grid) {
        WATCHED.add(grid);
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
            for (IStorageGrid grid : new ArrayList<>(WATCHED)) {
                ((ExposedSnapshotHolder) grid).mexposed$snapshot().poll(grid);
            }
        }
    }
}
