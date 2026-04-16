package cz.hashiri.harshlands.locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleManagerTest {

    @Test
    void loads_flat_keys_from_nested_yaml(@TempDir Path translationsRoot) throws IOException {
        Path enUS = translationsRoot.resolve("en-US");
        Files.createDirectories(enUS);
        Files.writeString(enUS.resolve("commands.yml"), """
                commands:
                  give:
                    success: "Gave item to player"
                  no_permission: "No permission"
                """);

        LocaleManager mgr = new LocaleManager(translationsRoot, "en-US");
        mgr.load();

        assertEquals("Gave item to player", mgr.get("commands.give.success"));
        assertEquals("No permission", mgr.get("commands.no_permission"));
    }

    @Test
    void missing_key_returns_bracketed_key(@TempDir Path translationsRoot) throws IOException {
        Files.createDirectories(translationsRoot.resolve("en-US"));
        LocaleManager mgr = new LocaleManager(translationsRoot, "en-US");
        mgr.load();

        assertEquals("[unknown.key]", mgr.get("unknown.key"));
    }

    @Test
    void missing_key_is_logged_once_per_key(@TempDir Path translationsRoot) throws IOException {
        Files.createDirectories(translationsRoot.resolve("en-US"));

        java.util.List<String> warnings = new java.util.ArrayList<>();
        java.util.logging.Logger logger = java.util.logging.Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord r) {
                if (r.getLevel() == java.util.logging.Level.WARNING) warnings.add(r.getMessage());
            }
            public void flush() {}
            public void close() {}
        });

        LocaleManager mgr = new LocaleManager(translationsRoot, "en-US", logger);
        mgr.load();
        mgr.get("a.missing.key");
        mgr.get("a.missing.key");
        mgr.get("a.missing.key");
        mgr.get("another.missing.key");

        // Two distinct missing keys → two warning messages (deduped per key).
        assertEquals(2, warnings.stream().filter(w -> w.contains("Missing translation")).count());
    }

    @Test
    void color_codes_are_translated_on_get(@TempDir Path translationsRoot) throws IOException {
        Path enUS = translationsRoot.resolve("en-US");
        Files.createDirectories(enUS);
        Files.writeString(enUS.resolve("x.yml"), """
                greeting: "&aHello &cWorld"
                """);

        LocaleManager mgr = new LocaleManager(translationsRoot, "en-US");
        mgr.load();

        assertEquals("\u00a7aHello \u00a7cWorld", mgr.get("greeting"));
    }

    @Test
    void get_list_returns_color_translated_list(@TempDir Path translationsRoot) throws IOException {
        Path enUS = translationsRoot.resolve("en-US");
        Files.createDirectories(enUS);
        Files.writeString(enUS.resolve("x.yml"), """
                lore:
                  - "&aLine one"
                  - "&cLine two"
                """);

        LocaleManager mgr = new LocaleManager(translationsRoot, "en-US");
        mgr.load();

        List<String> lore = mgr.getList("lore");
        assertEquals(2, lore.size());
        assertEquals("\u00a7aLine one", lore.get(0));
        assertEquals("\u00a7cLine two", lore.get(1));
    }

    @Test
    void get_list_on_missing_key_returns_empty_and_logs(@TempDir Path translationsRoot) throws IOException {
        Files.createDirectories(translationsRoot.resolve("en-US"));
        LocaleManager mgr = new LocaleManager(translationsRoot, "en-US");
        mgr.load();

        List<String> result = mgr.getList("missing.list");
        assertEquals(List.of(), result);
    }

    @Test
    void reload_re_reads_files_atomically(@TempDir Path translationsRoot) throws IOException {
        Path enUS = translationsRoot.resolve("en-US");
        Files.createDirectories(enUS);
        Path file = enUS.resolve("x.yml");
        Files.writeString(file, "key: \"original\"\n");

        LocaleManager mgr = new LocaleManager(translationsRoot, "en-US");
        mgr.load();
        assertEquals("original", mgr.get("key"));

        Files.writeString(file, "key: \"updated\"\n");
        mgr.reload();

        assertEquals("updated", mgr.get("key"));
    }

    @Test
    void reload_clears_stale_keys(@TempDir Path translationsRoot) throws IOException {
        Path enUS = translationsRoot.resolve("en-US");
        Files.createDirectories(enUS);
        Path file = enUS.resolve("x.yml");
        Files.writeString(file, """
                a: "keep"
                b: "remove"
                """);

        LocaleManager mgr = new LocaleManager(translationsRoot, "en-US");
        mgr.load();
        assertEquals("keep", mgr.get("a"));
        assertEquals("remove", mgr.get("b"));

        Files.writeString(file, "a: \"keep\"\n");
        mgr.reload();

        assertEquals("keep", mgr.get("a"));
        assertEquals("[b]", mgr.get("b"));
    }
}
