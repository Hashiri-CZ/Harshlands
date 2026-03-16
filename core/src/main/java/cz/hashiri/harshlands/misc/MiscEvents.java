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
package cz.hashiri.harshlands.misc;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.PlayerJumpEvent;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.Utils;
import cz.hashiri.harshlands.utils.recipe.HLAnvilRecipe;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.Set;

public class MiscEvents implements Listener {

    private final HLPlugin plugin;

    public MiscEvents(HLPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        HLPlayer hlPlayer = HLPlayer.getPlayers().remove(player.getUniqueId());
        if (hlPlayer != null) {
            hlPlayer.saveData();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!HLPlayer.isValidPlayer(player)) {
            HLPlayer rsvplayer = new HLPlayer(player);
            rsvplayer.retrieveData();
        }

        Collection<HLModule> rsvModules = HLModule.getModules().values();

        for (HLModule module : rsvModules) {
            if (module.isGloballyEnabled()) {
                if (module.getAllowedWorlds().contains(player.getWorld().getName())) {
                    Collection<NamespacedKey> keys = module.getModuleRecipes().getRecipeKeys();
                    FileConfiguration config = module.getUserConfig().getConfig();
                    for (NamespacedKey key : keys) {
                        if (config.getBoolean("Recipes." + key.getKey() + ".Unlock")) {
                            Utils.discoverRecipe(player, Bukkit.getRecipe(key));
                        }
                    }
                }
            }
        }

    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        Recipe r = event.getRecipe();

        if (r != null) {
            ItemStack[] matrix = event.getInventory().getMatrix();

            if (event.isRepair()) {

                ItemStack first = null;
                ItemStack second = null;
                boolean firstHLItem = false;
                boolean secondHLItem = false;

                for (ItemStack item : matrix) {
                    if (Utils.isItemReal(item)) {
                        if (first == null) {
                            first = item;
                            firstHLItem = HLItem.isHLItem(item);
                        }
                        else {
                            second = item;
                            secondHLItem = HLItem.isHLItem(item);
                        }
                    }
                    if (!(first == null || second == null)) {
                        break;
                    }
                }

                if (firstHLItem || secondHLItem) {
                    if (firstHLItem && secondHLItem) {
                        if (HLItem.getNameFromItem(first).equals(HLItem.getNameFromItem(second))) {

                            boolean hasCustomDur = Utils.hasCustomDurability(first);
                            int maxDur = hasCustomDur ? Utils.getMaxCustomDurability(first) : Utils.getMaxVanillaDurability(first);

                            int firstDur = hasCustomDur ? Utils.getCustomDurability(first) : Utils.getVanillaDurability(first);
                            int secondDur = hasCustomDur ? Utils.getCustomDurability(second) : Utils.getVanillaDurability(second);

                            int total = (int) Math.min(firstDur + secondDur + Math.floor(maxDur / 20D), maxDur);

                            ItemStack result = HLItem.getItem(HLItem.getNameFromItem(first));
                            Utils.changeDurability(result, total - maxDur, false, false, null);

                            event.getInventory().setResult(result);
                        }
                        else {
                            event.getInventory().setResult(null);
                        }
                    }
                    else {
                        event.getInventory().setResult(null);
                    }
                }
            }
            if (r instanceof Keyed keyed) {
                NamespacedKey key = keyed.getKey();

                if (key.getNamespace().equals(NamespacedKey.MINECRAFT)) {
                    switch (key.getKey()) {
                        case "dark_prismarine", "prismarine", "prismarine_bricks", "sea_lantern" -> {
                            for (ItemStack item : matrix) {
                                if (HLItem.isHLItem(item)) {
                                    event.getInventory().setResult(null);
                                    break;
                                }
                            }
                        }
                        default -> {}
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSmithing(PrepareSmithingEvent event) {
        SmithingInventory inv = event.getInventory();

        if (Utils.isNetheriteRecipe(inv)) {
            ItemStack base;

            // in version 1.21.11, a smithing template slot was added to the smithing table,
            // and the item to be upgraded is now placed in 1st slot instead of the 0th
            if (Utils.getMinecraftVersion(false).compareTo("1.20") >= 0) {
                base = inv.getItem(1);
            }
            else {
                base = inv.getItem(0);
            }

            if (HLItem.isHLItem(base)) {
                String rsvName = HLItem.getNameFromItem(base);
                switch (rsvName) {
                    case "diamond_rapier", "diamond_greatsword", "diamond_longsword", "diamond_spear"
                            , "diamond_saber", "diamond_boomerang", "diamond_dagger", "diamond_glaive", "diamond_halberd"
                            , "diamond_hammer", "diamond_javelin", "diamond_lance", "diamond_mace", "diamond_pike"
                            , "diamond_quarterstaff", "diamond_tomahawk", "diamond_throwing_knife", "diamond_warhammer"
                            , "diamond_battleaxe", "diamond_longbow", "diamond_crossbow", "diamond_knife", "diamond_saw", "diamond_mattock" -> {
                        FileConfiguration userConfig = HLModule.getModule(HLItem.getModuleNameFromItem(base)).getUserConfig().getConfig();

                        if (isEnabledForCurrentVersion(userConfig, "Recipes." + rsvName + ".Enabled"))
                            event.setResult(Utils.getNetheriteRSVWeapon(base));
                        else {
                            if (userConfig.contains("Recipes." + rsvName + ".Enabled.Versions." + Utils.getMinecraftVersion(true))) {
                                if (userConfig.getBoolean("Recipes." + rsvName + ".Enabled.Versions." + Utils.getMinecraftVersion(true)))
                                    event.setResult(Utils.getNetheriteRSVWeapon(base));
                                else
                                    event.setResult(null);
                            }
                            else
                                event.setResult(null);
                        }
                    }
                    default -> event.setResult(null);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDurability(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();

        if (!event.isCancelled()) {
            if (HLItem.isHLItem(item)) {
                if (Utils.hasCustomDurability(item)) {
                    Utils.changeDurability(item, -event.getDamage(), true, true, event.getPlayer());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDurability(PlayerItemMendEvent event) {
        ItemStack item = event.getItem();

        if (!event.isCancelled()) {
            if (HLItem.isHLItem(item)) {
                if (Utils.hasCustomDurability(item)) {
                    int customDif = Utils.getMaxCustomDurability(item) - Utils.getCustomDurability(item);
                    int dif = Utils.getMaxVanillaDurability(item) - Utils.getVanillaDurability(item);
                    Utils.changeDurability(item, (int) Math.ceil(event.getRepairAmount() * (double) customDif / dif), false, false, event.getPlayer());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onIncrementStatistics(PlayerStatisticIncrementEvent event) {
        if (!event.isCancelled()) {
            if (event.getStatistic() == Statistic.JUMP) {
                Player player = event.getPlayer();
                Location loc = player.getLocation(); // get the player's location
                Block block = loc.getBlock(); // get the block at that location

                Material blockMaterial = block.getType(); // get the material of the block
                if (!Tag.CLIMBABLE.isTagged(blockMaterial)) {
                    if (!(block.isLiquid() || player.isRiptiding() || player.isFlying() || player.isSwimming())) {
                        Bukkit.getServer().getPluginManager().callEvent(new PlayerJumpEvent(event.getPlayer()));
                    }
                }
            }
        }
    }

    /**
     * Adds anvil recipes
     * @param event The event called when a player adds items inside an anvil
     * @see Utils
     */
    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory(); // get the anvil inventory
        Set<HLAnvilRecipe> anvilRecipes = plugin.getMiscRecipes().getAnvilRecipes();

        for (HLAnvilRecipe recipe : anvilRecipes) {
            if (recipe.isValidRecipe(inv)) {
                recipe.useRecipe(event);
                break;
            }
        }

        ItemStack result = event.getResult();
        ItemStack first = inv.getItem(0);
        ItemStack second = inv.getItem(1);

        if (HLItem.isHLItem(first) || HLItem.isHLItem(second)) {
            if (Utils.isItemReal(result)) {
                if (HLItem.isHLItem(first)) {
                    if (HLItem.isHLItem(second)) {
                        if (HLItem.getNameFromItem(first).equals(HLItem.getNameFromItem(second))) {
                            if (Utils.hasCustomDurability(first)) {
                                int maxDur = Utils.getMaxCustomDurability(first);
                                int total = Math.min(Utils.getCustomDurability(first) + Utils.getCustomDurability(second), maxDur);

                                if (first.getItemMeta().hasEnchant(Enchantment.SHARPNESS) || second.getItemMeta().hasEnchant(Enchantment.SHARPNESS)) {
                                    Utils.updateDamageLore(result, result.getItemMeta().getEnchants().entrySet());
                                }

                                int resultDur = Math.min(Utils.getCustomDurability(result), maxDur);

                                Utils.changeDurability(result,  -resultDur + total, false, false, null);
                                event.setResult(result);
                            }
                        }
                        else {
                            event.setResult(null);
                        }
                    }
                    else if (Utils.isItemReal(second)) {
                        if (second.getItemMeta() instanceof EnchantmentStorageMeta enchMeta) {
                            if (enchMeta.hasStoredEnchant(Enchantment.SHARPNESS)) {
                                Utils.updateDamageLore(result, result.getItemMeta().getEnchants().entrySet());
                                event.setResult(result);
                            }
                        }
                        else if (HLItem.getItem(HLItem.getNameFromItem(first)).getRepairIng().test(second) && Utils.getDurability(first) < Utils.getMaxDurability(first)) {
                            int change = (int) Math.round(Utils.getMaxDurability(first) * 0.25 * second.getAmount());

                            Utils.changeDurability(result, change, false, false, null);
                            event.setResult(result);
                        }
                        else {
                            event.setResult(null);
                        }
                    }
                }
                else {
                    event.setResult(null);
                }
            }
            else {
                if (HLItem.isHLItem(first) && HLItem.getItem(HLItem.getNameFromItem(first)).getRepairIng().test(second) && Utils.getDurability(first) < Utils.getMaxDurability(first)) {
                    result = first.clone();
                    int change = Utils.getDurability(result);
                    change *= 0.25 * second.getAmount();

                    Utils.changeDurability(result, change, false, false, null);
                    event.setResult(result);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEnchant(EnchantItemEvent event) {
        if (!event.isCancelled()) {
            Utils.updateDamageLore(event.getItem(), event.getEnchantsToAdd().entrySet());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!event.isCancelled()) {
            Player player = event.getPlayer();

            String message = event.getMessage();

            if (message.length() > 1) {
                String[] args = message.substring(1).split(" ");

                if (args[0].equalsIgnoreCase("enchant")) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (Utils.isItemReal(player.getInventory().getItemInMainHand())) {
                                Utils.updateDamageLore(player.getInventory().getItemInMainHand(), player.getInventory().getItemInMainHand().getItemMeta().getEnchants().entrySet());
                            }
                        }
                    }.runTaskLater(plugin, 1L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onServerCommand(ServerCommandEvent event) {
        if (!event.isCancelled()) {
            String message = event.getCommand();

            if (message.length() > 1) {
                String[] args = message.substring(1).split(" ");
                if (args[0].equalsIgnoreCase("enchant")) {
                    if (args.length > 1) {
                        if (!(args[1] == null || args[1].isEmpty())) {
                            Player player = Bukkit.getPlayer(args[1]);
                            if (player != null) {
                            new BukkitRunnable() {
                                @Override
                                public void run() {
                                    if (Utils.isItemReal(player.getInventory().getItemInMainHand())) {
                                        Utils.updateDamageLore(player.getInventory().getItemInMainHand(), player.getInventory().getItemInMainHand().getItemMeta().getEnchants().entrySet());
                                    }
                                }
                            }.runTaskLater(plugin, 1L);
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isEnabledForCurrentVersion(FileConfiguration userConfig, String enabledRoot) {
        String singleVersionPath = enabledRoot + ".Enabled_1_21_11";
        if (userConfig.contains(singleVersionPath)) {
            return userConfig.getBoolean(singleVersionPath);
        }

        // Backward compatibility with older server configs.
        String legacyAllPath = enabledRoot + ".EnableAllVersions";
        if (userConfig.contains(legacyAllPath)) {
            return userConfig.getBoolean(legacyAllPath);
        }

        String versionPath = enabledRoot + ".Versions." + Utils.getMinecraftVersion(true);
        if (userConfig.contains(versionPath)) {
            return userConfig.getBoolean(versionPath);
        }

        return true;
    }
}


