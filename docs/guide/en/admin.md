# Admin Guide

*English | [日本語](../admin.md)*

A guide for server operators and OPs. It covers installation, configuration, permission management, creating admin shops, auditing and migration. For player-facing operations, see the [Player Guide (Basics)](user-basic.md) and the [Player Guide (Advanced)](user-advanced.md).

## 1. Installation and prerequisites

### 1.1 Requirements

- **Paper 1.21.8 or later** (development is verified on 1.21.11)
- **Java 21**
- **Vault**: required. A Vault-compatible economy plugin (e.g. EssentialsX Economy) is needed separately.
- **BedrockDialog**: required. A Paper plugin distributed on Modrinth. Add Geyser + Floodgate as well if you want Bedrock support.
- **PlaceholderAPI**: optional. Install it to use the placeholders.

### 1.2 Installing

1. Put `ModernVillagerShop-*.jar` into `plugins/`.
2. Put Vault and BedrockDialog there too (and PlaceholderAPI if you want it).
3. Start the server; `config.yml`, `lang/messages_ja.yml` and `lang/messages_en.yml` are extracted under `plugins/ModernVillagerShop/`.
4. Confirm a Vault-compatible economy plugin is up and running.

## 2. Configuration (`config.yml`)

The main options are excerpted below. See `src/main/resources/config.yml` for the full defaults.

### 2.1 Language

```yaml
locale: ja_JP           # default locale
fallbackLocale: en_US   # fallback for missing keys
```

Language files live at `plugins/ModernVillagerShop/lang/messages_<locale>.yml`. Keys must be present in both languages.

### 2.2 Storage

```yaml
storage:
  type: sqlite            # sqlite | mysql
  sqlite:
    file: shops.db        # relative to the plugin data folder
  mysql:
    host: localhost
    port: 3306
    database: vshop
    username: root
    password: ""
    properties: "useUnicode=true&characterEncoding=utf8&useSSL=false"
    poolSize: 8
```

- SQLite guarantees consistency by serializing writers. Convenient, but limited in scalability.
- MySQL is designed around row locks (`SELECT ... FOR UPDATE`) and READ COMMITTED or stricter. Suited to multi-server and larger deployments.
- To switch backends later, see [8. Migration](#8-migration).

### 2.3 Economy and fees

```yaml
economy:
  feeRate: 0.05           # fee rate for player shop SELL
  feeRateAdmin: 0.05      # fee rate for admin shops
  priceMin: 1
  priceMax: 1000000
  amountMax: 2304
  fractionDigits: 2       # decimal digits
  roundingMode: HALF_UP
  priceDriftTolerance: 0.01  # tolerated drift after the price is frozen
  currencyFormat: "<amount> <currency>"
  priceProvider:
    enabled: true         # master on/off for the dynamic price SPI
```

- Collected fees are removed from the server economy (they are not paid into any account).
- Setting `priceProvider.enabled: false` makes admin shops fall back to static unit prices too. Use it to isolate problems or when a pricing extension is down.

### 2.4 Shops

```yaml
shop:
  maxShopsPerPlayer: -1     # -1 for unlimited
  openDistance: 6.0         # right-click open distance
  minDistance: 0.5          # reject placement this close to an existing shop
  defaultLimitScope: PER_PLAYER
  villagerNameFormat: "<shop_name> <gray>[<primary>]</gray>"
  villagerNameFormatAdmin: "<shop_name>"
  closeWithInventory: REFUSE  # DISCARD | DROP | REFUSE
```

- `maxShopsPerPlayer` counts only shops held with the **PRIMARY** role. Shops you merely co-own are not counted.
- `closeWithInventory` behavior:
    - `DISCARD`: discard the stock and delete
    - `DROP`: drop the stock at the shop location and delete
    - `REFUSE`: refuse deletion while stock remains (default, safest)

### 2.5 Player cache

```yaml
playerCache:
  maxEntries: 5000            # entries beyond this are evicted by last_seen ascending
  defaultSort: LAST_SEEN_DESC # LAST_SEEN_DESC | NAME_ASC
  textureTtl: 7d              # skin re-fetch TTL
```

This cache backs the player-picker UI (adding co-owners, choosing a PRIMARY transfer target, `--player` arguments, and so on). It is upserted on login, on logout, and when co-owners are looked up.

### 2.6 Forbidden items

```yaml
items:
  blacklist:
    - SHULKER_BOX
    - WHITE_SHULKER_BOX
    # ... every shulker color, BUNDLE, etc.
```

- Specified by Bukkit Material name.
- Shulker boxes and bundles are included by default. Treat items that can hold other items carefully — they open unintended duplication and extraction paths.
- The plugin enforces no built-in blacklist of its own. Add and remove entries to match your policy.

### 2.7 UI icons

`ui.chest.icons.*` lets you override the material, display name, lore and custom model data of every navigation icon in the chest UI (next/prev page, close, filter, sort, back, empty slot, unavailable, unknown player head). Combine it with a resource pack to match your server's look.

## 3. Permission management

### 3.1 Permission bundles

`paper-plugin.yml` defines the following role-like groupings. Granting them through a permission plugin such as LuckPerms is convenient.

- **`modernvillagershop.player`** (default: `true`): the pack a regular player needs. Includes `use`, `egg`, `list`, `search`, `stats`, `history`, `open.nearby`, `edit.*`, `coowner.manage`, `coowner.transfer`.
- **`modernvillagershop.admin`** (default: `op`): the admin pack. Includes `admin.egg`, `admin.edit`, `admin.export`, `admin.import`, `edit.others`, `coowner.manage.others`, `coowner.transfer.others`, `history.others`, `open.any`, `migrate`, `reload`.

### 3.2 Individual permissions

The notable ones:

| Permission | Purpose |
| --- | --- |
| `modernvillagershop.use` | Open a shop UI (buy / deliver) |
| `modernvillagershop.egg` | Use a player shop spawn egg |
| `modernvillagershop.admin.egg` | Use an admin shop spawn egg |
| `modernvillagershop.admin.edit` | Edit admin shops |
| `modernvillagershop.edit.*` | The individual edit operations on your own shop (move / rename / profession / suspend / delete / delete.refund) |
| `modernvillagershop.edit.others` | Edit someone else's shop (ignores role) |
| `modernvillagershop.coowner.manage.others` | Manage co-owners of any shop |
| `modernvillagershop.coowner.transfer.others` | Force a PRIMARY transfer on any shop (e.g. for players who left) |
| `modernvillagershop.history.others` | View other players' and other shops' trade history |
| `modernvillagershop.open.nearby` / `open.any` / `open.<shopId>` | Distance conditions for opening a shop UI. `open.any` wins; a per-shopId node is also possible |
| `modernvillagershop.migrate` | Storage migration |
| `modernvillagershop.reload` | Configuration reload |

### 3.3 How "role" and "permission" relate

- **Role (PRIMARY / MANAGER / STAFF)** is set per shop and decides **what someone can do** in that shop.
- **Permissions (`modernvillagershop.*`)** decide **whether the feature is available at all**.
- Example: a player without `modernvillagershop.edit.rename` cannot rename their own shop even as PRIMARY.
- **`*.others`** nodes are admin overrides that ignore role and apply to any shop. Grant them only to staff.

## 4. Creating and running admin shops

An admin shop has no owner and infinite stock, which makes it useful as a public store or NPC exchange.

### 4.1 Creating one

1. As a user with the `modernvillagershop.admin.egg` permission:

    ```
    /vshop egg <target player> admin
    ```

    The `admin` egg type internally has `inf`-equivalent capacity (paged 45 slots at a time).
2. Hold the spawn egg and place it.
3. Register listing slots through the normal edit flow (`/vshop edit`, or `Edit items` after right-clicking the villager). Editing an admin shop requires `modernvillagershop.admin.edit`.

### 4.2 Characteristics

- **Infinite stock**: SELL is always available, and items delivered through BUY are absorbed (discarded) by the server.
- **No payee**: SELL revenue is handled by the system (nobody is paid).
- **No co-owners**: attempting it returns an `admin-shop` error.
- **Fees**: `economy.feeRateAdmin` applies, so you can tune it separately from player shops.
- **Dynamic pricing**: the only shop type whose prices the `PriceProvider` SPI can override (see [10. Dynamic price API](#10-dynamic-price-api-priceprovider)).

### 4.3 Bulk import / export of slots

Admin shop listing slots can be exported to and imported from YAML — useful for cloning an existing admin shop to another server, or editing a large number of slots externally.

Run these while looking at the target admin shop's villager **within 8 blocks**.

```
/vshop admin export <file name>
/vshop admin import <file name>
```

- Permissions: `modernvillagershop.admin.export` / `modernvillagershop.admin.import`
- Files are placed under `plugins/ModernVillagerShop/exports/`.
- **export**: if the file already exists it errors instead of overwriting. Use a different name or remove the existing file.
- **import**: deletes and replaces all existing slots. A backup is taken automatically beforehand and its path is reported in the message.

Practical uses: keep exported YAML under review in pull requests, or build it into a staging-to-production workflow.

## 5. Command reference (admin view)

| Command | Description | Key permission |
| --- | --- | --- |
| `/vshop help` | Show help | — |
| `/vshop list [page]` | List shops | `modernvillagershop.list` |
| `/vshop open <shopId>` | Open a shop UI | `open.nearby` / `open.any` / `open.<shopId>` |
| `/vshop search <item> [page]` | Search by item name | `modernvillagershop.search` |
| `/vshop stats <shopId>` | Show statistics | `modernvillagershop.stats` |
| `/vshop history [shopId] [page] [--flags]` | Trade history | `history` / `history.others` |
| `/vshop edit [shopId]` | Edit menu | `edit` / `edit.others` |
| `/vshop coowner <shopId>` | Co-owner management UI | `coowner.manage` / `.others` |
| `/vshop transfer <shopId> <player>` | Transfer PRIMARY | `coowner.transfer` / `.others` |
| `/vshop egg <player> <lines\|inf\|admin>` | Give a spawn egg | `egg` / `admin.egg` |
| `/vshop admin export <file>` | Export admin shop slots to YAML | `admin.export` |
| `/vshop admin import <file>` | Import admin shop slots from YAML | `admin.import` |
| `/vshop migrate <from> <to>` | Storage migration | `migrate` |
| `/vshop reload` | Reload config, language files and messages | `reload` |

`--from` / `--to` on `/vshop history` accept `YYYY-MM-DD` or `YYYY-MM-DDTHH:mm[:ss]`, interpreted in the server's default time zone.

## 6. Auditing and trade logs

- Trade history is persisted in the database. Users with `modernvillagershop.history.others` can inspect other players and other shops via `/vshop history <shopId>` or `--player <name>`.
- Every history record stores `basePrice` (the slot's static unit price), `finalPrice` (the price actually traded) and `resolvedBy` (the list of PriceProvider ids applied). Use these to validate dynamic pricing.
- Querying the database directly is more flexible than analyzing logs: `SELECT` from `shop_transactions` in `shops.db` (SQLite) or in your configured `database` (MySQL).

## 7. Reloading

```
/vshop reload
```

- Reloads `config.yml` and the language files.
- **Database connections are NOT rebuilt.** Changing storage settings requires a server restart.
- Handy for verifying language file edits at runtime.

## 8. Migration

Data can be migrated between SQLite and MySQL in either direction.

```
/vshop migrate <from> <to>
```

Example: `/vshop migrate sqlite mysql`

- Permission: `modernvillagershop.migrate`
- **Trading is paused during the migration.**
- Steps:
    1. Initialize the schema (target)
    2. Bulk-copy every table
    3. Consistency check
- On failure the target data is rolled back and the source is left untouched — the design errs on the safe side.

**Recommended flow**:

1. Announce maintenance and stop meaningful trading.
2. Back up `config.yml`'s `storage` section **before** switching it to the target configuration.
3. Verify the target MySQL connection details (and reflect them in `config.yml`).
4. Run `/vshop migrate <from> <to>`.
5. After the completion message, switch `storage.type` in `config.yml` and restart the server.
6. Once verified, discard the old data (e.g. `shops.db`).

## 9. PlaceholderAPI integration

With `placeholderapi.enabled: true` (the default), these are available:

- `%mvshop_shop_count_<player>%`: shops the player owns (PRIMARY only)
- `%mvshop_shop_name_<shopId>%`: shop name
- `%mvshop_shop_owner_<shopId>%`: owner name
- `%mvshop_total_sales_<player>%`: the player's cumulative sales
- `%mvshop_total_purchases_<player>%`: the player's cumulative purchases

Use them in scoreboards, chat prefixes, DeluxeMenus and so on.

## 10. Dynamic price API (PriceProvider)

An SPI is provided for other plugins to change **admin shop** prices dynamically (player shops are out of scope).

### 10.1 Design

- Pipeline-shaped: multiple providers are applied in ascending `order`. The static unit price is treated internally as `order = 0`.
- Each provider receives a `PriceContext` and the previous stage's `PriceResult`, and returns a price, a reason text and a cache TTL.
- `PriceResult#reason` is appended to the end of the slot lore in the chest UI (plain text on Bedrock).
- On a completed trade, `basePrice` / `finalPrice` / `resolvedBy` (the applied provider ids) are recorded in the history.

### 10.2 Trade consistency

- **PriceSnapshot**: the price is frozen the moment the purchase confirmation dialog opens and used until settlement.
- **Drift tolerance**: if the price re-resolved at settlement deviates beyond `economy.priceDriftTolerance`, the trade is canceled automatically.
- Rejection logic belongs in `ShopPreTransactionEvent`. Providers are responsible for pricing only (stopping a trade from a provider is discouraged).

### 10.3 Safety

- Providers are assumed to run synchronously. Do not include blocking I/O (a contract, not an enforcement).
- On exception the provider is skipped and the previous stage's result is used (the trade is not stopped). A warning is logged.
- Use `PriceResult#ttl` to declare the render cache lifetime; it suppresses repeated recalculation while the chest UI is drawn.
- Master kill switch: `economy.priceProvider.enabled: false`. Useful as a fail-safe during incidents.

See [spec.md §12.3](../../../spec.md) for the detailed interface.

## 11. Extension points (events / API)

Bukkit events are provided for other plugins:

- `ShopCreateEvent` / `ShopDeleteEvent`
- `ShopPreTransactionEvent` (cancellable, for rejecting trades)
- `ShopTransactionEvent` (after completion)
- `ShopSlotChangeEvent` (slot added / edited / removed)

The public API is retrieved through `ServicesManager`:

```java
ModernVillagerShopAPI api = Bukkit.getServicesManager()
        .load(ModernVillagerShopAPI.class);
```

Register a PriceProvider with `api.priceRegistry().register(plugin, provider)`. The API follows semantic versioning to maintain compatibility.

## 12. Troubleshooting

| Symptom | What to check |
| --- | --- |
| Disabled with an error on startup | Are Vault and BedrockDialog loaded? Java 21? Paper 1.21.8+? |
| Vault Economy not found | Is a Vault-compatible economy plugin installed alongside? Add EssentialsX Economy or similar. |
| A villager doesn't become a shop, or disappears | Is the chunk loaded? Shops are stored in the database and respawned by UUID check on chunk load (nothing happens while unloaded). |
| Inconsistencies after migrating to MySQL | Is the isolation level READ COMMITTED or stricter? Check that nothing disables `SELECT ... FOR UPDATE`. |
| Formatting disappears on Bedrock | By design. BedrockDialog flattens MiniMessage formatting to plain text, so write messages that don't depend on decoration. |
| Dialog `onClose` doesn't fire | Unsupported on Bedrock. This is why the current design relies on explicit cancel buttons. |
| Trade notifications don't arrive | Is notification turned off in the target player's `player_preferences`? The STAFF role is excluded by design. |
| `/vshop egg` rejects the admin type | Does the executor have `modernvillagershop.admin.egg`? |
| `/vshop admin export` reports "no-target-villager" | No shop villager within 8 blocks of your line of sight. Look straight at it and retry. |
| Old data still shows after `/vshop migrate` | You may not have switched `storage.type` in `config.yml` and restarted the server. |

## 13. Reference material

- [spec.md](../../../spec.md): the v1 specification (core design) *(Japanese)*
- [dialog.md](../../../dialog.md): Paper Dialog API + BedrockDialog notes
- [adventure.md](../../../adventure.md): Adventure / MiniMessage syntax
- [modern-commands.md](../../../modern-commands.md): Paper Brigadier command API
- [Player Guide (Basics)](user-basic.md) / [Player Guide (Advanced)](user-advanced.md)
