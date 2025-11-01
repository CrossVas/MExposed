package io.me.exposed;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public class MExposedConfig {

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class Common {
        public final ForgeConfigSpec.BooleanValue readOnly;

        public Common(ForgeConfigSpec.Builder builder) {
            builder.push("general");
            readOnly = builder.comment("Make AE2 Controller work in ReadOnly Mode, meaning items can be extracted, but can't be inserted.").define("readOnly", true);
        }
    }
}
