package me.f0reach.vshop.storage.repo;

import me.f0reach.vshop.model.ShopAppearance;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@code shop_appearance} and its child {@code
 * shop_appearance_equipment}. The two are always read and written together —
 * an appearance without its equipment is not a meaningful half.
 *
 * <p>Absence of a row is meaningful: it means "plain Villager", which is why
 * {@link #find(UUID)} returns an empty Optional rather than a default instance.
 * Use {@link ShopAppearance#defaultFor(UUID)} at the call site when a concrete
 * value is needed.
 */
public interface ShopAppearanceRepository {

    Optional<ShopAppearance> find(UUID shopId) throws SQLException;

    /** Every stored appearance, for bulk load on plugin enable. */
    List<ShopAppearance> findAll() throws SQLException;

    /** Inserts or replaces the appearance and its complete equipment set. */
    void upsert(ShopAppearance appearance) throws SQLException;

    /** Drops the appearance and its equipment, returning the shop to a plain Villager. */
    void delete(UUID shopId) throws SQLException;
}
