package net.cama.binders.api;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BinderRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, IBinder> REGISTRY = new HashMap<>();
    private static final List<String> ORDERED_KEYS = new ArrayList<>();

    /**
     * Registers a binder. Duplicate IDs are rejected (logged, not thrown) so a bad
     * config entry can never crash the game.
     *
     * @return true if registered, false if a binder with this ID already exists.
     */
    public static boolean register(IBinder binder) {
        if (REGISTRY.containsKey(binder.getId())) {
            LOGGER.warn("Binder with duplicate ID '{}' was not registered", binder.getId());
            return false;
        }
        REGISTRY.put(binder.getId(), binder);
        ORDERED_KEYS.add(binder.getId());
        return true;
    }

    /**
     * Removes a single binder by ID. Used by the JSON loader on reload so binders
     * registered programmatically by other mods survive /binders reload.
     */
    public static boolean unregister(String id) {
        if (REGISTRY.remove(id) != null) {
            ORDERED_KEYS.remove(id);
            return true;
        }
        return false;
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
