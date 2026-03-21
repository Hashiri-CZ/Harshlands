# Macro Nutrition System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a FoodExpansion module that tracks Protein, Carbohydrates, and Fats as independent nutrient bars with activity-scaled decay, attribute-modifier-based effects, BossBar HUD display, and cross-module integration with TAN (hydration), Comfort (absorption bonus), and Fear (malnourished condition).

**Architecture:** Standalone `FoodExpansionModule` extends `HLModule` with soft dependencies on TAN, Comfort, and Fear. Per-player `PlayerNutritionData` persists via `DataModule` (implements `HLDataModule`) to `hl_nutrition_data` table. Two periodic tasks per player: `NutritionDecayTask` (100t) for nutrient drain and `NutritionEffectTask` (40t) for attribute modifiers + HUD updates.

**Tech Stack:** Java 21, Bukkit/Spigot 1.21.11, Adventure API (net.kyori.adventure), HikariCP (H2/MySQL), Maven multi-module

**Spec:** `docs/superpowers/specs/2026-03-20-macro-nutrition-design.md`

**Note:** This is a Bukkit plugin with no unit test infrastructure. Verification is done via Maven build (`mvn clean package`) after each task. In-game testing is done at the end.

---

## File Map

### New Files

| File | Responsibility |
|------|---------------|
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutrientProfile.java` | Immutable record: protein, carbs, fats per food item |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutrientTier.java` | Enum: STARVING → SEVERELY_MALNOURISHED → MALNOURISHED → PEAK_NUTRITION → WELL_NOURISHED → NORMAL |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/PlayerNutritionData.java` | Mutable per-player state: nutrient levels, exhaustion, decay, death penalty, tier evaluation |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutritionDecayTask.java` | BukkitRunnable: passive decay + activity exhaustion accumulation |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutritionEffectTask.java` | BukkitRunnable: tier evaluation, attribute modifiers, starvation damage, HUD updates |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java` | Event listener: food consume, hunger slowdown, death/respawn, join/quit, activity flags, world change |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java` | HLModule: config loading, food map, module lifecycle, auto-save, debug provider |
| `core/src/main/java/cz/hashiri/harshlands/data/foodexpansion/DataModule.java` | HLDataModule: async load/save with dirty flag and main-thread snapshot |
| `core/src/main/resources/foodexpansion.yml` | Default config: decay rates, thresholds, food nutrient values, HUD positions |

### Modified Files

| File | Changes |
|------|---------|
| `core/src/main/java/cz/hashiri/harshlands/data/db/HLDatabase.java` | Add `NutritionDataRow` record, `hl_nutrition_data` table creation, `loadNutritionData()`, `saveNutritionData()` |
| `core/src/main/java/cz/hashiri/harshlands/data/HLPlayer.java` | Add `DataModule` field for nutrition, wire into constructor/retrieveData/saveData |
| `core/src/main/java/cz/hashiri/harshlands/rsv/HLPlugin.java` | Initialize `FoodExpansionModule` in `onEnable()` |
| `core/src/main/java/cz/hashiri/harshlands/commands/Commands.java` | Add `nutrition` subcommand (view/set/reset) |
| `core/src/main/java/cz/hashiri/harshlands/commands/Tab.java` | Add `nutrition` tab completions |
| `core/src/main/java/cz/hashiri/harshlands/fear/FearConditionEvaluator.java` | Add `Malnourished` condition check |
| `core/src/main/resources/config.yml` | Add `FoodExpansion.Enabled` + world toggles |
| `core/src/main/resources/plugin.yml` | Add nutrition permissions |
| `core/src/main/resources/fear.yml` | Add `Malnourished` condition config |

---

## Task 1: Config File + Data Model Records

**Files:**
- Create: `core/src/main/resources/foodexpansion.yml`
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutrientProfile.java`
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutrientTier.java`

- [ ] **Step 1: Create `foodexpansion.yml`**

Create the default config resource file with all nutrient values, decay rates, thresholds, HUD positions, and food definitions. Copy the full YAML from the spec's Configuration section (lines 343–569 of the spec).

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
      HydrationThreshold: 60
      MaxHealthBonus: 2.0
      SpeedMultiplier: 0.10
    PeakNutrition:
      Threshold: 80
      HydrationThreshold: 80
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

  DefaultFood:
    Protein: 2.0
    Carbs: 2.0
    Fats: 1.0

  HUD:
    Protein:
      X: -120
    Carbs:
      X: -80
    Fats:
      X: -40

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
    BEEF:
      Protein: 12.0
      Carbs: 0.0
      Fats: 8.0
    CHICKEN:
      Protein: 10.0
      Carbs: 0.0
      Fats: 5.0
    PORKCHOP:
      Protein: 11.0
      Carbs: 0.0
      Fats: 12.0
    MUTTON:
      Protein: 10.0
      Carbs: 0.0
      Fats: 10.0
    RABBIT:
      Protein: 9.0
      Carbs: 0.0
      Fats: 4.0
    COD:
      Protein: 9.0
      Carbs: 0.0
      Fats: 7.0
    SALMON:
      Protein: 10.0
      Carbs: 0.0
      Fats: 10.0
    PUFFERFISH:
      Protein: 3.0
      Carbs: 0.0
      Fats: 2.0
    POISONOUS_POTATO:
      Protein: 1.0
      Carbs: 5.0
      Fats: 0.0
```

- [ ] **Step 2: Create `NutrientProfile.java`**

```java
package cz.hashiri.harshlands.foodexpansion;

public record NutrientProfile(double protein, double carbs, double fats) {}
```

- [ ] **Step 3: Create `NutrientTier.java`**

```java
package cz.hashiri.harshlands.foodexpansion;

public enum NutrientTier {
    STARVING,
    SEVERELY_MALNOURISHED,
    MALNOURISHED,
    PEAK_NUTRITION,
    WELL_NOURISHED,
    NORMAL
}
```

- [ ] **Step 4: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/resources/foodexpansion.yml \
  core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutrientProfile.java \
  core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutrientTier.java
git commit -m "Add FoodExpansion config and data model records"
```

---

## Task 2: PlayerNutritionData

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/PlayerNutritionData.java`

This is the core mutable per-player state holder. It tracks nutrient levels, exhaustion accumulators, activity flags, hunger debt accumulator, and evaluates the current tier.

- [ ] **Step 1: Create `PlayerNutritionData.java`**

```java
package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.tan.TanModule;
import org.bukkit.entity.Player;

public class PlayerNutritionData {

    private double protein;
    private double carbs;
    private double fats;

    private double proteinExhaustion;
    private double carbsExhaustion;
    private double fatsExhaustion;

    private volatile boolean dirty = false;

    // Activity detection flags — set by event listeners, consumed by decay task
    private boolean miningFlag = false;
    private boolean fightingFlag = false;

    // Hunger debt accumulator for FoodLevelChangeEvent fractional drain
    private double hungerDebtAccumulator = 0.0;

    // Cached tier for change detection in effect task
    private NutrientTier cachedTier = NutrientTier.NORMAL;

    // Cached HUD values for change detection
    private int lastHudProtein = -1;
    private int lastHudCarbs = -1;
    private int lastHudFats = -1;

    // Starvation damage tick counter
    private int starvationTickCounter = 0;

    public PlayerNutritionData(double protein, double carbs, double fats,
                               double proteinExhaustion, double carbsExhaustion,
                               double fatsExhaustion) {
        this.protein = protein;
        this.carbs = carbs;
        this.fats = fats;
        this.proteinExhaustion = proteinExhaustion;
        this.carbsExhaustion = carbsExhaustion;
        this.fatsExhaustion = fatsExhaustion;
    }

    // --- Nutrient modification ---

    public void addNutrients(NutrientProfile profile, double comfortMultiplier) {
        this.protein = Math.min(100.0, this.protein + profile.protein() * comfortMultiplier);
        this.carbs = Math.min(100.0, this.carbs + profile.carbs() * comfortMultiplier);
        this.fats = Math.min(100.0, this.fats + profile.fats() * comfortMultiplier);
        dirty = true;
    }

    public void applyDecay(double proteinDelta, double carbsDelta, double fatsDelta) {
        double oldP = protein, oldC = carbs, oldF = fats;
        this.protein = Math.max(0.0, this.protein - proteinDelta);
        this.carbs = Math.max(0.0, this.carbs - carbsDelta);
        this.fats = Math.max(0.0, this.fats - fatsDelta);
        if (protein != oldP || carbs != oldC || fats != oldF) {
            dirty = true;
        }
    }

    public void applyDeathPenalty(double percentLoss) {
        double factor = 1.0 - (percentLoss / 100.0);
        this.protein *= factor;
        this.carbs *= factor;
        this.fats *= factor;
        dirty = true;
    }

    // --- Tier evaluation ---

    /**
     * Evaluates the current NutrientTier based on nutrient levels and hydration.
     * Checks worst-to-best: STARVING → SEVERELY_MALNOURISHED → MALNOURISHED →
     * then best-to-good: PEAK_NUTRITION → WELL_NOURISHED → NORMAL (fallthrough).
     *
     * @param hydrationPercent hydration as 0-100 scale (from TAN thirst 0-20 * 5),
     *                         or 100.0 if TAN is not loaded
     * @param wellNourishedThreshold config threshold for well-nourished (default 60)
     * @param wellNourishedHydration config hydration threshold for well-nourished (default 60)
     * @param peakThreshold config threshold for peak nutrition (default 80)
     * @param peakHydration config hydration threshold for peak nutrition (default 80)
     * @param malnourishedThreshold config threshold for malnourished (default 30)
     * @param severeThreshold config threshold for severely malnourished (default 15)
     */
    public NutrientTier evaluateTier(double hydrationPercent,
                                     double wellNourishedThreshold, double wellNourishedHydration,
                                     double peakThreshold, double peakHydration,
                                     double malnourishedThreshold, double severeThreshold) {
        // Worst first
        if (protein <= 0.0 || carbs <= 0.0 || fats <= 0.0) {
            return NutrientTier.STARVING;
        }
        if (protein < severeThreshold || carbs < severeThreshold || fats < severeThreshold) {
            return NutrientTier.SEVERELY_MALNOURISHED;
        }
        if (protein < malnourishedThreshold || carbs < malnourishedThreshold || fats < malnourishedThreshold) {
            return NutrientTier.MALNOURISHED;
        }
        // Best next
        if (protein > peakThreshold && carbs > peakThreshold && fats > peakThreshold
                && hydrationPercent > peakHydration) {
            return NutrientTier.PEAK_NUTRITION;
        }
        if (protein > wellNourishedThreshold && carbs > wellNourishedThreshold && fats > wellNourishedThreshold
                && hydrationPercent > wellNourishedHydration) {
            return NutrientTier.WELL_NOURISHED;
        }
        return NutrientTier.NORMAL;
    }

    // --- Exhaustion ---

    public void addProteinExhaustion(double amount) { this.proteinExhaustion += amount; }
    public void addCarbsExhaustion(double amount) { this.carbsExhaustion += amount; }
    public void addFatsExhaustion(double amount) { this.fatsExhaustion += amount; }

    /**
     * Checks if exhaustion exceeds threshold. If so, converts to nutrient decay
     * and resets exhaustion. Returns the amount to subtract from the nutrient.
     */
    public double drainProteinExhaustion(double threshold) {
        if (proteinExhaustion >= threshold) {
            double drain = Math.floor(proteinExhaustion / threshold);
            proteinExhaustion -= drain * threshold;
            return drain;
        }
        return 0.0;
    }

    public double drainCarbsExhaustion(double threshold) {
        if (carbsExhaustion >= threshold) {
            double drain = Math.floor(carbsExhaustion / threshold);
            carbsExhaustion -= drain * threshold;
            return drain;
        }
        return 0.0;
    }

    public double drainFatsExhaustion(double threshold) {
        if (fatsExhaustion >= threshold) {
            double drain = Math.floor(fatsExhaustion / threshold);
            fatsExhaustion -= drain * threshold;
            return drain;
        }
        return 0.0;
    }

    // --- Activity flags ---

    public boolean consumeMiningFlag() {
        boolean val = miningFlag;
        miningFlag = false;
        return val;
    }

    public boolean consumeFightingFlag() {
        boolean val = fightingFlag;
        fightingFlag = false;
        return val;
    }

    public void setMiningFlag() { this.miningFlag = true; }
    public void setFightingFlag() { this.fightingFlag = true; }

    // --- Hunger debt ---

    public double getHungerDebtAccumulator() { return hungerDebtAccumulator; }
    public void setHungerDebtAccumulator(double value) { this.hungerDebtAccumulator = value; }
    public void addHungerDebt(double debt) { this.hungerDebtAccumulator += debt; }

    // --- Starvation counter ---

    public int getStarvationTickCounter() { return starvationTickCounter; }
    public void setStarvationTickCounter(int value) { this.starvationTickCounter = value; }
    public void incrementStarvationTickCounter(int ticks) { this.starvationTickCounter += ticks; }

    // --- Cached tier ---

    public NutrientTier getCachedTier() { return cachedTier; }
    public void setCachedTier(NutrientTier tier) { this.cachedTier = tier; }

    // --- HUD cache ---

    public int getLastHudProtein() { return lastHudProtein; }
    public int getLastHudCarbs() { return lastHudCarbs; }
    public int getLastHudFats() { return lastHudFats; }
    public void setLastHudValues(int p, int c, int f) {
        lastHudProtein = p; lastHudCarbs = c; lastHudFats = f;
    }

    // --- Dirty flag ---

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }

    // --- Getters/Setters ---

    public double getProtein() { return protein; }
    public double getCarbs() { return carbs; }
    public double getFats() { return fats; }

    public void setProtein(double v) { protein = v; dirty = true; }
    public void setCarbs(double v) { carbs = v; dirty = true; }
    public void setFats(double v) { fats = v; dirty = true; }

    public double getProteinExhaustion() { return proteinExhaustion; }
    public double getCarbsExhaustion() { return carbsExhaustion; }
    public double getFatsExhaustion() { return fatsExhaustion; }

    /**
     * Restores all fields from a DB row WITHOUT marking dirty.
     * Used by DataModule.retrieveData() to load persisted state.
     */
    public void restoreFromRow(double protein, double carbs, double fats,
                               double proteinExhaustion, double carbsExhaustion,
                               double fatsExhaustion) {
        this.protein = protein;
        this.carbs = carbs;
        this.fats = fats;
        this.proteinExhaustion = proteinExhaustion;
        this.carbsExhaustion = carbsExhaustion;
        this.fatsExhaustion = fatsExhaustion;
        // Do NOT set dirty — this is a DB load, not a player-driven change
    }

    /** Returns the minimum nutrient value among protein, carbs, fats. */
    public double getMinNutrient() {
        return Math.min(protein, Math.min(carbs, fats));
    }

    /** Counts how many nutrients are below the given threshold. */
    public int countBelowThreshold(double threshold) {
        int count = 0;
        if (protein < threshold) count++;
        if (carbs < threshold) count++;
        if (fats < threshold) count++;
        return count;
    }
}
```

- [ ] **Step 2: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/PlayerNutritionData.java
git commit -m "Add PlayerNutritionData per-player state holder"
```

---

## Task 3: Database Layer

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/data/db/HLDatabase.java`

Add the `NutritionDataRow` record, table creation SQL, and load/save methods following the exact patterns used by TAN data (see `HLDatabase.java` lines 37-52 for records, 232-267 for table creation, 486-535 for load/save).

- [ ] **Step 1: Add `NutritionDataRow` record**

Add near the existing records (after `CabinFeverDataRow`, around line 52):

```java
public record NutritionDataRow(
    double protein, double carbs, double fats,
    double proteinExhaustion, double carbsExhaustion, double fatsExhaustion
) {}
```

- [ ] **Step 2: Add table creation SQL**

In the `createTables()` method, after the last `CREATE TABLE IF NOT EXISTS` statement (after `hl_cabin_fever_data`), add:

```java
stmt.executeUpdate("CREATE TABLE IF NOT EXISTS hl_nutrition_data ("
    + "uuid VARCHAR(36) PRIMARY KEY,"
    + "protein DOUBLE DEFAULT 50.0,"
    + "carbs DOUBLE DEFAULT 50.0,"
    + "fats DOUBLE DEFAULT 50.0,"
    + "protein_exhaustion DOUBLE DEFAULT 0.0,"
    + "carbs_exhaustion DOUBLE DEFAULT 0.0,"
    + "fats_exhaustion DOUBLE DEFAULT 0.0"
    + ")");
```

- [ ] **Step 3: Add `loadNutritionData()` method**

Follow the exact pattern of `loadTanData()` (lines 486-509):

```java
public CompletableFuture<Optional<NutritionDataRow>> loadNutritionData(UUID uuid) {
    return scheduler.supplyAsync(() -> {
        String sql = "SELECT protein, carbs, fats, protein_exhaustion, carbs_exhaustion, fats_exhaustion"
            + " FROM hl_nutrition_data WHERE uuid = ?";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new NutritionDataRow(
                        rs.getDouble("protein"),
                        rs.getDouble("carbs"),
                        rs.getDouble("fats"),
                        rs.getDouble("protein_exhaustion"),
                        rs.getDouble("carbs_exhaustion"),
                        rs.getDouble("fats_exhaustion")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.warning("[HLDatabase] Failed to load nutrition data for " + uuid + ": " + e.getMessage());
        }
        return Optional.empty();
    });
}
```

- [ ] **Step 4: Add `saveNutritionData()` method**

Follow the exact pattern of `saveTanData()` (lines 511-535), using `MERGE INTO ... KEY(uuid)` for H2 and `INSERT ... ON DUPLICATE KEY UPDATE` for MySQL:

```java
public CompletableFuture<Void> saveNutritionData(UUID uuid, NutritionDataRow row) {
    return scheduler.runAsync(() -> {
        String sql = isMysql
            ? "INSERT INTO hl_nutrition_data (uuid, protein, carbs, fats, protein_exhaustion, carbs_exhaustion, fats_exhaustion)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)"
                + " ON DUPLICATE KEY UPDATE protein=VALUES(protein), carbs=VALUES(carbs), fats=VALUES(fats),"
                + " protein_exhaustion=VALUES(protein_exhaustion), carbs_exhaustion=VALUES(carbs_exhaustion),"
                + " fats_exhaustion=VALUES(fats_exhaustion)"
            : "MERGE INTO hl_nutrition_data (uuid, protein, carbs, fats, protein_exhaustion, carbs_exhaustion, fats_exhaustion)"
                + " KEY(uuid)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, row.protein());
            ps.setDouble(3, row.carbs());
            ps.setDouble(4, row.fats());
            ps.setDouble(5, row.proteinExhaustion());
            ps.setDouble(6, row.carbsExhaustion());
            ps.setDouble(7, row.fatsExhaustion());
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[HLDatabase] Failed to save nutrition data for " + uuid + ": " + e.getMessage());
        }
    });
}
```

- [ ] **Step 5: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/data/db/HLDatabase.java
git commit -m "Add nutrition data table, record, and DB methods"
```

---

## Task 4: DataModule (Persistence Layer)

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/data/foodexpansion/DataModule.java`

Follow the exact pattern of `cz.hashiri.harshlands.data.toughasnails.DataModule` (constructor reads defaults from config, `retrieveData()` loads async with fallback to defaults, `saveData()` snapshots on main thread then writes async).

- [ ] **Step 1: Create `DataModule.java`**

```java
package cz.hashiri.harshlands.data.foodexpansion;

import cz.hashiri.harshlands.data.HLDataModule;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.db.HLDatabase;
import cz.hashiri.harshlands.foodexpansion.FoodExpansionModule;
import cz.hashiri.harshlands.foodexpansion.PlayerNutritionData;
import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DataModule implements HLDataModule {

    private final UUID id;
    private final HLDatabase database;
    private final PlayerNutritionData data;

    public DataModule(Player player) {
        FoodExpansionModule module = (FoodExpansionModule) HLModule.getModule(FoodExpansionModule.NAME);
        FileConfiguration config = module.getUserConfig().getConfig();
        HLPlugin plugin = HLPlugin.getPlugin();
        this.database = plugin.getDatabase();
        this.id = player.getUniqueId();

        double defaultProtein = config.getDouble("FoodExpansion.Defaults.Protein", 50.0);
        double defaultCarbs = config.getDouble("FoodExpansion.Defaults.Carbs", 50.0);
        double defaultFats = config.getDouble("FoodExpansion.Defaults.Fats", 50.0);

        this.data = new PlayerNutritionData(defaultProtein, defaultCarbs, defaultFats, 0.0, 0.0, 0.0);
    }

    @Override
    public void retrieveData() {
        database.loadNutritionData(id).thenAccept(optional -> {
            if (optional.isPresent()) {
                HLDatabase.NutritionDataRow row = optional.get();
                data.restoreFromRow(
                    row.protein(), row.carbs(), row.fats(),
                    row.proteinExhaustion(), row.carbsExhaustion(), row.fatsExhaustion()
                );
            } else {
                // No existing row — defaults already set in constructor; persist them
                saveData();
            }
        });
    }

    @Override
    public void saveData() {
        // Snapshot on main thread to avoid races with decay task
        HLDatabase.NutritionDataRow snapshot = new HLDatabase.NutritionDataRow(
            data.getProtein(), data.getCarbs(), data.getFats(),
            data.getProteinExhaustion(), data.getCarbsExhaustion(), data.getFatsExhaustion()
        );
        data.clearDirty();
        database.saveNutritionData(id, snapshot);
    }

    public PlayerNutritionData getData() {
        return data;
    }

    public boolean isDirty() {
        return data.isDirty();
    }
}
```

**Important note on `retrieveData()`:** Uses `data.restoreFromRow()` to set all 6 fields (nutrients + exhaustion) without marking dirty. This avoids the issue where `setProtein()`/etc. would mark dirty on load.

- [ ] **Step 2: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/data/foodexpansion/DataModule.java
git commit -m "Add nutrition DataModule with async load/save and snapshot pattern"
```

---

## Task 5: HLPlayer Integration

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/data/HLPlayer.java`

Add nutrition `DataModule` field, construct it conditionally (if `FoodExpansionModule` is globally enabled), wire into `retrieveData()` and `saveData()`.

- [ ] **Step 1: Read current `HLPlayer.java`**

Read the file to see exact line numbers for the field declarations (around lines 35-40), constructor (lines 42-59), `retrieveData()` and `saveData()` methods (lines 71-99).

- [ ] **Step 2: Add field declaration**

After the existing data module fields (after `cabinFeverDataModule`), add:

```java
private final cz.hashiri.harshlands.data.foodexpansion.DataModule nutritionDataModule;
```

- [ ] **Step 3: Add construction in constructor**

After the CabinFever data module creation block, following the same pattern:

```java
HLModule fem = HLModule.getModule(cz.hashiri.harshlands.foodexpansion.FoodExpansionModule.NAME);
this.nutritionDataModule = (fem != null && fem.isGloballyEnabled())
    ? new cz.hashiri.harshlands.data.foodexpansion.DataModule(player)
    : null;
```

- [ ] **Step 4: Wire into `retrieveData()`**

Add after existing `retrieveData()` calls:

```java
if (nutritionDataModule != null) {
    nutritionDataModule.retrieveData();
}
```

- [ ] **Step 5: Wire into `saveData()`**

Add after existing `saveData()` calls:

```java
if (nutritionDataModule != null) {
    nutritionDataModule.saveData();
}
```

- [ ] **Step 6: Add getter**

```java
public cz.hashiri.harshlands.data.foodexpansion.DataModule getNutritionDataModule() {
    return nutritionDataModule;
}
```

- [ ] **Step 7: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/data/HLPlayer.java
git commit -m "Wire nutrition DataModule into HLPlayer lifecycle"
```

---

## Task 6: NutritionDecayTask

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutritionDecayTask.java`

Per-player BukkitRunnable running every 100 ticks. Accumulates activity exhaustion, converts to decay, applies passive time-based decay.

- [ ] **Step 1: Create `NutritionDecayTask.java`**

```java
package cz.hashiri.harshlands.foodexpansion;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class NutritionDecayTask extends BukkitRunnable {

    private final Player player;
    private final PlayerNutritionData data;

    // Config values (cached from config at construction)
    private final double baseProteinDecay;
    private final double baseCarbsDecay;
    private final double baseFatsDecay;
    private final double exhaustionThreshold;
    private final double sprintMultiplier;
    private final double miningMultiplier;
    private final double fightingMultiplier;
    private final double swimmingMultiplier;

    // Base rates are per-5-minutes. Task runs every 100 ticks (5 seconds).
    // 5 minutes = 300 seconds = 60 intervals of 5 seconds.
    private static final double INTERVALS_PER_5MIN = 60.0;

    public NutritionDecayTask(Player player, PlayerNutritionData data, FileConfiguration config) {
        this.player = player;
        this.data = data;

        this.baseProteinDecay = config.getDouble("FoodExpansion.Decay.Protein", 1.0);
        this.baseCarbsDecay = config.getDouble("FoodExpansion.Decay.Carbs", 1.5);
        this.baseFatsDecay = config.getDouble("FoodExpansion.Decay.Fats", 0.5);
        this.exhaustionThreshold = config.getDouble("FoodExpansion.Activity.ExhaustionThreshold", 4.0);
        this.sprintMultiplier = config.getDouble("FoodExpansion.Activity.Sprinting", 1.5);
        this.miningMultiplier = config.getDouble("FoodExpansion.Activity.Mining", 1.2);
        this.fightingMultiplier = config.getDouble("FoodExpansion.Activity.Fighting", 1.3);
        this.swimmingMultiplier = config.getDouble("FoodExpansion.Activity.Swimming", 1.4);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        // 1. Accumulate activity exhaustion
        if (player.isSprinting()) {
            data.addCarbsExhaustion(sprintMultiplier);
        }
        if (player.isSwimming()) {
            data.addCarbsExhaustion(swimmingMultiplier);
            data.addFatsExhaustion(swimmingMultiplier);
        }
        if (data.consumeMiningFlag()) {
            data.addCarbsExhaustion(miningMultiplier);
        }
        if (data.consumeFightingFlag()) {
            data.addProteinExhaustion(fightingMultiplier);
            data.addFatsExhaustion(fightingMultiplier);
        }

        // 2. Convert exhaustion to decay
        double exhaustProtein = data.drainProteinExhaustion(exhaustionThreshold);
        double exhaustCarbs = data.drainCarbsExhaustion(exhaustionThreshold);
        double exhaustFats = data.drainFatsExhaustion(exhaustionThreshold);

        // 3. Passive time-based decay
        double passiveProtein = baseProteinDecay / INTERVALS_PER_5MIN;
        double passiveCarbs = baseCarbsDecay / INTERVALS_PER_5MIN;
        double passiveFats = baseFatsDecay / INTERVALS_PER_5MIN;

        // 4. Apply total decay
        data.applyDecay(
            passiveProtein + exhaustProtein,
            passiveCarbs + exhaustCarbs,
            passiveFats + exhaustFats
        );
    }
}
```

- [ ] **Step 2: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutritionDecayTask.java
git commit -m "Add NutritionDecayTask with passive + activity-scaled decay"
```

---

## Task 7: NutritionEffectTask (Attributes + HUD)

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutritionEffectTask.java`

Per-player BukkitRunnable running every 40 ticks. Evaluates tier, applies/removes attribute modifiers on tier change, handles starvation damage, updates HUD elements.

- [ ] **Step 1: Create `NutritionEffectTask.java`**

```java
package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.tan.TanModule;
import cz.hashiri.harshlands.utils.BossbarHUD;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.Map;

public class NutritionEffectTask extends BukkitRunnable {

    private final Player player;
    private final PlayerNutritionData data;
    private final BossbarHUD hud;

    // Config values
    private final double wellNourishedThreshold;
    private final double wellNourishedHydration;
    private final double peakThreshold;
    private final double peakHydration;
    private final double malnourishedThreshold;
    private final double severeThreshold;

    private final double wellNourishedMaxHealth;
    private final double wellNourishedSpeed;
    private final double peakMaxHealth;
    private final double peakSpeed;
    private final double peakAttackDamage;
    private final double malnourishedMiningSpeed;
    private final double severeAttackDamage;
    private final double severeSpeed;

    private final double starvationDamage;
    private final int starvationInterval;

    private final int hudProteinX;
    private final int hudCarbsX;
    private final int hudFatsX;

    // NamespacedKeys for attribute modifiers (use plugin instance to avoid deprecation)
    private static final NamespacedKey KEY_MAX_HEALTH = new NamespacedKey(HLPlugin.getPlugin(), "nutrition_max_health");
    private static final NamespacedKey KEY_SPEED = new NamespacedKey(HLPlugin.getPlugin(), "nutrition_speed");
    private static final NamespacedKey KEY_ATTACK = new NamespacedKey(HLPlugin.getPlugin(), "nutrition_attack");
    private static final NamespacedKey KEY_MINING = new NamespacedKey(HLPlugin.getPlugin(), "nutrition_mining");

    public NutritionEffectTask(Player player, PlayerNutritionData data, BossbarHUD hud, FileConfiguration config) {
        this.player = player;
        this.data = data;
        this.hud = hud;

        this.wellNourishedThreshold = config.getDouble("FoodExpansion.Effects.WellNourished.Threshold", 60);
        this.wellNourishedHydration = config.getDouble("FoodExpansion.Effects.WellNourished.HydrationThreshold", 60);
        this.peakThreshold = config.getDouble("FoodExpansion.Effects.PeakNutrition.Threshold", 80);
        this.peakHydration = config.getDouble("FoodExpansion.Effects.PeakNutrition.HydrationThreshold", 80);
        this.malnourishedThreshold = config.getDouble("FoodExpansion.Effects.Malnourished.Threshold", 30);
        this.severeThreshold = config.getDouble("FoodExpansion.Effects.SeverelyMalnourished.Threshold", 15);

        this.wellNourishedMaxHealth = config.getDouble("FoodExpansion.Effects.WellNourished.MaxHealthBonus", 2.0);
        this.wellNourishedSpeed = config.getDouble("FoodExpansion.Effects.WellNourished.SpeedMultiplier", 0.10);
        this.peakMaxHealth = config.getDouble("FoodExpansion.Effects.PeakNutrition.MaxHealthBonus", 4.0);
        this.peakSpeed = config.getDouble("FoodExpansion.Effects.PeakNutrition.SpeedMultiplier", 0.10);
        this.peakAttackDamage = config.getDouble("FoodExpansion.Effects.PeakNutrition.AttackDamageBonus", 0.10);
        this.malnourishedMiningSpeed = config.getDouble("FoodExpansion.Effects.Malnourished.MiningSpeedReduction", 0.30);
        this.severeAttackDamage = config.getDouble("FoodExpansion.Effects.SeverelyMalnourished.AttackDamageReduction", 0.20);
        this.severeSpeed = config.getDouble("FoodExpansion.Effects.SeverelyMalnourished.SpeedReduction", 0.20);

        this.starvationDamage = config.getDouble("FoodExpansion.Effects.Starving.DamagePerTick", 1.0);
        this.starvationInterval = config.getInt("FoodExpansion.Effects.Starving.DamageInterval", 80);

        this.hudProteinX = config.getInt("FoodExpansion.HUD.Protein.X", -120);
        this.hudCarbsX = config.getInt("FoodExpansion.HUD.Carbs.X", -80);
        this.hudFatsX = config.getInt("FoodExpansion.HUD.Fats.X", -40);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        // 1. Get hydration from TAN
        double hydrationPercent = getHydrationPercent();

        // 2. Evaluate tier
        NutrientTier newTier = data.evaluateTier(
            hydrationPercent,
            wellNourishedThreshold, wellNourishedHydration,
            peakThreshold, peakHydration,
            malnourishedThreshold, severeThreshold
        );

        // 3. Apply modifiers on tier change
        NutrientTier oldTier = data.getCachedTier();
        if (newTier != oldTier) {
            removeAllModifiers();
            applyModifiers(newTier);
            data.setCachedTier(newTier);
        }

        // 4. Starvation damage
        if (newTier == NutrientTier.STARVING) {
            data.incrementStarvationTickCounter(40); // this task runs every 40 ticks
            if (data.getStarvationTickCounter() >= starvationInterval) {
                data.setStarvationTickCounter(0);
                player.damage(starvationDamage);
            }
        } else {
            data.setStarvationTickCounter(0);
        }

        // 5. Update HUD
        updateHud();
    }

    private double getHydrationPercent() {
        TanModule tan = (TanModule) HLModule.getModule(TanModule.NAME);
        if (tan != null && tan.isGloballyEnabled()) {
            int thirst = tan.getThirstManager().getThirst(player);
            return (thirst / 20.0) * 100.0;
        }
        return 100.0; // If TAN not loaded, hydration always satisfied
    }

    // --- Attribute Modifiers ---

    public void removeAllModifiers() {
        removeModifier(Attribute.MAX_HEALTH, KEY_MAX_HEALTH);
        removeModifier(Attribute.MOVEMENT_SPEED, KEY_SPEED);
        removeModifier(Attribute.ATTACK_DAMAGE, KEY_ATTACK);
        removeModifier(Attribute.BLOCK_BREAK_SPEED, KEY_MINING);
    }

    private void removeModifier(Attribute attribute, NamespacedKey key) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst != null) {
            inst.removeModifier(key);
        }
    }

    public void applyModifiers(NutrientTier tier) {
        switch (tier) {
            case PEAK_NUTRITION -> {
                addModifier(Attribute.MAX_HEALTH, KEY_MAX_HEALTH, peakMaxHealth, AttributeModifier.Operation.ADD_NUMBER);
                addModifier(Attribute.MOVEMENT_SPEED, KEY_SPEED, peakSpeed, AttributeModifier.Operation.ADD_SCALAR);
                addModifier(Attribute.ATTACK_DAMAGE, KEY_ATTACK, peakAttackDamage, AttributeModifier.Operation.ADD_SCALAR);
            }
            case WELL_NOURISHED -> {
                addModifier(Attribute.MAX_HEALTH, KEY_MAX_HEALTH, wellNourishedMaxHealth, AttributeModifier.Operation.ADD_NUMBER);
                addModifier(Attribute.MOVEMENT_SPEED, KEY_SPEED, wellNourishedSpeed, AttributeModifier.Operation.ADD_SCALAR);
            }
            case MALNOURISHED -> {
                addModifier(Attribute.BLOCK_BREAK_SPEED, KEY_MINING, -malnourishedMiningSpeed, AttributeModifier.Operation.ADD_SCALAR);
            }
            case SEVERELY_MALNOURISHED -> {
                addModifier(Attribute.ATTACK_DAMAGE, KEY_ATTACK, -severeAttackDamage, AttributeModifier.Operation.ADD_SCALAR);
                addModifier(Attribute.MOVEMENT_SPEED, KEY_SPEED, -severeSpeed, AttributeModifier.Operation.ADD_SCALAR);
            }
            // NORMAL, STARVING — no attribute modifiers (starving uses direct damage instead)
            default -> {}
        }
    }

    private void addModifier(Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst != null) {
            inst.removeModifier(key); // Remove first to avoid duplicates
            inst.addModifier(new AttributeModifier(key, amount, operation));
        }
    }

    // --- HUD ---

    private void updateHud() {
        int p = (int) data.getProtein();
        int c = (int) data.getCarbs();
        int f = (int) data.getFats();

        // Only update if changed
        if (p == data.getLastHudProtein() && c == data.getLastHudCarbs() && f == data.getLastHudFats()) {
            return;
        }
        data.setLastHudValues(p, c, f);

        hud.setElement("nutrition_protein", hudProteinX, Component.text("P: " + p, colorForValue(p)));
        hud.setElement("nutrition_carbs", hudCarbsX, Component.text("C: " + c, colorForValue(c)));
        hud.setElement("nutrition_fats", hudFatsX, Component.text("F: " + f, colorForValue(f)));
    }

    private NamedTextColor colorForValue(int value) {
        if (value >= 60) return NamedTextColor.GREEN;
        if (value >= 30) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    public void removeHudElements() {
        hud.removeElement("nutrition_protein");
        hud.removeElement("nutrition_carbs");
        hud.removeElement("nutrition_fats");
    }
}
```

**IMPORTANT: Check the exact Bukkit 1.21 `Attribute` enum names.** The spec uses `GENERIC_MAX_HEALTH`, `GENERIC_MOVEMENT_SPEED`, etc. In Bukkit 1.21+, these may be `Attribute.MAX_HEALTH`, `Attribute.MOVEMENT_SPEED`, `Attribute.ATTACK_DAMAGE`, `Attribute.BLOCK_BREAK_SPEED`. Verify by checking the project's Bukkit API dependency. The code above uses the 1.21+ names — adjust if compilation fails.

**IMPORTANT: Check `AttributeModifier` constructor.** In Bukkit 1.21+, the constructor is `new AttributeModifier(NamespacedKey, double, Operation)`. In older versions it was `new AttributeModifier(UUID, String, double, Operation)`. The code above uses the 1.21+ constructor. Verify against the project's API version.

- [ ] **Step 2: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS. If `Attribute` enum names or `AttributeModifier` constructor don't match, check imports and adjust to match the project's Bukkit API version.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/NutritionEffectTask.java
git commit -m "Add NutritionEffectTask with attribute modifiers and HUD updates"
```

---

## Task 8: FoodExpansionEvents

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java`

Event listener handling: food consumption (nutrient addition + comfort bonus), vanilla hunger slowdown (debt accumulator), death penalty, respawn modifier reapplication, join/quit lifecycle, activity flag setting, world change.

- [ ] **Step 1: Create `FoodExpansionEvents.java`**

```java
package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.comfort.ComfortModule;
import cz.hashiri.harshlands.comfort.ComfortScoreCalculator;
import cz.hashiri.harshlands.comfort.ComfortTier;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.BossbarHUD;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FoodExpansionEvents implements Listener {

    private final FoodExpansionModule module;
    private final HLPlugin plugin;

    // Per-player tasks for cleanup on quit
    private final Map<UUID, BukkitTask> decayTasks = new HashMap<>();
    private final Map<UUID, NutritionEffectTask> effectTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> effectBukkitTasks = new HashMap<>();

    public FoodExpansionEvents(FoodExpansionModule module, HLPlugin plugin) {
        this.module = module;
        this.plugin = plugin;
    }

    // --- Food Consumption ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        String itemKey = event.getItem().getType().name();
        NutrientProfile profile = module.getNutrientProfile(itemKey);
        if (profile == null) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        // Comfort bonus
        double multiplier = 1.0;
        FileConfiguration config = module.getUserConfig().getConfig();
        if (config.getBoolean("FoodExpansion.Comfort.Enabled", true)) {
            ComfortModule cm = (ComfortModule) HLModule.getModule(ComfortModule.NAME);
            if (cm != null && cm.isGloballyEnabled()) {
                ComfortScoreCalculator.ComfortResult result = cm.getCachedResult(player, 60);
                if (result != null) {
                    String minTierStr = config.getString("FoodExpansion.Comfort.MinTier", "HOME");
                    try {
                        ComfortTier minTier = ComfortTier.valueOf(minTierStr.toUpperCase());
                        if (result.getTier().ordinal() >= minTier.ordinal()) {
                            multiplier = 1.0 + config.getDouble("FoodExpansion.Comfort.AbsorptionBonus", 0.10);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Invalid tier name in config — skip bonus
                    }
                }
            }
        }

        data.addNutrients(profile, multiplier);
    }

    // --- Vanilla Hunger Slowdown ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!module.isEnabled(player)) return;

        int oldLevel = player.getFoodLevel();
        int newLevel = event.getFoodLevel();

        // Only slow down hunger drain, not eating
        if (newLevel >= oldLevel) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        double drainMultiplier = module.getUserConfig().getConfig()
            .getDouble("FoodExpansion.VanillaHunger.DrainMultiplier", 0.5);

        int decrease = oldLevel - newLevel;
        double scaledDebt = decrease * drainMultiplier;
        data.addHungerDebt(scaledDebt);

        if (data.getHungerDebtAccumulator() >= 1.0) {
            int actualDecrease = (int) Math.floor(data.getHungerDebtAccumulator());
            data.setHungerDebtAccumulator(data.getHungerDebtAccumulator() - actualDecrease);
            event.setFoodLevel(oldLevel - actualDecrease);
        } else {
            event.setCancelled(true);
        }
    }

    // --- Death / Respawn ---

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!module.isEnabled(player)) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        double percentLoss = module.getUserConfig().getConfig()
            .getDouble("FoodExpansion.DeathPenalty.PercentLoss", 25.0);
        data.applyDeathPenalty(percentLoss);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        // Re-apply attribute modifiers (death clears them)
        // Delay by 1 tick to ensure player is fully respawned
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            NutritionEffectTask effectTask = effectTasks.get(player.getUniqueId());
            if (effectTask != null) {
                PlayerNutritionData data = getNutritionData(player);
                if (data != null) {
                    effectTask.applyModifiers(data.getCachedTier());
                }
            }
        }, 1L);
    }

    // --- Join / Quit ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        // Data loading is handled by HLPlayer.retrieveData() which calls our DataModule.
        // We start tasks after a short delay to allow async DB load to complete.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            PlayerNutritionData data = getNutritionData(player);
            if (data == null) return;

            startTasks(player, data);
        }, 20L); // 1 second delay for DB load
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        stopTasks(uuid);

        // Save if dirty (handled by HLPlayer.saveData() in main quit flow)
    }

    // --- Activity Detection ---

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data != null) {
            data.setMiningFlag();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!module.isEnabled(player)) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data != null) {
            data.setFightingFlag();
        }
    }

    // --- World Change ---

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean wasEnabled = module.isEnabled(event.getFrom());
        boolean nowEnabled = module.isEnabled(player.getWorld());

        if (wasEnabled && !nowEnabled) {
            // Entering disabled world — stopTasks() handles modifier removal and HUD cleanup
            stopTasks(uuid);
        } else if (!wasEnabled && nowEnabled) {
            // Entering enabled world — start tasks
            PlayerNutritionData data = getNutritionData(player);
            if (data != null) {
                startTasks(player, data);
            }
        }
    }

    // --- Task Management ---

    private void startTasks(Player player, PlayerNutritionData data) {
        UUID uuid = player.getUniqueId();

        // Don't double-start
        if (decayTasks.containsKey(uuid)) return;

        FileConfiguration config = module.getUserConfig().getConfig();

        // Get or create BossbarHUD for this player
        BossbarHUD hud = module.getOrCreateHud(player);

        NutritionDecayTask decayTask = new NutritionDecayTask(player, data, config);
        BukkitTask decayBukkit = decayTask.runTaskTimer(plugin, 100L, 100L);
        decayTasks.put(uuid, decayBukkit);

        NutritionEffectTask effectTask = new NutritionEffectTask(player, data, hud, config);
        BukkitTask effectBukkit = effectTask.runTaskTimer(plugin, 40L, 40L);
        effectTasks.put(uuid, effectTask);
        effectBukkitTasks.put(uuid, effectBukkit);
    }

    public void stopTasks(UUID uuid) {
        BukkitTask decay = decayTasks.remove(uuid);
        if (decay != null) decay.cancel();

        NutritionEffectTask effect = effectTasks.remove(uuid);
        if (effect != null) {
            effect.removeAllModifiers();
            effect.removeHudElements();
        }

        BukkitTask effectBukkit = effectBukkitTasks.remove(uuid);
        if (effectBukkit != null) effectBukkit.cancel();
    }

    public void stopAllTasks() {
        for (UUID uuid : new java.util.ArrayList<>(decayTasks.keySet())) {
            stopTasks(uuid);
        }
    }

    // --- Utility ---

    private PlayerNutritionData getNutritionData(Player player) {
        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hlPlayer == null) return null;
        cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
        return dm != null ? dm.getData() : null;
    }
}
```

**NOTE:** `ComfortResult.getTier()` returns `ComfortTier` enum (not String). The code uses `ordinal()` comparison against `ComfortTier.valueOf()` parsed from config. Verify the exact enum values in `ComfortTier.java` match: NONE, SHELTER, HOME, COZY, LUXURY.

**NOTE:** The `module.isEnabled(World)` and `module.isEnabled(Player)` calls come from `HLModule` base class. `isEnabled(entity)` checks if the module is enabled in the entity's world.

- [ ] **Step 2: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS. Fix any API mismatches (ComfortResult method names, ModuleEvents constructor, etc.)

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java
git commit -m "Add FoodExpansionEvents with consume, hunger slowdown, lifecycle, and activity"
```

---

## Task 9: FoodExpansionModule

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java`

The main module class. Follows `TanModule` pattern: constructor calls `super(NAME, plugin, ...)`, `initialize()` loads config, builds food map, registers events, starts auto-save, registers debug provider.

- [ ] **Step 1: Create `FoodExpansionModule.java`**

```java
package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.BossbarHUD;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FoodExpansionModule extends HLModule {

    public static final String NAME = "FoodExpansion";

    private final HLPlugin plugin;
    private final Map<String, NutrientProfile> foodMap = new HashMap<>();
    private NutrientProfile defaultFood;
    private FoodExpansionEvents events;

    // Per-player BossbarHUD references (may be shared with DisplayTask from TAN)
    private final Map<UUID, BossbarHUD> playerHuds = new HashMap<>();

    public FoodExpansionModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of()); // No hard deps, soft deps handled at runtime
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "foodexpansion.yml"));

        // Register debug provider
        HLPlugin.getPlugin().getDebugManager().registerProvider(new cz.hashiri.harshlands.debug.DebugProvider() {
            @Override public String getModuleName() { return NAME; }
            @Override public java.util.Collection<String> getSubsystems() {
                return List.of("Decay", "Effects", "Tiers");
            }
        });

        loadFoodMap();

        Utils.logModuleLifecycle("Initializing", NAME);

        events = new FoodExpansionEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(events, plugin);

        // Auto-save every 5 minutes (6000 ticks), SYNC timer so saveData() snapshots on main thread
        // (the actual DB write inside saveData() is already async via HLScheduler)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (HLPlayer hlPlayer : new java.util.ArrayList<>(HLPlayer.getPlayers().values())) {
                cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
                if (dm != null && dm.isDirty()) {
                    dm.saveData();
                }
            }
        }, 6000L, 6000L);
    }

    @Override
    public void shutdown() {
        if (events != null) {
            events.stopAllTasks();
            HandlerList.unregisterAll(events);
        }

        // Remove all attribute modifiers from online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isEnabled(player)) continue;
            NutritionEffectTask dummyForRemoval = null;
            // Modifiers are already removed in stopAllTasks via stopTasks()
        }

        playerHuds.clear();
    }

    private void loadFoodMap() {
        FileConfiguration config = getUserConfig().getConfig();

        // Load default food profile
        double defP = config.getDouble("FoodExpansion.DefaultFood.Protein", 2.0);
        double defC = config.getDouble("FoodExpansion.DefaultFood.Carbs", 2.0);
        double defF = config.getDouble("FoodExpansion.DefaultFood.Fats", 1.0);
        this.defaultFood = new NutrientProfile(defP, defC, defF);

        // Load per-food profiles
        ConfigurationSection foods = config.getConfigurationSection("FoodExpansion.Foods");
        if (foods != null) {
            for (String key : foods.getKeys(false)) {
                double protein = foods.getDouble(key + ".Protein", 0.0);
                double carbs = foods.getDouble(key + ".Carbs", 0.0);
                double fats = foods.getDouble(key + ".Fats", 0.0);
                foodMap.put(key.toUpperCase(), new NutrientProfile(protein, carbs, fats));
            }
        }
    }

    /**
     * Returns the NutrientProfile for a food item key (Material name).
     * Returns the default food profile if the item is a food but not explicitly configured.
     * Returns null if the item is not food.
     */
    public NutrientProfile getNutrientProfile(String itemKey) {
        NutrientProfile profile = foodMap.get(itemKey.toUpperCase());
        if (profile != null) return profile;

        // Return default for unlisted foods (the consume event already filters non-food items)
        if (defaultFood.protein() == 0.0 && defaultFood.carbs() == 0.0 && defaultFood.fats() == 0.0) {
            return null; // Default is all zeros — treat as "no nutrition data"
        }
        return defaultFood;
    }

    public BossbarHUD getOrCreateHud(Player player) {
        // Check if TAN's DisplayTask already created a HUD for this player
        // For now, create our own. Integration with shared HUD can be refined later.
        return playerHuds.computeIfAbsent(player.getUniqueId(), uuid -> {
            BossbarHUD hud = new BossbarHUD(player);
            hud.show();
            return hud;
        });
    }

    public void removeHud(UUID uuid) {
        BossbarHUD hud = playerHuds.remove(uuid);
        if (hud != null) {
            hud.hide();
        }
    }

    public FoodExpansionEvents getEvents() {
        return events;
    }
}
```

**IMPORTANT:** The `BossbarHUD` constructor takes `Audience` (which `Player` implements on Paper). However, TAN's `DisplayTask` already manages a `BossbarHUD` per player. The implementor MUST check if there's a way to access the existing HUD (e.g., via a static map in `DisplayTask` or player metadata). If a shared HUD exists, change `getOrCreateHud()` to retrieve it instead of creating a duplicate boss bar. If no shared access exists, a second BossbarHUD is acceptable as a temporary solution.

**NOTE:** Verify `DebugProvider` interface at `cz.hashiri.harshlands.debug.DebugProvider` and `Utils.logModuleLifecycle()` exist with expected signatures. Adjust if they differ.

- [ ] **Step 2: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS. Fix any BossbarHUD, DebugProvider, or Utils API mismatches.

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java
git commit -m "Add FoodExpansionModule with config loading, food map, and lifecycle"
```

---

## Task 10: Commands + Tab Completion

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/commands/Commands.java`
- Modify: `core/src/main/java/cz/hashiri/harshlands/commands/Tab.java`

Add the `nutrition` subcommand (view/set/reset) and tab completions.

- [ ] **Step 1: Read current `Commands.java` and `Tab.java`**

Read both files to find exact insertion points for the new `case "nutrition"` block and tab completions.

- [ ] **Step 2: Add `nutrition` case to `Commands.java`**

Add a new case in the switch block (after the existing cases like `"comfort"`, `"fear"`):

```java
case "nutrition" -> {
    if (!(sender instanceof Player) && args.length < 2) {
        sender.sendMessage("This command must be run as a player or with a target.");
        return true;
    }

    // /hl nutrition set <player> <protein> <carbs> <fats>
    if (args.length >= 6 && args[1].equalsIgnoreCase("set")) {
        if (!sender.hasPermission("harshlands.command.nutrition.set")) {
            sendNoPermissionMessage(sender);
            return true;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Player not found.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        try {
            double protein = Double.parseDouble(args[3]);
            double carbs = Double.parseDouble(args[4]);
            double fats = Double.parseDouble(args[5]);
            cz.hashiri.harshlands.foodexpansion.PlayerNutritionData data = getNutritionData(target);
            if (data == null) {
                sender.sendMessage(net.kyori.adventure.text.Component.text("Nutrition module not active for that player.", net.kyori.adventure.text.format.NamedTextColor.RED));
                return true;
            }
            data.setProtein(Math.max(0, Math.min(100, protein)));
            data.setCarbs(Math.max(0, Math.min(100, carbs)));
            data.setFats(Math.max(0, Math.min(100, fats)));
            sender.sendMessage(net.kyori.adventure.text.Component.text("Set nutrition for " + target.getName() + ".", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        } catch (NumberFormatException e) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Invalid number.", net.kyori.adventure.text.format.NamedTextColor.RED));
        }
        return true;
    }

    // /hl nutrition reset <player>
    if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
        if (!sender.hasPermission("harshlands.command.nutrition.reset")) {
            sendNoPermissionMessage(sender);
            return true;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Player not found.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        cz.hashiri.harshlands.foodexpansion.PlayerNutritionData data = getNutritionData(target);
        if (data == null) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Nutrition module not active for that player.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
        cz.hashiri.harshlands.foodexpansion.FoodExpansionModule fem =
            (cz.hashiri.harshlands.foodexpansion.FoodExpansionModule) HLModule.getModule(cz.hashiri.harshlands.foodexpansion.FoodExpansionModule.NAME);
        org.bukkit.configuration.file.FileConfiguration feConfig = fem.getUserConfig().getConfig();
        data.setProtein(feConfig.getDouble("FoodExpansion.Defaults.Protein", 50.0));
        data.setCarbs(feConfig.getDouble("FoodExpansion.Defaults.Carbs", 50.0));
        data.setFats(feConfig.getDouble("FoodExpansion.Defaults.Fats", 50.0));
        sender.sendMessage(net.kyori.adventure.text.Component.text("Reset nutrition for " + target.getName() + ".", net.kyori.adventure.text.format.NamedTextColor.GREEN));
        return true;
    }

    // /hl nutrition [player] — view
    Player target;
    if (args.length >= 2) {
        if (!sender.hasPermission("harshlands.command.nutrition.others")) {
            sendNoPermissionMessage(sender);
            return true;
        }
        target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(net.kyori.adventure.text.Component.text("Player not found.", net.kyori.adventure.text.format.NamedTextColor.RED));
            return true;
        }
    } else {
        if (!sender.hasPermission("harshlands.command.nutrition")) {
            sendNoPermissionMessage(sender);
            return true;
        }
        target = (Player) sender;
    }

    cz.hashiri.harshlands.foodexpansion.PlayerNutritionData data = getNutritionData(target);
    if (data == null) {
        sender.sendMessage(net.kyori.adventure.text.Component.text("Nutrition module not active for that player.", net.kyori.adventure.text.format.NamedTextColor.RED));
        return true;
    }

    sender.sendMessage(net.kyori.adventure.text.Component.text("--- Nutrition Status ---", net.kyori.adventure.text.format.NamedTextColor.GOLD));
    sender.sendMessage(buildNutrientBar("Protein", data.getProtein()));
    sender.sendMessage(buildNutrientBar("Carbs", data.getCarbs()));
    sender.sendMessage(buildNutrientBar("Fats", data.getFats()));
    sender.sendMessage(net.kyori.adventure.text.Component.text("Status: " + data.getCachedTier().name().replace("_", " "),
        net.kyori.adventure.text.format.NamedTextColor.GRAY));
    return true;
}
```

Also add these helper methods to `Commands.java`:

```java
private cz.hashiri.harshlands.foodexpansion.PlayerNutritionData getNutritionData(Player player) {
    HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
    if (hlPlayer == null) return null;
    cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
    return dm != null ? dm.getData() : null;
}

private net.kyori.adventure.text.Component buildNutrientBar(String label, double value) {
    int filled = (int) (value / 10.0);
    int empty = 10 - filled;
    net.kyori.adventure.text.format.NamedTextColor color =
        value >= 60 ? net.kyori.adventure.text.format.NamedTextColor.GREEN
        : value >= 30 ? net.kyori.adventure.text.format.NamedTextColor.YELLOW
        : net.kyori.adventure.text.format.NamedTextColor.RED;

    String bar = "█".repeat(filled) + "░".repeat(empty);
    return net.kyori.adventure.text.Component.text(
        String.format("%-8s %s %.1f/100", label + ":", bar, value), color);
}
```

- [ ] **Step 3: Add `nutrition` to `Tab.java`**

In the `firstArgs` set (line 75), add `"nutrition"`:

```java
firstArgs.addAll(Set.of("reload", "give", "spawnitem", "summon", "thirst", "temperature",
    "resetitem", "updateitem", "fear", "setfear", "comfort", "help", "version", "debug", "nutrition"));
```

In the `args.length == 2` switch block, add:

```java
case "nutrition" -> {
    // Suggest subcommands and player names
    List<String> suggestions = new java.util.ArrayList<>();
    suggestions.add("set");
    suggestions.add("reset");
    for (Player p : Bukkit.getOnlinePlayers()) {
        suggestions.add(p.getName());
    }
    return suggestions.stream()
        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
        .collect(java.util.stream.Collectors.toList());
}
```

In the `args.length == 3` section, add:

```java
case "nutrition" -> {
    if (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("reset")) {
        // Suggest player names
        return Bukkit.getOnlinePlayers().stream()
            .map(Player::getName)
            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
            .collect(java.util.stream.Collectors.toList());
    }
}
```

- [ ] **Step 4: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/commands/Commands.java \
  core/src/main/java/cz/hashiri/harshlands/commands/Tab.java
git commit -m "Add /hl nutrition command with view, set, and reset subcommands"
```

---

## Task 11: Fear Integration

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/fear/FearConditionEvaluator.java`
- Modify: `core/src/main/resources/fear.yml`

Add the `Malnourished` condition to the fear evaluator, following the exact pattern of existing conditions.

- [ ] **Step 1: Read `FearConditionEvaluator.java`**

Read the file to find the exact insertion point in the `evaluate()` method and the pattern for condition checks.

- [ ] **Step 2: Add `Malnourished` condition to `evaluate()` method**

After the existing condition evaluations (around line 120, after `storm` and `night`), add:

```java
double malnourished = config.getBoolean("FearMeter.Conditions.Malnourished.Enabled", true) ? evalMalnourished(player) : 0.0;
```

Add `malnourished` to the `netDelta` sum:

```java
double netDelta = darkness + cave + underground + lowHealth + enemies + cold + storm + night + malnourished
                - brightLight - nearFire - companions;
```

- [ ] **Step 3: Add `evalMalnourished()` method**

Add the evaluation method following the pattern of `evalDarkness()`:

```java
private double evalMalnourished(Player player) {
    cz.hashiri.harshlands.data.HLModule feModule = cz.hashiri.harshlands.data.HLModule.getModule(
        cz.hashiri.harshlands.foodexpansion.FoodExpansionModule.NAME);
    if (feModule == null || !feModule.isGloballyEnabled()) return 0.0;

    cz.hashiri.harshlands.data.HLPlayer hlPlayer = cz.hashiri.harshlands.data.HLPlayer.getPlayers()
        .get(player.getUniqueId());
    if (hlPlayer == null) return 0.0;

    cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
    if (dm == null) return 0.0;

    double threshold = config.getDouble("FearMeter.Conditions.Malnourished.Threshold", 30.0);
    double rate = config.getDouble("FearMeter.Conditions.Malnourished.Amount", 3.0);
    int count = dm.getData().countBelowThreshold(threshold);
    return count * rate;
}
```

- [ ] **Step 4: Add `Malnourished` section to `fear.yml`**

Under `FearMeter.Conditions:`, after the last existing condition, add:

```yaml
  Malnourished:
    Enabled: true
    Threshold: 30.0
    Amount: 3.0
```

- [ ] **Step 5: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/fear/FearConditionEvaluator.java \
  core/src/main/resources/fear.yml
git commit -m "Add Malnourished condition to FearConditionEvaluator"
```

---

## Task 12: Plugin Wiring + Config/Permissions

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/rsv/HLPlugin.java`
- Modify: `core/src/main/resources/config.yml`
- Modify: `core/src/main/resources/plugin.yml`

Wire `FoodExpansionModule` into the plugin startup, add the config toggle, and register permissions.

- [ ] **Step 1: Read current `HLPlugin.java`**

Read the `onEnable()` method to find the exact insertion point for initializing `FoodExpansionModule` (after ComfortModule initialization).

- [ ] **Step 2: Add `FoodExpansionModule` initialization to `onEnable()`**

After the ComfortModule initialization block:

```java
FoodExpansionModule foodExpansionModule = new FoodExpansionModule(this);
if (foodExpansionModule.isGloballyEnabled())
    foodExpansionModule.initialize();
```

Add the import at the top of the file:

```java
import cz.hashiri.harshlands.foodexpansion.FoodExpansionModule;
```

- [ ] **Step 3: Add `FoodExpansion` section to `config.yml`**

After the Comfort section, add:

```yaml
FoodExpansion:
  Enabled: true
  Worlds:
    world: true
    world_nether: true
    world_the_end: true
```

- [ ] **Step 4: Add permissions to `plugin.yml`**

In the permissions section, add:

```yaml
  harshlands.command.nutrition:
    description: View your nutrition status
    default: true
  harshlands.command.nutrition.others:
    description: View other players' nutrition status
    default: op
  harshlands.command.nutrition.set:
    description: Set a player's nutrition values
    default: op
  harshlands.command.nutrition.reset:
    description: Reset a player's nutrition to defaults
    default: op
```

Add as children of `harshlands.command.*`:

```yaml
      harshlands.command.nutrition: true
      harshlands.command.nutrition.others: true
      harshlands.command.nutrition.set: true
      harshlands.command.nutrition.reset: true
```

- [ ] **Step 5: Build to verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/rsv/HLPlugin.java \
  core/src/main/resources/config.yml \
  core/src/main/resources/plugin.yml
git commit -m "Wire FoodExpansionModule into plugin startup with config and permissions"
```

---

## Task 13: Final Build + Integration Verification

**Files:** None new — verification only.

- [ ] **Step 1: Full clean build**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS with no warnings related to nutrition/foodexpansion classes.

- [ ] **Step 2: Verify output JAR contains all new classes**

Run: `jar tf dist/target/harshlands-*.jar | grep foodexpansion`
Expected: All 9 new class files listed:
```
cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.class
cz/hashiri/harshlands/foodexpansion/NutrientProfile.class
cz/hashiri/harshlands/foodexpansion/NutrientTier.class
cz/hashiri/harshlands/foodexpansion/PlayerNutritionData.class
cz/hashiri/harshlands/foodexpansion/NutritionDecayTask.class
cz/hashiri/harshlands/foodexpansion/NutritionEffectTask.class
cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.class
cz/hashiri/harshlands/data/foodexpansion/DataModule.class
```

- [ ] **Step 3: Verify config in JAR**

Run: `jar tf dist/target/harshlands-*.jar | grep foodexpansion.yml`
Expected: `foodexpansion.yml`

- [ ] **Step 4: Review all changes**

Run: `git log --oneline` to see all commits in order. Run `git diff master~12..master --stat` to see the full change summary.

- [ ] **Step 5: Commit (if any fixes were needed)**

If any build issues were found and fixed during verification, commit the fixes.
