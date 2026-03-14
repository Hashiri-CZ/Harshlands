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
package cz.hashiri.harshlands.utils;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.fear.FearModule;
import cz.hashiri.harshlands.iceandfire.IceFireModule;
import cz.hashiri.harshlands.integrations.CompatiblePlugin;
import cz.hashiri.harshlands.integrations.RealisticSeasons;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.tan.TanModule;
import cz.hashiri.harshlands.tan.TempManager;
import cz.hashiri.harshlands.tan.ThirstManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DisplayTask extends BukkitRunnable implements HLTask {

    private static final Map<UUID, DisplayTask> tasks = new HashMap<>();
    private final UUID id;
    private final FileConfiguration tanConfig;
    private final FileConfiguration ifConfig;
    private final HLPlugin plugin;
    private final HLPlayer player;
    private final CharacterValues characterValues;
    private boolean underSirenEffect = false;
    private boolean parasitesActive = false;
    private final TanModule tanModule;
    private final IceFireModule ifModule;
    private final FearModule fearModule;
    private final FileConfiguration fearConfig;
    private final RealisticSeasons rs;

    // Cached config values
    private final boolean sirenChangeScreenEnabled;
    private final boolean hypothermiaScreenEnabled;
    private final boolean hypothermiaUseVanillaFreeze;
    private final int     hypothermiaFreezeTickCount;
    private final boolean hyperthermiaScreenEnabled;
    private final boolean thirstDehydrationScreenEnabled;

    // Cached permission booleans — refreshed every 200 ticks (10 seconds)
    private boolean permSirenResistance;
    private boolean permColdResistance;
    private boolean permHotResistance;
    private boolean permThirstResistance;
    private int permCacheCountdown = 0;

    public DisplayTask(HLPlugin plugin, HLPlayer player) {
        this.plugin = plugin;

        this.tanModule = (TanModule) HLModule.getModule(TanModule.NAME);
        this.ifModule = (IceFireModule) HLModule.getModule(IceFireModule.NAME);

        this.tanConfig = tanModule.isGloballyEnabled() ? tanModule.getUserConfig().getConfig() : null;
        this.ifConfig = ifModule.isGloballyEnabled() ? ifModule.getUserConfig().getConfig() : null;
        this.player = player;
        this.characterValues = new CharacterValues();
        this.id = player.getPlayer().getUniqueId();
        this.rs = (RealisticSeasons) CompatiblePlugin.getPlugin(RealisticSeasons.NAME);
        tasks.put(id, this);
        this.fearModule = (FearModule) HLModule.getModule(FearModule.NAME);
        this.fearConfig = (fearModule != null && fearModule.isGloballyEnabled())
                ? fearModule.getUserConfig().getConfig()
                : null;

        sirenChangeScreenEnabled       = ifConfig  != null && ifConfig.getBoolean("Siren.ChangeScreen.Enabled");
        hypothermiaScreenEnabled       = tanConfig != null && tanConfig.getBoolean("Temperature.Hypothermia.ScreenTinting.Enabled");
        hypothermiaUseVanillaFreeze    = tanConfig != null && tanConfig.getBoolean("Temperature.Hypothermia.ScreenTinting.UseVanillaFreezeEffect");
        hypothermiaFreezeTickCount     = tanConfig != null ? tanConfig.getInt("VisualTickPeriod") + 5 : 5;
        hyperthermiaScreenEnabled      = tanConfig != null && tanConfig.getBoolean("Temperature.Hyperthermia.ScreenTinting");
        thirstDehydrationScreenEnabled = tanConfig != null && tanConfig.getBoolean("Thirst.Dehydration.ScreenTinting");
    }

    @Override
    public void run() {
        Player player = this.player.getPlayer();

        if (globalConditionsMet(player)) {
            if (permCacheCountdown <= 0) {
                permSirenResistance  = player.hasPermission("harshlands.iceandfire.resistance.sirenvisual");
                permColdResistance   = player.hasPermission("harshlands.toughasnails.resistance.cold.visual");
                permHotResistance    = player.hasPermission("harshlands.toughasnails.resistance.hot.visual");
                permThirstResistance = player.hasPermission("harshlands.toughasnails.resistance.thirst.visual");
                permCacheCountdown = 200;
            } else {
                permCacheCountdown--;
            }

            String actionbarText = "";
            String titleText = "";

            if (ifConfig != null && ifModule.getAllowedWorlds().contains(player.getWorld().getName())) {
                if (!permSirenResistance) {
                    if (underSirenEffect) {
                        if (sirenChangeScreenEnabled) {
                            titleText += characterValues.getSirenView();
                        }
                    }
                }
            }

            if (tanConfig != null) {
                TempManager tempManager = tanModule.getTempManager();
                ThirstManager thirstManager = tanModule.getThirstManager();
                double temperature = tempManager.getTemperature(player);
                double thirst = thirstManager.getThirst(player);
                int tempInt   = (int) Math.round(temperature);
                int thirstInt = (int) Math.round(thirst);

                boolean isUnderwater = player.getRemainingAir() < 300 || player.getEyeLocation().getBlock().getType() == Material.WATER;

                if (tempManager.isTempEnabled(player) && thirstManager.isThirstEnabled(player)) {
                    actionbarText += characterValues.getTemperatureThirstActionbar(player, tempInt, thirstInt, isUnderwater, parasitesActive);
                }
                else {
                    // only temperature is enabled
                    if (tempManager.isTempEnabled(player)) {
                        actionbarText += characterValues.getTemperatureOnlyActionbar(player, tempInt);
                    }
                    // only thirst is enabled
                    else {
                        actionbarText += characterValues.getThirstOnlyActionbar(player, thirstInt, isUnderwater, parasitesActive);
                    }
                }

                if (temperature < 6) {
                    if (hypothermiaScreenEnabled && !rs.disableHypothermiaTinting()) {
                        if (!permColdResistance) {
                            if (hypothermiaUseVanillaFreeze) {
                                Utils.setFreezingView(player, hypothermiaFreezeTickCount);
                            }
                            else {
                                titleText += characterValues.getIceVignette(tempInt);
                            }
                        }
                    }
                }
                if (temperature > 19) {
                    if (hyperthermiaScreenEnabled && !rs.disableHyperthermiaTinting()) {
                        if (!permHotResistance) {
                            titleText += characterValues.getFireVignette(tempInt);
                        }
                    }
                }

                if (thirst < 5) {
                    if (thirstDehydrationScreenEnabled) {
                        if (!permThirstResistance) {
                            titleText += characterValues.getThirstVignette(thirstInt);
                        }
                    }
                }
            }

            if (fearConfig != null) {
                cz.hashiri.harshlands.data.fear.DataModule fearDm = this.player.getFearDataModule();
                if (fearDm != null && fearModule.isEnabled(player.getWorld())) {
                    double currentFear = fearDm.getFearLevel();
                    int dir = fearDm.getFearDirection();
                    boolean fearIncreasing = dir > 0;
                    boolean fearDecreasing = dir < 0;
                    actionbarText += characterValues.getFearActionbar(currentFear, fearIncreasing, fearDecreasing);
                }
            }

            Audience audience = (Audience) player;
            if (!actionbarText.isEmpty()) {
                audience.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(actionbarText));
            }

            if (!titleText.isEmpty()) {
                audience.showTitle(Title.title(
                    LegacyComponentSerializer.legacySection().deserialize(titleText),
                    Component.empty(),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(70 * 50L), Duration.ZERO)));
            }
        }
        else {
            stop();
        }
    }

    @Override
    public boolean conditionsMet(@Nullable Player player) {
        return globalConditionsMet(player);
    }

    @Override
    public void start() {
        int tickPeriod = tanConfig.getInt("VisualTickPeriod");
        this.runTaskTimer(plugin, 0L, tickPeriod);
    }

    @Override
    public void stop() {
        tasks.remove(id);
        cancel();
    }

    public void setUnderSirenEffect(boolean underSirenEffect) {
        this.underSirenEffect = underSirenEffect;
    }

    public void setParasitesActive(boolean parasitesActive) {
        this.parasitesActive = parasitesActive;
    }

    public static boolean hasTask(UUID id) {
        return tasks.containsKey(id) && tasks.get(id) != null;
    }

    public static Map<UUID, DisplayTask> getTasks() {
        return tasks;
    }
}
