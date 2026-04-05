package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.AboveActionBarHUD;
import cz.hashiri.harshlands.utils.BossbarHUD;
import cz.hashiri.harshlands.utils.DisplayTask;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import cz.hashiri.harshlands.commands.Tab;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodRegistry;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodRecipes;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodDrops;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class FoodExpansionModule extends HLModule {

    public static final String NAME = "FoodExpansion";

    private final HLPlugin plugin;
    private final Map<String, NutrientProfile> foodMap = new HashMap<>();
    private NutrientProfile defaultFood;
    private FoodExpansionEvents events;
    private CustomFoodRegistry customFoodRegistry;
    private CustomFoodRecipes customFoodRecipes;
    private CustomFoodDrops customFoodDrops;

    // Per-player BossbarHUD references (may be shared with DisplayTask from TAN)
    private final Map<UUID, BossbarHUD> playerHuds = new HashMap<>();
    private final Map<UUID, AboveActionBarHUD> standaloneAboveActionBarHuds = new HashMap<>();
    private BukkitTask autoSaveTask;
    private BukkitTask satiationDecayTask;

    // Shared NamespacedKeys for attribute modifiers (created once, used by all NutritionEffectTasks)
    private NamespacedKey keyMaxHealth;
    private NamespacedKey keySpeed;
    private NamespacedKey keyAttack;
    private NamespacedKey keyMining;

    public FoodExpansionModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of()); // No hard deps, soft deps handled at runtime
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "foodexpansion.yml"));
        Utils.logModuleLifecycle("Initializing", NAME);

        // Create shared attribute modifier keys
        keyMaxHealth = new NamespacedKey(plugin, "nutrition_max_health");
        keySpeed = new NamespacedKey(plugin, "nutrition_speed");
        keyAttack = new NamespacedKey(plugin, "nutrition_attack");
        keyMining = new NamespacedKey(plugin, "nutrition_mining");

        // Register debug provider
        HLPlugin.getPlugin().getDebugManager().registerProvider(new cz.hashiri.harshlands.debug.DebugProvider() {
            @Override public String getModuleName() { return NAME; }
            @Override public java.util.Collection<String> getSubsystems() {
                return List.of("Decay", "Effects", "Tiers");
            }
        });

        loadFoodMap();

        // Custom foods
        FileConfiguration feConfig = getUserConfig().getConfig();
        ConfigurationSection customFoodsSec = feConfig.getConfigurationSection("FoodExpansion.CustomFoods");
        customFoodRegistry = new CustomFoodRegistry(customFoodsSec, plugin.getLogger());

        // Register custom food macros into the shared food map
        for (cz.hashiri.harshlands.foodexpansion.items.CustomFoodDefinition def : customFoodRegistry.getAllDefinitions()) {
            if (def.getMacros() != null) {
                foodMap.put(def.getId().toLowerCase(), def.getMacros());
            }
        }

        // Register custom food IDs for /hl give tab completion
        Tab.addItemIds(customFoodRegistry.getAllIds());

        ConfigurationSection bonusRecipesSec = feConfig.getConfigurationSection("FoodExpansion.BonusRecipes");
        customFoodRecipes = new CustomFoodRecipes(customFoodRegistry, this, plugin, customFoodsSec, bonusRecipesSec);
        Bukkit.getPluginManager().registerEvents(customFoodRecipes, plugin);

        ConfigurationSection mobDropsSec = feConfig.getConfigurationSection("FoodExpansion.MobDrops");
        customFoodDrops = new CustomFoodDrops(customFoodRegistry, this, mobDropsSec, plugin.getLogger());
        Bukkit.getPluginManager().registerEvents(customFoodDrops, plugin);

        // NOTE: Decay and effect tasks cache config values at construction.
        // A server reload (/hl reload) will NOT update values for currently online players.
        // Players must rejoin for new config values to take effect.
        events = new FoodExpansionEvents(this, plugin);
        Bukkit.getPluginManager().registerEvents(events, plugin);

        // Auto-save every 5 minutes (6000 ticks), SYNC timer so saveData() snapshots on main thread
        // (the actual DB write inside saveData() is already async via HLScheduler)
        autoSaveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (HLPlayer hlPlayer : new java.util.ArrayList<>(HLPlayer.getPlayers().values())) {
                cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
                if (dm != null && dm.isDirty()) {
                    dm.saveData();
                }
            }
        }, 6000L, 6000L);

        // Satiation decay timer — decrements all per-food satiation counters every N minutes
        long decayIntervalTicks = feConfig.getLong("FoodExpansion.Overeating.DecayIntervalMinutes", 3) * 60 * 20;
        satiationDecayTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (HLPlayer hlPlayer : new java.util.ArrayList<>(HLPlayer.getPlayers().values())) {
                cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hlPlayer.getNutritionDataModule();
                if (dm != null) {
                    dm.getData().decaySatiationCounters();
                }
            }
        }, decayIntervalTicks, decayIntervalTicks);
    }

    @Override
    public void shutdown() {
        if (autoSaveTask != null) { autoSaveTask.cancel(); autoSaveTask = null; }
        if (satiationDecayTask != null) { satiationDecayTask.cancel(); satiationDecayTask = null; }
        if (events != null) {
            events.stopAllTasks(); // This removes modifiers and HUD elements for all players
            HandlerList.unregisterAll(events);
        }
        if (customFoodRecipes != null) {
            HandlerList.unregisterAll(customFoodRecipes);
            customFoodRecipes.shutdown();
        }
        if (customFoodDrops != null) {
            HandlerList.unregisterAll(customFoodDrops);
        }
        standaloneAboveActionBarHuds.clear();
        playerHuds.clear();
    }

    private void loadFoodMap() {
        FileConfiguration config = getUserConfig().getConfig();

        // Load default food profile
        double defP = config.getDouble("FoodExpansion.DefaultFood.Protein", 2.0);
        double defC = config.getDouble("FoodExpansion.DefaultFood.Carbs", 2.0);
        double defF = config.getDouble("FoodExpansion.DefaultFood.Fats", 1.0);
        this.defaultFood = new NutrientProfile(defP, defC, defF);

        // Load per-food profiles
        ConfigurationSection foods = config.getConfigurationSection("FoodExpansion.Foods");
        if (foods != null) {
            for (String key : foods.getKeys(false)) {
                double protein = foods.getDouble(key + ".Protein", 0.0);
                double carbs = foods.getDouble(key + ".Carbs", 0.0);
                double fats = foods.getDouble(key + ".Fats", 0.0);
                foodMap.put(key.toLowerCase(), new NutrientProfile(protein, carbs, fats));
            }
        }
    }

    public NutrientProfile getNutrientProfile(String itemKey) {
        NutrientProfile profile = foodMap.get(itemKey.toLowerCase());
        if (profile != null) return profile;

        // Return default for unlisted foods
        if (defaultFood.protein() == 0.0 && defaultFood.carbs() == 0.0 && defaultFood.fats() == 0.0) {
            return null; // Default is all zeros — treat as "no nutrition data"
        }
        return defaultFood;
    }

    /**
     * Retrieves the BossbarHUD for a player. Always checks TAN's DisplayTask first
     * to avoid duplicate boss bars if TAN starts after FoodExpansion.
     * Only creates a standalone HUD if TAN is genuinely absent.
     */
    public BossbarHUD getOrCreateHud(Player player) {
        UUID uuid = player.getUniqueId();

        // Always prefer TAN's HUD if available (it may have been created after our first call)
        DisplayTask dt = DisplayTask.getTasks().get(uuid);
        if (dt != null) {
            BossbarHUD tanHud = dt.getBossbarHud();
            // If we previously created a standalone HUD, clean it up
            BossbarHUD oldHud = playerHuds.remove(uuid);
            if (oldHud != null && oldHud != tanHud) {
                oldHud.hide();
            }
            return tanHud;
        }

        // No TAN — create/reuse our own standalone HUD
        return playerHuds.computeIfAbsent(uuid, u -> {
            BossbarHUD hud = new BossbarHUD((net.kyori.adventure.audience.Audience) player);
            hud.show();
            return hud;
        });
    }

    /**
     * Retrieves the AboveActionBarHUD for a player. Checks TAN's DisplayTask first,
     * falls back to creating a standalone instance wrapping the player's BossbarHUD.
     */
    public AboveActionBarHUD getOrCreateAboveActionBarHud(Player player) {
        UUID uuid = player.getUniqueId();
        DisplayTask dt = DisplayTask.getTasks().get(uuid);
        if (dt != null) {
            return dt.getAboveActionBarHud();
        }
        // No TAN — create one wrapping our standalone BossbarHUD
        BossbarHUD hud = getOrCreateHud(player);
        FileConfiguration feConfig = getUserConfig().getConfig();
        int centerX = feConfig.getInt("FoodExpansion.HUD.IconCenterX", 65);
        int iconW = feConfig.getInt("FoodExpansion.HUD.IconWidth", 32);
        return standaloneAboveActionBarHuds.computeIfAbsent(uuid, u -> new AboveActionBarHUD(hud, centerX, iconW));
    }

    /**
     * Tracks whether we created a HUD (vs borrowed from DisplayTask).
     * Only hide HUDs that FoodExpansion created — borrowed ones belong to TAN.
     */
    public void removeHud(UUID uuid) {
        standaloneAboveActionBarHuds.remove(uuid);
        BossbarHUD hud = playerHuds.remove(uuid);
        if (hud == null) return;
        // Only hide if this HUD is NOT owned by DisplayTask
        DisplayTask dt = DisplayTask.getTasks().get(uuid);
        if (dt == null || dt.getBossbarHud() != hud) {
            hud.hide();
        }
    }

    public FoodExpansionEvents getEvents() {
        return events;
    }

    public CustomFoodRegistry getCustomFoodRegistry() {
        return customFoodRegistry;
    }

    /**
     * Returns all registered custom food recipe keys (for recipe book discovery).
     */
    public java.util.List<org.bukkit.NamespacedKey> getCustomFoodRecipeKeys() {
        return customFoodRecipes != null ? customFoodRecipes.getRegisteredKeys() : java.util.List.of();
    }

    public NamespacedKey getKeyMaxHealth() { return keyMaxHealth; }
    public NamespacedKey getKeySpeed() { return keySpeed; }
    public NamespacedKey getKeyAttack() { return keyAttack; }
    public NamespacedKey getKeyMining() { return keyMining; }
}
