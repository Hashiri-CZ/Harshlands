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

    /**
     * Builds the text portion of one preview cell: {@code "Label value (+delta)"} where
     * {@code value} is colored by {@code valueColor} and {@code (+delta)} by {@code deltaColor}.
     * The leading label is in white. Values are floored to integers for display.
     */
    public static net.kyori.adventure.text.Component buildCellText(
            String label, double current, double delta,
            net.kyori.adventure.text.format.NamedTextColor valueColor,
            net.kyori.adventure.text.format.NamedTextColor deltaColor) {
        int intCurrent = (int) Math.floor(current);
        int intDelta = (int) Math.floor(delta);
        return net.kyori.adventure.text.Component.text()
                .append(net.kyori.adventure.text.Component.text(
                        label + " ", net.kyori.adventure.text.format.NamedTextColor.WHITE))
                .append(net.kyori.adventure.text.Component.text(
                        Integer.toString(intCurrent), valueColor))
                .append(net.kyori.adventure.text.Component.text(
                        " (+" + intDelta + ")", deltaColor))
                .build();
    }

    /** A built preview row: the Adventure Component plus its total pixel advance (for X centering). */
    public record Row(net.kyori.adventure.text.Component component, int advance) {}

    /**
     * Builds the full three-cell preview row as a text-only component ready to render at the
     * bossbar baseline. Cells are separated by {@code cellSpacing}-pixel negative-space shifts
     * (no Component.space() glue) so the reported advance is exact.
     */
    public static Row buildRow(
            NutrientProfile profile,
            double currentP, double currentC, double currentF,
            double comfortMult,
            double severeT, double malnourishedT, double wellT, double peakT,
            String labelP, String labelC, String labelF,
            int cellSpacing) {

        double deltaP = computeDelta(profile.protein(), currentP, comfortMult);
        double deltaC = computeDelta(profile.carbs(),   currentC, comfortMult);
        double deltaF = computeDelta(profile.fats(),    currentF, comfortMult);

        net.kyori.adventure.text.format.NamedTextColor colorP = pickCurrentColor(
                currentP, severeT, malnourishedT, wellT, peakT);
        net.kyori.adventure.text.format.NamedTextColor colorC = pickCurrentColor(
                currentC, severeT, malnourishedT, wellT, peakT);
        net.kyori.adventure.text.format.NamedTextColor colorF = pickCurrentColor(
                currentF, severeT, malnourishedT, wellT, peakT);

        net.kyori.adventure.text.Component cellP = buildCellText(labelP, currentP, deltaP, colorP, pickDeltaColor(deltaP));
        net.kyori.adventure.text.Component cellC = buildCellText(labelC, currentC, deltaC, colorC, pickDeltaColor(deltaC));
        net.kyori.adventure.text.Component cellF = buildCellText(labelF, currentF, deltaF, colorF, pickDeltaColor(deltaF));

        net.kyori.adventure.text.Component combined = net.kyori.adventure.text.Component.text()
                .append(cellP)
                .append(cz.hashiri.harshlands.utils.BossbarHUD.NegativeSpaceHelper.shift(cellSpacing))
                .append(cellC)
                .append(cz.hashiri.harshlands.utils.BossbarHUD.NegativeSpaceHelper.shift(cellSpacing))
                .append(cellF)
                .build();

        int pTextAdvance = measureTextAdvance(makeCellText(labelP, currentP, deltaP));
        int cTextAdvance = measureTextAdvance(makeCellText(labelC, currentC, deltaC));
        int fTextAdvance = measureTextAdvance(makeCellText(labelF, currentF, deltaF));

        int totalAdvance = 2 * cellSpacing + pTextAdvance + cTextAdvance + fTextAdvance;

        return new Row(combined, totalAdvance);
    }

    /** Shared text-only form used both for display and advance measurement. */
    private static String makeCellText(String label, double current, double delta) {
        return label + " " + ((int) Math.floor(current)) + " (+" + ((int) Math.floor(delta)) + ")";
    }
}
