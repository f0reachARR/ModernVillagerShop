package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.CoOwnerRepositoryContract;

class SqliteCoOwnerRepositoryTest extends CoOwnerRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
