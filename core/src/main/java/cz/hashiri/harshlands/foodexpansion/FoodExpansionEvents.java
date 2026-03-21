package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.comfort.ComfortModule;
import cz.hashiri.harshlands.comfort.ComfortScoreCalculator;
import cz.hashiri.harshlands.comfort.ComfortTier;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.data.foodexpansion.DataModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FoodExpansionEvents implements Listener {

    private final FoodExpansionModule module;
    private final HLPlugin plugin;

    // Cached config values (M1)
    private final boolean comfortEnabled;
    private final String comfortMinTier;
    private final double comfortAbsorptionBonus;
    private final double hungerDrainMultiplier;
    private final double deathPenaltyPercent;
    private final boolean overeatingEnabled;
    private final int hungerThreshold;
    private final long cooldownMs;
    private final NavigableMap<Integer, Double> satiationTiers;
    private final int msgWarningThreshold;
    private final String msgWarningText;
    private final int msgSevereThreshold;
    private final String msgSevereText;
    private final int msgBlockedThreshold;
    private final String msgBlockedText;

    // Per-player tasks for cleanup on quit
    private final Map<UUID, BukkitTask> decayTasks = new HashMap<>();
    private final Map<UUID, NutritionEffectTask> effectTasks = new HashMap<>();
    private final Map<UUID, BukkitTask> effectBukkitTasks = new HashMap<>();

    // Overeating state
    private final Set<UUID> forceEatingPlayers = new HashSet<>();
    private final Map<UUID, Integer> forceEatingPreNudgeLevel = new HashMap<>();
    private final Map<UUID, Long> forceEatCooldowns = new HashMap<>();

    public FoodExpansionEvents(FoodExpansionModule module, HLPlugin plugin) {
        this.module = module;
        this.plugin = plugin;

        FileConfiguration config = module.getUserConfig().getConfig();
        this.comfortEnabled = config.getBoolean("FoodExpansion.Comfort.Enabled", true);
        this.comfortMinTier = config.getString("FoodExpansion.Comfort.MinTier", "HOME");
        this.comfortAbsorptionBonus = config.getDouble("FoodExpansion.Comfort.AbsorptionBonus", 0.10);
        this.hungerDrainMultiplier = config.getDouble("FoodExpansion.VanillaHunger.DrainMultiplier", 0.5);
        this.deathPenaltyPercent = config.getDouble("FoodExpansion.DeathPenalty.PercentLoss", 25.0);

        this.overeatingEnabled = config.getBoolean("FoodExpansion.Overeating.Enabled", true);
        this.hungerThreshold = config.getInt("FoodExpansion.Overeating.HungerThreshold", 20);
        this.cooldownMs = config.getLong("FoodExpansion.Overeating.CooldownMs", 500L);

        // Load satiation tiers into a TreeMap for descending iteration
        this.satiationTiers = new TreeMap<>();
        org.bukkit.configuration.ConfigurationSection tierSection = config.getConfigurationSection("FoodExpansion.Overeating.Tiers");
        if (tierSection != null) {
            for (String key : tierSection.getKeys(false)) {
                try {
                    satiationTiers.put(Integer.parseInt(key), tierSection.getDouble(key));
                } catch (NumberFormatException ignored) {}
            }
        } else {
            satiationTiers.put(0, 1.0);
            satiationTiers.put(1, 0.7);
            satiationTiers.put(2, 0.4);
            satiationTiers.put(3, 0.15);
            satiationTiers.put(4, 0.0);
        }

        this.msgWarningThreshold = config.getInt("FoodExpansion.Overeating.Messages.Warning", 2);
        this.msgWarningText = config.getString("FoodExpansion.Overeating.Messages.WarningText", "&7You're getting tired of &f{food}&7...");
        this.msgSevereThreshold = config.getInt("FoodExpansion.Overeating.Messages.Severe", 3);
        this.msgSevereText = config.getString("FoodExpansion.Overeating.Messages.SevereText", "&7You can barely stomach more &f{food}&7.");
        this.msgBlockedThreshold = config.getInt("FoodExpansion.Overeating.Messages.Blocked", 4);
        this.msgBlockedText = config.getString("FoodExpansion.Overeating.Messages.BlockedText", "&cYou can't eat any more &f{food}&c right now.");
    }

    // --- Food Consumption ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        Material mat = event.getItem().getType();
        if (!mat.isEdible()) return; // Skip potions, milk, ominous bottles

        String itemKey = mat.name();
        NutrientProfile profile = module.getNutrientProfile(itemKey);
        if (profile == null) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        // Comfort bonus
        double multiplier = 1.0;
        if (comfortEnabled) {
            ComfortModule cm = (ComfortModule) HLModule.getModule(ComfortModule.NAME);
            if (cm != null && cm.isGloballyEnabled()) {
                ComfortScoreCalculator.ComfortResult result = cm.getCachedResult(player, 60);
                if (result != null) {
                    try {
                        ComfortTier minTier = ComfortTier.valueOf(comfortMinTier.toUpperCase());
                        if (result.getTier().ordinal() >= minTier.ordinal()) {
                            multiplier = 1.0 + comfortAbsorptionBonus;
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

        int decrease = oldLevel - newLevel;
        double scaledDebt = decrease * hungerDrainMultiplier;
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

        data.applyDeathPenalty(deathPenaltyPercent);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        // Re-apply attribute modifiers (death clears them)
        // Delay by 1 tick to ensure player is fully respawned
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!module.isEnabled(player)) return;
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

        // Poll every 10 ticks (0.5s) up to 100 ticks (5s) for data readiness
        new BukkitRunnable() {
            private int attempts = 0;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                attempts++;
                DataModule dm = getDataModule(player);
                if (dm != null && dm.isDataReady()) {
                    cancel();
                    startTasks(player, dm.getData());
                } else if (attempts >= 10) {
                    cancel();
                    plugin.getLogger().warning("Nutrition data load timed out for " + player.getName());
                    PlayerNutritionData data = getNutritionData(player);
                    if (data != null) startTasks(player, data);
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
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

        NutritionDecayTask decayTask = new NutritionDecayTask(player, data, config);
        BukkitTask decayBukkit = decayTask.runTaskTimer(plugin, 100L, 100L);
        decayTasks.put(uuid, decayBukkit);

        NutritionEffectTask effectTask = new NutritionEffectTask(player, data, module, config);
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

    // --- Overeating Helpers ---

    private double getOvereatMultiplier(int satiationCount) {
        for (Map.Entry<Integer, Double> entry : satiationTiers.descendingMap().entrySet()) {
            if (satiationCount >= entry.getKey()) {
                return entry.getValue();
            }
        }
        return 1.0;
    }

    private void sendOvereatMessage(Player player, String foodKey, int satiationCount) {
        String foodName = formatFoodName(foodKey);
        String msg = null;
        if (satiationCount >= msgBlockedThreshold) {
            msg = msgBlockedText;
        } else if (satiationCount >= msgSevereThreshold) {
            msg = msgSevereText;
        } else if (satiationCount >= msgWarningThreshold) {
            msg = msgWarningText;
        }
        if (msg != null) {
            player.sendMessage(cz.hashiri.harshlands.utils.Utils.translateMsg(
                msg, player, java.util.Map.of("food", foodName)));
        }
    }

    private static String formatFoodName(String materialName) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    // --- Utility ---

    private DataModule getDataModule(Player player) {
        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hlPlayer == null) return null;
        return hlPlayer.getNutritionDataModule();
    }

    private PlayerNutritionData getNutritionData(Player player) {
        DataModule dm = getDataModule(player);
        return dm != null ? dm.getData() : null;
    }
}
