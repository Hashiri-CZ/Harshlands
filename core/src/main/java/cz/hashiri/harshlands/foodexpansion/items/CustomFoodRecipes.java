package cz.hashiri.harshlands.foodexpansion.items;

import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.foodexpansion.FoodExpansionModule;
import cz.hashiri.harshlands.utils.Utils;
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
        Utils.logStartup("Registered " + count + " custom food recipes");
    }

    private void registerShapeless(String foodId, ConfigurationSection recipeSec) {
        NamespacedKey key = new NamespacedKey(plugin, "food_" + foodId.toLowerCase());
        int resultCount = recipeSec.getInt("Count", recipeSec.getInt("Result.Count", 1));
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
        int resultCount = recipeSec.getInt("Count", recipeSec.getInt("Result.Count", 1));
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

        CustomFoodDefinition def = registry.getDefinition(foodId);
        ItemStack result;
        if (def != null) {
            result = registry.createItemStack(foodId, 1);
        } else {
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

    private RecipeChoice resolveIngredient(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Empty ingredient name");
        }

        if (name.contains(",")) {
            List<Material> materials = new ArrayList<>();
            for (String part : name.split(",")) {
                materials.add(Material.valueOf(part.trim().toUpperCase()));
            }
            return new RecipeChoice.MaterialChoice(materials);
        }

        CustomFoodDefinition def = registry.getDefinition(name);
        if (def != null) {
            return new RecipeChoice.ExactChoice(registry.createItemStack(name, 1));
        }

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

        NamespacedKey key = getRecipeKey(recipe);
        if (key == null || !registeredKeys.contains(key)) return;

        for (ItemStack matrix : event.getInventory().getMatrix()) {
            if (matrix != null && BUCKET_INGREDIENTS.contains(matrix.getType())) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    ItemStack bucket = new ItemStack(Material.BUCKET);
                    if (!player.getInventory().addItem(bucket).isEmpty()) {
                        player.getWorld().dropItem(player.getLocation(), bucket);
                    }
                });
                break;
            }
        }
    }

    // --- World-Gating ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (module.isEnabled(player.getWorld())) return;

        NamespacedKey key = getRecipeKey(event.getRecipe());
        if (key != null && registeredKeys.contains(key)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        if (!module.isEnabled(event.getBlock().getWorld())) {
            ItemStack source = event.getSource();
            ItemStack result = event.getResult();
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
