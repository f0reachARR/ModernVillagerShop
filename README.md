# ModernVillagerShop

[![Modrinth](https://img.shields.io/modrinth/dt/modernvillagershop?logo=modrinth&label=Modrinth&color=00AF5C)](https://modrinth.com/plugin/modernvillagershop)
[![GitHub release](https://img.shields.io/github/v/release/f0reachARR/ModernVillagerShop?logo=github&label=Release)](https://github.com/f0reachARR/ModernVillagerShop/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://github.com/f0reachARR/ModernVillagerShop/blob/main/LICENSE)

Turn Villagers into real shops — player-owned or admin-run — with selling, buying (order slots), co-ownership and revenue sharing, on Paper 1.21.8+.

ModernVillagerShop replaces the vanilla trade window with a **chest UI + Dialog** hybrid: a chest inventory to browse and manage listings, and native Paper Dialogs for every amount, price and confirmation prompt. Because dialogs are driven through [BedrockDialog](https://modrinth.com/plugin/bedrockdialog), Bedrock players (Geyser + Floodgate) get the same flows rendered as Bedrock forms.

## Features

- **SELL and BUY in one shop.** Each listing slot is a sell offer, a buy order (the shop pays players for delivering items), or both at once.
- **Player shops and admin shops.** Player shops keep a real, persisted stock; admin shops have infinite stock, no owner, and can run commands instead of handing over items.
- **Co-owners with revenue sharing.** `PRIMARY` / `MANAGER` / `STAFF` roles per shop, percentage shares that always add up to 100%, instant payout split on every sale, and an ownership-transfer flow.
- **Trade limits.** Per-slot caps, counted per player or server-wide, with an optional rolling reset window. Remaining amount and time-to-reset are shown in the slot lore.
- **Villagers that stay put.** Shop villagers are AI-locked, invulnerable, protected from portals, and respawned from the database on chunk load if something removes them.
- **Custom shop appearance.** With [FancyNpcs](https://modrinth.com/plugin/fancynpcs) installed, a shop can render as an NPC instead of a villager — a player skin, any entity type, glow colour, scale and equipment — all through `/vshop appearance`, no UI to click through.
- **Spawn-egg based creation.** `/vshop egg` hands out an egg that encodes how many listing rows the shop gets — 1 to *n* rows, or unlimited.
- **Full trade history and statistics.** Filterable history (`--side`, `--from`, `--to`, `--player`, `--shop`), per-shop stats, cumulative fees, and audit fields (`basePrice` / `finalPrice` / `resolvedBy`) on every record.
- **Owner notifications.** Chat notification on each trade while online, a summary on next login while offline, toggleable per player.
- **SQLite or MySQL.** Same schema on both, with an in-game `/vshop migrate` to move data between backends.
- **Fully localizable.** Every player-facing string lives in `lang/messages_<locale>.yml` (English and Japanese ship in the box), parsed as MiniMessage.
- **Extensible.** Bukkit events, a public API facade, PlaceholderAPI placeholders, and a dynamic-price SPI for admin shops.

## Requirements

| | |
| --- | --- |
| Server | Paper **1.21.8+** (developed against 1.21.11) |
| Java | **21** |
| [Vault](https://www.spigotmc.org/resources/vault.34315/) | **required** — plus any Vault-compatible economy plugin (e.g. EssentialsX Economy) |
| [BedrockDialog](https://modrinth.com/plugin/bedrockdialog) | **required** |
| Geyser + Floodgate | optional — needed only to serve Bedrock clients |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | optional |
| [FancyNpcs](https://modrinth.com/plugin/fancynpcs) | optional — lets shops render as NPCs instead of villagers |

## Installation

1. Download `ModernVillagerShop-*.jar` from [Modrinth](https://modrinth.com/plugin/modernvillagershop) (or the [GitHub releases](https://github.com/f0reachARR/ModernVillagerShop/releases/latest)) and drop it, together with `Vault` and `BedrockDialog`, into `plugins/`.
2. Make sure a Vault-compatible economy plugin is installed and running.
3. Start the server. `plugins/ModernVillagerShop/config.yml` and `lang/messages_{en,ja}.yml` are generated on first boot.
4. Set `locale` in `config.yml` (`ja_JP` by default, `en_US` also ships) and run `/vshop reload`.

## Quick start

**Give a player a shop egg** — the number is how many 9-slot rows of listings the shop gets:

```
/vshop egg Steve 3        # a 27-slot shop
/vshop egg Steve inf      # unlimited listings, paged
/vshop egg Steve admin    # admin shop egg (needs modernvillagershop.admin.egg)
```

**Place it.** Right-click a block with the egg — a villager spawns and becomes the shop.

**Add listings.** `/vshop edit` while looking at the villager (or `/vshop edit <shopId>`) → *Edit listings* → put an item from your inventory into an empty slot. The item is only snapshotted, never consumed. A dialog then asks for side (SELL / BUY / BOTH), unit price, pack size, and optional trade limits.

**Stock it.** *Manage stock* in the edit menu opens a chest UI; move items in and out freely, changes are persisted on close. (Admin shops skip this — their stock is infinite.)

**Trade.** Any player right-clicks the villager, clicks a slot, enters a number of packs, and confirms. Money moves immediately: the buyer is charged, the fee is burned, and the remainder is split across co-owners by share — even for offline owners.

## Commands

Root command is `/vshop` (bare `/vshop` prints help).

| Command | Description | Permission |
| --- | --- | --- |
| `/vshop help` | Command list | — |
| `/vshop list [page]` | List shops | `modernvillagershop.list` |
| `/vshop open <shopId>` | Open a shop UI | `open.nearby` / `open.any` / `open.<shopId>` |
| `/vshop search <item> [page]` | Search listings by item name or ID | `modernvillagershop.search` |
| `/vshop stats <shopId>` | Shop statistics | `modernvillagershop.stats` |
| `/vshop history [page] [--shop <id>] [--side sell\|buy] [--from <date>] [--to <date>] [--player <name>]` | Trade history | `history` / `history.others` |
| `/vshop edit [shopId]` | Open the owner/editor menu | `edit` / `edit.others` |
| `/vshop appearance <shopId> <sub>` | Change how the shop looks (needs FancyNpcs) | `edit.appearance` / `edit.others` |
| `/vshop coowner <shopId>` | Co-owner management UI | `coowner.manage` / `.others` |
| `/vshop transfer <shopId> <player>` | Transfer `PRIMARY` ownership | `coowner.transfer` / `.others` |
| `/vshop egg <player> <lines\|inf\|admin>` | Give a shop spawn egg | `egg` / `admin.egg` |
| `/vshop admin export <file>` | Export the admin shop you are looking at to YAML | `admin.export` |
| `/vshop admin import <file>` | Replace an admin shop's listings from YAML (auto-backup first) | `admin.import` |
| `/vshop migrate <from> <to>` | Migrate storage between backends | `migrate` |
| `/vshop reload` | Reload config, language files and message cache | `reload` |

`--from` / `--to` accept `YYYY-MM-DD` or `YYYY-MM-DDTHH:mm[:ss]`, interpreted in the server's default time zone.

### `/vshop appearance`

Command-only, by design — this is an occasional operation with a lot of knobs, which reads better flat than as a menu tree.

| Subcommand | Effect |
| --- | --- |
| `show` | Print the current settings (read-only, so the console can run it too) |
| `npc [skin]` | Switch to a PLAYER NPC. Without `skin`, uses the owner's name |
| `villager` | Switch back to a plain villager |
| `type <entityType>` | Change the NPC's entity type |
| `skin <name\|uuid\|url\|@none> [slim]` | Set or clear the skin (PLAYER type only) |
| `glow <true\|false> [color]` | Glow and glow colour |
| `scale <n>` | Size multiplier |
| `equip <slot> [none]` | Equip the item in your hand, or clear the slot |
| `attribute <name> <value\|@none>` | Set a FancyNpcs attribute (e.g. `pose sitting`) |
| `reset` | Drop every override and go back to a villager |

Equipment slots are FancyNpcs': `MAINHAND`, `OFFHAND`, `HEAD`, `CHEST`, `LEGS`, `FEET`, `BODY`, `SADDLE`.

Appearance is stored in this plugin's own database, so it travels with `/vshop migrate` and NPCs are rebuilt from it on every boot. If FancyNpcs is missing or `fancynpcs.enabled` is false, NPC-backed shops fall back to villagers with a single warning — cosmetics never take a shop offline.

## Permissions

All nodes are prefixed `modernvillagershop.`. Two convenience bundles exist:

- **`modernvillagershop.player`** (default: everyone) — `use`, `egg`, `list`, `search`, `stats`, `history`, `open.nearby`, and the `edit.*` / `coowner.*` nodes needed to run your own shop.
- **`modernvillagershop.admin`** (default: op) — `admin.egg`, `admin.edit`, `admin.export`, `admin.import`, `admin.appearance`, `edit.others`, `coowner.manage.others`, `coowner.transfer.others`, `history.others`, `open.any`, `migrate`, `reload`.

Two independent layers decide what a player can do:

- **Permissions** decide whether a command or feature is available at all.
- **Co-owner role** (`PRIMARY` / `MANAGER` / `STAFF`) decides what someone may do *inside a shop they belong to*. `STAFF` can only restock; `MANAGER` can edit listings, prices, stock, name, profession and suspend state; `PRIMARY` additionally deletes the shop and manages co-owners.

The `*.others` nodes are moderation overrides — they ignore role entirely and apply to any shop.

Two appearance nodes sit outside the bundles' defaults: `edit.appearance.url` (op) gates loading a skin from an arbitrary URL, since that pulls a remote image through the server, and `admin.appearance` (op) bypasses the `fancynpcs.allowedTypes` and `fancynpcs.maxScale` limits.

## Configuration highlights

Full annotated defaults live in [`src/main/resources/config.yml`](https://github.com/f0reachARR/ModernVillagerShop/blob/main/src/main/resources/config.yml).

```yaml
locale: ja_JP             # ja_JP | en_US (fallbackLocale covers missing keys)

storage:
  type: sqlite            # sqlite | mysql

economy:
  feeRate: 0.05           # trade fee, burned (not paid to any account)
  feeRateAdmin: 0.05      # separate rate for admin shops
  priceMin: 1
  priceMax: 1000000
  amountMax: 2304         # max items per single trade
  fractionDigits: 2       # currency precision; math is BigDecimal internally
  roundingMode: HALF_UP

shop:
  maxShopsPerPlayer: -1   # -1 = unlimited; counts PRIMARY roles only
  openDistance: 6.0       # how close a player must be to open a shop
  minDistance: 0.5        # minimum spacing between shop villagers
  closeWithInventory: REFUSE   # DISCARD | DROP | REFUSE
  villagerNameFormat: "<shop_name> <gray>[<primary>]</gray>"
  villagerLook:
    enabled: true         # villagers turn toward nearby players
    radius: 6.0

items:
  blacklist:              # container-like items are blacklisted by default
    - SHULKER_BOX
    - BUNDLE
```

Also configurable: per-event UI sounds (`sounds.*`), every chest-UI navigation icon (`ui.chest.icons.*`, material / name / lore / custom model data), and the player cache used by the player-picker UI.

> **Note on blacklists:** item identity uses `ItemStack#isSimilar`, so enchantments and item metadata must match — but items that *contain* other items (shulker boxes, bundles) should stay blacklisted. The defaults already cover them.

## Localization

Language files are `plugins/ModernVillagerShop/lang/messages_<locale>.yml`, written in [MiniMessage](https://docs.advntr.dev/minimessage/format.html). `messages_en.yml` and `messages_ja.yml` ship with the plugin and are kept key-for-key in sync; copy one to add a locale. `fallbackLocale` fills in anything missing. `/vshop reload` picks up edits without a restart.

## Storage and migration

SQLite (default, `shops.db` in the plugin folder) and MySQL are both first-class — the schema is created and kept in sync automatically on either backend.

To move an existing server from SQLite to MySQL: fill in `storage.mysql`, then run

```
/vshop migrate sqlite mysql
```

which copies shops, listings, stock, transactions, notifications, limits, co-owners and caches. Switch `storage.type` afterwards and restart.

## Integrations

**PlaceholderAPI** (`placeholderapi.enabled: true`):

| Placeholder | Value |
| --- | --- |
| `%mvshop_shop_count_<player>%` | Shops owned (PRIMARY only) |
| `%mvshop_shop_name_<shopId>%` | Shop name |
| `%mvshop_shop_owner_<shopId>%` | Owner name |
| `%mvshop_total_sales_<player>%` | Cumulative sales |
| `%mvshop_total_purchases_<player>%` | Cumulative purchases |

**FancyNpcs** (`fancynpcs.enabled: true`) — renders shops as NPCs; see [`/vshop appearance`](#vshop-appearance). Built against the `de.oliver:FancyNpcs` 2.x API. FancyNpcs 2.10.0+ requires Java 25 on the server; if you are on Java 21, use the `-java21` builds FancyNpcs publishes.

**Events** — `ShopCreateEvent`, `ShopDeleteEvent`, `ShopPreTransactionEvent` (cancellable), `ShopTransactionEvent`, `ShopSlotChangeEvent`.

**Public API** — `ModernVillagerShopAPI` is registered with Bukkit's `ServicesManager` and exposes shop lookup, search, statistics and history for dashboards, logging and third-party integrations.

**Dynamic price SPI** — external plugins can register a `PriceProvider` through `ModernVillagerShopAPI.priceRegistry()` to compute **admin shop** prices at display and trade time. Providers are chained by `order`, may attach a display `reason` shown in the slot lore, and specify a cache TTL. Prices are frozen when the confirmation dialog opens and re-checked at settlement — a drift beyond `economy.priceDriftTolerance` cancels the trade instead of charging an unexpected amount. `economy.priceProvider.enabled: false` disables all providers and falls back to static prices.

## Documentation

- [Admin guide](https://github.com/f0reachARR/ModernVillagerShop/blob/main/docs/guide/en/admin.md) — setup, config, permissions, admin shops, auditing, migration
- [Player guide — basics](https://github.com/f0reachARR/ModernVillagerShop/blob/main/docs/guide/en/user-basic.md) — using other people's shops
- [Player guide — advanced](https://github.com/f0reachARR/ModernVillagerShop/blob/main/docs/guide/en/user-advanced.md) — running your own shop, co-owners, history
- [Specification](https://github.com/f0reachARR/ModernVillagerShop/blob/main/spec.md) — authoritative behaviour spec *(Japanese)*

Japanese versions of all three guides are in [docs/guide/](https://github.com/f0reachARR/ModernVillagerShop/tree/main/docs/guide).

## Building from source

```bash
./gradlew build        # compile + tests + shaded jar in build/libs/
./gradlew runServer    # boot a local Paper test server
```

Java 21 is required. HikariCP is shaded and relocated; Paper, Vault, BedrockDialog and PlaceholderAPI are `compileOnly`.

## License

[MIT](https://github.com/f0reachARR/ModernVillagerShop/blob/main/LICENSE).

## Issues and contributions

Bug reports and feature requests: [GitHub Issues](https://github.com/f0reachARR/ModernVillagerShop/issues). Contribution guidelines are in [AGENTS.md](https://github.com/f0reachARR/ModernVillagerShop/blob/main/AGENTS.md).
