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
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.HLTask;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class TemperatureEnvironmentTask extends BukkitRunnable implements HLTask {

    record BlockTempEntry(double value, boolean isRegulatory) {}

    private final TemperatureCalculateTask calcTask;
    private final FileConfiguration config;
    private final HLPlugin plugin;
    private final HLPlayer player;
    private final Collection<String> allowedWorlds;
    private final ConfigurationSection section;
    private final Map<Material, BlockTempEntry> blockTempMap;
    private final Map<Long, ChunkSnapshot> snapshots;
    private final int cubeLength;
    private final int minY;
    private final int maxY;
    private double regulate = 0D;
    private double change = 0D;

    public TemperatureEnvironmentTask(TanModule module, HLPlugin plugin, HLPlayer player,
                                       Map<Long, ChunkSnapshot> snapshots) {
        this.plugin = plugin;
        this.config = module.getUserConfig().getConfig();
        this.player = player;
        this.allowedWorlds = module.getAllowedWorlds();
        this.calcTask = TemperatureCalculateTask.getTasks().get(player.getPlayer().getUniqueId());
        this.section = config.getConfigurationSection("Temperature.Environment.Blocks");
        this.snapshots = snapshots;
        this.cubeLength = config.getInt("Temperature.Environment.CubeLength");
        this.blockTempMap = buildBlockTempMap(section);
        World world = player.getPlayer().getWorld();
        this.minY = world.getMinHeight();
        this.maxY = world.getMaxHeight() - 1;
    }

    static Map<Material, BlockTempEntry> buildBlockTempMap(ConfigurationSection section) {
        if (section == null) return Map.of();
        Map<Material, BlockTempEntry> map = new EnumMap<>(Material.class);
        for (String key : section.getKeys(false)) {
            Material mat = Material.matchMaterial(key);
            if (mat == null) continue;
            double value = section.getDouble(key + ".Value", 0.0);
            boolean isReg = section.getBoolean(key + ".IsRegulatory", false);
            map.put(mat, new BlockTempEntry(value, isReg));
        }
        return map;
    }

    private Material getBlockTypeFromSnapshots(int x, int y, int z) {
        if (y < minY || y > maxY) return Material.AIR;
        int cx = x >> 4;
        int cz = z >> 4;
        long key = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
        ChunkSnapshot snap = snapshots.get(key);
        if (snap == null) return Material.AIR;
        return snap.getBlockType(x & 0xF, y, z & 0xF);
    }

    public static Map<Long, ChunkSnapshot> captureSnapshots(Location center, int cubeLength) {
        Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
        World world = center.getWorld();
        if (world == null) return snapshots;
        int minCX = (center.getBlockX() - cubeLength) >> 4;
        int maxCX = (center.getBlockX() + cubeLength) >> 4;
        int minCZ = (center.getBlockZ() - cubeLength) >> 4;
        int maxCZ = (center.getBlockZ() + cubeLength) >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    long k = ((long) cx << 32) | (cz & 0xFFFFFFFFL);
                    snapshots.put(k, world.getChunkAt(cx, cz).getChunkSnapshot());
                }
            }
        }
        return snapshots;
    }

    @Override
    public void run() {
        Player player = this.player.getPlayer();

        if (conditionsMet(player)) {
            regulate = 0D;
            change = 0D;
            Location pLoc = player.getLocation();
            int px = pLoc.getBlockX();
            int py = pLoc.getBlockY();
            int pz = pLoc.getBlockZ();

            for (int x = -(cubeLength - 1); x < cubeLength; x++) {
                for (int y = -(cubeLength - 1); y < cubeLength; y++) {
                    for (int z = -(cubeLength - 1); z < cubeLength; z++) {
                        Material mat = getBlockTypeFromSnapshots(px + x, py + y, pz + z);
                        if (mat.isAir()) continue;
                        BlockTempEntry entry = blockTempMap.get(mat);
                        if (entry == null) continue;
                        if (entry.isRegulatory()) regulate += entry.value();
                        else change += entry.value();
                    }
                }
            }
            calcTask.setChangeEnv(change);
            calcTask.setRegulateEnv(regulate);
        }
        else {
            stop();
        }
    }

    public static boolean willAffectTemperature(@Nullable BlockData data, @Nonnull ConfigurationSection section) {
        if (data == null) {
            return false;
        }

        String material = data.getMaterial().toString();

        if (!section.contains(material)) {
            return false;
        }

        if (data instanceof Lightable lightable) {
            if (section.contains(material + ".Lit")) {
                return section.getBoolean(material + ".Lit") == lightable.isLit();
            }
        }
        else if (data instanceof Levelled levelled) {
            if (section.contains(material + ".MaximumLevel")) {
                return levelled.getLevel() <= section.getInt(material + ".MaximumLevel");
            }
            else {
                if (section.contains(material + ".MinimumLevel")) {
                    return levelled.getLevel() >= section.getInt(material + ".MinimumLevel");
                }
            }
        }
        return true;
    }

    public static boolean willAffectTemperature(@Nonnull Block block, @Nonnull ConfigurationSection section) {
        return willAffectTemperature(block.getBlockData(), section);
    }

    public static boolean isRegulatory(@Nullable BlockData blockData, @Nonnull ConfigurationSection section) {
        if (blockData == null) {
            return false;
        }
        return section.getBoolean(blockData.getMaterial() + ".IsRegulatory");
    }

    public static boolean isRegulatory(@Nonnull Block block, @Nonnull ConfigurationSection section) {
        return isRegulatory(block.getBlockData(), section);
    }

    public static double getValue(@Nullable BlockData blockData, @Nonnull ConfigurationSection section) {
        if (blockData == null) {
            return 0D;
        }

        if (!section.contains(blockData.getMaterial() + ".Value")) {
            return 0D;
        }

        double val = section.getDouble(blockData.getMaterial() + ".Value");
        String type = blockData.getMaterial().toString();

        if (blockData instanceof Lightable lightable) {
            if (section.contains(type + ".Lit") && section.getBoolean(type + ".Lit")) {
                return lightable.isLit() ? val : 0D;
            }
        }
        if (blockData instanceof Levelled levelled) {
            if (section.contains(type + ".MinimumLevel")) {
                int minLevel = section.getInt(type + ".MinimumLevel");
                return levelled.getLevel() >= minLevel ? val : 0D;
            }
            else if (section.contains(type + ".MaximumLevel")) {
                int maxLevel = section.getInt(type + ".MaximumLevel");
                return levelled.getLevel() <= maxLevel ? val : 0D;
            }
        }
        return val;
    }

    public static double getValue(@Nonnull Block block, @Nonnull ConfigurationSection section) {
        return getValue(block.getBlockData(), section);
    }

    public boolean willAffectTemperature(@Nonnull Block block) {
        return willAffectTemperature(block.getBlockData(), section);
    }

    public void add(@Nonnull BlockData data) {
        String type = data.getMaterial().toString();

        double val = getValue(data, section);

        if (section.getBoolean(type + ".IsRegulatory")) {
            regulate += val;
        }
        else {
            change += val;
        }
    }

    public void add(@Nonnull Block data) {
        String type = data.getType().toString();

        double val = getValue(data, section);

        if (section.getBoolean(type + ".IsRegulatory")) {
            regulate += val;
        }
        else {
            change += val;
        }
    }

    @Override
    public boolean conditionsMet(@Nullable Player player) {
        return globalConditionsMet(player) && calcTask != null && allowedWorlds.contains(player.getWorld().getName());
    }

    @Override
    public void start() {
        this.runTaskAsynchronously(plugin);
    }

    @Override
    public void stop() {
        cancel();
    }
}
