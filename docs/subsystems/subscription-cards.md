# Subscription Cards

Voidscape subscription is an account-level timed XP boost unlocked by redeeming a tradable subscription card. Each card adds 7 days of account subscription time, shared by every character linked to the same portal account.

## Rates

- Normal Voidscape target rates are 10x combat XP and 1.5x skilling XP.
- Subscribed accounts add `+1x` to combat and skilling XP, so the normal effective rate is 11x combat XP and 2.5x skilling XP.
- Combat skills follow the same grouping as the existing server XP multiplier: Attack, Defense, Strength, Hits, Ranged, Prayer, Good Magic, Evil Magic, and Magic.
- `Player.getExperienceMultiplier(...)` applies the subscription bonus before Wilderness and skull additive bonuses.

## Card Item

- Item id `1602`, `Subscription card`.
- Right-click command: `Redeem`.
- Client sprite id `617`, packed into `Client_Base/Cache/video/Authentic_Sprites.orsc` at archive entry `2767`.
- Cards are tradable. The free starter card and any later paid/earned cards are the same item.
- Redeeming consumes the clicked card and stores/extends the account-wide `acct_sub:<webAccountId>` timestamp in the global `player_cache` row (`playerID = 0`) when the character is linked to a portal account.
- Unlinked beta characters can redeem physical cards too. Their fallback timestamp is `char_sub:<playerId>` in the same global `player_cache` ledger, so tradable public-beta cards still work before portal linking exists for that character.
- If the account is already subscribed, redeeming another card extends the expiry by 7 days from the current account expiry.
- Unlinked character-scoped time does not transfer to other characters. Linking later resumes the account-wide `acct_sub:<webAccountId>` ledger.

## Lumbridge Vendor

`server/plugins/com/openrsc/server/plugins/custom/npcs/VoidSubscriptionVendor.java` owns the vendor flow.

- NPC id `848`, `Void Subscription Vendor`.
- Spawned near Lumbridge respawn by `NpcLocsVoidSubscriptions.json`.
- Right-click command: `Subscribe`.
- Talking to the vendor or right-clicking `Subscribe` opens a menu for one paid-card collection, the character's reserved free card, a signup code, or an explanation.
- The vendor shares the Void Auctioneer's shadow-broker appearance rather than the robe pieces used by the Void acolyte/herald NPCs.
- The free-card option checks the character's 2026 launch entitlement first, then the linked account's older starter-card promise. Each successful interaction grants only one physical card and claims only that marker in the same database transaction.
- A paid collection claims exactly one oldest pending entitlement for the linked web account. Multiple purchases are collected one card at a time; a full inventory or unsupported client leaves the entitlement pending.
- The vendor does not take payment, open a coin shop, or maintain stock/price tiers. Fiat checkout is initiated from the in-game Subscription Shop and authorized by Tebex.
- Starter cards use global `player_cache` keys named `starter_card:<webAccountId>` with value `1` for available and `2` for claimed.

### 2026 launch character campaign

The release campaign is a one-time final-roster cutover, not a character-creation or login reward. The reviewed native-account cutover snapshots every current save after the launch reset, requires an explicit decision for every staff, banned, invalid, or obvious test identity, and seeds one global marker named `launch_subcard_2026:<playerId>` per included character. Value `1` is available and value `2` is claimed. The immutable player id makes the entitlement character-specific without requiring a portal link, while keeping the marker as an audit tombstone if the portal later deletes that save.

The same SQLite transaction writes every missing campaign marker and `launch_subcard_2026:done = 1`. Once that seal exists, rerunning account backfill may repair ownership records but can never add launch cards for characters created after the snapshot. Deleting a launch character forfeits an unclaimed card; a replacement character does not inherit it. Registration must be paused around the final dry-run/apply so the review token describes the exact roster being launched.

The launch card is separate from an already-promised portal starter card, signup/referral code, or paid SHOP entitlement. A character with both a launch marker and a linked-account starter marker can therefore collect the launch card on the first free-card interaction and the starter card on the next. No existing claimed or waiting promotion is silently revoked by the cutover.

Pre-release starter-card reservations use the same global-cache pattern while the website/account system is still a local prototype. The portal writes the available marker when `PORTAL_OPENRSC_DB` points at the game SQLite database; the game server marks it claimed after the vendor successfully puts the card in the player's inventory. The portal dashboard and admin account view read that game marker when available, so `1` appears as waiting and `2` appears as claimed instead of disappearing. Staff revoke clears only an unclaimed `1` marker; a claimed `2` marker remains as the audit truth because the physical card has already been minted. Redeeming the physical item is still the only action that starts or extends subscription time.

## Signup Codes (vendor input box)

The public prelaunch landing page (email + username, no account) hands out one-time signup codes instead of account-bound markers.

- The portal mints `VOID-XXXX-XXXX` (alphabet excludes 0/O/1/I/L/U), one per canonical email; re-signing up with the same email returns the same code.
- During beta, each credited founder invite also mints one referral reward `VOID-XXXX-XXXX` code for the referrer. These reward codes are not account-bound; they use the same global cache ledger and the same vendor input flow as public signup codes.
- During in-game beta character creation, a new player can enter the inviter's in-game name on the appearance screen. The server validates the IGN, rejects self-referrals and obvious same-connection alt referrals using the inviter's saved login/creation IPs, stores one `refcode:<newPlayerId>` reward entry on the inviter, mints a real `VOID-XXXX-XXXX` code into the same signup-code ledger, and lets the inviter review earned codes with `::codes`.
- Issued codes are reserved as global cache rows: `playerID = 0`, `key = signup_code:<NORMALIZED CODE>`, `value = 1` (issued) / `2` (redeemed). Normalization uppercases and strips non-alphanumerics, so `void-abcd-2345` and `VOID ABCD 2345` both work.
- Redemption is part of the vendor dialogue: talking to the Void Subscription Vendor (NPC 848) with no account-reserved card pops the custom client's text input box ("Got a signup code from the website?"). The plugin blocks in `Functions.inputBox` (mirrors `multi()`: 60s timeout; cancel sends nothing and surfaces as timeout; the reply arrives via `INTERFACE_OPTIONS` sub-option 9 and is ignored unless `input_box_pending` is set). On a valid code, the code transition, exact item `1602` inventory persistence, and `item_origin` provenance commit in one transaction. Any failure rolls all three back, leaving the code available and no card minted. Full inventory or a client that cannot represent item `1602` refuses without consuming the code and never creates a ground item. Requires custom client `10087+` (`ActionSender.supportsInputBox`); older clients are told to update.
- The vendor is non-exclusive during the prompt: the plugin clears the NPC's busy/interaction state before showing the popup, so multiple players can enter codes simultaneously instead of queueing behind "the vendor is busy". Single-use correctness comes from the database transaction's shared connection lock plus the ledger value, not NPC exclusivity.
- Codes are deliberately not bound to a character or username: the card itself is tradable, so binding would add friction without preventing anything.
- Staff visibility: `GET /api/admin/signups` (JSON or `?format=csv`) lists every signup plus any referral reward codes with live issued/redeemed status read from the game DB; `POST /api/admin/signups/sync` re-writes signup and reward codes minted while the game DB was unavailable, skipping redeemed ones.

For a quick server-side test without the portal: insert a row (`INSERT INTO player_cache (playerID,type,key,value) VALUES (0,0,'signup_code:VOIDTESTAA22','1');`), talk to the vendor, and enter `VOID-TEST-AA22` in the popup.

Because cards are tradable, abuse prevention belongs before item creation: the portal grants at most one starter-card marker per web account, while the launch campaign grants exactly one reviewed marker per final-roster player id and then seals the cohort. Once a card exists, it behaves like any other tradable subscription card.

## In-game Shop and paid-card ledger

- The gold desktop/web `SHOP` tab opens an in-canvas catalog for item `1602`; opening the catalog never opens a URL. It shows the seven-day `+1x` benefit, tradability, current subscription/rates, and vendor collection instructions.
- `Buy securely` is the only checkout boundary. Desktop opens `https://voidscape.gg/portal#subscription-buy`; TeaVM resolves the same-origin `/portal#subscription-buy`. Signed-out portal users retain that one purchase intent through login, while signed-in users immediately create one checkout. Native Google Play Android keeps its keyboard tab and has no purchase URL.
- The portal creates an expiring, account-bound Headless checkout intent for one allowlisted single-payment package. It never writes player inventory. Browser events, redirects, and return URLs never fulfill a card.
- Only a valid raw-body Tebex webhook whose intent, basket, package, quantity, one-off sequence, currency, and signed money fields match can insert one `pending` row in `portal_commerce_entitlements`. Webhook ids, payload hashes, provider transaction ids, intent ids, and entitlement line keys make retries exact-once.
- The game server is the sole inventory writer. Vendor collection changes one `pending` entitlement to `claimed`, persists exact item `1602`, and records `subscription_tebex` provenance in the same transaction.
- Refund/dispute handling is monotonic. Unclaimed cards can be frozen/revoked; a claimed tradable card is never clawed out of inventory and instead enters review/debt handling.
- Live credentials remain disabled until a controlled real purchase/refund proves that Tebex preserves `voidscape_intent`/basket custom data into the webhook, the package has zero commands/RCON/gift/Discord/other deliverables, no command-queue side effect occurs, and exactly one pending entitlement is created.

## Transaction and failure contract

`SubscriptionCardTransactions` is the only production path for vendor grants and physical-card redemption.

- It reserves the player's save lifecycle before changing inventory, so an asynchronous full-player save cannot persist a tentative state.
- Grants re-read the entitlement marker inside `GameDatabase.atomicallySettled`, add the card without sending inventory packets, then persist the exact inventory, claimed marker, and synchronous `item_origin` row together.
- The grant API supports both global markers (starter/signup codes) and integer character-local markers for versioned launch campaigns.
- Redemption removes the exact clicked card by unique item ID without sending inventory packets, reads the current account/character expiry inside the same transaction, then persists inventory, the seven-day extension, and synchronous `item_transfer` provenance together.
- A confirmed rollback restores the same removed card object, unique item ID, and original inventory slot. Failed grants remove the exact tentative item ID; reserved item IDs are allowed to remain unused.
- A lost `COMMIT` acknowledgement is verified under the same connection lock using the durable marker/expiry and exact inventory item ID. If the database cannot establish either commit or rollback, the player session is fenced, its save reservation is retained, and the connection is closed without another save; an operator restart/reconciliation then reloads authoritative database state instead of guessing and duplicating or losing a card.
- Client inventory and subscription settings packets are sent only after commit. Linked online characters have their in-memory account expiry refreshed after commit so their XP checks do not use a stale account value.
- Prepared statements and lazy result-set calls acquire the same connection lock, including statements created before `BEGIN`, so they cannot interleave into the transaction. The game server remains the single writer for entitlement settlement; running multiple game-server JVMs or letting a portal mutate these inventory/cache rows directly is unsupported.

The portal's Subscription view should therefore show two separate states: the account's current effective XP rates/subscription status, and whether a starter card is waiting at Lumbridge or already claimed. A fresh pre-release signup remains `Unsubscribed` at 10x combat / 1.5x skilling, while the reward wallet shows `1 card reserved in Lumbridge` until the in-game vendor gives the physical card.

## Local Verification

For a live-client free-card claim check, launch the local server and the workbench client, seed either `launch_subcard_2026:<playerId> = 1` globally or the linked account's `starter_card:<webAccountId> = 1`, and either log the character in near the Lumbridge vendor at `(126, 649)` or use a staff/dev test account that can run the workbench teleport helper, then run:

```bash
curl -fsS -X POST http://127.0.0.1:18787/scenario/subscription-vendor-claim
curl -fsS -X POST http://127.0.0.1:18787/scenario/subscription-card-redeem
```

The claim scenario uses the real client NPC command packet against NPC `848`, verifies the vendor does not open a shop, and saves before/after screenshots under `tmp/workbench/screenshots/`. A successful claim changes only the selected marker from `1` to `2`, adds exactly one `1602` inventory item, and records `subscription_launch_2026` or `subscription_vendor` provenance. The redeem scenario then sends the real inventory item command packet, consumes item `1602`, and writes `acct_sub:<webAccountId>` with a future expiry for linked characters or `char_sub:<playerId>` for unlinked characters. Running the claim again without another available marker must not add a card. If the inventory is full, the marker remains `1`, no extra `1602` is saved, and the chat warning tells the player to free one inventory slot.

Run `tests/subscription-card-transactions-unit.sh` for deterministic reservation, replay, concurrent account extension, exact-slot compensation, full-inventory/old-client no-drop, and database-step fault-injection coverage.

## Client Definitions

The card and vendor are client-visible definitions.

- Current coordinated release source uses `Client_Base/src/orsc/Config.java` `CLIENT_VERSION = 10132` and launch `client_version: 10132`; the card/vendor definition contract itself began at `10053`.
- Client item and NPC rows are appended in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- Custom clients `10054+` still read a 32-bit per-item shop price override after each shop item row in `SEND_SHOP_OPEN`; the claim-only subscription vendor no longer uses that shop path.
- Custom clients `10055+` read subscription status and effective combat/skilling XP rates from `SEND_GAME_SETTINGS` for the wrench Profile panel.
