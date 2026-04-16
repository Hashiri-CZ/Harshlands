package cz.hashiri.harshlands.locale;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesTest {

    @BeforeEach
    void reset() {
        Messages.reset();
    }

    @Test
    void get_delegates_to_bound_locale_manager(@TempDir Path root) throws IOException {
        Path enUS = root.resolve("en-US");
        Files.createDirectories(enUS);
        Files.writeString(enUS.resolve("x.yml"), "hello: \"world\"\n");
        Messages.bind(new LocaleManager(root, "en-US"));
        Messages.reload();

        assertEquals("world", Messages.get("hello"));
    }

    @Test
    void get_with_placeholders_substitutes(@TempDir Path root) throws IOException {
        Path enUS = root.resolve("en-US");
        Files.createDirectories(enUS);
        Files.writeString(enUS.resolve("x.yml"), "greet: \"Hello %player%, you have %count% items\"\n");
        Messages.bind(new LocaleManager(root, "en-US"));
        Messages.reload();

        String out = Messages.get("greet", Map.of("player", "Steve", "count", 3));
        assertEquals("Hello Steve, you have 3 items", out);
    }

    @Test
    void get_list_delegates(@TempDir Path root) throws IOException {
        Path enUS = root.resolve("en-US");
        Files.createDirectories(enUS);
        Files.writeString(enUS.resolve("x.yml"), """
                lore:
                  - "a"
                  - "b"
                """);
        Messages.bind(new LocaleManager(root, "en-US"));
        Messages.reload();

        assertEquals(List.of("a", "b"), Messages.getList("lore"));
    }

    @Test
    void builder_with_chain_substitutes_placeholders(@TempDir Path root) throws IOException {
        Path enUS = root.resolve("en-US");
        Files.createDirectories(enUS);
        Files.writeString(enUS.resolve("x.yml"), "give: \"Gave %amount% %item% to %player%\"\n");
        Messages.bind(new LocaleManager(root, "en-US"));
        Messages.reload();

        String out = Messages.of("give")
                .with("amount", 5)
                .with("item", "canteen")
                .with("player", "Steve")
                .build();

        assertEquals("Gave 5 canteen to Steve", out);
    }

    @Test
    void builder_send_on_null_recipient_is_a_noop() {
        Messages.of("any.key").send(null); // must not throw
    }

    @Test
    void missing_key_without_bound_manager_returns_bracket_key() {
        Messages.reset();
        assertEquals("[x.y.z]", Messages.get("x.y.z"));
    }
}
