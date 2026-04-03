package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.tan.TanModule;
import cz.hashiri.harshlands.utils.AboveActionBarHUD;

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
    private AboveActionBarHUD aboveActionBarHud;

    // Linger state: server tick when macro recovered above threshold, -1 = not lingering
    private long proteinRecoveryTick = -1;
    private long carbsRecoveryTick = -1;
    private long fatsRecoveryTick = -1;

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

    private final double lowThreshold;
    private final long lingerTicks;

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

        this.lowThreshold = config.getDouble("FoodExpansion.HUD.LowThreshold", 40);
        this.lingerTicks = config.getLong("FoodExpansion.HUD.LingerSeconds", 5) * 20L;

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

        // 5. Update low-macro warning icons
        if (aboveActionBarHud == null) {
            aboveActionBarHud = module.getOrCreateAboveActionBarHud(player);
        }
        updateIcons();
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

    // --- HUD Icons ---

    private void updateIcons() {
        long currentTick = player.getWorld().getGameTime();

        proteinRecoveryTick = updateIconSlot(
                AboveActionBarHUD.Slot.PROTEIN, data.getProtein(),
                proteinRecoveryTick, currentTick);
        carbsRecoveryTick = updateIconSlot(
                AboveActionBarHUD.Slot.CARBS, data.getCarbs(),
                carbsRecoveryTick, currentTick);
        fatsRecoveryTick = updateIconSlot(
                AboveActionBarHUD.Slot.FAT, data.getFats(),
                fatsRecoveryTick, currentTick);
    }

    private long updateIconSlot(AboveActionBarHUD.Slot slot, double value,
                                long recoveryTick, long currentTick) {
        if (value < lowThreshold) {
            aboveActionBarHud.setVisible(slot, true);
            return -1;
        }
        if (recoveryTick == -1) {
            return currentTick;
        }
        if (currentTick - recoveryTick >= lingerTicks) {
            aboveActionBarHud.setVisible(slot, false);
            return -1;
        }
        return recoveryTick;
    }

    public void removeHudElements() {
        if (aboveActionBarHud == null) return;
        aboveActionBarHud.setVisible(AboveActionBarHUD.Slot.PROTEIN, false);
        aboveActionBarHud.setVisible(AboveActionBarHUD.Slot.CARBS, false);
        aboveActionBarHud.setVisible(AboveActionBarHUD.Slot.FAT, false);
        proteinRecoveryTick = -1;
        carbsRecoveryTick = -1;
        fatsRecoveryTick = -1;
    }
}
