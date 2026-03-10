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
package cz.hashiri.harshlands.utils.recipe;

import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Ingredient;
import cz.hashiri.harshlands.utils.HLItem;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;

public class HLShapelessRecipe extends ShapelessRecipe implements HLRecipe {

    private final ItemStack result;
    private final Collection<RecipeIngredient> ingredients = new ArrayList<>();

    public HLShapelessRecipe(@Nonnull FileConfiguration config, @Nonnull String name, @Nonnull HLPlugin plugin) {
        super(new NamespacedKey(plugin, name), HLRecipe.getResult(config, name));
        this.result = HLItem.getItem(name + ".Result");
        String ingredientsPath = name + ".Ingredients";

        for (String text : config.getStringList(ingredientsPath)) {
            if (Ingredient.isValid(text)) {
                ingredients.add(new RecipeIngredient(text));
            }
        }

        ingredients.forEach(ing -> this.addIngredient(ing.getRecipeChoice()));
    }

    public boolean isValidRecipe(@Nonnull CraftingInventory inv) {
        for (RecipeIngredient ingredient : ingredients) {
            if (!ingredient.test(inv)) {
                return false;
            }
        }

        return true;
    }

    public void useRecipe(@Nonnull PrepareItemCraftEvent e) {
        e.getInventory().setResult(result);
    }
}

