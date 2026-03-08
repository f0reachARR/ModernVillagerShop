package me.f0reach.vshop.storage;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository {
    void record(int shopId, int listingId, String direction,
                UUID buyerUuid, UUID sellerUuid,
                int qty, double gross, double fee, double net) throws SQLException;

    Optional<Instant> findOldestTradeTimeForPlayer(int listingId, UUID playerUuid, Instant since)
            throws SQLException;

    int countTradesForPlayer(int listingId, UUID playerUuid) throws SQLException;

    int countTradesForPlayerSince(int listingId, UUID playerUuid, Instant since) throws SQLException;
}
