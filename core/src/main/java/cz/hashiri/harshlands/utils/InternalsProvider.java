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
package cz.hashiri.harshlands.utils;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.baubles.EndermanAlly;
import cz.hashiri.harshlands.iceandfire.*;
import cz.hashiri.harshlands.utils.recipe.RecipeDisplayRegistry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.ItemMeta;

public abstract class InternalsProvider {

    public abstract EndermanAlly spawnEndermanAlly(Player owner, Location loc);
    public abstract FireDragon spawnFireDragon(Location loc);
    public abstract FireDragon spawnFireDragon(Location loc, int stage);
    public abstract FireDragon spawnFireDragon(Location loc, DragonVariant variant);
    public abstract FireDragon spawnFireDragon(Location loc, DragonVariant variant, int stage);

    public abstract IceDragon spawnIceDragon(Location loc);
    public abstract IceDragon spawnIceDragon(Location loc, int stage);
    public abstract IceDragon spawnIceDragon(Location loc, DragonVariant variant);
    public abstract IceDragon spawnIceDragon(Location loc, DragonVariant variant, int stage);

    public abstract LightningDragon spawnLightningDragon(Location loc);
    public abstract LightningDragon spawnLightningDragon(Location loc, int stage);
    public abstract LightningDragon spawnLightningDragon(Location loc, DragonVariant variant);
    public abstract LightningDragon spawnLightningDragon(Location loc, DragonVariant variant, int stage);

    public abstract SeaSerpent spawnSeaSerpent(Location loc);
    public abstract SeaSerpent spawnSeaSerpent(Location loc, SeaSerpentVariant variant);

    public abstract Siren spawnSiren(Location loc);

    public abstract Tag<Material> getTag(String name);

    public abstract boolean isUndead(Entity entity);

    public abstract boolean isNetheriteRecipe(SmithingInventory inv);

    public abstract void registerEntities();

    public abstract void setFreezingView(Player player, int ticks);

    public abstract void attack(LivingEntity attacker, Entity defender);

    public abstract boolean hasItemModel(ItemMeta meta);

    public abstract NamespacedKey getItemModel(ItemMeta meta);

    public abstract void setItemModel(ItemMeta meta, NamespacedKey key);

    public abstract boolean hasEquippableComponentModel(ItemMeta meta);

    public abstract NamespacedKey getEquippableComponentModel(ItemMeta meta);

    public abstract void setEquippableComponentModel(ItemMeta meta, NamespacedKey key, EquipmentSlot slot);

    public abstract boolean assignInvestigateNoiseGoal(org.bukkit.entity.Mob mob, Location target);

    /**
     * Installs the version-specific recipe display patcher that fixes the recipe
     * book / preview rendering of Harshlands custom items. Default no-op for
     * versions that do not need the fix.
     */
    public void installRecipeDisplayPatcher(@javax.annotation.Nonnull HLPlugin plugin,
                                            @javax.annotation.Nonnull RecipeDisplayRegistry registry) {
        // no-op default
    }

    /**
     * Uninstalls the patcher installed by {@link #installRecipeDisplayPatcher}.
     */
    public void uninstallRecipeDisplayPatcher() {
        // no-op default
    }
}


