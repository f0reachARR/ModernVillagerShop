package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ConnectionProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTransactionRepositoryTest {
    private static final DateTimeFormatter SQLITE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void recordsAndQueriesTradesPerPlayerAndWindow() throws Exception {
        SqliteTestDatabase database = SqliteTestDatabase.create(tempDir, "transactions.db");
        ConnectionProvider provider = database.connectionProvider();
        SqliteTransactionRepository repository = new SqliteTransactionRepository(provider);

        UUID buyerUuid = UUID.randomUUID();
        UUID otherBuyerUuid = UUID.randomUUID();
        UUID sellerUuid = UUID.randomUUID();

        repository.record(11, 101, "PURCHASE", buyerUuid, sellerUuid, 3, 25.0, 2.5, 22.5);
        repository.record(11, 101, "PURCHASE", buyerUuid, null, 1, 5.0, 0.5, 4.5);
        repository.record(11, 101, "PURCHASE", otherBuyerUuid, null, 1, 5.0, 0.5, 4.5);
        repository.record(11, 202, "PURCHASE", buyerUuid, null, 1, 5.0, 0.5, 4.5);

        Instant now = Instant.parse("2026-03-08T00:00:00Z");
        updateCreatedAt(provider, 1, now.minusSeconds(120));
        updateCreatedAt(provider, 2, now.minusSeconds(20));
        updateCreatedAt(provider, 3, now.minusSeconds(10));
        updateCreatedAt(provider, 4, now.minusSeconds(5));

        assertEquals(2, repository.countTradesForPlayer(101, buyerUuid));
        assertEquals(1, repository.countTradesForPlayerSince(101, buyerUuid, now.minusSeconds(60)));

        Optional<Instant> oldestInWindow = repository.findOldestTradeTimeForPlayer(101, buyerUuid, now.minusSeconds(60));
        assertTrue(oldestInWindow.isPresent());
        assertEquals(now.minusSeconds(20), oldestInWindow.orElseThrow());

        Optional<Instant> noneInWindow = repository.findOldestTradeTimeForPlayer(101, buyerUuid, now.minusSeconds(10));
        assertTrue(noneInWindow.isEmpty());
    }

    private void updateCreatedAt(ConnectionProvider provider, int txId, Instant createdAt) throws SQLException {
        String sql = "UPDATE transactions SET created_at = ? WHERE tx_id = ?";
        try (Connection conn = provider.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, SQLITE_TIMESTAMP.format(createdAt));
            ps.setInt(2, txId);
            ps.executeUpdate();
        }
    }
}
