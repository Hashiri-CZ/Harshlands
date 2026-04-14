package cz.hashiri.harshlands.migration;

import cz.hashiri.harshlands.migration.manifests.BaublesManifest;
import cz.hashiri.harshlands.migration.manifests.ComfortManifest;
import cz.hashiri.harshlands.migration.manifests.FearManifest;
import cz.hashiri.harshlands.migration.manifests.FoodExpansionManifest;
import cz.hashiri.harshlands.migration.manifests.ToughAsNailsManifest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleManifestTest {

    @Test
    void tan_manifest_routes_keys_to_correct_destinations(@TempDir Path dataFolder) throws IOException {
        // Arrange — synthetic legacy TAN YAML
        Path legacy = dataFolder.resolve("toughasnails.yml");
        Files.writeString(legacy, """
                Initialize:
                  Enabled: true
                  Message: "&6Initializing"
                DehydrationDeath:
                  Enabled: true
                  Messages:
                    - "&4You died of thirst."
                Dehydration:
                  Timer: 200
                MobDrops:
                  ZOMBIE:
                    sandstone_dust:
                      Chance: 0.5
                BlockDrops:
                  STONE:
                    pebble:
                      Chance: 0.1
                """);

        // Act
        ModuleManifest manifest = new ToughAsNailsManifest();
        ModuleSplitResult out = manifest.split(YamlConfiguration.loadConfiguration(legacy.toFile()), dataFolder);

        // Assert — Settings gets gameplay tuning
        YamlConfiguration settings = out.settings();
        assertTrue(settings.contains("Initialize.Enabled"));  // kept
        assertEquals(200, settings.getInt("Dehydration.Timer"));
        // Messages are stripped from Settings (they live in Translations)
        assertNull(settings.getString("Initialize.Message"));

        // Translations get messages, with keys normalized
        Map<String, Object> translations = out.translations();
        assertEquals("&6Initializing", translations.get("toughasnails.initialize.message"));
        assertNotNull(translations.get("toughasnails.dehydration_death.messages"));

        // MobDrops / BlockDrops routed to their own files
        assertNotNull(out.mobDrops().get("MobDrops.ZOMBIE.sandstone_dust.Chance"));
        assertNotNull(out.blockDrops().get("BlockDrops.STONE.pebble.Chance"));
    }

    @Test
    void baubles_manifest_routes_correctly() {
        BaublesManifest manifest = new BaublesManifest();

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("Initialize.Message", "a");
        legacy.set("WormholeInventory.SkullName", "Skull");
        legacy.set("WormholeInventory.Rows", 6);
        legacy.set("MobDrops.ZOMBIE.eye.Chance", 0.3);

        ModuleSplitResult out = manifest.split(legacy, null);

        Map<String, Object> translations = out.translations();
        assertEquals("a", translations.get("baubles.initialize.message"));
        assertEquals("Skull", translations.get("baubles.wormhole_inventory.skull_name"));

        YamlConfiguration settings = out.settings();
        assertEquals(6, settings.getInt("WormholeInventory.Rows"));

        assertNotNull(out.mobDrops().get("MobDrops.ZOMBIE.eye.Chance"));
    }

    @Test
    void comfort_manifest_routes_correctly() {
        ComfortManifest manifest = new ComfortManifest();

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("Messages.NoComfort", "&7cold");
        legacy.set("Categories.Bed.Material", "WHITE_BED");
        legacy.set("Categories.Bed.Points", 5);

        ModuleSplitResult out = manifest.split(legacy, null);

        Map<String, Object> translations = out.translations();
        assertNotNull(translations.get("comfort.messages.no_comfort"));

        YamlConfiguration settings = out.settings();
        assertEquals("WHITE_BED", settings.getString("Categories.Bed.Material"));
        assertEquals(5, settings.getInt("Categories.Bed.Points"));
    }

    @Test
    void food_expansion_manifest_routes_correctly() {
        FoodExpansionManifest manifest = new FoodExpansionManifest();

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("FoodExpansion.Overeating.Messages.WarningText", "full");
        legacy.set("FoodExpansion.CustomFoods.bacon.DisplayName", "Bacon");
        legacy.set("FoodExpansion.CustomFoods.bacon.Saturation", 5);

        ModuleSplitResult out = manifest.split(legacy, null);

        Map<String, Object> translations = out.translations();
        assertNotNull(translations.get("foodexpansion.food_expansion.overeating.messages.warning_text"));
        assertNotNull(translations.get("foodexpansion.food_expansion.custom_foods.bacon.display_name"));

        YamlConfiguration settings = out.settings();
        assertEquals(5, settings.getInt("FoodExpansion.CustomFoods.bacon.Saturation"));
    }

    @Test
    void fear_manifest_routes_correctly() {
        FearManifest manifest = new FearManifest();

        YamlConfiguration legacy = new YamlConfiguration();
        legacy.set("Initialize.Message", "init");
        legacy.set("CharacterOverrides.FearBar5", "|");
        legacy.set("CharacterOverrides.FearActionbar", "actbar");
        legacy.set("FearMeter.Capacity", 100);

        ModuleSplitResult out = manifest.split(legacy, null);

        Map<String, Object> translations = out.translations();
        assertEquals("init", translations.get("fear.initialize.message"));
        assertEquals("|", translations.get("fear.character_overrides.fear_bar5"));
        assertEquals("actbar", translations.get("fear.character_overrides.fear_actionbar"));

        YamlConfiguration settings = out.settings();
        assertEquals(100, settings.getInt("FearMeter.Capacity"));
    }
}
