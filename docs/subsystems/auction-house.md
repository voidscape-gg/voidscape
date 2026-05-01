# Auction House

Voidscape's auction house is a fixed-price player-to-player marketplace gated behind a single custom NPC at Edgeville bank. Built on top of upstream OpenRSC's existing `Market` service with voidscape adjustments. Not authentic — RSC never had one.

## Player flow

1. Walk to Edgeville bank south side, **(217, 460)**.
2. Right-click the **Void Auctioneer** (NPC id 837) → **"Auction"** (or talk-to + menu option).
3. Ironman accounts blocked; Bank PIN validated (if set).
4. AH UI opens (PC client only — Android excluded).
5. **Browse tab**: search by name + 10 category buttons + sortable list (Price Low/High, Name, Each Low/High). Select a listing, adjust qty with quick buttons, then two-click confirm the purchase.
6. **My Auctions tab**: pick item + amount + total price, post listing. Cancel any of your active listings.
7. Listings expire after **7 days** if unsold; expired stock returns to seller via the auctioneer's Collect path.
8. **5% sale tax** taken from seller's proceeds at point of sale (gp sink — destroyed).
9. **Cap**: 6 active listings per player.

## Architecture

**Service**: `server/src/.../content/market/Market.java` — single-threaded `ScheduledExecutor` runs every 50ms, drains task queues, refreshes the in-memory `auctionItems` cache from DB. Started/stopped from `World.java`.

**Tasks** (each in `content/market/task/`):
- `NewMarketItemTask` — list an item. Validates tradable, price > 0, amount > 0, total price divides evenly by quantity, inventory holds enough, and **queryPlayerAuctionCount < 6**. Removes items from inventory, inserts auction row, and saves the inventory inside one DB transaction; rolls the inventory back on failure.
- `BuyMarketItemTask` — purchase. Validates not own listing + has coins + has space. Adds the bought item, removes buyer coins, creates seller collectible credit, updates/sells out the listing, and saves the affected inventory/bank containers inside `database.atomically(...)`. 5% tax deducted from seller credit.
- `CancelMarketItemTask` — seller cancels; returns remaining stock directly to inventory if possible, otherwise bank, and marks the auction canceled in the same DB transaction. Staff use moderator delete instead of canceling into their own inventory.
- `OpenMarketTask` — re-renders UI; sends raw packet 132 with auction list chunks (200 items each).
- `PlayerCollectItemsTask` — pulls collectible (sold/expired) items into seller bank and marks claims collected in the same DB transaction.
- `ModeratorDeleteAuctionTask` — admin/mod listing nuke that returns the remaining stock to the seller's collectible queue in one DB transaction.

**Networking**: zero new opcodes. Uses existing `OpcodeIn.INTERFACE_OPTION` (sub-op 10 → AUCTION → sub-sub-op 0=BUY, 1=CREATE, 2=CANCEL, 3=REFRESH, 4=CLOSE, 5=DELETE) handled in `InterfaceOptionHandler.java`. Outbound list payload uses raw packet id 132 from `OpenMarketTask`; hours-left is a short so 7-day listings display correctly. Custom outbound `OpcodeOut.SEND_AUCTION_PROGRESS` exists but is currently unused for the marketplace flow.

**DB**: `auctions` + `expired_auctions` tables. Schema in `server/database/{mysql,sqlite}/patches/2026_04_26_add_auctionhouse.sql`. Auto-applied via `JDBCPatchApplier` on first boot. SQLite uses MySql query strings via inheritance (`SqliteGameDatabase extends MySqlGameDatabase`); no separate query class.

**NPC**:
- Custom id 837 "Void Auctioneer" defined in `server/conf/server/defs/NpcDefsCustom.json`
- Client-side NPCDef appended in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` after Edgar (must be present or client renders the "Ana (not in a barrel)" sentinel)
- Spawn loc in `server/conf/server/defs/locs/NpcLocsAuction.json` — single entry
- Plugin in `server/plugins/.../custom/npcs/VoidAuctioneer.java`
- `WorldPopulator.java:268` loads the spawn iff `spawn_auction_npcs: true` (not gated on `LOCATION_DATA == 2` in voidscape, unlike upstream)

**Client UI**: `Client_Base/src/com/openrsc/interfaces/misc/AuctionHouse.java` — 490×326 panel, two tabs (browse + my-listings), sort cycle, search field (case-insensitive substring on item name), 10 category buttons. Hardcodes outbound packet id 199 for AH operations.

## Key data shapes

**`auctions` row**:
- `auctionID` PK auto-increment (bigint mysql / integer sqlite)
- `itemID`, `amount` (initial qty), `amount_left`, `price` (total for `amount_left` items)
- `seller` (player id), `seller_username`
- `buyer_info` text (CSV-ish: `[unix_ts: username: x<qty>]`)
- `sold-out` (0/1), `was_cancel` (0/1)
- `time` (varchar of unix seconds at listing creation)

Per-unit price is computed at runtime as `price / amount_left`. Partial buys keep this consistent: a 10@1000gp listing sold 5 → row updates to `amount_left=5`, `price=500` → next buy still computes 100gp/each.

**`expired_auctions` row** — collectible queue: an item credit (or coin credit for sold proceeds) waiting for the player to collect via the auctioneer.

## Pitfalls / non-obvious

1. **Client NPCDef list is positional, not id-keyed.** Adding a server-side custom NPC requires a matching `npcs.add(...)` in `Client_Base/.../EntityHandler.java` at the right ordinal. Voidscape's pattern: append to `loadNPCDefinitions4()` after Edgar. Without it, the client falls back to "Ana (not in a barrel)" for the unknown id.
2. **Schema gate**: upstream put the schema in `database/{mysql,sqlite}/addons/` which is **not** auto-applied. Voidscape moved it to `patches/` so `JDBCPatchApplier` runs it on first boot.
3. **Per-unit price** is `total_price / amount_left` (re-derived each tick). Listing 10 items for 1000gp total = 100gp each, not "100gp per unit, total 1000gp". The UI shows both.
4. **`queryPlayerAuctionCount` parameterized-SQL bug** (upstream): `seller='?'` was a literal string, never bound. Voidscape fixed to real `?`. Fix is in `MySqlQueries.java:203`.
5. **Auction mutations now save affected containers synchronously.** The market thread still mutates the live player object first, then commits the auction row and affected inventory/bank save inside one DB transaction. This is much stronger than the upstream autosave-dependent flow, but the ultimate persistence model is still not a true item-ledger service.
6. **Price divisibility is enforced.** Since DB rows store remaining total price and derive per-unit price by integer division, listing creation rejects totals that do not divide evenly by quantity.
7. **`Market.checkAndRemoveExpiredItems()` runs on the market thread (50ms tick)**. With voidscape's 7-day expiry, it actually fires now (was effectively dead with `TIME_LIMIT = MAX_VALUE`). It refunds `amount_left`, not original amount, and its refund + sellout update are transactional.
8. **Untradable + coin filter** is server-side only — the client UI lets you select untradables in the list view; server rejects on submit. Acceptable UX.
9. **Discord webhook integration exists** (`want_discord_auction_updates` + `auctionAdd`/`auctionBuy` in `DiscordService`) but voidscape leaves it disabled (no webhook URL configured).

## Tuning knobs

| Knob | Where | Notes |
|---|---|---|
| Enable / disable | `server/local.conf` `spawn_auction_npcs` | False removes the NPC + blocks all opcode dispatch (`InterfaceOptionHandler.java:82`) |
| Listing cap | `NewMarketItemTask.MAX_LISTINGS_PER_PLAYER` | Constant, recompile to change |
| Expiry | `MarketItem.TIME_LIMIT` | Seconds; voidscape = 7d |
| Sale tax % | `BuyMarketItemTask` (`auctionPrice / 20`) | 5% by integer division — destroyed (gp sink), not redirected |
| NPC spawn | `server/conf/server/defs/locs/NpcLocsAuction.json` | Move/add NPC instances; id 837 only |
| NPC look | `server/conf/server/defs/NpcDefsCustom.json` (id 837) **and** the matching client `npcs.add` line | Both must change in lockstep |

## Glossary candidates

- **Market** — the runtime auction service (`Market.java`); not the same as a `Shop`.
- **MarketItem** — runtime wrapper around `AuctionItem` DB row, used in the in-memory cache.
- **Collectible** — a row in `expired_auctions` waiting to be picked up by the player (sold proceeds, expired stock, or cancelled stock).
- **On-sale tax** — voidscape's 5% gp sink at point of sale, distinct from on-listing fees.
