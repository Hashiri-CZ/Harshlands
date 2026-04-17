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

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeDisplayRegistry {

    private final Map<NamespacedKey, Map<Integer, List<ItemStack>>> bySlot = new HashMap<>();

    public void register(@Nonnull NamespacedKey key, int slotIndex, @Nonnull List<ItemStack> displayStacks) {
        if (displayStacks.isEmpty()) {
            return;
        }
        bySlot.computeIfAbsent(key, k -> new HashMap<>()).put(slotIndex, List.copyOf(displayStacks));
    }

    @Nullable
    public Map<Integer, List<ItemStack>> get(@Nonnull NamespacedKey key) {
        Map<Integer, List<ItemStack>> slots = bySlot.get(key);
        return slots == null ? null : Collections.unmodifiableMap(slots);
    }

    public boolean contains(@Nonnull NamespacedKey key) {
        return bySlot.containsKey(key);
    }
}
