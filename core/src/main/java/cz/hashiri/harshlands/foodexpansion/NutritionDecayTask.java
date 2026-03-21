package cz.hashiri.harshlands.foodexpansion;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class NutritionDecayTask extends BukkitRunnable {

    private final Player player;
    private final PlayerNutritionData data;

    // Config values (cached from config at construction)
    private final double baseProteinDecay;
    private final double baseCarbsDecay;
    private final double baseFatsDecay;
    private final double exhaustionThreshold;
    private final double sprintMultiplier;
    private final double miningMultiplier;
    private final double fightingMultiplier;
    private final double swimmingMultiplier;

    // Base rates are per-5-minutes. Task runs every 100 ticks (5 seconds).
    // 5 minutes = 300 seconds = 60 intervals of 5 seconds.
    private static final double INTERVALS_PER_5MIN = 60.0;

    public NutritionDecayTask(Player player, PlayerNutritionData data, FileConfiguration config) {
        this.player = player;
        this.data = data;

        this.baseProteinDecay = config.getDouble("FoodExpansion.Decay.Protein", 1.0);
        this.baseCarbsDecay = config.getDouble("FoodExpansion.Decay.Carbs", 1.5);
        this.baseFatsDecay = config.getDouble("FoodExpansion.Decay.Fats", 0.5);
        this.exhaustionThreshold = config.getDouble("FoodExpansion.Activity.ExhaustionThreshold", 4.0);
        this.sprintMultiplier = config.getDouble("FoodExpansion.Activity.Sprinting", 1.5);
        this.miningMultiplier = config.getDouble("FoodExpansion.Activity.Mining", 1.2);
        this.fightingMultiplier = config.getDouble("FoodExpansion.Activity.Fighting", 1.3);
        this.swimmingMultiplier = config.getDouble("FoodExpansion.Activity.Swimming", 1.4);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }

        // 1. Accumulate activity exhaustion
        if (player.isSprinting()) {
            data.addCarbsExhaustion(sprintMultiplier);
        }
        if (player.isSwimming()) {
            data.addCarbsExhaustion(swimmingMultiplier);
            data.addFatsExhaustion(swimmingMultiplier);
        }
        if (data.consumeMiningFlag()) {
            data.addCarbsExhaustion(miningMultiplier);
        }
        if (data.consumeFightingFlag()) {
            data.addProteinExhaustion(fightingMultiplier);
            data.addFatsExhaustion(fightingMultiplier);
        }

        // 2. Convert exhaustion to decay
        double exhaustProtein = data.drainProteinExhaustion(exhaustionThreshold);
        double exhaustCarbs = data.drainCarbsExhaustion(exhaustionThreshold);
        double exhaustFats = data.drainFatsExhaustion(exhaustionThreshold);

        // 3. Passive time-based decay
        double passiveProtein = baseProteinDecay / INTERVALS_PER_5MIN;
        double passiveCarbs = baseCarbsDecay / INTERVALS_PER_5MIN;
        double passiveFats = baseFatsDecay / INTERVALS_PER_5MIN;

        // 4. Apply total decay
        data.applyDecay(
            passiveProtein + exhaustProtein,
            passiveCarbs + exhaustCarbs,
            passiveFats + exhaustFats
        );
    }
}
