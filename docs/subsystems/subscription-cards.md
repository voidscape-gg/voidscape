# Subscription Cards

Voidscape subscription is an account-level timed XP boost unlocked by redeeming a subscription card. Each card adds 7 days of account subscription time.

## Rates

- Normal Voidscape target rates are 7x combat XP and 4x skilling XP.
- Subscribed accounts use at least 10x combat XP and at least 6x skilling XP.
- Combat skills follow the same grouping as the existing server XP multiplier: Attack, Defense, Strength, Hits, Ranged, Prayer, Good Magic, Evil Magic, and Magic.
- `Player.getExperienceMultiplier(...)` applies the subscription floor before Wilderness and skull additive bonuses.

## Card Item

- Item id `1602`, `Subscription card`.
- Right-click command: `Redeem`.
- Client sprite id `617`, packed into `Client_Base/Cache/video/Authentic_Sprites.orsc` at archive entry `2767`.
- Redeeming consumes the clicked card and stores/extends the `void_sub_expires` timestamp in the player's cache.
- If the account is already subscribed, redeeming another card extends the expiry by 7 days from the current expiry.
- The old `void_subscription` boolean cache flag is treated as legacy state and removed when subscription status is evaluated.

## Lumbridge Vendor

`server/plugins/com/openrsc/server/plugins/custom/npcs/VoidSubscriptionVendor.java` owns the vendor flow.

- NPC id `848`, `Void Subscription Vendor`.
- Spawned near Lumbridge respawn by `NpcLocsVoidSubscriptions.json`.
- Right-click command: `Subscribe`.
- Talking to the vendor or right-clicking `Subscribe` opens the native shop window directly.
- The vendor shares the Void Auctioneer's shadow-broker appearance rather than the robe pieces used by the Void acolyte/herald NPCs.
- The vendor starts with 20 cards at 10,000 coins each.
- When a stock tier sells out, the next tier restocks to 20 cards and doubles the price.
- Purchases use a custom shop buy handler so stock and tier are read/saved under one lock instead of relying on runtime-only shop stock.
- Selling subscription cards back into the vendor is blocked.

Vendor stock and tier are persisted through global rows in the existing `player_cache` table:

- `playerID = 0`, `key = sub_vendor_stock`
- `playerID = 0`, `key = sub_vendor_tier`

This avoids a schema migration. The code deletes and reinserts each global cache key because `player_cache` has no uniqueness constraint on `(playerID, key)`.

## Client Definitions

The card and vendor are client-visible definitions, and the shop uses a custom-client exact price override so tier prices can display correctly after they exceed normal shop modifier limits.

- `Client_Base/src/orsc/Config.java` `CLIENT_VERSION = 10055`
- Server presets with the custom client use `client_version: 10055`
- Client item and NPC rows are appended in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- Custom clients `10054+` read a 32-bit per-item shop price override after each shop item row in `SEND_SHOP_OPEN`; older clients keep the old shop packet shape.
- Custom clients `10055+` read subscription status and effective combat/skilling XP rates from `SEND_GAME_SETTINGS` for the wrench Profile panel.
