package me.f0reach.vshop.storage.sqlite;

import me.f0reach.vshop.storage.repo.SchemaInitializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Creates the SQLite schema. SQLite stores integers as INTEGER (which is 64-bit
 * wide), booleans as 0/1 INTEGER, and uses {@code AUTOINCREMENT} for surrogate
 * keys. UUIDs are kept as TEXT and timestamps as epoch millis.
 */
public final class SqliteSchemaInitializer implements SchemaInitializer {

    private final DataSource dataSource;

    public SqliteSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void init() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            for (String ddl : TABLES) st.executeUpdate(ddl);
            for (String ddl : INDEXES) st.executeUpdate(ddl);
        }
    }

    private static final List<String> TABLES = List.of(
            "CREATE TABLE IF NOT EXISTS shops (" +
                    "id TEXT PRIMARY KEY," +
                    "type TEXT NOT NULL," +
                    "owner_uuid TEXT," +
                    "world_id TEXT NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw NUMERIC NOT NULL DEFAULT 0," +
                    "pitch NUMERIC NOT NULL DEFAULT 0," +
                    "villager_entity_id TEXT," +
                    "profession TEXT," +
                    "name TEXT NOT NULL," +
                    "suspended INTEGER NOT NULL DEFAULT 0," +
                    "row_count INTEGER NOT NULL DEFAULT 1," +
                    "created_at INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL" +
                    ")",
            "CREATE TABLE IF NOT EXISTS shop_co_owners (" +
                    "shop_id TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "role TEXT NOT NULL," +
                    "share_pct NUMERIC NOT NULL," +
                    "added_at INTEGER NOT NULL," +
                    "added_by TEXT," +
                    "PRIMARY KEY (shop_id, player_uuid)" +
                    ")",
            "CREATE TABLE IF NOT EXISTS shop_slots (" +
                    "id TEXT PRIMARY KEY," +
                    "shop_id TEXT NOT NULL," +
                    "slot_index INTEGER NOT NULL," +
                    "side TEXT NOT NULL," +
                    "item_data BLOB NOT NULL," +
                    "unit_price NUMERIC NOT NULL," +
                    "buy_unit_price NUMERIC," +
                    "unit_amount INTEGER NOT NULL," +
                    "buy_capacity INTEGER NOT NULL DEFAULT 0," +
                    "trade_limit INTEGER," +
                    "limit_scope TEXT NOT NULL," +
                    "reset_period_sec INTEGER," +
                    "UNIQUE (shop_id, slot_index)" +
                    ")",
            "CREATE TABLE IF NOT EXISTS shop_inventory (" +
                    "shop_id TEXT NOT NULL," +
                    "slot_index INTEGER NOT NULL," +
                    "item_data BLOB NOT NULL," +
                    "amount INTEGER NOT NULL," +
                    "PRIMARY KEY (shop_id, slot_index)" +
                    ")",
            "CREATE TABLE IF NOT EXISTS shop_limits (" +
                    "slot_id TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "amount INTEGER NOT NULL DEFAULT 0," +
                    "window_start INTEGER NOT NULL," +
                    "PRIMARY KEY (slot_id, player_uuid)" +
                    ")",
            "CREATE TABLE IF NOT EXISTS shop_transactions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "occurred_at INTEGER NOT NULL," +
                    "shop_id TEXT NOT NULL," +
                    "slot_id TEXT," +
                    "side TEXT NOT NULL," +
                    "buyer_uuid TEXT," +
                    "seller_uuid TEXT," +
                    "item_data BLOB NOT NULL," +
                    "amount INTEGER NOT NULL," +
                    "pack_count INTEGER NOT NULL DEFAULT 0," +
                    "unit_price NUMERIC NOT NULL," +
                    "fee NUMERIC NOT NULL," +
                    "base_price NUMERIC," +
                    "final_price NUMERIC," +
                    "resolved_by TEXT" +
                    ")",
            "CREATE TABLE IF NOT EXISTS shop_notifications (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "player_uuid TEXT NOT NULL," +
                    "shop_id TEXT," +
                    "transaction_id INTEGER," +
                    "count INTEGER NOT NULL DEFAULT 0," +
                    "total_amount NUMERIC NOT NULL DEFAULT 0," +
                    "created_at INTEGER NOT NULL," +
                    "delivered INTEGER NOT NULL DEFAULT 0" +
                    ")",
            "CREATE TABLE IF NOT EXISTS player_preferences (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "notifications INTEGER NOT NULL DEFAULT 1" +
                    ")",
            "CREATE TABLE IF NOT EXISTS player_cache (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "name TEXT NOT NULL," +
                    "name_lower TEXT NOT NULL," +
                    "texture_value TEXT," +
                    "texture_signature TEXT," +
                    "texture_updated_at INTEGER," +
                    "last_seen INTEGER NOT NULL," +
                    "updated_at INTEGER NOT NULL" +
                    ")"
    );

    private static final List<String> INDEXES = List.of(
            "CREATE INDEX IF NOT EXISTS idx_shops_owner ON shops(owner_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_shops_world ON shops(world_id)",
            "CREATE INDEX IF NOT EXISTS idx_slots_shop ON shop_slots(shop_id)",
            "CREATE INDEX IF NOT EXISTS idx_inv_shop ON shop_inventory(shop_id)",
            "CREATE INDEX IF NOT EXISTS idx_tx_shop ON shop_transactions(shop_id)",
            "CREATE INDEX IF NOT EXISTS idx_tx_buyer ON shop_transactions(buyer_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_tx_seller ON shop_transactions(seller_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_notif_player ON shop_notifications(player_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_cache_name_lower ON player_cache(name_lower)",
            "CREATE INDEX IF NOT EXISTS idx_cache_last_seen ON player_cache(last_seen)"
    );
}
