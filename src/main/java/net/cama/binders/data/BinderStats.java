package net.cama.binders.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BinderStats {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("binders");
    private static final File STATS_FILE = CONFIG_DIR.resolve("binders_stats.json").toFile();

    // Map of binder ID -> press count
    private static Map<String, Integer> pressCounts = new HashMap<>();

    // In-memory session counts for binders that reset on log
    private static Map<String, Integer> sessionCounts = new HashMap<>();

    // Unsaved changes pending? Counts are flushed periodically and on logout instead of
    // writing the file on every single keypress.
    private static boolean dirty = false;

    public static void load() {
        flush(); // don't lose unsaved counts when re-reading from disk

        if (STATS_FILE.exists()) {
            try {
                Type mapType = new TypeToken<HashMap<String, Integer>>(){}.getType();
                pressCounts = GSON.fromJson(readJsonFile(STATS_FILE.toPath()), mapType);
                if (pressCounts == null) pressCounts = new HashMap<>();
            } catch (Exception e) {
                // JsonSyntaxException is unchecked; a corrupt stats file must never crash the game
                LOGGER.error("Failed to parse {} — starting with empty stats", STATS_FILE, e);
                pressCounts = new HashMap<>();
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
        if (!Files.exists(CONFIG_DIR)) {
            try {
                Files.createDirectories(CONFIG_DIR);
            } catch (IOException e) {
                LOGGER.error("Failed to create config directory {}", CONFIG_DIR, e);
            }
        }

        try (Writer writer = Files.newBufferedWriter(STATS_FILE.toPath(), StandardCharsets.UTF_8)) {
            GSON.toJson(pressCounts, writer);
            dirty = false;
        } catch (IOException e) {
            LOGGER.error("Failed to save {}", STATS_FILE, e);
        }
    }

    /** Writes pending counts to disk, but only if something changed since the last save. */
    public static void flush() {
        if (dirty) save();
    }

    public static int getPressCount(String binderId, boolean resetOnLog) {
        if (resetOnLog) {
            return sessionCounts.getOrDefault(binderId, 0);
        }
        return pressCounts.getOrDefault(binderId, 0);
    }

    public static void incrementPressCount(String binderId, boolean resetOnLog) {
        if (resetOnLog) {
            sessionCounts.merge(binderId, 1, Integer::sum);
        } else {
            pressCounts.merge(binderId, 1, Integer::sum);
            dirty = true;
        }
    }

    /**
     * Drops persisted counts for JSON-defined binders that no longer exist, so the stats
     * file doesn't accumulate orphans forever. API-registered binders (no "json:" prefix)
     * are left alone.
     */
    public static void prune(Set<String> activeJsonIds) {
        if (pressCounts.keySet().removeIf(id -> id.startsWith("json:") && !activeJsonIds.contains(id))) {
            dirty = true;
        }
        sessionCounts.keySet().removeIf(id -> id.startsWith("json:") && !activeJsonIds.contains(id));
    }

    public static void clearSession() {
        sessionCounts.clear();
    }
}
