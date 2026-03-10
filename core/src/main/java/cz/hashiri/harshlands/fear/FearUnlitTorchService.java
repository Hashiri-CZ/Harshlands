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

import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Lightable;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class FearUnlitTorchService {

    private final HLPlugin plugin;
    private final Set<LocationKey> managedUnlitTorches = new HashSet<>();
    private final List<LocationKey> scanKeys = new ArrayList<>();
    private final int targetFullScanTicks;
    private final int minScanBatchSize;
    private int scanCursor = 0;
    private boolean scanDirty = true;
    private BukkitTask enforceTask;

    public FearUnlitTorchService(@Nonnull HLPlugin plugin, @Nonnull HLConfig userConfig) {
        this.plugin = plugin;
        this.targetFullScanTicks = Math.max(1, userConfig.getConfig().getInt(
                "TorchSystem.UnlitTorchEnforcement.TargetFullScanTicks", 20));
        this.minScanBatchSize = Math.max(1, userConfig.getConfig().getInt(
                "TorchSystem.UnlitTorchEnforcement.MinScanBatchSize", 32));
    }

    public void start() {
        if (enforceTask != null) {
            return;
        }

        enforceTask = Bukkit.getScheduler().runTaskTimer(plugin, this::enforceUnlitTorches, 1L, 1L);
    }

    public void stop() {
        if (enforceTask != null) {
            enforceTask.cancel();
            enforceTask = null;
        }

        managedUnlitTorches.clear();
        scanKeys.clear();
        scanCursor = 0;
        scanDirty = true;
    }

    public void convertPlacedUnlitTorch(@Nonnull Block placedTorchBlock) {
        if (setUnlitFromLitTorch(placedTorchBlock)) {
            enforceBlockUnlit(placedTorchBlock);
        }
    }

    public boolean setUnlitFromLitTorch(@Nonnull Block torchBlock) {
        Material type = torchBlock.getType();
        if (type == Material.TORCH) {
            torchBlock.setType(Material.REDSTONE_TORCH, false);
            addManagedUnlit(LocationKey.of(torchBlock.getLocation()));
            return true;
        }

        if (type != Material.WALL_TORCH) {
            return false;
        }

        BlockFace facing = torchBlock.getBlockData() instanceof Directional directional ? directional.getFacing() : null;
        torchBlock.setType(Material.REDSTONE_WALL_TORCH, false);
        if (facing != null && torchBlock.getBlockData() instanceof Directional directional) {
            directional.setFacing(facing);
            torchBlock.setBlockData(directional, false);
        }

        addManagedUnlit(LocationKey.of(torchBlock.getLocation()));
        return true;
    }

    public boolean isManagedUnlitTorch(@Nullable Block block) {
        if (block == null) {
            return false;
        }

        LocationKey key = LocationKey.of(block.getLocation());
        if (!managedUnlitTorches.contains(key)) {
            return false;
        }

        Material type = block.getType();
        return type == Material.REDSTONE_TORCH || type == Material.REDSTONE_WALL_TORCH;
    }

    public void unregisterUnlitTorch(@Nonnull Block block) {
        removeManagedUnlit(LocationKey.of(block.getLocation()));
    }

    @Nonnull
    public Set<String> snapshotManagedUnlitTorches() {
        Set<String> snapshot = new LinkedHashSet<>();

        Iterator<LocationKey> iterator = managedUnlitTorches.iterator();
        while (iterator.hasNext()) {
            LocationKey key = iterator.next();
            World world = Bukkit.getWorld(key.worldId());
            if (world == null) {
                iterator.remove();
                continue;
            }

            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                snapshot.add(serializeKey(key));
                continue;
            }

            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (!isRedstoneTorch(block.getType())) {
                iterator.remove();
                continue;
            }

            snapshot.add(serializeKey(key));
        }

        return snapshot;
    }

    public void restoreManagedUnlitTorches(@Nonnull Set<String> serializedKeys) {
        for (String serialized : serializedKeys) {
            LocationKey key = parseKey(serialized);
            if (key == null) {
                continue;
            }
            addManagedUnlit(key);
        }
    }

    public boolean igniteUnlitTorchBlock(@Nonnull Block block, @Nonnull FearTorchManager torchManager) {
        if (!isManagedUnlitTorch(block)) {
            return false;
        }

        Material type = block.getType();
        if (type == Material.REDSTONE_TORCH) {
            block.setType(Material.TORCH, false);
        } else if (type == Material.REDSTONE_WALL_TORCH) {
            BlockFace facing = block.getBlockData() instanceof Directional directional ? directional.getFacing() : null;
            block.setType(Material.WALL_TORCH, false);
            if (facing != null && block.getBlockData() instanceof Directional directional) {
                directional.setFacing(facing);
                block.setBlockData(directional, false);
            }
        } else {
            return false;
        }

        unregisterUnlitTorch(block);
        torchManager.registerPlacedLitTorch(block);
        return true;
    }

    public boolean suppressManagedUnlitUpdate(@Nonnull Block block) {
        if (!isManagedUnlitTorch(block)) {
            return false;
        }

        enforceBlockUnlit(block);
        return true;
    }

    private void enforceUnlitTorches() {
        if (managedUnlitTorches.isEmpty()) {
            return;
        }

        rebuildScanKeysIfNeeded();
        if (scanKeys.isEmpty()) {
            return;
        }

        int batchSize = Math.max(minScanBatchSize, (scanKeys.size() + targetFullScanTicks - 1) / targetFullScanTicks);
        UUID lastWorldId = null;
        World lastWorld = null;

        for (int i = 0; i < batchSize && !scanKeys.isEmpty(); i++) {
            if (scanCursor >= scanKeys.size()) {
                scanCursor = 0;
            }

            LocationKey key = scanKeys.get(scanCursor++);
            if (!managedUnlitTorches.contains(key)) {
                scanDirty = true;
                continue;
            }

            UUID wid = key.worldId();
            if (!wid.equals(lastWorldId)) {
                lastWorldId = wid;
                lastWorld = Bukkit.getWorld(wid);
            }
            World world = lastWorld;
            if (world == null) {
                removeManagedUnlit(key);
                continue;
            }

            if (!world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) {
                continue;
            }

            Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (!isRedstoneTorch(block.getType())) {
                removeManagedUnlit(key);
                continue;
            }

            enforceBlockUnlit(block);
        }
    }

    private boolean isRedstoneTorch(@Nonnull Material material) {
        return material == Material.REDSTONE_TORCH || material == Material.REDSTONE_WALL_TORCH;
    }

    private void enforceBlockUnlit(@Nonnull Block block) {
        if (!isRedstoneTorch(block.getType())) {
            return;
        }

        if (block.getBlockData() instanceof Lightable lightable && lightable.isLit()) {
            lightable.setLit(false);
            block.setBlockData(lightable, false);
        }
    }

    private void rebuildScanKeysIfNeeded() {
        if (!scanDirty) {
            return;
        }

        scanKeys.clear();
        scanKeys.addAll(managedUnlitTorches);
        if (scanCursor >= scanKeys.size()) {
            scanCursor = 0;
        }
        scanDirty = false;
    }

    private void addManagedUnlit(@Nonnull LocationKey key) {
        if (managedUnlitTorches.add(key)) {
            scanDirty = true;
        }
    }

    private void removeManagedUnlit(@Nonnull LocationKey key) {
        if (managedUnlitTorches.remove(key)) {
            scanDirty = true;
        }
    }

    private record LocationKey(UUID worldId, int x, int y, int z) {
        static LocationKey of(@Nonnull Location location) {
            return new LocationKey(location.getWorld().getUID(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }

    @Nonnull
    private String serializeKey(@Nonnull LocationKey key) {
        return key.worldId() + ";" + key.x() + ";" + key.y() + ";" + key.z();
    }

    @Nullable
    private LocationKey parseKey(@Nonnull String serialized) {
        String[] parts = serialized.split(";", 4);
        if (parts.length != 4) {
            return null;
        }

        try {
            UUID worldId = UUID.fromString(parts[0]);
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new LocationKey(worldId, x, y, z);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

