package net.cama.binders.client;

import com.mojang.logging.LogUtils;
import net.cama.binders.Config;
import net.cama.binders.api.BinderRegistry;
import net.cama.binders.api.IBinder;
import com.mojang.blaze3d.systems.RenderSystem;
import net.camacraft.camafy.client.anim.AnimatedFloat;
import net.camacraft.camafy.client.input.Keys;
import net.camacraft.camafy.util.anim.Ease;
import net.camacraft.camafy.util.color.Argb;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KeybindOverlay implements IGuiOverlay {
    public static final KeybindOverlay INSTANCE = new KeybindOverlay();
    private static final Logger LOGGER = LogUtils.getLogger();

    // Fade speed per second: 2.0 means it takes 0.5 seconds to go from 0 to 1
    private static final float FADE_PER_SECOND = 2.0f;

    // Fading alpha per binder (0.0 to 1.0), stepped by real time (a step is capped at 0.1 s)
    private final Map<String, AnimatedFloat> alphas = new HashMap<>();
    // Track the last valid icon for each binder to prevent flickering during fade out
    private final Map<String, ItemStack> lastIcons = new HashMap<>();
    // Binders whose shouldShow() threw — logged once, then skipped
    private final Set<String> erroredBinders = new HashSet<>();

    private record Entry(IBinder binder, float easedAlpha, float layoutAlpha) {}

    /** Clears cached animation/icon state; called when definitions are (re)loaded. */
    public void reset() {
        alphas.clear();
        lastIcons.clear();
        erroredBinders.clear();
    }

    @Override
    public void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight) {
        if (!Config.SHOW_OVERLAY.get()) return;

        // Not called while the GUI is hidden or there is no player (the overlay's gates)
        Minecraft mc = Minecraft.getInstance();

        // Config.Easing and Camafy's Ease name the same 14 curves
        Ease easing = Ease.valueOf(Config.EASING.get().name());

        List<Entry> entries = new ArrayList<>();

        // Update alphas and collect visible binders
        for (IBinder binder : BinderRegistry.getAll()) {
            String id = binder.getId();
            if (erroredBinders.contains(id)) continue;

            boolean shouldShow;
            try {
                shouldShow = binder.shouldShow(mc.player);
            } catch (Exception e) {
                // A broken context must not crash the render thread; log once and skip
                LOGGER.error("Binder '{}' threw in shouldShow — it will not be displayed", id, e);
                erroredBinders.add(id);
                continue;
            }

            float targetAlpha = shouldShow ? 1.0f : 0.0f;
            float currentAlpha = alphas.computeIfAbsent(id, k -> new AnimatedFloat(FADE_PER_SECOND)).update(targetAlpha);

            // Cache icon if visible (and not empty); copy so we don't hold a live inventory stack
            if (shouldShow) {
                ItemStack currentIcon = binder.getIcon(mc.player);
                if (!currentIcon.isEmpty()) {
                    lastIcons.put(id, currentIcon.copy());
                }
            }

            if (currentAlpha > 0.0f) {
                float eased = easing.apply(currentAlpha);
                // Layout uses a clamped alpha so back/elastic overshoot doesn't distort spacing
                float layout = Math.max(0.0f, Math.min(1.0f, eased));
                entries.add(new Entry(binder, eased, layout));
            }
        }

        if (entries.isEmpty()) return;

        int startX = Config.OFFSET_X.get();
        int startY = Config.OFFSET_Y.get();
        Config.Anchor anchor = Config.ANCHOR.get();

        // Entries in fade reserve height proportional to their animation, so the list
        // slides smoothly instead of leaving invisible gaps.
        int totalHeight = 0;
        for (Entry entry : entries) {
            totalHeight += entryAdvance(entry);
        }

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
                baseY = screenHeight / 2 - totalHeight / 2 + startY;
                break;
            case CENTER_RIGHT:
                baseX = screenWidth + startX;
                baseY = screenHeight / 2 - totalHeight / 2 + startY;
                break;
        }

        boolean stackUpwards = anchor == Config.Anchor.BOTTOM_LEFT || anchor == Config.Anchor.BOTTOM_RIGHT;
        boolean alignRight = anchor == Config.Anchor.TOP_RIGHT || anchor == Config.Anchor.BOTTOM_RIGHT || anchor == Config.Anchor.CENTER_RIGHT;

        int currentY = baseY;

        for (Entry entry : entries) {
            int advance = entryAdvance(entry);

            if (stackUpwards) {
                currentY -= advance;
            }

            float clampedAlpha = Math.max(0, Math.min(1, entry.easedAlpha()));
            int alphaInt = (int) (clampedAlpha * 255);

            // The font draws alpha below 4 fully opaque, which made ease-in fades flash at
            // full brightness on their first frames, so those frames are skipped.
            if (alphaInt >= Argb.MIN_FONT_ALPHA) {
                renderBinderEntry(guiGraphics, entry.binder(), baseX, currentY, alignRight, mc, entry.easedAlpha(), alphaInt);
            }

            if (!stackUpwards) {
                currentY += advance;
            }
        }
    }

    private static int entryAdvance(Entry entry) {
        int itemHeight = (int) (18 * entry.binder().getScale());
        return Math.round(itemHeight * entry.layoutAlpha());
    }

    private void renderBinderEntry(GuiGraphics guiGraphics, IBinder binder, int x, int y, boolean alignRight, Minecraft mc, float alpha, int alphaInt) {
        // Use cached icon if available, otherwise try to get current
        ItemStack icon = lastIcons.getOrDefault(binder.getId(), ItemStack.EMPTY);
        if (icon.isEmpty()) {
             icon = binder.getIcon(mc.player);
        }

        Component label = binder.getLabel(mc.player);
        KeyMapping keyMapping = binder.getKeyMapping();

        String keyName = "?";
        if (keyMapping != null) {
            // A key-routing mod (No Conflict) may have unbound the mapping and route it itself;
            // Camafy then shows the real physical key ("X", "Hold X", "X (menu)") instead of "Not Bound"
            keyName = Keys.displayText(keyMapping, "binders.compat.hold", "binders.compat.menu").getString();
        }

        String text;
        if (binder.isShowLabel()) {
            text = label.getString() + " [" + keyName + "]";
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
            // Scale with the eased alpha; clamped at 0 so EASE_IN_BACK's negative
            // overshoot can't mirror-flip the icon (overshoot past 1 is kept as a pop)
            float iconScale = Math.max(0.0f, alpha);
            guiGraphics.pose().scale(iconScale, iconScale, 1.0f);
            // Translate back
            guiGraphics.pose().translate(-8, -8, 0);

            guiGraphics.renderItem(icon, 0, 0);
            guiGraphics.pose().popPose();

            textOffset += 16 + spacing;
        }

        // Draw Text (alphaInt is already clamped and >= Argb.MIN_FONT_ALPHA)
        int color = binder.getColor();
        int finalColor = (alphaInt << 24) | (color & 0x00FFFFFF);

        guiGraphics.drawString(mc.font, text, textOffset, 4, finalColor, true);

        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }
}
