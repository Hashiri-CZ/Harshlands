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

    // Advance widths (in pixels) for characters used by the preview strip.
    // Values derived from Minecraft's default font; unknown characters default to 6.
    // Each advance includes the 1-pixel trailing space between glyphs.
    private static final java.util.Map<Character, Integer> ADVANCE = buildAdvanceTable();

    private static java.util.Map<Character, Integer> buildAdvanceTable() {
        java.util.Map<Character, Integer> m = new java.util.HashMap<>();
        m.put(' ', 4);
        // Digits — all 6 in the vanilla default font
        for (char c = '0'; c <= '9'; c++) m.put(c, 6);
        // Punctuation used in the preview
        m.put('+', 6);
        m.put('(', 5);
        m.put(')', 5);
        // Narrow lowercase glyphs commonly present in "Protein"/"Carbs"/"Fat"
        m.put('i', 2);
        m.put('t', 4);
        m.put('l', 3);
        // All other letters used in labels ("Protein", "Carbs", "Fat") are 6 wide
        // and fall through the default case below.
        return m;
    }

    /** Sum of per-character advance widths in pixels. Unknown characters contribute 6. */
    public static int measureTextAdvance(String text) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += ADVANCE.getOrDefault(text.charAt(i), 6);
        }
        return total;
    }
}
