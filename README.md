# Glass Break Compat (Forge 1.20.1)

Glues **Better Glass Breaking (BGB)**, **Kick & Shatter** and **TACZ 1.2.0** together.

## What it does
- **Sprint into glass** -> Better Glass Breaking's own procedural fracture runs (a grid of impacts across the player's body). The result is BGB's real hole geometry, so it is walk-through, saved in the chunk, and synced to every player.
- Kick & Shatter's instant glass deletion for *players* is switched off (configurable). Its doors and zombie logic are untouched.
- **TACZ bullets** break glass the way arrows do in BGB. BGB already ships a TACZ adapter; this mod makes sure every glass name is covered and adds a fallback if that adapter turns itself off.
- **Name matching**: any block whose registry path contains `glass`, `pane` or `window` (and is a full cube or pane-style block without its own block entity) is treated as glass for arrows, bullets, explosions, melee and sprinting.

## Build
1. Put this folder in a GitHub repo (the three jars are already in `libs/`; they are compile-only and are NOT bundled).
2. Push. GitHub Actions builds it; download `glassbreakcompat-*.jar` from the run's artifacts.
3. Drop the jar in `mods/` next to BGB, Kick & Shatter and TACZ (client and server).

## Config (`glassbreakcompat-common.toml`)
`impactEnergy`, `impactColumns`, `impactRows` control how much a sprint takes out; `minSpeed`, `cooldownTicks` control triggering; `keywords` / `excludeKeywords` control name matching.

## Also check in BGB's own config
- `healing = false` or broken glass repairs itself.
- `taczCompat = true`.
