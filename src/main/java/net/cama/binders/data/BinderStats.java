package net.cama.binders.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class BinderStats {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("binders");
    private static final File STATS_FILE = CONFIG_DIR.resolve("binders_stats.json").toFile();
    
    // Map of binder ID -> press count
    private static Map<String, Integer> pressCounts = new HashMap<>();
    
    // In-memory session counts for binders that reset on log
    private static Map<String, Integer> sessionCounts = new HashMap<>();

    public static void load() {
        if (STATS_FILE.exists()) {
            try (FileReader reader = new FileReader(STATS_FILE)) {
                Type mapType = new TypeToken<HashMap<String, Integer>>(){}.getType();
                pressCounts = GSON.fromJson(reader, mapType);
                if (pressCounts == null) pressCounts = new HashMap<>();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        
        try (FileWriter writer = new FileWriter(STATS_FILE)) {
            GSON.toJson(pressCounts, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static int getPressCount(String binderId, boolean resetOnLog) {
        if (resetOnLog) {
            return sessionCounts.getOrDefault(binderId, 0);
        }
        return pressCounts.getOrDefault(binderId, 0);
    }

    public static void incrementPressCount(String binderId, boolean resetOnLog) {
        if (resetOnLog) {
            sessionCounts.put(binderId, sessionCounts.getOrDefault(binderId, 0) + 1);
        } else {
            pressCounts.put(binderId, pressCounts.getOrDefault(binderId, 0) + 1);
            save(); 
        }
    }
    
    public static void clearSession() {
        sessionCounts.clear();
    }
}
