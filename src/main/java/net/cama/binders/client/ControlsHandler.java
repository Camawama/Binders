package net.cama.binders.client;

import net.cama.binders.Binders;
import net.cama.binders.Config;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Binders.MODID, value = Dist.CLIENT)
public class ControlsHandler {

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof ControlsScreen) {
            ControlsScreen screen = (ControlsScreen) event.getScreen();
            
            event.addListener(Button.builder(
                    Component.literal("Binders Overlay: " + (Config.SHOW_OVERLAY.get() ? "ON" : "OFF")),
                    button -> {
                        boolean newState = !Config.SHOW_OVERLAY.get();
                        Config.SHOW_OVERLAY.set(newState);
                        Config.SHOW_OVERLAY.save();
                        button.setMessage(Component.literal("Binders Overlay: " + (newState ? "ON" : "OFF")));
                    })
                    .bounds(screen.width / 2 + 160, 10, 100, 20) // Top right corner
                    .build());
        }
    }
}
