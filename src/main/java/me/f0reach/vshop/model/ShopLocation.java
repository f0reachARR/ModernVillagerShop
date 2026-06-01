package me.f0reach.vshop.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

public record ShopLocation(UUID worldId, double x, double y, double z, float yaw, float pitch) {

    public static ShopLocation fromBukkit(Location loc) {
        return new ShopLocation(loc.getWorld().getUID(), loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch());
    }

    public Location toBukkit() {
        World w = Bukkit.getWorld(worldId);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }

    public boolean isWorldLoaded() {
        return Bukkit.getWorld(worldId) != null;
    }
}
