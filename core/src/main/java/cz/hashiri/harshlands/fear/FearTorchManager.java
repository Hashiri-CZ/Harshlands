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

import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;

public class FearTorchManager {

    @FunctionalInterface
    public interface UnlitTorchSpawner {
        void spawnFromLitTorch(@Nonnull Block litTorchBlock);
    }

    private final HLPlugin plugin;
    private final long burnDurationMillis;
    private final long rainCheckIntervalTicks = 100L;
    private final UnlitTorchSpawner unlitTorchSpawner;
    private final Set<LocationKey> managedTorches = new HashSet<>();
    private final Map<LocationKey, Long> litTorches = new HashMap<>();
    private final PriorityQueue<ExpiryEntry> expiringTorches = new PriorityQueue<>(Comparator.comparingLong(ExpiryEntry::expiresAt));
    private BukkitTask task;
    private long ticksSinceRainCheck = 0L;

    public FearTorchManager(@Nonnull HLPlugin plugin, long burnDurationMinutes, @Nonnull UnlitTorchSpawner unlitTorchSpawner) {
        this.plugin = plugin;
        this.burnDurationMillis = Math.max(1L, burnDurationMinutes) * 60_000L;
        this.unlitTorchSpawner = unlitTorchSpawner;
    }

    public void start() {
        if (task != null) {
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        managedTorches.clear();
        litTorches.clear();
        expiringTorches.clear();
    }

    public void registerPlacedLitTorch(@Nonnull Block block) {
        managedTorches.add(LocationKey.of(block.getLocation()));
        if (isLitTorch(block.getType())) {
            LocationKey key = LocationKey.of(block.getLocation());
            long expiry = System.currentTimeMillis() + burnDurationMillis;
            litTorches.put(key, expiry);
            expiringTorches.add(new ExpiryEntry(key, expiry));
        }
    }

    public void unregisterTorch(@Nonnull Block block) {
        LocationKey key = LocationKey.of(block.getLocation());
        managedTorches.remove(key);
        litTorches.remove(key);
    }

    public boolean isManagedTorch(@Nonnull Block block) {
        return managedTorches.contains(LocationKey.of(block.getLocation()));
    }

    private void tick() {
        long now = System.currentTimeMillis();

        ticksSinceRainCheck += 20L;
        if (ticksSinceRainCheck >= rainCheckIntervalTicks) {
            ticksSinceRainCheck = 0L;
            extinguishRainExposedTorches();
        }

        while (!expiringTorches.isEmpty() && expiringTorches.peek().expiresAt() <= now) {
            ExpiryEntry entry = expiringTorches.poll();
            Long current = litTorches.get(entry.key());

            if (current == null || current.longValue() != entry.expiresAt()) {
                continue; // stale queue entry
            }

            Block block = getBlock(entry.key());
            if (block == null) {
                litTorches.remove(entry.key());
                managedTorches.remove(entry.key());
                continue;
            }

            World world = block.getWorld();
            int chunkX = block.getX() >> 4;
            int chunkZ = block.getZ() >> 4;

            // Avoid forced chunk loads; retry later.
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                long retryAt = now + 10_000L;
                litTorches.put(entry.key(), retryAt);
                expiringTorches.add(new ExpiryEntry(entry.key(), retryAt));
                continue;
            }

            Material type = block.getType();
            if (isLitTorch(type)) {
                setUnlit(block);
                managedTorches.remove(entry.key());
            } else {
                managedTorches.remove(entry.key());
            }

            litTorches.remove(entry.key());
        }
    }

    private void extinguishRainExposedTorches() {
        if (litTorches.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<LocationKey, Long>> iterator = litTorches.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LocationKey, Long> entry = iterator.next();
            LocationKey key = entry.getKey();

            Block block = getBlock(key);
            if (block == null) {
                iterator.remove();
                managedTorches.remove(key);
                continue;
            }

            World world = block.getWorld();
            int chunkX = block.getX() >> 4;
            int chunkZ = block.getZ() >> 4;

            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            Material type = block.getType();
            if (!isLitTorch(type)) {
                managedTorches.remove(key);
                iterator.remove();
                continue;
            }

            if (isRainingOnTorch(block)) {
                setUnlit(block);
                managedTorches.remove(key);
                iterator.remove();
            }
        }
    }

    private boolean isRainingOnTorch(@Nonnull Block block) {
        World world = block.getWorld();
        if (!world.hasStorm()) {
            return false;
        }

        int highestY = world.getHighestBlockYAt(block.getX(), block.getZ());
        return highestY <= block.getY();
    }

    @Nullable
    private Block getBlock(@Nonnull LocationKey key) {
        World world = Bukkit.getWorld(key.worldId());
        if (world == null) {
            return null;
        }
        return world.getBlockAt(key.x(), key.y(), key.z());
    }

    private void setUnlit(@Nonnull Block block) {
        if (isLitTorch(block.getType())) {
            unlitTorchSpawner.spawnFromLitTorch(block);
        }
    }

    @Nonnull
    public Map<String, Long> snapshotRemainingLitDurations() {
        Map<String, Long> snapshot = new HashMap<>();
        long now = System.currentTimeMillis();

        for (Map.Entry<LocationKey, Long> entry : litTorches.entrySet()) {
            long remaining = entry.getValue() - now;
            if (remaining > 0L) {
                snapshot.put(serializeKey(entry.getKey()), remaining);
            }
        }

        return snapshot;
    }

    public void restoreLitTorches(@Nonnull Map<String, Long> persistedRemainingDurations) {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : persistedRemainingDurations.entrySet()) {
            LocationKey key = parseKey(entry.getKey());
            if (key == null) {
                continue;
            }

            long remaining = Math.max(0L, entry.getValue());
            if (remaining <= 0L) {
                continue;
            }

            Block block = getBlock(key);
            if (block == null) {
                continue;
            }

            Material type = block.getType();
            if (isLitTorch(type)) {
                managedTorches.add(key);
            }

            if (isLitTorch(type)) {
                long expiry = now + remaining;
                litTorches.put(key, expiry);
                expiringTorches.add(new ExpiryEntry(key, expiry));
            }
        }
    }

    private boolean isLitTorch(@Nonnull Material material) {
        return material == Material.TORCH || material == Material.WALL_TORCH;
    }

    private record ExpiryEntry(LocationKey key, long expiresAt) { }

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

