package cz.hashiri.harshlands.foodexpansion;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PlayerNutritionData {

    private double protein;
    private double carbs;
    private double fats;

    private double proteinExhaustion;
    private double carbsExhaustion;
    private double fatsExhaustion;

    private volatile boolean dirty = false;

    private boolean miningFlag = false;
    private boolean fightingFlag = false;

    private double hungerDebtAccumulator = 0.0;

    private NutrientTier cachedTier = NutrientTier.NORMAL;

    private int lastHudProtein = -1;
    private int lastHudCarbs = -1;
    private int lastHudFats = -1;

    private int starvationTickCounter = 0;

    private final Map<String, Integer> satiationCounters = new HashMap<>();

    public PlayerNutritionData(double protein, double carbs, double fats,
                               double proteinExhaustion, double carbsExhaustion,
                               double fatsExhaustion) {
        this.protein = protein;
        this.carbs = carbs;
        this.fats = fats;
        this.proteinExhaustion = proteinExhaustion;
        this.carbsExhaustion = carbsExhaustion;
        this.fatsExhaustion = fatsExhaustion;
    }

    public void addNutrients(NutrientProfile profile, double comfortMultiplier) {
        this.protein = Math.min(100.0, this.protein + profile.protein() * comfortMultiplier);
        this.carbs = Math.min(100.0, this.carbs + profile.carbs() * comfortMultiplier);
        this.fats = Math.min(100.0, this.fats + profile.fats() * comfortMultiplier);
        dirty = true;
    }

    public void applyDecay(double proteinDelta, double carbsDelta, double fatsDelta) {
        double oldP = protein, oldC = carbs, oldF = fats;
        this.protein = Math.max(0.0, this.protein - proteinDelta);
        this.carbs = Math.max(0.0, this.carbs - carbsDelta);
        this.fats = Math.max(0.0, this.fats - fatsDelta);
        if (protein != oldP || carbs != oldC || fats != oldF) {
            dirty = true;
        }
    }

    public void applyDeathPenalty(double percentLoss) {
        double factor = 1.0 - (percentLoss / 100.0);
        this.protein *= factor;
        this.carbs *= factor;
        this.fats *= factor;
        dirty = true;
        this.hungerDebtAccumulator = 0.0;
        this.starvationTickCounter = 0;
    }

    public NutrientTier evaluateTier(double hydrationPercent,
                                     double wellNourishedThreshold, double wellNourishedHydration,
                                     double peakThreshold, double peakHydration,
                                     double malnourishedThreshold, double severeThreshold) {
        if (protein <= 0.0 || carbs <= 0.0 || fats <= 0.0) {
            return NutrientTier.STARVING;
        }
        if (protein < severeThreshold || carbs < severeThreshold || fats < severeThreshold) {
            return NutrientTier.SEVERELY_MALNOURISHED;
        }
        if (protein < malnourishedThreshold || carbs < malnourishedThreshold || fats < malnourishedThreshold) {
            return NutrientTier.MALNOURISHED;
        }
        if (protein >= peakThreshold && carbs >= peakThreshold && fats >= peakThreshold
                && hydrationPercent >= peakHydration) {
            return NutrientTier.PEAK_NUTRITION;
        }
        if (protein >= wellNourishedThreshold && carbs >= wellNourishedThreshold && fats >= wellNourishedThreshold
                && hydrationPercent >= wellNourishedHydration) {
            return NutrientTier.WELL_NOURISHED;
        }
        return NutrientTier.NORMAL;
    }

    public void addProteinExhaustion(double amount) { this.proteinExhaustion += amount; }
    public void addCarbsExhaustion(double amount) { this.carbsExhaustion += amount; }
    public void addFatsExhaustion(double amount) { this.fatsExhaustion += amount; }

    public double drainProteinExhaustion(double threshold) {
        if (proteinExhaustion >= threshold) {
            proteinExhaustion -= threshold;
            return 1.0;
        }
        return 0.0;
    }

    public double drainCarbsExhaustion(double threshold) {
        if (carbsExhaustion >= threshold) {
            carbsExhaustion -= threshold;
            return 1.0;
        }
        return 0.0;
    }

    public double drainFatsExhaustion(double threshold) {
        if (fatsExhaustion >= threshold) {
            fatsExhaustion -= threshold;
            return 1.0;
        }
        return 0.0;
    }

    public boolean consumeMiningFlag() {
        boolean val = miningFlag;
        miningFlag = false;
        return val;
    }

    public boolean consumeFightingFlag() {
        boolean val = fightingFlag;
        fightingFlag = false;
        return val;
    }

    public void setMiningFlag() { this.miningFlag = true; }
    public void setFightingFlag() { this.fightingFlag = true; }

    public double getHungerDebtAccumulator() { return hungerDebtAccumulator; }
    public void setHungerDebtAccumulator(double value) { this.hungerDebtAccumulator = value; }
    public void addHungerDebt(double debt) { this.hungerDebtAccumulator += debt; }

    public int getStarvationTickCounter() { return starvationTickCounter; }
    public void setStarvationTickCounter(int value) { this.starvationTickCounter = value; }
    public void incrementStarvationTickCounter(int ticks) { this.starvationTickCounter += ticks; }

    public NutrientTier getCachedTier() { return cachedTier; }
    public void setCachedTier(NutrientTier tier) { this.cachedTier = tier; }

    public int getLastHudProtein() { return lastHudProtein; }
    public int getLastHudCarbs() { return lastHudCarbs; }
    public int getLastHudFats() { return lastHudFats; }
    public void setLastHudValues(int p, int c, int f) {
        lastHudProtein = p; lastHudCarbs = c; lastHudFats = f;
    }

    public boolean isDirty() { return dirty; }
    public void clearDirty() { dirty = false; }
    public void markDirty() { dirty = true; }

    public double getProtein() { return protein; }
    public double getCarbs() { return carbs; }
    public double getFats() { return fats; }

    public void setProtein(double v) { protein = v; dirty = true; }
    public void setCarbs(double v) { carbs = v; dirty = true; }
    public void setFats(double v) { fats = v; dirty = true; }

    public double getProteinExhaustion() { return proteinExhaustion; }
    public double getCarbsExhaustion() { return carbsExhaustion; }
    public double getFatsExhaustion() { return fatsExhaustion; }

    public void restoreFromRow(double protein, double carbs, double fats,
                               double proteinExhaustion, double carbsExhaustion,
                               double fatsExhaustion) {
        this.protein = protein;
        this.carbs = carbs;
        this.fats = fats;
        this.proteinExhaustion = proteinExhaustion;
        this.carbsExhaustion = carbsExhaustion;
        this.fatsExhaustion = fatsExhaustion;
    }

    public int getSatiation(String foodKey) {
        return satiationCounters.getOrDefault(foodKey, 0);
    }

    public void incrementSatiation(String foodKey) {
        satiationCounters.merge(foodKey, 1, Integer::sum);
    }

    public void decaySatiationCounters() {
        Iterator<Map.Entry<String, Integer>> it = satiationCounters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            int newVal = entry.getValue() - 1;
            if (newVal <= 0) {
                it.remove();
            } else {
                entry.setValue(newVal);
            }
        }
    }

    public void clearSatiationCounters() {
        satiationCounters.clear();
    }

    public double getMinNutrient() {
        return Math.min(protein, Math.min(carbs, fats));
    }

    public int countBelowThreshold(double threshold) {
        int count = 0;
        if (protein < threshold) count++;
        if (carbs < threshold) count++;
        if (fats < threshold) count++;
        return count;
    }
}
