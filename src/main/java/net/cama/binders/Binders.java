package net.cama.binders;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(Binders.MODID)
public class Binders
{
    public static final String MODID = "binders";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Binders()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        MinecraftForge.EVENT_BUS.register(this);

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);

        // Make sure the server doesn't need this mod
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, 
            () -> new IExtensionPoint.DisplayTest(() -> "CLIENT", (a, b) -> true));
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        LOGGER.info("Binders common setup");
    }
}
