package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.comfort.ComfortModule;
import cz.hashiri.harshlands.comfort.ComfortScoreCalculator;
import cz.hashiri.harshlands.comfort.ComfortTier;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.BossbarHUD;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FoodExpansionEvents implements Listener {

    private final FoodExpansionModule module;
    private final HLPlugin plugin;

    // Per-player tasks for cleanup on quit
    private final Map<UUID, BukkitTask> decayTasks = new HashMap<>();
    private final Map<UUID, NutritionEffectTask> effectTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> effectBukkitTasks = new HashMap<>();

    public FoodExpansionEvents(FoodExpansionModule module, HLPlugin plugin) {
        this.module = module;
        this.plugin = plugin;
    }

    // --- Food Consumption ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        String itemKey = event.getItem().getType().name();
        NutrientProfile profile = module.getNutrientProfile(itemKey);
        if (profile == null) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        // Comfort bonus
        double multiplier = 1.0;
        FileConfiguration config = module.getUserConfig().getConfig();
        if (config.getBoolean("FoodExpansion.Comfort.Enabled", true)) {
            ComfortModule cm = (ComfortModule) HLModule.getModule(ComfortModule.NAME);
            if (cm != null && cm.isGloballyEnabled()) {
                ComfortScoreCalculator.ComfortResult result = cm.getCachedResult(player, 60);
                if (result != null) {
                    String minTierStr = config.getString("FoodExpansion.Comfort.MinTier", "HOME");
                    try {
                        ComfortTier minTier = ComfortTier.valueOf(minTierStr.toUpperCase());
                        if (result.getTier().ordinal() >= minTier.ordinal()) {
                            multiplier = 1.0 + config.getDouble("FoodExpansion.Comfort.AbsorptionBonus", 0.10);
                        }
                    } catch (IllegalArgumentException ignored) {
                        // Invalid tier name in config — skip bonus
                    }
                }
            }
        }

        data.addNutrients(profile, multiplier);
    }

    // --- Vanilla Hunger Slowdown ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!module.isEnabled(player)) return;

        int oldLevel = player.getFoodLevel();
        int newLevel = event.getFoodLevel();

        // Only slow down hunger drain, not eating
        if (newLevel >= oldLevel) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        double drainMultiplier = module.getUserConfig().getConfig()
            .getDouble("FoodExpansion.VanillaHunger.DrainMultiplier", 0.5);

        int decrease = oldLevel - newLevel;
        double scaledDebt = decrease * drainMultiplier;
        data.addHungerDebt(scaledDebt);

        if (data.getHungerDebtAccumulator() >= 1.0) {
            int actualDecrease = (int) Math.floor(data.getHungerDebtAccumulator());
            data.setHungerDebtAccumulator(data.getHungerDebtAccumulator() - actualDecrease);
            event.setFoodLevel(oldLevel - actualDecrease);
        } else {
            event.setCancelled(true);
        }
    }

    // --- Death / Respawn ---

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!module.isEnabled(player)) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        double percentLoss = module.getUserConfig().getConfig()
            .getDouble("FoodExpansion.DeathPenalty.PercentLoss", 25.0);
        data.applyDeathPenalty(percentLoss);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        // Re-apply attribute modifiers (death clears them)
        // Delay by 1 tick to ensure player is fully respawned
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            NutritionEffectTask effectTask = effectTasks.get(player.getUniqueId());
            if (effectTask != null) {
                PlayerNutritionData data = getNutritionData(player);
                if (data != null) {
                    effectTask.applyModifiers(data.getCachedTier());
                }
            }
        }, 1L);
    }

    // --- Join / Quit ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        // Data loading is handled by HLPlayer.retrieveData() which calls our DataModule.
        // We start tasks after a short delay to allow async DB load to complete.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;

            PlayerNutritionData data = getNutritionData(player);
            if (data == null) return;

            startTasks(player, data);
        }, 20L); // 1 second delay for DB load
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        stopTasks(uuid);

        // Save if dirty (handled by HLPlayer.saveData() in main quit flow)
    }

    // --- Activity Detection ---

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data != null) {
            data.setMiningFlag();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!module.isEnabled(player)) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data != null) {
            data.setFightingFlag();
        }
    }

    // --- World Change ---

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        boolean wasEnabled = module.isEnabled(event.getFrom());
        boolean nowEnabled = module.isEnabled(player.getWorld());

        if (wasEnabled && !nowEnabled) {
            // Entering disabled world — stopTasks() handles modifier removal and HUD cleanup
            stopTasks(uuid);
        } else if (!wasEnabled && nowEnabled) {
            // Entering enabled world — start tasks
            PlayerNutritionData data = getNutritionData(player);
            if (data != null) {
                startTasks(player, data);
            }
        }
    }

    // --- Task Management ---

    private void startTasks(Player player, PlayerNutritionData data) {
        UUID uuid = player.getUniqueId();

        // Don't double-start
        if (decayTasks.containsKey(uuid)) return;

        FileConfiguration config = module.getUserConfig().getConfig();

        // Get or create BossbarHUD for this player
        BossbarHUD hud = module.getOrCreateHud(player);

        NutritionDecayTask decayTask = new NutritionDecayTask(player, data, config);
        BukkitTask decayBukkit = decayTask.runTaskTimer(plugin, 100L, 100L);
        decayTasks.put(uuid, decayBukkit);

        NutritionEffectTask effectTask = new NutritionEffectTask(player, data, hud, config);
        BukkitTask effectBukkit = effectTask.runTaskTimer(plugin, 40L, 40L);
        effectTasks.put(uuid, effectTask);
        effectBukkitTasks.put(uuid, effectBukkit);
    }

    public void stopTasks(UUID uuid) {
        BukkitTask decay = decayTasks.remove(uuid);
        if (decay != null) decay.cancel();

        NutritionEffectTask effect = effectTasks.remove(uuid);
        if (effect != null) {
            effect.removeAllModifiers();
            effect.removeHudElements();
        }

        BukkitTask effectBukkit = effectBukkitTasks.remove(uuid);
        if (effectBukkit != null) effectBukkit.cancel();

        module.removeHud(uuid);
    }

    public void stopAllTasks() {
        for (UUID uuid : new java.util.ArrayList<>(decayTasks.keySet())) {
            stopTasks(uuid);
        }
    }

    // --- Utility ---

    private PlayerNutritionData getNutritionData(Player player) {
        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hlPlayer == null) return null;
        cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
        return dm != null ? dm.getData() : null;
    }
}
