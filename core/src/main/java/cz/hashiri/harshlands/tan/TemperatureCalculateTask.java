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

import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.integrations.CompatiblePlugin;
import cz.hashiri.harshlands.integrations.RealisticSeasons;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.HLTask;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.ConfigurationSection;

public class TemperatureCalculateTask extends BukkitRunnable implements HLTask {

    private static final Map<UUID, TemperatureCalculateTask> tasks = new ConcurrentHashMap<>();
    private final RealisticSeasons rs;
    private final TanModule module;
    private final TempManager manager;
    private final FileConfiguration config;
    private final HLPlugin plugin;
    private final HLPlayer player;
    private final UUID id;
    private final Collection<String> allowedWorlds;
    private double equilibriumTemp;
    private double regulate = 0D;
    private double change = 0D;
    private volatile double regulateEnv = 0D;
    private volatile double changeEnv = 0D;
    private final double seasonsDefaultTemp;
    private final double seasonsColdMultiplier;
    private final double seasonsHotMultiplier;
    private final double distSqr;

    // --- Cached config values (read once in constructor) ---
    private final double hotCutoff;
    private final double hotMultiplier;
    private final double warmCutoff;
    private final double warmMultiplier;
    private final double moderateCutoff;
    private final double moderateMultiplier;
    private final double coolCutoff;
    private final double coolMultiplier;
    private final double coldCutoff;
    private final double coldMultiplier;
    private final double frigidMultiplier;
    private final double tempMaxChange;
    private final double daylightCycleMultiplier;
    private final int cubeLength;

    private final boolean hypothermiaEnabled;
    private final double hypothermiaTemp;
    private final boolean coldBreathEnabled;
    private final double coldBreathMaxTemp;
    private final boolean hyperthermiaEnabled;
    private final double hyperthermiaTemp;
    private final boolean sweatingEnabled;
    private final double sweatingMinTemp;

    private record AddEntry(double value, boolean isRegulatory, boolean hasEnabledFlag, boolean enabled) {}
    private final Map<String, AddEntry> addEntries;

    private double temp;
    private Location currentLoc;
    public static final double MINIMUM_TEMPERATURE = 0.0;
    public static final double MAXIMUM_TEMPERATURE = 25.0;

    public static final double NEUTRAL_TEMPERATURE = 12.5;

    public TemperatureCalculateTask(TanModule module, HLPlugin plugin, HLPlayer player) {
        this.plugin = plugin;
        this.module = module;
        this.manager = module.getTempManager();
        this.config = module.getUserConfig().getConfig();
        this.player = player;
        this.id = player.getPlayer().getUniqueId();
        this.temp = player.getTanDataModule().getTemperature();
        this.allowedWorlds = module.getAllowedWorlds();
        this.currentLoc = player.getPlayer().getLocation();
        this.cubeLength = config.getInt("Temperature.Environment.CubeLength");
        this.distSqr = (double) cubeLength * cubeLength;
        this.rs = (RealisticSeasons) CompatiblePlugin.getPlugin(RealisticSeasons.NAME);

        this.seasonsDefaultTemp = rs.getDefaultTemperature();
        this.seasonsColdMultiplier = rs.getColdMultiplier();
        this.seasonsHotMultiplier = rs.getHotMultiplier();

        // Cache biome temperature thresholds
        this.hotCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.HotCutoff");
        this.hotMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.HotMultiplier");
        this.warmCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.WarmCutoff");
        this.warmMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.WarmMultiplier");
        this.moderateCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.ModerateCutoff");
        this.moderateMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.ModerateMultiplier");
        this.coolCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.CoolCutoff");
        this.coolMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.CoolMultiplier");
        this.coldCutoff = config.getDouble("Temperature.Environment.BiomeTemperature.ColdCutoff");
        this.coldMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.ColdMultiplier");
        this.frigidMultiplier = config.getDouble("Temperature.Environment.BiomeTemperature.FrigidMultiplier");
        this.tempMaxChange = config.getDouble("Temperature.MaxChange");
        this.daylightCycleMultiplier = config.getDouble("Temperature.Environment.DaylightCycleMultiplier");

        this.hypothermiaEnabled = config.getBoolean("Temperature.Hypothermia.Enabled");
        this.hypothermiaTemp = config.getDouble("Temperature.Hypothermia.Temperature");
        this.coldBreathEnabled = config.getBoolean("Temperature.ColdBreath.Enabled");
        this.coldBreathMaxTemp = config.getDouble("Temperature.ColdBreath.MaximumTemperature");
        this.hyperthermiaEnabled = config.getBoolean("Temperature.Hyperthermia.Enabled");
        this.hyperthermiaTemp = config.getDouble("Temperature.Hyperthermia.Temperature");
        this.sweatingEnabled = config.getBoolean("Temperature.Sweating.Enabled");
        this.sweatingMinTemp = config.getDouble("Temperature.Sweating.MinimumTemperature");

        this.addEntries = buildAddEntries(config);

        // MUST be last — makes task visible to other threads
        tasks.put(id, this);
    }

    @Override
    public void run() {
        Player player = this.player.getPlayer();

        if (conditionsMet(player)) {
            double oldTemp = temp;

            if (rs.isIntegrated() && rs.hasTemperatureEnabled(player)) {
                int seasonsTemp = rs.getTemperature(player);
                temp = (seasonsTemp - seasonsDefaultTemp) * (seasonsTemp > seasonsDefaultTemp ? seasonsHotMultiplier : seasonsColdMultiplier) + MAXIMUM_TEMPERATURE / 2;
            }
            else {
                regulate = 0D;
                change = 0D;
                World pWorld = player.getWorld();
                Location pLoc = player.getLocation();
                double px = pLoc.getX();
                double py = pLoc.getY();
                double pz = pLoc.getZ();

                double biomeTemp = pWorld.getTemperature((int) px, (int) py, (int) pz); // create a variable to store the temperature

                if (biomeTemp > hotCutoff) {
                    biomeTemp *= hotMultiplier;
                }
                else {
                    // less than hot cutoff
                    if (biomeTemp >= warmCutoff) {
                        biomeTemp *= warmMultiplier;
                    }
                    // less than warm cutoff
                    else if (biomeTemp >= moderateCutoff) {
                        biomeTemp *= moderateMultiplier;
                    }
                    // less than moderate cutoff
                    else if (biomeTemp >= coolCutoff) {
                        biomeTemp *= coolMultiplier;
                    }
                    // less than cool cutoff
                    else if (biomeTemp >= coldCutoff) {
                        biomeTemp *= coldMultiplier;
                    }
                    else {
                        biomeTemp *= frigidMultiplier;
                    }
                }

                double daylightChange = pWorld.getEnvironment() == World.Environment.NORMAL ? Math.sin(2 * Math.PI / 24000 * pWorld.getTime() - 3500) * daylightCycleMultiplier : 0D;
                double worldChange = biomeTemp + daylightChange;
                change += worldChange;

                change += changeEnv;
                regulate += regulateEnv;

                if (pLoc.getWorld().getName().equals(currentLoc.getWorld().getName())) {
                    if (pLoc.distanceSquared(currentLoc) > distSqr) {
                        currentLoc = pLoc;
                        new TemperatureEnvironmentTask(module, plugin, this.player, getRelevantChunkSnapshots(player)).start();
                    }
                }
                else {
                    currentLoc = pLoc;
                    new TemperatureEnvironmentTask(module, plugin, this.player, getRelevantChunkSnapshots(player)).start();
                }


                if (player.isInWater()) {
                    add("Temperature.Environment.SubmergedWater");
                }

                if (Utils.isInLava(player)) {
                    add("Temperature.Environment.SubmergedLava");
                }

                if (Utils.isExposedToSky(player)) {
                    if (pWorld.hasStorm()) {
                        add("Temperature.Environment.Storming");
                    }
                }
                else {
                    add("Temperature.Environment.Housed");
                }

                if (player.getFireTicks() > 0) {
                    add("Temperature.Environment.Burning");
                }

                for (ItemStack item : player.getInventory().getArmorContents()) {
                    if (Utils.isItemReal(item)) {
                        ItemMeta meta = item.getItemMeta();

                        if (HLItem.isHLItem(item)) {
                            String itemName = HLItem.getNameFromItem(item);

                            switch (itemName) {
                                case "wool_hood", "wool_boots", "wool_pants", "wool_jacket", "jelled_slime_helmet", "jelled_slime_chestplate", "jelled_slime_leggings", "jelled_slime_boots" ->
                                        add("Temperature.Armor." + itemName);
                                default -> {}
                            }
                        }
                        else {
                            Material mat = item.getType();
                            if (Utils.isArmor(mat)) {
                                add("Temperature.Armor." + mat);
                            }
                        }

//                        if (meta.hasEnchant(HLEnchants.COOLING)) {
//                            add("Temperature.Enchantments.Cooling");
//                        }
//
//                        if (meta.hasEnchant(HLEnchants.WARMING)) {
//                            add("Temperature.Enchantments.Warming");
//                        }
//
//                        if (meta.hasEnchant(HLEnchants.OZZY_LINER)) {
//                            add("Temperature.Enchantments.OzzyLiner");
//                        }
                    }
                }

                double normalTemp = NEUTRAL_TEMPERATURE + change;
                double regulatedTemp = temp;

                if (normalTemp != NEUTRAL_TEMPERATURE) {
                    if (normalTemp > NEUTRAL_TEMPERATURE) {
                        regulatedTemp = Math.max(normalTemp - regulate, NEUTRAL_TEMPERATURE);

                    }
                    else {
                        regulatedTemp = Math.min(normalTemp + regulate, NEUTRAL_TEMPERATURE);
                    }
                }

                equilibriumTemp = regulatedTemp;

                if (Math.abs(temp - regulatedTemp) < tempMaxChange) {
                    temp = regulatedTemp;
                }
                else {
                    temp = regulatedTemp > temp ? temp + tempMaxChange : temp - tempMaxChange;
                }
            }

            if (temp != NEUTRAL_TEMPERATURE) {
                if (temp < NEUTRAL_TEMPERATURE && hasColdImmunity(player)) {
                    temp = NEUTRAL_TEMPERATURE;
                }
                if (temp > NEUTRAL_TEMPERATURE && hasHotImmunity(player)) {
                    temp = NEUTRAL_TEMPERATURE;
                }
            }

            if (!hasColdImmunity(player)) {
                if (!rs.disableHypothermiaCompletely()) {
                    if (hypothermiaEnabled) {
                        if (temp <= hypothermiaTemp) {
                            if (!HypothermiaTask.hasTask(id)) {
                                new HypothermiaTask(module, plugin, this.player).start();
                            }
                        }
                    }
                }

                if (!rs.disableColdBreath()) {
                    if (!player.hasPermission("harshlands.toughasnails.resistance.cold.breath")) {
                        if (coldBreathEnabled) {
                            if (temp <= coldBreathMaxTemp) {
                                if (!ColdBreathTask.hasTask(id)) {
                                    new ColdBreathTask(module, plugin, this.player).start();
                                }
                            }
                        }
                    }
                }
            }

            if (!hasHotImmunity(player)) {
                if (!rs.disableHyperthermiaCompletely()) {
                    if (hyperthermiaEnabled) {
                        if (temp >= hyperthermiaTemp) {
                            if (!HyperthermiaTask.hasTask(id)) {
                                new HyperthermiaTask(module, plugin, this.player).start();
                            }
                        }
                    }
                }

                if (!rs.disableSweating()) {
                    if (!player.hasPermission("harshlands.toughasnails.resistance.hot.sweat")) {
                        if (sweatingEnabled) {
                            if (temp >= sweatingMinTemp) {
                                if (!SweatTask.hasTask(id)) {
                                    new SweatTask(module, plugin, this.player).start();
                                }
                            }
                        }
                    }
                }
            }

            if (!Utils.doublesEquals(temp, oldTemp)) {
                Bukkit.getServer().getPluginManager().callEvent(new TemperatureChangeEvent(player, oldTemp, temp));
            }
            manager.setTemperature(player, temp);

            // Debug instrumentation
            cz.hashiri.harshlands.debug.DebugManager debugMgr = plugin.getDebugManager();
            if (debugMgr.isActive("ToughAsNails", "Temperature", id)) {
                String chatLine = Math.abs(temp - oldTemp) >= 0.5
                        ? "§6[Temp] §f" + String.format("%.1f", oldTemp) + " §7-> §f" + String.format("%.1f", temp)
                        : "";
                String consoleLine = "temp=" + String.format("%.2f", temp)
                        + " eq=" + String.format("%.2f", equilibriumTemp)
                        + " change=" + String.format("%.2f", change) + " regulate=" + String.format("%.2f", regulate)
                        + " changeEnv=" + String.format("%.2f", changeEnv) + " regulateEnv=" + String.format("%.2f", regulateEnv)
                        + " inWater=" + player.isInWater() + " burning=" + (player.getFireTicks() > 0);
                debugMgr.send("ToughAsNails", "Temperature", id, chatLine, consoleLine);
            }
        }
        else {
            stop();
        }
    }

    public void add(String configPath) {
        AddEntry entry = addEntries.get(configPath);
        if (entry == null) return;
        if (entry.hasEnabledFlag() && !entry.enabled()) return;
        if (entry.isRegulatory()) regulate += entry.value();
        else change += entry.value();
    }

    private static Map<String, AddEntry> buildAddEntries(FileConfiguration config) {
        Map<String, AddEntry> map = new HashMap<>();
        for (String prefix : List.of("Temperature.Environment", "Temperature.Armor", "Temperature.Enchantments")) {
            ConfigurationSection section = config.getConfigurationSection(prefix);
            if (section == null) continue;
            for (String key : section.getKeys(false)) {
                String path = prefix + "." + key;
                // Skip sub-sections without Value (BiomeTemperature, Blocks, CubeLength, etc.)
                if (!config.contains(path + ".Value")) continue;
                double value = config.getDouble(path + ".Value");
                boolean isReg = config.getBoolean(path + ".IsRegulatory", false);
                boolean hasEnabled = config.contains(path + ".Enabled");
                boolean enabled = config.getBoolean(path + ".Enabled", true);
                map.put(path, new AddEntry(value, isReg, hasEnabled, enabled));
            }
        }
        return map;
    }

    private boolean hasHotImmunity(@Nonnull Player player) {
        return player.hasPermission("harshlands.toughasnails.resistance.hot.*");
    }

    private boolean hasColdImmunity(@Nonnull Player player) {
        return player.hasPermission("harshlands.toughasnails.resistance.cold.*");
    }

    private Map<Long, ChunkSnapshot> getRelevantChunkSnapshots(Player player) {
        return TemperatureEnvironmentTask.captureSnapshots(player.getLocation(), cubeLength);
    }

    @Override
    public boolean conditionsMet(@Nullable Player player) {
        return globalConditionsMet(player) && allowedWorlds.contains(player.getWorld().getName());
    }

    @Override
    public void start() {
        new TemperatureEnvironmentTask(module, plugin, player, getRelevantChunkSnapshots(player.getPlayer())).start();
        int tickPeriod = config.getInt("Temperature.CalculateTickPeriod"); // get the tick period
        this.runTaskTimer(plugin, 0L, tickPeriod);
    }

    @Override
    public void stop() {
        manager.setTemperature(player.getPlayer(), temp);
        tasks.remove(id);
        cancel();
    }

    private void saveData() {
        HLPlayer.getPlayers().get(id).getTanDataModule().saveData();
    }

    public static boolean hasTask(UUID id) {
        return tasks.get(id) != null;
    }

    public static Map<UUID, TemperatureCalculateTask> getTasks() {
        return tasks;
    }

    public double getEquilibriumTemp() {
        return equilibriumTemp;
    }

    public double getTemp() {
        return temp;
    }

    public double getChangeEnv() {
        return changeEnv;
    }

    public double getRegulateEnv() {
        return regulateEnv;
    }

    public void setTemp(double temp) {
        this.temp = temp;
    }

    public void setChangeEnv(double changeEnv) {
        this.changeEnv = changeEnv;
    }

    public void setRegulateEnv(double regulateEnv) {
        this.regulateEnv = regulateEnv;
    }
}

