# Voidscape SFX replacement map

Audit date: 2026-07-15. This maps the current working tree, including uncommitted
gameplay/client changes present on that date.

## Executive result

- The source of truth is `Client_Base/Cache/audio/`.
- It contains **37 playable WAV files** and one legacy `sounds.mem` blob.
- There are **1,073 WAV copies** elsewhere in build/package/tmp output: 29 copies of
  each of the 37 names. Every copy is byte-identical to its canonical file. Only the
  37 files under `Client_Base/Cache/audio/` are tracked by git.
- There are **two active sound keys with no matching WAV**: `combat1` and
  `projectile`. Those actions are silent today.
- Strict replacement therefore means 37 generated WAVs. Complete intended coverage
  means **39 generated WAVs**: the 37 replacements plus new `combat1.wav` and
  `projectile.wav`.
- `sounds.mem` is read into an unused local variable and never reaches a playback
  API. It does not need an audio replacement.

## Playback contract

The server normally sends a lowercase sound key in `SEND_PLAY_SOUND`; the client
performs an exact `key + ".wav"` lookup. The path is:

`game action -> Player.playSound/ActionSender.sendSound -> packet 204 -> PacketHandler -> soundPlayer -> Cache/audio/<key>.wav`

Primary implementation points:

- Server gate and packet send: `server/src/com/openrsc/server/net/rsc/ActionSender.java:1653`
- Client packet dispatch: `Client_Base/src/orsc/PacketHandler.java:478`
- Client key read/play: `Client_Base/src/orsc/PacketHandler.java:2607`
- Cache directory scan: `Client_Base/src/orsc/mudclient.java:36561`
- PC WAV playback: `PC_Client/src/orsc/soundPlayer.java:11`
- Android WAV playback: `Android_Client/Open RSC Android Client/src/main/java/orsc/soundPlayer.java:18`
- Web WAV playback/filter: `Web_Client_TeaVM/src/main/java/orsc/soundPlayer.java:10`

Important behavior:

- Server-authored SFX are suppressed when `member_world` is false. The current
  `server/local.conf` and the launch-staging contract set it to true.
- The PC client can play all keys.
- Android can play all keys, with at most eight simultaneous `MediaPlayer` instances;
  it stops audio when backgrounded or in AFK mode.
- The TeaVM web client currently rejects every key except `mechanical`, `click`,
  `spellfail`, `foundgem`, and `victory`. Those five are not cracker-exclusive: any
  server action using one of those names can also be heard on web.
- Prayer toggles and the Christmas cracker also call the client player directly,
  rather than waiting for a server sound packet.
- The development command `::sound <key>` can send any arbitrary key, but playback
  still succeeds only when a matching WAV exists.

## Generation/export standard

For the safest drop-in replacement, export every asset as:

- Exact lowercase filename from the manifest below.
- RIFF WAV, uncompressed PCM signed 16-bit little-endian, 8,000 Hz, mono.
- One-shot, no loop metadata, no speech, no music bed, no long ambience.
- Near-zero leading silence so feedback lands on the triggering action.
- Short, dry tail. Repeated gathering/combat events and the cracker reel overlap
  rapidly, and Android caps concurrent sounds at eight.
- Consistent perceived loudness across the set, with no clipped samples.

All current files are 8 kHz mono. Thirty-six are PCM signed 16-bit; `mix.wav` is the
only PCM unsigned 8-bit exception. Converting the replacement for `mix.wav` to the
same signed 16-bit format as the rest is the cleaner compatible choice.

The durations below are current lengths, not hard engine limits. Treat them as good
targets unless the brief specifically calls for an ultra-short transient.

## Complete 37-file replacement manifest

### Progress, rewards, alerts, and inventory

| File | Current | Exact trigger(s) | Generation brief |
|---|---:|---|---|
| `advance.wav` | 0.755 s | Any real skill level increase on non-OpenPK progression worlds (`Skills.java:310`); Betty's retired EvilMagic gag (`BettysMagicEmporium.java:286`). | Compact bright level-up flourish, magical but readable, celebratory rise, decisive ending, no orchestral bed. |
| `click.wav` | 0.043 s | Successful equip and unequip when the request enables sound (`Equipment.java:279,348`); legacy no-weapons duel equipment removal (`PlayerDuelHandler.java:485,488`); admin best-in-slot loadout completion (`Admins.java:1696`); every throttled Christmas cracker reel card tick (`mudclient.java:8656`). | Extremely short, dry tactile tick/click. Must remain pleasant under rapid repetition; no tail or low bass. |
| `coins.wav` | 0.770 s | Completed shop purchase (`InterfaceShopHandler.java:200`) and shop sale (`:361`). | Small handful/pouch of medieval coins exchanged, two or three bright metal clinks, compact and not jackpot-like. |
| `death.wav` | 0.997 s | Player death unless it is a scoped safe death (`Player.java:2789`). | Short ominous descending death stinger plus restrained collapse weight; final, dark, no voice. |
| `dropobject.wav` | 0.451 s | Player successfully drops any inventory item onto the ground (`DropObject.java:89`). | Generic small object set down/dropped on earth: soft thud plus tiny material rattle, neutral enough for any item. |
| `foundgem.wav` | 1.000 s | Random gem found while mining (`Mining.java:249`); Ring of Wealth produces a rare-table item (`DropTable.java:294`); Christmas cracker reveals a party hat (`mudclient.java:8664`). | Bright crystalline rare-reward sparkle/ping, clearly more special than ordinary pickup, compact and non-musical. |
| `takeobject.wav` | 0.080 s | Any successful ground-item pickup (`Player.java:4857`); taking a sacred Mage Arena cape (`MageArena.java:844`). | Ultra-short pickup tick with a faint cloth/object rustle; generic, crisp, no tail. |
| `underattack.wav` | 0.400 s | A player is newly put under attack and receives “You are under attack!” (`Mob.java:837`). | Urgent compact warning stinger, sharp onset, threatening but not a voice or modern alarm. |
| `victory.wav` | 0.514 s | Credited kill of essentially every normal NPC (`Npc.java:417`); PvP kill (`Player.java:2825`); Void Knight Death Match win (`DeathMatchArena.java:925`); Christmas cracker holiday-rare reveal (`mudclient.java:8662`). | Very short triumphant reward chime. It fires on routine NPC kills, so avoid a large fanfare or long tail. |

### Combat and projectiles

For all six numbered combat files, suffix `a` means **zero damage** and suffix `b`
means **damage greater than zero**. The same result is sent to both attacking and
defending players involved in the melee round.

| File | Current | Exact trigger(s) | Generation brief |
|---|---:|---|---|
| `combat1a.wav` | 0.190 s | Zero-damage melee round involving an ordinary living, non-armored NPC (`CombatEvent.java:448`). | Dry weapon swish or soft non-metal deflection, unmistakably a miss/no-damage result. |
| `combat1b.wav` | 0.161 s | Damaging melee round involving an ordinary living, non-armored NPC (`CombatEvent.java:448`). | Tight cloth/leather/flesh melee impact, stylized and non-gory, stronger than `1a`. |
| `combat2a.wav` | 0.348 s | Zero-damage PvP melee; zero-damage melee against an NPC in `ARMOR_NPCS` (`CombatEvent.java:443`); every zero-damage PK Catching Simulator training swing (`PkCatchingSimulator.java:760`); blocked Void Knight scripted damage (`VoidKnightBoss.java:299`). | Weapon glances off armor: light metallic scrape/clink, clearly no damage. |
| `combat2b.wav` | 0.527 s | Damaging PvP melee; damaging melee against an armored NPC (`CombatEvent.java:443`); damaging Void Knight scripted hit (`VoidKnightBoss.java:299`). | Firm metal-on-armor clash with a compact impact body, not an explosion. |
| `combat3a.wav` | 0.576 s | Zero-damage melee against an NPC in `UNDEAD_NPCS` (`CombatEvent.java:445`); all blocked/paralyzed contact attacks (`NpcBehavior.java:266,284`). | Hollow eerie miss/deflection with a faint bone rattle or spectral air, no damage. |
| `combat3b.wav` | 0.572 s | Damaging melee against an undead NPC (`CombatEvent.java:445`); any damaging contact attack using the separate contact-attack path (`NpcBehavior.java:284`). | Brittle bone/hollow supernatural impact, stronger than `3a`, concise and non-gory. |
| `outofammo.wav` | 0.864 s | Bow/crossbow has no ammunition (`RangeEvent.java:162`); throwing-weapon stack is exhausted (`ThrowingEvent.java:135`). | Medieval empty-action/failure cue: dry release or impotent click followed by a short downward warning tone; no firearm sound. |
| `shoot.wav` | 0.227 s | Every bow/crossbow shot (`RangeEvent.java:199`); every thrown weapon (`ThrowingEvent.java:220`); every dwarf multicannon shot (`FireCannonEvent.java:98`). | Generic fast projectile launch: sharp snap plus tiny whoosh. Avoid making it exclusively a bow, knife, or cannon. |

The exact current category arrays are:

- Undead IDs: `15, 53, 80, 178, 664` (Ghost); `41, 52, 68, 180, 214`
  (zombie); `40, 45, 46, 50, 179, 195` (skeleton); `516` (target practice
  zombie); `542` (UndeadOne); and unexpectedly `319` (named `farmer`).
- Armored IDs: `66, 189` (Black Knight), `102` (White Knight), `277` (Renegade
  knight), `322` (Knight), `323, 632, 633` (Paladin), plus the impossible ID
  `401324`.

`Constants.ARMOR_NPCS` almost certainly contains a missing comma: IDs `401` (Black
Knight titan) and `324` (Hero) exist, while `401324` does not. This audit does not
change it, but current behavior routes both real NPCs to the ordinary `combat1` pair.

### Gathering, production, food, and objects

| File | Current | Exact trigger(s) | Generation brief |
|---|---:|---|---|
| `anvil.wav` | 1.312 s | Each smithing production action after validation and before bars are consumed (`Smithing.java:356`). | Two or three compact hammer-on-anvil strikes, medieval forge, hard metal ring with a controlled tail. |
| `chisel.wav` | 0.079 s | Successful gem cutting only (`Crafting.java:1356`). | Tiny precise chisel-on-gem tap/scrape, glassy stone transient, ultra-short. |
| `cooking.wav` | 1.520 s | Tutorial stove attempt; each ordinary object/range cooking attempt, whether it later cooks or burns; inedible recipe preparation; Gnome Restaurant preparation (`ObjectCooking.java:43,212,284`, `GnomeCooking.java:195`). | Generic kitchen preparation: short sizzle with utensil/dough handling, broad enough for stove cooking and recipe molding. |
| `eat.wav` | 1.238 s | Eating normal supported food, fish oil, or sweetened fruit (`Eating.java:43,237,262`). | One clean bite, brief chew, subtle swallow; appetizing but stylized, no human vocalization. |
| `filljug.wav` | 0.840 s | Filling/refilling a supported empty container at its water source (`Refill.java:62`). | Water pouring into ceramic/glass container with a short rising fill resonance; no ambience. |
| `fish.wav` | 0.361 s | Start of every fishing attempt/batch cycle, before success is known (`Fishing.java:187`). | Quick line/net cast and small water splash. It represents attempting, not catching a fish. |
| `mechanical.wav` | 0.803 s | Spinning wheel production (`SpinningWheel.java:83`); operating a grain hopper even when empty (`Hopper.java:41`); Christmas cracker reel startup (`mudclient.java:8646`). | Compact wood-and-metal mechanism/ratchet spin, neutral enough for wheel, hopper, and prize reel. |
| `mine.wav` | 0.225 s | Every standard or gem-rock pick swing, including depleted/empty rock attempts and special crystal rock; every custom raw rune-stone batch action (`Mining.java:103,196,225`, `GemMining.java:102,128`, `RawRuneStone.java:44`). | Single hard pickaxe-on-stone strike, short stone chip, no success chime. |
| `mix.wav` | 1.133 s | Herb into water, secondary into unfinished potion, and other potion completion (`Herblaw.java:476,569,687`); unlocked Digsite panning action (`Panning.java:45`). | Liquid swirl/pour with vessel movement and a little water wash, broad enough for potion mixing and a panning tray. |
| `potato.wav` | 0.109 s | Every crop pickup for wheat, potatoes, and flax (`Pick.java:25-52`). | Very short plant pluck/root pull with leaf or stalk rustle. Do not make it potato-specific. |
| `prospect.wav` | 0.450 s | Prospect/examine special crystal rock, ordinary ore rock, or gem rock (`Mining.java:115,159`, `GemMining.java:73`). | Light stone tap/scrape inspection, more exploratory and softer than the `mine` impact. |

### Doors, movement, prayer, and magic

| File | Current | Exact trigger(s) | Generation brief |
|---|---:|---|---|
| `closedoor.wav` | 0.438 s | Standard DoorAction closes any door or gate through either replacement helper (`DoorAction.java:1470,1480`). | Generic medieval door/gate closing: brief creak into a solid wooden/metal latch clunk. |
| `opendoor.wav` | 0.813 s | Auto-opening route obstacles (`AutoOpenRouteObstacle.java:225`); standard DoorAction opens; generic plugin `doDoor`/`doGate` helpers (`Functions.java:1734,1840`); Fight Arena cell; two-part Sheep Herder gate sequence (`FightArena.java:602`, `SheepHerder.java:238,243`). | Generic medieval door/gate opening, hinge creak plus latch movement; works for wood and metal, not oversized. |
| `prayeroff.wav` | 0.150 s | Player clicks an active prayer off (`mudclient.java:22534`); server-forced prayer state changes from on to off (`PacketHandler.java:2352`). Members-mode clients only. | Ultra-short paired toggle-off tone: muted descending shimmer, immediately readable. |
| `prayeron.wav` | 0.150 s | Player clicks an inactive prayer on (`mudclient.java:22524`); server-forced prayer state changes from off to on (`PacketHandler.java:2349`). Members-mode clients only. | Ultra-short paired toggle-on tone: clean ascending sacred shimmer, complementary to `prayeroff`. |
| `recharge.wav` | 0.500 s | Recharging Prayer at an altar (`Prayer.java:26`); drinking from the Void Enclave healing pool when Hits need restoration (`VoidEnclaveHealingPool.java:44`). | Brief restorative magical/water shimmer with a gentle upward resolution; sacred and healing, not a level-up fanfare. |
| `retreat.wav` | 0.500 s | Player flees combat by walking (heard by runner and, in PvP, opponent: `WalkRequest.java:76,90`); scripted auto-walk retreat (`AutoWalkEvent.java:177`); NPC retreats from a player (`NpcBehavior.java:400`). | Quick escape/withdrawal cue, light rushing movement plus a short downward signal; readable to both runner and opponent. |
| `secretdoor.wav` | 0.941 s | Odd-looking wall secret passage (`DoorAction.java:747`); Heroes' Quest strange panel (`HerosQuest.java:607`); Clock Tower secret door (`ClockTower.java:379`). | Hidden stone/wood panel grinding or sliding open, mysterious mechanism, under one second. |
| `spellfail.wav` | 0.663 s | Combat spell accuracy roll fails and starts the failure cooldown (`SpellHandler.java:248`); Christmas cracker resolves to its ordinary/empty category (`mudclient.java:8666`). | Magical sputter/fizzle with a short descending disappointment tail; works for both failed spell and empty prize reveal. |
| `spellok.wav` | 0.687 s | Generic successful spell finalization, including utility spells (`SpellHandler.java:718`); elemental orb charged (`:2106`); hostile Void Knight siphon and fire/void spell launch (`VoidKnightBoss.java:249,280`). | Neutral magical cast/release burst, arcane energy rather than celebratory success, because hostile boss spells share it. |

## Missing sound keys: two additional files for complete coverage

These are live calls, but exact lookup fails because no files exist.

| Proposed file | Silent trigger today | Generation brief |
|---|---|---|
| `combat1.wav` | Successful cutting of a web (`CutWeb.java:55`); hitting either training dummy (`Dummy.java:27,52`). | Generic single weapon impact against cloth/wood/web, around 0.15-0.25 s. Alternatively change those calls to the existing `combat1b` key instead of creating a seventh combat asset. |
| `projectile.wav` | Throwing holy water at Ungadulu in Legends' Quest (`LegendsQuestHolyWater.java:98`). | Small glass vial throw/release with a short airborne whoosh, around 0.25-0.45 s. Do not reuse the broad cannon/bow `shoot` identity unless the code call is intentionally changed. |

## Legacy and delivery hazards

### `sounds.mem` is not audible

`Client_Base/src/orsc/mudclient.java:36572` calls `unpackData(...)` and assigns the
result to a method-local `soundData` variable. Nothing reads that variable. The PC,
Android, and web players all use individual WAVs. Keep `sounds.mem` unchanged for a
strict asset-only replacement, or remove it only as a separate code/cache cleanup.

### Cache manifest is incomplete

The current `Client_Base/Cache/MD5.SUM` includes only 27 of the 37 WAVs. It is missing:

`chisel.wav`, `click.wav`, `closedoor.wav`, `coins.wav`, `combat1a.wav`,
`combat1b.wav`, `combat2a.wav`, `combat2b.wav`, `combat3a.wav`, and `combat3b.wav`.

When shipping replacements, regenerate/update all audio entries in `MD5.SUM`, not
only the 27 already listed. Otherwise remote cache reconciliation can miss new or
changed files even though local/package builds contain them.

### Replace only the source-of-truth directory

Do not hand-edit the 28 derived copies of every asset. Replace
`Client_Base/Cache/audio/*.wav`, update `Client_Base/Cache/MD5.SUM`, then rebuild or
repackage:

- Android's `syncVoidscapeCache` copies all of `Client_Base/Cache` into generated APK
  assets (`Android_Client/Open RSC Android Client/build.gradle:29`).
- TeaVM's Maven resource step copies it to the web `Cache/` directory
  (`Web_Client_TeaVM/pom.xml:96`).
- The Voidscape launcher seeds the same cache tree
  (`PC_Launcher/src/main/java/launcher/Voidscape/VoidscapeUpdater.java:960`).

## Recommended generation batch

Generate the files as four coherent families so shared actions feel related:

1. **Combat family (10 current + 2 missing):** six numbered melee outcomes,
   `shoot`, `outofammo`, `underattack`, `death`, plus optional `combat1` and
   `projectile`.
2. **Interaction/crafting family (15):** `anvil`, `chisel`, `click`, `coins`,
   `cooking`, `dropobject`, `eat`, `filljug`, `fish`, `mechanical`, `mine`, `mix`,
   `potato`, `prospect`, `takeobject`.
3. **World/magic family (9):** `closedoor`, `opendoor`, `prayeroff`, `prayeron`,
   `recharge`, `retreat`, `secretdoor`, `spellok`, `spellfail` (nine names; treat
   the prayer and spell pairs together).
4. **Reward family (3):** `advance`, `foundgem`, `victory`; keep these distinct in
   importance because `victory` fires on every routine NPC kill.

The exact production count is **37 replacement WAVs**, or **39 total WAVs** if the
two currently silent trigger keys are restored.
