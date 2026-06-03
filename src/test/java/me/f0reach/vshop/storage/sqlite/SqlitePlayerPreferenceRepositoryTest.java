package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.PlayerPreferenceRepositoryContract;

class SqlitePlayerPreferenceRepositoryTest extends PlayerPreferenceRepositoryContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
