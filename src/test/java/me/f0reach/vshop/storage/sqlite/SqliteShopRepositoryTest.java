package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ShopRepositoryContract;

class SqliteShopRepositoryTest extends ShopRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
