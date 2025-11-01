package io.me.exposed;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(MExposed.ID)
public class MExposed {
    public static final String ID = "mexposed";

    public MExposed() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, MExposedConfig.COMMON_SPEC);
    }
}
