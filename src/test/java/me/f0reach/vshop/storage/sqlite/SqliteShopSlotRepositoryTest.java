package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ShopSlotRepositoryContract;

class SqliteShopSlotRepositoryTest extends ShopSlotRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
