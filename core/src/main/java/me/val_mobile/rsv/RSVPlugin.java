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
package me.val_mobile.rsv;

import me.val_mobile.baubles.BaubleModule;
import me.val_mobile.commands.Commands;
import me.val_mobile.commands.Tab;
import me.val_mobile.data.*;
import me.val_mobile.fear.FearModule;
import me.val_mobile.iceandfire.IceFireModule;
import me.val_mobile.integrations.PAPI;
import me.val_mobile.integrations.AuraSkills;
import me.val_mobile.integrations.RealisticSeasons;
import me.val_mobile.misc.*;
import me.val_mobile.ntp.NtpModule;
import me.val_mobile.spartanandfire.SfModule;
import me.val_mobile.spartanweaponry.SwModule;
import me.val_mobile.tan.TanModule;
import me.val_mobile.utils.ToolHandler;
import me.val_mobile.utils.ToolUtils;
import me.val_mobile.utils.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

public class RSVPlugin extends JavaPlugin {

    public static final String NAME = "RealisticSurvival";

    private static RSVPlugin plugin;
    private static Utils util;
    private ToolUtils toolUtils;
    private ToolHandler toolHandler;
    private PluginConfig config;
    private static RSVConfig lorePresetConfig;
    private RSVConfig miscItemsConfig;
    private RSVConfig miscRecipesConfig;
    private MiscRecipes miscRecipes;
    private MiscItems miscItems;
    private RSVConfig integrationsConfig;
    private RSVConfig commandsConfig;
    private RSVConfig auraSkillsRequirementsConfig;

//    private static RSVConfig langConfig;

    @Override
    public void onEnable() {
        plugin = this;
        this.config = new PluginConfig(this);

        lorePresetConfig = new RSVConfig(this, "lorepresets.yml");
        this.miscItemsConfig = new RSVConfig(this, "resources/misc_items.yml");
        this.miscRecipesConfig = new RSVConfig(this, "resources/misc_recipes.yml");
        this.integrationsConfig = new RSVConfig(this, "integrations.yml");
        this.commandsConfig = new RSVConfig(this, "commands.yml");
        this.auraSkillsRequirementsConfig = new RSVConfig(this, "auraskills_requirements.yml");
        migrateAuraSkillsRequirementsConfig();
        ensureIntegrationDefaults();

        util = new Utils(this);

        new UpdateChecker(this, 93795).checkUpdate();

        PluginManager pm = this.getServer().getPluginManager();

        this.toolHandler = new ToolHandler();
        this.toolUtils = new ToolUtils(this);
        this.toolUtils.initMap();
        ensureFearDefaults();

        this.miscItems = new MiscItems(this);
        this.miscRecipes = new MiscRecipes(this);

        IceFireModule ifModule = new IceFireModule(this);
        if (ifModule.isGloballyEnabled())
            ifModule.initialize();

        SwModule swModule = new SwModule(this);
        if (swModule.isGloballyEnabled())
            swModule.initialize();

        BaubleModule baubleModule = new BaubleModule(this);
        if (baubleModule.isGloballyEnabled()) {
            baubleModule.initialize();
        }

        NtpModule ntpModule = new NtpModule(this);
        if (ntpModule.isGloballyEnabled())
            ntpModule.initialize();

        SfModule sfModule = new SfModule(this);
        if (sfModule.isGloballyEnabled()) {
            sfModule.initialize();
        }

        TanModule tanModule = new TanModule(this);
        if (tanModule.isGloballyEnabled())
            tanModule.initialize();

        FearModule fearModule = new FearModule(this);
        if (fearModule.isGloballyEnabled()) {
            fearModule.initialize();
        }

//        FaModule faModule = new FaModule(this);
//        if (faModule.isGloballyEnabled())
//            faModule.initialize();

//        TODO: Add custom enchantment system
//        RSVEnchants rsvEnchants = new RSVEnchants(this);
//        rsvEnchants.registerAllEnchants();

        new BukkitRunnable() {
            @Override
            public void run() {
                RealisticSeasons rs = new RealisticSeasons(getPlugin());
                PAPI papi = new PAPI(getPlugin());
                AuraSkills auraSkills = new AuraSkills(getPlugin());
            }
        }.runTaskLater(this, 1L);

        if (config.getConfig().getBoolean("ResourcePack.Enabled"))
            pm.registerEvents(new ResourcePackEvents(this), this);

        if (config.getConfig().getBoolean("BStats"))
            new BStats(this).recordData();

        pm.registerEvents(new MiscEvents(this), this);
        pm.registerEvents(new ItemAcquireEvents(this), this);

        this.getCommand(NAME).setExecutor(new Commands(this));
        this.getCommand(NAME).setTabCompleter(new Tab(this));
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        Collection<RSVPlayer> players = RSVPlayer.getPlayers().values();
        Collection<RSVModule> modules = RSVModule.getModules().values();

        for (RSVPlayer player : players) {
            player.saveData();
        }

        for (RSVModule module : modules) {
            if (module.isGloballyEnabled()) {
                module.shutdown();
            }
        }
    }

    @Override
    public void onLoad() {
//        Utils.registerEntities();
    }

    @Override
    public FileConfiguration getConfig() {
        return config == null ? null : config.getConfig();
    }

    @Nonnull
    public static RSVPlugin getPlugin() {
        return plugin;
    }

    @Nonnull
    public static Utils getUtil() {
        return util;
    }

    @Nonnull
    public File getConfigFile() {
        return config.getFile();
    }

    @Nonnull
    public static FileConfiguration getLorePresetConfig() {
        return lorePresetConfig.getConfig();
    }

    @Nonnull
    public FileConfiguration getMiscItemsConfig() {
        return miscItemsConfig.getConfig();
    }

    @Nonnull
    public FileConfiguration getMiscRecipesConfig() {
        return miscRecipesConfig.getConfig();
    }

    @Nonnull
    public FileConfiguration getIntegrationsConfig() {
        return integrationsConfig.getConfig();
    }

    @Nonnull
    public FileConfiguration getCommandsConfig() {
        return commandsConfig.getConfig();
    }

    @Nonnull
    public FileConfiguration getAuraSkillsRequirementsConfig() {
        return auraSkillsRequirementsConfig.getConfig();
    }

    @Nonnull
    public MiscItems getMiscItems() {
        return miscItems;
    }

    @Nonnull
    public MiscRecipes getMiscRecipes() {
        return miscRecipes;
    }

    @Nonnull
    public ToolHandler getToolHandler() {
        return toolHandler;
    }

    @Nonnull
    public ToolUtils getToolUtils() {
        return toolUtils;
    }

    private void ensureFearDefaults() {
        FileConfiguration cfg = getConfig();
        boolean changed = false;

        if (!cfg.contains("Fear.Enabled")) {
            cfg.set("Fear.Enabled", true);
            changed = true;
        }

        String worldsPath = "Fear.Worlds";
        if (!cfg.contains(worldsPath)) {
            cfg.createSection(worldsPath);
            changed = true;
        }

        boolean autoEnableWorlds = cfg.getBoolean("AutomaticallyEnableWorlds");
        for (String world : Utils.getAllWorldNames()) {
            String path = worldsPath + "." + world;
            if (!cfg.contains(path)) {
                cfg.set(path, autoEnableWorlds);
                changed = true;
            }
        }

        if (changed) {
            try {
                cfg.save(getConfigFile());
            } catch (IOException exception) {
                getLogger().warning("Failed to write Fear defaults to config.yml: " + exception.getMessage());
            }
        }
    }

    private void ensureIntegrationDefaults() {
        if (integrationsConfig == null) {
            return;
        }

        FileConfiguration cfg = integrationsConfig.getConfig();
        boolean changed = false;

        String requirementsMessagePath = "AuraSkills.Requirements.Message";
        if (!cfg.contains(requirementsMessagePath)) {
            cfg.set(requirementsMessagePath, "&cYou need %SKILL% level %REQUIRED_LEVEL% to %ACTION% %ITEM%. Current level: %CURRENT_LEVEL%.");
            changed = true;
        }

        if (changed) {
            try {
                cfg.save(integrationsConfig.getFile());
                integrationsConfig.reloadConfig();
            } catch (IOException exception) {
                getLogger().warning("Failed to write AuraSkills integration defaults: " + exception.getMessage());
            }
        }
    }

    private void migrateAuraSkillsRequirementsConfig() {
        if (auraSkillsRequirementsConfig == null) {
            return;
        }

        FileConfiguration cfg = auraSkillsRequirementsConfig.getConfig();
        boolean changed = false;

        // Migrate legacy grouped roots:
        // Crafting:
        //   item:
        //     FARMING: 5
        // Using:
        //   item:
        //     FARMING: 0
        ConfigurationSection legacyCrafting = cfg.getConfigurationSection("Crafting");
        if (legacyCrafting != null) {
            for (String item : legacyCrafting.getKeys(false)) {
                ConfigurationSection itemSection = legacyCrafting.getConfigurationSection(item);
                if (itemSection == null) {
                    continue;
                }
                for (String skill : itemSection.getKeys(false)) {
                    String path = item + ".crafting." + skill;
                    if (!cfg.contains(path)) {
                        cfg.set(path, itemSection.getInt(skill));
                        changed = true;
                    }
                }
            }
            cfg.set("Crafting", null);
            changed = true;
        }

        ConfigurationSection legacyUsing = cfg.getConfigurationSection("Using");
        if (legacyUsing != null) {
            for (String item : legacyUsing.getKeys(false)) {
                ConfigurationSection itemSection = legacyUsing.getConfigurationSection(item);
                if (itemSection == null) {
                    continue;
                }
                for (String skill : itemSection.getKeys(false)) {
                    String path = item + ".using." + skill;
                    if (!cfg.contains(path)) {
                        cfg.set(path, itemSection.getInt(skill));
                        changed = true;
                    }
                }
            }
            cfg.set("Using", null);
            changed = true;
        }

        // Migrate inline legacy entries:
        // item:
        //   FARMING: 5
        // into:
        // item:
        //   crafting:
        //     FARMING: 5
        //   using:
        //     FARMING: 5
        for (String key : cfg.getKeys(false)) {
            if ("ConfigId".equals(key) || "Crafting".equals(key) || "Using".equals(key)) {
                continue;
            }

            ConfigurationSection itemSection = cfg.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }

            for (String child : itemSection.getKeys(false)) {
                if ("crafting".equalsIgnoreCase(child) || "using".equalsIgnoreCase(child)) {
                    continue;
                }

                Object raw = itemSection.get(child);
                if (!(raw instanceof Number number)) {
                    continue;
                }

                int level = number.intValue();
                String craftingPath = key + ".crafting." + child;
                String usingPath = key + ".using." + child;

                if (!cfg.contains(craftingPath)) {
                    cfg.set(craftingPath, level);
                    changed = true;
                }
                if (!cfg.contains(usingPath)) {
                    cfg.set(usingPath, level);
                    changed = true;
                }

                cfg.set(key + "." + child, null);
                changed = true;
            }
        }

        if (changed) {
            try {
                cfg.save(auraSkillsRequirementsConfig.getFile());
                auraSkillsRequirementsConfig.reloadConfig();
            } catch (IOException exception) {
                getLogger().warning("Failed to migrate auraskills_requirements.yml: " + exception.getMessage());
            }
        }
    }

}

