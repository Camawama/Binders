package net.cama.binders.client;

import com.mojang.logging.LogUtils;
import net.cama.binders.Binders;
import net.cama.binders.data.BinderManager;
import net.cama.binders.data.BinderStats;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Arrays;

@Mod.EventBusSubscriber(modid = Binders.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    // Flush pending stats roughly every 30 seconds instead of writing on every keypress
    private static final int FLUSH_INTERVAL_TICKS = 600;
    private static int flushCounter = 0;

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        BinderStats.clearSession();
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        BinderStats.flush();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (++flushCounter >= FLUSH_INTERVAL_TICKS) {
            flushCounter = 0;
            BinderStats.flush();
        }
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("binders")
                .then(Commands.literal("dump")
                    .executes(context -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.translatable("binders.command.dump.start"));
                            LOGGER.info("=== BINDERS KEYBIND DUMP ===");
                            Arrays.stream(mc.options.keyMappings)
                                    .forEach(key -> {
                                        String name = key.getName();
                                        String cat = key.getCategory();
                                        LOGGER.info("Key: '{}' | Category: '{}'", name, cat);
                                        mc.player.sendSystemMessage(Component.translatable("binders.command.dump.entry", name));
                                    });
                            LOGGER.info("============================");
                            mc.player.sendSystemMessage(Component.translatable("binders.command.dump.done"));
                        }
                        return 1;
                    })
                )
                .then(Commands.literal("reload")
                    .executes(context -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.translatable("binders.command.reload.start"));
                            try {
                                BinderManager.load();
                                mc.player.sendSystemMessage(Component.translatable("binders.command.reload.done"));
                            } catch (Exception e) {
                                mc.player.sendSystemMessage(Component.translatable("binders.command.reload.error", e.getMessage()));
                                LOGGER.error("Failed to reload binders", e);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
