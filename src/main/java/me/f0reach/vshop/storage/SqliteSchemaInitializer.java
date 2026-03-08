package me.f0reach.vshop.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public final class SqliteSchemaInitializer implements SchemaInitializer {
    @Override
    public void initialize(ConnectionProvider connectionProvider) throws SQLException {
        String shopsTable = "CREATE TABLE IF NOT EXISTS shops ("
                + "shop_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "type VARCHAR(16) NOT NULL, "
                + "villager_uuid VARCHAR(36) NOT NULL UNIQUE, "
                + "owner_uuid VARCHAR(36), "
                + "world VARCHAR(64) NOT NULL, "
                + "x DOUBLE NOT NULL, "
                + "y DOUBLE NOT NULL, "
                + "z DOUBLE NOT NULL, "
                + "active BOOLEAN NOT NULL DEFAULT 1, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ")";

        String listingsTable = "CREATE TABLE IF NOT EXISTS listings ("
                + "listing_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "shop_id INTEGER NOT NULL, "
                + "ui_slot INTEGER NOT NULL, "
                + "mode VARCHAR(8) NOT NULL, "
                + "item_serialized BLOB NOT NULL, "
                + "unit_price DOUBLE NOT NULL, "
                + "trade_qty INTEGER NOT NULL DEFAULT 1, "
                + "stock INTEGER NOT NULL DEFAULT 0, "
                + "target_stock INTEGER NOT NULL DEFAULT 0, "
                + "cooldown_seconds INTEGER NOT NULL DEFAULT 0, "
                + "lifetime_limit_per_player INTEGER NOT NULL DEFAULT 0, "
                + "enabled BOOLEAN NOT NULL DEFAULT 1, "
                + "updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "UNIQUE (shop_id, ui_slot), "
                + "FOREIGN KEY (shop_id) REFERENCES shops(shop_id) ON DELETE CASCADE"
                + ")";

        String transactionsTable = "CREATE TABLE IF NOT EXISTS transactions ("
                + "tx_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "shop_id INTEGER NOT NULL, "
                + "listing_id INTEGER NOT NULL, "
                + "direction VARCHAR(16) NOT NULL, "
                + "buyer_uuid VARCHAR(36) NOT NULL, "
                + "seller_uuid VARCHAR(36), "
                + "qty INTEGER NOT NULL, "
                + "gross DOUBLE NOT NULL, "
                + "fee DOUBLE NOT NULL, "
                + "net DOUBLE NOT NULL, "
                + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "FOREIGN KEY (shop_id) REFERENCES shops(shop_id), "
                + "FOREIGN KEY (listing_id) REFERENCES listings(listing_id)"
                + ")";

        String shopInventoryTable = "CREATE TABLE IF NOT EXISTS shop_inventory_slots ("
                + "shop_id INTEGER NOT NULL, "
                + "slot_index INTEGER NOT NULL, "
                + "item_serialized BLOB NOT NULL, "
                + "PRIMARY KEY (shop_id, slot_index), "
                + "FOREIGN KEY (shop_id) REFERENCES shops(shop_id) ON DELETE CASCADE"
                + ")";

        try (Connection conn = connectionProvider.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON");
            stmt.execute(shopsTable);
            stmt.execute(listingsTable);
            stmt.execute(transactionsTable);
            stmt.execute(shopInventoryTable);
            ensureListingColumns(conn);
        }
    }

    private void ensureListingColumns(Connection conn) throws SQLException {
        ensureColumn(conn, "listings", "trade_qty",
                "ALTER TABLE listings ADD COLUMN trade_qty INTEGER NOT NULL DEFAULT 1");
        ensureColumn(conn, "listings", "cooldown_seconds",
                "ALTER TABLE listings ADD COLUMN cooldown_seconds INTEGER NOT NULL DEFAULT 0");
        ensureColumn(conn, "listings", "lifetime_limit_per_player",
                "ALTER TABLE listings ADD COLUMN lifetime_limit_per_player INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureColumn(Connection conn, String tableName, String columnName, String alterSql) throws SQLException {
        if (hasColumn(conn, tableName, columnName)) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(alterSql);
        }
    }

    private boolean hasColumn(Connection conn, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        try (var columns = metaData.getColumns(null, conn.getSchema(), tableName, columnName)) {
            return columns.next();
        }
    }
}
