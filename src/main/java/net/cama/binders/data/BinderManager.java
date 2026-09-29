package net.cama.binders.data;

import com.mojang.logging.LogUtils;
import net.cama.binders.api.BinderRegistry;
import net.cama.binders.api.IBinder;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.cama.binders.client.KeybindOverlay;
import net.camacraft.camafy.util.color.Argb;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class BinderManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("binders");
    private static final File DEFINITIONS_FILE = CONFIG_DIR.resolve("binders_definitions.json").toFile();

    private static List<BinderDefinition> definitions = new ArrayList<>();

    // IDs this manager registered from JSON, so reloads never touch binders other mods registered via the API
    private static final Set<String> jsonBinderIds = new HashSet<>();

    // Compiled context/key patterns, cached per pattern string; empty = invalid (logged once at compile)
    private static final Map<String, Optional<Pattern>> patternCache = new HashMap<>();

    public static void load() {
        ensureConfigDir();

        BinderStats.load();

        // Remove only our own previously registered binders (API-registered ones survive reloads)
        for (String id : jsonBinderIds) {
            BinderRegistry.unregister(id);
        }
        jsonBinderIds.clear();
        patternCache.clear();
        KeybindOverlay.INSTANCE.reset();

        definitions = new ArrayList<>();

        if (DEFINITIONS_FILE.exists()) {
            try {
                Type listType = new TypeToken<ArrayList<BinderDefinition>>(){}.getType();
                List<BinderDefinition> parsed = GSON.fromJson(readJsonFile(DEFINITIONS_FILE.toPath()), listType);
                if (parsed != null) {
                    for (BinderDefinition def : parsed) {
                        if (def == null) continue; // stray null entry in the JSON array
                        sanitize(def);
                        definitions.add(def);
                    }
                }

                // Normalize the file structure only after a successful parse;
                // a broken file is left untouched for the user to fix.
                save();

            } catch (Exception e) {
                // Gson throws unchecked JsonSyntaxException — a config typo must never crash the game
                LOGGER.error("Failed to parse {} — no binders loaded. Fix the JSON and run /binders reload.",
                        DEFINITIONS_FILE, e);
                definitions = new ArrayList<>();
            }
        } else {
            definitions.add(new BinderDefinition("key.jump", "Jump", "minecraft:feather", "always", "OR", "#FFFFFF", 1.0f, true, -1, false, false, false));
            definitions.add(new BinderDefinition("key.inventory", "Inventory", "minecraft:chest", "always", "OR", "#FFFFFF", 1.0f, true, 5, true, false, false));
            save();
        }

        for (BinderDefinition def : definitions) {
            if (def.keyIsRegex()) {
                registerRegexBinders(def);
            } else {
                registerJsonBinder(new JsonBinder(def, null));
            }
        }

        BinderStats.prune(jsonBinderIds);
    }

    private static void sanitize(BinderDefinition def) {
        if (def.keyBinding == null) def.keyBinding = "";
        if (def.label == null) def.label = "";
        if (def.context == null) def.context = "always";
        if (def.contextLogic == null) def.contextLogic = "OR";
        if (def.color == null) def.color = "#FFFFFF";
    }

    private static void registerJsonBinder(IBinder binder) {
        if (BinderRegistry.register(binder)) {
            jsonBinderIds.add(binder.getId());
        } else {
            LOGGER.error("Duplicate binder ID '{}' — definition skipped. Give one of the definitions a unique 'id' (or 'label').",
                    binder.getId());
        }
    }

    private static void registerRegexBinders(BinderDefinition def) {
        Pattern pattern = getPattern(def.keyBinding);
        if (pattern == null) {
            LOGGER.warn("Skipping binder with invalid keyBinding regex '{}'", def.keyBinding);
            return;
        }
        int count = 0;
        for (KeyMapping keyMapping : Minecraft.getInstance().options.keyMappings) {
            if (pattern.matcher(keyMapping.getName()).matches()) {
                registerJsonBinder(new JsonBinder(def, keyMapping));
                count++;
                LOGGER.info("Regex '{}' matched key '{}'", def.keyBinding, keyMapping.getName());
            }
        }
        if (count == 0) {
            LOGGER.warn("Regex '{}' matched NO keys!", def.keyBinding);
        }
    }

    private static void ensureConfigDir() {
        if (!Files.exists(CONFIG_DIR)) {
            try {
                Files.createDirectories(CONFIG_DIR);
            } catch (IOException e) {
                LOGGER.error("Failed to create config directory {}", CONFIG_DIR, e);
            }
        }
    }

    /**
     * Reads as UTF-8. Older versions wrote with the platform charset (cp1252 on Windows),
     * so a file that isn't valid UTF-8 is re-read as windows-1252; the next save rewrites it as UTF-8.
     */
    private static String readJsonFile(Path path) throws IOException {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (CharacterCodingException e) {
            LOGGER.info("{} is not UTF-8 (written by an older Binders); converting from windows-1252", path);
            return Files.readString(path, Charset.forName("windows-1252"));
        }
    }

    public static void save() {
        ensureConfigDir();

        try (Writer writer = Files.newBufferedWriter(DEFINITIONS_FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(definitions, writer);
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", DEFINITIONS_FILE, e);
        }
    }

    /**
     * Compiles and caches a pattern. Invalid patterns are logged once and return null
     * instead of being recompiled (and re-logged) every frame.
     */
    private static Pattern getPattern(String patternStr) {
        return patternCache.computeIfAbsent(patternStr, s -> {
            try {
                return Optional.of(Pattern.compile(s));
            } catch (PatternSyntaxException e) {
                LOGGER.error("Invalid regex '{}' in binder config: {}", s, e.getMessage());
                return Optional.empty();
            }
        }).orElse(null);
    }

    private static class JsonBinder implements IBinder {
        private final BinderDefinition def;
        private final KeyMapping specificKeyMapping; // Used if this was created from a regex match
        private final String[] contexts;
        private final ItemStack staticIcon;
        private final int color;
        private KeyMapping cachedMapping; // Lazily resolved for non-regex binders; binders are rebuilt on reload

        public JsonBinder(BinderDefinition def, KeyMapping specificKeyMapping) {
            this.def = def;
            this.specificKeyMapping = specificKeyMapping;
            this.contexts = def.context.isEmpty() ? new String[0] : def.context.split(",");
            for (int i = 0; i < contexts.length; i++) {
                contexts[i] = contexts[i].trim();
            }
            this.staticIcon = resolveStaticIcon(def.iconItem);
            this.color = parseColor(def.color);
        }

        /**
         * Resolved once at load: an invalid item id logs a warning instead of throwing
         * a ResourceLocationException from the render loop every frame.
         */
        private static ItemStack resolveStaticIcon(String iconItem) {
            if (iconItem == null || iconItem.isEmpty()) return ItemStack.EMPTY;
            ResourceLocation rl = ResourceLocation.tryParse(iconItem);
            if (rl == null) {
                LOGGER.warn("Invalid iconItem id '{}' in binder config — no icon will be shown", iconItem);
                return ItemStack.EMPTY;
            }
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null) {
                LOGGER.warn("Unknown iconItem '{}' in binder config — no icon will be shown", iconItem);
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        }

        private static int parseColor(String color) {
            // "#RRGGBB" (also "0x" prefix, "#RGB" shorthand or AARRGGBB); always opaque, the
            // overlay supplies its own fade alpha
            if (!Argb.isValid(color)) {
                LOGGER.warn("Invalid color '{}' in binder config — using white", color);
                return 0xFFFFFFFF;
            }
            return Argb.opaque(Argb.parse(color, 0xFFFFFFFF));
        }

        @Override
        public String getId() {
            String keyName = specificKeyMapping != null ? specificKeyMapping.getName() : def.keyBinding;
            // An explicit 'id' keeps stats stable across label renames
            String identity = (def.id != null && !def.id.isEmpty()) ? def.id : def.label;
            return "json:" + keyName + ":" + identity;
        }

        @Override
        public boolean shouldTrackPresses() {
            return def.maxPresses >= 0;
        }

        @Override
        public boolean shouldShow(Player player) {
            // Check max presses first
            if (def.maxPresses >= 0) {
                if (BinderStats.getPressCount(getId(), def.resetOnLog) >= def.maxPresses) {
                    return false;
                }
            }

            if (contexts.length == 0) return true;

            boolean isAnd = "AND".equalsIgnoreCase(def.contextLogic);

            if (isAnd) {
                // AND logic: ALL contexts must be true
                for (String ctx : contexts) {
                    if (!checkSingleContext(ctx, player)) {
                        return false;
                    }
                }
                return true;
            } else {
                // OR logic: ANY context must be true
                for (String ctx : contexts) {
                    if (checkSingleContext(ctx, player)) {
                        return true;
                    }
                }
                return false;
            }
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
            if (ctx.equals("raining")) return player.level().isRaining();
            if (ctx.equals("thundering")) return player.level().isThundering();

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

            if (ctx.startsWith("wearing_head:")) {
                String itemIdOrPattern = ctx.substring("wearing_head:".length());
                return isWearing(player, EquipmentSlot.HEAD, itemIdOrPattern);
            }

            if (ctx.startsWith("wearing_chest:")) {
                String itemIdOrPattern = ctx.substring("wearing_chest:".length());
                return isWearing(player, EquipmentSlot.CHEST, itemIdOrPattern);
            }

            if (ctx.startsWith("wearing_legs:")) {
                String itemIdOrPattern = ctx.substring("wearing_legs:".length());
                return isWearing(player, EquipmentSlot.LEGS, itemIdOrPattern);
            }

            if (ctx.startsWith("wearing_feet:")) {
                String itemIdOrPattern = ctx.substring("wearing_feet:".length());
                return isWearing(player, EquipmentSlot.FEET, itemIdOrPattern);
            }

            if (ctx.startsWith("looking_at_block:")) {
                String blockIdOrPattern = ctx.substring("looking_at_block:".length());
                return isLookingAtBlock(player, blockIdOrPattern);
            }

            if (ctx.startsWith("looking_at_tag:")) {
                String tagId = ctx.substring("looking_at_tag:".length());
                return isLookingAtTag(player, tagId);
            }

            if (ctx.startsWith("looking_at_entity:")) {
                String entityIdOrPattern = ctx.substring("looking_at_entity:".length());
                return isLookingAtEntity(player, entityIdOrPattern);
            }

            if (ctx.startsWith("dimension:")) {
                String dimId = ctx.substring("dimension:".length());
                return player.level().dimension().location().toString().equals(dimId);
            }

            if (ctx.startsWith("biome:")) {
                String biomeId = ctx.substring("biome:".length());
                // Direct (inline datapack) biome holders have no key; Optional.get() would throw
                return player.level().getBiome(player.blockPosition()).unwrapKey()
                        .map(key -> key.location().toString().equals(biomeId))
                        .orElse(false);
            }

            if (ctx.startsWith("health_below:")) {
                try {
                    float val = Float.parseFloat(ctx.substring("health_below:".length()));
                    return player.getHealth() < val;
                } catch (NumberFormatException e) { return false; }
            }

            if (ctx.startsWith("hunger_below:")) {
                try {
                    int val = Integer.parseInt(ctx.substring("hunger_below:".length()));
                    return player.getFoodData().getFoodLevel() < val;
                } catch (NumberFormatException e) { return false; }
            }

            return false;
        }

        private boolean isWildcard(String pattern) {
            return pattern.equals("*") || pattern.equals("any") || pattern.equals("*:*");
        }

        private boolean isHolding(Player player, String itemIdOrPattern) {
            return isHoldingMain(player, itemIdOrPattern) || isHoldingOff(player, itemIdOrPattern);
        }

        private boolean isHoldingMain(Player player, String itemIdOrPattern) {
            if (def.contextIsRegex()) {
                return matchesItemRegex(player.getMainHandItem(), itemIdOrPattern);
            } else {
                return matchesItemExact(player.getMainHandItem(), itemIdOrPattern);
            }
        }

        private boolean isHoldingOff(Player player, String itemIdOrPattern) {
            if (def.contextIsRegex()) {
                return matchesItemRegex(player.getOffhandItem(), itemIdOrPattern);
            } else {
                return matchesItemExact(player.getOffhandItem(), itemIdOrPattern);
            }
        }

        private boolean isWearing(Player player, EquipmentSlot slot, String itemIdOrPattern) {
            ItemStack stack = player.getItemBySlot(slot);
            if (def.contextIsRegex()) {
                return matchesItemRegex(stack, itemIdOrPattern);
            } else {
                return matchesItemExact(stack, itemIdOrPattern);
            }
        }

        private boolean matchesItemExact(ItemStack stack, String itemId) {
            if (stack.isEmpty()) return false;
            if (isWildcard(itemId)) return true;

            ResourceLocation rl = ResourceLocation.tryParse(itemId);
            if (rl == null) return false;
            Item item = ForgeRegistries.ITEMS.getValue(rl);
            if (item == null) return false;
            return stack.getItem().equals(item);
        }

        private boolean matchesItemRegex(ItemStack stack, String patternStr) {
            if (stack.isEmpty()) return false;
            if (isWildcard(patternStr)) return true;

            ResourceLocation rl = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (rl == null) return false;

            Pattern pattern = getPattern(patternStr);
            return pattern != null && pattern.matcher(rl.toString()).matches();
        }

        private boolean isLookingAtBlock(Player player, String blockIdOrPattern) {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) return false;

            if (isWildcard(blockIdOrPattern)) return true;

            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = player.level().getBlockState(pos);
            Block block = state.getBlock();
            ResourceLocation rl = ForgeRegistries.BLOCKS.getKey(block);
            if (rl == null) return false;

            if (def.contextIsRegex()) {
                Pattern pattern = getPattern(blockIdOrPattern);
                return pattern != null && pattern.matcher(rl.toString()).matches();
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

            ResourceLocation tagLoc = ResourceLocation.tryParse(tagId);
            if (tagLoc == null) return false;
            TagKey<Block> tagKey = TagKey.create(ForgeRegistries.BLOCKS.getRegistryKey(), tagLoc);
            return state.is(tagKey);
        }

        private boolean isLookingAtEntity(Player player, String entityIdOrPattern) {
            HitResult hit = Minecraft.getInstance().hitResult;
            if (hit == null || hit.getType() != HitResult.Type.ENTITY) return false;

            if (isWildcard(entityIdOrPattern)) return true;

            EntityHitResult entityHit = (EntityHitResult) hit;
            Entity entity = entityHit.getEntity();
            EntityType<?> type = entity.getType();
            ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (rl == null) return false;

            if (def.contextIsRegex()) {
                Pattern pattern = getPattern(entityIdOrPattern);
                return pattern != null && pattern.matcher(rl.toString()).matches();
            } else {
                return rl.toString().equals(entityIdOrPattern);
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
                for (String ctx : contexts) {
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

                    // Handle wearing contexts for dynamic icons
                    if (ctx.startsWith("wearing_head:")) {
                        String pattern = ctx.substring("wearing_head:".length());
                        if (isWearing(player, EquipmentSlot.HEAD, pattern)) return player.getItemBySlot(EquipmentSlot.HEAD);
                    }
                    if (ctx.startsWith("wearing_chest:")) {
                        String pattern = ctx.substring("wearing_chest:".length());
                        if (isWearing(player, EquipmentSlot.CHEST, pattern)) return player.getItemBySlot(EquipmentSlot.CHEST);
                    }
                    if (ctx.startsWith("wearing_legs:")) {
                        String pattern = ctx.substring("wearing_legs:".length());
                        if (isWearing(player, EquipmentSlot.LEGS, pattern)) return player.getItemBySlot(EquipmentSlot.LEGS);
                    }
                    if (ctx.startsWith("wearing_feet:")) {
                        String pattern = ctx.substring("wearing_feet:".length());
                        if (isWearing(player, EquipmentSlot.FEET, pattern)) return player.getItemBySlot(EquipmentSlot.FEET);
                    }
                }
                // Fallback to main hand if no specific holding context matched (or if context was just "always")
                if (!player.getMainHandItem().isEmpty()) {
                    return player.getMainHandItem();
                }
            }

            return staticIcon;
        }

        @Override
        public KeyMapping getKeyMapping() {
            if (specificKeyMapping != null) return specificKeyMapping;

            if (cachedMapping == null) {
                cachedMapping = Arrays.stream(Minecraft.getInstance().options.keyMappings)
                        .filter(k -> k.getName().equals(def.keyBinding))
                        .findFirst()
                        .orElse(null);
            }
            return cachedMapping;
        }

        @Override
        public int getColor() {
            return color;
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
