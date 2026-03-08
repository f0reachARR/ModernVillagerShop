package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopType;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopRepository {
    int create(ShopType type, UUID villagerUuid, UUID ownerUuid,
               String world, double x, double y, double z) throws SQLException;

    Optional<Shop> findById(int shopId) throws SQLException;

    Optional<Shop> findByVillagerUuid(UUID villagerUuid) throws SQLException;

    List<Shop> findAll() throws SQLException;

    void delete(int shopId) throws SQLException;
}
