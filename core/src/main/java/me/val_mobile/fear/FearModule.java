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
package me.val_mobile.fear;

import me.val_mobile.data.ModuleItems;
import me.val_mobile.data.ModuleRecipes;
import me.val_mobile.data.RSVConfig;
import me.val_mobile.data.RSVModule;
import me.val_mobile.rsv.RSVPlugin;
import me.val_mobile.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FearModule extends RSVModule {

    public static final String NAME = "Fear";
    private final RSVPlugin plugin;
    private FearEvents events;
    private FearTorchManager torchManager;
    private FearUnlitTorchService unlitTorchService;
    private RSVConfig torchDataConfig;

    public FearModule(RSVPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new RSVConfig(plugin, "fear.yml"));
        migrateFearUserConfig();
        setItemConfig(new RSVConfig(plugin, "resources/fear/items.yml"));
        setRecipeConfig(new RSVConfig(plugin, "resources/fear/recipes.yml"));
        torchDataConfig = new RSVConfig(plugin, "resources/fear/torchdata.yml", false, false);
        migrateFearItemConfig();
        setModuleItems(new ModuleItems(this));
        setModuleRecipes(new ModuleRecipes(this, plugin));

        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Initialize.Enabled")) {
            String message = Utils.translateMsg(config.getString("Initialize.Message"), null, Map.of("NAME", NAME));
            plugin.getLogger().info(message);
        }

        getModuleItems().initialize();
        getModuleRecipes().initialize();

        if (config.getBoolean("TorchSystem.RemoveVanillaTorchRecipe")) {
            Bukkit.removeRecipe(new NamespacedKey(NamespacedKey.MINECRAFT, "torch"));
        }

        if (config.getBoolean("TorchSystem.Enabled")) {
            long burnMinutes = config.getLong("TorchSystem.BurnDurationMinutes", 60L);
            unlitTorchService = new FearUnlitTorchService(plugin, getUserConfig());
            unlitTorchService.start();
            torchManager = new FearTorchManager(plugin, burnMinutes, unlitTorchService::setUnlitFromLitTorch);
            torchManager.start();
            restoreTorchBurnData();

            events = new FearEvents(this, plugin, torchManager, unlitTorchService);
            events.initialize();
        }
    }

    @Override
    public void shutdown() {
        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Shutdown.Enabled")) {
            String message = Utils.translateMsg(config.getString("Shutdown.Message"), null, Map.of("NAME", NAME));
            plugin.getLogger().info(message);
        }

        if (torchManager != null) {
            saveTorchBurnData();
            torchManager.stop();
            torchManager = null;
        }

        if (unlitTorchService != null) {
            unlitTorchService.stop();
        }
        unlitTorchService = null;
    }

    private void saveTorchBurnData() {
        if (torchDataConfig == null || torchManager == null || unlitTorchService == null) {
            return;
        }

        FileConfiguration data = torchDataConfig.getConfig();
        data.set("LitTorches", null);
        data.set("UnlitTorches", null);

        Map<String, Long> snapshot = torchManager.snapshotRemainingLitDurations();
        for (Map.Entry<String, Long> entry : snapshot.entrySet()) {
            data.set("LitTorches." + entry.getKey(), entry.getValue());
        }
        data.set("UnlitTorches", new ArrayList<>(unlitTorchService.snapshotManagedUnlitTorches()));

        try {
            data.save(torchDataConfig.getFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to save fear torch burn data: " + exception.getMessage());
        }
    }

    private void restoreTorchBurnData() {
        if (torchDataConfig == null || torchManager == null || unlitTorchService == null) {
            return;
        }

        FileConfiguration data = torchDataConfig.getConfig();
        ConfigurationSection section = data.getConfigurationSection("LitTorches");
        Map<String, Long> persisted = new HashMap<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                persisted.put(key, section.getLong(key));
            }
        }

        torchManager.restoreLitTorches(persisted);
        List<String> unlitRaw = data.getStringList("UnlitTorches");
        if (!unlitRaw.isEmpty()) {
            Set<String> unlit = new LinkedHashSet<>(unlitRaw);
            unlitTorchService.restoreManagedUnlitTorches(unlit);
        }

        data.set("LitTorches", null);
        data.set("UnlitTorches", null);
        try {
            data.save(torchDataConfig.getFile());
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to clear restored fear torch burn data: " + exception.getMessage());
        }
    }

    private void migrateFearItemConfig() {
        FileConfiguration items = getItemConfig().getConfig();
        boolean changed = false;

        if (!"TORCH".equals(items.getString("unlit_torch.Material"))) {
            items.set("unlit_torch.Material", "TORCH");
            changed = true;
        }

        if (!items.contains("unlit_torch.ItemModel")) {
            items.set("unlit_torch.ItemModel", "DEFAULT");
            changed = true;
        }

        if (!items.contains("lit_torch")) {
            items.set("lit_torch.Material", "TORCH");
            items.set("lit_torch.DisplayName", "&fTorch");
            items.set("lit_torch.ItemModel", "minecraft:torch");
            changed = true;
        }

        if (changed) {
            try {
                items.save(getItemConfig().getFile());
                getItemConfig().reloadConfig();
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to migrate fear items config: " + exception.getMessage());
            }
        }
    }

    private void migrateFearUserConfig() {
        FileConfiguration config = getUserConfig().getConfig();
        boolean changed = false;
        String root = "TorchSystem.UnlitTorchEnforcement";

        if (!config.contains(root + ".TargetFullScanTicks")) {
            config.set(root + ".TargetFullScanTicks", 20);
            changed = true;
        }

        if (!config.contains(root + ".MinScanBatchSize")) {
            config.set(root + ".MinScanBatchSize", 32);
            changed = true;
        }

        if (changed) {
            try {
                config.save(getUserConfig().getFile());
                getUserConfig().reloadConfig();
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to migrate fear user config: " + exception.getMessage());
            }
        }
    }

}

