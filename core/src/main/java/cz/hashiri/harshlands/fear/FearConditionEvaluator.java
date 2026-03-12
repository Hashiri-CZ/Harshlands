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

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.fear.DataModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.tan.TanModule;
import cz.hashiri.harshlands.tan.TempManager;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.Lightable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;

import java.util.Collection;

public class FearConditionEvaluator {

    private final HLPlugin plugin;
    private final FileConfiguration config;

    public FearConditionEvaluator(HLPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void evaluate(Player player, DataModule dm) {
        double darkness     = config.getBoolean("FearMeter.Conditions.Darkness.Enabled", true)         ? evalDarkness(player)            : 0.0;
        double cave         = config.getBoolean("FearMeter.Conditions.Cave.Enabled", true)             ? evalCave(player)                : 0.0;
        double underground  = config.getBoolean("FearMeter.Conditions.AloneUnderground.Enabled", true) ? evalAloneUnderground(player)    : 0.0;
        double lowHealth    = config.getBoolean("FearMeter.Conditions.LowHealth.Enabled", true)        ? evalLowHealth(player, dm)       : 0.0;
        double enemies      = config.getBoolean("FearMeter.Conditions.NearbyEnemies.Enabled", true)    ? evalNearbyEnemies(player)       : 0.0;
        double cold         = config.getBoolean("FearMeter.Conditions.Cold.Enabled", true)             ? evalCold(player)                : 0.0;
        double storm        = config.getBoolean("FearMeter.Conditions.Storm.Enabled", true)            ? evalStorm(player)               : 0.0;
        double night        = config.getBoolean("FearMeter.Conditions.Night.Enabled", true)            ? evalNight(player)               : 0.0;

        double brightLight = config.getBoolean("FearMeter.Reductions.BrightLight.Enabled", true)   ? evalBrightLight(player)    : 0.0;
        double nearFire    = config.getBoolean("FearMeter.Reductions.NearFireSource.Enabled", true) ? evalNearFireSource(player) : 0.0;
        double companions  = config.getBoolean("FearMeter.Reductions.NearCompanions.Enabled", true) ? evalNearCompanions(player) : 0.0;

        double netDelta = darkness + cave + underground + lowHealth + enemies + cold + storm + night
                        - brightLight - nearFire - companions;

        if (config.getBoolean("FearMeter.PassiveDecay.Enabled", true)) {
            netDelta -= config.getDouble("FearMeter.PassiveDecay.Rate", 0.5);
        }

        if (netDelta > 0.0) {
            dm.increaseFear(netDelta);
        } else if (netDelta < 0.0) {
            dm.decreaseFear(-netDelta);
        }

        if (config.getBoolean("FearMeter.Debug", false)) {
            plugin.getLogger().info(String.format(
                "[Fear][DEBUG] %s | dark=%.2f cave=%.2f ugnd=%.2f lhp=%.2f mob=%.2f cold=%.2f storm=%.2f night=%.2f | -light=%.2f -fire=%.2f -comp=%.2f | net=%.2f | total=%.2f",
                player.getName(), darkness, cave, underground, lowHealth, enemies, cold, storm, night,
                brightLight, nearFire, companions, netDelta, dm.getFearLevel()
            ));
        }
    }

    private double evalDarkness(Player player) {
        int lightLevel = player.getLocation().getBlock().getLightLevel();
        if (lightLevel <= config.getInt("FearMeter.Conditions.Darkness.MaxLightLevel", 4)) {
            return config.getDouble("FearMeter.Conditions.Darkness.Rate", 1.0);
        }
        return 0.0;
    }

    private double evalCave(Player player) {
        int skyLight = player.getEyeLocation().getBlock().getLightFromSky();
        if (skyLight == 0) {
            return config.getDouble("FearMeter.Conditions.Cave.Rate", 0.4);
        }
        return 0.0;
    }

    private double evalAloneUnderground(Player player) {
        int skyLight = player.getEyeLocation().getBlock().getLightFromSky();
        if (skyLight != 0) return 0.0;
        double radius = config.getDouble("FearMeter.Conditions.AloneUnderground.SearchRadius", 20.0);
        Collection<Entity> nearby = player.getWorld().getNearbyEntities(
            player.getLocation(), radius, radius, radius,
            e -> e instanceof Player && !e.equals(player));
        if (nearby.isEmpty()) {
            return config.getDouble("FearMeter.Conditions.AloneUnderground.Rate", 0.5);
        }
        return 0.0;
    }

    private double evalLowHealth(Player player, DataModule dm) {
        double fraction = config.getDouble("FearMeter.Conditions.LowHealth.HealthFraction", 0.35);
        int requiredTicks = config.getInt("FearMeter.Conditions.LowHealth.RequiredTicks", 3);
        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttribute == null ? 20.0 : maxHealthAttribute.getValue();
        boolean isLow = maxHealth > 0.0 && (player.getHealth() / maxHealth) <= fraction;
        if (isLow) {
            int ticks = dm.incrementLowHealthTicks();
            if (ticks >= requiredTicks) {
                return config.getDouble("FearMeter.Conditions.LowHealth.Rate", 1.0);
            }
        } else {
            dm.resetLowHealthTicks();
        }
        return 0.0;
    }

    private double evalNearbyEnemies(Player player) {
        double radius = config.getDouble("FearMeter.Conditions.NearbyEnemies.SearchRadius", 16.0);
        Collection<Entity> mobs = player.getWorld().getNearbyEntities(
            player.getLocation(), radius, radius, radius,
            e -> e instanceof Monster);
        if (!mobs.isEmpty()) {
            double ratePerMob = config.getDouble("FearMeter.Conditions.NearbyEnemies.Rate", 0.5);
            double maxBonus = config.getDouble("FearMeter.Conditions.NearbyEnemies.MaxMobBonus", 3.0);
            return Math.min(mobs.size() * ratePerMob, maxBonus);
        }
        return 0.0;
    }

    private double evalCold(Player player) {
        HLModule tanMod = HLModule.getModule(TanModule.NAME);
        if (tanMod == null || !((TanModule) tanMod).isTempGloballyEnabled()) return 0.0;
        TempManager tempManager = ((TanModule) tanMod).getTempManager();
        double temp = tempManager.getTemperature(player);
        double threshold = config.getDouble("FearMeter.Conditions.Cold.TemperatureThreshold", 5.0);
        if (temp <= threshold) {
            return config.getDouble("FearMeter.Conditions.Cold.Rate", 0.8);
        }
        return 0.0;
    }

    private double evalStorm(Player player) {
        World world = player.getWorld();
        if (!world.hasStorm()) return 0.0;
        boolean requireOutdoors = config.getBoolean("FearMeter.Conditions.Storm.RequireOutdoors", true);
        if (requireOutdoors && !Utils.isExposedToSky(player)) return 0.0;
        return config.getDouble("FearMeter.Conditions.Storm.Rate", 0.3);
    }

    private double evalNight(Player player) {
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return 0.0;
        long time = world.getTime();
        long startTime = config.getLong("FearMeter.Conditions.Night.StartTime", 13000L);
        long endTime = config.getLong("FearMeter.Conditions.Night.EndTime", 23000L);
        if (time >= startTime && time <= endTime) {
            return config.getDouble("FearMeter.Conditions.Night.Rate", 0.5);
        }
        return 0.0;
    }

    private double evalBrightLight(Player player) {
        int lightLevel = player.getLocation().getBlock().getLightLevel();
        if (lightLevel >= config.getInt("FearMeter.Reductions.BrightLight.MinLightLevel", 12)) {
            return config.getDouble("FearMeter.Reductions.BrightLight.Rate", 0.8);
        }
        return 0.0;
    }

    private double evalNearFireSource(Player player) {
        int radius = config.getInt("FearMeter.Reductions.NearFireSource.SearchRadius", 5);
        Location loc = player.getLocation();
        World world = player.getWorld();
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (isFireSource(world.getBlockAt(loc.getBlockX() + x, loc.getBlockY() + y, loc.getBlockZ() + z))) {
                        return config.getDouble("FearMeter.Reductions.NearFireSource.Rate", 1.0);
                    }
                }
            }
        }
        return 0.0;
    }

    private boolean isFireSource(Block block) {
        return switch (block.getType()) {
            case TORCH, WALL_TORCH, LANTERN, SOUL_LANTERN, FIRE, SOUL_FIRE -> true;
            case CAMPFIRE, SOUL_CAMPFIRE -> ((Lightable) block.getBlockData()).isLit();
            default -> false;
        };
    }

    private double evalNearCompanions(Player player) {
        double radius = config.getDouble("FearMeter.Reductions.NearCompanions.SearchRadius", 12.0);
        Collection<Entity> companions = player.getWorld().getNearbyEntities(
            player.getLocation(), radius, radius, radius,
            e -> (!e.equals(player) && e instanceof Player) ||
                 (e instanceof Wolf wolf && wolf.isTamed()) ||
                 (e instanceof Cat cat && cat.isTamed()));
        if (!companions.isEmpty()) {
            double rateEach = config.getDouble("FearMeter.Reductions.NearCompanions.Rate", 0.6);
            double maxBonus = config.getDouble("FearMeter.Reductions.NearCompanions.MaxBonus", 3.0);
            return Math.min(companions.size() * rateEach, maxBonus);
        }
        return 0.0;
    }
}
