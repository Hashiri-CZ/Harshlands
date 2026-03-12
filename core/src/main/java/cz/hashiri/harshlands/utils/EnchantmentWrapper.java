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

import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.inventory.ItemStack;

@SuppressWarnings("deprecation")
public class EnchantmentWrapper extends Enchantment {

    private final NamespacedKey key;
    private final String name;
    private final int maxLvl;

    public EnchantmentWrapper(String namespace, String name, int lvl) {
        this.key = NamespacedKey.minecraft(namespace);
        this.name = name;
        this.maxLvl = lvl;
    }

    @Override
    public NamespacedKey getKey() {
        return key;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getMaxLevel() {
        return maxLvl;
    }

    @Override
    public int getStartLevel() {
        return 0;
    }

    @Override
    public EnchantmentTarget getItemTarget() {
        return null;
    }

    @Override
    public boolean isTreasure() {
        return false;
    }

    @Override
    public boolean isCursed() {
        return false;
    }

    @Override
    public boolean conflictsWith(Enchantment enchantment) {
        return false;
    }

    @Override
    public boolean canEnchantItem(ItemStack itemStack) {
        return false;
    }

    @Override
    public String getTranslationKey() {
        return "enchantment." + key.getNamespace() + "." + key.getKey();
    }

    @Override
    public org.bukkit.NamespacedKey getKeyOrThrow() {
        return key;
    }

    @Override
    public org.bukkit.NamespacedKey getKeyOrNull() {
        return key;
    }

    @Override
    public boolean isRegistered() {
        return false;
    }
}
