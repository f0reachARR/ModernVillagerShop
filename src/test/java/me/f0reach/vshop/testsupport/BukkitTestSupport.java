package me.f0reach.vshop.testsupport;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Tiny façade around MockBukkit so storage tests can build real ItemStacks
 * without each test class re-doing the lifecycle dance. We boot one mock
 * server per JVM (idempotent) and tear it down implicitly when the JVM exits;
 * mock state never leaks between test classes because storage tests don't
 * register listeners.
 */
public final class BukkitTestSupport {

    private static boolean started;

    private BukkitTestSupport() {}

    public static synchronized void ensureStarted() {
        if (started) return;
        // mock() returns the server but throws if one is already up — guard via flag.
        MockBukkit.mock();
        started = true;
    }

    public static ItemStack item(Material material, int amount) {
        ensureStarted();
        return new ItemStack(material, amount);
    }

    public static ItemStack item(Material material) {
        return item(material, 1);
    }

    /** Force-load Bukkit before a test class touches anything Bukkit-flavoured. */
    public static void ensureBukkit() {
        ensureStarted();
        // Touch Bukkit so the static init runs.
        Bukkit.getServer();
    }
}
