# Custom Foods Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add 37 custom food items + 1 ingredient (dough) to the FoodExpansion module, fully YAML-driven with PDC identity, recipes, mob drops, and macronutrient integration.

**Architecture:** New sub-package `cz.hashiri.harshlands.foodexpansion.items` with 4 classes (CustomFoodDefinition, CustomFoodRegistry, CustomFoodRecipes, CustomFoodDrops). Existing FoodExpansionModule orchestrates initialization. FoodExpansionEvents modified to resolve custom food identity via PDC before falling back to vanilla Material names.

**Tech Stack:** Bukkit/Spigot 1.21, PersistentDataContainer, CustomModelData, Bukkit Recipe API (ShapedRecipe, ShapelessRecipe, FurnaceRecipe, CampfireRecipe, SmokingRecipe)

**Spec:** `docs/superpowers/specs/2026-03-22-custom-foods-design.md`

---

## File Map

### New Files

| File | Responsibility |
|------|---------------|
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/FoodFlag.java` | Enum: MEAT, FAST_EAT, BOWL, ALWAYS_EAT |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/FoodEffect.java` | Record: PotionEffectType, duration, amplifier, chance |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodDefinition.java` | Data class holding one food's parsed config |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRegistry.java` | Central registry: load YAML, create ItemStacks, PDC identity checks |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRecipes.java` | Recipe registration (shaped, shapeless, furnace/campfire/smoker), bucket returns, world-gating |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodDrops.java` | Mob drop listener: EntityDeathEvent, looting, fire detection |

### Modified Files

| File | Changes |
|------|---------|
| `core/src/main/resources/foodexpansion.yml` | Add `CustomFoods` section (38 entries) and `MobDrops` section (8 entries) |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java` | Add CustomFoodRegistry field, initialize/shutdown custom foods, expose getter, load custom macros into foodMap |
| `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java` | Modify onPlayerConsume() for PDC resolution, add potion effects, BOWL return safety, modify onPlayerInteract() for custom food PDC resolution and ALWAYS_EAT handling |
| `core/src/main/java/cz/hashiri/harshlands/commands/Tab.java` | Add custom food IDs to tab completion for give command |

---

## Task 1: FoodFlag Enum and FoodEffect Record

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/FoodFlag.java`
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/FoodEffect.java`

- [ ] **Step 1: Create FoodFlag enum**

```java
package cz.hashiri.harshlands.foodexpansion.items;

public enum FoodFlag {
    MEAT,      // Wolves can be fed with it
    FAST_EAT,  // Informational: base material should be DRIED_KELP (16 ticks)
    BOWL,      // Returns bowl after eating
    ALWAYS_EAT // Can eat when hunger bar is full
}
```

Write this to `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/FoodFlag.java`.

- [ ] **Step 2: Create FoodEffect record**

```java
package cz.hashiri.harshlands.foodexpansion.items;

import org.bukkit.potion.PotionEffectType;

public record FoodEffect(PotionEffectType type, int durationTicks, int amplifier, double chance) {}
```

Write this to `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/FoodEffect.java`.

- [ ] **Step 3: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/FoodFlag.java \
        core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/FoodEffect.java
git commit -m "Add FoodFlag enum and FoodEffect record for custom foods"
```

---

## Task 2: CustomFoodDefinition Data Class

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodDefinition.java`

- [ ] **Step 1: Create CustomFoodDefinition**

```java
package cz.hashiri.harshlands.foodexpansion.items;

import cz.hashiri.harshlands.foodexpansion.NutrientProfile;
import org.bukkit.Material;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class CustomFoodDefinition {
    private final String id;
    private final String displayName;
    private final Material baseMaterial;
    private final int customModelData;
    private final int hunger;
    private final float saturation;
    private final NutrientProfile macros;
    private final EnumSet<FoodFlag> flags;
    private final List<FoodEffect> effects;
    private final boolean isFood; // false for ingredients like dough

    public CustomFoodDefinition(String id, String displayName, Material baseMaterial,
                                 int customModelData, int hunger, float saturation,
                                 @Nullable NutrientProfile macros, EnumSet<FoodFlag> flags,
                                 List<FoodEffect> effects, boolean isFood) {
        this.id = id;
        this.displayName = displayName;
        this.baseMaterial = baseMaterial;
        this.customModelData = customModelData;
        this.hunger = hunger;
        this.saturation = saturation;
        this.macros = macros;
        this.flags = flags;
        this.effects = effects;
        this.isFood = isFood;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getBaseMaterial() { return baseMaterial; }
    public int getCustomModelData() { return customModelData; }
    public int getHunger() { return hunger; }
    public float getSaturation() { return saturation; }
    @Nullable public NutrientProfile getMacros() { return macros; }
    public EnumSet<FoodFlag> getFlags() { return flags; }
    public List<FoodEffect> getEffects() { return effects; }
    public boolean isFood() { return isFood; }
    public boolean hasFlag(FoodFlag flag) { return flags.contains(flag); }
}
```

Write this to `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodDefinition.java`.

- [ ] **Step 2: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodDefinition.java
git commit -m "Add CustomFoodDefinition data class"
```

---

## Task 3: CustomFoodRegistry — Core Registry

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRegistry.java`

This is the central class. It parses the `CustomFoods` YAML section, builds definitions, creates canonical ItemStacks, and provides identity checking via PDC.

- [ ] **Step 1: Create CustomFoodRegistry**

```java
package cz.hashiri.harshlands.foodexpansion.items;

import cz.hashiri.harshlands.foodexpansion.NutrientProfile;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;

public class CustomFoodRegistry {

    public static final String PDC_KEY = "hl_food";

    private final Map<String, CustomFoodDefinition> definitions = new LinkedHashMap<>();
    private final Logger logger;

    public CustomFoodRegistry(ConfigurationSection customFoodsSection, Logger logger) {
        this.logger = logger;
        if (customFoodsSection != null) {
            loadFoods(customFoodsSection);
        }
    }

    private void loadFoods(ConfigurationSection section) {
        for (String id : section.getKeys(false)) {
            ConfigurationSection foodSec = section.getConfigurationSection(id);
            if (foodSec == null) continue;

            // Base material
            String matName = foodSec.getString("BaseMaterial", "PAPER");
            Material baseMaterial;
            try {
                baseMaterial = Material.valueOf(matName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Custom food '" + id + "': invalid BaseMaterial '" + matName + "', skipping");
                continue;
            }

            String displayName = foodSec.getString("DisplayName", id);
            int cmd = foodSec.getInt("CustomModelData", 0);

            // Nutrition (vanilla hunger/saturation) — absent for non-food ingredients
            boolean isFood = foodSec.contains("Nutrition");
            int hunger = foodSec.getInt("Nutrition.Hunger", 0);
            float saturation = (float) foodSec.getDouble("Nutrition.Saturation", 0.0);

            // Macros (P/C/F) — absent for non-food ingredients
            NutrientProfile macros = null;
            if (foodSec.contains("Macros")) {
                double p = foodSec.getDouble("Macros.Protein", 0.0);
                double c = foodSec.getDouble("Macros.Carbs", 0.0);
                double f = foodSec.getDouble("Macros.Fats", 0.0);
                macros = new NutrientProfile(p, c, f);
            }

            // Flags
            EnumSet<FoodFlag> flags = EnumSet.noneOf(FoodFlag.class);
            List<String> flagList = foodSec.getStringList("Flags");
            for (String flagStr : flagList) {
                try {
                    flags.add(FoodFlag.valueOf(flagStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    logger.warning("Custom food '" + id + "': unknown flag '" + flagStr + "'");
                }
            }

            // Effects
            List<FoodEffect> effects = new ArrayList<>();
            List<Map<?, ?>> effectList = foodSec.getMapList("Effects");
            for (Map<?, ?> effectMap : effectList) {
                String typeName = String.valueOf(effectMap.get("Type"));
                PotionEffectType effectType = Registry.EFFECT.get(NamespacedKey.minecraft(typeName.toLowerCase()));
                if (effectType == null) {
                    logger.warning("Custom food '" + id + "': unknown effect type '" + typeName + "'");
                    continue;
                }
                int duration = effectMap.containsKey("Duration") ? ((Number) effectMap.get("Duration")).intValue() : 200;
                int amplifier = effectMap.containsKey("Amplifier") ? ((Number) effectMap.get("Amplifier")).intValue() : 0;
                double chance = effectMap.containsKey("Chance") ? ((Number) effectMap.get("Chance")).doubleValue() : 1.0;
                effects.add(new FoodEffect(effectType, duration, amplifier, chance));
            }

            CustomFoodDefinition def = new CustomFoodDefinition(
                id, displayName, baseMaterial, cmd, hunger, saturation,
                macros, flags, effects, isFood
            );
            definitions.put(id.toLowerCase(), def);
        }
        logger.info("Loaded " + definitions.size() + " custom food definitions");
    }

    /**
     * Creates a canonical ItemStack for the given custom food ID.
     * The stack has: base material, display name, CustomModelData, PDC tag "hl_food".
     * This must be deterministic — same inputs always produce identical ItemStacks.
     */
    public ItemStack createItemStack(String foodId, int count) {
        CustomFoodDefinition def = definitions.get(foodId.toLowerCase());
        if (def == null) throw new IllegalArgumentException("Unknown custom food: " + foodId);

        ItemStack stack = new ItemStack(def.getBaseMaterial(), count);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        // Display name (translate color codes)
        meta.setDisplayName(Utils.translateMsg(def.getDisplayName(), null, null));

        // CustomModelData
        if (def.getCustomModelData() > 0) {
            Utils.setCustomModelData(meta, def.getCustomModelData());
        }

        stack.setItemMeta(meta);

        // PDC tag — must be set after setItemMeta since Utils.addNbtTag re-reads meta
        Utils.addNbtTag(stack, PDC_KEY, def.getId(), PersistentDataType.STRING);

        return stack;
    }

    /**
     * Checks if an ItemStack is a custom food (has the hl_food PDC tag).
     */
    public boolean isCustomFood(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return Utils.hasNbtTag(item, PDC_KEY);
    }

    /**
     * Extracts the custom food ID from an ItemStack's PDC.
     * Returns null if not a custom food.
     */
    @Nullable
    public String getFoodId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return Utils.getNbtTag(item, PDC_KEY, PersistentDataType.STRING);
    }

    @Nullable
    public CustomFoodDefinition getDefinition(String foodId) {
        return definitions.get(foodId.toLowerCase());
    }

    public Collection<CustomFoodDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }
}
```

Write this to `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRegistry.java`.

- [ ] **Step 2: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRegistry.java
git commit -m "Add CustomFoodRegistry with YAML loading and PDC identity"
```

---

## Task 4: CustomFoodRecipes — Recipe Registration

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRecipes.java`

Handles all Bukkit recipe registration: shapeless, shaped, furnace (+campfire, +smoker). Also handles bucket returns and world-gating.

- [ ] **Step 1: Create CustomFoodRecipes**

```java
package cz.hashiri.harshlands.foodexpansion.items;

import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.foodexpansion.FoodExpansionModule;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.RecipeChoice;

import java.util.*;
import java.util.logging.Logger;

public class CustomFoodRecipes implements Listener {

    private final CustomFoodRegistry registry;
    private final FoodExpansionModule module;
    private final HLPlugin plugin;
    private final Logger logger;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public CustomFoodRecipes(CustomFoodRegistry registry, FoodExpansionModule module,
                              HLPlugin plugin, ConfigurationSection customFoodsSection,
                              ConfigurationSection bonusRecipesSection) {
        this.registry = registry;
        this.module = module;
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        if (customFoodsSection != null) {
            registerAll(customFoodsSection);
        }
        if (bonusRecipesSection != null) {
            registerBonusRecipes(bonusRecipesSection);
        }
    }

    private void registerAll(ConfigurationSection customFoodsSection) {
        int count = 0;
        for (String id : customFoodsSection.getKeys(false)) {
            ConfigurationSection foodSec = customFoodsSection.getConfigurationSection(id);
            if (foodSec == null || !foodSec.contains("Recipe")) continue;

            ConfigurationSection recipeSec = foodSec.getConfigurationSection("Recipe");
            if (recipeSec == null) continue;

            String type = recipeSec.getString("Type", "").toUpperCase();
            try {
                switch (type) {
                    case "SHAPELESS" -> registerShapeless(id, recipeSec);
                    case "SHAPED" -> registerShaped(id, recipeSec);
                    case "FURNACE" -> registerFurnace(id, recipeSec);
                    default -> logger.warning("Custom food '" + id + "': unknown recipe type '" + type + "'");
                }
                count++;
            } catch (Exception e) {
                logger.warning("Custom food '" + id + "': failed to register recipe: " + e.getMessage());
            }
        }
        logger.info("Registered " + count + " custom food recipes");
    }

    private void registerShapeless(String foodId, ConfigurationSection recipeSec) {
        NamespacedKey key = new NamespacedKey(plugin, "food_" + foodId.toLowerCase());
        int resultCount = recipeSec.getInt("Result.Count", 1);
        ItemStack result = registry.createItemStack(foodId, resultCount);

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);

        List<String> ingredients = recipeSec.getStringList("Ingredients");
        for (String ing : ingredients) {
            RecipeChoice choice = resolveIngredient(ing);
            recipe.addIngredient(choice);
        }

        Bukkit.addRecipe(recipe);
        registeredKeys.add(key);
    }

    private void registerShaped(String foodId, ConfigurationSection recipeSec) {
        NamespacedKey key = new NamespacedKey(plugin, "food_" + foodId.toLowerCase());
        int resultCount = recipeSec.getInt("Result.Count", 1);
        ItemStack result = registry.createItemStack(foodId, resultCount);

        ShapedRecipe recipe = new ShapedRecipe(key, result);

        List<String> pattern = recipeSec.getStringList("Pattern");
        recipe.shape(pattern.toArray(new String[0]));

        ConfigurationSection keySec = recipeSec.getConfigurationSection("Key");
        if (keySec != null) {
            for (String k : keySec.getKeys(false)) {
                String ingName = keySec.getString(k);
                RecipeChoice choice = resolveIngredient(ingName);
                recipe.setIngredient(k.charAt(0), choice);
            }
        }

        Bukkit.addRecipe(recipe);
        registeredKeys.add(key);
    }

    private void registerFurnace(String foodId, ConfigurationSection recipeSec) {
        String inputName = recipeSec.getString("Input", "");
        float xp = (float) recipeSec.getDouble("Experience", 0.35);
        int cookTime = recipeSec.getInt("CookingTime", 200);

        RecipeChoice inputChoice = resolveIngredient(inputName);

        // Determine result — check if foodId is a custom food or vanilla item
        ItemStack result;
        CustomFoodDefinition def = registry.getDefinition(foodId);
        if (def != null) {
            result = registry.createItemStack(foodId, 1);
        } else {
            // Vanilla output (e.g., "Bread" from dough, "Leather" from compressed_flesh)
            // foodId here should be a vanilla material name
            Material mat = Material.valueOf(foodId.toUpperCase());
            result = new ItemStack(mat);
        }

        // Furnace
        NamespacedKey furnaceKey = new NamespacedKey(plugin, "food_" + foodId.toLowerCase());
        FurnaceRecipe furnace = new FurnaceRecipe(furnaceKey, result, inputChoice, xp, cookTime);
        Bukkit.addRecipe(furnace);
        registeredKeys.add(furnaceKey);

        // Campfire (3x cook time)
        NamespacedKey campfireKey = new NamespacedKey(plugin, "food_" + foodId.toLowerCase() + "_campfire");
        CampfireRecipe campfire = new CampfireRecipe(campfireKey, result, inputChoice, xp, cookTime * 3);
        Bukkit.addRecipe(campfire);
        registeredKeys.add(campfireKey);

        // Smoker (0.5x cook time)
        NamespacedKey smokerKey = new NamespacedKey(plugin, "food_" + foodId.toLowerCase() + "_smoker");
        SmokingRecipe smoker = new SmokingRecipe(smokerKey, result, inputChoice, xp, cookTime / 2);
        Bukkit.addRecipe(smoker);
        registeredKeys.add(smokerKey);
    }

    /**
     * Resolves an ingredient name to a RecipeChoice.
     * - Lowercase name → custom food (ExactChoice with canonical stack)
     * - UPPERCASE name → vanilla material (MaterialChoice)
     * - Comma-separated UPPERCASE → multi-material choice (e.g., "RED_MUSHROOM,BROWN_MUSHROOM")
     */
    private RecipeChoice resolveIngredient(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Empty ingredient name");
        }

        // Check for multi-material (comma-separated)
        if (name.contains(",")) {
            List<Material> materials = new ArrayList<>();
            for (String part : name.split(",")) {
                materials.add(Material.valueOf(part.trim().toUpperCase()));
            }
            return new RecipeChoice.MaterialChoice(materials);
        }

        // Check if it's a custom food ID (lowercase convention)
        CustomFoodDefinition def = registry.getDefinition(name);
        if (def != null) {
            return new RecipeChoice.ExactChoice(registry.createItemStack(name, 1));
        }

        // Vanilla material (uppercase)
        return new RecipeChoice.MaterialChoice(Material.valueOf(name.toUpperCase()));
    }

    // --- Bucket Return Handler ---

    private static final Set<Material> BUCKET_INGREDIENTS = EnumSet.of(
        Material.MILK_BUCKET, Material.WATER_BUCKET
    );

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Recipe recipe = event.getRecipe();

        // Only handle our custom food recipes
        NamespacedKey key = getRecipeKey(recipe);
        if (key == null || !registeredKeys.contains(key)) return;

        // Check if any ingredient was a bucket
        for (ItemStack matrix : event.getInventory().getMatrix()) {
            if (matrix != null && BUCKET_INGREDIENTS.contains(matrix.getType())) {
                // Schedule bucket return on next tick (after crafting completes)
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack bucket = new ItemStack(Material.BUCKET);
                    if (!player.getInventory().addItem(bucket).isEmpty()) {
                        player.getWorld().dropItem(player.getLocation(), bucket);
                    }
                });
                break; // Only return one bucket even if recipe uses multiple
            }
        }
    }

    // --- World-Gating ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (module.isEnabled(player.getWorld())) return;

        // Cancel if the result is one of our custom foods
        NamespacedKey key = getRecipeKey(event.getRecipe());
        if (key != null && registeredKeys.contains(key)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        // Gate furnace recipes in disabled worlds
        if (!module.isEnabled(event.getBlock().getWorld())) {
            ItemStack source = event.getSource();
            ItemStack result = event.getResult();
            // Check if input or output is a custom food
            if (registry.isCustomFood(source) || registry.isCustomFood(result)) {
                event.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private NamespacedKey getRecipeKey(Recipe recipe) {
        if (recipe instanceof ShapedRecipe sr) return sr.getKey();
        if (recipe instanceof ShapelessRecipe sr) return sr.getKey();
        if (recipe instanceof FurnaceRecipe fr) return fr.getKey();
        if (recipe instanceof CampfireRecipe cr) return cr.getKey();
        if (recipe instanceof SmokingRecipe sr) return sr.getKey();
        return null;
    }

    public void shutdown() {
        for (NamespacedKey key : registeredKeys) {
            Bukkit.removeRecipe(key);
        }
        registeredKeys.clear();
    }

    public List<NamespacedKey> getRegisteredKeys() {
        return Collections.unmodifiableList(registeredKeys);
    }
}
```

Write this to `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRecipes.java`.

- [ ] **Step 2: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRecipes.java
git commit -m "Add CustomFoodRecipes with shaped, shapeless, furnace registration"
```

---

## Task 5: CustomFoodDrops — Mob Drop Listener

**Files:**
- Create: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodDrops.java`

- [ ] **Step 1: Create CustomFoodDrops**

```java
package cz.hashiri.harshlands.foodexpansion.items;

import cz.hashiri.harshlands.foodexpansion.FoodExpansionModule;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class CustomFoodDrops implements Listener {

    private final CustomFoodRegistry registry;
    private final FoodExpansionModule module;
    private final Map<EntityType, DropDefinition> dropMap = new EnumMap<>(EntityType.class);

    public CustomFoodDrops(CustomFoodRegistry registry, FoodExpansionModule module,
                            ConfigurationSection mobDropsSection, Logger logger) {
        this.registry = registry;
        this.module = module;

        if (mobDropsSection != null) {
            loadDrops(mobDropsSection, logger);
        }
    }

    private void loadDrops(ConfigurationSection section, Logger logger) {
        for (String entityName : section.getKeys(false)) {
            ConfigurationSection dropSec = section.getConfigurationSection(entityName);
            if (dropSec == null) continue;

            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("MobDrops: unknown entity type '" + entityName + "'");
                continue;
            }

            String rawId = dropSec.getString("Item", "");
            String cookedId = dropSec.getString("CookedItem", "");
            int min = dropSec.getInt("MinAmount", 0);
            int max = dropSec.getInt("MaxAmount", 1);

            if (registry.getDefinition(rawId) == null) {
                logger.warning("MobDrops: unknown custom food '" + rawId + "' for " + entityName);
                continue;
            }

            dropMap.put(entityType, new DropDefinition(rawId, cookedId, min, max));
        }
        logger.info("Loaded " + dropMap.size() + " mob drop definitions");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        // Skip baby mobs
        if (entity instanceof org.bukkit.entity.Ageable ageable && !ageable.isAdult()) return;

        // Check world-gating
        if (!module.isEnabled(entity.getWorld())) return;

        DropDefinition drop = dropMap.get(entity.getType());
        if (drop == null) return;

        // Calculate looting bonus
        int lootingLevel = 0;
        Player killer = entity.getKiller();
        if (killer != null) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            lootingLevel = weapon.getEnchantmentLevel(Enchantment.LOOTING);
        }

        int maxWithLooting = drop.maxAmount + lootingLevel;
        int count = drop.minAmount >= maxWithLooting
            ? drop.minAmount
            : ThreadLocalRandom.current().nextInt(drop.minAmount, maxWithLooting + 1);

        if (count <= 0) return;

        // Fire detection: if entity is burning, drop cooked variant
        boolean onFire = entity.getFireTicks() > 0;
        String itemId = onFire ? drop.cookedId : drop.rawId;

        ItemStack dropStack = registry.createItemStack(itemId, count);
        event.getDrops().add(dropStack);
    }

    private record DropDefinition(String rawId, String cookedId, int minAmount, int maxAmount) {}
}
```

Write this to `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodDrops.java`.

- [ ] **Step 2: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodDrops.java
git commit -m "Add CustomFoodDrops mob drop listener with looting and fire support"
```

---

## Task 6: YAML Config — Add CustomFoods and MobDrops Sections

**Files:**
- Modify: `core/src/main/resources/foodexpansion.yml` (append after line 248)

This adds all 38 custom food definitions and 8 mob drop entries to the config.

- [ ] **Step 1: Add CustomFoods section to foodexpansion.yml**

Append the following after the Overeating section (after line 248) in `core/src/main/resources/foodexpansion.yml`. This contains all 38 items with their properties, flags, effects, and recipes exactly as specified in the design spec.

The YAML to add is large (all 38 foods). Key structure for each food:
```yaml
  CustomFoods:
    bacon:
      DisplayName: "&fBacon"
      BaseMaterial: DRIED_KELP
      CustomModelData: 10001
      Nutrition:
        Hunger: 1
        Saturation: 0.2
      Macros:
        Protein: 3.0
        Carbs: 0.0
        Fats: 4.0
      Flags: [MEAT, FAST_EAT]
      Recipe:
        Type: SHAPELESS
        Ingredients: [PORKCHOP]
        Result:
          Count: 2
```

Include ALL 38 entries from the spec's Complete Food Catalog (meats, soups, sweets, other, ingredients), with their Recipe sections matching the Complete Recipe Catalog. Include Effects sections for foods with special effects (spider_soup, blaze_cream, bat_soup, golden_feast, jelly, bat_wing, cooked_bat_wing).

Special recipe cases to handle:
- `cooked_mushroom`: `Input: "RED_MUSHROOM,BROWN_MUSHROOM"` (comma-separated for MaterialChoice)
- `roasted_seed`: `Input: "WHEAT_SEEDS,MELON_SEEDS,PUMPKIN_SEEDS,BEETROOT_SEEDS,TORCHFLOWER_SEEDS,PITCHER_POD"`
- `dough → bread`: Add as a separate entry with `Type: FURNACE`, `Input: dough`, output is vanilla BREAD. Register under key `bread_from_dough`.
- `compressed_flesh → leather`: Add as separate entry under key `leather_from_flesh`, `Type: FURNACE`, `Input: compressed_flesh`, output is vanilla LEATHER.

For the vanilla-output furnace recipes (bread_from_dough, leather_from_flesh), these cannot go inside CustomFoods because they don't define custom items. Instead, add them as special entries in a new `FoodExpansion.BonusRecipes` section:

```yaml
  BonusRecipes:
    bread_from_dough:
      Type: FURNACE
      Input: dough
      Output: BREAD
      Experience: 0.35
      CookingTime: 200
    leather_from_flesh:
      Type: FURNACE
      Input: compressed_flesh
      Output: LEATHER
      Experience: 0.35
      CookingTime: 200
```

- [ ] **Step 2: Add MobDrops section**

Append after CustomFoods in `foodexpansion.yml`:

```yaml
  MobDrops:
    SQUID:
      Item: squid
      CookedItem: cooked_squid
      MinAmount: 0
      MaxAmount: 2
    HORSE:
      Item: horse_meat
      CookedItem: cooked_horse_meat
      MinAmount: 1
      MaxAmount: 3
    BAT:
      Item: bat_wing
      CookedItem: cooked_bat_wing
      MinAmount: 0
      MaxAmount: 1
    WOLF:
      Item: wolf_meat
      CookedItem: cooked_wolf_meat
      MinAmount: 0
      MaxAmount: 2
    OCELOT:
      Item: ocelot_meat
      CookedItem: cooked_ocelot_meat
      MinAmount: 0
      MaxAmount: 1
    PARROT:
      Item: parrot_meat
      CookedItem: cooked_parrot_meat
      MinAmount: 0
      MaxAmount: 1
    LLAMA:
      Item: llama_meat
      CookedItem: cooked_llama_meat
      MinAmount: 0
      MaxAmount: 2
    POLAR_BEAR:
      Item: polar_bear_meat
      CookedItem: cooked_polar_bear_meat
      MinAmount: 0
      MaxAmount: 3
```

- [ ] **Step 3: Verify YAML validity**

Open the file and check that indentation is consistent (2 spaces), all sections are under `FoodExpansion:`, and there are no duplicate keys.

- [ ] **Step 4: Commit**

```bash
git add core/src/main/resources/foodexpansion.yml
git commit -m "Add all 38 custom food definitions and mob drops to foodexpansion.yml"
```

---

## Task 7: Wire Up FoodExpansionModule

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java`

- [ ] **Step 1: Add imports and fields**

At `FoodExpansionModule.java:3` (after existing imports), add:

```java
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodRegistry;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodRecipes;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodDrops;
```

After line 29 (`private FoodExpansionEvents events;`), add:

```java
    private CustomFoodRegistry customFoodRegistry;
    private CustomFoodRecipes customFoodRecipes;
    private CustomFoodDrops customFoodDrops;
```

- [ ] **Step 2: Modify initialize() to create and register custom food components**

In `initialize()`, after the `loadFoodMap()` call (line 53), add:

```java
        // Custom foods
        FileConfiguration feConfig = getUserConfig().getConfig();
        ConfigurationSection customFoodsSec = feConfig.getConfigurationSection("FoodExpansion.CustomFoods");
        customFoodRegistry = new CustomFoodRegistry(customFoodsSec, plugin.getLogger());

        // Register custom food macros into the shared food map
        for (cz.hashiri.harshlands.foodexpansion.items.CustomFoodDefinition def : customFoodRegistry.getAllDefinitions()) {
            if (def.getMacros() != null) {
                foodMap.put(def.getId().toLowerCase(), def.getMacros());
            }
        }

        ConfigurationSection bonusRecipesSec = feConfig.getConfigurationSection("FoodExpansion.BonusRecipes");
        customFoodRecipes = new CustomFoodRecipes(customFoodRegistry, this, plugin, customFoodsSec, bonusRecipesSec);
        Bukkit.getPluginManager().registerEvents(customFoodRecipes, plugin);

        ConfigurationSection mobDropsSec = feConfig.getConfigurationSection("FoodExpansion.MobDrops");
        customFoodDrops = new CustomFoodDrops(customFoodRegistry, this, mobDropsSec, plugin.getLogger());
        Bukkit.getPluginManager().registerEvents(customFoodDrops, plugin);
```

Note: The `foodMap` field is private. Add a package-private getter or change the visibility. Simplest: add this method to FoodExpansionModule after `getNutrientProfile()`:

```java
    Map<String, NutrientProfile> getFoodMap() {
        return foodMap;
    }
```

Then in the initialization code above, replace `foodMap.put(...)` with `getFoodMap().put(...)` or just access `foodMap` directly since initialize() is in the same class.

- [ ] **Step 3: Modify shutdown() to clean up custom food components**

In `shutdown()`, before `playerHuds.clear()` (line 95), add:

```java
        if (customFoodRecipes != null) {
            HandlerList.unregisterAll(customFoodRecipes);
            customFoodRecipes.shutdown();
        }
        if (customFoodDrops != null) {
            HandlerList.unregisterAll(customFoodDrops);
        }
```

- [ ] **Step 4: Add getter**

After the `getEvents()` method (line 168), add:

```java
    public CustomFoodRegistry getCustomFoodRegistry() {
        return customFoodRegistry;
    }
```

- [ ] **Step 5: Modify getNutrientProfile() to support lowercase custom food keys**

The current `getNutrientProfile()` at line 124-133 uppercases the key: `foodMap.get(itemKey.toUpperCase())`. Custom foods use lowercase keys. Modify the lookup to check both:

Replace line 125:
```java
        NutrientProfile profile = foodMap.get(itemKey.toUpperCase());
```

With:
```java
        // Check exact key first (handles lowercase custom food IDs), then uppercase (vanilla)
        NutrientProfile profile = foodMap.get(itemKey);
        if (profile == null) {
            profile = foodMap.get(itemKey.toUpperCase());
        }
```

- [ ] **Step 6: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java
git commit -m "Wire CustomFoodRegistry, Recipes, and Drops into FoodExpansionModule"
```

---

## Task 8: Modify FoodExpansionEvents for Custom Food Identity

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java`

This is the critical integration: resolving custom food identity via PDC in both the consume and interact handlers.

- [ ] **Step 1: Add import**

At the top of `FoodExpansionEvents.java`, add:

```java
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodRegistry;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodDefinition;
import cz.hashiri.harshlands.foodexpansion.items.FoodFlag;
import cz.hashiri.harshlands.foodexpansion.items.FoodEffect;
```

- [ ] **Step 2: Modify onPlayerConsume() for PDC-based food resolution**

Replace lines 129-131 in `onPlayerConsume()`:

```java
        String itemKey = mat.name();
        NutrientProfile profile = module.getNutrientProfile(itemKey);
        if (profile == null) return;
```

With:

```java
        // Resolve food identity: check PDC first (custom food), fall back to Material (vanilla)
        CustomFoodRegistry cfRegistry = module.getCustomFoodRegistry();
        String itemKey;
        CustomFoodDefinition customDef = null;
        if (cfRegistry != null && cfRegistry.isCustomFood(event.getItem())) {
            itemKey = cfRegistry.getFoodId(event.getItem());
            customDef = cfRegistry.getDefinition(itemKey);
        } else {
            itemKey = mat.name();
        }

        NutrientProfile profile = module.getNutrientProfile(itemKey);
        if (profile == null) return;
```

- [ ] **Step 3: Fix overeating foodKey to use PDC-resolved key**

In the overeating block within `onPlayerConsume()`, replace line 159:
```java
            String foodKey = mat.name();
```
With:
```java
            String foodKey = itemKey; // Use PDC-resolved key for custom foods
```

This ensures custom foods track satiation per-food (e.g., `"bacon"`) rather than per-base-material (e.g., `"DRIED_KELP"`).

- [ ] **Step 4: Add potion effects and BOWL return after nutrient application**

After line 171 (`data.addNutrients(profile, multiplier);`), add:

```java
        // Apply custom food effects (potion effects)
        if (customDef != null && customDef.getEffects() != null) {
            for (FoodEffect effect : customDef.getEffects()) {
                if (effect.chance() >= 1.0 || Math.random() < effect.chance()) {
                    player.addPotionEffect(new PotionEffect(
                        effect.type(), effect.durationTicks(), effect.amplifier(), false, true, true));
                }
            }
        }

        // BOWL flag safety: return bowl if base material didn't already
        if (customDef != null && customDef.hasFlag(FoodFlag.BOWL)) {
            Material baseMat = customDef.getBaseMaterial();
            // MUSHROOM_STEW already returns a bowl natively; only add for non-stew bases
            if (baseMat != Material.MUSHROOM_STEW && baseMat != Material.BEETROOT_SOUP
                    && baseMat != Material.RABBIT_STEW && baseMat != Material.SUSPICIOUS_STEW) {
                ItemStack bowl = new ItemStack(Material.BOWL);
                if (!player.getInventory().addItem(bowl).isEmpty()) {
                    player.getWorld().dropItem(player.getLocation(), bowl);
                }
            }
        }
```

- [ ] **Step 5: Rewrite onPlayerInteract() lines 186-210 for custom food PDC resolution and ALWAYS_EAT**

Replace the block from line 186 (`ItemStack item = ...`) through line 210 (`return;` after blocked message) with this consolidated version:

```java
        // Get the food item from the hand that triggered the event
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null || !item.getType().isEdible()) return;

        // Resolve food key via PDC for custom foods
        CustomFoodRegistry cfRegistry = module.getCustomFoodRegistry();
        String foodKey;
        boolean isAlwaysEat = false;
        if (cfRegistry != null && cfRegistry.isCustomFood(item)) {
            foodKey = cfRegistry.getFoodId(item);
            CustomFoodDefinition def = cfRegistry.getDefinition(foodKey);
            isAlwaysEat = def != null && def.hasFlag(FoodFlag.ALWAYS_EAT);
        } else {
            foodKey = item.getType().name();
        }

        // Only nudge if hunger is at or above threshold (player can't eat normally)
        // Exception: ALWAYS_EAT foods always get the nudge
        if (player.getFoodLevel() < hungerThreshold && !isAlwaysEat) return;

        UUID uuid = player.getUniqueId();

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastNudge = forceEatCooldowns.get(uuid);
        if (lastNudge != null && now - lastNudge < cooldownMs) return;

        // Check satiation — if at hard cap, block eating entirely
        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        int satiation = data.getSatiation(foodKey);
        double multiplier = getOvereatMultiplier(satiation);

        if (multiplier <= 0.0) {
            sendOvereatMessage(player, foodKey, satiation);
            return;
        }
```

This replaces lines 186-210 as a single clean block. The rest of the method (nudge, timeout cleanup) remains unchanged.

- [ ] **Step 6: Modify sendOvereatMessage() for custom food display names**

In `onPlayerConsume()`, the `sendOvereatMessage` calls pass `foodKey` which for custom foods is a lowercase ID like `"bacon"`. The `formatFoodName()` method at line 454 title-cases underscored names, which works for both `"COOKED_BEEF"` and `"bacon"`. However, custom foods have a display name in their definition. Modify `sendOvereatMessage()` at line 438 to use the display name if available:

Replace lines 438-451:

```java
    private void sendOvereatMessage(Player player, String foodKey, int satiationCount) {
        String foodName;
        CustomFoodRegistry cfRegistry = module.getCustomFoodRegistry();
        if (cfRegistry != null) {
            CustomFoodDefinition def = cfRegistry.getDefinition(foodKey);
            if (def != null) {
                // Strip color codes from display name
                foodName = org.bukkit.ChatColor.stripColor(
                    cz.hashiri.harshlands.utils.Utils.translateMsg(def.getDisplayName(), null, null));
            } else {
                foodName = formatFoodName(foodKey);
            }
        } else {
            foodName = formatFoodName(foodKey);
        }

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
```

- [ ] **Step 7: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 8: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionEvents.java
git commit -m "Integrate custom food PDC resolution into consume and interact handlers"
```

---

## Task 9: Add registerBonusRecipes Method to CustomFoodRecipes

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRecipes.java`

The constructor already accepts `bonusRecipesSection` and calls `registerBonusRecipes()` (added in Task 4). Now add the method implementation.

- [ ] **Step 1: Add registerBonusRecipes method**

Add this method to `CustomFoodRecipes.java`:

```java
    private void registerBonusRecipes(ConfigurationSection section) {
        for (String key : section.getKeys(false)) {
            ConfigurationSection recipeSec = section.getConfigurationSection(key);
            if (recipeSec == null) continue;

            String type = recipeSec.getString("Type", "").toUpperCase();
            if (!"FURNACE".equals(type)) {
                logger.warning("BonusRecipe '" + key + "': only FURNACE type supported, got '" + type + "'");
                continue;
            }

            String inputName = recipeSec.getString("Input", "");
            String outputName = recipeSec.getString("Output", "");
            float xp = (float) recipeSec.getDouble("Experience", 0.35);
            int cookTime = recipeSec.getInt("CookingTime", 200);

            RecipeChoice inputChoice = resolveIngredient(inputName);
            Material outputMat;
            try {
                outputMat = Material.valueOf(outputName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("BonusRecipe '" + key + "': unknown output material '" + outputName + "'");
                continue;
            }
            ItemStack result = new ItemStack(outputMat);

            // Furnace
            NamespacedKey furnaceKey = new NamespacedKey(plugin, "food_bonus_" + key);
            Bukkit.addRecipe(new FurnaceRecipe(furnaceKey, result, inputChoice, xp, cookTime));
            registeredKeys.add(furnaceKey);

            // Campfire (3x cook time)
            NamespacedKey campfireKey = new NamespacedKey(plugin, "food_bonus_" + key + "_campfire");
            Bukkit.addRecipe(new CampfireRecipe(campfireKey, result, inputChoice, xp, cookTime * 3));
            registeredKeys.add(campfireKey);

            // Smoker (0.5x cook time)
            NamespacedKey smokerKey = new NamespacedKey(plugin, "food_bonus_" + key + "_smoker");
            Bukkit.addRecipe(new SmokingRecipe(smokerKey, result, inputChoice, xp, cookTime / 2));
            registeredKeys.add(smokerKey);
        }
    }
```

- [ ] **Step 2: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/foodexpansion/items/CustomFoodRecipes.java
git commit -m "Add BonusRecipes support for dough→bread and flesh→leather"
```

---

## Task 10: Tab Completion for Custom Food IDs

**Files:**
- Modify: `core/src/main/java/cz/hashiri/harshlands/commands/Tab.java`

The `/hl give` command should offer custom food IDs in tab completion.

- [ ] **Step 1: Read Tab.java to understand current structure**

Read `core/src/main/java/cz/hashiri/harshlands/commands/Tab.java` fully. Identify where `items` set is populated (line 48: `HLItem.getItemMap().keySet()`) and where it's used in tab completion logic (likely in `onTabComplete()` when the subcommand is "give").

- [ ] **Step 2: Add custom food IDs to the items set**

After the existing `items` field initialization, the custom food IDs need to be added. Since `Tab` is constructed before modules initialize, the best approach is to add a method that `FoodExpansionModule` calls during initialization:

Add a static method to Tab.java:
```java
    public static void registerCustomFoodIds(Set<String> foodIds) {
        // This will be called by FoodExpansionModule after loading custom foods
    }
```

Or, if the items set is used for tab completion in the give subcommand, add the custom food IDs to it from FoodExpansionModule.initialize() after creating the registry:

```java
        // Add custom food IDs to tab completion
        for (String id : customFoodRegistry.getAllIds()) {
            // Register with whatever mechanism Tab.java uses
        }
```

**Note:** The exact implementation depends on how Tab.java's `onTabComplete()` method works. Read it fully before implementing. The key requirement is that typing `/hl give <tab>` shows custom food IDs alongside existing item IDs.

- [ ] **Step 3: Build to verify compilation**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn compile -pl core -am -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add core/src/main/java/cz/hashiri/harshlands/commands/Tab.java \
        core/src/main/java/cz/hashiri/harshlands/foodexpansion/FoodExpansionModule.java
git commit -m "Add custom food IDs to /hl give tab completion"
```

---

## Task 11: Build Full JAR and Verify

**Files:** None new — integration verification

- [ ] **Step 1: Full Maven build**

Run: `/c/Program\ Files/apache-maven-3.9.13/bin/mvn clean package -pl core,dist -am`
Expected: BUILD SUCCESS, output JAR in `dist/target/`

- [ ] **Step 2: Check JAR contains new classes**

Run: `jar tf dist/target/harshlands-*.jar | grep "foodexpansion/items"`
Expected: Lists all 6 new class files:
- `FoodFlag.class`
- `FoodEffect.class`
- `CustomFoodDefinition.class`
- `CustomFoodRegistry.class`
- `CustomFoodRecipes.class`
- `CustomFoodDrops.class`

- [ ] **Step 3: Verify foodexpansion.yml is in JAR**

Run: `jar tf dist/target/harshlands-*.jar | grep "foodexpansion.yml"`
Expected: `foodexpansion.yml` present

- [ ] **Step 4: Commit (if any build fixes were needed)**

```bash
git add -A
git commit -m "Fix build issues from integration"
```

---

## Task 12: Bump Config Version

**Files:**
- Modify: `core/src/main/resources/foodexpansion.yml` (line 1)

- [ ] **Step 1: Bump ConfigId**

Change line 1 of `core/src/main/resources/foodexpansion.yml`:
```yaml
ConfigId: "1.2.16-RELEASE"
```
To:
```yaml
ConfigId: "1.3.0-RELEASE"
```

- [ ] **Step 2: Commit**

```bash
git add core/src/main/resources/foodexpansion.yml
git commit -m "Bump foodexpansion.yml config version to 1.3.0"
```
