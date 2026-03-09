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
package me.val_mobile.data;

import me.val_mobile.utils.RSVItem;
import me.val_mobile.utils.Utils;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemManager {

    private final FileConfiguration itemConfig;
    private final FileConfiguration userConfig;
    private final Map<String, RSVItem> items = new HashMap<>();
    private final RSVModule module;

    public ItemManager(RSVModule module) {
        this.itemConfig = module.getItemConfig().getConfig();
        this.userConfig = module.getUserConfig().getConfig();
        this.module = module;
        initialize();
    }

    public ItemManager(FileConfiguration itemConfig) {
        this.itemConfig = itemConfig;
        this.userConfig = null;
        this.module = null;
        initialize();
    }

    public void initialize() {
        Set<String> keys = itemConfig.getKeys(false);
        keys.remove("ConfigId");

        for (String key : keys) {
            if (userConfig == null) {
                RSVItem item = new RSVItem(null, itemConfig, key);
                items.putIfAbsent(key, item);
            }
            else {
                if (isEnabledForCurrentVersion("Items." + key + ".Enabled")) {
                    RSVItem item = new RSVItem(module, itemConfig, key);
                    items.putIfAbsent(key, item);
                }
            }
        }
    }

    private boolean isEnabledForCurrentVersion(String enabledRoot) {
        String singleVersionPath = enabledRoot + ".Enabled_1_21_11";
        if (userConfig.contains(singleVersionPath)) {
            return userConfig.getBoolean(singleVersionPath);
        }

        // Backward compatibility with older server configs.
        String legacyAllPath = enabledRoot + ".EnableAllVersions";
        if (userConfig.contains(legacyAllPath)) {
            return userConfig.getBoolean(legacyAllPath);
        }

        String versionPath = enabledRoot + ".Versions." + Utils.getMinecraftVersion(true);
        if (userConfig.contains(versionPath)) {
            return userConfig.getBoolean(versionPath);
        }

        return true;
    }

    public RSVModule getModule() {
        return module;
    }

    public Map<String, RSVItem> getItems() {
        return items;
    }

    public RSVItem getItem(String name) {
        return items.get(name);
    }
}

