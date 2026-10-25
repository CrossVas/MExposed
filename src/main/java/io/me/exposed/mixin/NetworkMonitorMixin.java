package io.me.exposed.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.data.IAEStack;
import appeng.me.cache.GridStorageCache;
import appeng.me.cache.NetworkMonitor;
import io.me.exposed.snapshot.ExposedSnapshot;
import io.me.exposed.snapshot.ExposedSnapshotHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NetworkMonitor.class, remap = false)
public abstract class NetworkMonitorMixin {

    @Shadow
    @Final
    private GridStorageCache myGridCache;

    @Inject(method = "injectItems", at = @At("HEAD"))
    private void mexposed$beforeInsert(IAEStack<?> input, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack<?>> cir) {
        ExposedSnapshot snapshot = ((ExposedSnapshotHolder) myGridCache).mexposed$peekSnapshot();
        if (snapshot != null && mode == Actionable.MODULATE)
            snapshot.enter(input.getStackSize());
    }

    @Inject(method = "extractItems", at = @At("HEAD"))
    private void mexposed$beforeExtract(IAEStack<?> request, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack<?>> cir) {
        ExposedSnapshot snapshot = ((ExposedSnapshotHolder) myGridCache).mexposed$peekSnapshot();
        if (snapshot != null && mode == Actionable.MODULATE)
            snapshot.enter(request.getStackSize());
    }

    // injectItems returns the leftover (null = all accepted).
    @Inject(method = "injectItems", at = @At("RETURN"))
    private void mexposed$afterInsert(IAEStack<?> input, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack<?>> cir) {
        ExposedSnapshot snapshot = ((ExposedSnapshotHolder) myGridCache).mexposed$peekSnapshot();
        if (snapshot != null && mode == Actionable.MODULATE) {
            long requested = snapshot.exit(input.getStackSize());
            IAEStack<?> leftover = cir.getReturnValue();
            long inserted = leftover == null ? requested : requested - leftover.getStackSize();
            snapshot.applyDelta(input, inserted);
            snapshot.verify(myGridCache, input, "insert", requested, inserted);
        }
    }

    // extractItems returns what was actually extracted (null = nothing).
    @Inject(method = "extractItems", at = @At("RETURN"))
    private void mexposed$afterExtract(IAEStack<?> request, Actionable mode, IActionSource src, CallbackInfoReturnable<IAEStack<?>> cir) {
        ExposedSnapshot snapshot = ((ExposedSnapshotHolder) myGridCache).mexposed$peekSnapshot();
        if (snapshot != null && mode == Actionable.MODULATE) {
            long requested = snapshot.exit(request.getStackSize());
            IAEStack<?> result = cir.getReturnValue();
            long extracted = result == null ? 0L : result.getStackSize();
            snapshot.applyDelta(request, -extracted);
            snapshot.verify(myGridCache, request, "extract", requested, extracted);
        }
    }
}
