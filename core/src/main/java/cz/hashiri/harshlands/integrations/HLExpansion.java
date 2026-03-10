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
package cz.hashiri.harshlands.integrations;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.tan.TanModule;
import cz.hashiri.harshlands.tan.TempManager;
import cz.hashiri.harshlands.tan.TemperatureCalculateTask;
import cz.hashiri.harshlands.tan.ThirstManager;
import cz.hashiri.harshlands.utils.CharacterValues;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class HLExpansion extends PlaceholderExpansion {

    private final HLPlugin plugin;
    private final TempManager tempManager;
    private final ThirstManager thirstManager;
    private final CharacterValues charValues;

    public HLExpansion(HLPlugin plugin) {
        this.plugin = plugin;
        TanModule module = (TanModule) HLModule.getModule(TanModule.NAME);
        this.tempManager = module.getTempManager();
        this.thirstManager = module.getThirstManager();
        this.charValues = new CharacterValues();
    }

    @Override
    public @Nonnull String getIdentifier() {
        return "rsv";
    }

    @Override
    public @Nonnull String getAuthor() {
        return "Hashiri_";
    }

    @Override
    public @Nonnull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @Nonnull String params) {
        String[] args = params.split("_");

        if (args.length == 0) {
            printErrorMessage(params);
            return "";
        }

        switch (args[0]) {
            case "tan" -> {
                if (!HLModule.getModule(TanModule.NAME).isEnabled(player)) {
                    printErrorMessage(params);
                    return "";
                }

                if (args.length < 3) {
                    printErrorMessage(params);
                    return "";
                }
                switch (args[1]) {
                    case "temp" -> {
                        if (!tempManager.isTempEnabled(player)) {
                            printErrorMessage(params);
                            return "";
                        }

                        double internalTemp = tempManager.getTemperature(player);

                        switch (args[2]) {
                            case "numeric" -> {
                                if (args.length < 5) {
                                    printErrorMessage(params);
                                    return "";
                                }

                                double temp;

                                int decimalplaces;

                                try {
                                    decimalplaces = Integer.parseInt(args[4]);
                                } catch (NumberFormatException e) {
                                    return "";
                                }

                                FileConfiguration intConfig = plugin.getIntegrationsConfig();
                                double celsius = 2 * internalTemp / (TemperatureCalculateTask.MAXIMUM_TEMPERATURE * (internalTemp > TemperatureCalculateTask.NEUTRAL_TEMPERATURE ? intConfig.getDouble(RealisticSeasons.NAME + ".HotMultiplier") : intConfig.getDouble(RealisticSeasons.NAME + ".ColdMultiplier"))) + intConfig.getDouble(RealisticSeasons.NAME + ".DefaultTemperature");

                                switch (args[3]) {
                                    case "internal" -> temp = internalTemp;
                                    case "celsius" -> temp = celsius;
                                    case "fahrenheit" -> temp = celsius * 9D/5 + 32;
                                    case "kelvin" -> temp = celsius + 273.15;
                                    default -> {
                                        printErrorMessage(params);
                                        return "";
                                    }
                                }

                                return String.valueOf(Utils.round(temp, decimalplaces));
                            }
                            case "bar" -> {
                                return charValues.getTemperature(player, (int) Math.round(internalTemp));
                            }
                            case "vignette" -> {
                                return charValues.getFireVignette(player, (int) Math.round(internalTemp));
                            }
                            default -> {}
                        }
                    }
                    case "thirst" -> {
                        if (args.length < 4) {
                            printErrorMessage(params);
                            return "";
                        }

                        if (!thirstManager.isThirstEnabled(player)) {
                            printErrorMessage(params);
                            return "";
                        }

                        double thirst = thirstManager.getThirst(player);

                        switch (args[2]) {
                            case "numeric" -> {
                                int decimalplaces;

                                try {
                                    decimalplaces = Integer.parseInt(args[3]);
                                } catch (NumberFormatException e) {
                                    printErrorMessage(params);
                                    return "";
                                }

                                return String.valueOf(Utils.round(thirst, decimalplaces));
                            }
                            case "bar" -> {
                                return charValues.getThirst(player, (int) Math.round(thirst), player.getRemainingAir() < 300 || player.isInWater(), thirstManager.hasParasites(player));
                            }
                            case "vignette" -> {
                                return charValues.getThirstVignette(player, (int) Math.round(thirst));
                            }
                            default -> {}
                        }
                    }
                    case "hud" -> {
                        if (player == null || !(player.isOnline() && TemperatureCalculateTask.hasTask(player.getUniqueId()) && TemperatureCalculateTask.hasTask(player.getUniqueId()))) {
                            printErrorMessage(params);
                            return "";
                        }

                        charValues.getTemperatureThirstActionbar(player, (int) Math.round(tempManager.getTemperature(player)), (int) Math.round(thirstManager.getThirst(player)), player.getRemainingAir() < 300 || player.isInWater(), thirstManager.hasParasites(player));
                    }
                    default -> {}
                }
            }
            default -> {}
        }

        printErrorMessage(params);
        return "";
    }

    private void printErrorMessage(@Nonnull String params) {
        ConfigurationSection section = plugin.getIntegrationsConfig().getConfigurationSection(PAPI.NAME + ".Error");

        if (section.getBoolean("Enabled")) {
            plugin.getLogger().info(Utils.translateMsg(section.getString("Message"), null, Map.of("PLACEHOLDER", "%" + params + "%")));
        }
    }
}

