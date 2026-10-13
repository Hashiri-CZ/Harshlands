package cz.hashiri.harshlands.disease;

import cz.hashiri.harshlands.disease.ProgressionNotifier.Transition;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pure parts of the optional progression feedback: which transition a stage change
 * warrants, and how the config section maps onto the three channels.
 */
class ProgressionNotifierTest {

    // ── classify ─────────────────────────────────────────────────────────────

    @Test
    void leaving_incubation_is_an_onset_not_a_worsening() {
        // Stage 0 -> 1 is numerically an increase, but the player felt nothing during
        // incubation, so "your condition worsens" would be the wrong first thing they read.
        assertEquals(Transition.ONSET, ProgressionNotifier.classify(0, 1));
    }

    @Test
    void advancing_between_active_stages_is_a_worsening() {
        assertEquals(Transition.WORSENED, ProgressionNotifier.classify(1, 2));
        assertEquals(Transition.WORSENED, ProgressionNotifier.classify(2, 3));
    }

    @Test
    void regressing_between_active_stages_is_an_easing() {
        assertEquals(Transition.EASED, ProgressionNotifier.classify(3, 2));
        assertEquals(Transition.EASED, ProgressionNotifier.classify(2, 1));
    }

    @Test
    void terminal_regression_straight_to_stage_one_still_reads_as_easing() {
        // The known terminal->stage-1 shortcut must not be reported as a worsening.
        assertEquals(Transition.EASED, ProgressionNotifier.classify(3, 1));
    }

    @Test
    void an_unchanged_stage_warrants_no_message() {
        assertNull(ProgressionNotifier.classify(0, 0));
        assertNull(ProgressionNotifier.classify(2, 2));
    }

    // ── fromConfig ───────────────────────────────────────────────────────────

    @Test
    void a_missing_section_yields_a_silent_notifier() {
        // The module falls back to this when Notifications is absent from an older
        // Settings/disease.yml, so an un-updated config must behave exactly as before.
        ProgressionNotifier notifier = ProgressionNotifier.fromConfig(null);

        assertFalse(notifier.isOnContractEnabled());
        assertFalse(notifier.isOnStageChangeEnabled());
        assertFalse(notifier.isOnNaturalCureEnabled());
    }

    @Test
    void defaults_announce_stage_changes_and_cures_but_not_contraction() {
        // An empty section means the admin added Notifications but set nothing: contraction
        // stays quiet (announcing it would reveal the exact instant of infection and make
        // incubation pointless), while stage changes and natural cures speak up.
        ConfigurationSection empty = new MemoryConfiguration().createSection("Notifications");
        ProgressionNotifier notifier = ProgressionNotifier.fromConfig(empty);

        assertFalse(notifier.isOnContractEnabled());
        assertTrue(notifier.isOnStageChangeEnabled());
        assertTrue(notifier.isOnNaturalCureEnabled());
    }

    @Test
    void explicit_config_values_win_over_the_defaults() {
        ConfigurationSection section = new MemoryConfiguration().createSection("Notifications");
        section.set("OnContract", true);
        section.set("OnStageChange", false);
        section.set("OnNaturalCure", false);
        ProgressionNotifier notifier = ProgressionNotifier.fromConfig(section);

        assertTrue(notifier.isOnContractEnabled());
        assertFalse(notifier.isOnStageChangeEnabled());
        assertFalse(notifier.isOnNaturalCureEnabled());
    }
}
