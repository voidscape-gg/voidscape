---
name: Player titles V2
description: Stable scope and protocol invariants for the main-loop-only title system
type: project
---

The approved title system has 65 rows: 25 Renown, 20 Feat, 7 Supreme, and 13
Unique. It is deliberately main-game-loop-only. Do not restore title rewards for
Gnomeball, Death Match Arena, Undead Siege, Void Colossus/Rift events, Void Arena
seasons, Sir Charles, stake duels, or the Wanderer unless Ryan explicitly changes
that doctrine. The removed six ids and rejected Grandmaster, Knight of the Void,
Void Arena Champion, Duelist, and Unmasker rows are not catalog content.

Players may wear one suffix epithet (`player_title_active`) and one prefix
honorific (`player_honorific_active`). The launch honorifics are Saint (Supreme,
99 Prayer + maximum quest points), Knight (Supreme, all seven combat skills at 99,
combat 123, and maximum quest points), and Warlord (Unique monthly Wilderness
office). The Court lists the reigning Warlord plus Saints and Knights with recognition
dates.

Unlocks never auto-equip. Every wear or change costs 100,000 inventory coins,
including first wear and re-wear after a free clear; choosing an already-active row
is a free no-op. The Void Herald is the only mutation surface, while `::title` and
`::titles` are browse-only. A durable per-title marker offers paid wear / `Not now`
when ordinary interactions are clear. Cohort 10137 skins that standard menu as a
Void Glass ceremony through `~vstitleaward~`; generic clients retain readable
choices. The 10137 player-appearance tail is honorific string + tier byte appended
after modern hair. Cohort 10138 adds the persisted remote-label modes Names + titles,
Names only, and Hide names + titles; the local-player label contract is unchanged.
Keep server, both client decode branches, Android, workbench, and voidbot aligned;
do not change opcode order for title work.
