package cz.hashiri.harshlands.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shipped {@code ConfigId} must never be NEWER than the plugin version.
 *
 * <p>{@link HLConfig#createConfig()} only merges new keys into an existing on-disk file when
 * {@code isOlderVersion(onDiskConfigId, pluginVersion)} is true. A shipped ConfigId ahead of the
 * plugin version makes that permanently false, so every future addition to that file is silently
 * never delivered to existing installs — no error, no warning, the file just quietly stops
 * updating. {@code Items/disease/*.yml} once shipped with a ConfigId one patch ahead of the
 * plugin version and would have stayed frozen indefinitely.
 */
class ShippedConfigIdTest {

    /** Reads the Maven-filtered plugin.yml from the classpath, so this tracks the real version. */
    private static String pluginVersion() throws Exception {
        try (var in = ShippedConfigIdTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertNotNull(in, "plugin.yml must be on the test classpath");
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            String version = cfg.getString("version");
            assertNotNull(version, "plugin.yml must declare a version");
            assertFalse(version.contains("${"),
                "plugin.yml version is unfiltered ('" + version + "') — run against target/classes");
            return version;
        }
    }

    private static Path resourceRoot() throws Exception {
        java.net.URL url = ShippedConfigIdTest.class.getClassLoader().getResource("plugin.yml");
        assertNotNull(url);
        return Path.of(url.toURI()).getParent();
    }

    /** @return negative if a &lt; b, 0 if equal, positive if a &gt; b. Mirrors HLConfig's parsing. */
    private static int compareVersions(String a, String b) {
        String[] left = a.replace("-RELEASE", "").trim().split("\\.");
        String[] right = b.replace("-RELEASE", "").trim().split("\\.");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int l = i < left.length ? parseIntSafe(left[i]) : 0;
            int r = i < right.length ? parseIntSafe(right[i]) : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    @Test
    void no_shipped_config_id_is_newer_than_the_plugin_version() throws Exception {
        String version = pluginVersion();
        Path root = resourceRoot();

        List<String> ahead = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                YamlConfiguration cfg = new YamlConfiguration();
                try (InputStreamReader reader = new InputStreamReader(
                        Files.newInputStream(file), StandardCharsets.UTF_8)) {
                    cfg.load(reader);
                } catch (Exception malformed) {
                    continue; // not our concern here; other tests parse the files they own
                }
                String configId = cfg.getString("ConfigId");
                if (configId == null || configId.isBlank()) continue;

                if (compareVersions(configId, version) > 0) {
                    ahead.add(root.relativize(file) + " has ConfigId " + configId
                        + " but the plugin is " + version);
                }
            }
        }

        assertTrue(ahead.isEmpty(),
            "these files will never auto-update on an existing install:\n  "
                + String.join("\n  ", ahead));
    }

    @Test
    void the_disease_item_configs_track_the_current_version() throws Exception {
        String version = pluginVersion();
        for (String resource : List.of("Items/disease/items.yml", "Items/disease/recipes.yml")) {
            try (var in = getClass().getClassLoader().getResourceAsStream(resource)) {
                assertNotNull(in, resource + " must ship");
                YamlConfiguration cfg = new YamlConfiguration();
                cfg.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                assertTrue(compareVersions(cfg.getString("ConfigId", ""), version) <= 0,
                    resource + " ConfigId must not be ahead of " + version);
            }
        }
    }
}
