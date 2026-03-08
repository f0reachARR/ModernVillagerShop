package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.Listing;
import me.f0reach.vshop.model.ListingWithAccess;
import me.f0reach.vshop.model.ListingMode;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListingRepository {
    int create(int shopId, int uiSlot, ListingMode mode, byte[] itemSerialized,
               double unitPrice, int tradeQuantity, int stock, int targetStock,
               int cooldownSeconds, int lifetimeLimitPerPlayer) throws SQLException;

    List<Listing> findByShopId(int shopId) throws SQLException;

    List<ListingWithAccess> findDisplayEntriesByShopId(int shopId, UUID playerUuid, Instant now) throws SQLException;

    Optional<Listing> findById(int listingId) throws SQLException;

    int countByShopId(int shopId) throws SQLException;

    boolean existsByShopIdAndSlot(int shopId, int uiSlot) throws SQLException;

    void updateListingDetails(int listingId, double unitPrice, int tradeQuantity, int stock, int targetStock,
                              int cooldownSeconds, int lifetimeLimitPerPlayer) throws SQLException;

    void setEnabled(int listingId, boolean enabled) throws SQLException;

    void delete(int listingId) throws SQLException;

    boolean decrementStock(int listingId, int amount) throws SQLException;

    boolean incrementStock(int listingId, int amount) throws SQLException;
}
