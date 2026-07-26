# Player Guide (Basics)

*English | [日本語](../user-basic.md)*

A guide for regular players. It covers using shops that other people run. For owning and editing your own shop, see the [Player Guide (Advanced)](user-advanced.md).

## 1. What a shop is

- Shops exist in the world as Villagers.
- Each shop has multiple **listing slots**, and each slot is set to one of the following:
    - **SELL**: the owner sells an item and you buy it
    - **BUY**: the owner is buying an item — you deliver it and get paid
    - **BOTH**: the same item is sold and bought in a single slot
- A shop is either *open* or *suspended*. While suspended, neither buying nor delivering is possible.

## 2. Opening a shop

### Right-click the villager

- Right-click a shop villager from nearby and the chest UI opens on the spot.
- If it doesn't open, one of these applies:
    - You are too far away (default `shop.openDistance` = within 6 blocks)
    - The shop is suspended
    - The server admin has not granted you the `modernvillagershop.use` permission

### Open by command

`/vshop open <shopId>` opens a shop by ID. A `shopId` matches on the first few characters of the UUID. Whether you can bypass the distance check depends on your permissions (`modernvillagershop.open.nearby` is the default; `modernvillagershop.open.any` ignores distance).

## 3. Finding shops

### Browse the list

```
/vshop list [page]
```

Lists the shops on the server. Omit the page number to see page 1.

### Search by item

```
/vshop search <item name or ID> [page]
```

- Examples: `/vshop search diamond`, `/vshop search minecraft:iron_ingot`
- Performs a partial match against the names of items being sold or bought. Matching shops are listed with their ID, unit price and side (SELL / BUY).
- Open a shop you found with `/vshop open <shopId>`, or walk there and right-click the villager.

## 4. Buying (SELL slots)

1. Opening a shop shows the chest UI with a sample item for each listing slot.
2. Click the slot you want. (For a BOTH slot, a picker appears — choose `Buy from shop`.)
3. An **amount dialog** opens. The pack size is fixed by the shop, so you enter the number of **packs** you want.
    - Example: buying 3 packs from a slot where 1 pack = 16 items gives you 48 items.
4. A **purchase confirmation dialog** then shows the item, amount, unit price, total and fee. Press `Buy` if it looks right.
5. On success the money is deducted from your balance immediately and the items go into your inventory.

### When you can't buy

- **Out of stock**: the shop doesn't have that many items.
- **You don't have enough money**: your balance doesn't cover the total.
- **Trade limit reached**: this slot has a cap per time window. Waiting a while may clear it.
- **Price changed; transaction canceled**: if an admin shop uses dynamic pricing and the price moves too much after you opened the confirmation screen, the trade is canceled automatically. Just reopen the shop.
- **This slot is not currently tradable**: the owner is editing the shop, or the shop is suspended.

### If your inventory is full

Purchased items go straight into your inventory. Free up space beforehand — a purchase can fail if there isn't enough room.

## 5. Delivering (BUY slots)

A BUY slot is one where the owner buys items **from you**.

1. Open the shop and click a BUY slot (its lore shows a buy unit price).
2. For a BOTH slot, choose `Sell to shop`.
3. Enter the number of packs in the amount dialog.
4. Check the payout and fee in the delivery confirmation dialog, then press `Deliver`.
5. On success the items leave your inventory and you are paid immediately.

### When you can't deliver

- **You don't have enough items to deliver**: you don't hold the required number of the target item.
- **Buy slot is full**: the owner's buy capacity is exhausted — they won't take any more.
- **The shop owner does not have enough funds**: for player shops, the owner's balance doesn't cover the payout. Let them know. (Admin shops never show this.)
- **Trade limit reached**: the per-window delivery cap has been hit.

## 6. Viewing your trade history

Check your own history with:

```
/vshop history
/vshop history 2         # page 2
```

- Your recent purchases and deliveries are listed newest first.
- For finer filters (date range, counterparty, side), see the [Player Guide (Advanced)](user-advanced.md).

## 7. About item identity

Whether two items count as "the same item" is judged strictly, including name, enchantments and custom data (Bukkit's `ItemStack#isSimilar`). These are treated as different items:

- Tools with different enchantments, enchantment types, or levels
- Renamed vs. non-renamed items
- Decorative items with different custom model data

So an item that *looks* identical may still not be sellable or purchasable. The safest approach is to bring exactly the item shown in the shop UI.

## 8. FAQ

- **Hitting a shop villager doesn't kill it.** Shop villagers are invulnerable; only the owner or an admin can remove them. This is by design.
- **I don't get notifications.** Notifications are for shop owners and co-owners, not for customers. See the advanced guide for notifications on your own shop.
- **No colors on Bedrock.** On Bedrock, dialog formatting falls back to plain text by design. The display is plainer, but the flow is identical.

---

To continue with running your own shop, see the [Player Guide (Advanced)](user-advanced.md).
