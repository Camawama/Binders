package net.cama.binders.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BinderRegistry {
    private static final Map<String, IBinder> REGISTRY = new HashMap<>();
    private static final List<String> ORDERED_KEYS = new ArrayList<>();

    public static void register(IBinder binder) {
        if (REGISTRY.containsKey(binder.getId())) {
            // If reloading, we might encounter duplicates if we didn't clear properly.
            // But since we clear before load, this exception is good for catching logic errors.
            throw new IllegalArgumentException("Duplicate binder ID: " + binder.getId());
        }
        REGISTRY.put(binder.getId(), binder);
        ORDERED_KEYS.add(binder.getId());
    }
    
    public static void clear() {
        REGISTRY.clear();
        ORDERED_KEYS.clear();
    }

    public static IBinder get(String id) {
        return REGISTRY.get(id);
    }

    public static List<IBinder> getAll() {
        List<IBinder> list = new ArrayList<>();
        for (String key : ORDERED_KEYS) {
            list.add(REGISTRY.get(key));
        }
        return list;
    }
}
