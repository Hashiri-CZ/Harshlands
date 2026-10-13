package cz.hashiri.harshlands.disease;

import cz.hashiri.harshlands.disease.model.Disease;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts against the real shipped {@code Settings/disease.yml} rather than a fixture.
 *
 * <p>The other parse tests use hand-written YAML, so they verify the parser but say nothing
 * about the file operators actually receive. A trigger that references a disease id that no
 * longer exists, or a tag-borne disease with no contamination source, loads perfectly cleanly
 * and simply never fires — a failure mode that is invisible until someone plays for an hour
 * and notices a disease that never appears.
 */
class ShippedDiseaseSettingsTest {

    private static YamlConfiguration shippedSettings() throws IOException, URISyntaxException {
        try (InputStream in = ShippedDiseaseSettingsTest.class.getClassLoader()
                .getResourceAsStream("Settings/disease.yml")) {
            assertNotNull(in, "Settings/disease.yml must ship (not found on test classpath)");
            return YamlConfiguration.loadConfiguration(
                new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void every_shipped_disease_parses() throws Exception {
        List<Disease> diseases =
            DiseaseRegistry.parse(shippedSettings().getConfigurationSection("Diseases"));

        // All 11 diseases ship enabled; a parse failure or a stray Enabled:false shows up here.
        assertEquals(11, diseases.size(), "expected all 11 shipped diseases to parse and be enabled");
        for (Disease d : diseases) {
            assertFalse(d.stages().isEmpty(), d.id() + " has no stages");
            assertEquals(3, d.maxStage(), d.id() + " should have 3 stages");
        }
    }

    @Test
    void every_trigger_points_at_a_disease_that_exists() throws Exception {
        YamlConfiguration cfg = shippedSettings();
        List<String> ids = new ArrayList<>();
        for (Disease d : DiseaseRegistry.parse(cfg.getConfigurationSection("Diseases"))) {
            ids.add(d.id());
        }

        ConfigurationSection triggers = cfg.getConfigurationSection("Triggers");
        assertNotNull(triggers, "Triggers section must ship");

        for (String key : triggers.getKeys(true)) {
            if (!key.endsWith("Disease")) continue;
            String referenced = triggers.getString(key);
            assertTrue(ids.contains(referenced),
                "Triggers." + key + " references unknown disease '" + referenced + "'");
        }
    }

    @Test
    void a_tag_borne_disease_ships_with_a_working_contamination_source() throws Exception {
        YamlConfiguration cfg = shippedSettings();

        ConfigurationSection infected = cfg.getConfigurationSection("Triggers.InfectedItem");
        assertNotNull(infected, "Triggers.InfectedItem must ship");

        boolean anyTagBorne = false;
        for (String key : infected.getKeys(false)) {
            if (infected.getBoolean(key + ".RequireDiseasedTag", false)) anyTagBorne = true;
        }
        assertTrue(anyTagBorne, "expected at least one RequireDiseasedTag trigger (wasting_blight)");

        // Nothing else in the plugin writes the hldiseased tag, so without an enabled source
        // a tag-borne disease is simply uncontractable — which is exactly the state this
        // config previously shipped in.
        ConfigurationSection tainted = cfg.getConfigurationSection("TaintedItems");
        assertNotNull(tainted, "TaintedItems must ship, or a tag-borne disease can never be caught");
        assertTrue(tainted.getBoolean("MobDrops.Enabled", false),
            "MobDrops is the only bootstrap source — infected-player drops cannot start an outbreak");
        assertFalse(tainted.getStringList("MobDrops.Entities").isEmpty(),
            "MobDrops.Entities must not be empty");
        assertTrue(tainted.getDouble("MobDrops.Chance", 0.0) > 0.0,
            "MobDrops.Chance must be greater than zero");
    }

    @Test
    void tainted_mob_entity_names_are_real_entity_types() throws Exception {
        List<String> names = shippedSettings().getStringList("TaintedItems.MobDrops.Entities");
        for (String name : names) {
            // A typo here is silently skipped at runtime with only a warning, quietly shrinking
            // the contamination source; fail the build instead.
            org.bukkit.entity.EntityType.valueOf(name);
        }
    }

    @Test
    void shipped_notifications_match_the_documented_defaults() throws Exception {
        ProgressionNotifier notifier = ProgressionNotifier.fromConfig(
            shippedSettings().getConfigurationSection("Notifications"));

        assertFalse(notifier.isOnContractEnabled(),
            "OnContract must ship off — it reveals the exact instant of infection");
        assertTrue(notifier.isOnStageChangeEnabled());
        assertTrue(notifier.isOnNaturalCureEnabled());
    }
}
