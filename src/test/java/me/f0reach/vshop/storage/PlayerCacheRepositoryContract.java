package me.f0reach.vshop.storage;

import me.f0reach.vshop.model.PlayerCacheEntry;
import me.f0reach.vshop.storage.mysql.MysqlPlayerCacheRepository;
import me.f0reach.vshop.storage.repo.PlayerCacheRepository;
import me.f0reach.vshop.storage.sqlite.SqlitePlayerCacheRepository;
import me.f0reach.vshop.testsupport.AbstractRepositoryContract;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class PlayerCacheRepositoryContract extends AbstractRepositoryContract {

    private PlayerCacheRepository repository() {
        return backend() == Backend.SQLITE
                ? new SqlitePlayerCacheRepository(dataSource())
                : new MysqlPlayerCacheRepository(dataSource());
    }

    private static PlayerCacheEntry entry(UUID id, String name, Instant lastSeen,
                                          String textureValue, String textureSignature) {
        Instant updated = Instant.ofEpochSecond(1_700_000_000L);
        return new PlayerCacheEntry(id, name, name.toLowerCase(Locale.ROOT),
                textureValue, textureSignature,
                textureValue == null ? null : updated,
                lastSeen, updated);
    }

    @Test
    void upsertRoundTrips() throws SQLException {
        PlayerCacheRepository repo = repository();
        UUID id = UUID.randomUUID();
        repo.upsert(entry(id, "Steve", Instant.ofEpochSecond(1_700_000_500L),
                "texture-value", "sig"));

        Optional<PlayerCacheEntry> got = repo.findByUuid(id);
        assertTrue(got.isPresent());
        assertEquals("Steve", got.get().name());
        assertEquals("steve", got.get().nameLower());
        assertEquals("texture-value", got.get().textureValue());
        assertEquals("sig", got.get().textureSignature());
        assertNotNull(got.get().textureUpdatedAt());
    }

    @Test
    void findByNameIsCaseInsensitive() throws SQLException {
        PlayerCacheRepository repo = repository();
        UUID id = UUID.randomUUID();
        repo.upsert(entry(id, "AlEx", Instant.now(), null, null));

        Optional<PlayerCacheEntry> got = repo.findByName("ALEX");
        assertTrue(got.isPresent());
        assertEquals(id, got.get().playerUuid());
    }

    @Test
    void upsertPreservesPreviousTextureWhenIncomingIsNull() throws SQLException {
        PlayerCacheRepository repo = repository();
        UUID id = UUID.randomUUID();
        repo.upsert(entry(id, "Steve", Instant.now(), "tex", "sig"));
        // Second upsert without texture should NOT erase what we already had.
        repo.upsert(entry(id, "SteveRenamed", Instant.now(), null, null));

        PlayerCacheEntry got = repo.findByUuid(id).orElseThrow();
        assertEquals("SteveRenamed", got.name());
        assertEquals("tex", got.textureValue());
        assertEquals("sig", got.textureSignature());
    }

    @Test
    void pageByLastSeenOrdersDesc() throws SQLException {
        PlayerCacheRepository repo = repository();
        UUID older = UUID.randomUUID();
        UUID newer = UUID.randomUUID();
        repo.upsert(entry(older, "AAA", Instant.ofEpochSecond(1_000_000L), null, null));
        repo.upsert(entry(newer, "ZZZ", Instant.ofEpochSecond(2_000_000L), null, null));

        List<PlayerCacheEntry> page = repo.page(0, 10, false);
        assertEquals(2, page.size());
        assertEquals(newer, page.get(0).playerUuid());
        assertEquals(older, page.get(1).playerUuid());
    }

    @Test
    void pageByNameOrdersAsc() throws SQLException {
        PlayerCacheRepository repo = repository();
        UUID later = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        repo.upsert(entry(later, "Zoe", Instant.ofEpochSecond(2_000_000L), null, null));
        repo.upsert(entry(first, "Adam", Instant.ofEpochSecond(1_000_000L), null, null));

        List<PlayerCacheEntry> page = repo.page(0, 10, true);
        assertEquals(first, page.get(0).playerUuid());
        assertEquals(later, page.get(1).playerUuid());
    }

    @Test
    void searchSubstringPrefersPrefixMatches() throws SQLException {
        PlayerCacheRepository repo = repository();
        UUID anyOrder = UUID.randomUUID();
        UUID prefix = UUID.randomUUID();
        repo.upsert(entry(anyOrder, "xxalexyy", Instant.now(), null, null));
        repo.upsert(entry(prefix, "alex", Instant.now(), null, null));

        List<PlayerCacheEntry> matches = repo.search("alex", 10);
        assertEquals(2, matches.size());
        // Prefix match must come first per the CASE WHEN ... THEN 0 ELSE 1 ordering.
        assertEquals(prefix, matches.get(0).playerUuid());
    }

    @Test
    void countTracksRows() throws SQLException {
        PlayerCacheRepository repo = repository();
        assertEquals(0, repo.count());
        repo.upsert(entry(UUID.randomUUID(), "A", Instant.now(), null, null));
        repo.upsert(entry(UUID.randomUUID(), "B", Instant.now(), null, null));
        assertEquals(2, repo.count());
    }

    @Test
    void findByUnknownIsEmpty() throws SQLException {
        PlayerCacheRepository repo = repository();
        assertTrue(repo.findByUuid(UUID.randomUUID()).isEmpty());
        assertTrue(repo.findByName("ghost").isEmpty());
    }

    @Test
    void textureFieldsRemainNullWhenNotProvided() throws SQLException {
        PlayerCacheRepository repo = repository();
        UUID id = UUID.randomUUID();
        repo.upsert(entry(id, "NoTex", Instant.now(), null, null));

        PlayerCacheEntry got = repo.findByUuid(id).orElseThrow();
        assertNull(got.textureValue());
        assertNull(got.textureSignature());
        assertNull(got.textureUpdatedAt());
    }
}
