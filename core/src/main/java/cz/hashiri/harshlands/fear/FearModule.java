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
package cz.hashiri.harshlands.fear;

import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.data.ModuleItems;
import cz.hashiri.harshlands.data.ModuleRecipes;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.db.HLDatabase;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FearModule extends HLModule {

    public static final String NAME = "Fear";
    private final HLPlugin plugin;
    private FearEvents events;
    private FearTorchManager torchManager;
    private FearUnlitTorchService unlitTorchService;
    private HLConfig torchDataConfig;
    private NightmareManager nightmareManager;

    public FearModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "fear.yml"));
        migrateFearUserConfig();
        setItemConfig(new HLConfig(plugin, "resources/fear/items.yml"));
        setRecipeConfig(new HLConfig(plugin, "resources/fear/recipes.yml"));
        torchDataConfig = new HLConfig(plugin, "resources/fear/torchdata.yml", false, false);
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

        boolean fearMeterEnabled = config.getBoolean("FearMeter.Enabled", true);
        if (fearMeterEnabled) {
            long checkInterval = config.getLong("FearMeter.CheckIntervalTicks", 100L);
            FearConditionEvaluator evaluator = new FearConditionEvaluator(plugin, config);
            FearMeterEvents fearMeterEvents = new FearMeterEvents(plugin, config);
            fearMeterEvents.initialize();

            nightmareManager = new NightmareManager(plugin, config);
            nightmareManager.initialize();

            // Periodic fear check — evaluates all conditions and applies fear delta
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                for (HLPlayer hlPlayer : new ArrayList<>(HLPlayer.getPlayers().values())) {
                    Player p = hlPlayer.getPlayer();
                    if (p == null || !p.isOnline()) continue;
                    if (p.isDead()) continue;
                    if (!isEnabled(p.getWorld())) continue;
                    cz.hashiri.harshlands.data.fear.DataModule dm = hlPlayer.getFearDataModule();
                    if (dm == null) continue;
                    evaluator.evaluate(p, dm);
                    nightmareManager.checkSpawnOrDespawn(p, dm.getFearLevel());
                }
            }, checkInterval, checkInterval);

            Bukkit.getScheduler().runTaskTimer(plugin, nightmareManager::tickAll, 20L, 20L);

            // Periodic auto-save every 5 min (6000 ticks), dirty players only
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                for (HLPlayer hlPlayer : new ArrayList<>(HLPlayer.getPlayers().values())) {
                    cz.hashiri.harshlands.data.fear.DataModule dm = hlPlayer.getFearDataModule();
                    if (dm != null && dm.isDirty()) dm.saveData();
                }
            }, 6000L, 6000L);

            // Periodic fear effects (shaking, fake sounds, heartbeat)
            long effectsInterval = config.getLong("FearMeter.EffectsIntervalTicks", 20L);
            FearEffectsTask effectsTask = new FearEffectsTask(plugin, config, this);
            Bukkit.getScheduler().runTaskTimer(plugin, effectsTask, effectsInterval, effectsInterval);
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

        if (nightmareManager != null) {
            nightmareManager.removeAllNightmares();
            nightmareManager = null;
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
        if (torchManager == null) {
            return;
        }

        // Save lit torches to DB (blocking wait to ensure completion on shutdown)
        Map<String, Long> snapshot = torchManager.snapshotRemainingLitDurations();
        HLDatabase db = plugin.getDatabase();
        if (db != null) {
            CompletableFuture<Void> future = db.saveLitTorches(snapshot);
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                plugin.getLogger().warning("[Fear] Timed out waiting for lit torch data to save to DB.");
            } catch (Exception e) {
                plugin.getLogger().warning("[Fear] Failed to save lit torch data to DB: " + e.getMessage());
            }
        }

        // Save unlit torches to YAML (kept as-is)
        if (torchDataConfig != null && unlitTorchService != null) {
            FileConfiguration data = torchDataConfig.getConfig();
            data.set("UnlitTorches", new ArrayList<>(unlitTorchService.snapshotManagedUnlitTorches()));
            try {
                data.save(torchDataConfig.getFile());
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to save fear unlit torch data: " + exception.getMessage());
            }
        }
    }

    private void restoreTorchBurnData() {
        if (torchManager == null) {
            return;
        }

        // Restore lit torches from DB
        HLDatabase db = plugin.getDatabase();
        if (db != null) {
            try {
                Map<String, Long> persisted = db.loadLitTorches().get(5, TimeUnit.SECONDS);
                torchManager.restoreLitTorches(persisted);
            } catch (TimeoutException e) {
                plugin.getLogger().warning("[Fear] Timed out loading lit torch data from DB.");
            } catch (Exception e) {
                plugin.getLogger().warning("[Fear] Failed to load lit torch data from DB: " + e.getMessage());
            }
        }

        // Restore unlit torches from YAML
        if (torchDataConfig != null && unlitTorchService != null) {
            FileConfiguration data = torchDataConfig.getConfig();
            List<String> unlitRaw = data.getStringList("UnlitTorches");
            if (!unlitRaw.isEmpty()) {
                Set<String> unlit = new LinkedHashSet<>(unlitRaw);
                unlitTorchService.restoreManagedUnlitTorches(unlit);
            }

            data.set("UnlitTorches", null);
            try {
                data.save(torchDataConfig.getFile());
            } catch (IOException exception) {
                plugin.getLogger().warning("Failed to clear restored unlit torch data: " + exception.getMessage());
            }
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
