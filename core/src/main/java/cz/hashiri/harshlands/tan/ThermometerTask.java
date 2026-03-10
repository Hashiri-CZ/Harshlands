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

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.HLTask;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static cz.hashiri.harshlands.tan.TemperatureCalculateTask.MAXIMUM_TEMPERATURE;
import static cz.hashiri.harshlands.tan.TemperatureCalculateTask.MINIMUM_TEMPERATURE;

public class ThermometerTask extends BukkitRunnable implements HLTask {

    private static final Map<UUID, ThermometerTask> tasks = new HashMap<>();
    private final FileConfiguration config;
    private final HLPlugin plugin;
    private final HLPlayer player;
    private final UUID id;
    private final Collection<String> allowedWorlds;
    private final TemperatureCalculateTask task;
    private final Location originalCompassTarget;


    public ThermometerTask(HLPlugin plugin, HLPlayer player) {
        this.plugin = plugin;
        this.config = HLModule.getModule(TanModule.NAME).getUserConfig().getConfig();
        this.player = player;
        this.id = player.getPlayer().getUniqueId();
        this.allowedWorlds = HLModule.getModule(TanModule.NAME).getAllowedWorlds();
        this.task = TemperatureCalculateTask.getTasks().get(id);
        this.originalCompassTarget = player.getPlayer().getCompassTarget();
        tasks.put(id, this);
    }

    @Override
    public void run() {
        Player player = this.player.getPlayer();

        if (conditionsMet(player)) {
            double equilibriumTemp = task.getEquilibriumTemp();

            if (equilibriumTemp > MAXIMUM_TEMPERATURE) {
                equilibriumTemp = MAXIMUM_TEMPERATURE;
            }
            if (equilibriumTemp < MINIMUM_TEMPERATURE) {
                equilibriumTemp = MINIMUM_TEMPERATURE;
            }

            Location loc = player.getEyeLocation();

            double rad = Math.PI * ((MAXIMUM_TEMPERATURE / 2D) - equilibriumTemp) / MAXIMUM_TEMPERATURE;

            Vector dir = loc.getDirection().normalize();
            dir.rotateAroundY(rad).multiply(1000);
            loc.add(dir);
            player.setCompassTarget(loc);
        }
        else {
            stop();
        }
    }

    @Override
    public boolean conditionsMet(@Nullable Player player) {
        return globalConditionsMet(player) && allowedWorlds.contains(player.getWorld().getName()) && task != null && HLItem.isHoldingItem("thermometer", player);
    }

    @Override
    public void start() {
        int tickPeriod = config.getInt("Items.thermometer.TickPeriod"); // get the tick period
        this.runTaskTimer(plugin, 1L, tickPeriod);
    }

    @Override
    public void stop() {
        if (!(player.getPlayer() == null || originalCompassTarget == null)) {
            player.getPlayer().setCompassTarget(originalCompassTarget);
        }

        tasks.remove(id);
        cancel();
    }

    public static boolean hasTask(UUID id) {
        return tasks.containsKey(id) && tasks.get(id) != null;
    }


    public static Map<UUID, ThermometerTask> getTasks() {
        return tasks;
    }
}

