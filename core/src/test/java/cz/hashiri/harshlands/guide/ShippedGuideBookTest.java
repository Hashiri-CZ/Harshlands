/*
    Copyright (C) 2026  Hashiri_
    GNU GPL v3.
 */
package cz.hashiri.harshlands.guide;

import cz.hashiri.harshlands.locale.LocaleManager;
import cz.hashiri.harshlands.locale.Messages;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the REAL shipped guide book, not a fixture.
 *
 * <p>{@link GuideModule} already runs the line/width checks at startup, but only as log
 * warnings on a running server — so an overflowing page ships silently and is only noticed by
 * a player looking at a mangled book. These assertions move that check to build time.
 */
class ShippedGuideBookTest {

    /**
     * Binds the real shipped translations before each test. Without this, {@code HintSender}
     * cannot resolve {@code hints.Obtain.<key>.Name} and silently falls back to a title-cased
     * key — which renders far wider than the real display name ("Purified Water Bottle" vs
     * "Pure Water"), so page widths measured here would be fiction.
     */
    @BeforeEach
    void bindShippedLocale() throws Exception {
        java.net.URL url = getClass().getClassLoader().getResource("Translations/en-US/hints.yml");
        assertNotNull(url, "Translations/en-US/hints.yml must ship");
        Path enUS = Path.of(url.toURI()).getParent();
        Messages.bind(new LocaleManager(enUS.getParent(), enUS.getFileName().toString()));
        Messages.reload();

        assertFalse(Messages.get("hints.Obtain.purified_water_bottle.Name").startsWith("[hints."),
            "locale binding failed — every width below would be measured against fallback names");
    }

    @AfterEach
    void unbindLocale() {
        Messages.reset();
    }

    private static YamlConfiguration shippedGuide() throws Exception {
        try (InputStream in = ShippedGuideBookTest.class.getClassLoader()
                .getResourceAsStream("Translations/en-US/guide.yml")) {
            assertNotNull(in, "Translations/en-US/guide.yml must ship");
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return cfg;
        }
    }

    private static List<String> plainLines(BaseComponent[] page) {
        String plain = new TextComponent(page).toPlainText();
        return List.of(plain.split("\n", -1));
    }

    @Test
    void no_shipped_page_overflows_the_book() throws Exception {
        List<BaseComponent[]> pages = GuideBookBuilder.buildPages(shippedGuide());
        assertFalse(pages.isEmpty(), "the shipped guide must produce pages");

        List<String> problems = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            List<String> lines = plainLines(pages.get(i));
            if (lines.size() > 14) {
                problems.add("page " + (i + 1) + " has " + lines.size() + " lines (max 14)");
            }
            for (int l = 0; l < lines.size(); l++) {
                if (BookLineMetrics.exceedsBookLine(lines.get(l))) {
                    problems.add("page " + (i + 1) + " line " + (l + 1) + " is "
                        + BookLineMetrics.pixelWidth(lines.get(l)) + "px (max 114): \""
                        + lines.get(l) + "\"");
                }
            }
        }
        assertTrue(problems.isEmpty(), "shipped guide pages overflow:\n  " + String.join("\n  ", problems));
    }

    @Test
    void every_contents_entry_resolves_to_a_real_chapter() throws Exception {
        ConfigurationSection root = shippedGuide().getConfigurationSection("guide");
        assertNotNull(root);
        ConfigurationSection chapters = root.getConfigurationSection("Chapters");
        assertNotNull(chapters);

        List<Map<?, ?>> entries = root.getMapList("Contents.Entries");
        assertFalse(entries.isEmpty(), "Contents.Entries must not be empty");

        for (Map<?, ?> entry : entries) {
            Object key = entry.get("key");
            assertNotNull(key, "every Contents entry needs a key");
            // GuideBookBuilder SILENTLY SKIPS a ToC entry whose chapter is missing, which
            // would renumber every later chapter's link target without any visible error.
            assertFalse(chapters.getStringList(key.toString()).isEmpty(),
                "Contents references chapter '" + key + "' but Chapters has no such (non-empty) key");
        }
    }

    @Test
    void the_disease_chapter_ships_and_is_reachable() throws Exception {
        ConfigurationSection root = shippedGuide().getConfigurationSection("guide");
        assertNotNull(root);

        assertFalse(root.getConfigurationSection("Chapters").getStringList("disease").isEmpty(),
            "the disease chapter must ship");

        boolean listed = false;
        for (Map<?, ?> entry : root.getMapList("Contents.Entries")) {
            if ("disease".equals(String.valueOf(entry.get("key")))) listed = true;
        }
        assertTrue(listed, "the disease chapter must be listed in Contents, or it is unreachable");
    }

    @Test
    void contents_labels_are_numbered_contiguously_from_one() throws Exception {
        ConfigurationSection root = shippedGuide().getConfigurationSection("guide");
        List<Map<?, ?>> entries = root.getMapList("Contents.Entries");

        // Labels look like "&010. Sickness"; inserting a chapter mid-book is easy to do
        // without renumbering the ones after it.
        int expected = 1;
        for (Map<?, ?> entry : entries) {
            String label = org.bukkit.ChatColor.stripColor(
                org.bukkit.ChatColor.translateAlternateColorCodes('&', String.valueOf(entry.get("label"))));
            int dot = label.indexOf('.');
            assertTrue(dot > 0, "Contents label has no leading number: " + label);
            assertEquals(expected, Integer.parseInt(label.substring(0, dot).trim()),
                "Contents numbering jumped at label: " + label);
            expected++;
        }
    }
}
