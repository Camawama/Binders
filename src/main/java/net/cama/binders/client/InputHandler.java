package net.cama.binders.client;

import net.cama.binders.Binders;
import net.cama.binders.api.BinderRegistry;
import net.cama.binders.api.IBinder;
import net.cama.binders.data.BinderStats;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Binders.MODID, value = Dist.CLIENT)
public class InputHandler {

    @SubscribeEvent
    public static void onKeyInputDirect(InputEvent.Key event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            checkBinders(event.getKey(), -1);
        }
    }
    
    @SubscribeEvent
    public static void onMouseInputDirect(InputEvent.MouseButton.Pre event) {
        if (event.getAction() == GLFW.GLFW_PRESS) {
            checkBinders(-1, event.getButton());
        }
    }

    private static void checkBinders(int keyCode, int mouseButton) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        for (IBinder binder : BinderRegistry.getAll()) {
            KeyMapping keyMapping = binder.getKeyMapping();
            if (keyMapping == null) continue;
            
            boolean match = false;
            
            if (keyCode != -1) {
                if (keyMapping.matches(keyCode, -1)) {
                    match = true;
                }
            } 
            else if (mouseButton != -1) {
                if (keyMapping.matchesMouse(mouseButton)) {
                    match = true;
                }
            }
            
            if (match) {
                if (binder.shouldShow(mc.player)) {
                    BinderStats.incrementPressCount(binder.getId(), binder.isResetOnLog());
                }
            }
        }
    }
}
