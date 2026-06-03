package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ShopInventoryRepositoryContract;

class SqliteShopInventoryRepositoryTest extends ShopInventoryRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
