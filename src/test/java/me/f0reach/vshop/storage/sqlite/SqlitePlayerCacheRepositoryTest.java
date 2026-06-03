package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.PlayerCacheRepositoryContract;

class SqlitePlayerCacheRepositoryTest extends PlayerCacheRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
