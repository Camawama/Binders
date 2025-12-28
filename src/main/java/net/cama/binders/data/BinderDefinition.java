package net.cama.binders.data;

public class BinderDefinition {
    public String keyBinding; // e.g., "key.jump" or regex "key.mekanism.*"
    public String label;      // e.g., "Jump" or "{auto}" for automatic naming
    public String iconItem;   // e.g., "minecraft:feather"
    public String context;    // e.g., "flying", "holding:minecraft:diamond_sword"
    public String color;      // Hex color string e.g. "#FFFFFF"
    public float scale;       // Scale of the entry, default 1.0
    public boolean showLabel; // Whether to show the label text, default true
    public int maxPresses;    // Max number of times to show before hiding (-1 for infinite)
    public boolean resetOnLog; // Whether to reset the press count when logging into a world
    public boolean isRegex;    // Whether keyBinding is a regex pattern
    public boolean dynamicIcon; // Whether to use the held item as the icon

    public BinderDefinition(String keyBinding, String label, String iconItem, String context, String color, float scale, boolean showLabel, int maxPresses, boolean resetOnLog, boolean isRegex, boolean dynamicIcon) {
        this.keyBinding = keyBinding;
        this.label = label;
        this.iconItem = iconItem;
        this.context = context;
        this.color = color;
        this.scale = scale;
        this.showLabel = showLabel;
        this.maxPresses = maxPresses;
        this.resetOnLog = resetOnLog;
        this.isRegex = isRegex;
        this.dynamicIcon = dynamicIcon;
    }
    
    public BinderDefinition() {
        this.color = "#FFFFFF";
        this.context = "always";
        this.scale = 1.0f;
        this.showLabel = true;
        this.maxPresses = -1;
        this.resetOnLog = false;
        this.isRegex = false;
        this.dynamicIcon = false;
    }
}
