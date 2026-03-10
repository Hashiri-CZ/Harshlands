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
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.FurnaceRecipe;

public class HLFurnaceRecipe extends FurnaceRecipe implements HLRecipe {

    public HLFurnaceRecipe(FileConfiguration config, String name, HLPlugin plugin) {
        super(new NamespacedKey(plugin, name), HLRecipe.getResult(config, name), new RecipeIngredient(config.getString(name + ".Input")).getRecipeChoice(),
                (float) config.getDouble(name + ".Experience"), config.getInt(name + ".CookingTime"));
    }
}

