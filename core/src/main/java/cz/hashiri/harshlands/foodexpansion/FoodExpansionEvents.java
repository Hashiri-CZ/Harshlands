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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import cz.hashiri.harshlands.foodexpansion.items.CustomFoodRegistry;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodDefinition;
import cz.hashiri.harshlands.foodexpansion.items.FoodFlag;
import cz.hashiri.harshlands.foodexpansion.items.FoodEffect;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.UUID;

public class FoodExpansionEvents implements Listener {

    private final FoodExpansionModule module;
    private final HLPlugin plugin;

    // Cached config values (M1)
    private final boolean comfortEnabled;
    private final String comfortMinTier;
    private final double comfortAbsorptionBonus;
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
    private final int nauseaThreshold;
    private final int nauseaDurationTicks;

    // Per-player tasks for cleanup on quit
    private final Map<UUID, BukkitTask> decayTasks = new ConcurrentHashMap<>();
    private final Map<UUID, NutritionEffectTask> effectTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> effectBukkitTasks = new ConcurrentHashMap<>();

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
        this.msgWarningText = config.getString("FoodExpansion.Overeating.Messages.WarningText", "&7You're getting tired of &f%food%&7...");
        this.msgSevereThreshold = config.getInt("FoodExpansion.Overeating.Messages.Severe", 3);
        this.msgSevereText = config.getString("FoodExpansion.Overeating.Messages.SevereText", "&7You can barely stomach more &f%food%&7.");
        this.msgBlockedThreshold = config.getInt("FoodExpansion.Overeating.Messages.Blocked", 4);
        this.msgBlockedText = config.getString("FoodExpansion.Overeating.Messages.BlockedText", "&cYou can't eat any more &f%food%&c right now.");
        this.nauseaThreshold = config.getInt("FoodExpansion.Overeating.Nausea.Threshold", 3);
        this.nauseaDurationTicks = config.getInt("FoodExpansion.Overeating.Nausea.DurationTicks", 160);
    }

    // --- Food Consumption ---

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        Material mat = event.getItem().getType();
        if (!mat.isEdible()) return; // Skip potions, milk, ominous bottles

        // Resolve food identity: check PDC first (custom food), fall back to Material (vanilla)
        CustomFoodRegistry cfRegistry = module.getCustomFoodRegistry();
        String itemKey;
        CustomFoodDefinition customDef = null;
        if (cfRegistry != null && cfRegistry.isCustomFood(event.getItem())) {
            itemKey = cfRegistry.getFoodId(event.getItem());
            customDef = cfRegistry.getDefinition(itemKey);
        } else {
            itemKey = mat.name();
        }

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

        // Overeating tracking
        UUID uuid = player.getUniqueId();
        if (overeatingEnabled) {
            String foodKey = itemKey;
            int satiation = data.getSatiation(foodKey);

            // Apply satiation multiplier and messages only for force-eats
            if (forceEatingPlayers.remove(uuid)) {
                forceEatingPreNudgeLevel.remove(uuid);
                double overeatMultiplier = getOvereatMultiplier(satiation);
                multiplier *= overeatMultiplier;
                sendOvereatMessage(player, foodKey, satiation + 1);
                // Apply nausea at high satiation
                if (satiation + 1 >= nauseaThreshold) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDurationTicks, 0, false, true, true));
                }
            }

            // Always increment satiation counter, whether force-eat or normal eat
            data.incrementSatiation(foodKey);
        }

        data.addNutrients(profile, multiplier);

        // Apply custom food effects (potion effects)
        if (customDef != null && customDef.getEffects() != null) {
            for (FoodEffect effect : customDef.getEffects()) {
                if (effect.chance() >= 1.0 || Math.random() < effect.chance()) {
                    player.addPotionEffect(new PotionEffect(
                        effect.type(), effect.durationTicks(), effect.amplifier(), false, true, true));
                }
            }
        }

        // BOWL flag safety: return bowl if base material didn't already
        if (customDef != null && customDef.hasFlag(FoodFlag.BOWL)) {
            Material baseMat = customDef.getBaseMaterial();
            if (baseMat != Material.MUSHROOM_STEW && baseMat != Material.BEETROOT_SOUP
                    && baseMat != Material.RABBIT_STEW && baseMat != Material.SUSPICIOUS_STEW) {
                ItemStack bowl = new ItemStack(Material.BOWL);
                if (!player.getInventory().addItem(bowl).isEmpty()) {
                    player.getWorld().dropItem(player.getLocation(), bowl);
                }
            }
        }
    }

    // --- Overeating: Hunger Nudge ---

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!overeatingEnabled) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() == null) return;

        Player player = event.getPlayer();
        if (!module.isEnabled(player)) return;

        // Get the food item from the hand that triggered the event
        ItemStack item = player.getInventory().getItem(event.getHand());
        if (item == null || !item.getType().isEdible()) return;

        // Resolve food key via PDC for custom foods
        CustomFoodRegistry cfRegistry = module.getCustomFoodRegistry();
        String foodKey;
        boolean isAlwaysEat = false;
        if (cfRegistry != null && cfRegistry.isCustomFood(item)) {
            foodKey = cfRegistry.getFoodId(item);
            CustomFoodDefinition def = cfRegistry.getDefinition(foodKey);
            isAlwaysEat = def != null && def.hasFlag(FoodFlag.ALWAYS_EAT);
        } else {
            foodKey = item.getType().name();
        }

        // Only nudge if hunger is at or above threshold (player can't eat normally)
        // Exception: ALWAYS_EAT foods always get the nudge
        if (player.getFoodLevel() < hungerThreshold && !isAlwaysEat) return;

        UUID uuid = player.getUniqueId();

        // Guard: only one nudge in-flight at a time per player
        if (forceEatingPlayers.contains(uuid)) return;

        // Cooldown check
        long now = System.currentTimeMillis();
        Long lastNudge = forceEatCooldowns.get(uuid);
        if (lastNudge != null && now - lastNudge < cooldownMs) return;

        // Check satiation — if at hard cap, block eating entirely
        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        int satiation = data.getSatiation(foodKey);
        double multiplier = getOvereatMultiplier(satiation);

        if (multiplier <= 0.0) {
            sendOvereatMessage(player, foodKey, satiation);
            return;
        }

        // Nudge hunger down by 1 so vanilla allows eating
        int preNudgeLevel = player.getFoodLevel();
        player.setFoodLevel(preNudgeLevel - 1);

        // Track this force-eat
        forceEatingPlayers.add(uuid);
        forceEatingPreNudgeLevel.put(uuid, preNudgeLevel);
        forceEatCooldowns.put(uuid, now);

        // Timeout cleanup: if PlayerItemConsumeEvent never fires (player cancelled eating),
        // restore hunger and clear flags after 5 seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (forceEatingPlayers.remove(uuid)) {
                Integer preLevel = forceEatingPreNudgeLevel.remove(uuid);
                if (preLevel != null && player.isOnline() && player.getFoodLevel() == preLevel - 1) {
                    player.setFoodLevel(preLevel);
                }
            }
        }, 100L);
    }

    // --- Death / Respawn ---

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!module.isEnabled(player)) return;

        PlayerNutritionData data = getNutritionData(player);
        if (data == null) return;

        data.applyDeathPenalty(deathPenaltyPercent);
        data.clearSatiationCounters();
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

        // Discover all custom food recipes in the recipe book
        discoverCustomFoodRecipes(player);

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
        // Clean up overeating state
        forceEatingPlayers.remove(uuid);
        forceEatingPreNudgeLevel.remove(uuid);
        forceEatCooldowns.remove(uuid);

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
            // Undiscover custom food recipes
            undiscoverCustomFoodRecipes(player);
        } else if (!wasEnabled && nowEnabled) {
            // Entering enabled world — start tasks
            PlayerNutritionData data = getNutritionData(player);
            if (data != null) {
                startTasks(player, data);
            }
            // Discover custom food recipes
            discoverCustomFoodRecipes(player);
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

    // --- Recipe Discovery ---

    private void discoverCustomFoodRecipes(Player player) {
        java.util.List<org.bukkit.NamespacedKey> keys = module.getCustomFoodRecipeKeys();
        if (!keys.isEmpty()) {
            player.discoverRecipes(keys);
        }
    }

    private void undiscoverCustomFoodRecipes(Player player) {
        java.util.List<org.bukkit.NamespacedKey> keys = module.getCustomFoodRecipeKeys();
        if (!keys.isEmpty()) {
            player.undiscoverRecipes(keys);
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
        String foodName;
        CustomFoodRegistry cfRegistry = module.getCustomFoodRegistry();
        if (cfRegistry != null) {
            CustomFoodDefinition def = cfRegistry.getDefinition(foodKey);
            if (def != null) {
                foodName = org.bukkit.ChatColor.stripColor(
                    cz.hashiri.harshlands.utils.Utils.translateMsg(def.getDisplayName(), null, null));
            } else {
                foodName = formatFoodName(foodKey);
            }
        } else {
            foodName = formatFoodName(foodKey);
        }

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
