package me.f0reach.vshop.storage;

import java.sql.SQLException;

public interface SchemaInitializer {
    void initialize(ConnectionProvider connectionProvider) throws SQLException;
}
