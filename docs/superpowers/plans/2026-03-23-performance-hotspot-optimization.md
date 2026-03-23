# Performance Hotspot Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the 7 highest-impact runtime performance bottlenecks to reduce TPS impact and memory pressure.

**Architecture:** Surgical edits to existing files only. All config values that are read in hot paths (every tick, every event) get cached as final fields at construction time. Block scanning switches from `new Location().getBlock()` to ChunkSnapshot-based lookups. Thread-unsafe `HashMap` task registries are swapped to `ConcurrentHashMap`.

**Tech Stack:** Java 21, Spigot 1.21.11, Bukkit API (ChunkSnapshot, ConcurrentHashMap)

**Spec:** `docs/superpowers/specs/2026-03-23-performance-hotspot-optimization-design.md`

**Build command:** `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`

---

### Task 1: ConcurrentHashMap Migration (Section 3)

Swap all static `HashMap<UUID, Task>` task registries to `ConcurrentHashMap` and fix the double-lookup `hasTask()` pattern. This is a mechanical change across ~32 files with no behavioral impact — do it first so all subsequent tasks build on thread-safe foundations.

**Files (modify all):**
- `core/src/main/java/cz/hashiri/harshlands/tan/TemperatureCalculateTask.java:45`
- `core/src/main/java/cz/hashiri/harshlands/tan/ThirstCalculateTask.java:39`
- `core/src/main/java/cz/hashiri/harshlands/tan/HypothermiaTask.java:38`
- `core/src/main/java/cz/hashiri/harshlands/tan/HyperthermiaTask.java:38`
- `core/src/main/java/cz/hashiri/harshlands/tan/ColdBreathTask.java:38`
- `core/src/main/java/cz/hashiri/harshlands/tan/SweatTask.java:38`
- `core/src/main/java/cz/hashiri/harshlands/tan/DehydrationTask.java:36`
- `core/src/main/java/cz/hashiri/harshlands/tan/ParasiteTask.java:40`
- `core/src/main/java/cz/hashiri/harshlands/tan/ThermometerTask.java:41`
- `core/src/main/java/cz/hashiri/harshlands/utils/DisplayTask.java:46`
- `core/src/main/java/cz/hashiri/harshlands/baubles/EnderCrownTask.java:42`
- `core/src/main/java/cz/hashiri/harshlands/baubles/BrokenHeartRepairTask.java:36`
- `core/src/main/java/cz/hashiri/harshlands/baubles/MagicMirrorTask.java:34`
- `core/src/main/java/cz/hashiri/harshlands/baubles/PolarizedStoneTask.java:38`
- `core/src/main/java/cz/hashiri/harshlands/baubles/PotionBaubleTask.java:34`
- `core/src/main/java/cz/hashiri/harshlands/baubles/StoneGreaterInertiaTask.java:34`
- `core/src/main/java/cz/hashiri/harshlands/baubles/StoneNegativeGravityTask.java:36`
- `core/src/main/java/cz/hashiri/harshlands/baubles/ScarliteRingTask.java:35`
- `core/src/main/java/cz/hashiri/harshlands/baubles/StoneSeaTask.java:39`
- `core/src/main/java/cz/hashiri/harshlands/baubles/WormholeMirrorTask.java:33`
- `core/src/main/java/cz/hashiri/harshlands/baubles/TickableBaubleManager.java:46`
- `core/src/main/java/cz/hashiri/harshlands/spartanweaponry/TwoHandedTask.java:38`
- `core/src/main/java/cz/hashiri/harshlands/spartanweaponry/ThrowWeaponTask.java:57`
- `core/src/main/java/cz/hashiri/harshlands/spartanweaponry/EntityPrepareThrowTask.java:51`
- `core/src/main/java/cz/hashiri/harshlands/spartanweaponry/EntityLongAttackTask.java:48`
- `core/src/main/java/cz/hashiri/harshlands/spartanandfire/FreezeTask.java:36`
- `core/src/main/java/cz/hashiri/harshlands/spartanandfire/BurnTask.java:29`
- `core/src/main/java/cz/hashiri/harshlands/spartanandfire/ElectrocuteTask.java:32`
- `core/src/main/java/cz/hashiri/harshlands/spartanandfire/UnfreezeTask.java:31`
- `core/src/main/java/cz/hashiri/harshlands/ntp/FireStarterTask.java:36`
- `core/src/main/java/cz/hashiri/harshlands/ntp/CeramicBucketMeltTask.java:37`
- `core/src/main/java/cz/hashiri/harshlands/iceandfire/TideGuardianTask.java:35`
- `core/src/main/java/cz/hashiri/harshlands/fear/NightmareManager.java:61` (instance field, only ConcurrentHashMap swap — `hasActiveNightmare()` already uses single `containsKey()`)
- `core/src/main/java/cz/hashiri/harshlands/data/HLPlayer.java:41` (static `players` map — no hasTask method)
- `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java:71-73` (3 instance fields)

- [ ] **Step 1: Add import and swap HashMap → ConcurrentHashMap in all files**

For each file listed above, make two changes:

1. Add import (if not present): `import java.util.concurrent.ConcurrentHashMap;`
2. Change the declaration:
```java
// Before:
private static final Map<UUID, XxxTask> tasks = new HashMap<>();
// After:
private static final Map<UUID, XxxTask> tasks = new ConcurrentHashMap<>();
```

For `NightmareManager.java:61`:
```java
// Before:
private final Map<UUID, NightmareEntry> activeNightmares = new HashMap<>();
// After:
private final Map<UUID, NightmareEntry> activeNightmares = new ConcurrentHashMap<>();
```

For `HLPlayer.java:41`:
```java
// Before:
private static final Map<UUID, HLPlayer> players = new HashMap<>();
// After:
private static final Map<UUID, HLPlayer> players = new ConcurrentHashMap<>();
```

For `FoodExpansionEvents.java:71-73`:
```java
// Before:
private final Map<UUID, BukkitTask> decayTasks = new HashMap<>();
private final Map<UUID, NutritionEffectTask> effectTasks = new HashMap<>();
private final Map<UUID, BukkitTask> effectBukkitTasks = new HashMap<>();
// After:
private final Map<UUID, BukkitTask> decayTasks = new ConcurrentHashMap<>();
private final Map<UUID, NutritionEffectTask> effectTasks = new ConcurrentHashMap<>();
private final Map<UUID, BukkitTask> effectBukkitTasks = new ConcurrentHashMap<>();
```

- [ ] **Step 2: Fix hasTask() double-lookup pattern in all task files**

In every file that has a `hasTask` method with the pattern `tasks.containsKey(id) && tasks.get(id) != null`, replace with:
```java
public static boolean hasTask(UUID id) {
    return tasks.get(id) != null;
}
```

Skip `NightmareManager` (already uses single `containsKey()`).
Skip `HLPlayer` and `FoodExpansionEvents` (no `hasTask` method).

Note: `PotionBaubleTask` has a different pattern (iterates collection) — only swap the HashMap there, leave the method logic intact. `TickableBaubleManager` uses `baubles` instead of `tasks` — apply same `get() != null` fix to its equivalent method.

- [ ] **Step 3: Verify grep finds no remaining HashMap task registries**

Run: `grep -rn "new HashMap<>()" core/src/main/java/cz/hashiri/harshlands/ | grep -i "tasks\|baubles\|players\|nightmare\|decayTask\|effectTask"`

Expected: No output (all swapped).

- [ ] **Step 4: Build and verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "Replace HashMap with ConcurrentHashMap in all task registries

Swap ~35 static/instance HashMap<UUID, Task> registries to
ConcurrentHashMap for thread safety. Fix hasTask() double-lookup
pattern (containsKey + get) to single get() != null."
```

---

### Task 2: TemperatureCalculateTask Config Caching (Section 2)

Cache all ~25 config values read per tick in the constructor. This is the second-highest impact fix.

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/tan/TemperatureCalculateTask.java`

- [ ] **Step 1: Add cached config fields and `AddEntry` record**

Add these fields after the existing field declarations (around line 63):

```java
// --- Cached config values (read once in constructor) ---
// Biome temperature thresholds
private final double hotCutoff;
private final double hotMultiplier;
private final double warmCutoff;
private final double warmMultiplier;
private final double moderateCutoff;
private final double moderateMultiplier;
private final double coolCutoff;
private final double coolMultiplier;
private final double coldCutoff;
private final double coldMultiplier;
private final double frigidMultiplier;
private final double tempMaxChange;
private final double daylightCycleMultiplier;
private final int cubeLength;

// Effect thresholds
private final boolean hypothermiaEnabled;
private final double hypothermiaTemp;
private final boolean coldBreathEnabled;
private final double coldBreathMaxTemp;
private final boolean hyperthermiaEnabled;
private final double hyperthermiaTemp;
private final boolean sweatingEnabled;
private final double sweatingMinTemp;

// Pre-parsed add() entries
private record AddEntry(double value, boolean isRegulatory, boolean hasEnabledFlag, boolean enabled) {}
private final Map<String, AddEntry> addEntries;
```

- [ ] **Step 2: Initialize cached fields in constructor**

In the constructor, add the following initializations. **Important:** Move `tasks.put(id, this)` to the very end of the constructor (after all field initialization) so the task is not visible to other threads before it's fully constructed:

```java
// Cache biome temperature thresholds
this.hotCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.HotCutoff");
this.hotMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.HotMultiplier");
this.warmCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.WarmCutoff");
this.warmMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.WarmMultiplier");
this.moderateCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.ModerateCutoff");
this.moderateMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.ModerateMultiplier");
this.coolCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.CoolCutoff");
this.coolMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.CoolMultiplier");
this.coldCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.ColdCutoff");
this.coldMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.ColdMultiplier");
this.frigidMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.FrigidMultiplier");
this.tempMaxChange = config.getDouble("Temperature.MaxChange");
this.daylightCycleMultiplier = config.getDouble("Temperature.Environment.DaylightCycleMultiplier");
this.cubeLength = config.getInt("Temperature.Environment.CubeLength");

// Cache effect thresholds
this.hypothermiaEnabled = config.getBoolean("Temperature.Hypothermia.Enabled");
this.hypothermiaTemp = config.getDouble("Temperature.Hypothermia.Temperature");
this.coldBreathEnabled = config.getBoolean("Temperature.ColdBreath.Enabled");
this.coldBreathMaxTemp = config.getDouble("Temperature.ColdBreath.MaximumTemperature");
this.hyperthermiaEnabled = config.getBoolean("Temperature.Hyperthermia.Enabled");
this.hyperthermiaTemp = config.getDouble("Temperature.Hyperthermia.Temperature");
this.sweatingEnabled = config.getBoolean("Temperature.Sweating.Enabled");
this.sweatingMinTemp = config.getDouble("Temperature.Sweating.MinimumTemperature");

// Pre-parse add() entries (Environment non-block entries + Armor + Enchantments)
this.addEntries = buildAddEntries(config);
```

- [ ] **Step 3: Add `buildAddEntries()` method**

Add this private method to the class:

```java
private static Map<String, AddEntry> buildAddEntries(FileConfiguration config) {
    Map<String, AddEntry> map = new HashMap<>();
    for (String prefix : List.of("Temperature.Environment", "Temperature.Armor", "Temperature.Enchantments")) {
        ConfigurationSection section = config.getConfigurationSection(prefix);
        if (section == null) continue;
        for (String key : section.getKeys(false)) {
            String path = prefix + "." + key;
            // Skip sub-sections that don't have a Value (e.g. BiomeTemperature, Blocks, CubeLength)
            if (!config.contains(path + ".Value")) continue;
            double value = config.getDouble(path + ".Value");
            boolean isReg = config.getBoolean(path + ".IsRegulatory", false);
            boolean hasEnabled = config.contains(path + ".Enabled");
            boolean enabled = config.getBoolean(path + ".Enabled", true);
            map.put(path, new AddEntry(value, isReg, hasEnabled, enabled));
        }
    }
    return map;
}
```

- [ ] **Step 4: Replace config reads in `run()` with cached fields**

Replace the biome temperature chain (lines 101-136) — change every `config.getDouble(...)` to the cached field. For example:

```java
// Before (line 101):
double tempMaxChange = config.getDouble("Temperature.MaxChange");
// After:
// (remove this line — tempMaxChange is now a field)

// Before (lines 113-135): config.getDouble("...HotCutoff") etc.
// After: use hotCutoff, hotMultiplier, warmCutoff, etc.
```

Replace the daylight cycle line (138):
```java
// Before:
* config.getDouble("Temperature.Environment.DaylightCycleMultiplier")
// After:
* daylightCycleMultiplier
```

Replace the effect threshold checks (lines 244-289):
```java
// Before:
if (config.getBoolean("Temperature.Hypothermia.Enabled")) {
    if (temp <= config.getDouble("Temperature.Hypothermia.Temperature")) {
// After:
if (hypothermiaEnabled) {
    if (temp <= hypothermiaTemp) {
```

Apply the same pattern for coldBreath, hyperthermia, and sweating blocks.

- [ ] **Step 5: Replace `add()` method body**

Replace the `add(String configPath)` method (lines 316-337):

```java
public void add(String configPath) {
    AddEntry entry = addEntries.get(configPath);
    if (entry == null) return;
    if (entry.hasEnabledFlag() && !entry.enabled()) return;
    if (entry.isRegulatory()) regulate += entry.value();
    else change += entry.value();
}
```

- [ ] **Step 6: Mark volatile fields for cross-thread visibility**

Change these field declarations:
```java
// Before:
private double regulateEnv = 0D;
private double changeEnv = 0D;
// After:
private volatile double regulateEnv = 0D;
private volatile double changeEnv = 0D;
```

- [ ] **Step 7: Build and verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`

Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/tan/TemperatureCalculateTask.java
git commit -m "Cache all config values in TemperatureCalculateTask constructor

Replace ~25 per-tick YAML config.getDouble()/getBoolean() calls with
final fields initialized once. Pre-parse add() config entries into a
HashMap<String, AddEntry>. Mark changeEnv/regulateEnv as volatile.
Move tasks.put() to end of constructor for safe publication."
```

---

### Task 3: TemperatureEnvironmentTask Rewrite (Sections 1 + 7)

Rewrite the block scanning loop to use ChunkSnapshots with pre-parsed block config. This is the highest-impact single change.

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/tan/TemperatureEnvironmentTask.java`
- Modify: `core/src/main/java/cz/hashiri/harshlands/tan/TemperatureCalculateTask.java` (snapshot capture + pass to constructor)

- [ ] **Step 1: Add `BlockTempEntry` record and `blockTempMap` to TemperatureEnvironmentTask**

Add at the top of the class (after field declarations):

```java
// Pre-parsed block temperature data — built once per task instance
record BlockTempEntry(double value, boolean isRegulatory) {}
private final Map<Material, BlockTempEntry> blockTempMap;
private final Map<Long, ChunkSnapshot> snapshots;
private final int cubeLength;
private final int minY;
private final int maxY;
```

- [ ] **Step 2: Add `buildBlockTempMap()` static method**

```java
static Map<Material, BlockTempEntry> buildBlockTempMap(ConfigurationSection section) {
    if (section == null) return Map.of();
    Map<Material, BlockTempEntry> map = new EnumMap<>(Material.class);
    for (String key : section.getKeys(false)) {
        Material mat = Material.matchMaterial(key);
        if (mat == null) continue;
        double value = section.getDouble(key + ".Value", 0.0);
        boolean isReg = section.getBoolean(key + ".IsRegulatory", false);
        map.put(mat, new BlockTempEntry(value, isReg));
    }
    return map;
}
```

- [ ] **Step 3: Add `getBlockTypeFromSnapshots()` helper**

```java
private Material getBlockTypeFromSnapshots(int x, int y, int z) {
    if (y < minY || y > maxY) return Material.AIR;
    int cx = x >> 4;
    int cz = z >> 4;
    long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    ChunkSnapshot snap = snapshots.get(key);
    if (snap == null) return Material.AIR;
    return snap.getBlockType(x & 0xF, y, z & 0xF);
}
```

- [ ] **Step 4: Rewrite constructor to accept snapshots and cache config**

Update constructor signature and body:

```java
public TemperatureEnvironmentTask(TanModule module, HLPlugin plugin, HLPlayer player,
                                   Map<Long, ChunkSnapshot> snapshots) {
    this.plugin = plugin;
    this.config = module.getUserConfig().getConfig();
    this.player = player;
    this.allowedWorlds = module.getAllowedWorlds();
    this.calcTask = TemperatureCalculateTask.getTasks().get(player.getPlayer().getUniqueId());
    this.section = config.getConfigurationSection("Temperature.Environment.Blocks");
    this.snapshots = snapshots;
    this.cubeLength = config.getInt("Temperature.Environment.CubeLength");
    this.blockTempMap = buildBlockTempMap(section);
    World world = player.getPlayer().getWorld();
    this.minY = world.getMinHeight();
    this.maxY = world.getMaxHeight() - 1;
}
```

- [ ] **Step 5: Rewrite `run()` loop**

Replace the triple-nested loop (lines 60-86) with:

```java
@Override
public void run() {
    Player player = this.player.getPlayer();

    if (conditionsMet(player)) {
        regulate = 0D;
        change = 0D;
        Location pLoc = player.getLocation();
        int px = pLoc.getBlockX();
        int py = pLoc.getBlockY();
        int pz = pLoc.getBlockZ();

        for (int x = -(cubeLength - 1); x < cubeLength; x++) {
            for (int y = -(cubeLength - 1); y < cubeLength; y++) {
                for (int z = -(cubeLength - 1); z < cubeLength; z++) {
                    Material mat = getBlockTypeFromSnapshots(px + x, py + y, pz + z);
                    if (mat.isAir()) continue;
                    BlockTempEntry entry = blockTempMap.get(mat);
                    if (entry == null) continue;
                    if (entry.isRegulatory()) regulate += entry.value();
                    else change += entry.value();
                }
            }
        }
        calcTask.setChangeEnv(change);
        calcTask.setRegulateEnv(regulate);
    }
    else {
        stop();
    }
}
```

- [ ] **Step 6: Keep static methods as backward-compatible wrappers**

The static methods `willAffectTemperature()`, `isRegulatory()`, `getValue()` are called by `TanEvents.java:947-971`. Keep them as-is — they still take `ConfigurationSection` and do YAML lookups, but they only run on block change events (rare), not every tick. No changes needed.

- [ ] **Step 7: Add snapshot capture to TemperatureCalculateTask**

In `TemperatureCalculateTask.java`, add the snapshot helper method:

```java
private Map<Long, ChunkSnapshot> getRelevantChunkSnapshots(Player player) {
    Location loc = player.getLocation();
    int minCX = (loc.getBlockX() - cubeLength) >> 4;
    int maxCX = (loc.getBlockX() + cubeLength) >> 4;
    int minCZ = (loc.getBlockZ() - cubeLength) >> 4;
    int maxCZ = (loc.getBlockZ() + cubeLength) >> 4;
    Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
    World world = player.getWorld();
    for (int cx = minCX; cx <= maxCX; cx++) {
        for (int cz = minCZ; cz <= maxCZ; cz++) {
            if (world.isChunkLoaded(cx, cz)) {
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                snapshots.put(key, world.getChunkAt(cx, cz).getChunkSnapshot());
            }
        }
    }
    return snapshots;
}
```

Add required imports: `import org.bukkit.ChunkSnapshot;`

- [ ] **Step 8: Extract static snapshot capture utility**

Add a public static helper to `TemperatureEnvironmentTask` to avoid duplicating snapshot logic:

```java
public static Map<Long, ChunkSnapshot> captureSnapshots(Location center, int cubeLength) {
    Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
    World world = center.getWorld();
    if (world == null) return snapshots;
    int minCX = (center.getBlockX() - cubeLength) >> 4;
    int maxCX = (center.getBlockX() + cubeLength) >> 4;
    int minCZ = (center.getBlockZ() - cubeLength) >> 4;
    int maxCZ = (center.getBlockZ() + cubeLength) >> 4;
    for (int cx = minCX; cx <= maxCX; cx++) {
        for (int cz = minCZ; cz <= maxCZ; cz++) {
            if (world.isChunkLoaded(cx, cz)) {
                long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                snapshots.put(key, world.getChunkAt(cx, cz).getChunkSnapshot());
            }
        }
    }
    return snapshots;
}
```

- [ ] **Step 9: Update all 6 TemperatureEnvironmentTask constructor call sites**

There are 6 call sites that use the old 3-arg constructor. ALL must be updated to pass snapshots.

**In `TemperatureCalculateTask.java`** — use `getRelevantChunkSnapshots()` (which delegates to the static method):

```java
private Map<Long, ChunkSnapshot> getRelevantChunkSnapshots(Player player) {
    return TemperatureEnvironmentTask.captureSnapshots(player.getLocation(), cubeLength);
}
```

Line 148:
```java
new TemperatureEnvironmentTask(module, plugin, this.player, getRelevantChunkSnapshots(player)).start();
```

Line 153 (same change):
```java
new TemperatureEnvironmentTask(module, plugin, this.player, getRelevantChunkSnapshots(player)).start();
```

Line 354 (`start()` method):
```java
new TemperatureEnvironmentTask(module, plugin, player,
        getRelevantChunkSnapshots(player.getPlayer())).start();
```

**In `TanEvents.java`** — 4 call sites at lines 421, 436, 449, 982. All run on the main thread, so snapshot capture is safe. Use `cubeLength` from config (which is available as `config.getInt("Temperature.Environment.CubeLength")`):

Line 421:
```java
// Before:
new TemperatureEnvironmentTask(module, plugin, HLPlayer.getPlayers().get(player.getUniqueId())).runTaskLaterAsynchronously(plugin, 1L);
// After:
HLPlayer hlp = HLPlayer.getPlayers().get(player.getUniqueId());
Map<Long, ChunkSnapshot> snaps = TemperatureEnvironmentTask.captureSnapshots(player.getLocation(), config.getInt("Temperature.Environment.CubeLength"));
new TemperatureEnvironmentTask(module, plugin, hlp, snaps).runTaskLaterAsynchronously(plugin, 1L);
```

Line 436 (forEach lambda — needs to capture snapshots per entity):
```java
// Before:
nearby.forEach(entity -> new TemperatureEnvironmentTask(module, plugin, HLPlayer.getPlayers().get(entity.getUniqueId())).runTaskLaterAsynchronously(plugin, 1L));
// After:
int cl = config.getInt("Temperature.Environment.CubeLength");
nearby.forEach(entity -> {
    Map<Long, ChunkSnapshot> snaps = TemperatureEnvironmentTask.captureSnapshots(entity.getLocation(), cl);
    new TemperatureEnvironmentTask(module, plugin, HLPlayer.getPlayers().get(entity.getUniqueId()), snaps).runTaskLaterAsynchronously(plugin, 1L);
});
```

Line 449 (same forEach pattern):
```java
// Same transformation as line 436
int cl = config.getInt("Temperature.Environment.CubeLength");
nearby.forEach(entity -> {
    Map<Long, ChunkSnapshot> snaps = TemperatureEnvironmentTask.captureSnapshots(entity.getLocation(), cl);
    new TemperatureEnvironmentTask(module, plugin, HLPlayer.getPlayers().get(entity.getUniqueId()), snaps).runTaskLaterAsynchronously(plugin, 1L);
});
```

Line 982:
```java
// Before:
new TemperatureEnvironmentTask(module, plugin, HLPlayer.getPlayers().get(player.getUniqueId())).runTaskLaterAsynchronously(plugin, 1L);
// After:
HLPlayer hlp = HLPlayer.getPlayers().get(player.getUniqueId());
Map<Long, ChunkSnapshot> snaps = TemperatureEnvironmentTask.captureSnapshots(player.getLocation(), config.getInt("Temperature.Environment.CubeLength"));
new TemperatureEnvironmentTask(module, plugin, hlp, snaps).runTaskLaterAsynchronously(plugin, 1L);
```

Add required import to TanEvents.java: `import org.bukkit.ChunkSnapshot;`

- [ ] **Step 10: Build and verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`

Expected: BUILD SUCCESS. If it fails, check that ALL 6 constructor call sites were updated (3 in TemperatureCalculateTask, 4 in TanEvents — grep for `new TemperatureEnvironmentTask(`).

- [ ] **Step 11: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/tan/TemperatureEnvironmentTask.java \
        core/src/main/java/cz/hashiri/harshlands/tan/TemperatureCalculateTask.java \
        core/src/main/java/cz/hashiri/harshlands/tan/TanEvents.java
git commit -m "Rewrite block scanning to use ChunkSnapshot + pre-parsed config

Replace 343 new Location() + getBlock() calls with ChunkSnapshot-based
Material lookups. Pre-parse Temperature.Environment.Blocks config into
EnumMap<Material, BlockTempEntry>. Capture snapshots on main thread,
scan async. Clamp Y to world bounds. Extract captureSnapshots() static
utility. Update all 6 constructor call sites in TemperatureCalculateTask
and TanEvents."
```

---

### Task 4: FootstepHandler Config Caching (Section 4)

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/dynamicsurroundings/FootstepHandler.java`

- [ ] **Step 1: Add cached config fields**

Replace the existing `config` field usage with cached values. Add fields after line 287:

```java
private final double stepThreshold;
private final float footstepVolume;
private final float pitchVariance;
private final float armorOverlayVolume;
```

- [ ] **Step 2: Initialize in constructor**

In the constructor (after `this.config = config;`):

```java
this.stepThreshold = config.getDouble("Footsteps.Threshold", 1.5);
this.footstepVolume = (float) config.getDouble("Footsteps.Volume", 0.8);
this.pitchVariance = (float) config.getDouble("Footsteps.PitchVariance", 0.1);
this.armorOverlayVolume = (float) config.getDouble("Footsteps.ArmorOverlay.Volume", 0.3);
```

- [ ] **Step 3: Replace config reads in methods**

In `handleMove()` (line 332):
```java
// Before:
double threshold = config.getDouble("Footsteps.Threshold", 1.5);
// After:
double threshold = stepThreshold;
```

In `playFootstepSound()` (lines 364-366):
```java
// Before:
float vol = (float) config.getDouble("Footsteps.Volume", 0.8);
float pitchVariance = (float) config.getDouble("Footsteps.PitchVariance", 0.1);
// After:
float vol = footstepVolume;
float pitch = 1.0f + (random.nextFloat() - 0.5f) * pitchVariance;
```
(Note: remove the local `pitchVariance` variable since it shadows the field — just use `this.pitchVariance` in the pitch calculation.)

In `playLandSound()` (line 381):
```java
// Before:
float vol = (float) config.getDouble("Footsteps.Volume", 0.8) * 1.2f;
// After:
float vol = footstepVolume * 1.2f;
```

In `playArmorOverlay()` (line 390):
```java
// Before:
float vol = (float) config.getDouble("Footsteps.ArmorOverlay.Volume", 0.3);
// After:
float vol = armorOverlayVolume;
```

- [ ] **Step 4: Build and verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/dynamicsurroundings/FootstepHandler.java
git commit -m "Cache FootstepHandler config values in constructor

Replace 5 per-footstep YAML config.getDouble() calls with final
fields initialized once at construction."
```

---

### Task 5: ComfortScoreCalculator Tier Pre-Parsing (Section 5)

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/comfort/ComfortScoreCalculator.java`

- [ ] **Step 1: Add TierRange record and field**

Add after the existing field declarations (around line 43):

```java
private record TierRange(int minScore, int maxScore, ComfortTier tier) {}
private final List<TierRange> tierRanges;
```

- [ ] **Step 2: Build tier ranges in constructor**

In the constructor, after `this.totalCategories = categoryPoints.size();` (line 51), add:

```java
// Pre-build tier ranges
List<TierRange> ranges = new ArrayList<>();
ConfigurationSection tiersSection = config.getConfigurationSection("Tiers");
if (tiersSection != null) {
    for (String tierKey : tiersSection.getKeys(false)) {
        ConfigurationSection tierSec = tiersSection.getConfigurationSection(tierKey);
        if (tierSec == null) continue;
        try {
            ranges.add(new TierRange(
                tierSec.getInt("MinScore", 0),
                tierSec.getInt("MaxScore", Integer.MAX_VALUE),
                ComfortTier.valueOf(tierKey)
            ));
        } catch (IllegalArgumentException ignored) {
            // Unknown tier key in config — skip
        }
    }
}
this.tierRanges = List.copyOf(ranges);
```

- [ ] **Step 3: Replace `resolveTier()` method body**

Replace the entire `resolveTier()` method (lines 187-215):

```java
@Nonnull
private ComfortTier resolveTier(int score) {
    if (score <= 0) return ComfortTier.NONE;
    for (TierRange range : tierRanges) {
        if (score >= range.minScore && score <= range.maxScore) return range.tier;
    }
    return ComfortTier.NONE;
}
```

Remove the `config` field if it's now unused (check — it may still be used by `calculate()` indirectly). If `config` is only used in `resolveTier()` and the constructor, it can be removed as a field.

- [ ] **Step 4: Build and verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/comfort/ComfortScoreCalculator.java
git commit -m "Pre-build comfort tier ranges in constructor

Replace per-call config parsing in resolveTier() with pre-built
List<TierRange> initialized once at construction."
```

---

### Task 6: NoiseEvaluationTask Entity Filtering (Section 6)

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/soundecology/NoiseEvaluationTask.java`

- [ ] **Step 1: Add Monster predicate to getNearbyEntities**

Replace line 86:
```java
// Before:
Collection<Entity> nearby = world.getNearbyEntities(loc, scanRadius, yRadius, scanRadius);
// After:
Collection<Entity> nearby = world.getNearbyEntities(loc, scanRadius, yRadius, scanRadius,
        entity -> entity instanceof Monster);
```

- [ ] **Step 2: Remove the redundant `instanceof Monster` check in the loop**

Replace lines 91:
```java
// Before:
if (!(entity instanceof Monster)) continue;
Mob mob = (Mob) entity;
// After:
Mob mob = (Mob) entity;
```

- [ ] **Step 3: Replace debug stream with `nearby.size()`**

Replace line 128:
```java
// Before:
+ " hostileNearby=" + nearby.stream().filter(e -> e instanceof Monster).count();
// After:
+ " hostileNearby=" + nearby.size();
```

- [ ] **Step 4: Build and verify**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/soundecology/NoiseEvaluationTask.java
git commit -m "Filter getNearbyEntities to Monster only via predicate

Use predicate overload to filter during entity scan instead of
post-filtering. Replace redundant debug stream with nearby.size()."
```

---

### Task 7: Final Verification

- [ ] **Step 1: Full clean build**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`

Expected: BUILD SUCCESS

- [ ] **Step 2: Verify no remaining HashMap task registries**

Run: `grep -rn "new HashMap<>()" core/src/main/java/ | grep -i "tasks\|baubles\|players\|nightmare\|decayTask\|effectTask"`

Expected: No output

- [ ] **Step 3: Verify no remaining config reads in hot paths**

Run: `grep -n "config.getDouble\|config.getBoolean\|config.getInt" core/src/main/java/cz/hashiri/harshlands/tan/TemperatureCalculateTask.java`

Expected: Only in the constructor and `buildAddEntries()` — none in `run()` or `add()`.

Run: `grep -n "config.getDouble" core/src/main/java/cz/hashiri/harshlands/dynamicsurroundings/FootstepHandler.java`

Expected: Only in the constructor — none in `handleMove`, `playFootstepSound`, `playLandSound`, `playArmorOverlay`.
