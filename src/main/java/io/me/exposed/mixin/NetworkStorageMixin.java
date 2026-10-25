package io.me.exposed.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import io.me.exposed.snapshot.ExposedSnapshot;
import io.me.exposed.snapshot.ExposedSnapshotHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin implements ExposedSnapshotHolder {

    @Shadow
    private List<?> queuedOperations;

    @Unique
    private ExposedSnapshot mexposed$snapshot;

    @Override
    public ExposedSnapshot mexposed$snapshot() {
        if (mexposed$snapshot == null)
            mexposed$snapshot = new ExposedSnapshot();
        return mexposed$snapshot;
    }

    @Inject(method = "insert", at = @At("HEAD"))
    private void mexposed$beforeInsert(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (mexposed$snapshot != null && mode == Actionable.MODULATE)
            mexposed$snapshot.enter();
    }

    @Inject(method = "extract", at = @At("HEAD"))
    private void mexposed$beforeExtract(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (mexposed$snapshot != null && mode == Actionable.MODULATE)
            mexposed$snapshot.enter();
    }

    @Inject(method = "insert", at = @At("RETURN"))
    private void mexposed$afterInsert(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (mexposed$snapshot != null && mode == Actionable.MODULATE) {
            mexposed$snapshot.exit();
            mexposed$snapshot.applyDelta(what, cir.getReturnValueJ());
            mexposed$snapshot.verify((NetworkStorage) (Object) this, what, "insert", amount, cir.getReturnValueJ());
        }
    }

    @Inject(method = "extract", at = @At("RETURN"))
    private void mexposed$afterExtract(AEKey what, long amount, Actionable mode, IActionSource source, CallbackInfoReturnable<Long> cir) {
        if (mexposed$snapshot != null && mode == Actionable.MODULATE) {
            mexposed$snapshot.exit();
            mexposed$snapshot.applyDelta(what, -cir.getReturnValueJ());
            mexposed$snapshot.verify((NetworkStorage) (Object) this, what, "extract", amount, cir.getReturnValueJ());
        }
    }

    @Inject(method = "mount", at = @At("RETURN"))
    private void mexposed$afterMount(int priority, MEStorage inventory, CallbackInfo ci) {
        if (mexposed$snapshot != null)
            mexposed$snapshot.notifyStorageChanged();
    }

    @Inject(method = "unmount", at = @At("RETURN"))
    private void mexposed$afterUnmount(MEStorage inventory, CallbackInfo ci) {
        if (mexposed$snapshot != null)
            mexposed$snapshot.notifyStorageChanged();
    }

    // Queued mounts/unmounts are applied here, after the call that queued them.
    @Inject(method = "flushQueuedOperations", at = @At("HEAD"))
    private void mexposed$beforeFlush(CallbackInfo ci) {
        if (mexposed$snapshot != null && queuedOperations != null)
            mexposed$snapshot.notifyStorageChanged();
    }
}
