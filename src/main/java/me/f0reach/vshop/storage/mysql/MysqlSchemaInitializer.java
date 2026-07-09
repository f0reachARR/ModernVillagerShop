package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.storage.repo.SchemaInitializer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

/**
 * Creates the MySQL schema. Uses InnoDB+utf8mb4, fixed-width VARCHAR for UUIDs,
 * BIGINT epoch millis for timestamps, and TINYINT(1) for booleans. Indexes are
 * created without {@code IF NOT EXISTS} because MySQL does not support that
 * clause; duplicate-key errors are swallowed instead.
 */
public final class MysqlSchemaInitializer implements SchemaInitializer {

    private static final int ERROR_DUPLICATE_KEY = 1061;
    private static final int ERROR_DUPLICATE_COLUMN = 1060;

    private final DataSource dataSource;

    public MysqlSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void init() throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            for (String ddl : TABLES) st.executeUpdate(ddl);
            for (String ddl : INDEXES) {
                try {
                    st.executeUpdate(ddl);
                } catch (SQLException ex) {
                    if (!isDuplicateIndex(ex)) throw ex;
                }
            }
            for (String ddl : ALTERS) {
                try {
                    st.executeUpdate(ddl);
                } catch (SQLException ex) {
                    if (!isDuplicateColumn(ex)) throw ex;
                }
            }
        }
    }

    private static boolean isDuplicateIndex(SQLException ex) {
        if (ex.getErrorCode() == ERROR_DUPLICATE_KEY) return true;
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("duplicate key") || msg.contains("already exists");
    }

    private static boolean isDuplicateColumn(SQLException ex) {
        if (ex.getErrorCode() == ERROR_DUPLICATE_COLUMN) return true;
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase(Locale.ROOT);
        return msg.contains("duplicate column");
    }

    private static final String SUFFIX = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

    private static final List<String> TABLES = List.of(
            "CREATE TABLE IF NOT EXISTS shops (" +
                    "id VARCHAR(36) PRIMARY KEY," +
                    "type VARCHAR(32) NOT NULL," +
                    "owner_uuid VARCHAR(36)," +
                    "world_id VARCHAR(36) NOT NULL," +
                    "x DOUBLE NOT NULL," +
                    "y DOUBLE NOT NULL," +
                    "z DOUBLE NOT NULL," +
                    "yaw DECIMAL(10,4) NOT NULL DEFAULT 0," +
                    "pitch DECIMAL(10,4) NOT NULL DEFAULT 0," +
                    "villager_entity_id VARCHAR(36)," +
                    "profession VARCHAR(128)," +
                    "name VARCHAR(255) NOT NULL," +
                    "suspended TINYINT(1) NOT NULL DEFAULT 0," +
                    "row_count INT NOT NULL DEFAULT 1," +
                    "created_at BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL" +
                    ")" + SUFFIX,
            "CREATE TABLE IF NOT EXISTS shop_co_owners (" +
                    "shop_id VARCHAR(36) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "role VARCHAR(16) NOT NULL," +
                    "share_pct DECIMAL(5,2) NOT NULL," +
                    "added_at BIGINT NOT NULL," +
                    "added_by VARCHAR(36)," +
                    "PRIMARY KEY (shop_id, player_uuid)" +
                    ")" + SUFFIX,
            "CREATE TABLE IF NOT EXISTS shop_slots (" +
                    "id VARCHAR(36) PRIMARY KEY," +
                    "shop_id VARCHAR(36) NOT NULL," +
                    "slot_index INT NOT NULL," +
                    "side VARCHAR(8) NOT NULL," +
                    "item_data LONGBLOB NOT NULL," +
                    "unit_price DECIMAL(20,4) NOT NULL," +
                    "buy_unit_price DECIMAL(20,4)," +
                    "unit_amount INT NOT NULL," +
                    "buy_capacity INT NOT NULL DEFAULT 0," +
                    "trade_limit INT," +
                    "limit_scope VARCHAR(16) NOT NULL," +
                    "reset_period_sec BIGINT," +
                    "command TEXT," +
                    "UNIQUE KEY uk_shop_slot (shop_id, slot_index)" +
                    ")" + SUFFIX,
            "CREATE TABLE IF NOT EXISTS shop_inventory (" +
                    "shop_id VARCHAR(36) NOT NULL," +
                    "slot_index INT NOT NULL," +
                    "item_data LONGBLOB NOT NULL," +
                    "amount INT NOT NULL," +
                    "PRIMARY KEY (shop_id, slot_index)" +
                    ")" + SUFFIX,
            "CREATE TABLE IF NOT EXISTS shop_limits (" +
                    "slot_id VARCHAR(36) NOT NULL," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "amount INT NOT NULL DEFAULT 0," +
                    "window_start BIGINT NOT NULL," +
                    "PRIMARY KEY (slot_id, player_uuid)" +
                    ")" + SUFFIX,
            "CREATE TABLE IF NOT EXISTS shop_transactions (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "occurred_at BIGINT NOT NULL," +
                    "shop_id VARCHAR(36) NOT NULL," +
                    "slot_id VARCHAR(36)," +
                    "side VARCHAR(8) NOT NULL," +
                    "buyer_uuid VARCHAR(36)," +
                    "seller_uuid VARCHAR(36)," +
                    "item_data LONGBLOB NOT NULL," +
                    "amount INT NOT NULL," +
                    "pack_count INT NOT NULL DEFAULT 0," +
                    "unit_price DECIMAL(20,4) NOT NULL," +
                    "fee DECIMAL(20,4) NOT NULL," +
                    "base_price DECIMAL(20,4)," +
                    "final_price DECIMAL(20,4)," +
                    "resolved_by VARCHAR(255)" +
                    ")" + SUFFIX,
            "CREATE TABLE IF NOT EXISTS shop_notifications (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "player_uuid VARCHAR(36) NOT NULL," +
                    "shop_id VARCHAR(36)," +
                    "transaction_id BIGINT," +
                    "count INT NOT NULL DEFAULT 0," +
                    "total_amount DECIMAL(20,4) NOT NULL DEFAULT 0," +
                    "created_at BIGINT NOT NULL," +
                    "delivered TINYINT(1) NOT NULL DEFAULT 0" +
                    ")" + SUFFIX,
            "CREATE TABLE IF NOT EXISTS player_preferences (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "notifications TINYINT(1) NOT NULL DEFAULT 1" +
                    ")" + SUFFIX,
            "CREATE TABLE IF NOT EXISTS player_cache (" +
                    "player_uuid VARCHAR(36) PRIMARY KEY," +
                    "name VARCHAR(255) NOT NULL," +
                    "name_lower VARCHAR(255) NOT NULL," +
                    "texture_value MEDIUMTEXT," +
                    "texture_signature MEDIUMTEXT," +
                    "texture_updated_at BIGINT," +
                    "last_seen BIGINT NOT NULL," +
                    "updated_at BIGINT NOT NULL" +
                    ")" + SUFFIX
    );

    private static final List<String> INDEXES = List.of(
            "CREATE INDEX idx_shops_owner ON shops(owner_uuid)",
            "CREATE INDEX idx_shops_world ON shops(world_id)",
            "CREATE INDEX idx_slots_shop ON shop_slots(shop_id)",
            "CREATE INDEX idx_inv_shop ON shop_inventory(shop_id)",
            "CREATE INDEX idx_tx_shop ON shop_transactions(shop_id)",
            "CREATE INDEX idx_tx_buyer ON shop_transactions(buyer_uuid)",
            "CREATE INDEX idx_tx_seller ON shop_transactions(seller_uuid)",
            "CREATE INDEX idx_notif_player ON shop_notifications(player_uuid)",
            "CREATE INDEX idx_cache_name_lower ON player_cache(name_lower)",
            "CREATE INDEX idx_cache_last_seen ON player_cache(last_seen)"
    );

    /**
     * Idempotent additive migrations for pre-existing installations. Each ADD
     * COLUMN is attempted once at startup; duplicate-column errors (1060) are
     * swallowed so re-runs are a no-op.
     */
    private static final List<String> ALTERS = List.of(
            "ALTER TABLE shop_slots ADD COLUMN command TEXT"
    );
}
