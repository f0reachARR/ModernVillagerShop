package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.ShopNotificationRepositoryContract;

class SqliteShopNotificationRepositoryTest extends ShopNotificationRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
