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
package cz.hashiri.harshlands.tan;

import cz.hashiri.harshlands.data.ModuleItems;
import cz.hashiri.harshlands.data.ModuleRecipes;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.data.toughasnails.DataModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TanModule extends HLModule {

    private final HLPlugin plugin;

    private TanEvents events;

    public static final String NAME = "ToughAsNails";
    private final Set<UUID> hypothermiaDeath = new HashSet<>();
    private final Set<UUID> hyperthermiaDeath = new HashSet<>();
    private final Set<UUID> dehydrationDeath = new HashSet<>();
    private final Set<UUID> parasiteDeath = new HashSet<>();
    private final TempManager tempManager;
    private ThirstManager thirstManager;
    private boolean tempGloballyEnabled;
    private boolean thirstGloballyEnabled;

    public TanModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
        this.tempManager = new TempManager(this);
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "toughasnails.yml"));
        setItemConfig(new HLConfig(plugin, "resources/toughasnails/items.yml"));
        setRecipeConfig(new HLConfig(plugin, "resources/toughasnails/recipes.yml"));
        this.thirstManager = new ThirstManager(this);

        setModuleItems(new ModuleItems(this));
        setModuleRecipes(new ModuleRecipes(this, plugin));

        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Initialize.Enabled")) {
            String message = Utils.translateMsg(config.getString("Initialize.Message"), null, Map.of("NAME", NAME));

            plugin.getLogger().info(message);
        }

        this.tempGloballyEnabled = config.getBoolean("Temperature.Enabled") && isGloballyEnabled();
        this.thirstGloballyEnabled = config.getBoolean("Thirst.Enabled") && isGloballyEnabled();

        events = new TanEvents(this, plugin);

        getModuleItems().initialize();
        getModuleRecipes().initialize();
        events.initialize();

        // Periodic auto-save every 5 minutes (6000 ticks) for dirty player data
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            List<HLPlayer> snapshot = new ArrayList<>(HLPlayer.getPlayers().values());
            for (HLPlayer rsvPlayer : snapshot) {
                DataModule dm = rsvPlayer.getTanDataModule();
                if (dm != null && dm.isDirty()) {
                    dm.saveData();
                }
            }
        }, 6000L, 6000L);
    }

    @Override
    public void shutdown() {
        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Shutdown.Enabled")) {
            String message = Utils.translateMsg(config.getString("Shutdown.Message"), null, Map.of("NAME", NAME));

            plugin.getLogger().info(message);
        }
    }

    @Nonnull
    public TempManager getTempManager() {
        return tempManager;
    }

    @Nonnull
    public ThirstManager getThirstManager() {
        return thirstManager;
    }

    @Nonnull
    public TanEvents getEvents() {
        return events;
    }

    @Nonnull
    public Set<UUID> getDehydrationDeath() {
        return dehydrationDeath;
    }

    @Nonnull
    public Set<UUID> getHyperthermiaDeath() {
        return hyperthermiaDeath;
    }

    @Nonnull
    public Set<UUID> getHypothermiaDeath() {
        return hypothermiaDeath;
    }

    @Nonnull
    public Set<UUID> getParasiteDeath() {
        return parasiteDeath;
    }

    public boolean isTempGloballyEnabled() {
        return tempGloballyEnabled;
    }

    public boolean isThirstGloballyEnabled() {
        return thirstGloballyEnabled;
    }
}
