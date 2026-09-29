package net.cama.binders.client;

import net.cama.binders.Binders;
import net.cama.binders.Config;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
            Screen screen = event.getScreen();

            event.addListener(Button.builder(
                    overlayButtonText(Config.SHOW_OVERLAY.get()),
                    button -> {
                        boolean newState = !Config.SHOW_OVERLAY.get();
                        Config.SHOW_OVERLAY.set(newState);
                        Config.SHOW_OVERLAY.save();
                        button.setMessage(overlayButtonText(newState));
                    })
                    // Anchored to the right edge; a fixed width/2 offset was clipped
                    // off-screen at common GUI scales
                    .bounds(screen.width - 105, 10, 100, 20)
                    .build());
        }
    }

    private static Component overlayButtonText(boolean on) {
        return Component.translatable("binders.button.overlay",
                Component.translatable(on ? "binders.state.on" : "binders.state.off"));
    }
}
