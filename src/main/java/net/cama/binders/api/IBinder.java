package net.cama.binders.api;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public interface IBinder {
    /**
     * Unique ID for this binder (e.g. "binders:jump")
     */
    String getId();

    /**
     * Whether this binder should currently be displayed.
     */
    boolean shouldShow(Player player);

    /**
     * The label to display (e.g. "Jump", "Fly", "Place").
     */
    Component getLabel(Player player);

    /**
     * The icon to display. Return ItemStack.EMPTY for no icon.
     */
    ItemStack getIcon(Player player);

    /**
     * The key mapping associated with this action.
     */
    KeyMapping getKeyMapping();
    
    /**
     * Default color for the text, in ARGB format (including alpha).
     */
    default int getColor() {
        return 0xFFFFFFFF;
    }

    /**
     * Whether presses of this binder should be counted for stats/maxPresses.
     * Return false to skip tracking (and the disk writes that come with it).
     */
    default boolean shouldTrackPresses() {
        return true;
    }

    /**
     * Scale of the binder entry.
     */
    default float getScale() {
        return 1.0f;
    }

    /**
     * Whether to show the label text (e.g. "Jump").
     * If false, only the key name (e.g. "[Space]") will be shown.
     */
    default boolean isShowLabel() {
        return true;
    }

    /**
     * Whether the press count for this binder should reset on login.
     */
    default boolean isResetOnLog() {
        return false;
    }
}
