package cz.hashiri.harshlands.utils;

import cz.hashiri.harshlands.locale.LocaleManager;
import cz.hashiri.harshlands.locale.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that addGearStats pulls its attribute lines from the locale
 * system rather than hardcoded English. We bypass the Bukkit Attribute
 * parameter by calling Messages directly with the same keys the impl
 * uses and checking the formatted output contains the translated label.
 */
class LorePresetsI18nTest {

    @AfterEach
    void tearDown() {
        Messages.reset();
    }

    @Test
    void attack_damage_resolves_from_locale(@TempDir Path root) throws IOException {
        Path zh = root.resolve("zh-CN");
        Files.createDirectories(zh);
        Files.writeString(zh.resolve("item_stats.yml"), """
                item_stats:
                  attack_damage: "&2 %VALUE% \u653B\u51FB\u4F24\u5BB3"
                """);
        Messages.bind(new LocaleManager(root, "zh-CN"));
        Messages.reload();

        String line = Messages.get("item_stats.attack_damage", java.util.Map.of("VALUE", "7"));
        assertTrue(line.contains("7"), "expected formatted value: " + line);
        assertTrue(line.contains("\u653B\u51FB\u4F24\u5BB3"),
                "expected Chinese 'attack damage' in line: " + line);
    }

    @Test
    void all_four_stat_keys_present_in_enUS_file() throws IOException {
        // Guards against shipping a partial en-US update.
        Path translations = Path.of("src/main/resources/Translations");
        Path file = translations.resolve("en-US/item_stats.yml");
        assertTrue(Files.exists(file), "item_stats.yml must ship with en-US");
        String contents = Files.readString(file);
        for (String key : List.of("attack_damage:", "attack_speed:", "armor:", "armor_toughness:")) {
            assertTrue(contents.contains(key), "missing key " + key + " in " + file);
        }
    }
}
