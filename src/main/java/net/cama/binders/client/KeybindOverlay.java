package net.cama.binders.client;

import net.cama.binders.Config;
import net.cama.binders.api.BinderRegistry;
import net.cama.binders.api.IBinder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KeybindOverlay implements IGuiOverlay {
    public static final KeybindOverlay INSTANCE = new KeybindOverlay();
    
    // Track alpha values for fading animations (0.0 to 1.0)
    private final Map<String, Float> alphas = new HashMap<>();
    // Track the last valid icon for each binder to prevent flickering during fade out
    private final Map<String, ItemStack> lastIcons = new HashMap<>();
    
    private long lastTime = 0;

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!Config.SHOW_OVERLAY.get()) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        long time = System.currentTimeMillis();
        float dt = (lastTime == 0) ? 0.016f : (time - lastTime) / 1000f;
        lastTime = time;
        
        // Clamp dt to prevent huge jumps
        if (dt > 0.1f) dt = 0.1f;

        List<IBinder> activeBinders = new ArrayList<>();
        
        // Update alphas and collect visible binders
        for (IBinder binder : BinderRegistry.getAll()) {
            boolean shouldShow = binder.shouldShow(mc.player);
            float currentAlpha = alphas.getOrDefault(binder.getId(), 0.0f);
            float targetAlpha = shouldShow ? 1.0f : 0.0f;
            
            // Fade speed: 2.0 means it takes 0.5 seconds to go from 0 to 1
            float speed = 2.0f * dt; 
            
            if (currentAlpha < targetAlpha) {
                currentAlpha = Math.min(currentAlpha + speed, targetAlpha);
            } else if (currentAlpha > targetAlpha) {
                currentAlpha = Math.max(currentAlpha - speed, targetAlpha);
            }
            
            alphas.put(binder.getId(), currentAlpha);
            
            // Cache icon if visible (and not empty)
            if (shouldShow) {
                ItemStack currentIcon = binder.getIcon(mc.player);
                if (!currentIcon.isEmpty()) {
                    lastIcons.put(binder.getId(), currentIcon);
                }
            }
            
            // Keep in list if visible at all
            if (currentAlpha > 0.0f) {
                activeBinders.add(binder);
            }
        }

        if (activeBinders.isEmpty()) return;

        int startX = Config.OFFSET_X.get();
        int startY = Config.OFFSET_Y.get();
        Config.Anchor anchor = Config.ANCHOR.get();
        Config.Easing easing = Config.EASING.get();

        // Calculate base position based on anchor
        int baseX = 0;
        int baseY = 0;

        switch (anchor) {
            case TOP_LEFT:
                baseX = startX;
                baseY = startY;
                break;
            case TOP_RIGHT:
                baseX = screenWidth + startX;
                baseY = startY;
                break;
            case BOTTOM_LEFT:
                baseX = startX;
                baseY = screenHeight + startY;
                break;
            case BOTTOM_RIGHT:
                baseX = screenWidth + startX;
                baseY = screenHeight + startY;
                break;
            case CENTER_LEFT:
                baseX = startX;
                baseY = screenHeight / 2 + startY;
                break;
            case CENTER_RIGHT:
                baseX = screenWidth + startX;
                baseY = screenHeight / 2 + startY;
                break;
        }

        boolean stackUpwards = anchor == Config.Anchor.BOTTOM_LEFT || anchor == Config.Anchor.BOTTOM_RIGHT;
        boolean alignRight = anchor == Config.Anchor.TOP_RIGHT || anchor == Config.Anchor.BOTTOM_RIGHT || anchor == Config.Anchor.CENTER_RIGHT;

        int currentY = baseY;
        
        for (IBinder binder : activeBinders) {
            float scale = binder.getScale();
            int itemHeight = (int) (18 * scale); 
            
            if (stackUpwards) {
                currentY -= itemHeight;
            }
            
            float rawAlpha = alphas.get(binder.getId());
            // Apply easing function to the alpha value for animation
            float easedAlpha = AnimationUtils.ease(rawAlpha, easing);
            
            // Only render if alpha is significant
            if (rawAlpha > 0.05f) {
                renderBinderEntry(guiGraphics, binder, baseX, currentY, alignRight, mc, easedAlpha);
            }
            
            if (!stackUpwards) {
                currentY += itemHeight;
            }
        }
    }

    private void renderBinderEntry(GuiGraphics guiGraphics, IBinder binder, int x, int y, boolean alignRight, Minecraft mc, float alpha) {
        // Use cached icon if available, otherwise try to get current
        ItemStack icon = lastIcons.getOrDefault(binder.getId(), ItemStack.EMPTY);
        if (icon.isEmpty()) {
             icon = binder.getIcon(mc.player);
        }
        
        Component label = binder.getLabel(mc.player);
        KeyMapping keyMapping = binder.getKeyMapping();
        String keyName = keyMapping != null ? keyMapping.getTranslatedKeyMessage().getString() : "?";
        
        String text = "";
        if (binder.isShowLabel()) {
            text += label.getString();
        }
        
        if (binder.isShowLabel()) {
            text += " [" + keyName + "]";
        } else {
            text = "[" + keyName + "]";
        }
        
        int textWidth = mc.font.width(text);
        int iconWidth = icon.isEmpty() ? 0 : 16;
        int spacing = (iconWidth > 0) ? 4 : 0;
        int contentWidth = iconWidth + spacing + textWidth;

        float scale = binder.getScale();
        float scaledWidth = contentWidth * scale;
        
        int drawX = x;
        int drawY = y;

        if (alignRight) {
            drawX -= scaledWidth;
        }

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(drawX, drawY, 0);
        guiGraphics.pose().scale(scale, scale, 1.0f);

        // Apply alpha to everything
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        
        // Draw Icon with Scaling Animation
        int textOffset = 0;
        if (!icon.isEmpty()) {
            guiGraphics.pose().pushPose();
            // Translate to center of icon (8, 8) to scale from center
            guiGraphics.pose().translate(8, 8, 0);
            // Scale based on alpha (using the eased alpha)
            float iconScale = alpha; 
            guiGraphics.pose().scale(iconScale, iconScale, 1.0f);
            // Translate back
            guiGraphics.pose().translate(-8, -8, 0);
            
            guiGraphics.renderItem(icon, 0, 0);
            guiGraphics.pose().popPose();
            
            textOffset += 16 + spacing;
        }
        
        // Draw Text
        int color = binder.getColor();
        // Clamp alpha to 0-1 for color calculation just in case easing overshoots (like Elastic/Back)
        float clampedAlpha = Math.max(0, Math.min(1, alpha));
        int alphaInt = (int) (clampedAlpha * 255);
        // Combine alpha with color (ARGB)
        int finalColor = (alphaInt << 24) | (color & 0x00FFFFFF);
        
        guiGraphics.drawString(mc.font, text, textOffset, 4, finalColor, true);

        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }
}
