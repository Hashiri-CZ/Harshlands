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
package cz.hashiri.harshlands.comfort;

import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ComfortModule extends HLModule {

    public static final String NAME = "Comfort";

    @Nonnull
    private final HLPlugin plugin;

    @Nullable
    private ComfortScoreCalculator calculator;

    @Nullable
    private ComfortEvents events;

    @Nullable
    private CabinFeverSubsystem cabinFeverSubsystem;

    @Nullable
    private CabinFeverEvents cabinFeverEvents;

    private final Map<UUID, CachedComfortResult> papiCache = new ConcurrentHashMap<>();

    public ComfortModule(@Nonnull HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "Settings/comfort.yml"));

        HLPlugin.getPlugin().getDebugManager().registerProvider(new cz.hashiri.harshlands.debug.DebugProvider() {
            @Override public String getModuleName() { return NAME; }
            @Override public java.util.Collection<String> getSubsystems() { return java.util.List.of("Score", "CabinFever"); }
        });

        FileConfiguration config = getUserConfig().getConfig();

        if (config.getBoolean("Initialize.Enabled")) {
            Utils.logModuleInit("comfort", NAME);
        }

        calculator = new ComfortScoreCalculator(config, plugin.getLogger());
        events = new ComfortEvents(this, plugin, calculator, config);
        Bukkit.getPluginManager().registerEvents(events, plugin);

        if (config.getBoolean("CabinFever.Enabled", false)) {
            cabinFeverSubsystem = new CabinFeverSubsystem(plugin, config);
            cabinFeverSubsystem.initialize();
            cabinFeverEvents = new CabinFeverEvents(cabinFeverSubsystem, this);
            Bukkit.getPluginManager().registerEvents(cabinFeverEvents, plugin);
        }
    }

    @Override
    public void shutdown() {
        FileConfiguration config = getUserConfig() != null ? getUserConfig().getConfig() : null;
        if (config != null && config.getBoolean("Shutdown.Enabled")) {
            Utils.logModuleShutdown("comfort", NAME);
        }

        if (cabinFeverSubsystem != null) {
            cabinFeverSubsystem.shutdown();
            cabinFeverSubsystem = null;
        }

        if (cabinFeverEvents != null) {
            HandlerList.unregisterAll(cabinFeverEvents);
            cabinFeverEvents = null;
        }

        if (events != null) {
            HandlerList.unregisterAll(events);
            events = null;
        }

        papiCache.clear();
        calculator = null;
    }

    @Nullable
    public ComfortScoreCalculator getCalculator() {
        return calculator;
    }

    /**
     * Returns a cached comfort result for PAPI performance.
     * If called from an async thread, only returns the cached value (never calculates,
     * since World.getBlockAt() is not thread-safe). The cache is populated from sync
     * context in onBedEnter and /hl comfort command.
     */
    @Nullable
    public ComfortScoreCalculator.ComfortResult getCachedResult(@Nonnull Player player, int cacheSeconds) {
        if (calculator == null) {
            return null;
        }

        UUID uuid = player.getUniqueId();
        CachedComfortResult cached = papiCache.get(uuid);
        long now = System.currentTimeMillis();

        if (cached != null && (now - cached.timestampMillis) < (cacheSeconds * 1000L)) {
            return cached.result;
        }

        // Only calculate on the main thread — block reads are not thread-safe
        if (!Bukkit.isPrimaryThread()) {
            return cached != null ? cached.result : null;
        }

        ComfortScoreCalculator.ComfortResult result = calculator.calculate(player.getLocation());
        papiCache.put(uuid, new CachedComfortResult(result, now));
        return result;
    }

    /**
     * Updates the PAPI cache from sync context (called from onBedEnter).
     */
    public void updateCache(@Nonnull Player player, @Nonnull ComfortScoreCalculator.ComfortResult result) {
        papiCache.put(player.getUniqueId(), new CachedComfortResult(result, System.currentTimeMillis()));
    }

    public void clearCacheFor(@Nonnull UUID uuid) {
        papiCache.remove(uuid);
    }

    @Nullable
    public CabinFeverSubsystem getCabinFeverSubsystem() {
        return cabinFeverSubsystem;
    }

    private static class CachedComfortResult {
        final ComfortScoreCalculator.ComfortResult result;
        final long timestampMillis;

        CachedComfortResult(@Nonnull ComfortScoreCalculator.ComfortResult result, long timestampMillis) {
            this.result = result;
            this.timestampMillis = timestampMillis;
        }
    }
}
