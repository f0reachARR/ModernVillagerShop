package me.f0reach.vshop.storage.repo;

import java.sql.SQLException;

/**
 * Initializes the database schema. SQLite and MySQL each have their own
 * implementation rather than sharing a templated DDL — accepting duplicated
 * statements keeps each backend's column types and quirks fully visible at
 * the point of use.
 */
public interface SchemaInitializer {

    void init() throws SQLException;
}
