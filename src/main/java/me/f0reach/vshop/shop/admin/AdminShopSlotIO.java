package me.f0reach.vshop.shop.admin;

import me.f0reach.vshop.model.InventoryEntry;
import me.f0reach.vshop.model.LimitScope;
import me.f0reach.vshop.model.Shop;
import me.f0reach.vshop.model.ShopSlot;
import me.f0reach.vshop.model.TradeSide;
import me.f0reach.vshop.storage.repo.ShopInventoryRepository;
import me.f0reach.vshop.storage.repo.ShopSlotRepository;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Export / import of admin-shop slot definitions to a YAML file under the
 * plugin data folder ({@code plugins/ModernVillagerShop/exports/}). Import
 * fully replaces the target admin shop's slots and writes an auto-backup of
 * the pre-import state next to the source file.
 */
public final class AdminShopSlotIO {

    public static final int FORMAT_VERSION = 1;
    public static final String DIRECTORY = "exports";
    static final Pattern FILE_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_.\\-]+$");
    private static final String EXTENSION = ".yml";
    private static final DateTimeFormatter BACKUP_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC);

    private final File dataFolder;
    private final ShopSlotRepository slots;
    private final ShopInventoryRepository inventory;
    private final Clock clock;

    public AdminShopSlotIO(File dataFolder, ShopSlotRepository slots, ShopInventoryRepository inventory, Clock clock) {
        this.dataFolder = dataFolder;
        this.slots = slots;
        this.inventory = inventory;
        this.clock = clock;
    }

    public Path exportSlots(Shop shop, String rawFileName, boolean overwrite)
            throws IOException, SQLException {
        requireAdmin(shop);
        Path target = resolveExportPath(rawFileName);
        if (!overwrite && java.nio.file.Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString());
        }
        List<ShopSlot> loaded = slots.findByShop(shop.id());
        writeYaml(shop, loaded, target);
        return target;
    }

    public ImportResult importSlots(Shop shop, String rawFileName)
            throws IOException, SQLException, InvalidExportFormatException {
        requireAdmin(shop);
        Path source = resolveExportPath(rawFileName);
        if (!java.nio.file.Files.exists(source)) {
            throw new NoSuchFileException(source.toString());
        }

        List<ParsedSlot> parsed = parseSlots(source);

        List<ShopSlot> current = slots.findByShop(shop.id());
        Path backup = writeBackup(shop, current, source);

        for (ShopSlot existing : current) {
            slots.delete(existing.id());
        }
        // shop_inventory is not tied to admin shops in normal operation, but
        // guard against orphan rows so overwrite is truly clean.
        for (int slotIndex : distinctSlotIndicesInInventory(shop.id())) {
            inventory.delete(shop.id(), slotIndex);
        }

        int inserted = 0;
        for (ParsedSlot p : parsed) {
            ShopSlot slot = new ShopSlot(UUID.randomUUID(), shop.id(), p.slotIndex, p.side, p.item,
                    p.unitPrice, p.buyUnitPrice, p.unitAmount, p.buyCapacity, p.tradeLimit,
                    p.limitScope, p.resetPeriod);
            slots.upsert(slot);
            inserted++;
        }

        return new ImportResult(current.size(), inserted, backup);
    }

    Path resolveExportPath(String rawFileName) throws IOException {
        String normalized = normalizeFileName(rawFileName);
        File dir = new File(dataFolder, DIRECTORY);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Failed to create export directory: " + dir.getAbsolutePath());
        }
        return dir.toPath().resolve(normalized);
    }

    static String normalizeFileName(String rawFileName) {
        if (rawFileName == null) throw new IllegalArgumentException("File name is required.");
        String trimmed = rawFileName.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("File name is required.");
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..")) {
            throw new IllegalArgumentException("Invalid file name: " + rawFileName);
        }
        if (!FILE_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Invalid file name: " + rawFileName);
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(EXTENSION)) {
            trimmed = trimmed + EXTENSION;
        }
        return trimmed;
    }

    private static void requireAdmin(Shop shop) {
        if (!shop.isAdminShop()) {
            throw new IllegalArgumentException("Not an admin shop: " + shop.id());
        }
    }

    private void writeYaml(Shop shop, List<ShopSlot> loaded, Path target) throws IOException {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("version", FORMAT_VERSION);
        yml.set("exported_at", DateTimeFormatter.ISO_INSTANT.format(clock.instant()));
        yml.set("shop.id", shop.id().toString());
        if (shop.name() != null) yml.set("shop.name", shop.name());
        yml.set("shop.row_count", shop.rowCount());
        if (shop.profession() != null) yml.set("shop.profession", shop.profession().getKey().toString());

        // Represent slots as a keyed section (entry-0, entry-1, ...) so nested
        // ItemStack instances are serialized via Bukkit's ConfigurationSerializable
        // path. Preserving list order is unnecessary because the import routes
        // each entry by its explicit slot_index.
        for (int i = 0; i < loaded.size(); i++) {
            ShopSlot slot = loaded.get(i);
            ConfigurationSection s = yml.createSection("slots.entry-" + i);
            s.set("slot_index", slot.slotIndex());
            s.set("side", slot.side().name());
            s.set("item", slot.itemTemplate());
            s.set("unit_price", slot.unitPrice().toPlainString());
            if (slot.buyUnitPrice() != null) s.set("buy_unit_price", slot.buyUnitPrice().toPlainString());
            s.set("unit_amount", slot.unitAmount());
            s.set("buy_capacity", slot.buyCapacity());
            if (slot.tradeLimit() != null) {
                s.set("trade_limit", slot.tradeLimit());
                s.set("limit_scope", slot.limitScope().name());
            }
            if (slot.resetPeriod() != null) s.set("reset_period", slot.resetPeriod().toString());
        }
        yml.save(target.toFile());
    }

    private List<ParsedSlot> parseSlots(Path source) throws InvalidExportFormatException {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(source.toFile());
        int version = yml.getInt("version", -1);
        if (version != FORMAT_VERSION) {
            throw new InvalidExportFormatException("Unsupported version: " + version);
        }
        ConfigurationSection slotsSection = yml.getConfigurationSection("slots");
        if (slotsSection == null) {
            throw new InvalidExportFormatException("Missing 'slots' section.");
        }
        List<ParsedSlot> out = new ArrayList<>();
        Set<Integer> seenIndices = new HashSet<>();
        for (String key : slotsSection.getKeys(false)) {
            ConfigurationSection entry = slotsSection.getConfigurationSection(key);
            if (entry == null) {
                throw new InvalidExportFormatException("slots." + key + " is not a mapping.");
            }
            ParsedSlot parsed = parseSlot(key, entry);
            if (!seenIndices.add(parsed.slotIndex)) {
                throw new InvalidExportFormatException("Duplicate slot_index: " + parsed.slotIndex);
            }
            out.add(parsed);
        }
        return out;
    }

    private ParsedSlot parseSlot(String key, ConfigurationSection section) throws InvalidExportFormatException {
        String prefix = "slots." + key;
        int slotIndex = section.getInt("slot_index", Integer.MIN_VALUE);
        if (slotIndex == Integer.MIN_VALUE || slotIndex < 0) {
            throw new InvalidExportFormatException(prefix + ": 'slot_index' must be >= 0.");
        }
        TradeSide side = enumOrFail(prefix + ".side", section.getString("side"), TradeSide.class);
        ItemStack item = section.getItemStack("item");
        if (item == null) {
            throw new InvalidExportFormatException(prefix + ": missing or invalid 'item'.");
        }
        BigDecimal unitPrice = parseDecimal(prefix + ".unit_price",
                section.getString("unit_price"), true);
        BigDecimal buyUnitPrice = parseDecimal(prefix + ".buy_unit_price",
                section.getString("buy_unit_price"), false);
        int unitAmount = section.getInt("unit_amount", 1);
        if (unitAmount < 1) {
            throw new InvalidExportFormatException(prefix + ": 'unit_amount' must be >= 1.");
        }
        int buyCapacity = section.getInt("buy_capacity", 0);
        Integer tradeLimit = section.contains("trade_limit") && section.get("trade_limit") != null
                ? section.getInt("trade_limit") : null;
        LimitScope scope = tradeLimit == null
                ? LimitScope.PER_PLAYER
                : enumOrFail(prefix + ".limit_scope",
                        section.getString("limit_scope", LimitScope.PER_PLAYER.name()), LimitScope.class);
        Duration resetPeriod = parseDuration(prefix + ".reset_period",
                section.getString("reset_period"));
        return new ParsedSlot(slotIndex, side, item, unitPrice, buyUnitPrice, unitAmount, buyCapacity,
                tradeLimit, scope, resetPeriod);
    }

    private static <E extends Enum<E>> E enumOrFail(String field, String value, Class<E> type)
            throws InvalidExportFormatException {
        if (value == null) throw new InvalidExportFormatException("Missing " + field + ".");
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new InvalidExportFormatException("Invalid " + field + ": " + value);
        }
    }

    private static BigDecimal parseDecimal(String field, String value, boolean required)
            throws InvalidExportFormatException {
        if (value == null || value.isEmpty()) {
            if (required) throw new InvalidExportFormatException("Missing " + field + ".");
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            throw new InvalidExportFormatException("Invalid " + field + ": " + value);
        }
    }

    private static Duration parseDuration(String field, String value) throws InvalidExportFormatException {
        if (value == null || value.isEmpty()) return null;
        try {
            Duration d = Duration.parse(value);
            return d.isZero() ? null : d;
        } catch (DateTimeParseException ex) {
            throw new InvalidExportFormatException("Invalid " + field + ": " + value);
        }
    }

    private Path writeBackup(Shop shop, List<ShopSlot> current, Path source) throws IOException {
        String base = source.getFileName().toString();
        if (base.toLowerCase(Locale.ROOT).endsWith(EXTENSION)) {
            base = base.substring(0, base.length() - EXTENSION.length());
        }
        String backupName = base + ".pre-import-" + BACKUP_STAMP.format(clock.instant()) + EXTENSION;
        Path backupPath = source.resolveSibling(backupName);
        writeYaml(shop, current, backupPath);
        return backupPath;
    }

    private Set<Integer> distinctSlotIndicesInInventory(UUID shopId) throws SQLException {
        Set<Integer> indices = new HashSet<>();
        for (InventoryEntry entry : inventory.findByShop(shopId)) {
            indices.add(entry.slotIndex());
        }
        return indices;
    }

    public static final class ImportResult {
        private final int deleted;
        private final int inserted;
        private final Path backupPath;

        public ImportResult(int deleted, int inserted, Path backupPath) {
            this.deleted = deleted;
            this.inserted = inserted;
            this.backupPath = backupPath;
        }

        public int deleted() { return deleted; }
        public int inserted() { return inserted; }
        public Path backupPath() { return backupPath; }
    }

    public static final class InvalidExportFormatException extends Exception {
        public InvalidExportFormatException(String message) {
            super(message);
        }
    }

    private record ParsedSlot(int slotIndex, TradeSide side, ItemStack item, BigDecimal unitPrice,
                              BigDecimal buyUnitPrice, int unitAmount, int buyCapacity,
                              Integer tradeLimit, LimitScope limitScope, Duration resetPeriod) {}
}
