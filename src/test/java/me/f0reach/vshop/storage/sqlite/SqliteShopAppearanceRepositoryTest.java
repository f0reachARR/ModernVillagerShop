package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ShopAppearanceRepositoryContract;

class SqliteShopAppearanceRepositoryTest extends ShopAppearanceRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
