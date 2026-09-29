# Changelog

All notable changes to Binders are documented in this file.
Audit IDs (e.g. `2.1`) reference findings in [AUDIT.md](AUDIT.md).

## [Unreleased]

### Fixed

- A keybind that is both No Conflict's tap and hold selection for its key counted every
  press twice (No Conflict's synthetic press on key-down, then the physical press while the
  keybind was still bound). One physical press now counts once per binder.
- `mods.toml`: `displayTest` is `IGNORE_ALL_VERSION` (Binders is client-only), so neither
  side cares whether the other has it.

## [1.1.0] — 2026-07-18

Full fix pass over the 2026-07-18 codebase audit.

### Added — No Conflict integration (audit §1)

- New `NoConflictCompat` class (`client/compat/NoConflictCompat.java`) that binds to No
  Conflict's new public API via reflection, so Binders compiles and runs with or without
  No Conflict installed. If No Conflict is present but too old to have the API, Binders
  logs a warning and falls back to vanilla behavior. *(1.1, 1.4)*
- The overlay now shows the **real physical key** for keybinds No Conflict manages,
  instead of "Not Bound", and indicates how the key is actually reached *(1.1, 1.3)*:
  - `[G]` — this binder's keybind is the tap selection for G
  - `[Hold G]` — it is the hold selection
  - `[G (menu)]` — it is only reachable through No Conflict's radial menu
- `mods.toml`: declared No Conflict as an optional client-side dependency with
  `ordering="AFTER"`.
- **Companion change in the No Conflict repo**: added
  `net.camacraft.noconflict.api.NoConflictApi` (`isManaged`, `getPhysicalKey`,
  `getTapSelection`, `getHoldSelection`) plus `ConflictManager.isManagedMapping` /
  `getOriginalKey` accessors. No Conflict must be rebuilt from that repo for the
  integration to activate.

### Fixed — crashes from user config errors (audit §2)

- Malformed JSON in `binders_definitions.json` or `binders_stats.json` no longer crashes
  the game at startup (Gson's unchecked `JsonSyntaxException` was previously uncaught);
  it now logs an error and loads nothing until the file is fixed. On a parse failure the
  broken file is left untouched instead of being overwritten. *(2.1)*
- Duplicate binder IDs (same keybinding + label twice, or overlapping regex definitions)
  no longer throw during startup; the duplicate definition is logged and skipped.
  `BinderRegistry.register` now returns `boolean` instead of throwing. *(2.2)*
- An invalid `iconItem` id (e.g. uppercase letters) no longer throws
  `ResourceLocationException` from the render loop every frame; icons are resolved once
  at load time with a logged warning, falling back to no icon. Unknown item ids are also
  reported at load instead of failing silently. *(2.3)*
- The `biome:` context no longer crashes the render thread for direct (inline datapack)
  biome holders (`Optional.get()` on an unwrapped key). *(2.4)*
- `/binders reload` after deleting the definitions file no longer duplicates the default
  definitions (the in-memory list is now reset at the start of every load). *(2.5)*
- The overlay guards each binder's `shouldShow` — a throwing context is logged once and
  that binder is skipped, instead of crashing the render thread. *(2.5 partial hardening)*
- Stray `null` entries in the definitions JSON array are filtered instead of causing an
  NPE during sanitization. *(2.6)*

### Fixed — input counting (audit §3)

- Mouse presses are now counted from `InputEvent.MouseButton.Post` instead of `.Pre`, so
  clicks canceled by other mods aren't counted, and No Conflict's synthetic mouse events
  for resolved conflicts are. *(3.1, 1.2)*
- Press counting now goes through `isActiveAndMatches`, which respects Forge key
  modifiers — a keybind bound to SHIFT+G is no longer counted when plain G is pressed.
  *(3.3)*
- Press-count stats are no longer written to disk on every keypress. Counts are marked
  dirty and flushed every 30 seconds, on logout, and before any reload re-reads the
  file. *(3.2)*
- Binders with unlimited presses (`maxPresses: -1`) no longer track (or persist) press
  counts at all — new `IBinder.shouldTrackPresses()` hook. *(3.2)*
- Orphaned entries in `binders_stats.json` (from deleted/renamed JSON binders) are pruned
  on load. Note: counts for regex binders whose target mod is temporarily uninstalled are
  also pruned. *(3.5)*
- New optional `"id"` field on definitions gives stats a stable identity, so renaming a
  binder's `label` no longer resets its `maxPresses` progress. *(3.5)*

### Fixed — overlay rendering (audit §4)

- Fade-ins with EASE_IN easings no longer flash fully opaque for their first frames
  (vanilla's font renderer treats alpha < 4 as opaque; those frames are now skipped).
  *(4.1)*
- `EASE_IN_BACK`'s negative overshoot no longer mirror-flips the icon (icon scale is
  clamped at 0; the >1 "pop" of EASE_OUT_BACK/ELASTIC is kept intentionally). *(4.2)*
- Fading entries now reserve height proportional to their animation progress, so the
  list slides smoothly instead of showing invisible gaps that snap shut. *(4.3)*
- `CENTER_LEFT` / `CENTER_RIGHT` anchors now actually center the list vertically instead
  of hanging it below the center line. *(4.4)*
- Animation state and cached icons are cleared on `/binders reload`; cached icons are now
  defensive copies instead of live references into the player's inventory. *(4.5)*

### Changed — API (audit §5)

- `/binders reload` no longer wipes binders registered programmatically by other mods:
  the JSON loader now tracks and unregisters only its own binders
  (`BinderRegistry.unregister(id)` is new). *(5.1)*
- New `keyIsRegex` / `contextIsRegex` definition fields split the double duty of
  `isRegex` (which applied regex matching to both the keybinding name *and* context
  item/block/entity ids). `isRegex` keeps its legacy meaning as the fallback for both.
  *(5.2)*
- `IBinder.getColor()` default is now `0xFFFFFFFF` (opaque white in ARGB) instead of
  `0xFFFFFF` (zero alpha), and the format is documented. *(5.3)*
- `BinderManager.load()` now runs via `event.enqueueWork(...)` on the main thread instead
  of directly inside the parallel-dispatched `FMLClientSetupEvent`. *(5.4)*

### Changed — performance (audit §6)

- Context and keybinding regex patterns are compiled once and cached instead of being
  recompiled every frame per binder; invalid patterns are logged once instead of at
  60 Hz. *(6.1)*
- Binder colors and context lists are parsed once at load instead of every frame.
- `getKeyMapping()` resolves and caches the key mapping instead of linear-scanning all
  registered key mappings on every render/input event. *(6.2)*

### Changed — config & UX (audit §7)

- The "Binders Overlay" toggle button in the Controls screen is anchored to the right
  edge; it was previously clipped off-screen at common GUI scales (e.g. 1920×1080 at
  auto scale). *(7.1)*
- Added `assets/binders/lang/en_us.json`; the toggle button, No Conflict key hints, and
  all `/binders` command feedback are now translatable. *(7.2)*
- Removed dead code: empty `Config.onLoad` handler, no-op `MinecraftForge.EVENT_BUS`
  instance registration, log-only `commonSetup`, stale comments. *(7.3)*

### Changed — project hygiene (audit §8)

- Removed the manual `DisplayTest` registration with the nonstandard `"CLIENT"` marker;
  client-only signaling is handled solely by `displayTest="IGNORE_SERVER_VERSION"` in
  mods.toml (the manual one overrode the TOML's canonical `IGNORESERVERONLY` marker).
  *(8.1)*
- Replaced `LICENSE.txt` (which was Forge's LGPL 2.1 notice from the MDK) with the MIT
  license actually declared in `gradle.properties`. *(8.2)*
- Deleted Forge MDK leftovers: `changelog.txt` (Forge's own 996-line changelog — this
  file replaces it) and `CREDITS.txt` (Forge's credits). *(8.3)*
- README: replaced the expiring Discord CDN screenshot link with a repo-relative path
  (**action needed:** drop the screenshot at `docs/screenshot.png`). *(8.4)*
- README: removed the nonexistent "fonts" styling claim; documented `{auto}` labels, the
  `id` / `keyIsRegex` / `contextIsRegex` fields, press-count persistence and
  `binders_stats.json`, and the No Conflict integration. *(8.6)*
- Version bumped to 1.1.0.

### Intentionally not changed

- `mods.toml` still lacks `issueTrackerURL` / `displayURL` / `logoFile` — needs a real
  URL and logo art from the author. *(8.5)*
- The `forge`/`minecraft` dependency entries keep `side="BOTH"` (harmless, and the MDK
  default). *(8.5)*
- Presses made while a GUI screen is open are still not counted (documented in the
  README instead; keybinds with GUI conflict contexts would need a different tracking
  path). *(3.4)*

## [1.0.1] and earlier

- Pre-audit releases: on-screen keybind overlay with JSON-defined binders, context
  system (player state, held/worn items, looking-at, dimension/biome/health/hunger),
  regex keybinding matching, dynamic icons, press limits with persistent stats,
  configurable anchor/offset/easing, `/binders dump` and `/binders reload` commands,
  and a Controls-screen toggle button.
