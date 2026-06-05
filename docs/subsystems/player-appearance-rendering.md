# Player appearance rendering

Voidscape inherits the OpenRSC RuneScape Classic paperdoll renderer. The server
does not render player pixels. It sends appearance animation IDs and colour
indexes; the client draws layered 2D sprite flipbooks every frame.

## Server state

`server/src/com/openrsc/server/model/PlayerAppearance.java` stores the player's
base colours and selectable body/head IDs:

- `hairColour`, `topColour`, `trouserColour`, `skinColour`
- `head`, `body`
- `getSprites()` returns the unequipped 12-slot appearance array:
  head, shirt, pants, shield, weapon, hat, body, legs, gloves, boots, amulet,
  cape.

`server/src/com/openrsc/server/model/entity/player/Player.java` stores the live
merged appearance in `wornItems`. Login initializes it from
`PlayerAppearance.getSprites()` in
`server/src/com/openrsc/server/service/PlayerService.java`, then equipment
changes call `Player.updateWornItems(...)` to replace the affected slot.

`server/src/com/openrsc/server/constants/AppearanceId.java` defines the slot
numbers. The first twelve slots are transmitted to clients:

- `0` head
- `1` shirt
- `2` pants
- `3` shield
- `4` weapon
- `5` hat
- `6` body armour
- `7` leg armour
- `8` gloves
- `9` boots
- `10` amulet
- `11` cape

## Network contract

`server/src/com/openrsc/server/GameStateUpdater.java` writes the appearance
update. Normal custom-client updates send the `wornItems` count, then each
appearance ID as a `short`, followed by hair, top, trouser, and skin colour
indexes. Retro/authentic clients receive byte-sized or converted IDs.

Changing the number, meaning, or encoding of appearance fields would be a packet
contract change and must follow `docs/subsystems/networking-protocol.md`.
Adding another head animation normally does not need a new opcode if it uses the
existing appearance ID flow.

## Client composition

`Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java` defines
client `AnimationDef` entries. Each definition has:

- animation name, such as `head1`, `head4`, `sword`
- category, such as `player` or `equipment`
- `charColour`, which decides the primary recolour mask
- `genderModel`
- `hasA` combat-facing frames
- `hasF` mirrored/special frames

`Client_Base/src/orsc/mudclient.java` assigns runtime sprite bases in
`loadEntitiesAuthentic()`. Unique animation names reserve sprite blocks; repeated
names share the same block with different recolours.

`Client_Base/src/orsc/PacketHandler.java` reads appearance updates into
`ORSCharacter.layerAnimation[]`, then stores colour indexes on the character.

`Client_Base/src/orsc/mudclient.java` draws players in `drawPlayer(...)`. The
renderer computes the current direction and animation frame, then loops through
`animDirLayer_To_CharLayer` so layers draw in the correct front/back order. For
each visible slot, it looks up the `AnimationDef`, selects the sprite frame,
applies sidecar shifts and scaling, recolours mask pixels, and draws onto the
scene.

## Mask colours

The sprite pixels are tiny palette masks, not full-colour art:

- grayscale pixels (`R == G == B`) are recoloured by the definition's
  `charColour`.
- if `charColour` is `1`, the client uses the player's hair colour.
- if `charColour` is `2`, the client uses the player's top colour.
- if `charColour` is `3`, the client uses the player's trouser colour.
- pixels where `R == 255 && G == B` are recoloured by the player's skin colour.
- archive pixel value zero is transparent.

Voidscape custom hair colours (`Void`, `Frost`, `Blood`, `Ember`, `Gold`,
`Toxic`, `Moon`, and `Coal`) normalize the default head/beard grayscale clusters
to the side-swept PNG overlay's three neutral shade values (`#5c5c5c`,
`#8a8a8a`, `#ffffff`). The client then applies the same colour transform used by
the swept overlay, so every default head and beard keeps its authentic
silhouette while matching the richer custom colours from the prototype.

The Top/Bottom selectors include the classic clothing colours plus the appended
Voidscape clothing colours. Classic clothing keeps the original grayscale
multiply path; the Voidscape clothing colours reuse the same eight colour
families with muted RGB values and a softer five-step shade ramp for shirt and
trouser grayscale masks. Trial skin tones use their own appended palette indexes
(`Dawn`, `Rose`, `Bronze`, `Umber`, `Ash`) plus a skin-specific three-shade ramp
on the `R == 255 && G == B` skin mask pixels. This is still the original RSC
paperdoll pipeline; only the palette indexes and mask-to-shade mapping changed.

AI-generated anti-aliasing or arbitrary full-colour pixels will not recolour
like authentic RSC art.

## Hairstyle implication

Vanilla RSC hair is baked into the head animation sheet. Changing hair by
redrawing a whole head is fragile because any face, neck, or sidecar drift
becomes visible when the client attaches the head to the fixed player body.

Voidscape currently ships the default/classic head workflow. The experimental
modern PNG overlay path remains in code and protocol history, but the shipped
appearance selector clamps `hairStyle` to Classic so hair colours are not tied
to one overlay shape.

Safe art workflows:

1. For authentic/default heads, extract a locked vanilla bald base, author
   transparent hair-only frames, preview the locked base plus hair on the real
   client-scale paperdoll, then bake the hair onto the base to produce a normal
   full-head frame set.
2. For a future Voidscape modern hair pass, ship neutral shaded transparent PNG
   overlays under `Client_Base/Cache/voidscape/hair/style_XX/`. The existing
   `hairColour` appearance byte can tint those overlays at draw time, but each
   shape must be explicitly approved against the default head types before the
   Style selector is re-enabled.

See `docs/recipes/add-custom-hairstyle.md` and
`tools/hairstyle-art/README.md` for the current tooling.
