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

import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.enchantments.Enchantment;

import java.util.HashSet;
import java.util.Map;

public class HLEnchants {

    private static final HashSet<Enchantment> enchants = new HashSet<>();

    public static final Enchantment WARMING = new EnchantmentWrapper("warming", "Warming", 1);
    public static final Enchantment COOLING = new EnchantmentWrapper("cooling", "Cooling", 1);
    public static final Enchantment OZZY_LINER = new EnchantmentWrapper("ozzy_liner", "Ozzy Liner", 1);

    private final HLPlugin plugin;

    public HLEnchants(HLPlugin plugin) {
        this.plugin = plugin;
        populateEnchants();
    }

    public void registerAllEnchants() {
        for (Enchantment enchant : enchants) {
            register(enchant);
        }
    }

    public void register(Enchantment ench) {
        // Custom enchantment registration via reflection is no longer supported in 1.21+.
        // Enchantments defined via EnchantmentWrapper are used directly via the static fields.
        String raw = plugin.getConfig().getString("RegisteredEnchant");
        plugin.getLogger().info(Utils.translateMsg(raw, null, Map.of("ENCHANT", ench.getKey().getKey())));
    }

    public void populateEnchants() {
        enchants.add(WARMING);
        enchants.add(COOLING);
        enchants.add(OZZY_LINER);
    }

    public static HashSet<Enchantment> getEnchants() {
        return enchants;
    }
}
