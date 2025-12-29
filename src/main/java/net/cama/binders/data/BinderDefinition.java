package net.cama.binders.data;

public class BinderDefinition {
    public String keyBinding; 
    public String label;      
    public String iconItem;   
    public String context = "always";    
    public String contextLogic = "OR"; 
    public String color = "#FFFFFF";      
    public float scale = 1.0f;       
    public boolean showLabel = true; 
    public int maxPresses = -1;    
    public boolean resetOnLog = false; 
    public boolean isRegex = false;    
    public boolean dynamicIcon = false; 

    public BinderDefinition(String keyBinding, String label, String iconItem, String context, String contextLogic, String color, float scale, boolean showLabel, int maxPresses, boolean resetOnLog, boolean isRegex, boolean dynamicIcon) {
        this.keyBinding = keyBinding;
        this.label = label;
        this.iconItem = iconItem;
        this.context = context != null ? context : "always";
        this.contextLogic = contextLogic != null ? contextLogic : "OR";
        this.color = color != null ? color : "#FFFFFF";
        this.scale = scale;
        this.showLabel = showLabel;
        this.maxPresses = maxPresses;
        this.resetOnLog = resetOnLog;
        this.isRegex = isRegex;
        this.dynamicIcon = dynamicIcon;
    }
    
    public BinderDefinition() {
        // Defaults are already set in field declarations
    }
}
