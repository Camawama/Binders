# Binders — Full Codebase Audit

**Date:** 2026-07-18
**Scope:** All source under `src/main/java`, resources, build scripts (Forge 1.20.1, mod version 1.0.1), plus a compatibility analysis against **No Conflict** (`F:\GITHUB\NO CONFLICT\noconflict`).

Severity legend (same convention as No Conflict's AUDIT.md):
- 🔴 **Critical** — actively causes wrong behavior or a crash in common paths
- 🟠 **Major** — broken edge case, design gap, or compat hazard
- 🟡 **Minor** — polish, robustness, dead code, cosmetics

---

## Resolution status (fix pass, 2026-07-18)

**Fixed:** §1 (No Conflict API added to that repo; `NoConflictCompat` + display/counting fixes here),
2.1–2.6, 3.1–3.3, 3.5 (optional `id` field + pruning), 4.1–4.5, 5.1–5.4, 6.1, 6.2, 7.1–7.3,
8.1–8.4, 8.6. See [changelog.md](changelog.md) for the per-change history.

**Intentionally not changed:**
- 3.4: GUI presses still aren't counted — documented in the README instead.
- 8.4 (partial): the README now points at `docs/screenshot.png`; the screenshot file itself
  must be added by the author (the old Discord CDN link had already expired).
- 8.5: `issueTrackerURL`/`displayURL`/`logoFile` need a real URL and art from the author;
  dependency `side="BOTH"` kept (MDK default, harmless).
- Counting followed the "rely on synthetic events" variant of the 1.4 design rather than
  querying tap/hold selections on raw input: No Conflict's synthetic PRESS events represent
  actual activations, so counting them (plus the mouse Pre→Post switch) is both simpler and
  more accurate than counting raw presses that may turn into holds or radial-menu opens.

---

## 1. No Conflict compatibility (requested feature)

### How No Conflict breaks Binders today

No Conflict's core mechanism (`ConflictManager.applyResolutions`) sets every *conflicted* `KeyMapping` to `InputConstants.UNKNOWN` and re-routes input itself via mixins, synthetic events, and a radial menu. Binders reads `KeyMapping` state directly, so the two mods interact badly on exactly the keybinds a modpack dev most wants to highlight (the conflicted ones):

### 🔴 1.1 Overlay shows "Not Bound" for every No Conflict–managed keybind

`KeybindOverlay.renderBinderEntry` (`KeybindOverlay.java:152`) calls `keyMapping.getTranslatedKeyMessage()`. For any mapping No Conflict manages, the key is `UNKNOWN`, so the overlay renders `[Not Bound]` even though the key absolutely works (via No Conflict's tap/hold/radial routing). This makes Binders actively misleading in packs that ship both mods.

### 🔴 1.2 Press counting (`maxPresses`) is unreliable under No Conflict

`InputHandler.checkBinders` (`InputHandler.java:43`) matches via `keyMapping.matches(keyCode, -1)` / `matchesMouse(...)`:

- **Real presses of a managed key never match** — the mapping is `UNKNOWN` at the time Forge fires `InputEvent.Key`.
- **Keyboard taps are counted only by accident**: No Conflict temporarily rebinds the selected mapping and posts a *synthetic* `InputEvent.Key` (`KeyHandler.postSyntheticTap`), which Binders happens to catch. This works today but is timing/order-dependent, not a contract.
- **Mouse-bound conflicts are never counted**: No Conflict posts synthetic mouse input as `InputEvent.MouseButton.Post` (`KeyHandler.java:273`), but Binders listens to `InputEvent.MouseButton.Pre` (`InputHandler.java:26`). Synthetic mouse taps are invisible to Binders.
- Presses resolved through the radial menu (`executePendingTap`) run via `mc.tell(...)` and may occur while a screen is momentarily open, hitting the `mc.screen != null` early-return (`InputHandler.java:34`).

### 🟠 1.3 The displayed hint is wrong even when a key IS shown

If a binder points at a mapping that shares a physical key `G` but is **not** the user's tap selection, displaying `[G]` is misleading: pressing G triggers the *selected* mapping (or opens the radial menu on hold). Binders has no way to know this without talking to No Conflict.

### Recommended integration design

Since you author both mods, the clean path is a tiny public API in No Conflict plus an optional compat module in Binders:

**In No Conflict** — add `net.camacraft.noconflict.api.NoConflictApi` (thin static facade over `ConflictManager`; requires only a package-private getter for `originalKeys`):

```java
public final class NoConflictApi {
    /** True if No Conflict has taken over this mapping (its KeyMapping key is forced UNKNOWN). */
    public static boolean isManaged(KeyMapping mapping);
    /** The real physical key the user has bound, or null. Covers suppressedConflicts too. */
    public static InputConstants.Key getPhysicalKey(KeyMapping mapping);
    public static @Nullable KeyMapping getTapSelection(InputConstants.Key key);
    public static @Nullable KeyMapping getHoldSelection(InputConstants.Key key);
}
```

**In Binders:**

1. **Optional dependency**, never hard: gate everything behind `ModList.get().isLoaded("noconflict")` and isolate all No Conflict imports in a single `NoConflictCompat` class that is only classloaded when the mod is present (holder/indirection pattern). Add to `mods.toml`:
   ```toml
   [[dependencies.binders]]
       modId="noconflict"
       mandatory=false
       ordering="AFTER"
       side="CLIENT"
   ```
2. **Build setup**: `compileOnly fg.deobf(...)` against the No Conflict jar (flatDir `libs/` repo is simplest given both projects are local; CurseMaven once published).
3. **Fix the display** (`KeybindOverlay.renderBinderEntry`): when `keyMapping.getKey() == UNKNOWN` and compat is active, render `NoConflictApi.getPhysicalKey(mapping)`'s display name instead of "Not Bound". When the mapping is managed but is *not* the tap selection for that physical key, annotate it (e.g. `[G ⟳]` or a configurable suffix like `"(hold G)"`) so the player knows it goes through No Conflict's hold/radial flow.
4. **Fix the counting** (`InputHandler`):
   - Switch the mouse listener from `InputEvent.MouseButton.Pre` to `.Post` — this catches both real presses and No Conflict's synthetic events with one subscription (real presses fire Post too), and stops counting clicks that other mods cancel (see 3.1).
   - For managed mappings, stop relying on `matches()` alone: on a raw press of physical key K, if compat is active and `binder.getKeyMapping()` equals `getTapSelection(K)` or `getHoldSelection(K)`, count it. Then No Conflict's synthetic events become redundant rather than load-bearing (guard against double-counting: skip the synthetic event when you already counted the raw press — e.g. tag counts by game tick + key).
5. No changes needed for regex binders — they match on `KeyMapping.getName()`, which No Conflict never alters.

---

## 2. Robustness: user config errors can crash the game

Binders' whole audience is modpack devs hand-editing JSON. Today several plausible typos crash Minecraft instead of logging.

### 🔴 2.1 Malformed JSON crashes at startup

`BinderManager.load()` (`BinderManager.java:68`) only catches `IOException`, but Gson throws the *unchecked* `JsonSyntaxException` for malformed JSON. `load()` runs inside `FMLClientSetupEvent` (`ClientSetup.java:18`), so a stray comma in `binders_definitions.json` = mod-loading error screen. Same gap in `BinderStats.load()` (`BinderStats.java:31`). Catch `Exception` (or `JsonParseException`), log, and fall back to defaults / an empty list.

### 🔴 2.2 Duplicate binder IDs crash at startup

`BinderRegistry.register` (`BinderRegistry.java:16`) throws `IllegalArgumentException` on a duplicate ID. The ID is `"json:" + key + ":" + label` (`BinderManager.java:153`), so two definitions with the same keybinding and label — or two regex definitions whose patterns overlap on a key with equal labels — throw during client setup → crash. `load()` should log-and-skip duplicates, not propagate.

### 🔴 2.3 Invalid `iconItem` string crashes the render thread every frame

`getIcon` (`BinderManager.java:500`) calls `new ResourceLocation(def.iconItem)` unguarded. An uppercase letter or space (`"Minecraft:Feather"`) throws `ResourceLocationException` from the overlay render loop → instant crash, every frame. Validate/resolve the icon **once at load time**, log a warning, and fall back to `ItemStack.EMPTY`.

### 🟠 2.4 `biome:` context can throw on the render thread

`checkSingleContext` (`BinderManager.java:259`) does `.unwrapKey().get()` — a direct (inline datapack) biome holder has no key and `Optional.get()` throws `NoSuchElementException`, uncaught all the way up through `KeybindOverlay.render`. Use `unwrapKey().map(...).orElse(false)`.

### 🟠 2.5 `/binders reload` edge cases corrupt in-memory state

- If the user deletes `binders_definitions.json` and runs `/binders reload`, the file-missing branch (`BinderManager.java:91`) **appends** defaults to the still-populated static `definitions` list → duplicate definitions → 2.2's exception. Reset `definitions = new ArrayList<>()` at the top of `load()`, not only in the parse branch.
- If registration throws mid-loop, the registry is left half-populated (the command's try/catch reports the error but doesn't restore state). Build the new registry fully, then swap.

### 🟡 2.6 Null list elements from JSON NPE the sanitize loop

`[ {...}, null ]` parses fine and the loop at `BinderManager.java:76` NPEs on `def.context`. Filter nulls after parsing.

---

## 3. Input counting correctness (independent of No Conflict)

### 🟠 3.1 Mouse presses are counted from the cancelable Pre event

`InputEvent.MouseButton.Pre` (`InputHandler.java:26`) fires before screens/mods process the click — if another mod cancels it, Binders still counts it. Move to `.Post` (also required for compat, see 1.2/1.4).

### 🟠 3.2 Every counted press writes JSON to disk synchronously

`BinderStats.incrementPressCount` (`BinderStats.java:69`) calls `save()` — a full file write — on the input path. A persistent binder on `key.jump` writes `binders_stats.json` on **every jump**. Mark dirty and flush on a timer / `LoggingOut` / `GameShuttingDownEvent` instead. Note the counter also increments (and saves) even when `maxPresses == -1`, where the count is never used — skip counting for unlimited binders unless you plan to surface stats.

### 🟡 3.3 Forge key modifiers are ignored

`keyMapping.matches(keyCode, -1)` doesn't check `KeyModifier`. A mapping bound to `SHIFT+G` is counted when plain `G` is pressed. Use `getKeyModifier().isActive(...)` alongside `matches`, or compare against `isActiveAndMatches`.

### 🟡 3.4 Presses inside GUIs are never counted

The `mc.screen != null` early-return (`InputHandler.java:34`) means keybinds with a GUI conflict context (inventory-usable keys) never accumulate presses. Probably fine for v1 — worth a comment/doc note that `maxPresses` only tracks in-world presses.

### 🟡 3.5 Stats identity is fragile and never pruned

The stats key embeds the label, so renaming a binder's label silently resets its `maxPresses` progress; removed binders leave orphan entries in `binders_stats.json` forever. Consider keying stats on `keyBinding` + a stable optional `id` field, and pruning unknown IDs on load.

---

## 4. Overlay rendering

### 🟠 4.1 Fade-in flashes fully opaque with EASE_IN easings

Vanilla's font renderer force-promotes near-zero alpha to opaque (`if ((color & 0xFC000000) == 0) color |= 0xFF000000`). The gate at `KeybindOverlay.java:133` checks the **raw** alpha (> 0.05), but an ease-in curve maps raw 0.06 to eased ≈ 0.003 → `alphaInt == 0` → the text draws **fully opaque** for the first frames, then snaps to nearly invisible. Skip drawing when `alphaInt < 4` (or clamp it to ≥ 4).

### 🟡 4.2 Back/elastic easings distort the icon

`iconScale = alpha` uses the *eased* value un-clamped (`KeybindOverlay.java:195`): `EASE_IN_BACK` goes negative (briefly mirror-flips the icon), `EASE_OUT_BACK`/`ELASTIC` overshoot past 1 (icon pops oversized — possibly intended charm). At minimum clamp scale to `max(0, ...)`.

### 🟡 4.3 Fading entries reserve layout space while invisible

Entries with `rawAlpha ≤ 0.05` aren't drawn but still advance `currentY`, so the list shows gaps during fades, and entries teleport when a fade completes. Either animate the height with the same alpha, or accept the jump and drop the invisible spacing.

### 🟡 4.4 CENTER anchors don't center the list

`CENTER_LEFT`/`CENTER_RIGHT` start at `screenHeight / 2` and stack strictly downward (`KeybindOverlay.java:105-115`), so the list hangs below center instead of being vertically centered. Offset the start by `totalHeight / 2`.

### 🟡 4.5 Per-binder caches never invalidated

`alphas` and `lastIcons` (`KeybindOverlay.java:24-26`) are never cleared on `/binders reload`, accumulating stale IDs; `lastIcons` also stores **live** player `ItemStack` references (mutating stack). Clear both maps on reload and store `stack.copy()`.

---

## 5. API design

### 🟠 5.1 `BinderManager.load()` wipes third-party binders

The README pitches Binders as "a utility mod **and API**", and `BinderRegistry.register` is public — but `load()` calls `BinderRegistry.clear()` (`BinderManager.java:65`), so any binder another mod registered programmatically is destroyed on `/binders reload` (and there is no event to re-register). Track JSON-sourced binder IDs separately and clear only those, or fire a `RegisterBindersEvent`-style callback after each load.

### 🟠 5.2 `isRegex` conflates two unrelated behaviors

One flag simultaneously means "`keyBinding` is a regex over key mapping names" **and** "item/block/entity ids inside `context` are regexes" (`BinderManager.java:289`, `:358`, `:402`). A user who wants `key\..*` with a plain `holding:minecraft:torch` context can't have it, and vice versa. Split into `keyIsRegex` / `contextIsRegex` (keeping `isRegex` as a legacy alias for both).

### 🟡 5.3 `IBinder.getColor()` default has zero alpha

`0xFFFFFF` (`IBinder.java:38`) is transparent-black-ish as ARGB; the overlay happens to mask and re-add alpha, but any other consumer of the API will render nothing. Default to `0xFFFFFFFF` and document the format.

### 🟡 5.4 Registry not built for the parallel-dispatch thread

`load()` runs directly in `FMLClientSetupEvent` (a parallel dispatch event) while touching `Minecraft.getInstance().options` and static non-concurrent maps. Wrap in `event.enqueueWork(...)` to run on the main thread.

---

## 6. Performance

### 🟠 6.1 Regex patterns recompiled every frame

`matchesItemRegex`, `isLookingAtBlock`, `isLookingAtEntity` (`BinderManager.java:335`, `:360`, `:404`) call `Pattern.compile` on every evaluation — and `shouldShow`/`getIcon` run **per binder per frame** from the overlay. Precompile patterns once per definition at load time (this also lets you report invalid regexes once, at load, instead of spamming the log at 60 Hz).

### 🟡 6.2 `getKeyMapping()` linear-scans all key mappings per frame

Non-regex `JsonBinder.getKeyMapping` (`BinderManager.java:512`) streams over `options.keyMappings` on every render and every input event. Resolve once lazily and cache (invalidate on reload); key mapping instances never change identity after registration.

---

## 7. Config & UX

### 🟠 7.1 Controls-screen toggle button renders off-screen at common GUI scales

`ControlsHandler.java:29` places the button at `screen.width / 2 + 160` with width 100. At 1920×1080 with auto GUI scale (4 → 480 scaled width) the button spans 400–500 and is clipped; at 854×480 scale 2 (427 wide) it's mostly invisible. Anchor from the right edge instead: `screen.width - 105, 10`.

### 🟡 7.2 No localization

All user-facing strings are hardcoded English literals (button label, command feedback). No Conflict already ships `assets/<modid>/lang/en_us.json`; Binders has no lang file at all. Add one and use `Component.translatable`.

### 🟡 7.3 Dead code

- `Config.onLoad` is an empty event handler (`Config.java:59`).
- `MinecraftForge.EVENT_BUS.register(this)` in `Binders.java:24` registers an instance with zero `@SubscribeEvent` methods.
- `commonSetup` only logs; `ClientSetup.java:26-29` is a stale thinking-out-loud comment block.

---

## 8. Project hygiene & metadata

### 🟠 8.1 DisplayTest is registered twice, with a nonstandard marker

`mods.toml:18` sets `displayTest="IGNORE_SERVER_VERSION"` (which generates the canonical client-only-mod display test using Forge's `IGNORESERVERONLY` magic constant), **and** `Binders.java:29` manually registers a `DisplayTest` with the arbitrary string `"CLIENT"`. The manual registration overwrites the TOML-generated one with a non-magic version string, which defeats the "this mod can be absent on the server" signaling on the server list. Delete the code registration and keep only the TOML line.

### 🟠 8.2 Repo license contradicts itself

`gradle.properties` declares `mod_license=MIT` (baked into mods.toml), but `LICENSE.txt` is Forge's LGPL 2.1 notice from the MDK. Anyone reading the repo sees LGPL; anyone reading the mod list sees MIT. Replace `LICENSE.txt` with the actual MIT text.

### 🟡 8.3 Forge MDK leftovers committed

- `changelog.txt` (996 lines) is **Forge's** changelog, not the mod's — the `.gitignore` pattern `forge*changelog.txt` missed it because of the filename. Delete it (start a real `CHANGELOG.md` if wanted; git history shows you update versions regularly).
- `CREDITS.txt` is Forge's credits file.

### 🟡 8.4 README image will die

`README.md:4` embeds a Discord CDN attachment with a signed, expiring URL (`?ex=...&is=...&hm=...`). Discord invalidates these; the screenshot will 404 on GitHub/CurseForge. Commit the image to the repo (e.g. `docs/screenshot.png`) and reference it relatively.

### 🟡 8.5 mods.toml missing optional-but-useful fields

No `issueTrackerURL`, `displayURL`, or `logoFile`. Worth adding before wider publication. The `forge`/`minecraft` dependencies could also declare `side="CLIENT"` for accuracy on a client-only mod.

### 🟡 8.6 README drift

- "Custom Styling — colors, **fonts**, and animations": there is no font customization.
- The `{auto}` label feature (`BinderManager.java:419`) is undocumented.
- `isRegex` affecting *context* matching semantics (see 5.2) is undocumented — users will assume it only applies to `keyBinding`.
- No mention of `binders_stats.json` or that `maxPresses` persists across sessions unless `resetOnLog`.

---

## Suggested priority order

1. **§2 crash-proofing** (2.1–2.3 are startup/render crashes from config typos — worst possible failure mode for a modpack-dev tool).
2. **§1 No Conflict compat** (the requested feature; do the No Conflict API + `NoConflictCompat` + display/counting fixes together).
3. 3.1/3.2 (input correctness + disk writes), 4.1 (visible flicker), 7.1 (off-screen button).
4. 5.1/5.2 (API contract), 6.1 (regex caching).
5. Everything 🟡.
