package me.char321.sfadvancements.vanilla;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.advancement.Advancement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class VanillaHookTest {
    private final Map<NamespacedKey, Advancement> persisted = new HashMap<>();
    private final NamespacedKey key = new NamespacedKey("slimefunadvancements", "basic");
    private Field serverField;
    private Object previousServer;
    private int registrations;

    @BeforeEach
    void installServer() throws Exception {
        UnsafeValues unsafe = proxy(UnsafeValues.class, (instance, method, args) -> {
            if (method.getName().equals("loadAdvancement")) {
                registrations++;
                NamespacedKey loadedKey = (NamespacedKey) args[0];
                if (persisted.containsKey(loadedKey)) {
                    throw new IllegalArgumentException("Advancement already exists");
                }
                Advancement advancement = advancement(loadedKey);
                persisted.put(loadedKey, advancement);
                return advancement;
            }
            throw new AssertionError("Unexpected unsafe operation: " + method.getName());
        });
        Server server = proxy(Server.class, (instance, method, args) -> {
            if (method.getName().equals("getAdvancement")) return persisted.get(args[0]);
            if (method.getName().equals("getUnsafe")) return unsafe;
            throw new AssertionError("Unexpected server operation: " + method.getName());
        });
        serverField = Bukkit.class.getDeclaredField("server");
        serverField.setAccessible(true);
        previousServer = serverField.get(null);
        serverField.set(null, server);
    }

    @AfterEach
    void restoreServer() throws Exception {
        serverField.set(null, previousServer);
    }

    @Test
    void preservesPersistedAdvancementWithoutUnsafeRegistration() throws Exception {
        Advancement existing = advancement(key);
        persisted.put(key, existing);
        VanillaHook hook = new VanillaHook();

        load(hook);

        assertEquals(0, registrations);
        assertSame(existing, persisted.get(key));
        assertTrue(loadedKeys(hook).contains(key));
    }

    @Test
    void registersMissingAdvancement() throws Exception {
        VanillaHook hook = new VanillaHook();

        load(hook);

        assertEquals(1, registrations);
        assertNotNull(persisted.get(key));
        assertTrue(loadedKeys(hook).contains(key));
    }

    @Test
    void secondPluginInstanceReusesAdvancementAfterRestart() throws Exception {
        load(new VanillaHook());
        Advancement existing = persisted.get(key);
        VanillaHook restarted = new VanillaHook();

        load(restarted);

        assertEquals(1, registrations);
        assertSame(existing, persisted.get(key));
        assertTrue(loadedKeys(restarted).contains(key));
    }

    private void load(VanillaHook hook) throws Exception {
        Method method = VanillaHook.class.getDeclaredMethod("loadAdvancement", NamespacedKey.class, JsonObject.class);
        method.setAccessible(true);
        method.invoke(hook, key, new JsonObject());
    }

    private Set<?> loadedKeys(VanillaHook hook) throws Exception {
        Field field = VanillaHook.class.getDeclaredField("loadedKeys");
        field.setAccessible(true);
        return (Set<?>) field.get(hook);
    }

    private Advancement advancement(NamespacedKey advancementKey) {
        return proxy(Advancement.class, (instance, method, args) -> {
            if (method.getName().equals("getKey")) return advancementKey;
            throw new AssertionError("Unexpected advancement operation: " + method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }
}
