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
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages Nightmare Enderman entities spawned when a player reaches maximum fear (100).
 * Handles spawn, despawn, scaling behaviour and crash-recovery cleanup.
 */
public class NightmareManager {

    private final HLPlugin plugin;
    private final FileConfiguration config;
    private final NamespacedKey nightmareKey;
    private final PotionEffectType darknessPotionEffect = Registry.EFFECT.get(NamespacedKey.minecraft("darkness"));

    // player UUID → active nightmare data
    private final Map<UUID, NightmareEntry> activeNightmares = new HashMap<>();

    private static class NightmareEntry {
        final UUID endermanUUID;
        long ticksAlive = 0;

        NightmareEntry(UUID endermanUUID) {
            this.endermanUUID = endermanUUID;
        }
    }

    public NightmareManager(HLPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.nightmareKey = new NamespacedKey(plugin, "nightmare_enderman");
    }

    /**
     * Called once during module initialization.
     * Scans all loaded worlds and removes any Nightmare Endermen left over from a previous crash.
     */
    public void initialize() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity instanceof Enderman && isNightmare(entity)) {
                    entity.remove();
                    plugin.getLogger().info("[Fear] Removed orphan Nightmare Enderman in world '" + world.getName() + "'.");
                }
            }
        }
    }

    public boolean hasActiveNightmare(UUID playerUUID) {
        return activeNightmares.containsKey(playerUUID);
    }

    /** Spawns a Nightmare Enderman near the given player. No-op if one already exists for them. */
    public void spawnNightmare(Player player) {
        if (!config.getBoolean("Nightmare.Enabled", true)) return;
        if (hasActiveNightmare(player.getUniqueId())) return;

        double spawnRadius = config.getDouble("Nightmare.SpawnRadius", 10.0);
        Location spawnLoc = findRadialLocation(player.getLocation(), spawnRadius);
        if (spawnLoc == null) {
            // Fallback: spawn directly beside player if no safe spot found
            spawnLoc = player.getLocation().clone().add(spawnRadius, 0, 0);
        }

        Enderman enderman = (Enderman) player.getWorld().spawnEntity(spawnLoc, EntityType.ENDERMAN);

        // Persist ownership so we can recover it after a crash
        enderman.getPersistentDataContainer().set(nightmareKey, PersistentDataType.STRING, player.getUniqueId().toString());

        // Name and appearance
        enderman.setCustomName(LegacyComponentSerializer.legacySection().serialize(
                Component.text("Nightmare", NamedTextColor.DARK_RED, TextDecoration.BOLD)));
        enderman.setCustomNameVisible(true);

        // Carved pumpkin head — doesn't drop on death
        EntityEquipment equipment = enderman.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            equipment.setHelmetDropChance(0.0f);
        }

        // 200 HP (100 hearts)
        AttributeInstance maxHealth = enderman.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(200.0);
            enderman.setHealth(200.0);
        }

        // Permanent Resistance I
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0, false, false));

        // Never despawn naturally
        enderman.setRemoveWhenFarAway(false);

        // Immediately target the player
        enderman.setTarget(player);

        activeNightmares.put(player.getUniqueId(), new NightmareEntry(enderman.getUniqueId()));
        plugin.getLogger().info("[Fear] Nightmare Enderman spawned for player " + player.getName() + ".");
    }

    /** Removes the active Nightmare Enderman for the given player (if any). */
    public void despawnNightmare(UUID playerUUID) {
        NightmareEntry entry = activeNightmares.remove(playerUUID);
        if (entry == null) return;
        Entity entity = Bukkit.getEntity(entry.endermanUUID);
        if (entity != null && !entity.isDead()) {
            entity.remove();
        }
        Player player = Bukkit.getPlayer(playerUUID);
        if (player != null) {
            if (darknessPotionEffect != null) player.removePotionEffect(darknessPotionEffect);
        }
    }

    public void despawnNightmare(Player player) {
        despawnNightmare(player.getUniqueId());
    }

    /**
     * Called from the 100-tick fear check loop.
     * Spawns a Nightmare if fear reached 100 and none is active,
     * or despawns an existing one if fear dropped to the threshold.
     */
    public void checkSpawnOrDespawn(Player player, double fearLevel) {
        if (!config.getBoolean("Nightmare.Enabled", true)) return;
        double spawnThreshold   = config.getDouble("Nightmare.SpawnFearThreshold",   100.0);
        double despawnThreshold = config.getDouble("Nightmare.DespawnFearThreshold",  80.0);

        if (fearLevel >= spawnThreshold && !hasActiveNightmare(player.getUniqueId())) {
            spawnNightmare(player);
        } else if (fearLevel <= despawnThreshold && hasActiveNightmare(player.getUniqueId())) {
            despawnNightmare(player.getUniqueId());
        }
    }

    /**
     * Called every 20 ticks from the nightmare tick task.
     * Retargets, teleports if needed, and scales each active Nightmare.
     */
    public void tickAll() {
        for (UUID playerUUID : new ArrayList<>(activeNightmares.keySet())) {
            NightmareEntry entry = activeNightmares.get(playerUUID);
            if (entry == null) continue;

            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline() || player.isDead()) {
                despawnNightmare(playerUUID);
                continue;
            }

            Entity entity = Bukkit.getEntity(entry.endermanUUID);
            if (entity == null || entity.isDead()) {
                // Killed by player or something else — just remove from tracking
                activeNightmares.remove(playerUUID);
                continue;
            }

            if (!(entity instanceof Enderman enderman)) {
                activeNightmares.remove(playerUUID);
                entity.remove();
                continue;
            }

            entry.ticksAlive += 20;

            // Keep targeting the player every tick in case AI resets
            enderman.setTarget(player);

            // Teleport to player if they've moved too far away
            double maxDist = config.getDouble("Nightmare.MaxFollowDistance", 30.0);
            double dist = enderman.getLocation().distance(player.getLocation());
            if (dist > maxDist) {
                double teleportRadius = config.getDouble("Nightmare.TeleportRadius", 4.0);
                Location teleportLoc = findRadialLocation(player.getLocation(), teleportRadius);
                if (teleportLoc != null) {
                    enderman.teleport(teleportLoc);
                }
            }

            // Darkness effect on the hunted player
            if (darknessPotionEffect != null) {
                player.removePotionEffect(darknessPotionEffect);
                player.addPotionEffect(new PotionEffect(darknessPotionEffect, 100, 0, false, false));
            }

            // Increase speed/strength over time
            applyScaling(enderman, entry.ticksAlive);
        }
    }

    /** Removes all active Nightmare Endermen. Called on plugin shutdown. */
    public void removeAllNightmares() {
        for (NightmareEntry entry : new ArrayList<>(activeNightmares.values())) {
            Entity entity = Bukkit.getEntity(entry.endermanUUID);
            if (entity != null && !entity.isDead()) {
                entity.remove();
            }
        }
        activeNightmares.clear();
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private boolean isNightmare(Entity entity) {
        return entity.getPersistentDataContainer().has(nightmareKey, PersistentDataType.STRING);
    }

    private void applyScaling(Enderman enderman, long ticksAlive) {
        long seconds = ticksAlive / 20;
        long stage2After = config.getLong("Nightmare.Scaling.Stage2.AfterSeconds", 60);
        long stage3After = config.getLong("Nightmare.Scaling.Stage3.AfterSeconds", 120);

        int speedAmp;
        int strengthAmp;

        if (seconds >= stage3After) {
            speedAmp    = config.getInt("Nightmare.Scaling.Stage3.SpeedAmplifier",    2);
            strengthAmp = config.getInt("Nightmare.Scaling.Stage3.StrengthAmplifier", 1);
        } else if (seconds >= stage2After) {
            speedAmp    = config.getInt("Nightmare.Scaling.Stage2.SpeedAmplifier",    1);
            strengthAmp = config.getInt("Nightmare.Scaling.Stage2.StrengthAmplifier", 0);
        } else {
            speedAmp    = config.getInt("Nightmare.Scaling.Stage1.SpeedAmplifier",    0);
            strengthAmp = -1; // no strength yet
        }

        // Duration of 100 ticks (5 s), refreshed every 20 ticks so it never expires mid-chase
        enderman.removePotionEffect(PotionEffectType.RESISTANCE);
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, false, false));

        enderman.removePotionEffect(PotionEffectType.SPEED);
        enderman.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, speedAmp, false, true));

        enderman.removePotionEffect(PotionEffectType.STRENGTH);
        if (strengthAmp >= 0) {
            enderman.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, strengthAmp, false, true));
        }
    }

    /**
     * Finds a safe 3-block-tall location on the circumference of a circle
     * centred on {@code center} with the given {@code radius}.
     */
    private Location findRadialLocation(Location center, double radius) {
        World world = center.getWorld();
        if (world == null) return null;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 15; attempt++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double x = center.getX() + Math.cos(angle) * radius;
            double z = center.getZ() + Math.sin(angle) * radius;
            Location candidate = findSafeY(world, x, z, center.getBlockY());
            if (candidate != null) return candidate;
        }
        return null;
    }

    /**
     * Searches up and down from {@code startY} for a column with solid floor
     * and at least 3 passable blocks above (Enderman height).
     */
    private Location findSafeY(World world, double x, double z, int startY) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int minY = world.getMinHeight() + 1;
        int maxY = world.getMaxHeight() - 3;
        for (int dy = 0; dy <= 8; dy++) {
            for (int sign : new int[]{0, 1}) {
                int y = startY + (sign == 0 ? dy : -dy);
                if (y < minY || y > maxY) continue;
                if (world.getBlockAt(bx, y - 1, bz).isPassable())  continue; // needs solid floor
                if (!world.getBlockAt(bx, y,     bz).isPassable()) continue;
                if (!world.getBlockAt(bx, y + 1, bz).isPassable()) continue;
                if (!world.getBlockAt(bx, y + 2, bz).isPassable()) continue; // enderman is ~3 blocks tall
                return new Location(world, bx + 0.5, y, bz + 0.5);
            }
        }
        return null;
    }
}