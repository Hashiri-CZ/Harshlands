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
}
