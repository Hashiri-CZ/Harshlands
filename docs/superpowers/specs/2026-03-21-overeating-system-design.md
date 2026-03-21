# Overeating System Design

## Problem

The FoodExpansion nutrition system slows vanilla hunger drain (`DrainMultiplier: 0.5`), keeping the hunger bar full for extended periods. Minecraft prevents eating when hunger is at 20/20. This means players' macro nutrients (protein, carbs, fats) deplete over time but they physically cannot eat to replenish them — the core gameplay loop is broken.

## Solution

Allow players to eat food even when their hunger bar is full, with a **per-food-item satiation counter** that causes diminishing macro returns on repeated consumption of the same food. This solves the "can't eat" problem while encouraging dietary variety.

## Design

### Force-Eat When Full

When a player right-clicks while holding edible food and their hunger is at or above a configurable threshold (default: 20), vanilla blocks the eat. We intercept this:

- Listen to `PlayerInteractEvent` (right-click with food in hand, `Action.RIGHT_CLICK_AIR` or `Action.RIGHT_CLICK_BLOCK`)
- **Hand filtering**: Only process `event.getHand() == EquipmentSlot.HAND` (main hand) or `EquipmentSlot.OFF_HAND` — whichever hand holds the food. Filter on `event.getHand()` to prevent double-fire (Bukkit fires once per hand per right-click).
- Get the food item from the specific hand slot via `player.getInventory().getItem(event.getHand() == EquipmentSlot.HAND ? EquipmentSlot.HAND : EquipmentSlot.OFF_HAND)`
- If hunger >= threshold AND item is edible AND item has a nutrient profile (or default profile):
  - Check satiation — if at hard cap (0% multiplier), send blocked chat message, do NOT consume item, return
  - Use `event.setUseItemInHand(Event.Result.DENY)` to prevent vanilla item-use side effects (no need to cancel the full event — that could interfere with block placement etc.)
  - Play eat sound via `player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0f, 1.0f)`
  - Remove one food item from the correct hand slot. For stew/soup items (`MUSHROOM_STEW`, `BEETROOT_SOUP`, `RABBIT_STEW`, `SUSPICIOUS_STEW`), add an empty `BOWL` to inventory after consuming.
  - Apply macro nutrients: compute `finalMultiplier = overeatMultiplier * comfortMultiplier` and pass to `addNutrients(profile, finalMultiplier)`. No signature change needed — the existing parameter accepts any combined multiplier.
  - Increment satiation counter for that food
  - Send chat message if applicable
  - Do NOT change the hunger bar — this is a nutrition-only eat
- **Cooldown**: Enforce a 500ms cooldown per player between force-eats to prevent right-click spam exploits. Track via `Map<UUID, Long>` with `System.currentTimeMillis()`.
- **Event priority**: `EventPriority.HIGH` to run after most other interact handlers but before MONITOR. Do NOT set `ignoreCancelled = true` — per Bukkit quirks with PlayerInteractEvent, this can cause missed events.

### Special Food Items

Force-eating bypasses the vanilla consume pipeline, so special effects tied to `PlayerItemConsumeEvent` do not trigger:

- **Golden Apple / Enchanted Golden Apple**: Force-eating grants only macro nutrients, NOT absorption hearts or regeneration. This is intentional — the player already has a full hunger bar, and these items remain most powerful when eaten normally.
- **Chorus Fruit**: Force-eating grants only macro nutrients, no teleport effect. Intentional — force-eat is a nutrition mechanic.
- **Suspicious Stew**: Force-eating grants only macro nutrients, no random potion effect.
- **Honey Bottle**: Force-eating grants only macro nutrients, no poison cure. Returns a glass bottle to inventory.

These items are documented as "nutrition-only when force-eaten." Players who want the special effects should eat them normally (at lower hunger).

### Normal Eating (Hunger < Threshold)

Continues through the existing `PlayerItemConsumeEvent` handler unchanged. No satiation penalty. No changes to the existing `onPlayerConsume` method.

### Per-Food Satiation Counter

Each player tracks a `Map<String, Integer>` of food item key (Material name) to satiation count.

- **Increment**: +1 each time that specific food is eaten while full (via the force-eat path)
- **Decay**: A single global timer registered in `FoodExpansionModule.initialize()` iterates all online players with nutrition data and calls `decaySatiationCounters()`. Runs every N minutes (configurable, default: 3 min). This is a global timer (not per-player) because decay does not need per-player config.
- **Reset on death**: All counters clear (fresh start on respawn)
- **Not persisted to DB**: Satiation is transient session state. Resets on quit/rejoin. This is intentional — it's a short-term anti-spam mechanic, not long-term progression.

### Diminishing Returns

The satiation count determines a multiplier applied to macro gains from force-eating:

| Satiation Count | Multiplier | Chat Message |
|---|---|---|
| 0 | 1.0 (100%) | *(none)* |
| 1 | 0.7 (70%) | *(none)* |
| 2 | 0.4 (40%) | `"You're getting tired of <food>..."` |
| 3 | 0.15 (15%) | `"You can barely stomach more <food>."` |
| 4+ | 0.0 (hard cap) | `"You can't eat any more <food> right now."` |

- The tier lookup iterates from highest configured tier to lowest, returning the multiplier for the first tier whose count is <= the player's satiation count. Implementation: load tiers into a `TreeMap<Integer, Double>` and iterate `descendingMap()`.
- Chat messages use the food's display name (e.g., "Cooked Beef" not "COOKED_BEEF"). Derive via `WordUtils`-style capitalization of `Material.name().replace("_", " ").toLowerCase()`.
- At hard cap (0% multiplier), the food item is NOT consumed — the eat is fully blocked with the chat message.

### What This Does NOT Change

- Normal eating (hunger < threshold) — no penalties, no counter increment, existing `onPlayerConsume` handler unchanged
- Vanilla hunger slowdown system — unchanged
- Death penalty, decay, effects, HUD — all untouched

## Config Schema

```yaml
FoodExpansion:
  Overeating:
    Enabled: true
    # Hunger level at or above which force-eating activates
    HungerThreshold: 20
    # Minutes between satiation counter decay ticks (each tick decrements by 1)
    DecayIntervalMinutes: 3
    # Cooldown in milliseconds between force-eats per player
    CooldownMs: 500
    # Satiation count -> multiplier mapping
    # Implementation iterates from highest to lowest; first count <= player's satiation wins
    Tiers:
      0: 1.0
      1: 0.7
      2: 0.4
      3: 0.15
      4: 0.0
    Messages:
      # Satiation count at which each message first appears
      Warning: 2
      WarningText: "&7You're getting tired of &f{food}&7..."
      Severe: 3
      SevereText: "&7You can barely stomach more &f{food}&7."
      Blocked: 4
      BlockedText: "&cYou can't eat any more &f{food}&c right now."
```

## Data Model Changes

### PlayerNutritionData

Add to existing class:

- `Map<String, Integer> satiationCounters` — per-food satiation count (default: empty `HashMap`)
- `int getSatiation(String foodKey)` — returns `satiationCounters.getOrDefault(foodKey, 0)`
- `void incrementSatiation(String foodKey)` — `satiationCounters.merge(foodKey, 1, Integer::sum)`
- `void decaySatiationCounters()` — decrements all values by 1, removes entries that reach 0 via `Iterator.remove()`
- `void clearSatiationCounters()` — `satiationCounters.clear()`

No DB persistence. No dirty flag interaction (satiation is transient).

### No New Classes

The force-eat logic lives in `FoodExpansionEvents` as a new `onPlayerInteract` handler. The satiation data lives in `PlayerNutritionData`. No new classes needed.

## Implementation Touchpoints

| File | Change |
|---|---|
| `PlayerNutritionData.java` | Add satiation counter map and methods |
| `FoodExpansionEvents.java` | Add `onPlayerInteract` handler for force-eating with hand filtering, cooldown tracking, container item returns; call `clearSatiationCounters()` in `onPlayerDeath` |
| `FoodExpansionModule.java` | Register global satiation decay timer in `initialize()`; store and cancel its `BukkitTask` handle in `shutdown()` |
| `foodexpansion.yml` | Add `Overeating` config section |

## Edge Cases

- **Double-fire per click**: `PlayerInteractEvent` fires once per hand. Filter on `event.getHand()` — only process the hand that holds the food item. If neither hand holds food, return early.
- **Stacked food**: Remove exactly 1 from the stack via `item.setAmount(item.getAmount() - 1)`
- **Offhand food**: Handled by the hand-filtering logic — if food is in offhand and offhand triggers the event, process it
- **Container items**: Stews/soups return a `BOWL`, Honey Bottle returns a `GLASS_BOTTLE`. Check `Material` and add the container item to inventory (or drop if inventory is full).
- **Creative mode**: Skip force-eat entirely (already skipped by existing gamemode checks in the module)
- **Non-food right-clicks**: Only trigger on `Material.isEdible()` items. Blocks, tools, etc. are ignored.
- **Comfort bonus**: Applied as part of the combined multiplier — `finalMultiplier = overeatMultiplier * comfortMultiplier`
- **0% multiplier (blocked)**: Do not consume the item, do not play eat sound, only send chat message
- **Eating feedback**: Use `player.playSound()` for eat sound and `player.swingMainHand()` for visual feedback. The full vanilla eating animation (crunch particles, arm movement) cannot be replicated server-side. This is an acceptable trade-off.
- **Cooldown**: 500ms between force-eats prevents spam. Check `System.currentTimeMillis() - lastForceEat < cooldownMs`.
