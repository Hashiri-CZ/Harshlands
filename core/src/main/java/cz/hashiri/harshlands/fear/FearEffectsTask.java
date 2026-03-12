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
package cz.hashiri.harshlands.fear;

import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.data.fear.DataModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class FearEffectsTask implements Runnable {

    private final HLPlugin plugin;
    private final FileConfiguration config;
    private final FearModule fearModule;

    public FearEffectsTask(HLPlugin plugin, FileConfiguration config, FearModule fearModule) {
        this.plugin = plugin;
        this.config = config;
        this.fearModule = fearModule;
    }

    @Override
    public void run() {
        for (HLPlayer hlPlayer : new ArrayList<>(HLPlayer.getPlayers().values())) {
            Player player = hlPlayer.getPlayer();
            if (player == null || !player.isOnline()) continue;
            if (player.isDead()) continue;
            if (!fearModule.isEnabled(player.getWorld())) continue;
            DataModule dm = hlPlayer.getFearDataModule();
            if (dm == null) continue;
            double fear = dm.getFearLevel();
            applyShaking(player, fear);
            applyFakeMobSounds(player, fear);
            applyHeartbeat(player, fear);
        }
    }

    private void applyShaking(Player player, double fear) {
        if (!config.getBoolean("FearMeter.Effects.Shaking.Enabled", true)) return;
        if (fear < config.getDouble("FearMeter.Effects.Shaking.MinFear", 60.0)) return;
        if (ThreadLocalRandom.current().nextDouble() >= config.getDouble("FearMeter.Effects.Shaking.Chance", 0.08)) return;
        int duration = config.getInt("FearMeter.Effects.Shaking.Duration", 60);
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, duration, 0, true, false));
    }

    private void applyFakeMobSounds(Player player, double fear) {
        if (!config.getBoolean("FearMeter.Effects.FakeMobSounds.Enabled", true)) return;
        if (fear < config.getDouble("FearMeter.Effects.FakeMobSounds.MinFear", 50.0)) return;
        if (ThreadLocalRandom.current().nextDouble() >= config.getDouble("FearMeter.Effects.FakeMobSounds.Chance", 0.05)) return;
        org.bukkit.configuration.ConfigurationSection soundsSection =
            config.getConfigurationSection("FearMeter.Effects.FakeMobSounds.Sounds");
        if (soundsSection == null) return;
        List<String> pool = new ArrayList<>();
        for (String soundName : soundsSection.getKeys(false)) {
            int weight = soundsSection.getInt(soundName, 1);
            for (int i = 0; i < weight; i++) pool.add(soundName);
        }
        if (pool.isEmpty()) return;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        String soundName = pool.get(rng.nextInt(pool.size()));
        Sound sound = Utils.resolveSound(soundName);
        if (sound == null) {
            plugin.getLogger().warning("[Fear] Unknown sound in FakeMobSounds: " + soundName);
            return;
        }
        double maxDist = config.getDouble("FearMeter.Effects.FakeMobSounds.MaxDistance", 20.0);
        Location soundLoc = player.getLocation().clone().add(
            (rng.nextDouble() - 0.5) * 2 * maxDist,
            (rng.nextDouble() - 0.5) * maxDist * 0.5,
            (rng.nextDouble() - 0.5) * 2 * maxDist
        );
        float volume = (float) config.getDouble("FearMeter.Effects.FakeMobSounds.Volume", 1.0);
        float pitch  = (float) config.getDouble("FearMeter.Effects.FakeMobSounds.Pitch", 1.0);
        player.playSound(soundLoc, sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private void applyHeartbeat(Player player, double fear) {
        if (!config.getBoolean("FearMeter.Effects.Heartbeat.Enabled", true)) return;
        if (fear < config.getDouble("FearMeter.Effects.Heartbeat.MinFear", 85.0)) return;
        float volume = (float) config.getDouble("FearMeter.Effects.Heartbeat.Volume", 0.6);
        float pitch  = (float) config.getDouble("FearMeter.Effects.Heartbeat.Pitch", 1.0);
        Sound heartbeat = Utils.resolveSound("ENTITY_WARDEN_HEARTBEAT");
        if (heartbeat == null) {
            return; // sound not available on this server version
        }
        player.playSound(player.getLocation(), heartbeat, SoundCategory.AMBIENT, volume, pitch);
    }
}
