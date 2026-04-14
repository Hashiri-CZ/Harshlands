/*
    Copyright (C) 2026  Hashiri_

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
package cz.hashiri.harshlands.dynamicsurroundings;

import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Map;

public class DynamicSurroundingsModule extends HLModule {

    public static final String NAME = "DynamicSurroundings";

    private final HLPlugin plugin;

    public DynamicSurroundingsModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "Settings/dynamicsurroundings.yml"));

        HLPlugin.getPlugin().getDebugManager().registerProvider(new cz.hashiri.harshlands.debug.DebugProvider() {
            @Override public String getModuleName() { return NAME; }
            @Override public java.util.Collection<String> getSubsystems() { return java.util.List.of("Ambient", "Footsteps"); }
        });

        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Initialize.Enabled")) {
            Utils.logModuleInit("dynamicsurroundings", NAME);
        }

        FootstepHandler footstepHandler = config.getBoolean("Footsteps.Enabled", true)
                ? new FootstepHandler(this, config) : null;
        ItemSoundHandler itemSoundHandler = config.getBoolean("ItemSounds.Enabled", true)
                ? new ItemSoundHandler(this, config) : null;
        AmbientSoundHandler ambientHandler = config.getBoolean("Ambient.Enabled", true)
                ? new AmbientSoundHandler(this, config) : null;

        plugin.getServer().getPluginManager().registerEvents(
                new DynamicSurroundingsEvents(this, plugin, footstepHandler, itemSoundHandler), plugin);

        if (ambientHandler != null) {
            new AmbientTask(this, ambientHandler)
                    .runTaskTimer(plugin, 100L, config.getLong("Ambient.CheckIntervalTicks", 100L));
        }
    }

    @Override
    public void shutdown() {
        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Shutdown.Enabled")) {
            Utils.logModuleShutdown("dynamicsurroundings", NAME);
        }
    }

    public String getPackUrl() {
        return plugin.getConfig().getString("DynamicSurroundings.ResourcePack.Url");
    }
}
