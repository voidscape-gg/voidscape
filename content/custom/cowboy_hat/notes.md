# Cowboy hat

## Decisions

- Item ID `1609`; inventory sprite `638` at archive entry `2788`.
- Appearance ID `245`; client animation index `244`.
- Unique `cowboyhat` worn block uses archive entries `1890..1907`.
- The hat is tradable, non-stackable, noteable, zero-stat, and unisex. OpenRSC
  derives noteability for every tradable, non-stackable item, so both raw
  client/server flags intentionally state the resulting behavior.
- Image generation supplies directional source material only. Production
  sprites retain the authentic wizard-hat frame sizes and per-frame sidecars.
- Production pixels use hard transparency and a baked brown leather palette;
  the runtime animation has `charColour=0`.
- Launch support targets Voidscape's active `custom_sprites: false` path. The
  legacy OpenPK/Cabbage custom-sprite presets need a separate appearance and
  archive port before this item may enter their reward pools.

## Palette

- `#000001` near-black outline
- `#24150D`, `#3E2516`, `#6B4325`, `#9B6736`, `#C28B4A` leather ramp
- `#16100D` hat band
- `#D6B456` muted buckle

## Test Notes

- Verify all 18 movement/combat frames through the real client rasterizer.
- Verify inventory, equip/unequip, bank, drop/pickup, and standard tradeability.
- Portal-avatar rendering is intentionally handled in integration Slice 5.
