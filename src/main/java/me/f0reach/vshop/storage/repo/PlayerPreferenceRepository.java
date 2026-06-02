package me.f0reach.vshop.storage.repo;

import java.sql.SQLException;
import java.util.UUID;

/**
 * Tiny repository for the {@code player_preferences} table. Currently only the
 * trade-notification toggle is exposed; more knobs (chat formatting, etc.) can
 * be added later by extending the row.
 */
public interface PlayerPreferenceRepository {

    /** Returns {@code true} if the player wants trade notifications. Defaults to true if no row. */
    boolean wantsNotifications(UUID playerUuid) throws SQLException;

    void setWantsNotifications(UUID playerUuid, boolean wants) throws SQLException;
}
