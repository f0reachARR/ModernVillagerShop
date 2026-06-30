# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository

ModernVillagerShop — a Paper 1.21.8+ plugin that turns Villagers into player/admin-owned shops with SELL and BUY (order-slot) trade, a Dialog + chest hybrid UI, and SQLite/MySQL storage. Java 21 only. The authoritative spec is [spec.md](spec.md); [AGENTS.md](AGENTS.md) holds the human-facing contribution guide and has full overlap with this file — keep both honest if you change one.

## Commands

- `./gradlew build` — compile + run the SQLite test suite + produce the shaded plugin JAR (this is the pre-PR check).
- `./gradlew shadowJar` — only the fat JAR (relocates HikariCP under `me.f0reach.vshop.lib.hikari`).
- `./gradlew runServer` — boot a local Paper test server (currently pinned to 1.21.11 in [build.gradle](build.gradle)). The working tree at [run/](run/) is the server data dir.
- `./gradlew test --tests me.f0reach.vshop.storage.sqlite.SqliteShopRepositoryTest` — single test class. Append `.methodName` for one method.
- `./gradlew clean` — wipe `build/`.

### Running the MySQL tests

MySQL repository tests are gated on `VSHOP_TEST_MYSQL_URL` via `@EnabledIfEnvironmentVariable` and are silently skipped without it. Each JVM allocates a throwaway `vshop_test_<uuid>` schema, so the user needs `CREATE`/`DROP` plus `ALL` on `vshop_test_%.*`. Local recipe:

```bash
docker run --rm -p 3307:3306 -e MYSQL_ROOT_PASSWORD=rootpw \
  -e MYSQL_USER=vshop -e MYSQL_PASSWORD=vshop mysql:8.4
# then grant: GRANT ALL ON `vshop_test_%`.* TO 'vshop'@'%'; GRANT CREATE,DROP ON *.* TO 'vshop'@'%';
VSHOP_TEST_MYSQL_URL=jdbc:mysql://127.0.0.1:3307 \
VSHOP_TEST_MYSQL_USER=vshop VSHOP_TEST_MYSQL_PASSWORD=vshop \
./gradlew test
```

CI runs both backends — see [.github/workflows/ci.yml](.github/workflows/ci.yml).

## Architecture

### Plugin bootstrap

[ModernVillagerShopPlugin.java](src/main/java/me/f0reach/vshop/ModernVillagerShopPlugin.java) is a hand-wired composition root. It instantiates every service in dependency order inside `onEnable()`, registers listeners, registers the `/vshop` Brigadier command via `LifecycleEvents.COMMANDS`, and exposes each collaborator through a public getter. There is no DI framework. When you add a service, add it here and expose the getter — other code reaches it through the plugin instance.

### Layered package layout (`me.f0reach.vshop.*`)

- `config` — `PluginConfig` wraps the YAML `FileConfiguration`. Treat it as immutable; `/vshop reload` builds a fresh instance.
- `locale` — `MessageManager` loads `lang/messages_<locale>.yml`, parses MiniMessage, and is the only place that emits player-facing text.
- `storage` — `StorageManager` owns the `DataSourceProvider` (Hikari) and exposes one repository per concern (`shops()`, `slots()`, `inventory()`, `transactions()`, `notifications()`, `limits()`, `coOwners()`, `playerCache()`, `playerPreferences()`). Repository implementations live under `storage/sqlite` and `storage/mysql`; the SQL-agnostic schema bootstrap is in `storage/repo/SchemaInitializer`. Cross-backend data movement is in `storage/migrate/MigrationService` (invoked by `/vshop migrate`).
- `economy` — `EconomyService` is the only caller of Vault. Fee/share math is centralized here; never call `Economy` directly elsewhere.
- `shop` — domain. `ShopRegistry` is the in-memory authoritative map of `UUID -> Shop`. `ShopService` is the lifecycle coordinator (create/load/delete, persistence + registry + villager state in lockstep). `ShopVillagerManager` handles the live entity (AI lock, invulnerability, respawn on chunk load, custom name regen). Subpackages mirror flows: `trade` (purchase/sell), `edit` (slot/menu editing), `coowner` (PRIMARY/MANAGER/STAFF), `egg` (spawn-egg crafting), `listener` (Bukkit events that fan into the services), `cache` (player-head/online cache).
- `ui` — `ui/dialog` is the BedrockDialog adapter (`DialogService`); `ui/chest` builds the inventory-based browse/edit/restock/player-picker UIs; `ui/text` renders chat output (history, search, list).
- `command` — `VShopCommand` builds the Brigadier tree and delegates per-subcommand classes in `command/sub`.
- `integration` — `MvshopPlaceholders` is an optional PAPI expansion, registered only when both the plugin is present and `placeholderapi.enabled` is true.
- `api` — public surface registered to `ServicesManager` (`ModernVillagerShopAPI`); `api/price/PriceRegistry` is the extension point external plugins use to influence prices (read via `shop.trade.PriceResolver`).
- `item`, `model` — data carriers (item snapshots, enums, value objects).

### Cross-cutting invariants

- Persistence is repository-per-table. There is no ORM; each repository is hand-written SQL with a SQLite and a MySQL implementation. Schema differences are kept inside the backend-specific class, not branched in shared code.
- The Villager is the source of truth for "is there a shop here," but its position/customName are derived state regenerated from DB on load. Never mutate the entity directly — go through `ShopVillagerManager` or `ShopService`.
- Co-owner role (`PRIMARY` / `MANAGER` / `STAFF`) gates *what an owner can do in their own shop*; the `modernvillagershop.*` permissions in [paper-plugin.yml](src/main/resources/paper-plugin.yml) gate *whether the command/feature is available at all*. `*.others` permissions bypass role for moderation.
- BedrockDialog callbacks may fire off the main thread. Anything that touches Bukkit API must be wrapped in `Bukkit.getScheduler().runTask(plugin, ...)` — see existing flows in `shop/trade/TradeFlow` and `shop/edit/SlotEditFlow` for the pattern.
- BedrockDialog only ships `ConfirmDialog` / `NoticeDialog` / `MultiButtonDialog` / `InputDialog` and has no `onClose` on Bedrock — design flows around explicit cancel buttons, not close detection. Sliders are banned for amount/price (use `InputDialog`).
- Localization: every user-visible string lives in `lang/messages_*.yml` and both `messages_en.yml` and `messages_ja.yml` must stay in sync when keys are added.

## Testing notes

- Stack: JUnit 5 + MockBukkit (`mockbukkit-v1.21`).
- Repository tests follow a **contract + two thin subclasses** pattern. The SQL-agnostic checks live in `me.f0reach.vshop.storage.<Repo>Contract` (or directly under `storage/repo` for shared SQL pieces); each backend has `Sqlite<Repo>Test` / `Mysql<Repo>Test` that picks the data source via `testsupport/AbstractRepositoryContract`. Add new repository tests by extending the contract on both sides — don't write backend-only tests unless the behavior is backend-specific.
- Tests using Bukkit types should extend or use `testsupport/BukkitTestSupport` to manage MockBukkit lifecycle.
- Test naming: `<ClassName>Test`; method names describe behavior (`createsShopWhenVillagerIsValid`).

## Style

- Java 21, UTF-8, 4-space indent, no tabs.
- Packages lowercase under `me.f0reach.vshop.*`; classes `PascalCase`; methods/fields `camelCase`; constants `UPPER_SNAKE_CASE`.
- Prefer small classes split by domain. New storage logic goes into the matching repository — do not add SQL to services.

## Reference docs

The repo bundles vendored Markdown references that future Claude instances should consult before touching the corresponding subsystem (they reflect API decisions newer than common training data):

- [spec.md](spec.md) — authoritative v1 spec. Read before any behavior change.
- [dialog.md](dialog.md) — PaperMC Dialog API + BedrockDialog wrapper notes.
- [adventure.md](adventure.md) — Adventure/MiniMessage usage patterns.
- [modern-commands.md](modern-commands.md) — Paper Brigadier command API.
