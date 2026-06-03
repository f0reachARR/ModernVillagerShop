package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.TimezoneSafetyContract;

class SqliteTimezoneSafetyTest extends TimezoneSafetyContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
