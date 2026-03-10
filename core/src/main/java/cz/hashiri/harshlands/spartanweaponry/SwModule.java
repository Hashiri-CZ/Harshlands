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
package cz.hashiri.harshlands.spartanweaponry;

import cz.hashiri.harshlands.data.ModuleItems;
import cz.hashiri.harshlands.data.ModuleRecipes;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public class SwModule extends HLModule {

    private final HLPlugin plugin;
    public static final String NAME = "SpartanWeaponry";

    private SwEvents events;

    public SwModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "spartanweaponry.yml"));
        setItemConfig(new HLConfig(plugin, "resources/spartanweaponry/items.yml"));
        setRecipeConfig(new HLConfig(plugin, "resources/spartanweaponry/recipes.yml"));

        setModuleItems(new ModuleItems(this));
        setModuleRecipes(new ModuleRecipes(this, plugin));

        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Initialize.Enabled")) {
            String message = Utils.translateMsg(config.getString("Initialize.Message"), null, Map.of("NAME", NAME));

            plugin.getLogger().info(message);
        }

        events = new SwEvents(this, plugin);

        getModuleItems().initialize();
        getModuleRecipes().initialize();
        events.initialize();
    }

    @Override
    public void shutdown() {
        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Shutdown.Enabled")) {
            String message = Utils.translateMsg(config.getString("Shutdown.Message"), null, Map.of("NAME", NAME));

            plugin.getLogger().info(message);
        }
    }

    public SwEvents getEvents() {
        return events;
    }

}

