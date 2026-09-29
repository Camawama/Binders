package net.cama.binders.client;

import net.cama.binders.Binders;
import net.cama.binders.data.BinderManager;
import net.camacraft.camafy.client.gui.hud.HudOverlays;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = Binders.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // FMLClientSetupEvent is dispatched in parallel; run the load on the main thread
        event.enqueueWork(BinderManager::load);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        // Above all other overlays; Camafy skips it while the GUI is hidden (F1) or there is
        // no player, and keeps a throwing binder from breaking the rest of the HUD.
        HudOverlays.top(event, "keybind_overlay", KeybindOverlay.INSTANCE);
    }
}
