/*
    Copyright (C) 2025  Hashiri_

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.hashiri.harshlands.data;

import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import cz.hashiri.harshlands.utils.recipe.*;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.*;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

public class RecipeManager {

    private final Set<NamespacedKey> recipeKeys = new HashSet<>();
    private final Set<HLAnvilRecipe> anvilRecipes = new HashSet<>();
    private final Set<HLBrewingRecipe> brewingRecipes = new HashSet<>();
    private final HLPlugin plugin;
    private final FileConfiguration recipeConfig;
    private final FileConfiguration userConfig;

    public RecipeManager(HLPlugin plugin, FileConfiguration recipeConfig, FileConfiguration userConfig) {
        this.plugin = plugin;
        this.recipeConfig = recipeConfig;
        this.userConfig = userConfig;
        initialize();
    }

    public void initialize() {
        Set<String> keys = recipeConfig.getKeys(false);

        for (String name : keys) {
            Recipe recipe;

            String type = recipeConfig.getString(name + ".Type");

            if (type != null) {
                if (userConfig == null) {
                    recipe = getRecipe(type, name);
                    addRecipe(recipe);
                }
                else {
                    if (isEnabledForCurrentVersion("Recipes." + name + ".Enabled")) {
                        recipe = getRecipe(type, name);
                        addRecipe(recipe);
                    }
                }
            }
        }
    }

    private boolean isEnabledForCurrentVersion(String enabledRoot) {
        String singleVersionPath = enabledRoot + ".Enabled_1_21_11";
        if (userConfig.contains(singleVersionPath)) {
            return userConfig.getBoolean(singleVersionPath);
        }

        // Backward compatibility with older server configs.
        String legacyAllPath = enabledRoot + ".EnableAllVersions";
        if (userConfig.contains(legacyAllPath)) {
            return userConfig.getBoolean(legacyAllPath);
        }

        String versionPath = enabledRoot + ".Versions." + Utils.getMinecraftVersion(true);
        if (userConfig.contains(versionPath)) {
            return userConfig.getBoolean(versionPath);
        }

        return true;
    }

    public Recipe getRecipe(String type, String recipeName) {
        Recipe recipe = null;
        switch (type) {
            case "Shaped" -> {
                try {
                    recipe = new HLShapedRecipe(recipeConfig, recipeName, plugin);
                    recipeKeys.add(((ShapedRecipe) recipe).getKey());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                }
            }
            case "Shapeless" -> {
                // shapeless recipes do not work properly on spigot
                if (Utils.isServerRunningPaper()) {
                    try {
                        recipe = new HLShapelessRecipe(recipeConfig, recipeName, plugin);
                        recipeKeys.add(((ShapelessRecipe) recipe).getKey());
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                    }
                }
                else {
                    try {
                        recipe = new HLShapedRecipe(recipeConfig, recipeName, plugin);
                        recipeKeys.add(((ShapedRecipe) recipe).getKey());
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                    }
                }
            }
            case "Smithing" -> {
                try {
                    recipe = new HLSmithingRecipe(recipeConfig, recipeName, plugin);
                    recipeKeys.add(((SmithingRecipe) recipe).getKey());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                }
            }
            case "Furnace" -> {
                try {
                    recipe = new HLFurnaceRecipe(recipeConfig, recipeName, plugin);
                    recipeKeys.add(((FurnaceRecipe) recipe).getKey());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                }
            }
            case "Campfire" -> {
                try {
                    recipe = new HLCampfireRecipe(recipeConfig, recipeName, plugin);
                    recipeKeys.add(((CampfireRecipe) recipe).getKey());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                }
            }
            case "Smoker" -> {
                try {
                    recipe = new HLSmokingRecipe(recipeConfig, recipeName, plugin);
                    recipeKeys.add(((SmokingRecipe) recipe).getKey());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                }
            }
            case "Stonecutting" -> {
                try {
                    recipe = new HLStonecuttingRecipe(recipeConfig, recipeName, plugin);
                    recipeKeys.add(((StonecuttingRecipe) recipe).getKey());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                }
            }
            case "Anvil" -> {
                try {
                    recipe = new HLAnvilRecipe(recipeConfig, recipeName);
                    anvilRecipes.add((HLAnvilRecipe) recipe);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                }
            }
            case "Brewing" -> {
                try {
                    recipe = new HLBrewingRecipe(recipeConfig, recipeName, plugin);
                    brewingRecipes.add((HLBrewingRecipe) recipe);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "[RecipeManager] Failed to load '" + recipeName + "' (" + type + "): " + e.getMessage(), e);
                }
            }
            default -> {
                return null;
            }
        }
        return recipe;
    }

    public void addRecipe(Recipe r) {
        if (r != null && !(r instanceof HLBrewingRecipe || r instanceof HLAnvilRecipe)) {
            if (r instanceof Keyed keyed) {
                if (Bukkit.getRecipe(keyed.getKey()) == null) {
                    Bukkit.addRecipe(r);
                }
            }
        }
    }

    public Set<NamespacedKey> getRecipeKeys() {
        return recipeKeys;
    }

    public Set<HLAnvilRecipe> getAnvilRecipes() {
        return anvilRecipes;
    }

    public Set<HLBrewingRecipe> getBrewingRecipes() {
        return brewingRecipes;
    }
}

