package net.cama.binders.client;

import net.cama.binders.Binders;
import net.cama.binders.data.BinderManager;
import net.cama.binders.data.BinderStats;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Binders.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        BinderManager.load();
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("keybind_overlay", KeybindOverlay.INSTANCE);
    }
    
    // We need to listen to login events to clear session stats
    // This needs to be on the FORGE bus, not the MOD bus.
    // So we need a separate inner class or just register it manually.
    // Let's use a separate class for Forge events.
}
