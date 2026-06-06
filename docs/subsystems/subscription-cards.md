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
- Talking to the vendor or right-clicking `Subscribe` checks whether a subscription card is reserved for that character.
- The vendor shares the Void Auctioneer's shadow-broker appearance rather than the robe pieces used by the Void acolyte/herald NPCs.
- If the pre-release portal has reserved a founder card for the character name, the vendor grants one physical card and marks the reservation claimed.
- If no card is reserved, the vendor only tells the player that no subscription card is ready for that character.
- The vendor does not sell subscription cards, does not open a shop, and does not maintain stock or price tiers.
- Founder cards use global `player_cache` keys named `founder_sub_card:<normalized username>` with value `1` for available and `2` for claimed.

Pre-release founder card reservations use the same global-cache pattern while the website/account system is still a local prototype. The website flow asks for a desired reserved username and uses Google login for the portal identity; the local dev Google endpoint can synthesize a `@google.voidscape.local` account for testing. The portal writes the available marker when `PORTAL_OPENRSC_DB` points at the game SQLite database; the game server marks it claimed after the vendor successfully puts the card in the player's inventory. Redeeming the physical item is still the only action that starts or extends subscription time.

The portal's Subscription view should therefore show two separate states: the account's current effective XP rates/subscription status, and the founder reward wallet. A fresh pre-release signup remains `Unsubscribed` at 7x combat / 4x skilling, while the reward wallet shows `1 card reserved in Lumbridge` until the in-game vendor gives the physical card.

## Local Verification

For a live-client founder-card claim check, launch the local server and the workbench client, seed a local global marker for the logged-in character, then run:

```bash
curl -fsS -X POST http://127.0.0.1:18787/scenario/subscription-vendor-claim
```

The scenario uses the real client NPC command packet against NPC `848`, verifies the vendor does not open a shop, and saves before/after screenshots under `tmp/workbench/screenshots/`. A successful founder claim should change `founder_sub_card:<normalized username>` from `1` to `2` and add exactly one `1602` inventory item. Running the scenario again without resetting the marker should not add a second free card. If the inventory is full, the marker should remain `1`, no extra `1602` should be saved, and the chat warning should tell the player to free one inventory slot.

## Client Definitions

The card and vendor are client-visible definitions.

- `Client_Base/src/orsc/Config.java` `CLIENT_VERSION = 10069`
- Server presets with the custom client use `client_version: 10069`
- Client item and NPC rows are appended in `Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java`
- Custom clients `10054+` still read a 32-bit per-item shop price override after each shop item row in `SEND_SHOP_OPEN`; the claim-only subscription vendor no longer uses that shop path.
- Custom clients `10055+` read subscription status and effective combat/skilling XP rates from `SEND_GAME_SETTINGS` for the wrench Profile panel.
