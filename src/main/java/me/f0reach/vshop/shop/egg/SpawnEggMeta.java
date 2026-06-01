package me.f0reach.vshop.shop.egg;

/**
 * Spawn-egg payload captured in PDC. {@code rowCount} == -1 indicates "infinite"
 * (paginated) capacity; {@code admin} indicates the admin-shop variant.
 */
public record SpawnEggMeta(int rowCount, boolean admin) {

    public static SpawnEggMeta ofAdmin() {
        return new SpawnEggMeta(-1, true);
    }

    public static SpawnEggMeta ofInfinitePlayer() {
        return new SpawnEggMeta(-1, false);
    }

    public static SpawnEggMeta ofFixedRows(int rows) {
        if (rows < 1) throw new IllegalArgumentException("rows must be >= 1");
        return new SpawnEggMeta(rows, false);
    }

    public boolean isInfinite() {
        return rowCount < 0 && !admin;
    }

    public String displayType() {
        if (admin) return "admin";
        if (rowCount < 0) return "inf";
        return String.valueOf(rowCount);
    }
}
