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

import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.utils.HLTask;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ThirstEffectsTask extends BukkitRunnable implements HLTask {

    public enum Tier {
        DEHYDRATED, PARCHED, THIRSTY
    }

    private static final Map<UUID, ThirstEffectsTask> tasks = new ConcurrentHashMap<>();

    private final ThirstManager manager;
    private final UUID id;
    private final FileConfiguration config;
    private final HLPlugin plugin;
    private final HLPlayer player;
    private final Collection<String> allowedWorlds;
    private final TanModule module;

    private final int tickPeriod;
    private final double thirstyThreshold;
    private final double parchedThreshold;
    private final double dehydratedThreshold;

    private final EnumMap<Tier, List<PotionEffect>> effectsByTier;

    private final boolean damageEnabled;
    private final double damage;
    private final double damageCutoff;

    public ThirstEffectsTask(TanModule module, HLPlugin plugin, HLPlayer player) {
        this.plugin = plugin;
        this.manager = module.getThirstManager();
        this.module = module;
        this.config = module.getUserConfig().getConfig();
        this.player = player;
        this.id = player.getPlayer().getUniqueId();
        this.allowedWorlds = module.getAllowedWorlds();

        this.tickPeriod = config.getInt("Thirst.Effects.TickPeriod");
        int durationBuffer = config.getInt("Thirst.Effects.EffectDurationBuffer");
        int effectDuration = tickPeriod + durationBuffer;

        this.thirstyThreshold = config.getDouble("Thirst.Effects.Tiers.Thirsty.Threshold");
        this.parchedThreshold = config.getDouble("Thirst.Effects.Tiers.Parched.Threshold");
        this.dehydratedThreshold = config.getDouble("Thirst.Effects.Tiers.Dehydrated.Threshold");

        this.effectsByTier = new EnumMap<>(Tier.class);
        effectsByTier.put(Tier.THIRSTY, loadTierEffects("Thirst.Effects.Tiers.Thirsty.PotionEffects", effectDuration));
        effectsByTier.put(Tier.PARCHED, loadTierEffects("Thirst.Effects.Tiers.Parched.PotionEffects", effectDuration));
        effectsByTier.put(Tier.DEHYDRATED, loadTierEffects("Thirst.Effects.Tiers.Dehydrated.PotionEffects", effectDuration));

        this.damageEnabled = config.getBoolean("Thirst.Effects.Tiers.Dehydrated.Damage.Enabled");
        this.damage = config.getDouble("Thirst.Effects.Tiers.Dehydrated.Damage.Amount");
        this.damageCutoff = config.getDouble("Thirst.Effects.Tiers.Dehydrated.Damage.Cutoff");

        tasks.put(id, this);
    }

    private List<PotionEffect> loadTierEffects(String path, int effectDuration) {
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) return Collections.emptyList();
        Set<String> keys = section.getKeys(false);
        if (keys.isEmpty()) return Collections.emptyList();
        List<PotionEffect> out = new ArrayList<>(keys.size());
        for (String key : keys) {
            int amplifier = section.getInt(key + ".Amplifier");
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(key.toLowerCase()));
            if (type == null) {
                plugin.getLogger().warning("Unknown potion effect type in thirst tier config: " + key);
                continue;
            }
            out.add(new PotionEffect(type, effectDuration, amplifier));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * Pure tier selector. Checks highest severity first so misconfigured thresholds
     * (e.g. parched > thirsty) still pick the most severe matching tier instead of
     * falling through.
     *
     * @return the active tier, or {@code null} if the player's thirst is above all tier thresholds.
     */
    static Tier selectTier(double thirst, double dehydratedThreshold, double parchedThreshold, double thirstyThreshold) {
        if (thirst <= dehydratedThreshold) return Tier.DEHYDRATED;
        if (thirst <= parchedThreshold) return Tier.PARCHED;
        if (thirst <= thirstyThreshold) return Tier.THIRSTY;
        return null;
    }

    @Override
    public void run() {
        Player bukkitPlayer = this.player.getPlayer();

        if (!conditionsMet(bukkitPlayer)) {
            stop();
            return;
        }

        double thirst = manager.getThirst(bukkitPlayer);
        Tier active = selectTier(thirst, dehydratedThreshold, parchedThreshold, thirstyThreshold);
        if (active == null) {
            stop();
            return;
        }

        if (!bukkitPlayer.hasPermission("harshlands.toughasnails.resistance.thirst.potioneffects")) {
            List<PotionEffect> effects = effectsByTier.get(active);
            if (effects != null && !effects.isEmpty()) {
                bukkitPlayer.addPotionEffects(effects);
            }
        }

        if (active == Tier.DEHYDRATED
                && !bukkitPlayer.hasPermission("harshlands.toughasnails.resistance.thirst.damage")
                && damageEnabled
                && bukkitPlayer.getHealth() >= damageCutoff) {
            if (bukkitPlayer.getHealth() - damage <= 0) {
                module.getDehydrationDeath().add(id);
            }
            bukkitPlayer.damage(damage);
        }
    }

    @Override
    public boolean conditionsMet(@Nullable Player bukkitPlayer) {
        return globalConditionsMet(bukkitPlayer)
                && !bukkitPlayer.isDead()
                && allowedWorlds.contains(bukkitPlayer.getWorld().getName())
                && manager.getThirst(bukkitPlayer) <= thirstyThreshold;
    }

    @Override
    public void start() {
        this.runTaskTimer(plugin, 0L, tickPeriod);
    }

    @Override
    public void stop() {
        tasks.remove(id);
        cancel();
    }

    public static boolean hasTask(UUID id) {
        return tasks.get(id) != null;
    }

    public static Map<UUID, ThirstEffectsTask> getTasks() {
        return tasks;
    }
}
