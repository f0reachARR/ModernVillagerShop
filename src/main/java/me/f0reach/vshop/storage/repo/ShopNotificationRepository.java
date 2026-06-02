package me.f0reach.vshop.storage.repo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ShopNotificationRepository {

    void queueTx(Connection c, UUID playerUuid, UUID shopId, Long transactionId, int count, BigDecimal totalAmount) throws SQLException;

    List<Pending> findUndelivered(UUID playerUuid) throws SQLException;

    void markDelivered(List<Long> ids) throws SQLException;

    record Pending(long id, UUID shopId, Long transactionId, int count, BigDecimal totalAmount, Instant createdAt) {}
}
