package io.me.exposed;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class MExposedConfig {

    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class Common {
        public final ModConfigSpec.BooleanValue readOnly;

        public Common(ModConfigSpec.Builder builder) {
            builder.push("general");
            readOnly = builder.comment("Make AE2 Controller work in ReadOnly Mode, meaning items can be extracted, but can't be inserted.").translation("Read Only").define("readOnly", true);
        }
    }
}
