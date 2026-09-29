package net.cama.binders.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.cama.binders.Binders;
import net.cama.binders.api.BinderRegistry;
import net.cama.binders.api.IBinder;
import net.cama.binders.data.BinderStats;
import net.camacraft.camafy.client.input.Keys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = Binders.MODID, value = Dist.CLIENT)
public class InputHandler {

    /**
     * Binders already counted for the current press of a key, by binder id, until that key is
     * released. One physical press can reach a mapping twice: No Conflict fires a mapping that is
     * both its tap and hold selection the moment the key goes down (a synthetic press), and the
     * physical press follows while the mapping is still bound to the key. A release that never
     * arrives (a screen swallowed it) is healed each tick once the key is up.
     */
    private static final Map<String, InputConstants.Key> COUNTED = new HashMap<>();

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        InputConstants.Key key = InputConstants.getKey(event.getKey(), event.getScanCode());
        if (event.getAction() == GLFW.GLFW_PRESS) {
            checkBinders(key);
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            released(key);
        }
    }

    // Post, not Pre: Pre fires even for clicks another mod cancels, and No Conflict's
    // synthetic mouse events for resolved conflicts are only posted as Post.
    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Post event) {
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(event.getButton());
        if (event.getAction() == GLFW.GLFW_PRESS) {
            checkBinders(key);
        } else if (event.getAction() == GLFW.GLFW_RELEASE) {
            released(key);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !COUNTED.isEmpty()) {
            COUNTED.values().removeIf(key -> !Keys.isPhysicallyDown(key));
        }
    }

    private static void released(InputConstants.Key key) {
        if (!COUNTED.isEmpty()) COUNTED.values().removeIf(key::equals);
    }

    private static void checkBinders(InputConstants.Key key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        for (IBinder binder : BinderRegistry.getAll()) {
            if (!binder.shouldTrackPresses()) continue;

            KeyMapping keyMapping = binder.getKeyMapping();
            if (keyMapping == null) continue;

            // Unlike matches(), this also checks the Forge key modifier, so a SHIFT+G binding
            // is no longer counted when plain G is pressed. A conflict context that throws
            // counts as inactive.
            if (!Keys.safeIsActiveAndMatches(keyMapping, key)) continue;
            // This press of the key already counted for this binder (see COUNTED)
            if (key.equals(COUNTED.get(binder.getId()))) continue;

            try {
                if (binder.shouldShow(mc.player)) {
                    BinderStats.incrementPressCount(binder.getId(), binder.isResetOnLog());
                    COUNTED.put(binder.getId(), key);
                }
            } catch (Exception e) {
                // badly coded binder contexts must not kill input handling
            }
        }
    }
}
