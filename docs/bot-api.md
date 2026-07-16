# voidbot packet API (bot-api.md)

Spec for `tools/voidbot`, the headless game-interaction client. The implemented command list below is the current CLI surface; the larger handler table is a protocol reference/roadmap for adding more real player actions. The wire protocol was reverse-engineered and **validated against a live login capture** (2026-06-10); see the *Protocol* section at the bottom and the capture notes in `tools/voidbot/protocol.py`.

> Rule of thumb: if an action is not in **Implemented voidbot commands**, extend voidbot first, then use it. Never drive the game by screenshot, mouse, or click coordinates.

## Command table

Inbound handlers in `server/src/com/openrsc/server/net/rsc/handlers/` → intended voidbot command coverage. `precond` = client/interface state the daemon must hold.

| Handler | Opcode (wire) | Payload | Player action | voidbot command | Preconditions |
|---|---|---|---|---|---|
| AttackHandler | NPC_ATTACK (custom wire packet id 190; enum ordinal 27), PLA | serverIndex:short (TargetMobStruct.serverIndex; packet length must be exactly 2 or it is r | Attack an NPC (NPC_ATTACK) or another player (PLAYER_ATTACK) by server index; me | `voidbot attack npc <server-index> \| voidbot attack player <server-index>` | Not in a duel (packet silently dropped); not already in comb |
| BankHandler | BANK_CLOSE (custom wire packet id 212; enum ordinal 68) | (none — parser allocates empty BankStruct, reads nothing) | Close the bank interface | `voidbot bank close` | Bank interface open (player.accessingBank(), else flagged su |
| BankHandler | BANK_WITHDRAW (custom wire packet id 22; enum ordinal 69) | catalogID:short, amount:int, noted:byte (1=as-note; this third field is read ONLY when ser | Withdraw amount of item (by catalog/item id, not bank slot) from bank to invento | `voidbot bank withdraw <catalog-id> <amount> [--noted]` | Bank interface open; not Ultimate Ironman; not busy/ranging/ |
| BankHandler | BANK_DEPOSIT (custom wire packet id 23; enum ordinal 70) | catalogID:short, amount:int (length must be >= 6) | Deposit amount of item (by catalog/item id) from inventory into bank | `voidbot bank deposit <catalog-id> <amount>` | Bank interface open; not Ultimate Ironman; not busy/ranging/ |
| BankHandler | BANK_DEPOSIT_ALL_FROM_INVENTORY (custom wire packet id 24; e | (none — empty BankStruct) | Deposit the entire inventory into the bank in one action | `voidbot bank deposit-all` | Bank interface open; not Ultimate Ironman; not busy/ranging/ |
| BankHandler | BANK_DEPOSIT_ALL_FROM_EQUIPMENT (custom wire packet id 26; e | (none — empty BankStruct) | Unequip and deposit all worn equipment into the bank | `voidbot bank deposit-equipment` | Bank interface open; not Ultimate Ironman; not busy/ranging/ |
| BankHandler | BANK_SAVE_PRESET (custom wire packet id 27; enum ordinal 73) | presetSlot:short (0..2; BankPreset.PRESET_COUNT=3; packet length must be exactly 2) | Save the current inventory as a bank preset loadout in the given slot | `voidbot bank save-preset <slot 0-2>` | Bank interface open; not Ultimate Ironman; not busy/ranging/ |
| BankHandler | BANK_LOAD_PRESET (custom wire packet id 28; enum ordinal 74) | presetSlot:short (0..2; packet length must be exactly 2) | Load a previously saved bank preset loadout (withdraw/deposit to match preset) | `voidbot bank load-preset <slot 0-2>` | Bank interface open; not Ultimate Ironman; not busy/ranging/ |
| BlackHoleHandler | ON_BLACK_HOLE (custom wire packet id 86; enum ordinal 78) | (none — NoPayloadStruct; packet length must be exactly 0) | Escape the black hole (Cage of Disorder): teleports to the dwarven mines (311,33 | `voidbot black-hole-return` | Player's tile must satisfy location.onBlackHole() (silently  |
| BlinkHandler | BLINK (custom wire packet id 59; enum ordinal 5) | x:short, y:short (TargetPositionStruct.coordinate; packet length must be exactly 4; struct | Staff teleport: instantly teleport self to tile (x, y) via ctrl-click on the map | `voidbot blink <x> <y>` | player.isMod() — non-mod senders are flagged suspicious ('no |
| ChatHandler | CHAT_MESSAGE (custom wire packet id 216; enum ordinal 64) | length:smart08_16 (1 byte if <128 else 2 bytes minus 0x8000), encryptedChat:byte[remaining | Send a public chat message visible to nearby players | `voidbot say <message>` | None to send — always accepted. Mute, shadow-mute, tutorial  |
| ClientDebugHandler | SEND_DEBUG_INFO (enum ordinal 91; NO wire packet id in the C | infoString:string (RSC string: bytes until 0x0A terminator) — per Payload38Parser.java:519 | Client uploads a debug/diagnostic text blob; server only logs it at DEBUG level | `voidbot debug-report <text> (daemon-internal; do not expose on this server)` | Logged-in session; on voidscape (CUSTOM protocol, client_ver |
| CombatStyleHandler | COMBAT_STYLE_CHANGED (custom wire packet id 29; enum ordinal 6) | style:byte; exact payload length 1; `0=controlled`, `1=aggressive`, `2=accurate`, `3=defensive` | Change the player's combat style; a duel journal records the style in force when each melee swing is resolved | `voidbot combat-style <controlled\|aggressive\|accurate\|defensive>` | Handler accepts valid styles even during combat; invalid values mark the player suspicious |
| CommandHandler | COMMAND (custom wire packet id 38; enum ordinal 65) | command:string (RSC string: raw bytes until 0x0A terminator; format '<cmd> [arg1 arg2 ...] | Execute a server slash-command (::cmd) — quest points, info commands, mod/admin  | `voidbot command <cmd> [args...]` | Rate limit: non-admins must wait 1000 ms between commands (v |
| FriendHandler | SOCIAL_ADD_FRIEND (wire 195), SOCIAL_REMOVE_FRIEND (wire 167 | player:string (packet.readString(), target username). SOCIAL_SEND_PRIVATE_MESSAGE only: me | Manage friends/ignore list and send a private message: add/remove friend, add/re | `voidbot friend-add <name> \| voidbot friend-remove <name> \| voidbot ignore-add ` | Mostly none. friend-add/ignore-add: target account must exis |
| GameObjectAction | OBJECT_COMMAND (wire 136), OBJECT_COMMAND2 (wire 79) | x:short, y:short (exact object world tile; payload length 4) | Perform scenery option 1 or 2 | `voidbot object-action <x> <y> [1\|2] [--id <objectId>]` | Object must be visible. `--id` emits the real client-style entity prewalk. While an exact-target OpLoc gather is busy, matching id/tile/option clicks coalesce into one follow-up; a mismatch cancels. |
| GameObjectWallAction | INTERACT_WITH_BOUNDARY (wire 14), INTERACT_WITH_BOUNDARY2 (w | x:short, y:short, direction:byte (wall-object facing 0-3; packet length must be exactly 5  | Perform option 1 (INTERACT_WITH_BOUNDARY) or option 2 (INTERACT_WITH_BOUNDARY2)  | NOT IMPLEMENTED (planned syntax): `voidbot boundary-action <x> <y> <direction> [--option 1\|2]  (direction is the w` | Wall object with that exact direction must exist at (x,y) in |
| GameSettingHandler | GAME_SETTINGS_CHANGED (wire 111); OpcodeIn enum ordinal 63 | index:byte, value:byte (packet length must be exactly 2). Parser copies value into cameraM | Change a game/client setting. For custom clients (version > 235, includes 10087) | `voidbot set-setting <index> <value>  (optionally alias well-known indices: voidb` | none (logged-in session only). index must be 0-99 else the p |
| GroundItemTake | GROUND_ITEM_TAKE (wire 247 — shared packet ID with CHANGE_DE | x:short, y:short, itemId:short (catalog/definition id of the ground item) | Pick up a ground item at a world tile. Server queues WalkToPointAction to the it | `voidbot take-item <x> <y> <item-id>` | Ground item with that itemId must be visible to the player a |
| Heartbeat | HEARTBEAT (wire 67); OpcodeIn enum ordinal 0 | (none — NoPayloadStruct; packet length must be 0) | Session keepalive ping; no gameplay effect. Server handler body is empty — every | `(daemon-internal: voidbot session loop sends HEARTBEAT automatically when no oth` | none (active logged-in connection) |
| InterfaceOptionHandler | INTERFACE_OPTIONS (wire 199, custom opcode); OpcodeIn enum o | index:byte (InterfaceOptions discriminator), then per-option union: [0 SWAP_CERT \| 1 SWAP | Multiplexed custom-interface action: toggle cert/note swap mode, reorder bank/in | `Family: voidbot cert-swap <0\|1>; voidbot note-swap <0\|1>; voidbot bank-move <s` | Global: not in combat; index must map to InterfaceOptions en |
| InterfaceOptionHandler — Void Arena | INTERFACE_OPTIONS wire `199` | option `18`: `action:byte`, `ruleMask:byte`, `targetServerIndex:short`; actions challenge/decline/rules/accept/confirm are `0..4` | Drive the stock two-player Death Match setup without UI input | `arena-challenge <server-index>`; `arena-rules`; `arena-accept`; `arena-confirm`; `arena-decline [server-index]` | Both players must satisfy the server's current lobby/setup checks; accept/confirm/rules infer the target from server-confirmed arena state |
| InterfaceShopHandler | SHOP_BUY (wire 236), SHOP_SELL (wire 221), SHOP_CLOSE (wire 166) | SHOP_CLOSE: none. SHOP_BUY / SHOP_SELL: catalogID:short (item definition id), stockAmount:unsigned short (current displayed stock), amount:unsigned short | Buy an item from, sell an item to, or close the currently open shop. | `voidbot shop-sell <catalog-id> <stock> <amount>; voidbot shop-close`; SHOP_BUY is not implemented | Shop interface must be open (`player.getShop() != null`); amount must be positive. |
| ItemActionHandler | ITEM_COMMAND (wire packet id 90, custom protocol; OpcodeIn o | index:short (inventory slot, or -1 for worn item), amount:int (always present on custom pr | Invoke an inventory item's right-click command (eat, drink, bury, rub, open, etc | `voidbot item-action <inv-slot> <command-index> [--amount <n>]  (worn-item varian` | Logged in; not in combat; not busy; item exists at slot and  |
| ItemDropHandler | ITEM_DROP (wire packet id 246, custom protocol; OpcodeIn ord | index:short (inventory slot, or -1 for worn item), amount:int (present ONLY when server co | Drop an item (or a chosen stack amount, 'Drop X') from inventory onto the ground | `voidbot drop <inv-slot> [--amount <n>]` | Not in combat; not dueling; not busy; no active trade or due |
| ItemEquip | ITEM_EQUIP_FROM_INVENTORY (wire packet id 169; OpcodeIn ordi | slotIndex:short (both opcodes; exact wire length 2). FROM_INVENTORY: inventory slot 0-29.  | Wield/wear an item from an inventory slot, or equip directly from a bank slot (e | `voidbot equip <inv-slot>; voidbot equip-from-bank <bank-slot>` | passCheck: not busy (unless in combat); duel rule 'No extra  |
| ItemExamineRequest | ITEM_EXAMINE_REQUEST (wire packet id 36, voidscape-custom op | slot:short (inventory slot; exact wire length 2) | Examine an inventory item; server returns the item description as a game message | NOT IMPLEMENTED (planned syntax): `voidbot examine <inv-slot>` | Slot within inventory bounds and occupied; no busy/combat ch |
| ItemUnequip | ITEM_UNEQUIP_FROM_INVENTORY (wire packet id 170; ordinal 41) | FROM_INVENTORY: slotIndex:short (inventory slot 0-29; wire length 2). FROM_EQUIPMENT and R | Remove a worn item: back to inventory (classic worlds address it by the inventor | `voidbot unequip <inv-slot>; voidbot unequip-slot <equip-slot 0-13>; voidbot uneq` | passCheck: not busy (unless in combat); duel 'no extra items |
| ItemUseOnGroundItem | GROUND_ITEM_USE_ITEM (wire packet id 53, custom protocol; Op | groundItemX:short, groundItemY:short, slotIndex:short (inventory slot of the carried item) | Use a carried inventory item on an item lying on the ground (classic example: ti | `voidbot use-on-ground <inv-slot> <x> <y> <ground-item-id>` | Not in combat; not dueling; not busy. A ground item with tha |
| ItemUseOnItem | ITEM_USE_ITEM (wire packet id 91, custom protocol; OpcodeIn  | slotIndex1:short, slotIndex2:short (both inventory slots; exact wire length 4) | Use one inventory item on another (combine/craft: herblaw, fletching, gem cuttin | `voidbot use-on-item <slot1> <slot2>` | Not in combat; not dueling; not busy. Both slots occupied (n |
| ItemUseOnNpc | NPC_USE_ITEM (wire packet id 135, custom protocol; OpcodeIn  | serverIndex:short (NPC's world/server index — NOT the NPC definition id), slotIndex:short  | Use a carried inventory item on an NPC (e.g., cert/note exchange with bankers, q | `voidbot use-on-npc <npc-server-index> <inv-slot>` | Not in combat; not dueling; not busy. NPC with that server i |
| ItemUseOnObject | USE_WITH_BOUNDARY (ordinal 23, custom wire id 161); USE_ITEM | USE_WITH_BOUNDARY: objectX:short, objectY:short, direction:byte, slotID:short, [itemID:sho | Use an inventory (or equipped) item on a wall/boundary object (door, fence) or o | `voidbot use-item-on-boundary <slot> <x> <y> <dir> [--equipped-item <catalogId>] ` | Not in combat, not dueling, not busy. Object must exist in p |
| ItemUseOnPlayer | PLAYER_USE_ITEM (ordinal 31, custom wire id 113) | serverIndex:short (target player's server index), slotIndex:short (caster's inventory slot | Use an inventory item on another player (e.g. gnome ball pass, christmas cracker | `voidbot use-item-on-player <playerIndex> <slot>` | Not in combat, not dueling, not busy. Target player must res |
| KnownPlayersHandler | KNOWN_PLAYERS (ordinal 92; NO wire id in the CUSTOM dialect  | playerCount:short, then repeated playerCount times: playerServerIndex:short, playerServerA | None — client-to-server cache sync telling the server which player appearances t | `session-internal (daemon auto-manages appearance cache; no CLI verb)` | Logged-in session; only meaningful on authentic-era protocol |
| Logout | CONFIRM_LOGOUT (ordinal 3, custom wire id 31) | none (NoPayloadStruct; length == 0) | Final/forceful logout used by the authentic client when the window closes — runs | `voidbot logout --confirm (also sent automatically by the daemon on shutdown)` | player.canLogout() must be true (not recently in combat, not |
| LogoutRequest | LOGOUT (ordinal 4, custom wire id 102) | none (NoPayloadStruct; length == 0) | Graceful logout request (the logout button) — server either unregisters the play | `voidbot logout` | player.canLogout() (not in/just out of combat, not busy). On |
| MenuReplyHandler | QUESTION_DIALOG_ANSWER (ordinal 7, custom wire id 116) | option:byte (signed; length == 1). -1 = cancel/escape, 30 = special 'blank reply' sentinel | Answer a pending multi-option question dialog (NPC dialogue choices, 'What would | `voidbot menu-reply <optionIndex> ; voidbot menu-cancel (sends -1)` | A menu must be pending: either player.getMenu() != null (Men |
| NpcCommand | NPC_COMMAND (ordinal 25, custom wire id 202); NPC_COMMAND2 ( | serverIndex:short (NPC server index). Length == 2 bytes. Same shape for both opcodes; the  | Invoke an NPC's right-click action: NPC_COMMAND = NPCDef command1 (e.g. 'pickpoc | `voidbot npc-command <npcServerIndex> <1\|2>  (daemon emits wire id 202 for 1, 20` | Not in combat, not busy (dueling NOT checked here, unlike ta |
| NpcTalkTo | NPC_TALK_TO (ordinal 24, custom wire id 153) | serverIndex:short (NPC server index). Length == 2 bytes. | Talk to an NPC — walks to it and starts its dialogue script. | `voidbot npc-talk <npcServerIndex>` | Not in combat, not dueling, not busy. NPC must resolve via w |
| NpcUseItem (server/src/com/openrsc/server/net/rsc/handlers/NpcUseItem.java) — DEAD CODE; live handler for this opcode is ItemUseOnNpc.java | NPC_USE_ITEM (enum ordinal 29; CUSTOM-protocol wire packet I | serverIndex:short (NPC server index), slotIndex:short (inventory slot 0-29). Struct: ItemO | Use an inventory item on an NPC (walks to NPC, fires UseNpcTrigger plugins, e.g. | `voidbot use-item-on-npc <npc-server-index> <inv-slot>` | NPC with that server index loaded in world; item present in  |
| PlayerAppearanceUpdater (server/src/com/openrsc/server/net/rsc/handlers/PlayerAppearanceUpdater.java) | PLAYER_APPEARANCE_CHANGE (enum ordinal 8; custom wire id 235) | WIRE ORDER: gender/head/body/mustEqual2, hair/top/trouser/skin, ironman, one_xp, hair_style, then client >=10125 country bytes (`00 00` none or ASCII `CC`), then optional referral string. | Submit the character-design screen; also updates the player-chosen global-chat country flag. | `voidbot design-character --gender <male\|female> --country <cc\|none>` | Server must have opened the appearance screen: player.isChangingAppearance(). |
| PlayerDuelHandler (server/src/com/openrsc/server/net/rsc/handlers/PlayerDuelHandler.java) | PLAYER_DUEL wire `103`; DUEL_FIRST_SETTINGS_CHANGED `8`; DUEL_OFFER_ITEM `33`; DUEL_FIRST_ACCEPTED `176`; DUEL_SECOND_ACCEPTED `77`; DUEL_DECLINED `197` | Request: target server index `short`. Rules: four disallow bytes in retreat/magic/prayer/weapons order. Offer: `byte count`, then each `short catalogId`, `int amount`, `short noted`; it replaces the complete offer and the handler accepts at most 8 rows. Accept/confirm/decline payloads are empty. | Drive the complete two-screen player-duel lifecycle with stake and rules | `voidbot duel-request <server-index>`; `duel-offer [id:amount[:noted] ...]`; `duel-settings [--no-retreat] [--no-magic] [--no-prayer] [--no-weapons]`; `duel-accept`; `duel-confirm`; `duel-decline` | Members world; both players eligible, reachable and nearby; both must request each other; offers/rules reset both first-screen accepts; both first accepts precede both confirmations |
| PlayerFollowRequest (server/src/com/openrsc/server/net/rsc/handlers/PlayerFollowRequest.java) | PLAYER_FOLLOW (enum ordinal 35; CUSTOM-protocol wire packet  | serverIndex:short (target player server index). Struct: TargetMobStruct. Packet length mus | Start following another player (radius 1); prints 'Following <name>'. | `voidbot follow-player <player-server-index>` | Target player index must resolve to an online player (else s |
| PlayerTradeHandler (server/src/com/openrsc/server/net/rsc/handlers/PlayerTradeHandler.java) | PLAYER_INIT_TRADE_REQUEST (ordinal 34, wire 142); PLAYER_ACC | PLAYER_INIT_TRADE_REQUEST: targetPlayerID:short (player server index), len==2. PLAYER_ADDE | Full trade lifecycle: request trade with a player; replace your offer with a lis | `voidbot trade-request <player-server-index> \| voidbot trade-offer [<catalogId>:` | All opcodes: sender not busy/ranging/banking/dueling/in-comb |
| PrayerHandler (server/src/com/openrsc/server/net/rsc/handlers/PrayerHandler.java) | PRAYER_ACTIVATED wire `60`; PRAYER_DEACTIVATED wire `254` | `prayerID:byte`, `0..14` inclusive (`14` is Protect from Magic); exact payload length 1 | Turn one prayer on or off. | `voidbot prayer-on <prayer-id 0-14>`; `voidbot prayer-off <prayer-id 0-14>` | World and duel/arena rules must allow prayer; activation also requires the prayer's level and a positive Prayer-point balance. |
| PrivacySettingHandler (server/src/com/openrsc/server/net/rsc/handlers/PrivacySettingHandler.java) | PRIVACY_SETTINGS_CHANGED (enum ordinal 66; CUSTOM-protocol w | blockChat:byte, blockPrivate:byte, blockTrade:byte, blockDuel:byte — for custom clients th | Change chat/PM/trade/duel privacy blocking modes (the settings-tab toggles). | `voidbot set-privacy --chat <0\|1\|2> --private <0\|1\|2> --trade <0\|1\|2> --due` | none (no interface state required; one of the few opcodes ex |
| ReportHandler (server/src/com/openrsc/server/net/rsc/handlers/ReportHandler.java) | REPORT_ABUSE (enum ordinal 67; CUSTOM-protocol wire packet I | targetPlayerName:string (RSC string via packet.readString()), reason:byte (rule code; Cons | File an abuse report against a player name (and, for mods, optionally perma-mute | `voidbot report <player-name> <reason-code> [--mute]` | Cannot report self; name must be non-empty and exist in the  |
| SecuritySettingsHandler | CHANGE_PASS (enum ordinal 87; CUSTOM wire packet id 25) | oldPassword:string (LF-terminated), newPassword:string (LF-terminated); both trimmed serve | Change account password | `voidbot change-password <old-password> <new-password>` | none (any logged-in session) |
| SecuritySettingsHandler | CHANGE_RECOVERY_REQUEST (enum ordinal 85; CUSTOM wire packet | none (empty payload) | Request the 'set recovery questions' screen (server-side arm step before SET_REC | `voidbot request-recovery-change (daemon should auto-chain this immediately befor` | Must be > 2000 ms after login (anti-ISAAC-desync guard silen |
| SecuritySettingsHandler | SET_RECOVERY (enum ordinal 88; CUSTOM wire packet id 208) | 5 interleaved pairs, in order q1,a1,q2,a2,q3,a3,q4,a4,q5,a5 — each question:string (LF-ter | Submit five account recovery questions and answers | `voidbot set-recovery <q1> <a1> <q2> <a2> <q3> <a3> <q4> <a4> <q5> <a5>` | player.isChangingRecovery() must be true — i.e. a CHANGE_REC |
| SecuritySettingsHandler | CHANGE_DETAILS_REQUEST (enum ordinal 86; CUSTOM wire packet  | none (MUST be empty — wire id 247 with payload length > 1 is parsed as GROUND_ITEM_TAKE in | Request the contact-details entry screen (arm step before SET_DETAILS) | `voidbot request-details-change (daemon should auto-chain immediately before set-` | Contact details not modified within the last 24 hours (else  |
| SecuritySettingsHandler | SET_DETAILS (enum ordinal 89; CUSTOM wire packet id 253) | fullName:string (LF), zipCode:string (LF), country:string (LF), email:string (LF) — exactl | Submit account contact details | `voidbot set-details <full-name> <zip-code> <country> <email> (allow empty string` | player.isChangingDetails() must be true (prior CHANGE_DETAIL |
| SecuritySettingsHandler | CANCEL_RECOVERY_REQUEST (enum ordinal 90; CUSTOM wire packet | none (no fields read) | Cancel a pending (not-yet-applied) recovery questions change | `voidbot cancel-recovery-change` | A pending recovery change set less than 14 days ago must exi |
| SleepHandler | SLEEPWORD_ENTERED (enum ordinal 76; CUSTOM wire packet id 45 | sleepWord:string (LF/0x0A-terminated byte run via Packet.readString). NOTE: the CUSTOM pro | Submit the sleepword captcha answer while sleeping | `voidbot sleep-word <word> (plus voidbot sleep-word --refresh to send the literal` | player.isSleeping() must be true (player used a bed/sleeping |
| SpellHandler | CAST_ON_SELF (enum ordinal 48; CUSTOM wire packet id 137) | spellId:short — index into Constants.spellMap (0=HOME_TELEPORT, 1=WIND_STRIKE, 2=CONFUSE,  | Cast a self-targeted spell: teleports, stat boosts, Charge, Bones-to-Bananas | NOT IMPLEMENTED (planned syntax): `voidbot cast-self <spell-id>` | Generic cast gates (apply to ALL Cast* opcodes): not busy un |
| SpellHandler | PLAYER_CAST_PVP (enum ordinal 30; CUSTOM wire packet id 229) | spellId:short, targetIndex:short (target PLAYER server index). Payload length must be 4. | Cast an offensive/curse spell on another player | `voidbot cast-player <spell-id> <player-server-index>` | Generic cast gates (see CAST_ON_SELF row); spell must be typ |
| SpellHandler | CAST_ON_NPC (enum ordinal 28; CUSTOM wire packet id 50) | spellId:short, targetIndex:short (target NPC server index). Payload length must be 4. | Cast an offensive spell on an NPC | `voidbot cast-npc <spell-id> <npc-server-index>` | Generic cast gates; spell must be type 2; NPC must resolve v |
| SpellHandler | CAST_ON_INVENTORY_ITEM (enum ordinal 39; CUSTOM wire packet  | spellId:short, targetIndex:short (INVENTORY SLOT index, not catalog id). Payload length mu | Cast a spell on an inventory item (alchemy, superheat, enchants, Curse/Enfeeble  | `voidbot cast-item <spell-id> <inventory-slot>` | Generic cast gates; additionally dropped outright while a tr |
| SpellHandler | CAST_ON_BOUNDARY (enum ordinal 22; CUSTOM wire packet id 180 | spellId:short, x:short, y:short (boundary tile), direction:byte. Payload length must be 7. | Cast a spell on a wall/boundary object (doors etc.) | `voidbot cast-boundary <spell-id> <x> <y> <direction>` | Generic cast gates only. |
| SpellHandler | CAST_ON_SCENERY (enum ordinal 52; CUSTOM wire packet id 99) | spellId:short, x:short, y:short (scenery/GameObject tile). Payload length must be 6. | Cast a spell on a scenery object (e.g. Charge Orb spells on obelisks) | `voidbot cast-object <spell-id> <x> <y>` | Generic cast gates; a GameObject must exist at that tile wit |
| SpellHandler | CAST_ON_GROUND_ITEM (enum ordinal 36; CUSTOM wire packet id  | spellId:short, x:short, y:short (ground item tile), targetIndex:short (ground item CATALOG | Cast a spell on a ground item (Telekinetic Grab) | `voidbot cast-ground-item <spell-id> <x> <y> <item-id>` | Generic cast gates; a visible ground item with that item id  |
| SpellHandler | CAST_ON_LAND (enum ordinal 49; CUSTOM wire packet id 158) | spellId:short, x:short, y:short (clicked tile). Payload length must be 6. | Cast a non-targeted spell at the ground (client sends this for ground-click cast | `voidbot cast-land <spell-id> [<x> <y>] (coords accepted but ignored server-side;` | Generic cast gates; Charge additionally requires Mage Arena  |
| TutorialHandler | SKIP_TUTORIAL (enum ordinal 77; CUSTOM wire packet id 84) | none (NoPayloadStruct; parser requires payload length == 0) | Skip Tutorial Island and teleport to the spawn town | `voidbot skip-tutorial` | Player must be located on Tutorial Island AND server config  |
| WalkRequest | WALK_TO_POINT (enum ordinal 2; CUSTOM wire packet id 187) | firstStepX:short, firstStepY:short, then zero or more signed-byte waypoint deltas | Ground-click movement and combat retreat | `voidbot walk-step <x> <y>` | While busy with batching enabled, this deliberately interrupts the active plugin, including a queued gather repeat. |
| WalkRequest | WALK_TO_ENTITY (enum ordinal 1; CUSTOM wire packet id 16) | Same path payload as WALK_TO_POINT | Interaction prelude emitted before a scenery/entity command | Daemon-internal for `object-action --id` | A busy exact-target gather tentatively accepts this prelude; the following object command must match. A mismatched object command or other semantic follow-up interrupts the gather. |
| WorldWalkRequest | WORLD_WALK_REQUEST (enum ordinal 93; CUSTOM wire packet id 35) | destX:short, destY:short (absolute server coordinates; floor is encoded in Y as floor = de | World-map click: server-side long-distance auto-walk to a destination tile | `voidbot goto <x> <y>` | Not in combat (reply reason 7 COMBAT), not busy unless a men |

## Coverage notes

### Gathering input pairing

The one-slot repeat buffer applies only to ordinary scenery commands (`OpLoc`) for
Woodcutting, Fishing, and Mining on batch-progression worlds. It does not change
item-on-scenery (`UseLoc`) actions such as using an axe on a tree. Repeated exact
id/tile/option clicks collapse to one pending attempt; a point walk, different target,
different option, or other semantic action remains a cancellation. `object-action
--id` is the acceptance-test form because it calculates the object edge and sends
`WALK_TO_ENTITY` before the object command, matching the stock client packet sequence.

Opcodes handled outside the per-handler table (login/session/pre-auth):
- NPC_DEFINITION_REQUEST (ordinal 79, voidscape/OpenRSC custom) — PayloadCustomParser.toOpcodeEnum maps CUSTOM wire packet id 89 to it (PayloadCustomParser.java:255-257), but there is NO parse case in P
- LOGIN (ordinal 80, CUSTOM wire packet id 0) — incoming opcode handled pre-session by LoginPacketHandler/RSCProtocolDecoder rather than a handlers/ PayloadProcessor; no row covers it, yet a bot CLI mus
- RELOGIN (ordinal 81, retro-rsc) — wire id 19 via ReverseOpcodeLookup.java:18 (no PayloadCustomParser case); handled in LoginPacketHandler (opcode == OpcodeIn.RELOGIN branches at lines 204/286/375/444/
- REGISTER_ACCOUNT (ordinal 82, CUSTOM wire packet id 2) — pre-login account creation, handled in LoginPacketHandler; no row covers it.
- FORGOT_PASSWORD (ordinal 83, CUSTOM wire packet id 4 when NOT logged in, per resolveOpcode case 4) — pre-login recovery flow; mentioned only in passing in the CAST_ON_INVENTORY_ITEM row's notes, no ro
- RECOVERY_ATTEMPT (ordinal 84, CUSTOM wire packet id 8 when NOT logged in, per resolveOpcode case 8) — pre-login recovery-answer submission; mentioned only as the pid-8 conflict partner in the PlayerDu

Audit corrections applied / noted:
- No wire-format errors found. Spot-checked 25+ rows (including the mandated WalkRequest, BankHandler, MenuReplyHandler, SpellHandler, NpcTalkTo) against PayloadCustomParser.java: every CUSTOM wire id m
- Minor precision issue — GameSettingHandler row: the claim 'index 4-99 persist UI/QoL prefs into player cache' is overbroad. Only the enumerated indices write cache keys (4, 7-11, 16-55 with gaps, plus
- Minor precision issue — ReportHandler row: 'target must appear in the world snapshot list within the last 60 seconds' is approximately right but the actual loop (ReportHandler.java:38-60) rejects as s
- Minor completeness note — ClientDebugHandler/SEND_DEBUG_INFO row: 'era dialects Payload38/Payload69 map it to packet id 17' is true (both at parser line 180-181, parse case at 519-523 as cited) but Pa
- Verified-correct claims worth affirming because they sound surprising: the BANK_LOAD/SAVE_PRESET 'voidscape removed upstream's WANT_EQUIPMENT_TAB gate' claim is confirmed by explicit comments in BankH


## Implemented voidbot commands (current)

The daemon (`tools/voidbot/voidbotd.py`) + CLI (`tools/voidbot/voidbot`) currently implement:

**Session**
- `voidbot start --user U (--password-file FILE|--pass P)` — launch the daemon (logs in, holds the session)
- `voidbot login --user U (--password-file FILE|--pass P)` — alias for `start`
- `voidbot register --user U (--password-file FILE|--pass P) [--email E|--no-email]` — one-shot account creation
  (REGISTER_ACCOUNT, wire opcode 2; no daemon). Requires `want_packet_register: true`
  in the active server config. Response byte 0 = created, 2 = name taken, 4 = registration disabled,
  5/6/7/8 = throttled/email/name-length/disallowed; no response = dropped by the
  2-logins-per-second throttle, retry after ~1s. Default email `<user>@voidscape.test`;
  use `--no-email` to exercise the desktop/Android launch path with no email field.
- `voidbot stop` — clean logout + shutdown
- `voidbot ping` — daemon/login liveness

For supervised or shared-host operation, use `--password-file`. It must be a regular
file with no group/other permission bits (use mode `0600` or `0400`) and contain one
non-empty UTF-8 line. The only exception is systemd's exact `LoadCredential` view:
the file may appear as `0440` inside its `CREDENTIALS_DIRECTORY` only when that
directory and the credential filesystem have systemd's protected, read-only
semantics. An ordinary `0640` file still fails closed. The wrapper forwards only the
path, so the secret is absent from daemon process arguments, logs, and JSON responses.
`--pass` remains available for interactive/backward-compatible use and is mutually
exclusive with `--password-file`.
SIGTERM and SIGINT use the same clean logout-and-close path as `voidbot stop`.
Unexpected socket loss or a server `CLOSE_CONNECTION` packet stops the daemon with exit
status 1 so `Restart=on-failure` supervisors can reconnect it.

**Running multiple instances (QA fleet)**
One daemon = one logged-in account. For parallel sessions give each instance its own
control port and account, and export the port for *every* call to that instance:

```bash
VOIDBOT_CTRL_PORT=18901 tools/voidbot/voidbot start --user qabot01 \
  --password-file /run/voidbot/qabot01.password
VOIDBOT_CTRL_PORT=18901 tools/voidbot/voidbot state position
```

Logs land at `${TMPDIR}/voidbotd-<port>.log`. The wrapper refuses to start onto a busy
port (exit 2) and the daemon aborts before login if its control bind fails. The server
exempts 127.0.0.1 from the per-IP player caps, but the 2-logins/sec throttle still
applies — stagger daemon starts by ~600ms (the throttle lifts for the host once one
admin account has logged in from it). Duplicate usernames are rejected with
ACCOUNT_LOGGEDIN, so one account per daemon. Account pool + provisioning:
`scripts/qa-provision-accounts.sh`; fleet conventions: `docs/QA-CAMPAIGN.md`.

**Actions** (real game packets)
- `voidbot goto <x> <y>` — server-pathfind walk (WORLD_WALK_REQUEST)
- `voidbot walk-step <x> <y>` — direct step (WALK_TO_POINT)
- `voidbot npc-talk (--id <npcId> | --server-index <i>)` — NPC_TALK_TO
- `voidbot npc-command (--id|--server-index) [--which 1|2]` — NPC_COMMAND1/2
- `voidbot attack-npc (--id|--server-index)` — WALK_TO_ENTITY followed by NPC_ATTACK1, matching the stock client's entity-click ordering
- `voidbot attack-player --server-index <i>` — PLAYER_ATTACK
- `voidbot follow-player <player-server-index>` — PLAYER_FOLLOW
- `voidbot friend-add <player-name>` — SOCIAL_ADD_FRIEND; quote names containing spaces
- `voidbot combat-style <controlled|aggressive|accurate|defensive>` — COMBAT_STYLE_CHANGED; valid during a duel
- `voidbot prayer-on <prayer-id 0-14>` / `prayer-off <prayer-id 0-14>` — PRAYER_ACTIVATED / PRAYER_DEACTIVATED; `14` is Protect from Magic
- `voidbot duel-request <player-server-index>` — PLAYER_DUEL; both nearby players request one another to open the stake screen
- `voidbot duel-offer [<catalogId>:<amount>[:noted] ...]` — DUEL_OFFER_ITEM full replacement, up to 8 rows; no rows clears the offer
- `voidbot duel-settings [--no-retreat] [--no-magic] [--no-prayer] [--no-weapons]` — replace all four duel-rule flags
- `voidbot duel-accept` / `duel-confirm` / `duel-decline` — first-screen accept, confirmation-screen accept, or decline
- `voidbot arena-challenge <player-server-index>` — send or reciprocate a Void Arena Death Match challenge
- `voidbot arena-rules [--ranked] [--f2p-only] [--prayer] [--ranged] [--magic]` — replace the complete rule mask; ranked implies every toggle, while no flags selects unrestricted unranked
- `voidbot arena-accept` / `arena-confirm` — accept the setup or its confirmation phase using the server-confirmed opponent
- `voidbot arena-decline [player-server-index]` — decline an active setup, or cancel an outgoing challenge when its target index is supplied
- `voidbot trade-request <player-server-index>` — PLAYER_INIT_TRADE_REQUEST
- `voidbot take-item <x> <y> <itemId>` — GROUND_ITEM_TAKE
- `voidbot object-action <x> <y> [1|2] [--id <objectId>]` — OBJECT_COMMAND1/2; `--id` calculates the object-edge prewalk and sends `WALK_TO_ENTITY` before the command, matching the real client and exercising gathering input pairing
- `voidbot cast-player <spellId> <player-server-index>` — PLAYER_CAST_PVP
- `voidbot cast-npc <spellId> <npc-server-index>` — CAST_ON_NPC
- `voidbot cast-object <spellId> <x> <y>` — CAST_ON_SCENERY
- `voidbot use-on-item <slot1> <slot2>` — ITEM_USE_ITEM (combine two inventory items: herblaw, fletching, gem cutting, potion mixing)
- `voidbot use-item-on-object <x> <y> <slot>` — USE_ITEM_ON_SCENERY (item on scenery: smelt ore on furnace, smith bar on anvil, cook on a range, craft on a wheel; prewalks to the object)
- `voidbot use-item-on-ground <slot> <x> <y> <groundItemId>` — GROUND_ITEM_USE_ITEM (item on a ground item: light dropped logs with a tinderbox)
- `voidbot use-item-on-npc <npc-server-index> <slot>` — NPC_USE_ITEM (item on an NPC: quests, cert/note exchange)
- `voidbot use-item-on-player <player-server-index> <slot>` — PLAYER_USE_ITEM
- `voidbot drop <slot> [--amount N]` — ITEM_DROP
- `voidbot item-command <slot> [--amount N] [--command K]` — ITEM_COMMAND (eat/redeem/...)
- `voidbot equip <slot>` / `voidbot unequip <slot>`
- `voidbot bank-withdraw --id <id> --amount <n> [--noted]` / `bank-deposit --id --amount` / `bank-deposit-all` / `bank-close`
- `voidbot shop-sell <catalog-id> <stock> <amount>` / `shop-close` — SHOP_SELL / SHOP_CLOSE; `stock` is the shop's currently displayed stock count
- `voidbot menu-reply <index>` / `voidbot menu-cancel` — QUESTION_DIALOG_ANSWER
- `voidbot input-reply --text "<s>"` — INTERFACE_OPTIONS sub-option 9 (server input box)
- `voidbot say <message>` — CHAT_MESSAGE
- `voidbot admin "<::cmd>"` — server admin command (COMMAND opcode)
- `voidbot logout`

**Queries** — `voidbot state <section>` where section ∈
`all | appearance | position | inventory | skills | prayers | friends | players | npcs | recent-npcs | ground-items | bank | dialog | shop | duel | arena |
combat-style | combat-events | messages | world-walk-route`

`state appearance` is `{"appearance":{"open":true|false}}`; it becomes true only
after the server sends the character-design screen opcode and false after
`design-character` submits it.

`state position` includes the logged-in bot's own `server_index`. This is the target ID a
second daemon passes to player-targeted actions such as `duel-request`.
`state prayers` is server-authoritative: `states` is `null` until the first
`SET_PRAYERS` packet, then a 15-element boolean list, while `active` lists its enabled
IDs. Sending `prayer-on` or `prayer-off` does not optimistically alter this state.
`state friends` contains the server-confirmed incremental friend list: current/former
name, online and same-world flags, raw status, and world name when visible.
`state players` returns the other players currently in the bot's area-of-interest, in
server order. Each record has `server_index`, `name`, absolute `x`/`y`, `sprite`, and an
`appearance` object (or `null` until the separate appearance update arrives). Appearance
includes worn sprite IDs, colours, combat level, overhead type, clan, visibility flags,
group/icon, title/tier, hair style, and honorific/tier. The bot itself is intentionally excluded.
`state npcs` remains the exact current NPC area-of-interest frame. `state recent-npcs`
returns the daemon's NPCs seen within the existing three-second smoothing horizon, and
`state all` exposes the same list as `recent_npcs`; safety callers should use this view
when one dropped frame must not be interpreted as proof that a corridor is clear.
`state duel` exposes the server-confirmed phase, opponent server index, opponent's echoed
stake, echoed rules, and both first-screen acceptance flags.
`state arena` decodes the stock client's hidden `@vsarena@` controls. It exposes the
server-confirmed opponent, effective rule mask, both players' accept/confirm flags,
ranked availability, and phase (`none`, `setup`, `confirm`, `closed`, `countdown`,
`started`, or `ended`). `started` is derived from the server-supplied countdown deadline;
`ended` requires a close after countdown/start, so setup cancellation cannot look like a
completed match.
`state combat-events` retains the latest 256 decoded NPC combat observations. Every row
has the same daemon-local `seq`, wall-clock `t`, and fields as its corresponding entry in
the full `events` stream, making short Sir Charles actions observable after the fact.

For proof-eligible stake duels, the daemon privately consumes the hidden
`@vsduelproof@v1|context|...` stream before the commit challenge. It requires sequential
160-byte canonical chunks within fixed count/byte caps, validates the VSDPCTX3 versions
and committed positive combat levels,
proof id and participant/container structure, and checks the length-delimited context hash
before generating its client seed. Context chunks and seeds are never exposed through
state/events and are zeroed on rejection, abort, reset, or proof replacement.

**Waits** — `voidbot wait <condition> [--timeout N] [args]`, conditions:
`logged-in | appearance-open | friend-present --name NAME | position --x --y | near --x --y --radius | inventory-contains --id --amount |
inventory-lacks --id | message --regex | dialog-open | input-open | bank-open |
npc-present --id | npc-dead (--id|--server_index) | ground-item --id | xp-gained --skill |
player-present (--name NAME|--server-index N) |
player-absent (--name NAME|--server-index N) |
player-near (--name NAME|--server-index N) [--radius N] |
duel-open | duel-confirm | duel-started | duel-ended |
arena-setup | arena-confirm | arena-started | arena-ended`

Arena waits are state-backed: `arena-setup` and `arena-confirm` match their exact setup
phases, `arena-started` also matches an already-ended match so a short fight cannot be
missed, and `arena-ended` matches only a match that passed through countdown/start.

Player-name matching is case-insensitive and exact. `player-near` defaults to radius 1
and uses the same Manhattan tile distance as the existing `near` wait. Player waits may
also select by server index when identity has not arrived yet. `player-absent` waits for
the first complete player AOI frame; name-based absence also waits until every current
nearby-player identity has arrived, avoiding a false match during packet ordering gaps.

`duel-ended` is definitive on both sides: the losing daemon observes the server DEATH
packet and the winner observes the server's `You have defeated ...!` result message.
Both receive `duel-started` from the server's `Commencing Duel!` message.

Native social acceptance can be verified without client UI. From an observer daemon
that currently sees the target, use its `state players` server index:

```bash
tools/voidbot/voidbot follow-player <server-index>
tools/voidbot/voidbot wait message --regex '^Following Fireee$' --timeout 10
tools/voidbot/voidbot friend-add Fireee
tools/voidbot/voidbot wait friend-present --name Fireee --timeout 10
```

The follow assertion is the server's normal requester message. The friend assertion is
the normal `UPDATE_FRIEND` response, so neither command treats packet transmission alone
as proof of success.

**Events** — `voidbot events --since <seq>` (timestamped game-event log). A
`WORLD_WALK_REQUEST` response emits `world_walk_route` with monotonic daemon-local
`seq`, monotonic `t`, `ok`, `reason`, `count`, and the decoded route tiles. Callers
must compare `seq` with the value captured before their request rather than treating
a previously cached response as acknowledgement.

Each decoded `NPC_COORDS` update emits one `npc_frame` event with the current NPC
`count`, a sparse `moved` list (`server_index`, definition `id`, previous coordinates,
and new coordinates), and any `removed` server indices. While normal world updates are
flowing, consecutive `npc_frame` rows are tick-by-tick movement evidence. To assert that
a visible NPC stayed still, require that its server index appears in neither `moved` nor
`removed`; for example, a two-tick recovery is two consecutive such frames before its
next movement row.

NPC overhead chat (SEND_UPDATE_NPC type 1, wire 104) is decoded: each line emits an
`npc_say` event (`server_index`, `text`, `recipient`) and is mirrored into
`state messages` with `type: "npc"` and `npc_index`, so `wait message --regex` can
assert NPC dialogue (e.g. denial lines outside menu dialogs). NPC damage types 2/10 emit
`npc_damage` (`damage`, current/maximum Hits, plus type-10 attacker feedback);
projectile types 3/4 emit `npc_projectile` (source NPC, target type/index, projectile ID);
and action-bubble type 7 emits `npc_action_bubble` (NPC and item ID). NPC definition IDs
are included when the source/target is in the exact or three-second smoothed AOI cache.
These events are also mirrored into the bounded `state combat-events` window. Skull and
wield updates remain fixed-size skips.

Exit codes: `0` ok/matched, `1` not-ok/timeout, `2` usage/connection error. Every command prints one JSON object.

## Known limitation

The bit-packed NPC area-of-interest stream (NPC_COORDS) is decoded incrementally;
under rapid region changes it can occasionally drop a single NPC frame. The daemon
mitigates this with a 3-second "recently seen" NPC cache exposed as `state recent-npcs`
and `state all.recent_npcs`; callers needing a freshly-spawned NPC should poll
`wait npc-present` (retry the spawn if it times out).
Tracking a specific NPC by `--server-index` is reliable; tracking by `--id` across
respawns is best-effort.

Nearby-player coordinates are also a bit-packed incremental stream, keyed by the prior
frame's ordered AOI list. Voidbot retains identity across movement and remove-then-readd
frames and applies the separately delivered appearance update in either packet order.
Names can briefly be `null` immediately after a player first enters view; use
`wait player-present --name ...` when identity is required.

## Protocol (validated)

- Custom client is the server's `authenticClient == -1` path: **plaintext framing, no
  ISAAC, no opcode rotation, RSA only at login.**
- Two connections: a throwaway one fetches server configs (RSA exponent `65537` +
  512-bit modulus as `\n`-terminated decimal strings), then a fresh one for login +
  the session.
- **C2S** `[u16 BE len][opcode][payload]`, len excludes the 2 length bytes.
- **S2C** `[u16 BE len][opcode][payload]`, len **includes** the 2 length bytes; the
  login response is a single raw (unframed) byte (`& 0x40` ⇒ success).
- LOGIN body: `reconnect:u8 | clientVersion:u32(10139, env VOIDBOT_CLIENT_VERSION) | username\n | encVer:u8(1) |
  u16 rsaPwLen + RSA(addCharacters(pw,20)+"\n") | u16 rsaDetailsLen + RSA("dir/jar\n") |
  UID:8 + limitations-trailer` (trailer constant for this client build).
- `addCharacters(pw,20)` preserves ASCII letters and digits, maps every other input character to `_`, and pads with spaces to exactly 20 ASCII bytes.
- Outbound opcode values: `Client_Base/src/orsc/net/Opcodes.java` `Out`. Inbound names:
  `Client_Base/src/orsc/PacketHandler.java`. Payload field order (custom dialect):
  `server/src/com/openrsc/server/net/rsc/parsers/impl/PayloadCustomParser.java`.
- Re-capture with `tools/voidbot/capture-proxy.py` if `CLIENT_VERSION` changes.
