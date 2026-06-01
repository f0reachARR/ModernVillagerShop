package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.Shop;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopRepository {

    Optional<Shop> findById(UUID id) throws SQLException;

    List<Shop> findAll() throws SQLException;

    List<Shop> findByOwner(UUID ownerUuid) throws SQLException;

    List<Shop> findInWorld(UUID worldId) throws SQLException;

    void insert(Shop shop) throws SQLException;

    void update(Shop shop) throws SQLException;

    void delete(UUID shopId) throws SQLException;
}
