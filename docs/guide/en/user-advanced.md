# Player Guide (Advanced)

*English | [日本語](../user-advanced.md)*

This guide is for players who want to run their own shop, act as a co-owner, or use the commands beyond plain buying and delivering. For the basics of finding shops, buying and delivering, see the [Player Guide (Basics)](user-basic.md).

## 1. Creating your own shop

### 1.1 Getting a spawn egg

Player shops are created from a **dedicated spawn egg** handed out by an admin. The number of listing rows is embedded in the egg as a parameter.

Ask an admin to run the following (or run it yourself if you hold the permission):

```
/vshop egg <target player> <rows | inf>
```

- `<rows>`: an integer of `1` or more. The shop gets 9 slots × rows of listings.
    - Shops of 1–6 rows fit on a single page (no paging).
    - 7 rows or more are split into 5 content rows + a navigation row, paged 45 slots at a time.
- `inf`: an egg for a shop with an unlimited number of listing slots (also paged 45 at a time).

Requires the `modernvillagershop.egg` permission.

### 1.2 Placing the shop

1. Hold the spawn egg and right-click on top of a block.
2. A villager spawns and that spot becomes your shop.
3. Placement fails — and the egg is *not* consumed — if either of these applies:
    - An existing shop villager is too close (under 0.5 blocks by default)
    - You have reached your shop limit (`shop.maxShopsPerPlayer`)

After placement, the villager displays "shop name `[owner name]`" above its head by default.

### 1.3 Deleting the shop

- Deleting is done from `Delete shop` in the **edit menu** (see below). Killing the villager does not remove it.
- What happens when stock remains is decided by the server setting `shop.closeWithInventory`. The default is **REFUSE** (deletion is rejected while stock remains), so empty the stock first.
- On deletion, if you have the `modernvillagershop.edit.delete.refund` permission, an equivalent spawn egg drops at the villager's location.

## 2. Opening the edit menu

You manage your shop from the edit menu. Open it either way:

- Run `/vshop edit <shopId>`
- Look at your shop and run `/vshop edit` (with the argument omitted, the shop you are looking at is used)

While you are in edit mode, purchases and deliveries by other players are blocked. Close the menu when you're done.

The edit menu offers actions like these (some are hidden depending on your role and permissions):

- **Edit items**: add, change and remove listing slots (chest UI)
- **Preview**: open the customer-facing browse UI to check how it looks
- **Edit inventory**: move stock in and out of a player shop
- **Shop info / Shop settings / Ownership**: submenus
- **Trade notifications**: toggle your personal notifications on/off
- **Suspend / Resume**: switch the shop state
- **Delete shop**: the deletion flow

## 3. Editing listing slots

Clicking `Edit items` in the edit menu opens the **editor chest UI**, distinguished from the browse view by its title and slot decoration.

### 3.1 Creating a new listing slot

1. Click an empty slot while holding an item on your cursor. (The item is not consumed — only snapshotted for reference.)
2. The slot settings dialog opens. Configure:
    - **Side**: `SELL` / `BUY` / `BOTH`
    - **Sell unit price** / **Buy unit price**
    - **Items per pack** (e.g. set 16 and buyers can only buy in units of 16)
    - **Buy capacity**: the maximum you are willing to buy (`-1` for unlimited)
    - **Trade limit**: cap per time window (blank for unlimited)
    - **Limit scope**: `PER_PLAYER` (per player) / `GLOBAL` (server-wide total)
    - **Limit reset (seconds)**: how often the limit window rolls over (blank for no reset)
3. Press `Apply` to confirm. If you cancel or close the dialog, the snapshot is discarded and the slot stays empty.

### 3.2 Editing and deleting an existing slot

- Clicking an existing slot opens the same dialog so you can rewrite its values.
- The **Delete this slot** button in the dialog removes the slot (via a confirmation dialog).

### 3.3 Things to watch out for

- **Forbidden items** (whatever the admin listed under `items.blacklist` — shulker boxes and bundles by default) cannot be listed.
- **Item identity is judged strictly.** If you register an enchanted book in a BUY slot, renamed books or books with a different enchantment level will not be accepted.
- Unit prices must fall within `economy.priceMin`–`priceMax`, and items per pack must be at most `economy.amountMax`.

## 4. Managing stock (player shops only)

`Edit inventory` in the edit menu opens the stock chest UI. Admin shops have no stock (they are treated as having infinite stock).

- You can move items freely between the chest UI and your own inventory.
- Slot positions are stored as-is, so you can arrange it and use it as a storage box.
- If you leave a forbidden item inside and close, that item is returned to you.
- Changes are written to the database when you close the UI. As with edit mode, other players' purchases and deliveries are held while it's open.

Items delivered into your BUY slots pile up in this stock, so you can resell them through SELL slots.

## 5. Changing name, appearance, state and location

These live in the `Shop settings` submenu of the edit menu.

- **Rename**: the villager's name tag is regenerated automatically according to `shop.villagerNameFormat` (default: `<shop_name> [<primary>]`).
- **Change profession**: changes the villager's appearance (profession block).
- **Suspend / Resume**: while suspended, both buying and delivering are rejected. Useful when you'll be away for a while.
- **Move**: with the `modernvillagershop.edit.move` permission, you can relocate the shop.

If your server has FancyNpcs installed, the shop can look like an **NPC** instead of a villager. That one is driven by commands rather than the menu.

```
/vshop appearance <shopId> npc            # a player NPC wearing your own skin
/vshop appearance <shopId> npc Notch      # pick a specific skin
/vshop appearance <shopId> glow true gold # make it glow gold
/vshop appearance <shopId> equip MAINHAND # hand it the item you are holding
/vshop appearance <shopId> show           # check the current settings
/vshop appearance <shopId> villager       # back to a villager
```

`<shopId>` is the first 8 characters of the shop ID, and Tab completes it. There is also `type` (entity kind), `scale`, `skin`, `attribute` and `reset`.

While the shop renders as an NPC the profession button disappears, because professions only exist on villagers. Switching back with `villager` restores the profession you had.

## 6. Co-owners (player shops only)

Instead of running a shop alone, several players can run it together.

### 6.1 Roles

| Role | Summary of rights |
| --- | --- |
| **PRIMARY** | Exactly one per shop. Delete the shop, manage co-owners, transfer PRIMARY, plus everything MANAGER can do |
| **MANAGER** | Add / edit / remove listing slots, change prices and amounts, restock and withdraw, suspend / resume, rename, profession, move |
| **STAFF** | Restocking only. No revenue share, no editing |

### 6.2 Revenue sharing

- Each owner is assigned a `share` (a percentage with two decimals). The total is corrected automatically to 100.00 via the PRIMARY's share.
- SELL revenue is split by `share` and paid out after the fee is deducted.
- BUY payouts always come **from the PRIMARY's account**. If the PRIMARY's balance is short, the trade is rejected.
- STAFF is treated as `share = 0` and receives nothing.

### 6.3 Adding, editing and removing

Only the PRIMARY can do this (or someone with the server admin permission `modernvillagershop.coowner.manage.others`).

```
/vshop coowner <shopId>
```

You can also reach it via `Ownership → Co-owners` in the edit menu.

- **Add**: pick a target from the player-picker chest UI (online players first, with name search and sorting), then set role and share. Players who have never joined cannot be selected (only cached players are candidates).
- **Change role / share**: click the member in the list to edit.
- **Remove**: click the member in the list → `Delete`.

### 6.4 Transferring PRIMARY

```
/vshop transfer <shopId> <target player>
```

Or `Ownership → Transfer` in the edit menu. A confirmation dialog finalizes it.

- The previous PRIMARY is automatically demoted to MANAGER.
- The target can be an existing co-owner or a completely new player.
- The villager's display name is regenerated on transfer.
- It works even if the target is offline (they are notified on next login).

## 7. Notifications

Notifications for trades in your own shop are toggled with the `Trade notifications` button in the edit menu.

- **While online**: a chat notification per trade (e.g. `Your shop ... sold ...`).
- **While offline**: a single summary on next login — "While you were away, your shops had N trades (total ...)". Use `/vshop history` for the details.
- Notifications go to PRIMARY and MANAGER. STAFF does not receive them.
- Adding, removing, or changing the role of a co-owner and transferring PRIMARY also notify the affected player (queued while offline).

## 8. Making use of trade history

The basic form is `/vshop history`, and you can narrow it down with filters.

```
/vshop history [shopId] [page] [--side sell|buy] [--from <date>] [--to <date>] [--player <name>] [--page <n>]
```

- **shopId**: prefix match is fine (just the first few characters of the UUID work). Omit it to cover every trade you were involved in.
- **--side sell** / **--side buy**: filter by side.
- **--from / --to**: range as `YYYY-MM-DD` or `YYYY-MM-DDTHH:mm[:ss]`, interpreted in the server's time zone.
- **--player**: filter by counterparty name (prefix match).
- **--page**: page number explicitly (the second positional argument works too).

Examples:

```
/vshop history                         # all of your history
/vshop history 3a2f                    # a specific shop (first 4 chars of the UUID)
/vshop history --side sell --from 2026-07-01
/vshop history --player Steve --page 2
```

## 9. Viewing statistics

```
/vshop stats <shopId>
```

- Number of listing slots / active slots
- Cumulative trade count and value for SELL and BUY
- Cumulative fees
- Trade counts over the last 7 days
- Top 5 most popular items

Every co-owner can view this.

## 10. Reading dynamic prices (PriceProvider)

In admin shops, an extension plugin may be setting prices dynamically. (Player shop prices are always static.)

- The lore of a slot may show a **reason text** (e.g. `On sale`). That message comes from the provider that overrode the price.
- The price is **frozen at the moment you open the purchase confirmation dialog** (shown as `(price frozen at <time>)`).
- If the price has drifted beyond the tolerance by the time you confirm, the trade is canceled automatically. Reopen the shop and try again.

## 11. Troubleshooting (your own shop)

| Symptom | What to do |
| --- | --- |
| **`/vshop egg` can't find the target player** | Check they're online and the spelling is right. Eggs cannot be issued to offline players. |
| **"Cannot place: a shop is too close"** | Move a bit further from the existing shop villager and place again. |
| **"Cannot close: stock remains" when deleting** | Empty the stock via `Edit inventory` first. The exact behavior depends on `shop.closeWithInventory`. |
| **The player I want as co-owner isn't in the picker UI** | They may never have joined the server. Only players already in `player_cache` are offered as candidates. |
| **No notifications arrive** | Check that `Trade notifications` in the edit menu isn't OFF. The STAFF role never receives notifications by design. |
| **BUY says "the shop owner does not have enough funds"** | If you are the PRIMARY, top up your balance. Even with co-owners, payouts always come from the PRIMARY's account. |
| **The villager moved (teleport, etc.)** | AI is disabled, but nether portals and forced teleports can come from elsewhere. The coordinates are stored in the database, so it may return to its original position on chunk reload. |

---

For server-side configuration, permissions and running admin shops, see the [Admin Guide](admin.md).
