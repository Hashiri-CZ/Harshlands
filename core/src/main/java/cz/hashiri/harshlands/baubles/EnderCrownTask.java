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
package cz.hashiri.harshlands.baubles;

import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.utils.HLTask;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

public class EnderCrownTask extends BukkitRunnable implements HLTask {

    private static final Map<UUID, EnderCrownTask> tasks = new ConcurrentHashMap<>();
    /** Per-wearer set of live ally entity UUIDs, for concurrent-ally cap tracking. */
    private static final Map<UUID, Set<UUID>> liveAllies = new ConcurrentHashMap<>();

    private final HLPlayer hlPlayer;
    private final UUID id;
    private final HLPlugin plugin;
    private final boolean transfromEndermen;
    private final double maxDist;
    private final boolean alliesEnabled;
    private final boolean mustTakeDamage;
    private final double maxHealthPercent;
    private final double allySpawnChance;
    private final int minAllies;
    private final int maxAllies;
    private final int maxConcurrentAllies;
    private final int allyDelay;
    private final Collection<String> allowedWorlds;
    private final boolean shouldTakeWaterDamage;
    private final double waterDamage;
    private final int waterDamageDelay;
    private final double waterDamageChance;
    private final Predicate<Entity> filter = entity -> entity instanceof Enderman && !EndermanAllyUtils.isEndermanAlly(entity);
    private final int tickPeriod;
    private int waterDamageTicks;
    private int allyTicks;

    public EnderCrownTask(BaubleModule module, HLPlayer hlPlayer, HLPlugin plugin) {
        this.hlPlayer = hlPlayer;
        this.id = hlPlayer.getPlayer().getUniqueId();
        FileConfiguration config = module.getUserConfig().getConfig();
        this.allowedWorlds = module.getAllowedWorlds();
        this.plugin = plugin;
        this.transfromEndermen = config.getBoolean("Items.ender_queens_crown.TransformRegularEndermen.Enabled");
        this.maxDist = config.getDouble("Items.ender_queens_crown.TransformRegularEndermen.MaxDistance");
        this.mustTakeDamage = config.getBoolean("Items.ender_queens_crown.SummonEndermenAllies.MustTakeDamage");
        this.alliesEnabled = config.getBoolean("Items.ender_queens_crown.SummonEndermenAllies.Enabled");
        this.minAllies = config.getInt("Items.ender_queens_crown.SummonEndermenAllies.MinAmount");
        this.maxAllies = config.getInt("Items.ender_queens_crown.SummonEndermenAllies.MaxAmount");
        this.maxConcurrentAllies = config.contains("Items.ender_queens_crown.SummonEndermenAllies.MaxConcurrentAllies")
                ? config.getInt("Items.ender_queens_crown.SummonEndermenAllies.MaxConcurrentAllies")
                : 5;
        this.allySpawnChance = config.getDouble("Items.ender_queens_crown.SummonEndermenAllies.Chance");
        this.allyDelay = config.getInt("Items.ender_queens_crown.SummonEndermenAllies.Delay");
        this.maxHealthPercent = config.getDouble("Items.ender_queens_crown.SummonEndermenAllies.MaxHealthPercent");
        this.shouldTakeWaterDamage = config.getBoolean("Items.ender_queens_crown.WaterContact.Enabled");
        this.waterDamage = config.getDouble("Items.ender_queens_crown.WaterContact.Damage");
        this.waterDamageChance = config.getDouble("Items.ender_queens_crown.WaterContact.Chance");
        this.waterDamageDelay = config.getInt("Items.ender_queens_crown.WaterContact.Delay");
        this.tickPeriod = config.getInt("Items.ender_queens_crown.TickPeriod");
        tasks.put(id, this);
    }

    @Override
    public void run() {
        Player player = this.hlPlayer.getPlayer();

        if (conditionsMet(player)) {
            if (transfromEndermen) {
                Collection<Entity> entities = player.getWorld().getNearbyEntities(player.getLocation(), maxDist, maxDist, maxDist, filter);
                for (Entity e : entities) {
                    EndermanAlly ally = Utils.spawnEndermanAlly(player, e.getLocation());
                    ally.addEntityToWorld(player.getWorld());
                    ally.getEntity().teleport(e.getLocation());
                    ((LivingEntity) ally.getEntity()).setHealth(((LivingEntity) e).getHealth());
                    e.remove();
                }
            }

            if (shouldTakeWaterDamage) {
                waterDamageTicks += tickPeriod;

                if (waterDamageTicks > waterDamageDelay) {
                    if (Utils.roll(waterDamageChance)) {
                        if (player.isInWater() || (player.getWorld().hasStorm() && player.getLocation().getY() >= player.getWorld().getHighestBlockYAt(player.getLocation()))) {
                            player.damage(waterDamage);
                            waterDamageTicks = 0;
                        }
                    }
                }
            }

            if (alliesEnabled) {
                allyTicks += tickPeriod;

                if (canSpawnAllies(false)) {
                    spawnAllies();
                }
            }
        }
        else {
            stop();
        }
    }

    @Override
    public boolean conditionsMet(@Nullable Player player) {
        return globalConditionsMet(player) && allowedWorlds.contains(player.getWorld().getName()) && hlPlayer.getBaubleDataModule().hasBauble("ender_queens_crown");
    }

    @Override
    public void start() {
        this.runTaskTimer(plugin, 0L, tickPeriod);
    }

    @Override
    public void stop() {
        tasks.remove(id);
        cancel();
    }

    public static Map<UUID, EnderCrownTask> getTasks() {
        return tasks;
    }

    public static boolean hasTask(UUID id) {
        return tasks.get(id) != null;
    }

    public boolean areAlliesEnabled() {
        return alliesEnabled;
    }

    public boolean canSpawnAllies(boolean playerDamaged) {
        if (allyTicks > allyDelay) {
            if (Utils.roll(allySpawnChance)) {
                Player player = this.hlPlayer.getPlayer();
                if (player.getHealth() / player.getAttribute(Attribute.MAX_HEALTH).getValue() <= maxHealthPercent) {
                    if (mustTakeDamage) {
                        return playerDamaged;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public void spawnAllies() {
        Player player = this.hlPlayer.getPlayer();
        Location loc = player.getLocation();
        World world = player.getWorld();

        // Prune any allies that are no longer valid (died or despawned but cleanup missed them)
        Set<UUID> myAllies = liveAllies.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        myAllies.removeIf(allyId -> {
            Entity e = player.getServer().getEntity(allyId);
            return e == null || !e.isValid();
        });

        int numAllies = Utils.getRandomNum(minAllies, maxAllies);

        for (int i = 0; i < numAllies; i++) {
            if (maxConcurrentAllies >= 0 && myAllies.size() >= maxConcurrentAllies) {
                break; // cap reached — skip remaining spawns this tick
            }
            EndermanAlly ally = Utils.spawnEndermanAlly(player, loc);
            ally.addEntityToWorld(world);
            Utils.randomTpSafely(ally.getEntity(), maxDist);
            myAllies.add(ally.getEntity().getUniqueId());
            allyTicks = 0;
        }
    }

    /**
     * Removes a dead/removed ally from the live-ally tracking set for its owner.
     * Called from entity death/remove event listeners in BaubleEvents.
     */
    public static void removeAlly(UUID ownerId, UUID allyId) {
        Set<UUID> set = liveAllies.get(ownerId);
        if (set != null) {
            set.remove(allyId);
        }
    }

    public static Map<UUID, Set<UUID>> getLiveAllies() {
        return liveAllies;
    }
}

