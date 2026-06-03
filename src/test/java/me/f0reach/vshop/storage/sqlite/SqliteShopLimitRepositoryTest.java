package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ShopLimitRepositoryContract;

class SqliteShopLimitRepositoryTest extends ShopLimitRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
