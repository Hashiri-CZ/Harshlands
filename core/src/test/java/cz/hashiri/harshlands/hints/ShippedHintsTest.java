package cz.hashiri.harshlands.hints;

import cz.hashiri.harshlands.locale.LocaleManager;
import cz.hashiri.harshlands.locale.Messages;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the three places a hint lives in sync: the {@link HintKey} enum, its default entry in
 * {@code Settings/hints.yml}, and its text in {@code Translations/en-US/hints.yml}.
 *
 * <p>A hint missing its translation still fires — it just prints the literal
 * {@code [hints.Hints.Foo]} fallback at the player. A missing {@code %item_<key>%} Obtain entry
 * still renders, but clicking the tag answers "Unknown item". Both are invisible until someone
 * happens to trigger that exact hint in-game.
 */
class ShippedHintsTest {

    private static final Pattern ITEM_TOKEN = Pattern.compile("%item_(\\w+)%");

    @BeforeEach
    void bindShippedLocale() throws Exception {
        Path enUS = localeDir();
        Messages.bind(new LocaleManager(enUS.getParent(), enUS.getFileName().toString()));
        Messages.reload();
    }

    @AfterEach
    void unbind() {
        Messages.reset();
    }

    private static Path localeDir() throws Exception {
        java.net.URL url = ShippedHintsTest.class.getClassLoader()
            .getResource("Translations/en-US/hints.yml");
        assertNotNull(url, "Translations/en-US/hints.yml must ship");
        return Path.of(url.toURI()).getParent();
    }

    private static YamlConfiguration load(String resource) throws Exception {
        try (InputStream in = ShippedHintsTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, resource + " must ship");
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return cfg;
        }
    }

    @Test
    void every_hint_key_has_shipped_text() {
        List<String> missing = new ArrayList<>();
        for (HintKey key : HintKey.values()) {
            String path = "hints.Hints." + key.translationKey();
            if (Messages.get(path).equals("[" + path + "]")) missing.add(key.name() + " -> " + path);
        }
        assertTrue(missing.isEmpty(), "HintKeys with no shipped text:\n  " + String.join("\n  ", missing));
    }

    @Test
    void every_hint_key_has_a_default_entry_in_settings() throws Exception {
        ConfigurationSection hints = load("Settings/hints.yml").getConfigurationSection("Hints");
        assertNotNull(hints, "Settings/hints.yml must have a Hints section");

        List<String> missing = new ArrayList<>();
        for (HintKey key : HintKey.values()) {
            // HintSender reads Hints.<ENUM_NAME>.Enabled; without the entry the hint still
            // defaults to on, but an admin has no way to discover or disable it.
            if (!hints.contains(key.name())) missing.add(key.name());
        }
        assertTrue(missing.isEmpty(), "HintKeys absent from Settings/hints.yml: " + missing);
    }

    @Test
    void the_disease_hints_ship() {
        for (HintKey key : List.of(HintKey.FIRST_SICKNESS_ONSET, HintKey.FIRST_SICKNESS_WORSENED,
                                   HintKey.FIRST_SICKNESS_TERMINAL, HintKey.FIRST_SICKNESS_CURED)) {
            String text = Messages.get("hints.Hints." + key.translationKey());
            assertFalse(text.startsWith("[hints."), key.name() + " has no shipped text");
        }
    }

    @Test
    void disease_hints_never_name_the_illness() {
        // The diagnostic kit is the only thing that should identify an illness; a hint that
        // named it would make the craftable item pointless.
        List<String> names = List.of("Respiratory", "Septicemia", "Endersion", "Crimson",
            "Wasting Blight", "Festering", "Tetanus", "Malnutrition", "Dysentery",
            "Battle Trauma", "Sybok");

        for (HintKey key : List.of(HintKey.FIRST_SICKNESS_ONSET, HintKey.FIRST_SICKNESS_WORSENED,
                                   HintKey.FIRST_SICKNESS_TERMINAL, HintKey.FIRST_SICKNESS_CURED)) {
            String text = Messages.get("hints.Hints." + key.translationKey());
            for (String name : names) {
                assertFalse(text.contains(name),
                    key.name() + " names '" + name + "' — that is the diagnostic kit's job");
            }
        }
    }

    @Test
    void every_item_token_used_anywhere_resolves_to_an_obtain_entry() throws Exception {
        Set<String> referenced = new LinkedHashSet<>();
        // Both files feed the same HintSender renderer, so both can emit a dead tag.
        for (String file : List.of("hints.yml", "guide.yml")) {
            String raw = Files.readString(localeDir().resolve(file), StandardCharsets.UTF_8);
            Matcher m = ITEM_TOKEN.matcher(raw);
            while (m.find()) referenced.add(m.group(1).toLowerCase());
        }
        assertFalse(referenced.isEmpty(), "expected item tokens in the shipped text");

        Set<String> known = Messages.getKeys("hints.Obtain");
        List<String> dead = new ArrayList<>();
        for (String key : referenced) {
            if (!known.contains(key)) dead.add(key);
        }
        assertTrue(dead.isEmpty(),
            "%item_<key>% tokens with no hints.Obtain entry (clicking them says \"Unknown item\"): " + dead);
    }

    @Test
    void every_obtain_entry_has_a_name_and_lines() {
        List<String> broken = new ArrayList<>();
        for (String key : Messages.getKeys("hints.Obtain")) {
            if (Messages.get("hints.Obtain." + key + ".Name").startsWith("[hints.")) {
                broken.add(key + " (no Name)");
            }
            if (Messages.getList("hints.Obtain." + key + ".Lines").isEmpty()) {
                broken.add(key + " (no Lines)");
            }
        }
        assertTrue(broken.isEmpty(), "malformed hints.Obtain entries: " + broken);
    }
}
