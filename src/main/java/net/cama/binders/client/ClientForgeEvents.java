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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.Arrays;

@Mod.EventBusSubscriber(modid = Binders.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientForgeEvents {

    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        BinderStats.clearSession();
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("binders")
                .then(Commands.literal("dump")
                    .executes(context -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("Dumping all keybind IDs to logs..."));
                            LOGGER.info("=== BINDERS KEYBIND DUMP ===");
                            Arrays.stream(mc.options.keyMappings)
                                    .forEach(key -> {
                                        String name = key.getName();
                                        String cat = key.getCategory();
                                        LOGGER.info("Key: '{}' | Category: '{}'", name, cat);
                                        mc.player.sendSystemMessage(Component.literal("Key: " + name));
                                    });
                            LOGGER.info("============================");
                            mc.player.sendSystemMessage(Component.literal("Dump complete. Check latest.log"));
                        }
                        return 1;
                    })
                )
                .then(Commands.literal("reload")
                    .executes(context -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.player != null) {
                            mc.player.sendSystemMessage(Component.literal("Reloading Binders definitions..."));
                            try {
                                BinderManager.load();
                                mc.player.sendSystemMessage(Component.literal("Reload complete!"));
                            } catch (Exception e) {
                                mc.player.sendSystemMessage(Component.literal("Error reloading binders: " + e.getMessage()));
                                LOGGER.error("Failed to reload binders", e);
                            }
                        }
                        return 1;
                    })
                )
        );
    }
}
