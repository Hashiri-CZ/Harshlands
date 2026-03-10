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
package cz.hashiri.harshlands.data.baubles;

import cz.hashiri.harshlands.utils.HLItem;
import org.bukkit.inventory.ItemStack;

public enum BaubleSlot {

    HEAD("Head", HLItem.getItem("head_slot"), 12),
    AMULET("Amulet", HLItem.getItem("amulet_slot"), 21),
    BODY("Body", HLItem.getItem("body_slot"), 30),
    RING("Ring", HLItem.getItem("ring_slot"), 14, 23),
    CHARM("Charm", HLItem.getItem("charm_slot"), 32),
    BELT("Belt", HLItem.getItem("belt_slot"), 31);

    private final int[] vals;
    private final String tag;
    private final ItemStack item;

    BaubleSlot(String tag, ItemStack item, int... vals) {
        this.vals = vals;
        this.tag = tag;
        this.item = item;
    }

    public int[] getValues() {
        return vals;
    }

    public ItemStack getItem() {
        return item;
    }

    public String getTag() {
        return tag;
    }

    public static ItemStack getItemInSlot(int slot) {
        for (BaubleSlot s : BaubleSlot.values()) {
            for (int k : s.getValues()) {
                if (slot == k) {
                    return s.getItem();
                }
            }
        }
        return HLItem.getItem("gui_glass");
    }
}

