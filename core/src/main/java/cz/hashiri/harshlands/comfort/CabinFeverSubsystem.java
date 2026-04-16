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

import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.data.cabinfever.DataModule;
import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.locale.Messages;
import cz.hashiri.harshlands.tan.ThirstCalculateTask;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class CabinFeverSubsystem {

    private static final NamespacedKey SPEED_MODIFIER_KEY = new NamespacedKey("harshlands", "cabin_fever_slow");

    private final HLPlugin plugin;
    private final FileConfiguration config;
    private final Logger logger;
    private final int checkInterval;
    private final int roofCheckHeight;
    private final Set<World.Environment> exemptEnvironments = new HashSet<>();
    private final long outdoorResetTicks;
    private final long thresholdRestless;
    private final long thresholdRisk;
    private final long thresholdFull;
    private final long comfortDelayTicks;
    private final long cureTicks;
    private final double speedPenalty;
    private final int nauseaRiskInterval;
    private final int nauseaFullInterval;
    private final int nauseaDuration;
    private final double thirstMultiplier;

    private final Map<UUID, Long> lastNauseaTick = new ConcurrentHashMap<>();
    private long tickCounter = 0;
    private BukkitTask mainTask;
    private BukkitTask autoSaveTask;

    public CabinFeverSubsystem(@Nonnull HLPlugin plugin, @Nonnull FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();

        this.checkInterval = config.getInt("CabinFever.CheckIntervalTicks", 600);
        this.roofCheckHeight = config.getInt("CabinFever.RoofCheckHeight", 4);

        for (String env : config.getStringList("CabinFever.ExemptEnvironments")) {
            try {
                exemptEnvironments.add(World.Environment.valueOf(env));
            } catch (IllegalArgumentException e) {
                logger.warning("[CabinFever] Unknown environment: " + env);
            }
        }

        this.outdoorResetTicks = config.getLong("CabinFever.OutdoorResetTicks", 6000);
        this.thresholdRestless = config.getLong("CabinFever.Thresholds.Restless", 48000);
        this.thresholdRisk = config.getLong("CabinFever.Thresholds.Risk", 72000);
        this.thresholdFull = config.getLong("CabinFever.Thresholds.Full", 96000);
        this.comfortDelayTicks = config.getLong("CabinFever.ComfortDelayTicks", 24000);
        this.cureTicks = config.getLong("CabinFever.CureTicks", 24000);
        this.speedPenalty = config.getDouble("CabinFever.Effects.SpeedPenalty", -0.05);
        this.nauseaRiskInterval = config.getInt("CabinFever.Effects.Nausea.RiskIntervalTicks", 6000);
        this.nauseaFullInterval = config.getInt("CabinFever.Effects.Nausea.FullIntervalTicks", 2400);
        this.nauseaDuration = config.getInt("CabinFever.Effects.Nausea.DurationTicks", 40);
        this.thirstMultiplier = config.getDouble("CabinFever.Effects.ThirstExhaustionMultiplier", 1.3);
    }

    public void initialize() {
        mainTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter += checkInterval;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    processPlayer(player);
                }
            }
        }.runTaskTimer(plugin, checkInterval, checkInterval);

        autoSaveTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Map.Entry<UUID, HLPlayer> entry : HLPlayer.getPlayers().entrySet()) {
                    DataModule dm = entry.getValue().getCabinFeverDataModule();
                    if (dm != null && dm.isDirty()) {
                        dm.saveData();
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 6000, 6000);

    }

    private void processPlayer(@Nonnull Player player) {
        if (player.isDead()) return;
        GameMode gm = player.getGameMode();
        if (gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;
        if (player.hasPermission("harshlands.comfort.cabinfever.bypass")) return;
        if (exemptEnvironments.contains(player.getWorld().getEnvironment())) return;

        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hlPlayer == null) return;
        DataModule dm = hlPlayer.getCabinFeverDataModule();
        if (dm == null) return;

        boolean indoors = isIndoors(player);

        if (indoors) {
            dm.setIndoorTicks(dm.getIndoorTicks() + checkInterval);
            dm.setOutdoorTicks(0);
        } else {
            dm.setOutdoorTicks(dm.getOutdoorTicks() + checkInterval);
            if (dm.getOutdoorTicks() >= outdoorResetTicks) {
                dm.setIndoorTicks(0);
            }
        }

        // Comfort delay from last sleep tier
        long comfortDelay = 0;
        String tier = dm.getLastComfortTier();
        if ("COZY".equals(tier) || "LUXURY".equals(tier)) {
            comfortDelay = comfortDelayTicks;
        }

        long effectiveIndoor = dm.getIndoorTicks() - comfortDelay;
        CabinFeverStage stage = evaluateStage(effectiveIndoor);

        cz.hashiri.harshlands.debug.DebugManager debugMgr = plugin.getDebugManager();
        if (debugMgr.isActive("Comfort", "CabinFever", player.getUniqueId())) {
            String chatLine = String.format("\u00a76[Comfort.CF] \u00a7f%s: %s indoor=%d outdoor=%d comfortDelay=%d",
                player.getName(), stage, dm.getIndoorTicks(), dm.getOutdoorTicks(), comfortDelay);
            String consoleLine = String.format("indoor=%d outdoor=%d effective=%d stage=%s indoors=%s comfortDelay=%d lastTier=%s feverActive=%s",
                dm.getIndoorTicks(), dm.getOutdoorTicks(), effectiveIndoor, stage, indoors, comfortDelay, dm.getLastComfortTier(), dm.isCabinFeverActive());
            debugMgr.send("Comfort", "CabinFever", player.getUniqueId(), chatLine, consoleLine);
        }

        // Cure check
        if (dm.isCabinFeverActive() && dm.getOutdoorTicks() >= cureTicks) {
            cure(player, dm);
            return;
        }

        applyEffects(player, dm, stage);
    }

    private boolean isIndoors(@Nonnull Player player) {
        int eyeY = player.getEyeLocation().getBlockY();
        World world = player.getWorld();
        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();

        for (int dy = 1; dy <= roofCheckHeight; dy++) {
            if (world.getBlockAt(x, eyeY + dy, z).getType().isOccluding()) {
                return true;
            }
        }
        return false;
    }

    private CabinFeverStage evaluateStage(long effectiveIndoor) {
        if (effectiveIndoor >= thresholdFull) return CabinFeverStage.FULL;
        if (effectiveIndoor >= thresholdRisk) return CabinFeverStage.RISK;
        if (effectiveIndoor >= thresholdRestless) return CabinFeverStage.RESTLESS;
        return CabinFeverStage.NONE;
    }

    private void applyEffects(@Nonnull Player player, @Nonnull DataModule dm, @Nonnull CabinFeverStage stage) {
        UUID uuid = player.getUniqueId();

        if (stage == CabinFeverStage.NONE) {
            removeSpeedModifier(player);
            updateThirstTask(uuid, false);
            if (dm.isCabinFeverActive()) {
                dm.setCabinFeverActive(false);
            }
            return;
        }

        // RESTLESS and above: speed debuff + action bar warning
        applySpeedModifier(player);
        String stageMsg = Messages.get("comfort.cabin_fever.messages." + stage.name().toLowerCase());
        ((Audience) player).sendActionBar(LegacyComponentSerializer.legacySection().deserialize(stageMsg));

        if (stage == CabinFeverStage.RISK || stage == CabinFeverStage.FULL) {
            int nauseaInterval = (stage == CabinFeverStage.FULL) ? nauseaFullInterval : nauseaRiskInterval;
            long lastNausea = lastNauseaTick.getOrDefault(uuid, 0L);
            long now = tickCounter;
            if (now - lastNausea >= nauseaInterval) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 0, true, false, true));
                lastNauseaTick.put(uuid, now);
            }
        }

        if (stage == CabinFeverStage.FULL) {
            dm.setCabinFeverActive(true);
            updateThirstTask(uuid, true);

            // Apply full effects from config
            ConfigurationSection fullEffects = config.getConfigurationSection("CabinFever.Effects.FullEffects");
            if (fullEffects != null) {
                for (String effectKey : fullEffects.getKeys(false)) {
                    int amplifier = fullEffects.getInt(effectKey, 0);
                    PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(effectKey.toLowerCase()));
                    if (type != null) {
                        player.addPotionEffect(new PotionEffect(type, 660, amplifier, true, false, true));
                    }
                }
            }
        } else {
            if (dm.isCabinFeverActive()) {
                // Downgraded from FULL — remove full-stage effects
                dm.setCabinFeverActive(false);
                updateThirstTask(uuid, false);
                removeFullEffects(player);
            }
        }
    }

    private void applySpeedModifier(@Nonnull Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;

        // Check if modifier already exists
        for (AttributeModifier mod : attr.getModifiers()) {
            if (SPEED_MODIFIER_KEY.equals(mod.getKey())) {
                return; // already applied
            }
        }

        attr.addModifier(new AttributeModifier(
            SPEED_MODIFIER_KEY, speedPenalty,
            AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY
        ));
    }

    private void removeSpeedModifier(@Nonnull Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) return;

        for (AttributeModifier mod : attr.getModifiers()) {
            if (SPEED_MODIFIER_KEY.equals(mod.getKey())) {
                attr.removeModifier(mod);
                return;
            }
        }
    }

    private void removeFullEffects(@Nonnull Player player) {
        ConfigurationSection fullEffects = config.getConfigurationSection("CabinFever.Effects.FullEffects");
        if (fullEffects == null) return;

        for (String effectKey : fullEffects.getKeys(false)) {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(effectKey.toLowerCase()));
            if (type != null) {
                player.removePotionEffect(type);
            }
        }
    }

    private void updateThirstTask(@Nonnull UUID uuid, boolean active) {
        ThirstCalculateTask task = ThirstCalculateTask.getTasks().get(uuid);
        if (task != null) {
            task.setCabinFeverActive(active);
        }
    }

    private void cure(@Nonnull Player player, @Nonnull DataModule dm) {
        dm.setIndoorTicks(0);
        dm.setOutdoorTicks(0);
        dm.setCabinFeverActive(false);

        removeSpeedModifier(player);
        removeFullEffects(player);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        updateThirstTask(player.getUniqueId(), false);
        lastNauseaTick.remove(player.getUniqueId());

        Messages.of("comfort.cabin_fever.messages.cured").send(player);

        cz.hashiri.harshlands.debug.DebugManager debugMgr = plugin.getDebugManager();
        if (debugMgr.isActive("Comfort", "CabinFever", player.getUniqueId())) {
            debugMgr.send("Comfort", "CabinFever", player.getUniqueId(),
                "\u00a76[Comfort.CF] \u00a7f" + player.getName() + ": CURED",
                "action=CURE outdoor=" + dm.getOutdoorTicks());
        }
    }

    public void removeEffects(@Nonnull Player player) {
        removeSpeedModifier(player);
        removeFullEffects(player);
        player.removePotionEffect(PotionEffectType.NAUSEA);
        lastNauseaTick.remove(player.getUniqueId());
        updateThirstTask(player.getUniqueId(), false);
    }

    public void resetPlayer(@Nonnull Player player) {
        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hlPlayer == null) return;
        DataModule dm = hlPlayer.getCabinFeverDataModule();
        if (dm == null) return;

        dm.setIndoorTicks(0);
        dm.setOutdoorTicks(0);
        dm.setCabinFeverActive(false);
        removeEffects(player);
    }

    public boolean isResetOnDeath() {
        return config.getBoolean("CabinFever.ResetOnDeath", false);
    }

    @Nonnull
    public CabinFeverStage getPlayerStage(@Nonnull Player player) {
        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hlPlayer == null) return CabinFeverStage.NONE;
        DataModule dm = hlPlayer.getCabinFeverDataModule();
        if (dm == null) return CabinFeverStage.NONE;

        long comfortDelay = 0;
        String tier = dm.getLastComfortTier();
        if ("COZY".equals(tier) || "LUXURY".equals(tier)) {
            comfortDelay = comfortDelayTicks;
        }

        return evaluateStage(dm.getIndoorTicks() - comfortDelay);
    }

    public void shutdown() {
        if (mainTask != null) {
            mainTask.cancel();
            mainTask = null;
        }
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
            autoSaveTask = null;
        }

        // Clean up effects from all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeEffects(player);
        }

        lastNauseaTick.clear();
    }

    public enum CabinFeverStage {
        NONE, RESTLESS, RISK, FULL
    }
}
