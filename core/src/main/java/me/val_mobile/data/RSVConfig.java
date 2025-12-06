/*
    Copyright (C) 2025  Val_Mobile

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
package me.val_mobile.data;

import me.val_mobile.rsv.RSVPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class RSVConfig extends FileBuilder {

    private static final Set<RSVConfig> configList = new HashSet<>();
    private boolean updated;

    private final String path;
    private final RSVPlugin plugin;
    private final boolean updateOldVersions;
    private FileConfiguration config;

    public RSVConfig(RSVPlugin plugin, String path, boolean replace, boolean updateOldVersions) {
        super(plugin, path, replace);
        this.plugin = plugin;
        this.path = path;
        this.updateOldVersions = updateOldVersions;
        createConfig();

        configList.add(this);
    }

    public RSVConfig(RSVPlugin plugin, String path) {
        this(plugin, path, false, true);
    }

    public void createConfig() {
        config = new YamlConfiguration(); // create a temporary empty config

        // catch and print any exceptions while loading config
        try {
            // load the file into the config
            config.load(file);
        } catch (IOException | InvalidConfigurationException e) {
            // print any exceptions that are thrown
            e.printStackTrace();
        }

        if (updateOldVersions && !updated) {
            updateConfig();
        }
    }

    private void updateConfig() {
        String currentVersion = config.contains("ConfigId") ? config.getString("ConfigId") : "";
        String latestVersion = plugin.getDescription().getVersion();
        updated = true;

        if (isOlderVersion(currentVersion, latestVersion)) {
            int num = 0;
            String newPath = path.replace(".yml", "_Backup_" + num + ".yml");
            while (new File(plugin.getDataFolder(), newPath).exists()) {
                num++;
                newPath = newPath.replace("_Backup_" + (num - 1), "_Backup_" + num);
            }

            FileConfiguration pluginConfig = plugin.getConfig();
            boolean autoUpdate = pluginConfig == null || pluginConfig.getBoolean("AutoUpdateConfig");

            if (autoUpdate) {
                try {
                    // Create backup
                    Files.copy(Path.of(file.getAbsolutePath()), Path.of(file.getAbsolutePath().replace(".yml", "_Backup_" + num + ".yml")));

                    InputStream stream = plugin.getResource(path);
                    InputStreamReader reader = new InputStreamReader(stream);
                    FileConfiguration embedded = YamlConfiguration.loadConfiguration(reader);

                    Set<String> embeddedKeys = embedded.getKeys(true);
                    Set<String> configKeys = config.getKeys(true);

                    for (String key : embeddedKeys) {
                        if (!configKeys.contains(key)) {
                            config.set(key, embedded.get(key));
                        }
                    }

                    config.set("ConfigId", latestVersion);
                    config.save(file);

                    plugin.getLogger().info("[RSVConfig] Updating config: " + currentVersion + " -> " + latestVersion);

                } catch (IOException e) {
                    plugin.getLogger().severe("[RSVConfig] Failed to update config: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                // AutoUpdate disabled: just create backup and replace config
                file.renameTo(new File(plugin.getDataFolder(), newPath));
                createFile(path);
                createConfig();
                plugin.getLogger().info("[RSVConfig] AutoUpdate disabled, replaced config with new default: " + currentVersion + " -> " + latestVersion);
            }

        }
        // No else block needed – no logs if config is already up-to-date
    }

    /** Semantic version comparison helper */
    private boolean isOlderVersion(String current, String latest) {
        String[] currParts = current.replace("-RELEASE", "").split("\\.");
        String[] latestParts = latest.replace("-RELEASE", "").split("\\.");

        for (int i = 0; i < Math.max(currParts.length, latestParts.length); i++) {
            int currNum = i < currParts.length ? Integer.parseInt(currParts[i]) : 0;
            int latestNum = i < latestParts.length ? Integer.parseInt(latestParts[i]) : 0;
            if (currNum < latestNum) return true;
            if (currNum > latestNum) return false;
        }
        return false;
    }



    /**
     * Gets the config
     * @return The config
     */
    public FileConfiguration getConfig() {
        return config;
    }

    /**
     * Assigns the current config to a new one
     * @param config The new config which the current config should be set to
     */
    public void setConfig(FileConfiguration config) {
        this.config = config;
    }

    /**
     * Reloads the config to use the most recent values
     */
    public void reloadConfig() {
        setConfig(YamlConfiguration.loadConfiguration(file));
    }

    public static Set<RSVConfig> getConfigList() {
        return configList;
    }
}
