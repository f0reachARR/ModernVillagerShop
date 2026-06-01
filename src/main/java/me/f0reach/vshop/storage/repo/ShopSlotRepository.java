package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.ShopSlot;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShopSlotRepository {

    List<ShopSlot> findByShop(UUID shopId) throws SQLException;

    Optional<ShopSlot> findById(UUID id) throws SQLException;

    void upsert(ShopSlot slot) throws SQLException;

    void delete(UUID id) throws SQLException;
}
