package me.f0reach.vshop.storage;

import me.f0reach.vshop.storage.mysql.MysqlPlayerPreferenceRepository;
import me.f0reach.vshop.storage.repo.PlayerPreferenceRepository;
import me.f0reach.vshop.storage.sqlite.SqlitePlayerPreferenceRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class PlayerPreferenceRepositoryContract extends AbstractRepositoryContract {

    private PlayerPreferenceRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqlitePlayerPreferenceRepository(dataSource())
                : new MysqlPlayerPreferenceRepository(dataSource());
    }

    @Test
    void defaultsToOptedInWhenNoRow() throws SQLException {
        assertTrue(repository().wantsNotifications(UUID.randomUUID()));
    }

    @Test
    void setFalseOverridesDefault() throws SQLException {
        PlayerPreferenceRepository repo = repository();
        UUID player = UUID.randomUUID();
        repo.setWantsNotifications(player, false);
        assertFalse(repo.wantsNotifications(player));
    }

    @Test
    void toggleRoundTrips() throws SQLException {
        PlayerPreferenceRepository repo = repository();
        UUID player = UUID.randomUUID();
        repo.setWantsNotifications(player, false);
        repo.setWantsNotifications(player, true);
        assertTrue(repo.wantsNotifications(player));
    }
}
