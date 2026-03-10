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
package cz.hashiri.harshlands.iceandfire;

import cz.hashiri.harshlands.data.ModuleItems;
import cz.hashiri.harshlands.data.ModuleRecipes;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.spartanandfire.BurnTask;
import cz.hashiri.harshlands.spartanandfire.ElectrocuteTask;
import cz.hashiri.harshlands.spartanandfire.FreezeTask;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;

public class IceFireModule extends HLModule {

    private final HLPlugin plugin;

    public static final String NAME = "IceandFire";
    private IceFireEvents events;


    public IceFireModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "iceandfire.yml"));
        setItemConfig(new HLConfig(plugin, "resources/iceandfire/items.yml"));
        setRecipeConfig(new HLConfig(plugin, "resources/iceandfire/recipes.yml"));
        setModuleItems(new ModuleItems(this));
        setModuleRecipes(new ModuleRecipes(this, plugin));

        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Initialize.Enabled")) {
            String message = Utils.translateMsg(config.getString("Initialize.Message"), null, Map.of("NAME", NAME));

            plugin.getLogger().info(message);
        }

        events = new IceFireEvents(this, plugin);

        getModuleItems().initialize();
        getModuleRecipes().initialize();
        events.initialize();
    }

    @Override
    public void shutdown() {
        FileConfiguration config = getUserConfig().getConfig();
        if (config.getBoolean("Shutdown.Enabled")) {
            String message = Utils.translateMsg(config.getString("Shutdown.Message"), null, Map.of("NAME", NAME));

            plugin.getLogger().info(message);
        }
    }

    public IceFireEvents getEvents() {
        return events;
    }

    public static double applyDragonItemBonusDamage(@Nullable Entity defender, @Nullable ItemStack item, double origDamage, @Nonnull HLModule compare) {
        if (!(HLItem.isHLItem(item) && HLItem.getModuleNameFromItem(item).equals(compare.getName())))
            return origDamage;

        return applyDragonItemBonusDamage(defender, HLItem.getNameFromItem(item), origDamage, compare);
    }

    public static double applyDragonItemBonusDamage(@Nullable Entity defender, @Nullable String name, double origDamage, @Nonnull HLModule compare) {
        if (compare.getUserConfig() == null || defender == null || name == null || name.isEmpty() || name.startsWith("_") || name.endsWith("_"))
            return origDamage;

        if (!(HLItem.isHLItem(name) && compare.getName().equals(HLItem.getModuleNameFromItem(HLItem.getItem(name)))))
            return origDamage;

        FileConfiguration config = compare.getUserConfig().getConfig();

        String type = name.substring(0, name.lastIndexOf("_"));
        String weaponType = name.substring(name.lastIndexOf("_") + 1);

        if (weaponType.equals("longbow") || weaponType.equals("crossbow")) {
            origDamage *= config.getDouble("Items." + name + ".AttackDamageMultiplier");
        }

        String prefix = switch (type) {
            case "dragonbone_flamed" -> "fire";
            case "dragonbone_iced" -> "ice";
            case "dragonbone_lightning" -> "lightning";
            default -> null;
        };

        if (prefix == null)
            return origDamage;

        if (Utils.hasNbtTag(defender, "rsvmob") && !Utils.getNbtTag(defender, "rsvmob", PersistentDataType.STRING).equals(prefix + "_dragon")) {
            origDamage += config.getDouble("Items." + name + ".DragonBonusDamage");
        }

        return origDamage;
    }

    public static void applyDragonItemEffect(@Nullable Entity defender, @Nullable ItemStack item, @Nonnull HLModule compare) {
        if (!(HLItem.isHLItem(item) && HLItem.getModuleNameFromItem(item).equals(compare.getName())))
            return;

        applyDragonItemEffect(defender, HLItem.getNameFromItem(item), compare);
    }

    public static void applyDragonItemEffect(@Nullable Entity defender, @Nullable String name, @Nonnull HLModule compare) {
        if (compare.getUserConfig() == null || defender == null || name == null || name.isEmpty() || name.startsWith("_") || name.endsWith("_"))
            return;

        if (!(HLItem.isHLItem(name) && compare.getName().equals(HLItem.getModuleNameFromItem(HLItem.getItem(name)))))
            return;

        FileConfiguration config = compare.getUserConfig().getConfig();
        HLPlugin plugin = HLPlugin.getPlugin();

        String type = name.substring(0, name.lastIndexOf("_"));

        switch (type) {
            case "dragonbone_flamed", "dragonsteel_fire" -> {
                if (!BurnTask.hasTask(defender.getUniqueId())) {
                    int fireTicks = config.getInt("Items." + name + ".InfernoAbility.FireTicks");
                    int tickPeriod = config.getInt("Items." + name + ".InfernoAbility.TickPeriod");

                    new BurnTask(plugin, defender, fireTicks, tickPeriod).start();
                }
            }
            case "dragonbone_iced", "dragonsteel_ice" -> {
                if (!FreezeTask.hasTask(defender.getUniqueId()) && config.getString("Items." + name + ".FreezeAbility.EncaseIce.Block") != null) {
                    new FreezeTask(plugin, compare, name, defender).start();
                }
            }
            case "dragonbone_lightning", "dragonsteel_lightning" -> {
                if (config.getBoolean("Items." + name + ".ElectrocuteAbility.SummonLightning.Enabled")) {
                    Location loc = defender.getLocation();
                    if (config.getBoolean("Items." + name + ".ElectrocuteAbility.SummonLightning.Cosmetic")) {
                        loc.getWorld().strikeLightningEffect(loc);
                    } else {
                        loc.getWorld().strikeLightning(loc);
                    }
                }

                if (defender instanceof Damageable damageable && !ElectrocuteTask.hasTask(defender.getUniqueId())) {
                    new ElectrocuteTask(plugin, compare, name, damageable).start();
                }
            }
            default -> {}
        }
    }
}

