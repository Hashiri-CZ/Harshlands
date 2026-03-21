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
package cz.hashiri.harshlands.commands;

import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.comfort.ComfortModule;
import cz.hashiri.harshlands.comfort.ComfortScoreCalculator;
import cz.hashiri.harshlands.fear.FearModule;
import cz.hashiri.harshlands.iceandfire.IceFireModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.tan.TanModule;
import cz.hashiri.harshlands.tan.TempManager;
import cz.hashiri.harshlands.tan.TemperatureCalculateTask;
import cz.hashiri.harshlands.tan.ThirstCalculateTask;
import cz.hashiri.harshlands.tan.ThirstManager;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.HLMob;
import cz.hashiri.harshlands.utils.Utils;
import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static cz.hashiri.harshlands.rsv.HLPlugin.NAME;

/**
 * Commands is a class that allows users to
 * access the plugin's commands in-game
 * @author Hashiri_
 * @version 1.2.10-RELEASE
 * @since 1.0
 */
public class Commands implements CommandExecutor {

    /**
     * Dependency injecting the main and custom config class for use
     * The custom config class must be injected because its non-static methods are needed
     */
    private final HLPlugin plugin;
    private final FileConfiguration config;

    // constructing the Commands class
    public Commands(HLPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getCommandsConfig();
    }

    /**
     * Performs various actions depending on what the player types as a command
     * @param sender The user who typed the command
     * @param cmd The command typed
     * @param label The word directly after the forward slash
     * @param args An array holding every argument after the label
     * @return A boolean showing if the user successfully executed the appropriate command
     * @see HLItem
     */
    @Override
    public boolean onCommand(@Nonnull CommandSender sender, @Nonnull Command cmd, @Nonnull String label, @Nonnull String[] args) {
        // check if the user typed /harshlands, case-insensitive
        if (cmd.getName().equalsIgnoreCase(NAME)) {
            if (sender instanceof BlockCommandSender) {
                if (!config.getBoolean("EnableCommandBlockUsage")) {
                    sendNoPermissionMessage(sender);
                    return true;
                }
            }
            else if (sender instanceof CommandMinecart) {
                if (!config.getBoolean("EnableCommandBlockMinecartUsage")) {
                    sendNoPermissionMessage(sender);
                    return true;
                }
            }

            // check if the user only typed /harshlands with no arguments
            if (args.length == 0) {
                // send the user a message explaining how to use the harshlands command
                sendIncompleteCommandMsg(sender);
                return true;
            }

            switch (args[0].toLowerCase()) {
                case "give" -> {
                    // check if the player has the permission to give himself/herself items
                    if (!sender.hasPermission("harshlands.command.give")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    // check if the user typed more than 3 arguments
                    if (args.length < 3) {
                        sendIncompleteCommandMsg(sender);
                        return true;
                    }

                    HLItem customItem = HLItem.getItem(args[2]); // get the item from its command name

                    if (!Utils.isItemReal(customItem)) {
                        sender.sendMessage(Utils.translateMsg(config.getString("MisspelledItemName"), sender, Map.of("MISSPELLED_NAME", args[2])));
                        return true;
                    }

                    int amount = 1;

                    if (args.length > 3) {
                        try {
                            amount = Integer.parseInt(args[3]);
                        } catch (NumberFormatException e) {
                            sendInvalidArgumentMsg(sender);
                            return true;
                        }
                    }

                    if (amount < 1) {
                        if (config.getBoolean("Give.TooFewItems.Enabled"))
                            sender.sendMessage(Utils.translateMsg(config.getString("Give.TooFewItems.Message"), sender, null));
                        return true;
                    }

                    if (amount > config.getInt("Give.TooManyItems.MaxValue")) {
                        if (config.getBoolean("Give.TooManyItems.Enabled")) {
                            Map<String, Object> placeholders = Map.of("MAXIMUM_VALUE", config.getInt("Give.TooManyItems.MaxValue"));
                            sender.sendMessage(Utils.translateMsg(config.getString("Give.TooManyItems.Message"), sender, placeholders));
                        }
                        return true;
                    }

                    List<Entity> targets = Bukkit.selectEntities(sender, args[1]);

                    if (targets.isEmpty()) {
                        sendInvalidArgumentMsg(sender);
                        return true;
                    }

                    List<Player> filteredTargets = new ArrayList<>();

                    for (Entity e : targets) {
                        if (e instanceof Player player && player.isOnline()) {
                            filteredTargets.add(player);
                        }
                    }

                    if (filteredTargets.isEmpty()) {
                        sendInvalidTargetMsg(sender);
                        return true;
                    }

                    for (Player player : filteredTargets) {
                        Utils.addItemToInventory(player.getInventory(), customItem, amount, player.getLocation());
                        playSound(player);
                    }

                    if (config.getBoolean("Give.CorrectExecution.Enabled")) {
                        org.bukkit.inventory.meta.ItemMeta giveMeta = customItem.getItemMeta();
                        String displayName = giveMeta != null ? giveMeta.getDisplayName() : "";
                        if (filteredTargets.size() == 1) {
                            Map<String, Object> placeholders = Map.of("VALUE", amount, "DISPLAY_NAME", displayName, "PLAYER_NAME", filteredTargets.get(0).getDisplayName());
                            sender.sendMessage(Utils.translateMsg(config.getString("Give.CorrectExecution.SingleTargetMessage"), sender, placeholders));
                        }
                        else {
                            Map<String, Object> placeholders = Map.of("VALUE", amount, "DISPLAY_NAME", displayName);
                            sender.sendMessage(Utils.translateMsg(config.getString("Give.CorrectExecution.MultipleTargetMessage"), sender, placeholders));
                        }
                    }

                    return true;
                }
                case "reload" -> {
                    // check if the player has the permission to reload the plugin
                    if (!sender.hasPermission("harshlands.command.reload")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    HLConfig.getConfigList().forEach(config -> config.reloadConfig());

                    if (config.getBoolean("Reload.CorrectExecution.Enabled"))
                        sender.sendMessage(Utils.translateMsg(config.getString("Reload.CorrectExecution.Message"), sender, null));

                    return true;
                }
                case "spawnitem" -> {
                    // check if the player has the permission to give himself/herself items
                    if (!sender.hasPermission("harshlands.command.spawnitem")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    if (args.length == 1) {
                        sendIncompleteCommandMsg(sender);
                        return true;
                    }

                    ItemStack item = HLItem.getItem(args[1]);

                    /**
                     * Check if the second argument is a custom item
                     * example: /harshlands spawnitem ^~%1t --> invalid item name
                     *          /harshlands spawnitem flint_hatchet --> valid item name
                     */
                    if (!Utils.isItemReal(item)) {
                        sender.sendMessage(Utils.translateMsg(config.getString("MisspelledItemName"), sender, Map.of("MISSPELLED_NAME", args[1])));
                        return true;
                    }

                    int amount = 1;
                    World world;
                    double x, y, z;

                    if (sender instanceof Player player) {
                        if (args.length == 2) {
                            world = player.getWorld();
                            Location loc = player.getLocation();
                            x = loc.getX();
                            y = loc.getY();
                            z = loc.getZ();
                        }
                        else {
                            if (args.length < 6) {
                                sendIncompleteCommandMsg(player);
                                return true;
                            }

                            try {
                                amount = Integer.parseInt(args[2]);

                                x = Double.parseDouble(args[3]);
                                y = Double.parseDouble(args[4]);
                                z = Double.parseDouble(args[5]);
                            }
                            catch (NumberFormatException e) {
                                sendInvalidArgumentMsg(sender);
                                return true;
                            }


                            world = player.getWorld();

                            if (args.length == 7) {
                                world = Bukkit.getWorld(args[6]);
                            }
                        }
                    }
                    else {
                        if (args.length < 7) {
                            sendIncompleteCommandMsg(sender);
                            return true;
                        }

                        try {
                            amount = Integer.parseInt(args[2]);

                            x = Double.parseDouble(args[3]);
                            y = Double.parseDouble(args[4]);
                            z = Double.parseDouble(args[5]);
                            world = Bukkit.getWorld(args[6]);
                        }
                        catch (NumberFormatException e) {
                            sendInvalidArgumentMsg(sender);
                            return true;
                        }
                    }

                    if (world == null) {
                        sender.sendMessage(Utils.translateMsg(config.getString("MisspelledWorld"), sender, null));
                        return true;
                    }

                    if (amount < 1) {
                        if (config.getBoolean("SpawnItem.TooFewItems.Enabled"))
                            sender.sendMessage(Utils.translateMsg(config.getString("SpawnItem.TooFewItems.Message"), sender, null));
                        return true;
                    }

                    if (amount > item.getMaxStackSize()) {
                        if (config.getBoolean("SpawnItem.ExceedsStackSize.Enabled")) {
                            Map<String, Object> placeholders = Map.of("STACK_SIZE", item.getMaxStackSize());
                            sender.sendMessage(Utils.translateMsg(config.getString("SpawnItem.ExceedsStackSize.Message"), sender, placeholders));
                        }
                        return true;
                    }

                    if (amount > config.getInt("SpawnItem.TooManyItems.MaxValue")) {
                        if (config.getBoolean("SpawnItem.TooManyItems.Enabled")) {
                            Map<String, Object> placeholders = Map.of("MAXIMUM_VALUE", config.getInt("SpawnItem.TooManyItems.MaxValue"));
                            sender.sendMessage(Utils.translateMsg(config.getString("SpawnItem.TooManyItems.Message"), sender, placeholders));
                        }
                        return true;
                    }

                    if (amount <= item.getMaxStackSize() || !config.getBoolean("SpawnItem.CheckStackSize")) {
                        item.setAmount(amount);
                    }
                    else {
                        sendInvalidArgumentMsg(sender);
                        return true;
                    }

                    world.dropItemNaturally(new Location(world, x, y, z), item);

                    if (config.getBoolean("SpawnItem.CorrectExecution.Enabled")) {
                        Map<String, Object> placeholders = Map.of("ITEM_NAME", item.hasItemMeta() ? item.getItemMeta().getDisplayName() : args[1], "X_COORD", x, "Y_COORD", y, "Z_COORD", z, "WORLD_NAME", world.getName());
                        sender.sendMessage(Utils.translateMsg(config.getString("SpawnItem.CorrectExecution.Message"), sender, placeholders));
                    }

                    return true;
                }
                case "summon" -> {
                    // check if the player has the permission to summon mobs
                    if (!sender.hasPermission("harshlands.command.summon")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    if (args.length == 1) {
                        sendIncompleteCommandMsg(sender);
                        return true;
                    }

                    HLMob mob;
                    World world;
                    double x, y, z;

                    if (sender instanceof Player player) {
                        if (args.length == 2) {
                            Location loc = player.getLocation();
                            world = loc.getWorld();
                            x = loc.getX();
                            y = loc.getY();
                            z = loc.getZ();
                        }
                        else {
                            if (args.length < 5) {
                                sendIncompleteCommandMsg(player);
                                return true;
                            }

                            try {
                                x = Double.parseDouble(args[2]);
                                y = Double.parseDouble(args[3]);
                                z = Double.parseDouble(args[4]);
                            }
                            catch (NumberFormatException e) {
                                sendInvalidArgumentMsg(sender);
                                return true;
                            }


                            world = player.getWorld();

                            if (args.length == 6) {
                                world = Bukkit.getWorld(args[6]);
                            }
                        }
                    }
                    else {
                        if (args.length < 6) {
                            sendIncompleteCommandMsg(sender);
                            return true;
                        }

                        try {
                            x = Double.parseDouble(args[2]);
                            y = Double.parseDouble(args[3]);
                            z = Double.parseDouble(args[4]);
                            world = Bukkit.getWorld(args[5]);
                        }
                        catch (NumberFormatException e) {
                            sendInvalidArgumentMsg(sender);
                            return true;
                        }
                    }

                    if (world == null) {
                        sender.sendMessage(Utils.translateMsg(config.getString("MisspelledWorld"), sender, null));
                        return true;
                    }

                    Location loc = new Location(world, x, y, z);
                    String mobName = args[1].toLowerCase();

                    switch (mobName) {
                        case "fire_dragon" -> mob = Utils.spawnFireDragon(loc);
                        case "ice_dragon" -> mob = Utils.spawnIceDragon(loc);
                        case "lightning_dragon" -> mob = Utils.spawnLightningDragon(loc);
                        case "sea_serpent" -> mob = Utils.spawnSeaSerpent(loc);
                        case "siren" -> mob = Utils.spawnSiren(loc);
                        default -> mob = null;
                    }

                    if (mob == null) {
                        if (config.getBoolean("Summon.MisspelledMob.Enabled"))
                            sender.sendMessage(Utils.translateMsg(config.getString("Summon.MisspelledMob.Message"), sender, Map.of("MISSPELLED_NAME", mobName)));
                        return true;
                    }

                    switch (mobName) {
                        case "fire_dragon", "ice_dragon", "lightning_dragon", "sea_serpent", "siren" -> {
                            if (!HLModule.getModule(IceFireModule.NAME).isEnabled(world)) {
                                if (config.getBoolean("Summon.RequiredModulesDisabled.Enabled")) {
                                    Map<String, Object> placeholders = Map.of("REQUIRED_MODULES", String.join(", ", mob.getRequiredModules()));
                                    sender.sendMessage(Utils.translateMsg(config.getString("Summon.RequiredModulesDisabled.Message"), sender, placeholders));
                                }
                                return true;
                            }
                        }
                    }

                    mob.addEntityToWorld(world);

                    if (config.getBoolean("Summon.CorrectExecution.Enabled")) {
                        Map<String, Object> placeholders = Map.of("MOB_NAME", StringUtils.capitalize(mobName.replaceAll("_", "")), "X_COORD", x, "Y_COORD", y, "Z_COORD", z, "WORLD_NAME", world.getName());
                        sender.sendMessage(Utils.translateMsg(config.getString("Summon.CorrectExecution.Message"), sender, placeholders));
                    }
                    return true;
                }
                case "temperature" -> {
                    // check if the player has the permission to change temperature
                    if (!sender.hasPermission("harshlands.command.temperature")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    if (args.length <= 2) {
                        sendIncompleteCommandMsg(sender);
                        return true;
                    }

                    TempManager manager = ((TanModule) HLModule.getModule(TanModule.NAME)).getTempManager();

                    List<Entity> targets = Bukkit.selectEntities(sender, args[1]);

                    if (targets == null) {
                        sendInvalidArgumentMsg(sender);
                        return true;
                    }

                    if (!HLModule.getModule(TanModule.NAME).isGloballyEnabled()) {
                        if (config.getBoolean("Temperature.TanModuleDisabled.Enabled"))
                            sender.sendMessage(Utils.translateMsg(config.getString("Temperature.TanModuleDisabled.Message"), sender, null));
                        return true;
                    }

                    List<Player> filteredTargets = new ArrayList<>();

                    for (Entity e : targets) {
                        if (e instanceof Player player && player.isOnline() && manager.isTempEnabled(player)) {
                            filteredTargets.add(player);
                        }
                    }

                    if (filteredTargets.isEmpty()) {
                        sendInvalidTargetMsg(sender);
                        return true;
                    }

                    double temperature = (TemperatureCalculateTask.MAXIMUM_TEMPERATURE + TemperatureCalculateTask.MINIMUM_TEMPERATURE) / 2;
                    double addition = 0;
                    boolean isRelative = false;

                    try {
                        if (args[2].startsWith("~")) {
                            isRelative = true;
                            addition = Double.parseDouble(args[2].substring(1));
                        }
                        else {
                            temperature = Double.parseDouble(args[2]);
                        }
                    } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                        sendInvalidArgumentMsg(sender);
                        return true;
                    }

                    if (temperature < TemperatureCalculateTask.MINIMUM_TEMPERATURE) {
                        if (config.getBoolean("Temperature.BelowMinValue.Enabled")) {
                            Map<String, Object> placeholders = Map.of("MINIMUM_VALUE", TemperatureCalculateTask.MINIMUM_TEMPERATURE);
                            sender.sendMessage(Utils.translateMsg(config.getString("Temperature.BelowMinValue.Message"), sender, placeholders));
                        }
                        return true;
                    }

                    if (temperature > TemperatureCalculateTask.MAXIMUM_TEMPERATURE) {
                        if (config.getBoolean("Temperature.AboveMaxValue.Enabled")) {
                            Map<String, Object> placeholders = Map.of("MAXIMUM_VALUE", TemperatureCalculateTask.MAXIMUM_TEMPERATURE);
                            sender.sendMessage(Utils.translateMsg(config.getString("Temperature.AboveMaxValue.Message"), sender, placeholders));
                        }
                        return true;
                    }

                    double oldTemp = manager.getTemperature(filteredTargets.get(0));

                    if (isRelative) {
                        double finalAddition = addition;
                        filteredTargets.forEach(player -> manager.addTemperature(player, finalAddition));
                    }
                    else {
                        double finalTemperature = temperature;
                        filteredTargets.forEach(player -> manager.setTemperature(player, finalTemperature));
                    }

                    if (config.getBoolean("Temperature.CorrectExecution.Enabled")) {
                        if (filteredTargets.size() == 1) {
                            if (isRelative) {
                                Map<String, Object> placeholders = Map.of("PLAYER_NAME", filteredTargets.get(0).getDisplayName(), "OLD_TEMPERATURE", oldTemp, "NEW_TEMPERATURE", temperature, "CHANGE", addition);
                                sender.sendMessage(Utils.translateMsg(config.getString("Temperature.CorrectExecution.SingleTargetRelativeMessage"), sender, placeholders));
                            }
                            else {
                                Map<String, Object> placeholders = Map.of("PLAYER_NAME", filteredTargets.get(0).getDisplayName(), "OLD_TEMPERATURE", oldTemp, "NEW_TEMPERATURE", temperature);
                                sender.sendMessage(Utils.translateMsg(config.getString("Temperature.CorrectExecution.SingleTargetMessage"), sender, placeholders));
                            }

                        }
                        else {
                            if (isRelative) {
                                Map<String, Object> placeholders = Map.of("CHANGE", addition);
                                sender.sendMessage(Utils.translateMsg(config.getString("Temperature.CorrectExecution.MultipleTargetRelativeMessage"), sender, placeholders));
                            }
                            else {
                                Map<String, Object> placeholders = Map.of("NEW_TEMPERATURE", temperature);
                                sender.sendMessage(Utils.translateMsg(config.getString("Temperature.CorrectExecution.MultipleTargetMessage"), sender, placeholders));
                            }
                        }
                    }

                    return true;
                }
                case "thirst" -> {
                    // check if the player has the permission to change thirst
                    if (!sender.hasPermission("harshlands.command.thirst")) {
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    if (args.length <= 2) {
                        sendIncompleteCommandMsg(sender);
                        return true;
                    }

                    ThirstManager manager = ((TanModule) HLModule.getModule(TanModule.NAME)).getThirstManager();

                    List<Entity> targets = Bukkit.selectEntities(sender, args[1]);

                    if (targets.isEmpty()) {
                        sendInvalidArgumentMsg(sender);
                        return true;
                    }

                    if (!HLModule.getModule(TanModule.NAME).isGloballyEnabled()) {
                        if (config.getBoolean("Thirst.TanModuleDisabled.Enabled"))
                            sender.sendMessage(Utils.translateMsg(config.getString("Thirst.TanModuleDisabled.Message"), sender, null));
                        return true;
                    }

                    List<Player> filteredTargets = new ArrayList<>();

                    for (Entity e : targets) {
                        if (e instanceof Player player && player.isOnline() && manager.isThirstEnabled(player)) {
                            filteredTargets.add(player);
                        }
                    }

                    if (filteredTargets.isEmpty()) {
                        sendInvalidTargetMsg(sender);
                        return true;
                    }

                    int thirst = (ThirstCalculateTask.MAXIMUM_THIRST + ThirstCalculateTask.MINIMUM_THIRST) / 2;
                    int addition = 0;
                    boolean isRelative = false;

                    try {
                        if (args[2].startsWith("~")) {
                            isRelative = true;
                            addition = Integer.parseInt(args[2].substring(1));
                        }
                        else {
                            thirst = Integer.parseInt(args[2]);
                        }
                    } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
                        sendInvalidArgumentMsg(sender);
                        return true;
                    }

                    if (thirst < ThirstCalculateTask.MINIMUM_THIRST) {
                        if (config.getBoolean("Thirst.BelowMinValue.Enabled")) {
                            Map<String, Object> placeholders = Map.of("MINIMUM_VALUE", ThirstCalculateTask.MINIMUM_THIRST);
                            sender.sendMessage(Utils.translateMsg(config.getString("Thirst.BelowMinValue.Message"), sender, placeholders));
                        }
                        return true;
                    }

                    if (thirst > ThirstCalculateTask.MAXIMUM_THIRST) {
                        if (config.getBoolean("Thirst.AboveMaxValue.Enabled")) {
                            Map<String, Object> placeholders = Map.of("MAXIMUM_VALUE", ThirstCalculateTask.MAXIMUM_THIRST);
                            sender.sendMessage(Utils.translateMsg(config.getString("Thirst.AboveMaxValue.Message"), sender, placeholders));
                        }
                        return true;
                    }

                    int oldThirst = manager.getThirst(filteredTargets.get(0));

                    if (isRelative) {
                        int finalAddition = addition;
                        filteredTargets.forEach(player -> manager.addThirst(player, finalAddition));
                    }
                    else {
                        int finalThirst = thirst;
                        filteredTargets.forEach(player -> manager.setThirst(player, finalThirst));
                    }

                    if (config.getBoolean("Thirst.CorrectExecution.Enabled")) {
                        if (filteredTargets.size() == 1) {
                            if (isRelative) {
                                Map<String, Object> placeholders = Map.of("PLAYER_NAME", filteredTargets.get(0).getDisplayName(), "OLD_THIRST", oldThirst, "NEW_THIRST", thirst, "CHANGE", addition);
                                sender.sendMessage(Utils.translateMsg(config.getString("Thirst.CorrectExecution.SingleTargetRelativeMessage"), sender, placeholders));
                            }
                            else {
                                Map<String, Object> placeholders = Map.of("PLAYER_NAME", filteredTargets.get(0).getDisplayName(), "OLD_THIRST", oldThirst, "NEW_THIRST", thirst);
                                sender.sendMessage(Utils.translateMsg(config.getString("Thirst.CorrectExecution.SingleTargetMessage"), sender, placeholders));
                            }

                        }
                        else {
                            if (isRelative) {
                                Map<String, Object> placeholders = Map.of("CHANGE", addition);
                                sender.sendMessage(Utils.translateMsg(config.getString("Thirst.CorrectExecution.MultipleTargetRelativeMessage"), sender, placeholders));
                            }
                            else {
                                Map<String, Object> placeholders = Map.of("NEW_THIRST", thirst);
                                sender.sendMessage(Utils.translateMsg(config.getString("Thirst.CorrectExecution.MultipleTargetMessage"), sender, placeholders));
                            }
                        }
                    }
                    return true;
                }
                case "resetitem" -> {
                    if (!sender.hasPermission("harshlands.command.resetitem")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    if (args.length == 1) {
                        if (sender instanceof Player player) {
                            PlayerInventory inv = player.getInventory();
                            ItemStack itemMainHand = inv.getItemInMainHand();

                            if (HLItem.isHLItem(itemMainHand)) {
                                inv.setItemInMainHand(HLItem.convertItemStackToHLItem(itemMainHand));
                                player.updateInventory();
                                if (config.getBoolean("ResetItem.CorrectExecution.Enabled")) {
                                    sender.sendMessage(Utils.translateMsg(config.getString("ResetItem.CorrectExecution.MainHand.SingleTargetMessage"), sender, null));
                                }
                            }
                            else {
                                if (config.getBoolean("ResetItem.NoValidItemsFound.Enabled")) {
                                    sender.sendMessage(Utils.translateMsg(config.getString("ResetItem.NoValidItemsFound.MainHand.SingleTargetMessage"), sender, null));
                                }
                            }
                            return true;
                        }
                        sendInvalidTargetMsg(sender);
                        return true;
                    }
                    List<Entity> targets = Bukkit.selectEntities(sender, args[1]);

                    if (targets.isEmpty()) {
                        sendInvalidArgumentMsg(sender);
                        return true;
                    }

                    List<Player> filteredTargets = new ArrayList<>();

                    for (Entity e : targets) {
                        if (e instanceof Player player && player.isOnline()) {
                            filteredTargets.add(player);
                        }
                    }

                    if (filteredTargets.isEmpty()) {
                        sendInvalidTargetMsg(sender);
                        return true;
                    }

                    boolean checkEntireInv = args.length >= 3 && args[2].equalsIgnoreCase("all");
                    boolean reset = false;
                    PlayerInventory inv;
                    ItemStack item;

                    for (Player player : filteredTargets) {
                        inv = player.getInventory();

                        if (checkEntireInv) {
                            for (int i = 0; i < inv.getSize(); i++) {
                                item = inv.getItem(i);
                                if (HLItem.isHLItem(item)) {
                                    inv.setItem(i, HLItem.convertItemStackToHLItem(item));
                                    reset = true;
                                }
                            }
                        }
                        else {
                            item = inv.getItemInMainHand();
                            if (HLItem.isHLItem(item)) {
                                inv.setItemInMainHand(HLItem.convertItemStackToHLItem(item));
                                reset = true;
                            }
                        }
                        player.updateInventory();
                    }

                    String execution = reset ? "CorrectExecution" : "NoValidItemsFound";
                    String single = filteredTargets.size() == 1 ? "SingleTargetMessage" : "MultipleTargetMessage";
                    String mainHand = checkEntireInv ? "Inventory" : "MainHand";

                    if (config.getBoolean("ResetItem." + execution + ".Enabled")) {
                        sender.sendMessage(Utils.translateMsg(config.getString("ResetItem." + execution + "." + mainHand + "." + single), sender, null));
                    }
                    return true;
                }
                case "updateitem" -> {
                    if (!sender.hasPermission("harshlands.command.updateitem")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    if (args.length == 1) {
                        if (sender instanceof Player player) {
                            PlayerInventory inv = player.getInventory();
                            ItemStack itemMainHand = inv.getItemInMainHand();

                            if (HLItem.isHLItem(itemMainHand)) {
                                Utils.updateItem(itemMainHand);
                                player.updateInventory();
                                if (config.getBoolean("UpdateItem.CorrectExecution.Enabled")) {
                                    sender.sendMessage(Utils.translateMsg(config.getString("UpdateItem.CorrectExecution.MainHand.SingleTargetMessage"), sender, null));
                                }
                            }
                            else {
                                if (config.getBoolean("UpdateItem.NoValidItemsFound.Enabled")) {
                                    sender.sendMessage(Utils.translateMsg(config.getString("UpdateItem.NoValidItemsFound.MainHand.SingleTargetMessage"), sender, null));
                                }
                            }
                            return true;
                        }
                        sendInvalidTargetMsg(sender);
                        return true;
                    }
                    List<Entity> targets = Bukkit.selectEntities(sender, args[1]);

                    if (targets.isEmpty()) {
                        sendInvalidArgumentMsg(sender);
                        return true;
                    }

                    List<Player> filteredTargets = new ArrayList<>();

                    for (Entity e : targets) {
                        if (e instanceof Player player && player.isOnline()) {
                            filteredTargets.add(player);
                        }
                    }

                    if (filteredTargets.isEmpty()) {
                        sendInvalidTargetMsg(sender);
                        return true;
                    }

                    boolean checkEntireInv = args.length >= 3 && args[2].equalsIgnoreCase("all");
                    boolean reset = false;
                    PlayerInventory inv;
                    ItemStack item;

                    for (Player player : filteredTargets) {
                        inv = player.getInventory();

                        if (checkEntireInv) {
                            for (int i = 0; i < inv.getSize(); i++) {
                                item = inv.getItem(i);
                                if (HLItem.isHLItem(item)) {
                                    Utils.updateItem(item);
                                    reset = true;
                                }
                            }
                        }
                        else {
                            item = inv.getItemInMainHand();
                            if (HLItem.isHLItem(item)) {
                                Utils.updateItem(item);
                                reset = true;
                            }
                        }
                        player.updateInventory();
                    }

                    String execution = reset ? "CorrectExecution" : "NoValidItemsFound";
                    String single = filteredTargets.size() == 1 ? "SingleTargetMessage" : "MultipleTargetMessage";
                    String mainHand = checkEntireInv ? "Inventory" : "MainHand";

                    if (config.getBoolean("UpdateItem." + execution + ".Enabled")) {
                        sender.sendMessage(Utils.translateMsg(config.getString("UpdateItem." + execution + "." + mainHand + "." + single), sender, null));
                    }
                    return true;
                }
                case "fear" -> {
                    if (!sender.hasPermission("harshlands.command.fear")) {
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    HLModule fearMod = HLModule.getModule(FearModule.NAME);
                    if (fearMod == null || !fearMod.isGloballyEnabled()) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.ModuleDisabled", "&c[Harshlands] Fear module is not enabled."), sender, null));
                        return true;
                    }

                    Player target;
                    if (args.length >= 2) {
                        if (!sender.hasPermission("harshlands.command.fear.others")) {
                            sendNoPermissionMessage(sender);
                            return true;
                        }
                        target = Bukkit.getPlayer(args[1]);
                        if (target == null) {
                            sender.sendMessage(Utils.translateMsg(
                                config.getString("Fear.PlayerNotFound", "&cPlayer not found."), sender, null));
                            return true;
                        }
                    } else {
                        if (!(sender instanceof Player)) {
                            sendIncompleteCommandMsg(sender);
                            return true;
                        }
                        target = (Player) sender;
                    }

                    HLPlayer hlTarget = HLPlayer.getPlayers().get(target.getUniqueId());
                    if (hlTarget == null) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.PlayerNotFound", "&cPlayer not found."), sender, null));
                        return true;
                    }

                    cz.hashiri.harshlands.data.fear.DataModule dm = hlTarget.getFearDataModule();
                    if (dm == null) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.ModuleDisabled", "&c[Harshlands] Fear module is not enabled."), sender, null));
                        return true;
                    }

                    sender.sendMessage(Utils.translateMsg(
                        config.getString("Fear.FearLevel", "&6[Harshlands] &f%PLAYER%'s fear: &e%FEAR_LEVEL%"),
                        null,
                        Map.of("PLAYER", target.getName(), "FEAR_LEVEL", String.format("%.2f", dm.getFearLevel()))
                    ));
                    return true;
                }
                case "setfear" -> {
                    if (!sender.hasPermission("harshlands.command.fear.set")) {
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    HLModule fearModSf = HLModule.getModule(FearModule.NAME);
                    if (fearModSf == null || !fearModSf.isGloballyEnabled()) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.ModuleDisabled", "&c[Harshlands] Fear module is not enabled."), sender, null));
                        return true;
                    }

                    if (args.length < 3) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.SetFear.Usage", "&c[Harshlands] Usage: /hl setfear <player> <amount>"), sender, null));
                        return true;
                    }

                    Player sfTarget = Bukkit.getPlayer(args[1]);
                    if (sfTarget == null) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.PlayerNotFound", "&cPlayer not found."), sender, null));
                        return true;
                    }

                    double sfAmount;
                    try {
                        sfAmount = Double.parseDouble(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.SetFear.InvalidAmount", "&c[Harshlands] Amount must be a number between 0 and 100."), sender, null));
                        return true;
                    }

                    if (sfAmount < 0 || sfAmount > 100) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.SetFear.InvalidAmount", "&c[Harshlands] Amount must be a number between 0 and 100."), sender, null));
                        return true;
                    }

                    HLPlayer sfHlTarget = HLPlayer.getPlayers().get(sfTarget.getUniqueId());
                    if (sfHlTarget == null) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.PlayerNotFound", "&cPlayer not found."), sender, null));
                        return true;
                    }

                    cz.hashiri.harshlands.data.fear.DataModule sfDm = sfHlTarget.getFearDataModule();
                    if (sfDm == null) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Fear.ModuleDisabled", "&c[Harshlands] Fear module is not enabled."), sender, null));
                        return true;
                    }

                    sfDm.setFearLevel(sfAmount);
                    plugin.getScheduler().runAsync(() -> sfDm.saveData());

                    sender.sendMessage(Utils.translateMsg(
                        config.getString("Fear.SetFear.Success", "&6[Harshlands] &fSet %PLAYER%'s fear to &e%FEAR_LEVEL%&f."),
                        null,
                        Map.of("PLAYER", sfTarget.getName(), "FEAR_LEVEL", String.format("%.2f", sfAmount))
                    ));
                    return true;
                }
                case "comfort" -> {
                    if (!sender.hasPermission("harshlands.command.comfort")) {
                        sendNoPermissionMessage(sender);
                        return true;
                    }

                    if (!(sender instanceof Player player)) {
                        sendIncompleteCommandMsg(sender);
                        return true;
                    }

                    HLModule comfortMod = HLModule.getModule(ComfortModule.NAME);
                    if (comfortMod == null || !comfortMod.isEnabled(player.getWorld())) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Comfort.ModuleDisabled", "&c[Harshlands] Comfort module is not enabled."), sender, null));
                        return true;
                    }

                    ComfortModule comfortModule = (ComfortModule) comfortMod;
                    ComfortScoreCalculator calc = comfortModule.getCalculator();
                    if (calc == null) {
                        sender.sendMessage(Utils.translateMsg(
                            config.getString("Comfort.ModuleDisabled", "&c[Harshlands] Comfort module is not enabled."), sender, null));
                        return true;
                    }

                    ComfortScoreCalculator.ComfortResult result = calc.calculate(player.getLocation());
                    comfortModule.updateCache(player, result);
                    FileConfiguration comfortConfig = comfortModule.getUserConfig().getConfig();

                    String checkMsg = comfortConfig.getString("Messages.ComfortCheck", "\u00a77Comfort Score: \u00a7f{score} \u00a77({tier})");
                    checkMsg = checkMsg.replace("{score}", String.valueOf(result.getScore()));
                    checkMsg = checkMsg.replace("{tier}", result.getTier().getDisplayName());
                    player.sendMessage(checkMsg);

                    if (!result.getFoundCategories().isEmpty()) {
                        String breakdownMsg = comfortConfig.getString("Messages.ComfortBreakdown", "\u00a77Nearby: \u00a7f{categories}");
                        breakdownMsg = breakdownMsg.replace("{categories}", String.join(", ", result.getFoundCategories()));
                        player.sendMessage(breakdownMsg);
                    }
                    return true;
                }
                case "help" -> {
                    if (!sender.hasPermission("harshlands.command.help")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }
                    config.getStringList("Help").forEach(msg -> sender.sendMessage(Utils.translateMsg(msg, sender, null)));
                    return true;
                }
                case "version" -> {
                    if (!sender.hasPermission("harshlands.command.version")) {
                        // send the player a message explaining that he/she does not have permission to execute the command
                        sendNoPermissionMessage(sender);
                        return true;
                    }
                    Map<String, Object> placeholders = Map.of("PLUGIN_VERSION", plugin.getDescription().getVersion());
                    sender.sendMessage(Utils.translateMsg(config.getString("Version"), sender, placeholders));
                    Bukkit.getServer().dispatchCommand(sender, "version");
                    return true;
                }
                case "debug" -> {
                    if (!sender.hasPermission("harshlands.command.debug")) {
                        sendNoPermissionMessage(sender);
                        return true;
                    }
                    if (!(sender instanceof Player observer)) {
                        sender.sendMessage("\u00a7c[Debug] \u00a7fThis command can only be used by players.");
                        return true;
                    }

                    cz.hashiri.harshlands.debug.DebugManager dm = plugin.getDebugManager();

                    // /hl debug — show status
                    if (args.length == 1) {
                        for (String line : dm.getStatus(observer.getUniqueId())) {
                            observer.sendMessage(line);
                        }
                        return true;
                    }

                    String moduleArg = args[1];

                    // /hl debug off — clear all
                    if (moduleArg.equalsIgnoreCase("off")) {
                        dm.clearAll(observer.getUniqueId());
                        observer.sendMessage("\u00a7c[Debug] \u00a7fDisabled all debug subscriptions");
                        return true;
                    }

                    // Resolve target player (arg 3 if present)
                    UUID targetUuid = null;
                    String targetName = "yourself";
                    if (args.length >= 3) {
                        if (!observer.hasPermission("harshlands.command.debug.others")) {
                            sendNoPermissionMessage(sender);
                            return true;
                        }
                        Player target = Bukkit.getPlayer(args[2]);
                        if (target == null || !target.isOnline()) {
                            observer.sendMessage("\u00a7c[Debug] \u00a7fPlayer '" + args[2] + "' is not online.");
                            return true;
                        }
                        targetUuid = target.getUniqueId();
                        targetName = target.getName();
                    }

                    // /hl debug Everything [player]
                    if (moduleArg.equalsIgnoreCase("Everything")) {
                        boolean enabled = dm.toggleAll(observer.getUniqueId(), targetUuid);
                        if (enabled) {
                            observer.sendMessage("\u00a7a[Debug] \u00a7fEnabled Everything for " + targetName);
                        } else {
                            observer.sendMessage("\u00a7c[Debug] \u00a7fDisabled all debug subscriptions");
                        }
                        return true;
                    }

                    // /hl debug Module[.Subsystem] [player]
                    String moduleName;
                    String subsystem = null;
                    int dotIndex = moduleArg.indexOf('.');
                    if (dotIndex >= 0) {
                        moduleName = moduleArg.substring(0, dotIndex);
                        subsystem = moduleArg.substring(dotIndex + 1);
                    } else {
                        moduleName = moduleArg;
                    }

                    if (!dm.hasProvider(moduleName)) {
                        observer.sendMessage("\u00a7c[Debug] \u00a7fModule '" + moduleName + "' is not enabled or does not exist.");
                        return true;
                    }

                    if (subsystem != null && !dm.getProviders().get(moduleName).getSubsystems().contains(subsystem)) {
                        observer.sendMessage("\u00a7c[Debug] \u00a7fSubsystem '" + subsystem + "' does not exist in module '" + moduleName + "'.");
                        observer.sendMessage("\u00a77Available: " + String.join(", ", dm.getProviders().get(moduleName).getSubsystems()));
                        return true;
                    }

                    boolean enabled = dm.toggle(observer.getUniqueId(), moduleName, subsystem, targetUuid);
                    String debugLabel = subsystem != null ? moduleName + "." + subsystem : moduleName + ".*";
                    if (enabled) {
                        observer.sendMessage("\u00a7a[Debug] \u00a7fEnabled " + debugLabel + " for " + targetName);
                    } else {
                        observer.sendMessage("\u00a7c[Debug] \u00a7fDisabled " + debugLabel + " for " + targetName);
                    }
                    return true;
                }
                case "nutrition" -> {
                    if (!(sender instanceof Player) && args.length < 2) {
                        sender.sendMessage("This command must be run as a player or with a target.");
                        return true;
                    }

                    // /hl nutrition set <player> <protein> <carbs> <fats>
                    if (args.length >= 6 && args[1].equalsIgnoreCase("set")) {
                        if (!sender.hasPermission("harshlands.command.nutrition.set")) {
                            sendNoPermissionMessage(sender);
                            return true;
                        }
                        Player target = Bukkit.getPlayer(args[2]);
                        if (target == null) {
                            sender.sendMessage("\u00a7cPlayer not found.");
                            return true;
                        }
                        try {
                            double protein = Double.parseDouble(args[3]);
                            double carbs = Double.parseDouble(args[4]);
                            double fats = Double.parseDouble(args[5]);
                            cz.hashiri.harshlands.foodexpansion.PlayerNutritionData data = getNutritionData(target);
                            if (data == null) {
                                sender.sendMessage("\u00a7cNutrition module not active for that player.");
                                return true;
                            }
                            data.setProtein(Math.max(0, Math.min(100, protein)));
                            data.setCarbs(Math.max(0, Math.min(100, carbs)));
                            data.setFats(Math.max(0, Math.min(100, fats)));
                            sender.sendMessage("\u00a7aSet nutrition for " + target.getName() + ".");
                        } catch (NumberFormatException e) {
                            sender.sendMessage("\u00a7cInvalid number.");
                        }
                        return true;
                    }

                    // /hl nutrition reset <player>
                    if (args.length >= 3 && args[1].equalsIgnoreCase("reset")) {
                        if (!sender.hasPermission("harshlands.command.nutrition.reset")) {
                            sendNoPermissionMessage(sender);
                            return true;
                        }
                        Player target = Bukkit.getPlayer(args[2]);
                        if (target == null) {
                            sender.sendMessage("\u00a7cPlayer not found.");
                            return true;
                        }
                        cz.hashiri.harshlands.foodexpansion.PlayerNutritionData data = getNutritionData(target);
                        if (data == null) {
                            sender.sendMessage("\u00a7cNutrition module not active for that player.");
                            return true;
                        }
                        cz.hashiri.harshlands.foodexpansion.FoodExpansionModule fem =
                            (cz.hashiri.harshlands.foodexpansion.FoodExpansionModule) HLModule.getModule(cz.hashiri.harshlands.foodexpansion.FoodExpansionModule.NAME);
                        org.bukkit.configuration.file.FileConfiguration feConfig = fem.getUserConfig().getConfig();
                        data.setProtein(feConfig.getDouble("FoodExpansion.Defaults.Protein", 50.0));
                        data.setCarbs(feConfig.getDouble("FoodExpansion.Defaults.Carbs", 50.0));
                        data.setFats(feConfig.getDouble("FoodExpansion.Defaults.Fats", 50.0));
                        sender.sendMessage("\u00a7aReset nutrition for " + target.getName() + ".");
                        return true;
                    }

                    // /hl nutrition [player] — view
                    Player target;
                    if (args.length >= 2) {
                        if (!sender.hasPermission("harshlands.command.nutrition.others")) {
                            sendNoPermissionMessage(sender);
                            return true;
                        }
                        target = Bukkit.getPlayer(args[1]);
                        if (target == null) {
                            sender.sendMessage("\u00a7cPlayer not found.");
                            return true;
                        }
                    } else {
                        if (!sender.hasPermission("harshlands.command.nutrition")) {
                            sendNoPermissionMessage(sender);
                            return true;
                        }
                        target = (Player) sender;
                    }

                    cz.hashiri.harshlands.foodexpansion.PlayerNutritionData data = getNutritionData(target);
                    if (data == null) {
                        sender.sendMessage("\u00a7cNutrition module not active for that player.");
                        return true;
                    }

                    sender.sendMessage("\u00a76--- Nutrition Status ---");
                    sender.sendMessage(buildNutrientBar("Protein", data.getProtein()));
                    sender.sendMessage(buildNutrientBar("Carbs", data.getCarbs()));
                    sender.sendMessage(buildNutrientBar("Fats", data.getFats()));
                    sender.sendMessage("\u00a77Status: " + data.getCachedTier().name().replace("_", " "));
                    return true;
                }
                default -> {
                    return true;
                }
            }
        }
        return false;
    }

    private cz.hashiri.harshlands.foodexpansion.PlayerNutritionData getNutritionData(Player player) {
        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hlPlayer == null) return null;
        cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
        return dm != null ? dm.getData() : null;
    }

    private String buildNutrientBar(String label, double value) {
        int filled = (int) (value / 10.0);
        int empty = 10 - filled;
        String color = value >= 60 ? "\u00a7a" : value >= 30 ? "\u00a7e" : "\u00a7c";

        String bar = "\u2588".repeat(filled) + "\u2591".repeat(empty);
        return color + String.format("%-8s %s %.1f/100", label + ":", bar, value);
    }

    private void sendInvalidTargetMsg(CommandSender sender) {
        sender.sendMessage(Utils.translateMsg(config.getString("InvalidTarget"), sender, null));
    }

    private void sendInvalidArgumentMsg(CommandSender sender) {
        sender.sendMessage(Utils.translateMsg(config.getString("InvalidArgument"), sender, null));
    }

    private void sendIncompleteCommandMsg(CommandSender sender) {
        sender.sendMessage(Utils.translateMsg(config.getString("IncompleteCommand"), sender, null));
    }

    private void sendNoPermissionMessage(CommandSender sender) {
        sender.sendMessage(Utils.translateMsg(config.getString("NoPermission"), sender, null));
    }

    private void playSound(Player player) {
        if (config.getBoolean("Give.CorrectExecution.Sound.Enabled"))
            Utils.playSound(player.getLocation(), config.getString("Give.CorrectExecution.Sound.Sound"), (float) config.getDouble("Give.CorrectExecution.Sound.Volume"), (float) config.getDouble("Give.CorrectExecution.Sound.Pitch"));
    }

}
