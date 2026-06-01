package me.f0reach.vshop.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Serializes ItemStack to a binary blob suitable for BLOB storage. Uses
 * Bukkit's object stream which preserves all NMS-level data including PDC.
 */
public final class ItemStackCodec {

    private ItemStackCodec() {}

    public static byte[] encode(ItemStack stack) {
        if (stack == null) return null;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             BukkitObjectOutputStream out = new BukkitObjectOutputStream(bos)) {
            out.writeObject(stack);
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new RuntimeException("Failed to encode ItemStack", ex);
        }
    }

    public static ItemStack decode(byte[] data) {
        if (data == null || data.length == 0) return null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             BukkitObjectInputStream in = new BukkitObjectInputStream(bis)) {
            return (ItemStack) in.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            throw new RuntimeException("Failed to decode ItemStack", ex);
        }
    }
}
