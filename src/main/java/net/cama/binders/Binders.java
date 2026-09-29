package net.cama.binders;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

@Mod(Binders.MODID)
public class Binders
{
    public static final String MODID = "binders";

    public Binders()
    {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
        // Client-only marking is handled by displayTest="IGNORE_SERVER_VERSION" in mods.toml
    }
}
