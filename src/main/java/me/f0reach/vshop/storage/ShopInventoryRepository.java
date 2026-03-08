package me.f0reach.vshop.storage;

import java.sql.SQLException;
import java.util.Map;

public interface ShopInventoryRepository {
    Map<Integer, byte[]> findByShopId(int shopId) throws SQLException;

    void upsertSlot(int shopId, int slotIndex, byte[] itemSerialized) throws SQLException;

    void deleteSlot(int shopId, int slotIndex) throws SQLException;
}
