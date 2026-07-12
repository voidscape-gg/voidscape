# Auction House

Voidscape's auction house is a fixed-price player-to-player marketplace gated behind a single custom NPC at Edgeville bank. Built on top of upstream OpenRSC's existing `Market` service with voidscape adjustments. Not authentic — RSC never had one.

## Player flow

1. Walk to Edgeville bank south side, **(217, 460)**.
2. Right-click the **Void Auctioneer** (NPC id 837) → **"Auction"** (or talk-to + menu option).
3. Ironman accounts blocked; Bank PIN validated (if set).
4. AH UI opens (PC client only — Android excluded).
5. **Browse tab**: search by name + 10 category buttons + sortable list (Price Low/High, Name, Each Low/High). Select a listing, adjust qty with quick buttons, price-check the 7-day market snapshot, then two-click confirm the purchase.
6. **My Auctions tab**: pick item + amount + total price, post listing. Cancel any of your active listings.
7. **Intel tab**: shows 7-day hot items and recent completed sales. Completed-sale intel is anonymous: item, amount, unit price, total price, and age, but no buyer/seller names.
8. Listings expire after **7 days** if unsold; expired stock returns to seller via the auctioneer's Collect path.
9. **5% sale tax** taken from seller's proceeds at point of sale (gp sink — destroyed).
10. **Cap**: 6 active listings per player.

## Architecture

**Service**: `server/src/.../content/market/Market.java` — single-threaded `ScheduledExecutor` runs every 50ms, drains task queues, refreshes the in-memory `auctionItems` cache from DB. Started/stopped from `World.java`.

**Tasks** (each in `content/market/task/`):
- `NewMarketItemTask` — list an item. Validates tradable, price > 0, amount > 0, total price divides evenly by quantity, inventory holds enough, and **queryPlayerAuctionCount < 6**. Removes items from inventory, inserts auction row, and saves the inventory inside one DB transaction; rolls the inventory back on failure.
- `BuyMarketItemTask` — purchase. Validates not own listing + has coins + has space. Adds the bought item, removes buyer coins, creates seller collectible credit, writes an `auction_sales` history row, updates/sells out the listing, and saves the affected inventory/bank containers inside `database.atomically(...)`. 5% tax deducted from seller credit. After a committed sale, the same `auction_sales` history feeds the monthly contested `magnate` title.
- `CancelMarketItemTask` — seller cancels; returns remaining stock directly to inventory if possible, otherwise bank, and marks the auction canceled in the same DB transaction. Staff use moderator delete instead of canceling into their own inventory.
- `OpenMarketTask` — re-renders UI; sends raw packet 132 with auction list chunks (200 items each) followed by an intel payload.
- `PlayerCollectItemsTask` — pulls collectible (sold/expired) items into seller bank and marks claims collected in the same DB transaction.
- `ModeratorDeleteAuctionTask` — admin/mod listing nuke that returns the remaining stock to the seller's collectible queue in one DB transaction.

**Networking**: zero new opcodes. Uses existing `OpcodeIn.INTERFACE_OPTION` (sub-op 10 → AUCTION → sub-sub-op 0=BUY, 1=CREATE, 2=CANCEL, 3=REFRESH, 4=CLOSE, 5=DELETE) handled in `InterfaceOptionHandler.java`. Outbound list payload uses raw packet id 132 from `OpenMarketTask`: subtype 0 resets the UI, subtype 1 streams listing chunks, subtype 2 streams market intel. Hours-left is a short so 7-day listings display correctly. Custom outbound `OpcodeOut.SEND_AUCTION_PROGRESS` exists but is currently unused for the marketplace flow.

**DB**: `auctions`, `expired_auctions`, and `auction_sales` tables. Auction/listing schema is in `server/database/{mysql,sqlite}/patches/2026_04_26_add_auctionhouse.sql`; sale-history schema is in `2026_06_03_add_auction_market_intel.sql`. Auto-applied via `JDBCPatchApplier` on first boot. SQLite uses MySql query strings via inheritance (`SqliteGameDatabase extends MySqlGameDatabase`); no separate query class.

**Workbench fixture**: admin command `::workbenchauctionfixture` clears and reseeds deterministic rows marked `wb-fixture`/`wb-buyer`: 6 active listings and 7 recent sales. The PC workbench calls it through `POST /fixture/auction-house` and before `/scenario/auction-house-open`, then asks `Market` to force a cache refresh so the reseeded rows are visible even if the active auction count did not change.

**NPC**:
- Custom id 837 "Void Auctioneer" defined in `server/conf/server/defs/NpcDefsCustom.json`
- Client-side NPCDef appended in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` after Edgar (must be present or client renders the "Ana (not in a barrel)" sentinel)
- Spawn loc in `server/conf/server/defs/locs/NpcLocsAuction.json` — single entry
- Plugin in `server/plugins/.../custom/npcs/VoidAuctioneer.java`
- `WorldPopulator.java` loads the spawn iff `spawn_auction_npcs: true` (not gated on `LOCATION_DATA == 2` in voidscape, unlike upstream)

**Client UI**: `Client_Base/src/com/openrsc/interfaces/misc/AuctionHouse.java` — 490×279 panel, three tabs (Browse, My Auctions, Intel), sort cycle, search field (case-insensitive substring on item name), 10 icon category tiles, listing rows with item sprites, and a selected-listing detail card. Hardcodes outbound packet id 199 for AH operations.

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

**`auction_sales` row** — completed-sale history for market intel:
- `sale_id` PK auto-increment
- `auction_id`, `item_id`, `amount`, `unit_price`, `total_price`, `tax`
- `seller`, `seller_username`, `buyer`, `buyer_username`
- `sold_at` unix seconds

The UI only displays item/amount/price/age summaries; player names are stored for staff/database auditability but not shown in the public intel panel.

## Pitfalls / non-obvious

1. **Client NPCDef list is positional, not id-keyed.** Adding a server-side custom NPC requires a matching `npcs.add(...)` in `Client_Base/.../EntityHandler.java` at the right ordinal. Voidscape's pattern: append to `loadNPCDefinitions4()` after Edgar. Without it, the client falls back to "Ana (not in a barrel)" for the unknown id.
2. **Schema gate**: upstream put the schema in `database/{mysql,sqlite}/addons/` which is **not** auto-applied. Voidscape moved it to `patches/` so `JDBCPatchApplier` runs it on first boot.
3. **Per-unit price** is `total_price / amount_left` (re-derived each tick). Listing 10 items for 1000gp total = 100gp each, not "100gp per unit, total 1000gp". The UI shows both.
4. **`queryPlayerAuctionCount` parameterized-SQL bug** (upstream): `seller='?'` was a literal string, never bound. Voidscape fixed to real `?`. Fix is in `MySqlQueries.java` (`playerAuctionCount` query).
5. **Auction mutations now save affected containers synchronously.** The market thread still mutates the live player object first, then commits the auction row and affected inventory/bank save inside one DB transaction. This is much stronger than the upstream autosave-dependent flow, but the ultimate persistence model is still not a true item-ledger service.
6. **Price divisibility is enforced.** Since DB rows store remaining total price and derive per-unit price by integer division, listing creation rejects totals that do not divide evenly by quantity.
7. **`Market.checkAndRemoveExpiredItems()` runs on the market thread (50ms tick)**. With voidscape's 7-day expiry, it actually fires now (was effectively dead with `TIME_LIMIT = MAX_VALUE`). It refunds `amount_left`, not original amount, and its refund + sellout update are transactional.
8. **Untradable + coin filter** is server-side only — the client UI lets you select untradables in the list view; server rejects on submit. Acceptable UX.
9. **Discord webhook integration exists** (`want_discord_auction_updates` + `auctionAdd`/`auctionBuy` in `DiscordService`) but voidscape leaves it disabled (no webhook URL configured).
10. **Market intel is informational, not anti-manipulation.** The 7-day window helps players price-check, but wash trades can still skew low-volume items.
11. **Workbench fixture rows are dev-only but real DB rows.** They are inserted through the same database layer as normal auctions, so they exercise the live `Market` cache and intel queries. They are safe to reseed because only rows with the fixture marker names are deleted.

## Tuning knobs

| Knob | Where | Notes |
|---|---|---|
| Enable / disable | `server/local.conf` `spawn_auction_npcs` | False removes the NPC + blocks all opcode dispatch (`InterfaceOptionHandler.java`, `AUCTION` case) |
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
