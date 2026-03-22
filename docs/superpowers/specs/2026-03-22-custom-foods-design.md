# Custom Foods Module Design

**Date:** 2026-03-22
**Status:** Approved
**Module:** FoodExpansion (sub-package: `foodexpansion.items`)

## Overview

Add 37 custom food items + 1 ingredient (dough) to the Harshlands FoodExpansion module, inspired by the Food Expansion Reimagined mod. Foods are fully YAML-driven, use PDC tags + CustomModelData for identity, and integrate with the existing macronutrition system.

## Decisions

- **Included:** 37 foods + dough (38 custom items total). **Excluded:** Forbidden Fruit, Starving Fruit (debug), Chocolate Cake (block).
- **Macronutrient profiles** pre-assigned based on real-world analogues.
- **Custom item approach:** PDC tag (`hl_food`) + CustomModelData on categorized vanilla base materials. No external plugin dependencies.
- **Fully YAML-driven:** All food properties, recipes, mob drops, and macros defined in `foodexpansion.yml`. Zero code changes needed to add/remove/tweak foods.
- **All smelting recipes** auto-register furnace + campfire + smoker variants.

## YAML Schema

### CustomFoods Section

Each food entry under `FoodExpansion.CustomFoods`:

```yaml
CustomFoods:
  <food_id>:
    DisplayName: "&fDisplay Name"
    BaseMaterial: VANILLA_MATERIAL    # Determines eat animation, stacking
    CustomModelData: 10001            # For resource pack
    Nutrition:
      Hunger: 8                       # Vanilla hunger half-drumsticks
      Saturation: 0.8                 # Vanilla saturation modifier
    Macros:
      Protein: 22.0                   # Harshlands macronutrient (0-100 scale)
      Carbs: 0.0
      Fats: 10.0
    Flags: [MEAT, FAST_EAT, BOWL, ALWAYS_EAT]   # Optional, empty by default
    Effects:                          # Optional
      - Type: NIGHT_VISION
        Duration: 200                 # ticks
        Amplifier: 0
        Chance: 1.0
    Recipe:
      Type: SHAPELESS | SHAPED | FURNACE
      # For SHAPELESS:
      Ingredients:
        - PORKCHOP                    # Vanilla material or custom food ID
      Result:
        Count: 2                      # Defaults to 1
      # For SHAPED:
      Pattern:
        - " SS"
        - " SS"
        - "W  "
      Key:
        S: SUGAR
        W: STICK
      # For FURNACE (auto-registers campfire + smoker):
      Input: bacon                    # Custom food ID or vanilla material
      Experience: 0.35
      CookingTime: 200                # Furnace ticks (campfire = 3x, smoker = 0.5x)
```

### Flags

| Flag | Behavior |
|------|----------|
| `MEAT` | Wolves can be fed with it |
| `FAST_EAT` | Informational only — indicates this food should use a fast-eat base material (DRIED_KELP, 16 ticks). The base material's native eat speed is what controls consumption time. Do not assign this flag to non-DRIED_KELP items. |
| `BOWL` | Returns bowl after eating. Redundant for MUSHROOM_STEW base (which returns bowl natively), but kept as explicit marker for clarity and safety — the consume handler checks this flag and manually returns a bowl if the base material didn't. |
| `ALWAYS_EAT` | Allows eating when hunger bar is full. Integrates with the existing overeating hunger-nudge mechanism in `onPlayerInteract()` — custom foods with this flag trigger the nudge unconditionally. The overeating satiation system still applies (diminishing returns are NOT bypassed). The `onPlayerInteract()` handler must also resolve custom food IDs via PDC (not just `Material.name()`). |

### MobDrops Section

```yaml
MobDrops:
  <ENTITY_TYPE>:
    Item: <custom_food_id>           # Raw/uncooked item
    CookedItem: <custom_food_id>     # Dropped when mob is on fire
    MinAmount: 0
    MaxAmount: 3
```

- Baby mobs excluded from drops
- Looting enchantment increases max drop by looting level
- Fire detection: if entity is burning, drops CookedItem instead of Item

### Non-Food Ingredients

Items without `Nutrition` and `Macros` sections are treated as non-food ingredients (e.g., dough). They get PDC tag and CustomModelData but no consumption behavior.

## Custom Food Identity

- **PDC tag:** `hl_food` (NamespacedKey, STRING type), value = food ID (e.g., `"bacon"`)
- Applied to every custom food ItemStack when created by `CustomFoodRegistry`

### Food Resolution in `onPlayerConsume()`

```
1. Read PDC tag "hl_food" from consumed item
2. If present → use as foodMap key (e.g., "bacon")
3. If absent → fall back to Material.name() (e.g., "COOKED_BEEF")
4. Look up NutrientProfile in foodMap
```

**Key convention:** Custom food IDs are lowercase (`bacon`), vanilla Material keys are UPPERCASE (`COOKED_BEEF`). No collision possible.

### Overeating Integration

- Satiation tracking uses the same food key — custom foods participate naturally
- `%food%` placeholder in overeating messages uses the item's display name (stripped of color codes)

## Java Architecture

### New Sub-Package: `cz.hashiri.harshlands.foodexpansion.items`

#### `CustomFoodDefinition`

Data class holding one food's parsed config:

```
Fields:
  - String id
  - String displayName
  - Material baseMaterial
  - int customModelData
  - int hunger, float saturation
  - NutrientProfile macros          (protein, carbs, fats)
  - EnumSet<FoodFlag> flags
  - List<FoodEffect> effects
  - RecipeDefinition recipe         (nullable for drop-only items)
  - boolean isFood                  (false for ingredients like dough)
```

#### `CustomFoodRegistry`

Central registry, reads `CustomFoods` section:

```
Methods:
  - loadFoods(ConfigurationSection) → Map<String, CustomFoodDefinition>
  - createItemStack(String foodId, int count) → ItemStack
      Builds stack with: base material, display name, CustomModelData,
      PDC tag "hl_food", lore (optional)
  - getDefinition(String foodId) → CustomFoodDefinition
  - isCustomFood(ItemStack) → boolean (checks PDC)
  - getFoodId(ItemStack) → String (extracts from PDC)
  - getAllDefinitions() → Collection<CustomFoodDefinition>
```

Registers all food entries' macros into `FoodExpansionModule.foodMap`.

#### `CustomFoodRecipes`

Reads `Recipe` from each definition, registers with Bukkit:

```
Methods:
  - registerAll()
      For each definition with a recipe:
        - SHAPED → Bukkit ShapedRecipe
        - SHAPELESS → Bukkit ShapelessRecipe
        - FURNACE → FurnaceRecipe + CampfireRecipe + SmokingRecipe
      Ingredient resolution: custom ID → ExactChoice(createItemStack()), UPPERCASE → Material
      NamespacedKey: "harshlands:food_<food_id>" (and "_campfire", "_smoker" suffixes)
  - shutdown()
      Removes all registered recipe NamespacedKeys
```

**Recipe registration details:**

- **Crafting recipes with custom item inputs** (e.g., bacon_and_egg needs cooked_bacon): Use `RecipeChoice.ExactChoice` with the canonical ItemStack from `CustomFoodRegistry.createItemStack()`. The registry must produce deterministic, canonical ItemStacks (no randomness in display name, lore, etc.) so ExactChoice matching works reliably.
- **Furnace recipes with custom item inputs** (e.g., cooked_bacon from bacon): Same approach — `ExactChoice` with canonical stack. A `FurnaceSmeltEvent` guard validates the PDC `hl_food` tag on the input to prevent edge cases where metadata differs.
- **Multi-material inputs**: `cooked_mushroom` accepts `RED_MUSHROOM` or `BROWN_MUSHROOM` — register as `RecipeChoice.MaterialChoice(RED_MUSHROOM, BROWN_MUSHROOM)`. `roasted_seed` accepts `WHEAT_SEEDS`, `MELON_SEEDS`, `PUMPKIN_SEEDS`, `BEETROOT_SEEDS`, `TORCHFLOWER_SEEDS`, `PITCHER_POD` — register as `MaterialChoice` with all six.
- **Bucket-return recipes**: Recipes using `MILK_BUCKET` or `WATER_BUCKET` (chocolate_bar, blaze_cream, bat_soup, beetroot_noodles, dough) must return an empty `BUCKET` after crafting. Implemented via a `PrepareItemCraftEvent` or `CraftItemEvent` handler that places a `BUCKET` in the player's inventory (or drops it if full).
- **World-gating**: Custom food recipes must respect the module's enabled-worlds list. A `PrepareItemCraftEvent` listener cancels crafting in disabled worlds. Furnace recipes are gated via `FurnaceSmeltEvent`.
- **Recipe discovery**: Custom food recipe NamespacedKeys are registered with `FoodExpansionModule` so they can be auto-discovered or granted via the existing recipe discovery system if one is added later.

#### `CustomFoodDrops`

Reads `MobDrops` section, handles EntityDeathEvent:

```
Fields:
  - Map<EntityType, DropDefinition> dropMap

Methods:
  - loadDrops(ConfigurationSection, CustomFoodRegistry)
  - onEntityDeath(EntityDeathEvent)
      Check entity type in dropMap, skip babies
      Calculate count: random(min, max) + looting bonus
      If entity on fire → CookedItem, else → Item
      Add ItemStack to drops
```

Registered as Bukkit event listener by `FoodExpansionModule`.

### Changes to Existing Code

#### `FoodExpansionEvents.onPlayerConsume()`

Before:
```java
String itemKey = mat.name();
```

After:
```java
String itemKey;
CustomFoodRegistry registry = module.getCustomFoodRegistry();
if (registry.isCustomFood(event.getItem())) {
    itemKey = registry.getFoodId(event.getItem());
} else {
    itemKey = mat.name();
}
```

Additional logic after nutrient application:
- Apply potion effects from `CustomFoodDefinition.effects`
- Handle `ALWAYS_EAT` flag in `PlayerInteractEvent` (allow eating when full)
- Handle `FAST_EAT` flag (modify consume duration)

#### `FoodExpansionModule`

- New field: `CustomFoodRegistry customFoodRegistry`
- `initialize()`: after loading vanilla food map, create registry, recipes, drops
- `shutdown()`: call `customFoodRecipes.shutdown()`
- Expose `getCustomFoodRegistry()` getter

#### `FoodExpansionModule.loadFoodMap()`

After loading vanilla foods from `Foods` section, also iterate `customFoodRegistry.getAllDefinitions()` and insert their macros into `foodMap`.

## Base Material Assignments

| Category | Base Material | Foods |
|----------|--------------|-------|
| Fast-eat | DRIED_KELP | bacon, cooked_bacon, roasted_seed |
| Raw meat | RAW_BEEF | horse_meat, wolf_meat, ocelot_meat, llama_meat, polar_bear_meat |
| Raw poultry | RAW_CHICKEN | parrot_meat, bat_wing |
| Raw fish | RAW_COD | squid |
| Cooked meat | COOKED_BEEF | cooked_horse_meat, cooked_wolf_meat, cooked_ocelot_meat, cooked_llama_meat, cooked_polar_bear_meat |
| Cooked poultry | COOKED_CHICKEN | cooked_parrot_meat, cooked_bat_wing |
| Cooked fish | COOKED_COD | cooked_squid |
| Soups/stews | MUSHROOM_STEW | carrot_seed_soup, spider_soup, nether_wart_soup, blaze_cream, melon_salad, beetroot_noodles, veggie_stew, bat_soup, golden_feast |
| Sweets | COOKIE | chocolate_bar, lollipop, jelly |
| Egg dishes | BAKED_POTATO | fried_egg, bacon_and_egg, cooked_mushroom |
| Pie | PUMPKIN_PIE | carrot_pie |
| Zombie food | ROTTEN_FLESH | compressed_flesh |
| Fruit | SWEET_BERRIES | cactus_fruit |
| Ingredient | PAPER | dough |

## Complete Food Catalog

### Meats

| ID | Display Name | Hunger | Sat | P | C | F | Flags | Source |
|----|-------------|--------|-----|---|---|---|-------|--------|
| bacon | Bacon | 1 | 0.2 | 3.0 | 0.0 | 4.0 | MEAT, FAST_EAT | Recipe: 1 Porkchop → 2 |
| cooked_bacon | Crispy Bacon | 4 | 0.4 | 8.0 | 0.0 | 10.0 | MEAT, FAST_EAT | Smelt: bacon |
| horse_meat | Horse Meat | 3 | 0.3 | 8.0 | 0.0 | 3.0 | MEAT | Drop: Horse 1-3 |
| cooked_horse_meat | Cooked Horse Meat | 8 | 0.8 | 22.0 | 0.0 | 10.0 | MEAT | Smelt: horse_meat |
| wolf_meat | Wolf Meat | 2 | 0.3 | 6.0 | 0.0 | 3.0 | MEAT | Drop: Wolf 0-2 |
| cooked_wolf_meat | Cooked Wolf Meat | 6 | 0.7 | 16.0 | 0.0 | 8.0 | MEAT | Smelt: wolf_meat |
| ocelot_meat | Ocelot Meat | 2 | 0.3 | 6.0 | 0.0 | 2.0 | MEAT | Drop: Ocelot 0-1 |
| cooked_ocelot_meat | Cooked Ocelot Meat | 6 | 0.7 | 16.0 | 0.0 | 7.0 | MEAT | Smelt: ocelot_meat |
| parrot_meat | Raw Parrot | 2 | 0.3 | 5.0 | 0.0 | 2.0 | MEAT | Drop: Parrot 0-1 |
| cooked_parrot_meat | Cooked Parrot | 6 | 0.7 | 14.0 | 0.0 | 6.0 | MEAT | Smelt: parrot_meat |
| llama_meat | Raw Llama | 2 | 0.3 | 6.0 | 0.0 | 2.0 | MEAT | Drop: Llama 0-2 |
| cooked_llama_meat | Llama Steak | 7 | 0.8 | 20.0 | 0.0 | 8.0 | MEAT | Smelt: llama_meat |
| polar_bear_meat | Raw Polar Bear | 3 | 0.3 | 8.0 | 0.0 | 5.0 | MEAT | Drop: Polar Bear 0-3 |
| cooked_polar_bear_meat | Polar Bear Steak | 8 | 0.8 | 22.0 | 0.0 | 14.0 | MEAT | Smelt: polar_bear_meat |
| bat_wing | Bat Wing | 1 | 0.1 | 2.0 | 0.0 | 1.0 | MEAT | Drop: Bat 0-1 |
| cooked_bat_wing | Cooked Bat Wing | 3 | 0.2 | 7.0 | 0.0 | 3.0 | MEAT | Smelt: bat_wing |
| squid | Raw Squid | 1 | 0.2 | 3.0 | 0.0 | 1.0 | — | Drop: Squid 0-2 |
| cooked_squid | Cooked Squid | 3 | 0.5 | 10.0 | 1.0 | 2.0 | — | Smelt: squid |

### Soups & Stews

| ID | Display Name | Hunger | Sat | P | C | F | Flags | Effects |
|----|-------------|--------|-----|---|---|---|-------|---------|
| carrot_seed_soup | Carrot Soup | 8 | 0.8 | 2.0 | 18.0 | 3.0 | BOWL | — |
| spider_soup | Spider Eye Stew | 3 | 0.4 | 3.0 | 4.0 | 2.0 | BOWL, ALWAYS_EAT | Night Vision 10s |
| nether_wart_soup | Nether Wart Stew | 4 | 0.4 | 1.0 | 8.0 | 1.0 | BOWL, ALWAYS_EAT | — |
| blaze_cream | Blaze Cream Soup | 4 | 0.4 | 1.0 | 6.0 | 4.0 | BOWL, ALWAYS_EAT | Fire Resistance 15s |
| melon_salad | Melon Salad | 6 | 0.6 | 1.0 | 15.0 | 0.5 | BOWL | — |
| beetroot_noodles | Beetroot Noodles | 6 | 0.6 | 3.0 | 14.0 | 2.0 | BOWL | — |
| veggie_stew | Veggie Stew | 10 | 1.0 | 4.0 | 20.0 | 3.0 | BOWL | — |
| bat_soup | Bat Soup | 6 | 0.6 | 8.0 | 6.0 | 4.0 | BOWL, ALWAYS_EAT | Night Vision 15s |
| golden_feast | Golden Feast | 14 | 1.0 | 5.0 | 25.0 | 5.0 | BOWL, ALWAYS_EAT | Saturation 2min |

### Sweets & Snacks

| ID | Display Name | Hunger | Sat | P | C | F | Flags | Effects |
|----|-------------|--------|-----|---|---|---|-------|---------|
| chocolate_bar | Chocolate Bar | 8 | 1.0 | 2.0 | 18.0 | 12.0 | — | — |
| lollipop | Lollipop | 4 | 0.5 | 0.0 | 12.0 | 0.0 | — | — |
| jelly | Green Jelly | 4 | 0.6 | 1.0 | 10.0 | 0.5 | ALWAYS_EAT | Nausea 10s |

### Other Foods

| ID | Display Name | Hunger | Sat | P | C | F | Flags | Effects |
|----|-------------|--------|-----|---|---|---|-------|---------|
| fried_egg | Fried Egg | 3 | 0.3 | 6.0 | 1.0 | 5.0 | — | — |
| bacon_and_egg | Bacon and Egg | 7 | 0.8 | 14.0 | 2.0 | 14.0 | — | — |
| compressed_flesh | Dried Flesh | 6 | 0.2 | 10.0 | 2.0 | 3.0 | MEAT | — |
| cactus_fruit | Cactus Fruit | 1 | 0.1 | 0.5 | 3.0 | 0.0 | — | — |
| cooked_mushroom | Cooked Mushroom | 2 | 0.2 | 2.0 | 3.0 | 0.5 | — | — |
| carrot_pie | Carrot Cake | 8 | 0.8 | 3.0 | 16.0 | 8.0 | — | — |
| roasted_seed | Roasted Seed | 1 | 0.1 | 1.0 | 1.0 | 2.0 | FAST_EAT | — |

### Ingredients (Non-Food)

| ID | Display Name | Base Material | CustomModelData |
|----|-------------|---------------|-----------------|
| dough | Dough | PAPER | 10036 |

## CustomModelData Assignments

Range 10001–10038 reserved for food expansion custom items (38 entries total: 37 foods + 1 ingredient).

| CMD | Food ID |
|-----|---------|
| 10001 | bacon |
| 10002 | cooked_bacon |
| 10003 | fried_egg |
| 10004 | bacon_and_egg |
| 10005 | jelly |
| 10006 | carrot_seed_soup |
| 10007 | squid |
| 10008 | cooked_squid |
| 10009 | compressed_flesh |
| 10010 | chocolate_bar |
| 10011 | spider_soup |
| 10012 | nether_wart_soup |
| 10013 | cactus_fruit |
| 10014 | horse_meat |
| 10015 | cooked_horse_meat |
| 10016 | cooked_mushroom |
| 10017 | carrot_pie |
| 10018 | bat_wing |
| 10019 | cooked_bat_wing |
| 10020 | blaze_cream |
| 10021 | melon_salad |
| 10022 | roasted_seed |
| 10023 | wolf_meat |
| 10024 | cooked_wolf_meat |
| 10025 | ocelot_meat |
| 10026 | cooked_ocelot_meat |
| 10027 | lollipop |
| 10028 | beetroot_noodles |
| 10029 | parrot_meat |
| 10030 | cooked_parrot_meat |
| 10031 | llama_meat |
| 10032 | cooked_llama_meat |
| 10033 | polar_bear_meat |
| 10034 | cooked_polar_bear_meat |
| 10035 | veggie_stew |
| 10036 | dough |
| 10037 | bat_soup |
| 10038 | golden_feast |

## Complete Recipe Catalog

### Crafting Recipes (Shapeless)

| Food ID | Ingredients | Count |
|---------|------------|-------|
| bacon | 1 Porkchop | 2 |
| bacon_and_egg | 1 cooked_bacon + 1 fried_egg | 1 |
| carrot_seed_soup | 1 Bowl + 2 Carrot | 1 |
| compressed_flesh | 4 Rotten Flesh + 1 Sugar | 1 |
| chocolate_bar | 2 Cocoa Beans + 2 Sugar + 1 Milk Bucket | 1 |
| spider_soup | 1 Bowl + 1 Spider Eye + 1 Fermented Spider Eye | 1 |
| nether_wart_soup | 1 Bowl + 2 Nether Wart + 1 Sugar | 1 |
| cactus_fruit | 1 Cactus | 1 |
| blaze_cream | 1 Bowl + 2 Blaze Powder + 1 Milk Bucket | 1 |
| melon_salad | 1 Bowl + 3 Melon Slice | 1 |
| beetroot_noodles | 1 Bowl + 2 Beetroot + 1 Wheat + 1 Water Bucket | 1 |
| carrot_pie | 1 Carrot + 1 Wheat + 1 Egg | 1 |
| jelly | 4 Slime Ball + 1 Sugar | 1 |
| veggie_stew | 1 Bowl + 1 Potato + 1 Carrot + 1 Beetroot + 1 Pumpkin Seeds + 1 Brown Mushroom + 1 Red Mushroom | 1 |
| bat_soup | 1 Bowl + 1 cooked_bat_wing + 1 Carrot + 1 Milk Bucket | 1 |
| golden_feast | 1 Bowl + 1 Glistering Melon Slice + 1 Golden Carrot + 1 Golden Apple | 1 |
| dough | 8 Wheat + 1 Water Bucket | 4 |

### Crafting Recipes (Shaped)

| Food ID | Pattern | Key |
|---------|---------|-----|
| lollipop | ` SS` / ` SS` / `W  ` | S=Sugar, W=Stick |

### Smelting Recipes (Furnace + Campfire + Smoker)

| Output | Input | XP | Furnace Time | Campfire Time | Smoker Time |
|--------|-------|----|-------------|---------------|-------------|
| cooked_bacon | bacon | 0.35 | 200 | 600 | 100 |
| fried_egg | Egg | 0.35 | 200 | 600 | 100 |
| cooked_horse_meat | horse_meat | 0.35 | 200 | 600 | 100 |
| cooked_wolf_meat | wolf_meat | 0.35 | 200 | 600 | 100 |
| cooked_ocelot_meat | ocelot_meat | 0.35 | 200 | 600 | 100 |
| cooked_parrot_meat | parrot_meat | 0.35 | 200 | 600 | 100 |
| cooked_llama_meat | llama_meat | 0.35 | 200 | 600 | 100 |
| cooked_polar_bear_meat | polar_bear_meat | 0.35 | 200 | 600 | 100 |
| cooked_bat_wing | bat_wing | 0.35 | 200 | 600 | 100 |
| cooked_squid | squid | 0.35 | 200 | 600 | 100 |
| cooked_mushroom | RED_MUSHROOM, BROWN_MUSHROOM (MaterialChoice) | 0.35 | 200 | 600 | 100 |
| roasted_seed | WHEAT_SEEDS, MELON_SEEDS, PUMPKIN_SEEDS, BEETROOT_SEEDS, TORCHFLOWER_SEEDS, PITCHER_POD (MaterialChoice) | 0.35 | 200 | 600 | 100 |
| Bread (vanilla) | dough | 0.35 | 200 | 600 | 100 |

### Bonus Recipe

| Output | Input | Type |
|--------|-------|------|
| Leather (vanilla) | compressed_flesh | Furnace (all 3) |

## Mob Drop Table

| Entity Type | Raw Item | Cooked Item | Min | Max | Notes |
|-------------|----------|-------------|-----|-----|-------|
| SQUID | squid | cooked_squid | 0 | 2 | |
| HORSE | horse_meat | cooked_horse_meat | 1 | 3 | Always drops ≥1 |
| BAT | bat_wing | cooked_bat_wing | 0 | 1 | |
| WOLF | wolf_meat | cooked_wolf_meat | 0 | 2 | |
| OCELOT | ocelot_meat | cooked_ocelot_meat | 0 | 1 | |
| PARROT | parrot_meat | cooked_parrot_meat | 0 | 1 | |
| LLAMA | llama_meat | cooked_llama_meat | 0 | 2 | |
| POLAR_BEAR | polar_bear_meat | cooked_polar_bear_meat | 0 | 3 | |

All drops: +1 max per looting level, no drops from baby mobs, cooked variant when entity is on fire.

## Resource Pack Requirements

The user will handle resource pack population. Requirements:
- 38 item textures needed: 37 foods + dough (Bread and Leather outputs are vanilla items, no custom texture needed)
- CustomModelData predicates on each base material matching the CMD values above (10001–10038)
- Model files for each custom food item

## Admin Commands

Custom food IDs should be registered with the existing `/hl` command system:
- `CustomFoodRegistry` exposes `getAllDefinitions()` which provides the ID list for tab completion
- `Tab.java` should include custom food IDs in relevant completions (e.g., give command)
- `createItemStack(foodId, count)` is the API for giving items to players

## Migration & Versioning

- Bump `foodexpansion.yml` ConfigId when custom foods are added
- CustomModelData range 10001–10038 is reserved; future foods should start at 10039+
- If a food is removed from config, its recipes are simply not registered — no migration needed
- Existing items in player inventories with removed food IDs will still have the PDC tag but won't provide macronutrients (falls through to default profile)
