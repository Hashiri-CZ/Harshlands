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
package cz.hashiri.harshlands.data;

import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;

public abstract class HLModule {

    private static final Map<String, HLModule> modules = new HashMap<>();
    private final boolean configEnabled;
    private final boolean isGloballyEnabled;

    @Nonnull
    private final String name;

    @Nonnull
    private final Set<String> allowedWorlds = new HashSet<>();

    @Nullable
    private ModuleItems moduleItems;

    @Nullable
    private ModuleRecipes moduleRecipes;

    @Nullable
    private HLConfig userConfig;

    @Nullable
    private HLConfig itemConfig;
    @Nullable
    private HLConfig recipesConfig;

    @Nonnull
    private final Map<HLModule, String> dependencies;

    @Nonnull
    private final Map<HLModule, String> softDependencies;

    public HLModule(@Nonnull String name, @Nonnull HLPlugin plugin, @Nonnull Map<HLModule, String> dependencies, @Nonnull Map<HLModule, String> softDependencies) {
        FileConfiguration config = plugin.getConfig();
        this.name = name;
        this.dependencies = dependencies;
        this.softDependencies = softDependencies;
        this.configEnabled = config.getBoolean(name + ".Enabled");
        this.isGloballyEnabled = configEnabled && areDependenciesEnabled();

        if (isGloballyEnabled) {
            ConfigurationSection section = config.getConfigurationSection(name + ".Worlds");
            Set<String> keys = section != null ? section.getKeys(false) : new HashSet<>();

            List<String> worlds = Utils.getAllWorldNames();
            boolean autoEnable = config.getBoolean("AutomaticallyEnableWorlds");

            for (String key : keys) {
                if (!worlds.contains(key)) {
                    config.set(name + ".Worlds." + key, null);
                }
            }

            for (String worldName : worlds) {
                if (keys.contains(worldName)) {
                    if (config.getBoolean(name + ".Worlds." + worldName)) {
                        allowedWorlds.add(worldName);
                    }
                }
                else {
                    config.createSection(name + ".Worlds." + worldName);
                    config.set(name + ".Worlds." + worldName, autoEnable);
                    allowedWorlds.add(worldName);
                }
            }

            getSoftDependencyErrorMessages().forEach(s -> plugin.getLogger().warning(s));
        }
        else {
            if (configEnabled) {
                getDependencyErrorMessages().forEach(s -> plugin.getLogger().warning(s));
            }
        }

        try {
            config.save(plugin.getConfigFile());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "[" + name + "] Failed to save config.yml", e);
        }
        modules.put(name, this);
    }

    public abstract void initialize();

    public abstract void shutdown();

    public boolean isGloballyEnabled() {
        return isGloballyEnabled;
    }

    public boolean isEnabled(@Nullable World world) {
        return isGloballyEnabled && (world == null || allowedWorlds.contains(world.getName()));
    }

    public boolean isEnabled(@Nullable Entity entity) {
        return isGloballyEnabled && (entity == null || allowedWorlds.contains(entity.getWorld().getName()));
    }

    @Nonnull
    public String getName() {
        return name;
    }

    @Nonnull
    public Set<String> getAllowedWorlds() {
        return allowedWorlds;
    }

    @Nullable
    public HLConfig getUserConfig() {
        return userConfig;
    }

    public void setUserConfig(@Nullable HLConfig config) {
        userConfig = config;
    }

    @Nullable
    public HLConfig getItemConfig() {
        return itemConfig;
    }

    public void setItemConfig(@Nullable HLConfig config) {
        itemConfig = config;
    }

    @Nullable
    public HLConfig getRecipeConfig() {
        return recipesConfig;
    }

    public void setRecipeConfig(@Nullable HLConfig config) {
        recipesConfig = config;
    }

    public void setModuleItems(@Nullable ModuleItems moduleItems) {
        this.moduleItems = moduleItems;
    }

    public void setModuleRecipes(@Nullable ModuleRecipes moduleRecipes) {
        this.moduleRecipes = moduleRecipes;
    }

    @Nullable
    public ModuleItems getModuleItems() {
        return moduleItems;
    }

    @Nullable
    public ModuleRecipes getModuleRecipes() {
        return moduleRecipes;
    }

    @Nonnull
    public static Map<String, HLModule> getModules() {
        return modules;
    }

    public boolean areDependenciesEnabled() {
        for (Map.Entry<HLModule, String> entry : dependencies.entrySet()) {
            if (entry.getKey() == null || !entry.getKey().isGloballyEnabled) {
                return false;
            }
        }
        return true;
    }

    public Collection<String> getDependencyErrorMessages() {
        Collection<String> messages = new ArrayList<>();

        for (Map.Entry<HLModule, String> entry : dependencies.entrySet()) {
            if ((entry.getKey() == null || !entry.getKey().isGloballyEnabled) && entry.getValue() != null) {
                messages.add(Utils.translateMsg("[" + name + "] " + entry.getValue(), null, null));
            }
        }

        return messages;
    }

    public Collection<String> getSoftDependencyErrorMessages() {
        Collection<String> messages = new ArrayList<>();

        for (Map.Entry<HLModule, String> entry : softDependencies.entrySet()) {
            if ((entry.getKey() == null || !entry.getKey().isGloballyEnabled) && entry.getValue() != null) {
                messages.add(Utils.translateMsg("[" + name + "] " + entry.getValue(), null, null));
            }
        }

        return messages;
    }

    @Nullable
    public static HLModule getModule(@Nonnull String name) {
        return modules.getOrDefault(name, null);
    }

    public boolean isConfigEnabled() {
        return configEnabled;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HLModule module) {
            return this.name.equals(module.name);
        }
        return false;
    }
}
