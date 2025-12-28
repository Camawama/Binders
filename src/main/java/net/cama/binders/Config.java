package net.cama.binders;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = Binders.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue SHOW_OVERLAY;
    public static final ForgeConfigSpec.IntValue OFFSET_X;
    public static final ForgeConfigSpec.IntValue OFFSET_Y;
    public static final ForgeConfigSpec.EnumValue<Anchor> ANCHOR;
    public static final ForgeConfigSpec.EnumValue<Easing> EASING;

    static {
        BUILDER.push("general");
        SHOW_OVERLAY = BUILDER
                .comment("Master toggle for the keybind overlay")
                .define("showOverlay", true);
        
        OFFSET_X = BUILDER
                .comment("Global X offset for the overlay list")
                .defineInRange("offsetX", -10, Integer.MIN_VALUE, Integer.MAX_VALUE);
        
        OFFSET_Y = BUILDER
                .comment("Global Y offset for the overlay list")
                .defineInRange("offsetY", -20, Integer.MIN_VALUE, Integer.MAX_VALUE);

        ANCHOR = BUILDER
                .comment("Where the list is anchored on the screen")
                .defineEnum("anchor", Anchor.BOTTOM_RIGHT);
        
        EASING = BUILDER
                .comment("Animation easing function")
                .defineEnum("easing", Easing.EASE_OUT_BACK);
        
        BUILDER.pop();
    }

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public enum Anchor {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, CENTER_LEFT, CENTER_RIGHT
    }

    public enum Easing {
        LINEAR,
        EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
        EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
        EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
        EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
        EASE_OUT_ELASTIC
    }

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
    }
}
