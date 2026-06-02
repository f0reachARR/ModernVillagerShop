package me.f0reach.vshop.storage.mysql;

import me.f0reach.vshop.storage.repo.PlayerPreferenceRepository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class MysqlPlayerPreferenceRepository implements PlayerPreferenceRepository {

    private final DataSource dataSource;

    public MysqlPlayerPreferenceRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean wantsNotifications(UUID playerUuid) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT notifications FROM player_preferences WHERE player_uuid = ?")) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next() || rs.getInt(1) != 0;
            }
        }
    }

    @Override
    public void setWantsNotifications(UUID playerUuid, boolean wants) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO player_preferences (player_uuid, notifications) VALUES (?, ?) " +
                             "ON DUPLICATE KEY UPDATE notifications = VALUES(notifications)")) {
            ps.setString(1, playerUuid.toString());
            ps.setInt(2, wants ? 1 : 0);
            ps.executeUpdate();
        }
    }
}
