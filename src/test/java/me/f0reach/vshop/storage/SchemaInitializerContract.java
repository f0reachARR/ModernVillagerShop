package me.f0reach.vshop.storage;

import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catches "added a table to one dialect but not the other" drift. Both
 * dialects must end up exposing the same nine logical tables; verifying it
 * here means a future schema change has to update both sides.
 */
public abstract class SchemaInitializerContract extends AbstractRepositoryContract {

    private static final Set<String> EXPECTED = Set.of(
            "shops",
            "shop_co_owners",
            "shop_slots",
            "shop_inventory",
            "shop_limits",
            "shop_transactions",
            "shop_notifications",
            "player_preferences",
            "player_cache"
    );

    @Test
    void initialiserCreatesAllExpectedTables() throws SQLException {
        Set<String> present = readTableNames();
        for (String table : EXPECTED) {
            assertTrue(present.contains(table), "missing table: " + table + ", saw " + present);
        }
    }

    @Test
    void runningInitialiserAgainIsIdempotent() throws SQLException {
        // Re-running must not fail; both dialects guard against re-create.
        try {
            handle.initializer().init();
        } catch (SQLException ex) {
            throw ex;
        }
    }

    private Set<String> readTableNames() throws SQLException {
        Set<String> names = new HashSet<>();
        try (Connection c = dataSource().getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            try (ResultSet rs = md.getTables(c.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    names.add(rs.getString("TABLE_NAME").toLowerCase());
                }
            }
        }
        return names;
    }
}
