package cz.hashiri.harshlands.foodexpansion;

/**
 * Pure, testable helpers for building the nutrient preview HUD strip.
 * No Bukkit dependencies; all methods are static and side-effect-free.
 */
public final class NutritionPreviewLayout {

    private NutritionPreviewLayout() {}

    /**
     * Computes the actual macro gain a player would receive by eating the held food right now.
     *
     * @param raw            base macro value from the food's {@link NutrientProfile}
     * @param current        player's current value for this macro
     * @param comfortMult    comfort absorption multiplier (1.0 when no bonus)
     * @return gain in [0.0, 100.0]; 0 when already at cap or beyond
     */
    public static double computeDelta(double raw, double current, double comfortMult) {
        double projected = Math.min(100.0, current + raw * comfortMult);
        return Math.max(0.0, projected - current);
    }

    /**
     * Maps a current macro value to a color using the nutrition tier thresholds.
     * Boundary behavior: each threshold is inclusive of the next tier (e.g., exactly
     * {@code severeT} → GOLD, not DARK_RED).
     *
     * @param value        current macro value
     * @param severeT      severely-malnourished threshold (default 15)
     * @param malnourishedT malnourished threshold (default 30)
     * @param wellT        well-nourished threshold (default 60)
     * @param peakT        peak-nutrition threshold (default 80)
     */
    public static net.kyori.adventure.text.format.NamedTextColor pickCurrentColor(
            double value, double severeT, double malnourishedT, double wellT, double peakT) {
        if (value < severeT) return net.kyori.adventure.text.format.NamedTextColor.DARK_RED;
        if (value < malnourishedT) return net.kyori.adventure.text.format.NamedTextColor.GOLD;
        if (value < wellT) return net.kyori.adventure.text.format.NamedTextColor.YELLOW;
        if (value < peakT) return net.kyori.adventure.text.format.NamedTextColor.GREEN;
        return net.kyori.adventure.text.format.NamedTextColor.AQUA;
    }

    /** Green for any positive delta, gray for zero. Deltas are never negative (foods don't remove macros). */
    public static net.kyori.adventure.text.format.NamedTextColor pickDeltaColor(double delta) {
        return delta > 0.0
                ? net.kyori.adventure.text.format.NamedTextColor.GREEN
                : net.kyori.adventure.text.format.NamedTextColor.GRAY;
    }
}
