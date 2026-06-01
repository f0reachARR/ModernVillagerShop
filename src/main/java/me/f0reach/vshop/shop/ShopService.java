package me.f0reach.vshop.shop;

import me.f0reach.vshop.config.PluginConfig;
import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopLocation;
import me.f0reach.vshop.model.ShopType;
import me.f0reach.vshop.shop.egg.SpawnEggMeta;
import me.f0reach.vshop.storage.StorageManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * Coordinates shop CRUD by combining storage, the in-memory registry and the
 * Villager manager. All public methods run on the main thread.
 */
public final class ShopService {

    private final StorageManager storage;
    private final ShopRegistry registry;
    private final ShopVillagerManager villagers;
    private final PluginConfig config;

    public ShopService(StorageManager storage, ShopRegistry registry, ShopVillagerManager villagers,
                       PluginConfig config) {
        this.storage = storage;
        this.registry = registry;
        this.villagers = villagers;
        this.config = config;
    }

    /**
     * Loads all shops from the database into the registry. Call once on enable.
     */
    public void loadAll() throws SQLException {
        registry.loadAll(storage.shops().findAll());
    }

    public boolean isNearAnotherShop(Location at) {
        double min = config.shop().minDistance();
        double minSq = min * min;
        UUID world = at.getWorld().getUID();
        for (Shop shop : registry.all()) {
            if (!world.equals(shop.location().worldId())) continue;
            ShopLocation l = shop.location();
            double dx = l.x() - at.getX();
            double dy = l.y() - at.getY();
            double dz = l.z() - at.getZ();
            if (dx * dx + dy * dy + dz * dz < minSq) return true;
        }
        return false;
    }

    /**
     * Creates a shop from a spawn-egg placement. Returns the newly created shop.
     * Throws {@link CreateException} on a validation failure that should leave
     * the egg unconsumed.
     */
    public Shop createFromEgg(Player creator, Location at, SpawnEggMeta egg) throws SQLException, CreateException {
        if (isNearAnotherShop(at)) {
            throw new CreateException("shop.place.too-close");
        }
        if (!egg.admin()) {
            int limit = config.shop().maxShopsPerPlayer();
            if (limit >= 0 && registry.countByOwner(creator.getUniqueId()) >= limit) {
                throw new CreateException("shop.place.too-many");
            }
        }

        UUID shopId = UUID.randomUUID();
        UUID ownerId = egg.admin() ? null : creator.getUniqueId();
        String defaultName = creator.getName() + "'s shop";
        if (egg.admin()) defaultName = "Admin Shop";

        Instant now = Instant.now();
        ShopLocation loc = ShopLocation.fromBukkit(at);
        Shop shop = new Shop(
                shopId,
                egg.admin() ? ShopType.ADMIN : ShopType.PLAYER,
                ownerId,
                loc,
                /*villagerEntityId*/ null,
                /*profession*/ null,
                defaultName,
                false,
                egg.rowCount(),
                now,
                now
        );

        UUID villagerId = villagers.spawn(shop, at, config);
        shop.setVillagerEntityId(villagerId);

        storage.shops().insert(shop);
        if (!egg.admin()) {
            storage.coOwners().upsert(new CoOwner(shopId, ownerId, CoOwnerRole.PRIMARY,
                    new BigDecimal("100.00"), now, ownerId));
        }
        registry.put(shop);
        return shop;
    }

    public void delete(Shop shop) throws SQLException {
        villagers.remove(shop);
        storage.shops().delete(shop.id());
        registry.remove(shop.id());
    }

    public void update(Shop shop) throws SQLException {
        shop.setUpdatedAt(Instant.now());
        storage.shops().update(shop);
        registry.put(shop);
    }

    public ShopRegistry registry() {
        return registry;
    }

    public ShopVillagerManager villagers() {
        return villagers;
    }

    public static final class CreateException extends Exception {
        private final String messageKey;

        public CreateException(String messageKey) {
            super(messageKey);
            this.messageKey = messageKey;
        }

        public String messageKey() {
            return messageKey;
        }
    }
}
