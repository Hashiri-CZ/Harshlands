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
package cz.hashiri.harshlands.iceandfire;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.HLMob;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface SeaSerpent extends HLMob {

    FileConfiguration CONFIG = HLModule.getModule(IceFireModule.NAME).getUserConfig().getConfig();

    @Override
    default List<String> getRequiredModules() {
        return List.of(IceFireModule.NAME);
    }

    @Override
    default String getParentModule() {
        return IceFireModule.NAME;
    }

    @Override
    default String name() {
        return "sea_serpent";
    }

    @Override
    default void addNbtData() {
        Utils.addNbtTag(getEntity(), "rsvmob", name(), PersistentDataType.STRING);
        Utils.addNbtTag(getEntity(), "rsvseaserpentvariant", getVariant().toString(), PersistentDataType.STRING);
    }

    @Override
    @Nonnull
    default Collection<ItemStack> getLoot(@Nullable ItemStack tool) {
        Collection<ItemStack> loot = new ArrayList<>();

        ConfigurationSection section = CONFIG.getConfigurationSection("SeaSerpent.LootTable");

        // initialize loot items
        ItemStack scales = HLItem.getItem("sea_serpent_scale_" + getVariant().toString().toLowerCase());

        loot.add(Utils.getMobLoot(section.getConfigurationSection("Scales"), scales, tool, false));
        loot.addAll(getConfigurableLoot(tool));

        return loot;
    }

    SeaSerpentVariant getVariant();
}

