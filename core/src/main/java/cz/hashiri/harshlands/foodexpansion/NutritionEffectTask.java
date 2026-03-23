package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.tan.TanModule;
import cz.hashiri.harshlands.utils.BossbarHUD;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumMap;
import java.util.Map;

public class NutritionEffectTask extends BukkitRunnable {

    private final Player player;
    private final PlayerNutritionData data;
    private final FoodExpansionModule module;
    private BossbarHUD hud;

    // Config values
    private final double wellNourishedThreshold;
    private final double wellNourishedHydration;
    private final double peakThreshold;
    private final double peakHydration;
    private final double malnourishedThreshold;
    private final double severeThreshold;

    private final double wellNourishedMaxHealth;
    private final double wellNourishedSpeed;
    private final double peakMaxHealth;
    private final double peakSpeed;
    private final double peakAttackDamage;
    private final double malnourishedMiningSpeed;
    private final double severeAttackDamage;
    private final double severeSpeed;

    private final double starvationDamage;
    private final int starvationInterval;

    private final int hudProteinX;
    private final int hudCarbsX;
    private final int hudFatsX;

    // NamespacedKeys for attribute modifiers — instance fields, initialized in constructor
    // (cannot be static final with HLPlugin.getPlugin() as it may be null at class-load time)
    private final NamespacedKey keyMaxHealth;
    private final NamespacedKey keySpeed;
    private final NamespacedKey keyAttack;
    private final NamespacedKey keyMining;

    public NutritionEffectTask(Player player, PlayerNutritionData data, FoodExpansionModule module, FileConfiguration config) {
        this.player = player;
        this.data = data;
        this.module = module;

        this.wellNourishedThreshold = config.getDouble("FoodExpansion.Effects.WellNourished.Threshold", 60);
        this.wellNourishedHydration = config.getDouble("FoodExpansion.Effects.WellNourished.HydrationThreshold", 60);
        this.peakThreshold = config.getDouble("FoodExpansion.Effects.PeakNutrition.Threshold", 80);
        this.peakHydration = config.getDouble("FoodExpansion.Effects.PeakNutrition.HydrationThreshold", 80);
        this.malnourishedThreshold = config.getDouble("FoodExpansion.Effects.Malnourished.Threshold", 30);
        this.severeThreshold = config.getDouble("FoodExpansion.Effects.SeverelyMalnourished.Threshold", 15);

        this.wellNourishedMaxHealth = config.getDouble("FoodExpansion.Effects.WellNourished.MaxHealthBonus", 2.0);
        this.wellNourishedSpeed = config.getDouble("FoodExpansion.Effects.WellNourished.SpeedMultiplier", 0.10);
        this.peakMaxHealth = config.getDouble("FoodExpansion.Effects.PeakNutrition.MaxHealthBonus", 4.0);
        this.peakSpeed = config.getDouble("FoodExpansion.Effects.PeakNutrition.SpeedMultiplier", 0.10);
        this.peakAttackDamage = config.getDouble("FoodExpansion.Effects.PeakNutrition.AttackDamageBonus", 0.10);
        this.malnourishedMiningSpeed = config.getDouble("FoodExpansion.Effects.Malnourished.MiningSpeedReduction", 0.30);
        this.severeAttackDamage = config.getDouble("FoodExpansion.Effects.SeverelyMalnourished.AttackDamageReduction", 0.20);
        this.severeSpeed = config.getDouble("FoodExpansion.Effects.SeverelyMalnourished.SpeedReduction", 0.20);

        this.starvationDamage = config.getDouble("FoodExpansion.Effects.Starving.DamagePerTick", 1.0);
        this.starvationInterval = config.getInt("FoodExpansion.Effects.Starving.DamageInterval", 80);

        this.hudProteinX = config.getInt("FoodExpansion.HUD.Protein.X", -120);
        this.hudCarbsX = config.getInt("FoodExpansion.HUD.Carbs.X", -80);
        this.hudFatsX = config.getInt("FoodExpansion.HUD.Fats.X", -40);

        // Reuse shared NamespacedKeys from module
        this.keyMaxHealth = module.getKeyMaxHealth();
        this.keySpeed = module.getKeySpeed();
        this.keyAttack = module.getKeyAttack();
        this.keyMining = module.getKeyMining();
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;

        // Re-check HUD each run — TAN may have started after our first call
        hud = module.getOrCreateHud(player);

        // 1. Get hydration from TAN
        double hydrationPercent = getHydrationPercent();

        // 2. Evaluate tier
        NutrientTier newTier = data.evaluateTier(
            hydrationPercent,
            wellNourishedThreshold, wellNourishedHydration,
            peakThreshold, peakHydration,
            malnourishedThreshold, severeThreshold
        );

        // 3. Apply modifiers on tier change
        NutrientTier oldTier = data.getCachedTier();
        if (newTier != oldTier) {
            removeAllModifiers();
            applyModifiers(newTier);
            data.setCachedTier(newTier);
        }

        // 4. Starvation damage
        if (newTier == NutrientTier.STARVING) {
            data.incrementStarvationTickCounter(40); // this task runs every 40 ticks
            if (data.getStarvationTickCounter() >= starvationInterval) {
                data.setStarvationTickCounter(0);
                player.damage(starvationDamage);
            }
        } else {
            data.setStarvationTickCounter(0);
        }

        // 5. Update HUD
        updateHud();
    }

    private double getHydrationPercent() {
        TanModule tan = (TanModule) HLModule.getModule(TanModule.NAME);
        if (tan != null && tan.isGloballyEnabled()) {
            int thirst = tan.getThirstManager().getThirst(player);
            return (thirst / 20.0) * 100.0;
        }
        return 100.0; // If TAN not loaded, hydration always satisfied
    }

    // --- Attribute Modifiers ---

    public void removeAllModifiers() {
        removeModifier(Attribute.MAX_HEALTH, keyMaxHealth);
        removeModifier(Attribute.MOVEMENT_SPEED, keySpeed);
        removeModifier(Attribute.ATTACK_DAMAGE, keyAttack);
        removeModifier(Attribute.BLOCK_BREAK_SPEED, keyMining);
    }

    private void removeModifier(Attribute attribute, NamespacedKey key) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        for (AttributeModifier mod : inst.getModifiers()) {
            if (key.equals(mod.getKey())) {
                inst.removeModifier(mod);
                return;
            }
        }
    }

    public void applyModifiers(NutrientTier tier) {
        switch (tier) {
            case PEAK_NUTRITION -> {
                addModifier(Attribute.MAX_HEALTH, keyMaxHealth, peakMaxHealth, AttributeModifier.Operation.ADD_NUMBER);
                addModifier(Attribute.MOVEMENT_SPEED, keySpeed, peakSpeed, AttributeModifier.Operation.ADD_SCALAR);
                addModifier(Attribute.ATTACK_DAMAGE, keyAttack, peakAttackDamage, AttributeModifier.Operation.ADD_SCALAR);
            }
            case WELL_NOURISHED -> {
                addModifier(Attribute.MAX_HEALTH, keyMaxHealth, wellNourishedMaxHealth, AttributeModifier.Operation.ADD_NUMBER);
                addModifier(Attribute.MOVEMENT_SPEED, keySpeed, wellNourishedSpeed, AttributeModifier.Operation.ADD_SCALAR);
            }
            case MALNOURISHED -> {
                addModifier(Attribute.BLOCK_BREAK_SPEED, keyMining, -malnourishedMiningSpeed, AttributeModifier.Operation.ADD_SCALAR);
            }
            case SEVERELY_MALNOURISHED -> {
                addModifier(Attribute.ATTACK_DAMAGE, keyAttack, -severeAttackDamage, AttributeModifier.Operation.ADD_SCALAR);
                addModifier(Attribute.MOVEMENT_SPEED, keySpeed, -severeSpeed, AttributeModifier.Operation.ADD_SCALAR);
            }
            // NORMAL, STARVING — no attribute modifiers (starving uses direct damage instead)
            default -> {}
        }
    }

    private void addModifier(Attribute attribute, NamespacedKey key, double amount, AttributeModifier.Operation operation) {
        AttributeInstance inst = player.getAttribute(attribute);
        if (inst == null) return;
        removeModifier(attribute, key); // Remove first to avoid duplicates
        inst.addModifier(new AttributeModifier(key, amount, operation, EquipmentSlotGroup.ANY));
    }

    // --- HUD ---

    private void updateHud() {
        int p = (int) Math.round(data.getProtein());
        int c = (int) Math.round(data.getCarbs());
        int f = (int) Math.round(data.getFats());

        // Only update if changed
        if (p == data.getLastHudProtein() && c == data.getLastHudCarbs() && f == data.getLastHudFats()) {
            return;
        }
        data.setLastHudValues(p, c, f);

        hud.setElement("nutrition_protein", hudProteinX, Component.text("P: " + p, colorForValue(p)));
        hud.setElement("nutrition_carbs", hudCarbsX, Component.text("C: " + c, colorForValue(c)));
        hud.setElement("nutrition_fats", hudFatsX, Component.text("F: " + f, colorForValue(f)));
    }

    private NamedTextColor colorForValue(int value) {
        if (value >= 60) return NamedTextColor.GREEN;
        if (value >= 30) return NamedTextColor.YELLOW;
        return NamedTextColor.RED;
    }

    public void removeHudElements() {
        if (hud == null) return;
        hud.removeElement("nutrition_protein");
        hud.removeElement("nutrition_carbs");
        hud.removeElement("nutrition_fats");
    }
}
