# Binders
**Binders** is a lightweight utility mod and API designed for modpack and mod developers who want to highlight important keybinds to players. No more confusion or hunting through menus to figure out a specific keybind. Binders makes critical keybinds stand out!

<!-- Discord CDN links expire; keep the screenshot in the repo instead (drop it at docs/screenshot.png) -->
![Binders overlay screenshot](docs/screenshot.png)

## Features
📌 **On-Screen Keybind Highlights**
Show keybinds directly on the screen with customizable icons, labels, and locations.

🧩 **Fully Configurable**
JSON configuration support to control what keybinds are shown, when, and how. Supports regex matching for advanced users.

🎮 **Context-Aware Display**
Make keybinds appear only during specific situations (e.g., when flying, while holding a specific item, in the Nether, etc.).

🎨 **Custom Styling**
Tweak the appearance with colors, scaling, and animations to match your modpack’s theme.

🤝 **No Conflict Integration**
If [No Conflict](https://github.com/Camawama) is installed, Binders shows the real physical key for conflicted keybinds that No Conflict manages (instead of "Not Bound"), including how they're reached: `[G]` for the tap selection, `[Hold G]` for the hold selection, `[G (menu)]` when the action is picked from the radial menu.

🔄 **Dynamic Updates**
Automatically responds to key remapping, so your on-screen display always stays accurate.

⌨️ **Utility Command**
Use `/binders dump` to log all available keybind IDs to `latest.log` for easy reference.
Use `/binders reload` to reload your config without restarting the game.

## Configuration Guide
Binders are defined in `config/binders/binders_definitions.json`.

### Example Definition
```json
{
  "keyBinding": "key.jump",
  "label": "Jump",
  "iconItem": "minecraft:feather",
  "context": "always",
  "contextLogic": "OR",
  "color": "#FFFFFF",
  "scale": 1.0,
  "showLabel": true,
  "maxPresses": -1,
  "resetOnLog": false,
  "isRegex": false,
  "dynamicIcon": false
}
```

Optional fields:
*   `"id"`: A stable identity for press-count stats. Without it, stats are keyed by `label`, so renaming the label resets `maxPresses` progress. Set an `id` to make stats survive label changes.
*   `"keyIsRegex"` / `"contextIsRegex"`: Fine-grained overrides for `isRegex`. The legacy `isRegex` flag applies regex matching to **both** the `keyBinding` and any item/block/entity ids inside `context`; these two let you control each independently (e.g. a regex `keyBinding` with a plain `holding:minecraft:torch` context).

### Press Counting (`maxPresses`)
When `maxPresses` is 0 or greater, the binder hides after the key has been pressed that many times. Counts persist across sessions in `config/binders/binders_stats.json` unless `"resetOnLog": true`, which counts per-session only. Presses are only counted while no GUI screen is open.

### Context System
The `context` field determines **when** a binder is visible. You can list multiple contexts separated by commas.

**Logic:**
*   `"contextLogic": "OR"` (Default): The binder shows if **ANY** of the listed contexts are true.
*   `"contextLogic": "AND"`: The binder shows only if **ALL** of the listed contexts are true.

#### Basic Contexts
*   `always`: Always visible.
*   `flying`: Visible when flying (creative or survival).
*   `creative`: Visible in Creative mode.
*   `survival`: Visible in Survival or Adventure mode.
*   `sneaking`: Visible when crouching.
*   `sprinting`: Visible when sprinting.
*   `swimming`: Visible when swimming.
*   `on_ground`: Visible when standing on solid ground.
*   `riding`: Visible when riding any entity (horse, boat, etc.).
*   `raining`: Visible when it is raining.
*   `thundering`: Visible during a thunderstorm.

#### Advanced Contexts
*   **Holding Items:**
    *   `holding:<item_id>`: Holding item in **either** hand.
    *   `holding_main:<item_id>`: Holding item in **main** hand.
    *   `holding_off:<item_id>`: Holding item in **off** hand.
*   **Wearing Armor:**
    *   `wearing_head:<item_id>`
    *   `wearing_chest:<item_id>`
    *   `wearing_legs:<item_id>`
    *   `wearing_feet:<item_id>`
*   **World & Player State:**
    *   `dimension:<dimension_id>`: e.g., `dimension:minecraft:the_nether`
    *   `biome:<biome_id>`: e.g., `biome:minecraft:plains`
    *   `health_below:<value>`: e.g., `health_below:10` (Shows when under 5 hearts).
    *   `hunger_below:<value>`: e.g., `hunger_below:6` (Shows when under 3 food shanks).
*   **Looking At:**
    *   `looking_at_block:<block_id>`: e.g., `looking_at_block:minecraft:chest`
    *   `looking_at_entity:<entity_id>`: e.g., `looking_at_entity:minecraft:villager`
    *   `looking_at_tag:<tag_id>`: e.g., `looking_at_tag:minecraft:logs`

#### Wildcards & Regex
*   You can use `*`, `any`, or `*:*` as a wildcard for items, blocks, or entities.
    *   Example: `looking_at_entity:*` (Visible when looking at ANY entity).
*   If `"isRegex": true` (or the specific `keyIsRegex`/`contextIsRegex` flags), you can use Regular Expressions for keybinding names and item/block IDs.
    *   Example: `holding:.*_sword` (Visible when holding any sword).
*   A regex `keyBinding` creates one binder per matching key. Set `"label": "{auto}"` to use each matched keybind's own translated name as the label.

### Dynamic Icons
If `"dynamicIcon": true`, the binder will try to use the item that triggered the context as the icon.
*   *Example:* If context is `holding:minecraft:diamond_sword`, the icon will become the Diamond Sword.

## Perfect For
*   **Modpack creators** who want to guide new players.
*   **Adventure maps** where specific actions are context-sensitive.
*   **Tutorials** or training scenarios.

## Compatibility
*   Minecraft Forge (1.20.1)
*   Works alongside most HUD and GUI mods.
*   **No Conflict**: first-class integration — conflicted keybinds managed by No Conflict display their real physical key and how they're triggered (tap / hold / radial menu). Requires a No Conflict version that ships `NoConflictApi`; with older versions Binders simply falls back to vanilla display.
