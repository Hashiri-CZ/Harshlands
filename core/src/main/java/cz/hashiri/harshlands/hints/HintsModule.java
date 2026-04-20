/*
    Copyright (C) 2026  Hashiri_
    GNU GPL v3.
 */
package cz.hashiri.harshlands.hints;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Map;

public class HintsModule extends HLModule {

    public static final String NAME = "Hints";

    private final HLPlugin plugin;

    public HintsModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "Settings/hints.yml"));
        FileConfiguration config = getUserConfig().getConfig();

        if (!config.getBoolean("Enabled", true)) {
            return;
        }

        if (config.getBoolean("Initialize.Enabled", true)) {
            Utils.logModuleInit("hints", NAME);
        }

        HintSender sender = new HintSender(plugin, this);
        HintsListener listener = new HintsListener(this, sender);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // Periodic save every 5 min (6000 ticks), dirty players only — matches TAN/Fear pattern
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (HLPlayer hlPlayer : new ArrayList<>(HLPlayer.getPlayers().values())) {
                cz.hashiri.harshlands.data.hints.DataModule dm = hlPlayer.getHintsDataModule();
                if (dm != null && dm.isDirty()) dm.saveData();
            }
        }, 6000L, 6000L);
    }

    @Override
    public void shutdown() {
        // No per-module resources to release — player data is flushed by HLPlugin.onDisable
    }
}
