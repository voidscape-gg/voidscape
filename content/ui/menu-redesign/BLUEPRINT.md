# Voidscape Menu Redesign Blueprint

## Core Direction

Voidscape UI should stay RuneScape Classic first: compact, readable, and roomy.
The new dark-fantasy assets should upgrade edges, icons, plaques, and accents
without turning panels into heavy boxed overlays.

## Transparency And Room

- Keep the classic clear-glass/tinted look for utility panels where the player is
  mostly managing state while still wanting to see the world.
- Inventory, friends/ignore, magic, and prayer should be more transparent than
  opaque. Their content areas should feel like a glass sheet over the game world
  with tasteful borders, not a solid modal.
- Stats, loot, options, and account can be more opaque because they are denser
  reading panels, but they should still avoid feeling claustrophobic.
- Do not hide large chunks of the viewport unless the interaction truly needs it.

## Panel Shape

- Prefer smaller panels with clean, narrow borders over larger nested frames.
- Avoid overnested chrome: no big outer frame plus big inner frame plus extra
  plaques around every region.
- For inventory specifically, keep the footprint close to the grid/header area:
  a clean title, a transparent grid surface, and a nice border are enough.
- Header text must fit inside the plaque with padding and should never be larger
  than the plaque visually supports.

## Implementation Rule

Do not raw-swap generated full-size assets into existing slice-sensitive cache
filenames. Fit one element at a time, test it in-game, and only keep the change
if it remains crisp, unstretched, and readable at small screen sizes.
