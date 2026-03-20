# Macro Nutrition System — Design Spec

**Module:** `FoodExpansion`
**Date:** 2026-03-20
**Status:** Approved

## Overview

A macro nutrition system inspired by Green Hell, TerraFirmaCraft, and Vintage Story. Every food item provides values across 3 nutrient categories (Protein, Carbohydrates, Fats). Hydration is unified with TAN's existing thirst system. Nutrient levels affect player attributes via a tiered buff/debuff system using attribute modifiers (no potion effects).

Vanilla hunger is retained but slowed (configurable drain multiplier), making the nutrition system the primary food-management challenge.

## Architecture

### Approach: Standalone Module with Cross-Module Queries

`FoodExpansionModule` extends `HLModule` as an independent module. It queries TAN, Comfort, and Fear via `HLModule.getModule()` at runtime. All dependencies are soft — each module works independently if others are disabled.

**Dependency direction (all soft):**
```
FoodExpansionModule ──> TanModule (reads thirst for hydration check)
FoodExpansionModule ──> ComfortModule (reads comfort tier on eat)
FearModule ──> FoodExpansionModule (reads nutrient levels for fear condition)
```

No circular dependencies.

### Package: `cz.hashiri.harshlands.foodexpansion`

## Data Model

### `NutrientProfile` — immutable record

```java
public record NutrientProfile(double protein, double carbs, double fats) {}
```

Loaded from config per food item. No hydration field — hydration is TAN's thirst system.

### `PlayerNutritionData` — mutable per-player state

Fields:
- `double protein, carbs, fats` — 0.0–100.0
- `double proteinExhaustion, carbsExhaustion, fatsExhaustion` — activity-based exhaustion accumulators
- `volatile boolean dirty`
- `boolean miningFlag, fightingFlag` — activity detection flags, reset each decay tick

Methods:
- `addNutrients(NutrientProfile, double comfortMultiplier)` — adds nutrients clamped to 100.0
- `applyDecay(double proteinDelta, double carbsDelta, double fatsDelta)` — subtracts clamped to 0.0
- `applyDeathPenalty(double percentLoss)` — reduces each nutrient by percentage
- `getNutrientTier()` → `NutrientTier` enum based on current levels

### `NutrientTier` enum

| Tier | Condition |
|------|-----------|
| `PEAK_NUTRITION` | All 3 nutrients > 80 AND hydration > 80% |
| `WELL_NOURISHED` | All 3 nutrients > 60 AND hydration > 60% |
| `NORMAL` | All 3 nutrients between 30–60 |
| `MALNOURISHED` | Any nutrient below 30 |
| `SEVERELY_MALNOURISHED` | Any nutrient below 15 |
| `STARVING` | Any nutrient at 0 |

Tier evaluation checks from worst to best and returns the first match. Hydration is queried from TanModule (thirst 0–20 scaled to 0–100). If TAN is not loaded, hydration check is skipped (always satisfied).

## Database

### Table: `hl_nutrition_data`

```sql
CREATE TABLE IF NOT EXISTS hl_nutrition_data (
    uuid VARCHAR(36) PRIMARY KEY,
    protein DOUBLE DEFAULT 50.0,
    carbs DOUBLE DEFAULT 50.0,
    fats DOUBLE DEFAULT 50.0,
    protein_exhaustion DOUBLE DEFAULT 0.0,
    carbs_exhaustion DOUBLE DEFAULT 0.0,
    fats_exhaustion DOUBLE DEFAULT 0.0
)
```

### `NutritionDataRow` record in `HLDatabase`

```java
public record NutritionDataRow(
    double protein, double carbs, double fats,
    double proteinExhaustion, double carbsExhaustion, double fatsExhaustion
) {}
```

### DB methods in `HLDatabase`

- `loadNutritionData(UUID)` → `CompletableFuture<Optional<NutritionDataRow>>`
- `saveNutritionData(UUID, NutritionDataRow)` → async fire-and-forget

Upsert:
- H2: `MERGE INTO hl_nutrition_data KEY(uuid) VALUES (...)`
- MySQL: `INSERT INTO hl_nutrition_data ... ON DUPLICATE KEY UPDATE ...`

### `NutritionDataModule` implements `HLDataModule`

- `retrieveData()` — async load from DB, populate `PlayerNutritionData`, defaults if not found
- `saveData()` — async save if dirty, clear dirty flag
- Registered in `HLPlayer` alongside TAN, Baubles, Fear, CabinFever data modules

### Auto-save

Every 6000 ticks (5 minutes), dirty players only. Registered in `FoodExpansionModule.initialize()`.

## Periodic Tasks

### `NutritionDecayTask` — per-player, runs every 100 ticks (5 seconds)

Each tick:

1. **Accumulate activity exhaustion:**
   - `player.isSprinting()` → add `sprintMultiplier × baseDelta` to `carbsExhaustion`
   - `player.isSwimming()` → add to `carbsExhaustion` and `fatsExhaustion`
   - `miningFlag` set → add to `carbsExhaustion` (reset flag)
   - `fightingFlag` set → add to `proteinExhaustion` and `fatsExhaustion` (reset flag)

2. **Convert exhaustion to decay:** When any exhaustion accumulator exceeds threshold (default 4.0), subtract from that nutrient and reset exhaustion. Mirrors TAN's `thirstExhaustion → thirstLevel` pattern.

3. **Passive time-based decay:** Applied every tick at `baseRate / 60` (base rates are per-5-minutes; task runs 60 times per 5 minutes). Nutrients always drain; activity accelerates it.

4. **Clamp** all values to 0.0–100.0, mark dirty if changed.

### `NutritionEffectTask` — per-player, runs every 40 ticks (2 seconds)

Each tick:

1. **Evaluate** current `NutrientTier` from `PlayerNutritionData`
2. **Compare** to cached previous tier. If changed:
   - Remove all old attribute modifiers (keyed by `NamespacedKey("harshlands", "nutrition_*")`)
   - Apply new attribute modifiers for current tier
3. **If STARVING:** Check damage interval counter, apply `player.damage(amount)` with starvation damage source
4. **Update HUD** elements (merged into this task for efficiency — updates every 2 seconds)

## Attribute Modifiers

All effects use `AttributeModifier` — no potion effects. Tiers are mutually exclusive.

| Tier | Attribute | Value | Operation |
|------|-----------|-------|-----------|
| Well-Nourished | `GENERIC_MAX_HEALTH` | +2.0 | `ADD_NUMBER` |
| Well-Nourished | `GENERIC_MOVEMENT_SPEED` | +0.10 | `ADD_SCALAR` |
| Peak Nutrition | `GENERIC_MAX_HEALTH` | +4.0 | `ADD_NUMBER` |
| Peak Nutrition | `GENERIC_MOVEMENT_SPEED` | +0.10 | `ADD_SCALAR` |
| Peak Nutrition | `GENERIC_ATTACK_DAMAGE` | +0.10 | `ADD_SCALAR` |
| Malnourished | `PLAYER_BLOCK_BREAK_SPEED` | -0.30 | `ADD_SCALAR` |
| Severely Malnourished | `GENERIC_ATTACK_DAMAGE` | -0.20 | `ADD_SCALAR` |
| Severely Malnourished | `GENERIC_MOVEMENT_SPEED` | -0.20 | `ADD_SCALAR` |

Starvation (any nutrient at 0): direct `player.damage()` every 80 ticks (configurable), bypassing attribute system.

## Event Handling

### `FoodExpansionEvents` extends `ModuleEvents`

**`PlayerItemConsumeEvent` (priority MONITOR):**
1. Look up `NutrientProfile` for consumed item from config map. If not found → ignore.
2. Comfort check: query `ComfortModule` for cached comfort result. If tier ≥ configured minimum → multiply nutrients by `1.0 + absorptionBonus`.
3. Call `playerNutritionData.addNutrients(profile, multiplier)`, mark dirty.

**`FoodLevelChangeEvent` — vanilla hunger slowdown:**
1. If food level would decrease (natural hunger drain): multiply change by `DrainMultiplier` (default 0.5).
2. If food level would increase (eating): allow normally.

**`PlayerDeathEvent`:**
1. Call `applyDeathPenalty(percentLoss)` on `PlayerNutritionData`, mark dirty.

**`PlayerRespawnEvent`:**
1. Re-apply attribute modifiers for current tier (death clears all modifiers).
2. Tasks continue running (not cancelled on death).

**`PlayerJoinEvent`:**
1. `NutritionDataModule.retrieveData()` async → on main thread: start `NutritionDecayTask`, `NutritionEffectTask`, register HUD elements, apply tier modifiers.

**`PlayerQuitEvent`:**
1. Cancel both tasks, remove HUD elements, remove attribute modifiers, save if dirty (async).

**Activity detection:**
- `BlockBreakEvent` → set `miningFlag` on `PlayerNutritionData`
- `EntityDamageByEntityEvent` (player is damager) → set `fightingFlag`
- Sprinting/swimming checked directly from `Player` in decay task

### World Change

On world change, check `isEnabled(World)`. If disabled in new world: remove modifiers, pause tasks. If enabled: resume tasks, apply modifiers.

## HUD Display

Three separate `HudElement` entries on the existing per-player `BossbarHUD`.

| Element ID | Placeholder | Color Logic |
|------------|-------------|-------------|
| `nutrition_protein` | `P: 75` | Green ≥60, Yellow 30–59, Red <30 |
| `nutrition_carbs` | `C: 60` | Same thresholds |
| `nutrition_fats` | `F: 90` | Same thresholds |

Updated every 40 ticks (2 seconds) by `NutritionEffectTask`. Only updates if values changed since last render.

Text uses Adventure `Component` with `NamedTextColor`. Color thresholds match effect tiers.

X/Y positioning configurable in `foodexpansion.yml`:
```yaml
HUD:
  Protein:
    X: -120
    Y: 0
  Carbs:
    X: -80
    Y: 0
  Fats:
    X: -40
    Y: 0
```

When icons are added later, the text `Component` is swapped for a font-character `Component` — the HUD element API doesn't change.

## Command

### `/hl nutrition [player]`

**Permission:** `harshlands.command.nutrition` (default: true)
**Other-player permission:** `harshlands.command.nutrition.others` (default: op)

Output:
```
--- Nutrition Status ---
Protein:  ████████░░ 75.0/100
Carbs:    ██████░░░░ 60.0/100
Fats:     █████████░ 90.0/100
Status:   Well-Nourished
```

Uses Adventure `Component` with colored progress bars matching HUD color thresholds.

Added to `Commands.java` switch block and `Tab.java` for tab completion (suggests online player names for second argument).

### plugin.yml additions

```yaml
permissions:
  harshlands.command.nutrition:
    description: "View your nutrition status"
    default: true
  harshlands.command.nutrition.others:
    description: "View other players' nutrition status"
    default: op
```

Both added as children of `harshlands.command.*`.

## Cross-Module Integration

### TAN — Hydration Unification

- FoodExpansionModule does NOT track hydration
- For tier evaluation, queries TAN: `tan.getThirstManager().getThirst(player)` (0–20) → scaled to 0–100
- Well-Nourished and Peak Nutrition require adequate hydration alongside all 3 nutrients
- If TAN not loaded: hydration check skipped (always satisfied), warning logged

### Comfort — Absorption Bonus

- On `PlayerItemConsumeEvent`, query ComfortModule for cached comfort result
- If tier ≥ configured minimum (default HOME): multiply nutrient gains by 1.10
- If ComfortModule not loaded: no bonus, no error

### Fear — Malnourished Condition

New condition in `FearConditionEvaluator`:

```yaml
# fear.yml
Malnourished:
  Enabled: true
  Amount: 3.0  # fear per evaluation tick, per deficient nutrient
```

- Checks if FoodExpansionModule is loaded, queries nutrients below 30
- Each deficient nutrient adds `Amount` to fear delta (all 3 deficient = +9.0)
- Follows exact pattern of existing conditions (Darkness, LowHealth, etc.)
- If FoodExpansion not loaded: evaluates to 0

## Player Lifecycle

| Event | Actions |
|-------|---------|
| **Join** | Load from DB async → init data, start 2 tasks, register HUD, apply modifiers |
| **Quit** | Cancel tasks, remove HUD, remove modifiers, save if dirty (async) |
| **Death** | Apply death penalty (configurable % loss), mark dirty |
| **Respawn** | Re-apply attribute modifiers for current tier |
| **World change** | Check `isEnabled(World)` — disable/enable modifiers and tasks accordingly |

## Shutdown Order

1. `FoodExpansionModule.shutdown()` — cancel all per-player tasks, remove all attribute modifiers
2. All online players' `NutritionDataModule.saveData()` fires async (via `HLPlayer.saveData()` in `HLPlugin.onDisable`)
3. `HLScheduler` drains remaining writes (10s timeout)
4. No blocking wait needed in module shutdown

## Performance

- **2 tasks per player** (decay 100t + effects/HUD 40t). Lightweight, main thread.
- **Config map:** `Map<String, NutrientProfile>` — O(1) lookup on eat events.
- **Attribute modifier churn:** Only on tier transitions (infrequent).
- **DB writes:** Dirty flag, 5-minute auto-save. Minimal pressure.
- **Memory per player:** ~56 bytes (6 doubles + flags + tier cache).

## Configuration: `foodexpansion.yml`

```yaml
FoodExpansion:
  Defaults:
    Protein: 50.0
    Carbs: 50.0
    Fats: 50.0

  DeathPenalty:
    PercentLoss: 25.0

  VanillaHunger:
    DrainMultiplier: 0.5

  Decay:
    Protein: 1.0
    Carbs: 1.5
    Fats: 0.5

  Activity:
    ExhaustionThreshold: 4.0
    Sprinting: 1.5
    Mining: 1.2
    Fighting: 1.3
    Swimming: 1.4

  Comfort:
    Enabled: true
    MinTier: "HOME"
    AbsorptionBonus: 0.10

  Effects:
    WellNourished:
      Threshold: 60
      MaxHealthBonus: 2.0
      SpeedMultiplier: 0.10
    PeakNutrition:
      Threshold: 80
      MaxHealthBonus: 4.0
      SpeedMultiplier: 0.10
      AttackDamageBonus: 0.10
    Malnourished:
      Threshold: 30
      MiningSpeedReduction: 0.30
    SeverelyMalnourished:
      Threshold: 15
      AttackDamageReduction: 0.20
      SpeedReduction: 0.20
    Starving:
      DamagePerTick: 1.0
      DamageInterval: 80

  HUD:
    Protein:
      X: -120
      Y: 0
    Carbs:
      X: -80
      Y: 0
    Fats:
      X: -40
      Y: 0

  Foods:
    COOKED_BEEF:
      Protein: 25.0
      Carbs: 5.0
      Fats: 15.0
    BREAD:
      Protein: 5.0
      Carbs: 30.0
      Fats: 5.0
    BAKED_POTATO:
      Protein: 5.0
      Carbs: 20.0
      Fats: 2.0
    COOKED_SALMON:
      Protein: 20.0
      Carbs: 0.0
      Fats: 20.0
    GOLDEN_APPLE:
      Protein: 0.0
      Carbs: 30.0
      Fats: 5.0
    MUSHROOM_STEW:
      Protein: 10.0
      Carbs: 15.0
      Fats: 10.0
    RABBIT_STEW:
      Protein: 20.0
      Carbs: 15.0
      Fats: 10.0
    COOKIE:
      Protein: 0.0
      Carbs: 15.0
      Fats: 10.0
    SWEET_BERRIES:
      Protein: 0.0
      Carbs: 10.0
      Fats: 0.0
    DRIED_KELP:
      Protein: 5.0
      Carbs: 5.0
      Fats: 0.0
    COOKED_CHICKEN:
      Protein: 20.0
      Carbs: 0.0
      Fats: 10.0
    COOKED_MUTTON:
      Protein: 20.0
      Carbs: 0.0
      Fats: 20.0
    COOKED_PORKCHOP:
      Protein: 22.0
      Carbs: 0.0
      Fats: 25.0
    COOKED_COD:
      Protein: 18.0
      Carbs: 0.0
      Fats: 15.0
    COOKED_RABBIT:
      Protein: 18.0
      Carbs: 0.0
      Fats: 8.0
    APPLE:
      Protein: 0.0
      Carbs: 15.0
      Fats: 0.0
    MELON_SLICE:
      Protein: 0.0
      Carbs: 8.0
      Fats: 0.0
    BEETROOT:
      Protein: 2.0
      Carbs: 12.0
      Fats: 0.0
    CARROT:
      Protein: 2.0
      Carbs: 10.0
      Fats: 0.0
    POTATO:
      Protein: 3.0
      Carbs: 15.0
      Fats: 0.0
    BEETROOT_SOUP:
      Protein: 5.0
      Carbs: 18.0
      Fats: 5.0
    PUMPKIN_PIE:
      Protein: 3.0
      Carbs: 20.0
      Fats: 12.0
    HONEY_BOTTLE:
      Protein: 0.0
      Carbs: 20.0
      Fats: 0.0
    GLOW_BERRIES:
      Protein: 0.0
      Carbs: 8.0
      Fats: 0.0
    ENCHANTED_GOLDEN_APPLE:
      Protein: 0.0
      Carbs: 40.0
      Fats: 10.0
    CHORUS_FRUIT:
      Protein: 5.0
      Carbs: 8.0
      Fats: 2.0
    ROTTEN_FLESH:
      Protein: 5.0
      Carbs: 0.0
      Fats: 3.0
    SPIDER_EYE:
      Protein: 3.0
      Carbs: 0.0
      Fats: 2.0
    SUSPICIOUS_STEW:
      Protein: 5.0
      Carbs: 10.0
      Fats: 5.0
    TROPICAL_FISH:
      Protein: 8.0
      Carbs: 0.0
      Fats: 5.0
```

## File Summary

New files:
- `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java`
- `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutrientProfile.java`
- `core/src/main/java/cz/hashiri/harshlands/foodexpansion/PlayerNutritionData.java`
- `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutrientTier.java`
- `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutritionDecayTask.java`
- `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutritionEffectTask.java`
- `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java`
- `core/src/main/java/cz/hashiri/harshlands/data/foodexpansion/NutritionDataModule.java`
- `core/src/main/resources/foodexpansion.yml`

Modified files:
- `core/src/main/java/cz/hashiri/harshlands/data/HLPlayer.java` — add NutritionDataModule
- `core/src/main/java/cz/hashiri/harshlands/data/db/HLDatabase.java` — add table, record, load/save methods
- `core/src/main/java/cz/hashiri/harshlands/rsv/HLPlugin.java` — initialize FoodExpansionModule
- `core/src/main/java/cz/hashiri/harshlands/commands/Commands.java` — add nutrition subcommand
- `core/src/main/java/cz/hashiri/harshlands/commands/Tab.java` — add nutrition tab completion
- `core/src/main/java/cz/hashiri/harshlands/fear/FearConditionEvaluator.java` — add Malnourished condition
- `core/src/main/resources/config.yml` — add FoodExpansion enabled toggle
- `core/src/main/resources/plugin.yml` — add nutrition permissions
- `core/src/main/resources/fear.yml` — add Malnourished condition config
