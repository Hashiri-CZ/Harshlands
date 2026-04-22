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
import cz.hashiri.harshlands.HLPlugin;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class FearConditionEvaluator {

    private final HLPlugin plugin;
    private final FileConfiguration config;

    public FearConditionEvaluator(HLPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    private record NearbySnapshot(List<Player> players, List<Monster> monsters, List<Entity> companions) {}

    private NearbySnapshot scanNearby(Player player) {
        double maxRadius = config.getDouble("FearMeter.Conditions.AloneUnderground.SearchRadius", 20.0);
        double enemyRadius = config.getDouble("FearMeter.Conditions.NearbyEnemies.SearchRadius", 16.0);
        double companionRadius = config.getDouble("FearMeter.Reductions.NearCompanions.SearchRadius", 12.0);
        double enemyRadiusSq = enemyRadius * enemyRadius;
        double companionRadiusSq = companionRadius * companionRadius;

        Collection<Entity> allNearby = player.getWorld().getNearbyEntities(
            player.getLocation(), maxRadius, maxRadius, maxRadius);

        List<Player> players = new ArrayList<>();
        List<Monster> monsters = new ArrayList<>();
        List<Entity> companions = new ArrayList<>();
        Location playerLoc = player.getLocation();

        for (Entity e : allNearby) {
            if (e.equals(player)) continue;
            double distSq = e.getLocation().distanceSquared(playerLoc);

            if (e instanceof Player p) {
                players.add(p);
                if (distSq <= companionRadiusSq) companions.add(p);
            } else if (e instanceof Monster m) {
                if (distSq <= enemyRadiusSq) monsters.add(m);
            } else if (e instanceof Wolf wolf && wolf.isTamed()) {
                if (distSq <= companionRadiusSq) companions.add(wolf);
            } else if (e instanceof Cat cat && cat.isTamed()) {
                if (distSq <= companionRadiusSq) companions.add(cat);
            }
        }

        return new NearbySnapshot(players, monsters, companions);
    }

    public void evaluate(Player player, DataModule dm) {
        NearbySnapshot snapshot = scanNearby(player);

        double darkness     = config.getBoolean("FearMeter.Conditions.Darkness.Enabled", true)         ? evalDarkness(player)            : 0.0;
        double cave         = config.getBoolean("FearMeter.Conditions.Cave.Enabled", true)             ? evalCave(player)                : 0.0;
        double underground  = config.getBoolean("FearMeter.Conditions.AloneUnderground.Enabled", true) ? evalAloneUnderground(player, snapshot) : 0.0;
        double lowHealth    = config.getBoolean("FearMeter.Conditions.LowHealth.Enabled", true)        ? evalLowHealth(player, dm)       : 0.0;
        double enemies      = config.getBoolean("FearMeter.Conditions.NearbyEnemies.Enabled", true)    ? evalNearbyEnemies(snapshot)     : 0.0;
        double cold         = config.getBoolean("FearMeter.Conditions.Cold.Enabled", true)             ? evalCold(player)                : 0.0;
        double storm        = config.getBoolean("FearMeter.Conditions.Storm.Enabled", true)            ? evalStorm(player)               : 0.0;
        double night        = config.getBoolean("FearMeter.Conditions.Night.Enabled", true)            ? evalNight(player)               : 0.0;
        double malnourished = config.getBoolean("FearMeter.Conditions.Malnourished.Enabled", true)  ? evalMalnourished(player)        : 0.0;

        double brightLight = config.getBoolean("FearMeter.Reductions.BrightLight.Enabled", true)   ? evalBrightLight(player)    : 0.0;
        double nearFire    = config.getBoolean("FearMeter.Reductions.NearFireSource.Enabled", true) ? evalNearFireSource(player) : 0.0;
        double companions  = config.getBoolean("FearMeter.Reductions.NearCompanions.Enabled", true) ? evalNearCompanions(snapshot) : 0.0;

        // Sum raw trigger gain and clamp it to MaxGainPerCheck before applying
        double grossGain = darkness + cave + underground + lowHealth + enemies + cold + storm + night + malnourished;
        double maxGain = config.getDouble("FearMeter.MaxGainPerCheck", 3.0);
        double clampedGain = Math.min(grossGain, maxGain);

        double reductions = brightLight + nearFire + companions;

        double decayAmount = 0.0;
        if (config.getBoolean("FearMeter.PassiveDecay.Enabled", true)) {
            double decayRate = config.getDouble("FearMeter.PassiveDecay.Rate", 0.5);
            if (grossGain == 0.0) {
                // No triggers firing — apply full passive decay
                decayAmount = decayRate;
            } else if (config.getBoolean("FearMeter.AllowPartialDecayUnderTriggers", true)
                    && grossGain < maxGain / 2.0) {
                // Triggers active but gain is modest — apply half decay so caves don't freeze progress
                decayAmount = decayRate * 0.5;
            }
            // If grossGain >= maxGain/2, no decay — triggers dominate
        }

        double netDelta = clampedGain - reductions - decayAmount;

        if (netDelta > 0.0) {
            dm.increaseFear(netDelta);
        } else if (netDelta < 0.0) {
            dm.decreaseFear(-netDelta);
        }

        cz.hashiri.harshlands.debug.DebugManager debugManager = plugin.getDebugManager();
        if (debugManager.isActive("Fear", "FearMeter", player.getUniqueId())) {
            double prevFear = dm.getFearLevel() - netDelta;
            String chatLine = String.format("\u00a75[Fear.Meter] \u00a7f%s: %.1f \u2192 %.1f (%+.1f) dark=%+.1f mob=%+.1f light=%+.1f gross=%.1f->capped=%.1f",
                player.getName(), prevFear, dm.getFearLevel(), netDelta, darkness, enemies, -brightLight, grossGain, clampedGain);
            String consoleLine = String.format(
                "dark=%.2f cave=%.2f ugnd=%.2f lhp=%.2f mob=%.2f cold=%.2f storm=%.2f night=%.2f | gross=%.2f capped=%.2f | -light=%.2f -fire=%.2f -comp=%.2f -decay=%.2f | net=%.2f | total=%.2f dir=%s",
                darkness, cave, underground, lowHealth, enemies, cold, storm, night,
                grossGain, clampedGain, brightLight, nearFire, companions, decayAmount, netDelta, dm.getFearLevel(),
                netDelta > 0 ? "UP" : netDelta < 0 ? "DOWN" : "STABLE");
            debugManager.send("Fear", "FearMeter", player.getUniqueId(), chatLine, consoleLine);
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

    private double evalAloneUnderground(Player player, NearbySnapshot snapshot) {
        int skyLight = player.getEyeLocation().getBlock().getLightFromSky();
        if (skyLight != 0) return 0.0;
        if (snapshot.players().isEmpty()) {
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

    private double evalNearbyEnemies(NearbySnapshot snapshot) {
        if (!snapshot.monsters().isEmpty()) {
            double ratePerMob = config.getDouble("FearMeter.Conditions.NearbyEnemies.Rate", 0.5);
            double maxBonus = config.getDouble("FearMeter.Conditions.NearbyEnemies.MaxMobBonus", 3.0);
            return Math.min(snapshot.monsters().size() * ratePerMob, maxBonus);
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

    private double evalNearCompanions(NearbySnapshot snapshot) {
        if (!snapshot.companions().isEmpty()) {
            double rateEach = config.getDouble("FearMeter.Reductions.NearCompanions.Rate", 0.6);
            double maxBonus = config.getDouble("FearMeter.Reductions.NearCompanions.MaxBonus", 3.0);
            return Math.min(snapshot.companions().size() * rateEach, maxBonus);
        }
        return 0.0;
    }

    private double evalMalnourished(Player player) {
        cz.hashiri.harshlands.data.HLModule feModule = cz.hashiri.harshlands.data.HLModule.getModule(
            cz.hashiri.harshlands.foodexpansion.FoodExpansionModule.NAME);
        if (feModule == null || !feModule.isGloballyEnabled()) return 0.0;

        cz.hashiri.harshlands.data.HLPlayer hlPlayer = cz.hashiri.harshlands.data.HLPlayer.getPlayers()
            .get(player.getUniqueId());
        if (hlPlayer == null) return 0.0;

        cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
        if (dm == null) return 0.0;

        double threshold = config.getDouble("FearMeter.Conditions.Malnourished.Threshold", 30.0);
        double rate = config.getDouble("FearMeter.Conditions.Malnourished.Amount", 3.0);
        int count = dm.getData().countBelowThreshold(threshold);
        return count * rate;
    }
}
