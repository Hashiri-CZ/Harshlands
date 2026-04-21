package cz.hashiri.harshlands.foodexpansion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NutritionPreviewLayoutTest {

    @Nested
    class DeltaMath {
        @Test void normal_case_returns_raw_times_multiplier() {
            assertEquals(8.0, NutritionPreviewLayout.computeDelta(8.0, 50.0, 1.0));
        }

        @Test void capped_at_100() {
            assertEquals(5.0, NutritionPreviewLayout.computeDelta(8.0, 95.0, 1.0));
        }

        @Test void at_cap_returns_zero() {
            assertEquals(0.0, NutritionPreviewLayout.computeDelta(8.0, 100.0, 1.0));
        }

        @Test void respects_comfort_multiplier() {
            assertEquals(12.0, NutritionPreviewLayout.computeDelta(8.0, 50.0, 1.5));
        }

        @Test void zero_raw_returns_zero() {
            assertEquals(0.0, NutritionPreviewLayout.computeDelta(0.0, 50.0, 1.0));
        }

        @Test void never_negative_when_already_over_cap() {
            // Defensive: if current somehow exceeds 100, delta should not go negative.
            assertEquals(0.0, NutritionPreviewLayout.computeDelta(8.0, 110.0, 1.0));
        }
    }

    @Nested
    class CurrentValueColor {
        // Defaults from foodexpansion.yml: severe=15, malnourished=30, wellNourished=60, peak=80
        private static final double SEVERE = 15, MALNOURISHED = 30, WELL = 60, PEAK = 80;

        @Test void zero_is_red() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.DARK_RED,
                    NutritionPreviewLayout.pickCurrentColor(0, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void just_below_severe_is_red() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.DARK_RED,
                    NutritionPreviewLayout.pickCurrentColor(14.999, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void at_severe_is_gold() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.GOLD,
                    NutritionPreviewLayout.pickCurrentColor(15, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void just_below_malnourished_is_gold() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.GOLD,
                    NutritionPreviewLayout.pickCurrentColor(29.999, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void at_malnourished_is_yellow() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                    NutritionPreviewLayout.pickCurrentColor(30, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void just_below_well_is_yellow() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                    NutritionPreviewLayout.pickCurrentColor(59.999, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void at_well_is_green() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN,
                    NutritionPreviewLayout.pickCurrentColor(60, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void just_below_peak_is_green() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN,
                    NutritionPreviewLayout.pickCurrentColor(79.999, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void at_peak_is_aqua() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.AQUA,
                    NutritionPreviewLayout.pickCurrentColor(80, SEVERE, MALNOURISHED, WELL, PEAK));
        }

        @Test void hundred_is_aqua() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.AQUA,
                    NutritionPreviewLayout.pickCurrentColor(100, SEVERE, MALNOURISHED, WELL, PEAK));
        }
    }

    @Nested
    class DeltaColor {
        @Test void positive_delta_is_green() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN,
                    NutritionPreviewLayout.pickDeltaColor(5.0));
        }

        @Test void zero_delta_is_gray() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.GRAY,
                    NutritionPreviewLayout.pickDeltaColor(0.0));
        }

        @Test void tiny_positive_delta_is_still_green() {
            assertEquals(net.kyori.adventure.text.format.NamedTextColor.GREEN,
                    NutritionPreviewLayout.pickDeltaColor(0.5));
        }
    }

    @Nested
    class TextAdvance {
        @Test void space_is_4() {
            assertEquals(4, NutritionPreviewLayout.measureTextAdvance(" "));
        }

        @Test void lowercase_i_is_narrow() {
            // 'i' = 2 advance (classic Minecraft narrow glyph)
            assertEquals(2, NutritionPreviewLayout.measureTextAdvance("i"));
        }

        @Test void digit_is_6() {
            assertEquals(6, NutritionPreviewLayout.measureTextAdvance("5"));
        }

        @Test void plus_sign_is_6() {
            assertEquals(6, NutritionPreviewLayout.measureTextAdvance("+"));
        }

        @Test void open_paren_is_5() {
            assertEquals(5, NutritionPreviewLayout.measureTextAdvance("("));
        }

        @Test void close_paren_is_5() {
            assertEquals(5, NutritionPreviewLayout.measureTextAdvance(")"));
        }

        @Test void sums_characters() {
            // "Fat" = F(6) + a(6) + t(4) = 16
            assertEquals(16, NutritionPreviewLayout.measureTextAdvance("Fat"));
        }

        @Test void protein_word() {
            // "Protein" = P(6) + r(6) + o(6) + t(4) + e(6) + i(2) + n(6) = 36
            assertEquals(36, NutritionPreviewLayout.measureTextAdvance("Protein"));
        }

        @Test void unknown_char_falls_back_to_6() {
            assertEquals(6, NutritionPreviewLayout.measureTextAdvance("ñ"));
        }

        @Test void empty_string_is_zero() {
            assertEquals(0, NutritionPreviewLayout.measureTextAdvance(""));
        }
    }

    @Nested
    class CellText {
        @Test void builds_expected_string() {
            net.kyori.adventure.text.Component c = NutritionPreviewLayout.buildCellText(
                    "Protein", 23, 8,
                    net.kyori.adventure.text.format.NamedTextColor.GOLD,
                    net.kyori.adventure.text.format.NamedTextColor.GREEN);
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
            assertEquals("Protein 23 (+8)", plain);
        }

        @Test void zero_delta_formats_as_plus_zero() {
            net.kyori.adventure.text.Component c = NutritionPreviewLayout.buildCellText(
                    "Fat", 80, 0,
                    net.kyori.adventure.text.format.NamedTextColor.AQUA,
                    net.kyori.adventure.text.format.NamedTextColor.GRAY);
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
            assertEquals("Fat 80 (+0)", plain);
        }

        @Test void rounds_down_to_integer() {
            // Display values are whole numbers, even though internal math is double
            net.kyori.adventure.text.Component c = NutritionPreviewLayout.buildCellText(
                    "Carbs", 41.7, 2.4,
                    net.kyori.adventure.text.format.NamedTextColor.YELLOW,
                    net.kyori.adventure.text.format.NamedTextColor.GREEN);
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
            assertEquals("Carbs 41 (+2)", plain);
        }
    }

    @Nested
    class RowBuilder {
        @Test void row_has_all_three_labels_and_values() {
            NutritionPreviewLayout.Row row = NutritionPreviewLayout.buildRow(
                    new NutrientProfile(8, 0, 2),
                    23, 41, 67,       // currents
                    1.0,              // comfortMult
                    15, 30, 60, 80,   // tier thresholds
                    "Protein", "Carbs", "Fat",
                    32,               // iconWidth
                    4,                // iconTextGap
                    24);              // cellSpacing

            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(row.component());
            // Icon codepoints are not printable; plain serializer emits them as their raw char.
            // We assert the *text* segments are all present in order.
            org.junit.jupiter.api.Assertions.assertTrue(plain.contains("Protein 23 (+8)"),
                    "row should contain protein cell text; was: " + plain);
            org.junit.jupiter.api.Assertions.assertTrue(plain.contains("Carbs 41 (+0)"),
                    "row should contain carbs cell text; was: " + plain);
            org.junit.jupiter.api.Assertions.assertTrue(plain.contains("Fat 67 (+2)"),
                    "row should contain fat cell text; was: " + plain);
        }

        @Test void total_advance_matches_formula() {
            NutritionPreviewLayout.Row row = NutritionPreviewLayout.buildRow(
                    new NutrientProfile(8, 0, 2),
                    23, 41, 67,
                    1.0,
                    15, 30, 60, 80,
                    "Protein", "Carbs", "Fat",
                    32, 4, 24);

            // Expected = 3*iconWidth + 2*cellSpacing + 3*iconTextGap + sum(text advances)
            // text advances:
            //   "Protein 23 (+8)" — details computed by measureTextAdvance
            //   "Carbs 41 (+0)"
            //   "Fat 67 (+2)"
            int pText = NutritionPreviewLayout.measureTextAdvance("Protein 23 (+8)");
            int cText = NutritionPreviewLayout.measureTextAdvance("Carbs 41 (+0)");
            int fText = NutritionPreviewLayout.measureTextAdvance("Fat 67 (+2)");
            int expected = 3 * 32 + 2 * 24 + 3 * 4 + pText + cText + fText;

            assertEquals(expected, row.advance());
        }

        @Test void delta_respects_cap() {
            NutritionPreviewLayout.Row row = NutritionPreviewLayout.buildRow(
                    new NutrientProfile(8, 8, 8),
                    95, 100, 50,
                    1.0,
                    15, 30, 60, 80,
                    "Protein", "Carbs", "Fat",
                    32, 4, 24);
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(row.component());
            org.junit.jupiter.api.Assertions.assertTrue(plain.contains("Protein 95 (+5)"));
            org.junit.jupiter.api.Assertions.assertTrue(plain.contains("Carbs 100 (+0)"));
            org.junit.jupiter.api.Assertions.assertTrue(plain.contains("Fat 50 (+8)"));
        }
    }
}
