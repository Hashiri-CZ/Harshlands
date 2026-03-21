# Overeating System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow players to eat food when their hunger bar is full, with per-food diminishing returns to encourage dietary variety.

**Architecture:** A `PlayerInteractEvent` handler nudges hunger down by 1 when a player right-clicks food while full, letting vanilla handle the eating animation and item consumption. The existing `onPlayerConsume` handler is extended to apply a satiation multiplier for force-eats. A per-food satiation counter in `PlayerNutritionData` tracks repeated consumption, with configurable diminishing returns and chat feedback.

**Tech Stack:** Bukkit/Spigot 1.21.11 API, existing Harshlands plugin architecture

**Spec:** `docs/superpowers/specs/2026-03-21-overeating-system-design.md`

---

## File Map

| File | Responsibility |
|---|---|
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/PlayerNutritionData.java` | Add satiation counter map and methods (5 methods) |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java` | Add `onPlayerInteract` handler, modify `onPlayerConsume` and `onFoodLevelChange`, add tracking state, cleanup on quit/death |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java` | Register satiation decay timer, store/cancel task handle |
| `core/src/main/resources/foodexpansion.yml` | Add `Overeating` config section |

---

## Task 1: Add satiation counter to PlayerNutritionData

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/PlayerNutritionData.java`

- [ ] **Step 1: Add satiation fields and methods**

After the `starvationTickCounter` field (line 26), add:

```java
private final Map<String, Integer> satiationCounters = new HashMap<>();
```

Add import `java.util.HashMap` and `java.util.Iterator` and `java.util.Map` at the top of the file.

Then add these methods before the `getMinNutrient()` method (before line 178):

```java
public int getSatiation(String foodKey) {
    return satiationCounters.getOrDefault(foodKey, 0);
}

public void incrementSatiation(String foodKey) {
    satiationCounters.merge(foodKey, 1, Integer::sum);
}

public void decaySatiationCounters() {
    Iterator<Map.Entry<String, Integer>> it = satiationCounters.entrySet().iterator();
    while (it.hasNext()) {
        Map.Entry<String, Integer> entry = it.next();
        int newVal = entry.getValue() - 1;
        if (newVal <= 0) {
            it.remove();
        } else {
            entry.setValue(newVal);
        }
    }
}

public void clearSatiationCounters() {
    satiationCounters.clear();
}
```

- [ ] **Step 2: Verify build compiles**

Run: `"/c/Program Files/apache-maven-3.9.13/bin/mvn" clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/PlayerNutritionData.java
git commit -m "Add satiation counter map and methods to PlayerNutritionData"
```

---

## Task 2: Add Overeating config section to foodexpansion.yml

**Files:**
- Modify: `core/src/main/resources/foodexpansion.yml`

- [ ] **Step 1: Add config section**

At the end of the file (after the `Foods` section, line 224), add:

```yaml

  Overeating:
    Enabled: true
    HungerThreshold: 20
    DecayIntervalMinutes: 3
    CooldownMs: 500
    Tiers:
      0: 1.0
      1: 0.7
      2: 0.4
      3: 0.15
      4: 0.0
    Messages:
      Warning: 2
      WarningText: "&7You're getting tired of &f{food}&7..."
      Severe: 3
      SevereText: "&7You can barely stomach more &f{food}&7."
      Blocked: 4
      BlockedText: "&cYou can't eat any more &f{food}&c right now."
```

Note: This is indented under `FoodExpansion:` (2-space indent), matching the existing config structure.

- [ ] **Step 2: Commit**

```
git add core/src/main/resources/foodexpansion.yml
git commit -m "Add Overeating config section to foodexpansion.yml"
```

---

## Task 3: Add overeating state and config caching to FoodExpansionEvents

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java`

This task adds the tracking state fields, cached config values, and helper methods. The event handlers are modified in subsequent tasks.

- [ ] **Step 1: Add imports**

Add these imports after the existing import block (after line 27):

```java
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
```

- [ ] **Step 2: Add overeating state fields**

After the `effectBukkitTasks` map (line 48), add:

```java
// Overeating state
private final Set<UUID> forceEatingPlayers = new HashSet<>();
private final Map<UUID, Integer> forceEatingPreNudgeLevel = new HashMap<>();
private final Map<UUID, Long> forceEatCooldowns = new HashMap<>();
```

- [ ] **Step 3: Add cached overeating config fields**

After the `deathPenaltyPercent` field (line 43), add:

```java
private final boolean overeatingEnabled;
private final int hungerThreshold;
private final long cooldownMs;
private final NavigableMap<Integer, Double> satiationTiers;
private final int msgWarningThreshold;
private final String msgWarningText;
private final int msgSevereThreshold;
private final String msgSevereText;
private final int msgBlockedThreshold;
private final String msgBlockedText;
```

- [ ] **Step 4: Initialize cached config values in constructor**

At the end of the constructor (after `this.deathPenaltyPercent = ...;` on line 59), add:

```java
this.overeatingEnabled = config.getBoolean("FoodExpansion.Overeating.Enabled", true);
this.hungerThreshold = config.getInt("FoodExpansion.Overeating.HungerThreshold", 20);
this.cooldownMs = config.getLong("FoodExpansion.Overeating.CooldownMs", 500L);

// Load satiation tiers into a TreeMap for descending iteration
this.satiationTiers = new TreeMap<>();
org.bukkit.configuration.ConfigurationSection tierSection = config.getConfigurationSection("FoodExpansion.Overeating.Tiers");
if (tierSection != null) {
    for (String key : tierSection.getKeys(false)) {
        try {
            satiationTiers.put(Integer.parseInt(key), tierSection.getDouble(key));
        } catch (NumberFormatException ignored) {}
    }
} else {
    // Defaults if config section missing
    satiationTiers.put(0, 1.0);
    satiationTiers.put(1, 0.7);
    satiationTiers.put(2, 0.4);
    satiationTiers.put(3, 0.15);
    satiationTiers.put(4, 0.0);
}

this.msgWarningThreshold = config.getInt("FoodExpansion.Overeating.Messages.Warning", 2);
this.msgWarningText = config.getString("FoodExpansion.Overeating.Messages.WarningText", "&7You're getting tired of &f{food}&7...");
this.msgSevereThreshold = config.getInt("FoodExpansion.Overeating.Messages.Severe", 3);
this.msgSevereText = config.getString("FoodExpansion.Overeating.Messages.SevereText", "&7You can barely stomach more &f{food}&7.");
this.msgBlockedThreshold = config.getInt("FoodExpansion.Overeating.Messages.Blocked", 4);
this.msgBlockedText = config.getString("FoodExpansion.Overeating.Messages.BlockedText", "&cYou can't eat any more &f{food}&c right now.");
```

- [ ] **Step 5: Add helper methods**

Before the `// --- Utility ---` section (before line 288), add:

```java
// --- Overeating Helpers ---

private double getOvereatMultiplier(int satiationCount) {
    for (Map.Entry<Integer, Double> entry : satiationTiers.descendingMap().entrySet()) {
        if (satiationCount >= entry.getKey()) {
            return entry.getValue();
        }
    }
    return 1.0;
}

private void sendOvereatMessage(Player player, String foodKey, int satiationCount) {
    String foodName = formatFoodName(foodKey);
    String msg = null;
    if (satiationCount >= msgBlockedThreshold) {
        msg = msgBlockedText;
    } else if (satiationCount >= msgSevereThreshold) {
        msg = msgSevereText;
    } else if (satiationCount >= msgWarningThreshold) {
        msg = msgWarningText;
    }
    if (msg != null) {
        player.sendMessage(cz.hashiri.harshlands.utils.Utils.translateMsg(
            msg, player, java.util.Map.of("food", foodName)));
    }
}

private static String formatFoodName(String materialName) {
    String[] words = materialName.toLowerCase().split("_");
    StringBuilder sb = new StringBuilder();
    for (String word : words) {
        if (!sb.isEmpty()) sb.append(' ');
        sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
    }
    return sb.toString();
}
```

- [ ] **Step 6: Verify build compiles**

Run: `"/c/Program Files/apache-maven-3.9.13/bin/mvn" clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java
git commit -m "Add overeating state fields, config caching, and helper methods"
```

---

## Task 4: Add PlayerInteractEvent handler for hunger nudge

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java`

- [ ] **Step 1: Add the onPlayerInteract handler**

After the `onPlayerConsume` method (after line 99, before `// --- Vanilla Hunger Slowdown ---`), add:

```java
// --- Overeating: Hunger Nudge ---

@EventHandler(priority = EventPriority.HIGH)
public void onPlayerInteract(PlayerInteractEvent event) {
    if (!overeatingEnabled) return;
    if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
    if (event.getHand() == null) return;

    Player player = event.getPlayer();
    if (!module.isEnabled(player)) return;

    // Get the food item from the hand that triggered the event
    ItemStack item = player.getInventory().getItem(event.getHand());
    if (item == null || !item.getType().isEdible()) return;

    // Only nudge if hunger is at or above threshold (player can't eat normally)
    if (player.getFoodLevel() < hungerThreshold) return;

    UUID uuid = player.getUniqueId();

    // Cooldown check
    long now = System.currentTimeMillis();
    Long lastNudge = forceEatCooldowns.get(uuid);
    if (lastNudge != null && now - lastNudge < cooldownMs) return;

    // Check satiation — if at hard cap, block eating entirely
    PlayerNutritionData data = getNutritionData(player);
    if (data == null) return;

    String foodKey = item.getType().name();
    int satiation = data.getSatiation(foodKey);
    double multiplier = getOvereatMultiplier(satiation);

    if (multiplier <= 0.0) {
        sendOvereatMessage(player, foodKey, satiation);
        return;
    }

    // Nudge hunger down by 1 so vanilla allows eating
    int preNudgeLevel = player.getFoodLevel();
    player.setFoodLevel(preNudgeLevel - 1);

    // Track this force-eat
    forceEatingPlayers.add(uuid);
    forceEatingPreNudgeLevel.put(uuid, preNudgeLevel);
    forceEatCooldowns.put(uuid, now);

    // Timeout cleanup: if PlayerItemConsumeEvent never fires (player cancelled eating),
    // restore hunger and clear flags after 5 seconds
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (forceEatingPlayers.remove(uuid)) {
            Integer preLevel = forceEatingPreNudgeLevel.remove(uuid);
            if (preLevel != null && player.isOnline() && player.getFoodLevel() == preLevel - 1) {
                player.setFoodLevel(preLevel);
            }
        }
    }, 100L);
}
```

- [ ] **Step 2: Verify build compiles**

Run: `"/c/Program Files/apache-maven-3.9.13/bin/mvn" clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java
git commit -m "Add PlayerInteractEvent handler for hunger nudge on force-eat"
```

---

## Task 5: Guard onFoodLevelChange against nudge cancellation

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java`

- [ ] **Step 1: Add forceEatingPlayers guard**

In the `onFoodLevelChange` method, after the `if (!module.isEnabled(player)) return;` line, add:

```java
// Skip drain-slowing for force-eat hunger nudges
if (forceEatingPlayers.contains(player.getUniqueId())) return;
```

This goes right after `if (!module.isEnabled(player)) return;` and before `int oldLevel = player.getFoodLevel();`.

- [ ] **Step 2: Verify build compiles**

Run: `"/c/Program Files/apache-maven-3.9.13/bin/mvn" clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java
git commit -m "Guard onFoodLevelChange against force-eat nudge cancellation"
```

---

## Task 6: Modify onPlayerConsume for satiation multiplier

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java`

- [ ] **Step 1: Add satiation logic to onPlayerConsume**

Replace the last line of `onPlayerConsume` (currently `data.addNutrients(profile, multiplier);`) with:

```java
// Overeating: apply satiation multiplier for force-eats
UUID uuid = player.getUniqueId();
if (overeatingEnabled && forceEatingPlayers.remove(uuid)) {
    forceEatingPreNudgeLevel.remove(uuid);
    String foodKey = mat.name();
    int satiation = data.getSatiation(foodKey);
    double overeatMultiplier = getOvereatMultiplier(satiation);
    multiplier *= overeatMultiplier;
    data.incrementSatiation(foodKey);
    sendOvereatMessage(player, foodKey, satiation + 1);
}

data.addNutrients(profile, multiplier);
```

- [ ] **Step 2: Verify build compiles**

Run: `"/c/Program Files/apache-maven-3.9.13/bin/mvn" clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java
git commit -m "Apply satiation multiplier in onPlayerConsume for force-eats"
```

---

## Task 7: Add cleanup on quit and death reset

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java`

- [ ] **Step 1: Clean up overeating state on quit**

In the `onPlayerQuit` method, after `stopTasks(uuid);`, add:

```java
// Clean up overeating state
forceEatingPlayers.remove(uuid);
forceEatingPreNudgeLevel.remove(uuid);
forceEatCooldowns.remove(uuid);
```

- [ ] **Step 2: Clear satiation counters on death**

In the `onPlayerDeath` method, after `data.applyDeathPenalty(deathPenaltyPercent);`, add:

```java
data.clearSatiationCounters();
```

- [ ] **Step 3: Verify build compiles**

Run: `"/c/Program Files/apache-maven-3.9.13/bin/mvn" clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java
git commit -m "Add overeating cleanup on quit and satiation reset on death"
```

---

## Task 8: Register satiation decay timer in FoodExpansionModule

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java`

- [ ] **Step 1: Add decay timer field**

After the `autoSaveTask` field (line 33), add:

```java
private BukkitTask satiationDecayTask;
```

- [ ] **Step 2: Register the global decay timer in initialize()**

After the auto-save timer registration (after line 71, the closing `}, 6000L, 6000L);`), add:

```java
// Satiation decay timer — decrements all per-food satiation counters every N minutes
FileConfiguration feConfig = getUserConfig().getConfig();
long decayIntervalTicks = feConfig.getLong("FoodExpansion.Overeating.DecayIntervalMinutes", 3) * 60 * 20;
satiationDecayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
    for (HLPlayer hlPlayer : new java.util.ArrayList<>(HLPlayer.getPlayers().values())) {
        cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
        if (dm != null) {
            dm.getData().decaySatiationCounters();
        }
    }
}, decayIntervalTicks, decayIntervalTicks);
```

- [ ] **Step 3: Cancel the decay timer in shutdown()**

In the `shutdown()` method, after the `autoSaveTask` cancellation (after line 76), add:

```java
if (satiationDecayTask != null) { satiationDecayTask.cancel(); satiationDecayTask = null; }
```

- [ ] **Step 4: Verify build compiles**

Run: `"/c/Program Files/apache-maven-3.9.13/bin/mvn" clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java
git commit -m "Register global satiation decay timer in FoodExpansionModule"
```

---

## Task 9: Final build and manual verification

- [ ] **Step 1: Full clean build**

Run: `"/c/Program Files/apache-maven-3.9.13/bin/mvn" clean package -pl core,dist -am`
Expected: BUILD SUCCESS

- [ ] **Step 2: Verification checklist (manual server testing)**

Test these scenarios on a running server:

1. **Force-eat works**: With full hunger bar (20/20), right-click food. Hunger should briefly dip then restore. Macros should increase. Vanilla eating animation should play.
2. **Diminishing returns**: Eat the same food 4+ times while full. Chat messages should appear at counts 2, 3. At count 4, eating should be blocked with red message.
3. **Food variety resets**: Eat 3 steaks (diminished), then switch to bread. Bread should give full macros.
4. **Satiation decay**: After eating 3 of the same food, wait 3+ minutes. Counter should decay, allowing more eating.
5. **Normal eating unchanged**: With hunger below 20, eating should work as before with no satiation penalty.
6. **Death resets**: Die and respawn. Satiation counters should be cleared.
7. **FoodLevelChange guard**: The hunger nudge should not be cancelled by the drain multiplier logic.
8. **Cancelled eating**: Start eating while full, then switch items mid-eat. Hunger should restore to 20 after 5 seconds.
9. **Creative mode**: In creative, the force-eat handler should not trigger.
10. **Special foods**: Golden Apple while full should grant absorption AND macros. Chorus Fruit should teleport AND grant macros.
