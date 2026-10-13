package cz.hashiri.harshlands.utils;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item ids are a single GLOBAL namespace, not per-module.
 *
 * <p>{@code HLItem} writes the config key into the {@code hlitem} NBT tag and stores the item
 * in one static {@code itemMap}, so two modules declaring the same key produce items that are
 * indistinguishable to every {@code getNameFromItem} check in the codebase. The second module
 * to initialise silently wins the map, and BOTH modules' right-click handlers then fire for
 * BOTH items.
 *
 * <p>This shipped with FirstAid and Disease both declaring {@code medical_kit}: one right-click
 * healed every limb AND ran a disease diagnosis. Nothing logged a warning.
 */
class ShippedItemIdCollisionTest {

    private static List<Path> shippedItemFiles() throws Exception {
        java.net.URL url = ShippedItemIdCollisionTest.class.getClassLoader().getResource("Items");
        assertNotNull(url, "Items/ must ship");
        Path itemsRoot = Path.of(url.toURI());

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> modules = Files.newDirectoryStream(itemsRoot)) {
            for (Path moduleDir : modules) {
                if (!Files.isDirectory(moduleDir)) continue;
                Path items = moduleDir.resolve("items.yml");
                if (Files.isRegularFile(items)) files.add(items);
            }
        }
        assertFalse(files.isEmpty(), "expected at least one Items/<module>/items.yml");
        return files;
    }

    @Test
    void no_item_id_is_declared_by_two_modules() throws Exception {
        Map<String, List<String>> owners = new HashMap<>();

        for (Path file : shippedItemFiles()) {
            String module = file.getParent().getFileName().toString();
            YamlConfiguration cfg = new YamlConfiguration();
            try (InputStreamReader reader = new InputStreamReader(
                    Files.newInputStream(file), StandardCharsets.UTF_8)) {
                cfg.load(reader);
            }
            for (String key : cfg.getKeys(false)) {
                // Only real item definitions are sections; ConfigId and friends are scalars.
                ConfigurationSection section = cfg.getConfigurationSection(key);
                if (section == null) continue;
                owners.computeIfAbsent(key, k -> new ArrayList<>()).add(module);
            }
        }

        List<String> collisions = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : owners.entrySet()) {
            if (entry.getValue().size() > 1) {
                collisions.add(entry.getKey() + " declared by " + entry.getValue());
            }
        }
        assertTrue(collisions.isEmpty(),
            "item ids must be unique across every module:\n  " + String.join("\n  ", collisions));
    }

    @Test
    void the_disease_diagnostic_item_does_not_reuse_the_firstaid_id() throws Exception {
        Map<String, String> byModule = new HashMap<>();
        for (Path file : shippedItemFiles()) {
            String module = file.getParent().getFileName().toString();
            YamlConfiguration cfg = new YamlConfiguration();
            try (InputStreamReader reader = new InputStreamReader(
                    Files.newInputStream(file), StandardCharsets.UTF_8)) {
                cfg.load(reader);
            }
            for (String key : cfg.getKeys(false)) {
                if (cfg.getConfigurationSection(key) != null) byModule.put(module + "/" + key, key);
            }
        }

        assertTrue(byModule.containsKey("firstaid/medical_kit"),
            "FirstAid keeps medical_kit — it is the established owner");
        assertFalse(byModule.containsKey("disease/medical_kit"),
            "Disease must not reuse medical_kit; its diagnostic item is diagnostic_kit");
        assertTrue(byModule.containsKey("disease/diagnostic_kit"),
            "the disease diagnostic item must ship as diagnostic_kit");
    }

    @Test
    void the_diagnostic_item_id_matches_what_the_disease_listener_looks_for() throws Exception {
        // The listener compares against a Java constant while the item comes from YAML;
        // renaming one without the other yields an item that simply does nothing.
        java.net.URL url = getClass().getClassLoader().getResource("Items/disease/items.yml");
        assertNotNull(url);
        YamlConfiguration cfg = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(Path.of(url.toURI())), StandardCharsets.UTF_8)) {
            cfg.load(reader);
        }
        assertNotNull(
            cfg.getConfigurationSection(
                cz.hashiri.harshlands.disease.DiseaseEvents.DIAGNOSTIC_ITEM_ID),
            "Items/disease/items.yml must define '"
                + cz.hashiri.harshlands.disease.DiseaseEvents.DIAGNOSTIC_ITEM_ID + "'");
    }

    @Test
    void every_disease_cure_item_referenced_by_settings_actually_exists() throws Exception {
        java.net.URL itemsUrl = getClass().getClassLoader().getResource("Items/disease/items.yml");
        java.net.URL settingsUrl = getClass().getClassLoader().getResource("Settings/disease.yml");
        assertNotNull(itemsUrl);
        assertNotNull(settingsUrl);

        YamlConfiguration items = new YamlConfiguration();
        try (InputStreamReader r = new InputStreamReader(
                Files.newInputStream(Path.of(itemsUrl.toURI())), StandardCharsets.UTF_8)) {
            items.load(r);
        }
        YamlConfiguration settings = new YamlConfiguration();
        try (InputStreamReader r = new InputStreamReader(
                Files.newInputStream(Path.of(settingsUrl.toURI())), StandardCharsets.UTF_8)) {
            settings.load(r);
        }

        ConfigurationSection diseases = settings.getConfigurationSection("Diseases");
        assertNotNull(diseases);
        for (String id : diseases.getKeys(false)) {
            String cure = diseases.getString(id + ".Cure.ItemId");
            if (cure == null || cure.isBlank()) continue; // diseases with no item cure
            assertNotNull(items.getConfigurationSection(cure),
                "disease '" + id + "' names cure item '" + cure + "' which no items.yml defines");
        }
    }
}
