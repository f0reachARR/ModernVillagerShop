package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ShopTransactionRepositoryContract;

class SqliteShopTransactionRepositoryTest extends ShopTransactionRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
