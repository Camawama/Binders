package net.cama.binders.data;

import com.mojang.logging.LogUtils;
import net.cama.binders.api.BinderRegistry;
import net.cama.binders.api.IBinder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.awt.Color;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class BinderManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("binders");
    private static final File DEFINITIONS_FILE = CONFIG_DIR.resolve("binders_definitions.json").toFile();
    
    private static List<BinderDefinition> definitions = new ArrayList<>();

    public static void load() {
        if (!Files.exists(CONFIG_DIR)) {
            try {
                Files.createDirectories(CONFIG_DIR);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Load stats as well
        BinderStats.load();
        
        // Clear existing registry before reloading
        BinderRegistry.clear();

        if (DEFINITIONS_FILE.exists()) {
            try (FileReader reader = new FileReader(DEFINITIONS_FILE)) {
                Type listType = new TypeToken<ArrayList<BinderDefinition>>(){}.getType();
                definitions = GSON.fromJson(reader, listType);
                if (definitions == null) {
                    definitions = new ArrayList<>();
                }
            } catch (IOException e) {
                e.printStackTrace();
                definitions = new ArrayList<>();
            }
        } else {
            // Create default config
            definitions.add(new BinderDefinition("key.jump", "Jump", "minecraft:feather", "always", "#FFFFFF", 1.0f, true, -1, false, false, false));
            definitions.add(new BinderDefinition("key.inventory", "Inventory", "minecraft:chest", "always", "#FFFFFF", 1.0f, true, 5, true, false, false));
            save();
        }
        
        for (BinderDefinition def : definitions) {
            if (def.isRegex) {
                registerRegexBinders(def);
            } else {
                BinderRegistry.register(new JsonBinder(def, null));
            }
        }
    }

    private static void registerRegexBinders(BinderDefinition def) {
        try {
            Pattern pattern = Pattern.compile(def.keyBinding);
            int count = 0;
            for (KeyMapping keyMapping : Minecraft.getInstance().options.keyMappings) {
                if (pattern.matcher(keyMapping.getName()).matches()) {
                    // Create a specific binder for this match
                    BinderRegistry.register(new JsonBinder(def, keyMapping));
                    count++;
                    LOGGER.info("Regex '{}' matched key '{}'", def.keyBinding, keyMapping.getName());
                }
            }
            if (count == 0) {
                LOGGER.warn("Regex '{}' matched NO keys!", def.keyBinding);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to compile regex '{}'", def.keyBinding, e);
        }
    }

    public static void save() {
        if (!Files.exists(CONFIG_DIR)) {
            try {
                Files.createDirectories(CONFIG_DIR);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
        try (FileWriter writer = new FileWriter(DEFINITIONS_FILE)) {
            GSON.toJson(definitions, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class JsonBinder implements IBinder {
        private final BinderDefinition def;
        private final KeyMapping specificKeyMapping; // Used if this was created from a regex match
        
        public JsonBinder(BinderDefinition def, KeyMapping specificKeyMapping) {
            this.def = def;
            this.specificKeyMapping = specificKeyMapping;
        }

        @Override
        public String getId() {
            String keyName = specificKeyMapping != null ? specificKeyMapping.getName() : def.keyBinding;
            return "json:" + keyName + ":" + def.label;
        }

        @Override
        public boolean shouldShow(Player player) {
            // Check max presses first
            if (def.maxPresses >= 0) {
                if (BinderStats.getPressCount(getId(), def.resetOnLog) >= def.maxPresses) {
                    return false;
                }
            }

            if (def.context == null || def.context.isEmpty()) return true;
            
            String[] contexts = def.context.split(",");
            for (String ctx : contexts) {
                if (checkSingleContext(ctx.trim(), player)) {
                    return true;
                }
            }
            return false;
        }

        private boolean checkSingleContext(String ctx, Player player) {
            if (ctx.equals("always")) return true;
            if (ctx.equals("flying")) return player.getAbilities().flying;
            if (ctx.equals("creative")) return player.isCreative();
            if (ctx.equals("survival")) return !player.isCreative() && !player.isSpectator();
            if (ctx.equals("sneaking")) return player.isCrouching();
            if (ctx.equals("sprinting")) return player.isSprinting();
            if (ctx.equals("swimming")) return player.isSwimming();
            if (ctx.equals("on_ground")) return player.onGround();
            if (ctx.equals("riding")) return player.isPassenger();
            
            if (ctx.startsWith("holding:")) {
                String itemIdOrPattern = ctx.substring("holding:".length());
                return isHolding(player, itemIdOrPattern);
            }
            
            if (ctx.startsWith("holding_main:")) {
                String itemIdOrPattern = ctx.substring("holding_main:".length());
                return isHoldingMain(player, itemIdOrPattern);
            }
            
            if (ctx.startsWith("holding_off:")) {
                String itemIdOrPattern = ctx.substring("holding_off:".length());
                return isHoldingOff(player, itemIdOrPattern);
            }
            
            if (ctx.startsWith("looking_at_block:")) {
                String blockIdOrPattern = ctx.substring("looking_at_block:".length());
                return isLookingAtBlock(player, blockIdOrPattern);
            }
            
            if (ctx.startsWith("looking_at_tag:")) {
                String tagId = ctx.substring("looking_at_tag:".length());
                return isLookingAtTag(player, tagId);
            }

            return false;
        }

        private boolean isHolding(Player player, String itemIdOrPattern) {
            return isHoldingMain(player, itemIdOrPattern) || isHoldingOff(player, itemIdOrPattern);
        }

        private boolean isHoldingMain(Player player, String itemIdOrPattern) {
            if (def.isRegex) {
                return matchesItemRegex(player.getMainHandItem(), itemIdOrPattern);
            } else {
                return matchesItemExact(player.getMainHandItem(), itemIdOrPattern);
            }
        }

        private boolean isHoldingOff(Player player, String itemIdOrPattern) {
            if (def.isRegex) {
                return matchesItemRegex(player.getOffhandItem(), itemIdOrPattern);
            } else {
                return matchesItemExact(player.getOffhandItem(), itemIdOrPattern);
            }
        }
        
        private boolean matchesItemExact(ItemStack stack, String itemId) {
            try {
                ResourceLocation rl = new ResourceLocation(itemId);
                Item item = ForgeRegistries.ITEMS.getValue(rl);
                if (item == null) return false;
                return stack.getItem().equals(item);
            } catch (Exception e) {
                return false;
            }
        }
        
        private boolean matchesItemRegex(ItemStack stack, String patternStr) {
            if (stack.isEmpty()) return false;
            ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (rl == null) return false;
            
            try {
                Pattern pattern = Pattern.compile(patternStr);
                return pattern.matcher(rl.toString()).matches();
            } catch (Exception e) {
                return false;
            }
        }
        
        private boolean isLookingAtBlock(Player player, String blockIdOrPattern) {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
            
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = player.level().getBlockState(pos);
            Block block = state.getBlock();
            ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(block);
            if (rl == null) return false;
            
            if (def.isRegex) {
                try {
                    Pattern pattern = Pattern.compile(blockIdOrPattern);
                    return pattern.matcher(rl.toString()).matches();
                } catch (Exception e) {
                    return false;
                }
            } else {
                return rl.toString().equals(blockIdOrPattern);
            }
        }
        
        private boolean isLookingAtTag(Player player, String tagId) {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;
            
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = player.level().getBlockState(pos);
            
            try {
                ResourceLocation tagLoc = new ResourceLocation(tagId);
                TagKey<Block> tagKey = TagKey.create(ForgeRegistries.BLOCKS.getRegistryKey(), tagLoc);
                return state.is(tagKey);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public Component getLabel(Player player) {
            if (def.label.equals("{auto}") && specificKeyMapping != null) {
                return Component.translatable(specificKeyMapping.getName());
            }
            return Component.literal(def.label);
        }

        @Override
        public ItemStack getIcon(Player player) {
            if (def.dynamicIcon) {
                // If dynamic icon is enabled, try to find the item that satisfied the context
                // We need to re-check the context to find WHICH item triggered it.
                // This is a bit inefficient but safe.
                
                if (def.context != null) {
                    String[] contexts = def.context.split(",");
                    for (String ctx : contexts) {
                        ctx = ctx.trim();
                        if (ctx.startsWith("holding:") || ctx.startsWith("holding_main:")) {
                            String pattern = ctx.substring(ctx.indexOf(":") + 1);
                            if (isHoldingMain(player, pattern)) {
                                return player.getMainHandItem();
                            }
                        }
                        if (ctx.startsWith("holding:") || ctx.startsWith("holding_off:")) {
                            String pattern = ctx.substring(ctx.indexOf(":") + 1);
                            if (isHoldingOff(player, pattern)) {
                                return player.getOffhandItem();
                            }
                        }
                        
                        // Handle looking_at contexts for dynamic icons
                        if (ctx.startsWith("looking_at_block:")) {
                            String pattern = ctx.substring("looking_at_block:".length());
                            if (isLookingAtBlock(player, pattern)) {
                                HitResult hit = Minecraft.getInstance().hitResult;
                                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                                    BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                                    BlockState state = player.level().getBlockState(pos);
                                    return new ItemStack(state.getBlock().asItem());
                                }
                            }
                        }
                        
                        if (ctx.startsWith("looking_at_tag:")) {
                            String tagId = ctx.substring("looking_at_tag:".length());
                            if (isLookingAtTag(player, tagId)) {
                                HitResult hit = Minecraft.getInstance().hitResult;
                                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                                    BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                                    BlockState state = player.level().getBlockState(pos);
                                    return new ItemStack(state.getBlock().asItem());
                                }
                            }
                        }
                    }
                }
                // Fallback to main hand if no specific holding context matched (or if context was just "always")
                if (!player.getMainHandItem().isEmpty()) {
                    return player.getMainHandItem();
                }
            }

            if (def.iconItem != null && !def.iconItem.isEmpty()) {
                Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(def.iconItem));
                if (item != null) {
                    return new ItemStack(item);
                }
            }
            return ItemStack.EMPTY;
        }

        @Override
        public KeyMapping getKeyMapping() {
            if (specificKeyMapping != null) return specificKeyMapping;

            return Arrays.stream(Minecraft.getInstance().options.keyMappings)
                    .filter(k -> k.getName().equals(def.keyBinding))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public int getColor() {
            try {
                return Color.decode(def.color).getRGB();
            } catch (Exception e) {
                return 0xFFFFFF;
            }
        }

        @Override
        public float getScale() {
            return def.scale;
        }

        @Override
        public boolean isShowLabel() {
            return def.showLabel;
        }

        @Override
        public boolean isResetOnLog() {
            return def.resetOnLog;
        }
    }
}
