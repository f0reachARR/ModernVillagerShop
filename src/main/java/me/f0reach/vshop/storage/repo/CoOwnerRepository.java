package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.CoOwner;
import me.f0reach.vshop.model.CoOwnerRole;

import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface CoOwnerRepository {

    List<CoOwner> findByShop(UUID shopId) throws SQLException;

    List<UUID> findShopsByPlayer(UUID playerUuid, CoOwnerRole role) throws SQLException;

    void upsert(CoOwner owner) throws SQLException;

    void delete(UUID shopId, UUID playerUuid) throws SQLException;
}
