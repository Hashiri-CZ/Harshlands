package cz.hashiri.harshlands.tan;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThirstEffectsTaskTest {

    // Defaults from toughasnails.yml
    private static final double D = 0.4; // Dehydrated threshold
    private static final double P = 5.0; // Parched threshold
    private static final double T = 10.0; // Thirsty threshold

    @Nested
    class DefaultThresholds {
        @Test void full_bar_is_no_tier() {
            assertNull(ThirstEffectsTask.selectTier(20.0, D, P, T));
        }

        @Test void just_above_thirsty_is_no_tier() {
            assertNull(ThirstEffectsTask.selectTier(11.0, D, P, T));
        }

        @Test void at_thirsty_boundary_activates_thirsty() {
            assertEquals(ThirstEffectsTask.Tier.THIRSTY,
                    ThirstEffectsTask.selectTier(10.0, D, P, T));
        }

        @Test void just_above_parched_is_thirsty() {
            assertEquals(ThirstEffectsTask.Tier.THIRSTY,
                    ThirstEffectsTask.selectTier(5.1, D, P, T));
        }

        @Test void at_parched_boundary_activates_parched() {
            assertEquals(ThirstEffectsTask.Tier.PARCHED,
                    ThirstEffectsTask.selectTier(5.0, D, P, T));
        }

        @Test void just_above_dehydrated_is_parched() {
            assertEquals(ThirstEffectsTask.Tier.PARCHED,
                    ThirstEffectsTask.selectTier(0.5, D, P, T));
        }

        @Test void at_dehydrated_boundary_activates_dehydrated() {
            assertEquals(ThirstEffectsTask.Tier.DEHYDRATED,
                    ThirstEffectsTask.selectTier(0.4, D, P, T));
        }

        @Test void empty_bar_is_dehydrated() {
            assertEquals(ThirstEffectsTask.Tier.DEHYDRATED,
                    ThirstEffectsTask.selectTier(0.0, D, P, T));
        }
    }

    @Nested
    class Misconfigured {
        // Parched threshold > Thirsty threshold (owner typo). Highest-severity match still wins.
        @Test void parched_higher_than_thirsty_still_picks_highest_match() {
            // thirst=8, P=15, T=10: 8 <= 15 so PARCHED wins (never falls through to THIRSTY check
            // because PARCHED is checked first after DEHYDRATED)
            assertEquals(ThirstEffectsTask.Tier.PARCHED,
                    ThirstEffectsTask.selectTier(8.0, 0.4, 15.0, 10.0));
        }

        @Test void above_all_misconfigured_thresholds_is_null() {
            assertNull(ThirstEffectsTask.selectTier(20.0, 0.4, 15.0, 10.0));
        }
    }
}
