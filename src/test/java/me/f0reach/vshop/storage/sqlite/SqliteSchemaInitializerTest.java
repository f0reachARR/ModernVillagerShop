package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.SchemaInitializerContract;

class SqliteSchemaInitializerTest extends SchemaInitializerContract {
    @Override protected Backend backend() { return Backend.SQLITE; }
}
