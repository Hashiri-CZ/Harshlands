package cz.hashiri.harshlands.migration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class FolderLayoutMigrationTest {

    @Test
    void detects_no_legacy_as_noop(@TempDir Path dataFolder) {
        FolderLayoutMigration mig = new FolderLayoutMigration(dataFolder, Logger.getAnonymousLogger());
        assertFalse(mig.needsMigration());
    }

    @Test
    void detects_legacy_when_root_module_yaml_present(@TempDir Path dataFolder) throws IOException {
        Files.writeString(dataFolder.resolve("toughasnails.yml"), "Initialize:\n  Enabled: true\n");
        FolderLayoutMigration mig = new FolderLayoutMigration(dataFolder, Logger.getAnonymousLogger());
        assertTrue(mig.needsMigration());
    }

    @Test
    void split_produces_target_layout_and_renames_legacy(@TempDir Path dataFolder) throws IOException {
        Files.writeString(dataFolder.resolve("toughasnails.yml"), """
                Initialize:
                  Enabled: true
                  Message: "&6Init"
                Dehydration:
                  Timer: 200
                MobDrops:
                  ZOMBIE:
                    sandstone_dust:
                      Chance: 0.5
                """);

        FolderLayoutMigration mig = new FolderLayoutMigration(dataFolder, Logger.getAnonymousLogger());
        mig.run();

        assertTrue(Files.exists(dataFolder.resolve("Settings/toughasnails.yml")));
        assertTrue(Files.exists(dataFolder.resolve("Translations/en-US/toughasnails.yml")));
        assertTrue(Files.exists(dataFolder.resolve("Items/toughasnails/mob_drops.yml")));
        assertTrue(Files.exists(dataFolder.resolve("toughasnails.yml.migrated")));
        assertFalse(Files.exists(dataFolder.resolve("toughasnails.yml")));

        // Settings keeps tuning, drops messages & drops
        String settings = Files.readString(dataFolder.resolve("Settings/toughasnails.yml"));
        assertTrue(settings.contains("Timer: 200"));
        assertFalse(settings.contains("&6Init"));
        assertFalse(settings.contains("MobDrops"));

        // Translations has the message, keyed by module.path
        String translations = Files.readString(dataFolder.resolve("Translations/en-US/toughasnails.yml"));
        assertTrue(translations.contains("&6Init"));
    }

    @Test
    void migration_is_idempotent(@TempDir Path dataFolder) throws IOException {
        Files.writeString(dataFolder.resolve("toughasnails.yml"), "Initialize:\n  Enabled: true\n");
        FolderLayoutMigration mig = new FolderLayoutMigration(dataFolder, Logger.getAnonymousLogger());
        mig.run();

        // Running again is a no-op — legacy is gone, Settings/ exists.
        FolderLayoutMigration mig2 = new FolderLayoutMigration(dataFolder, Logger.getAnonymousLogger());
        assertFalse(mig2.needsMigration());
        mig2.run(); // must not throw
    }

    @Test
    void database_file_is_moved(@TempDir Path dataFolder) throws IOException {
        Files.writeString(dataFolder.resolve("data.mv.db"), "fake-h2-bytes");
        Files.writeString(dataFolder.resolve("toughasnails.yml"), "Initialize:\n  Enabled: true\n");

        new FolderLayoutMigration(dataFolder, Logger.getAnonymousLogger()).run();

        assertTrue(Files.exists(dataFolder.resolve("Data/data.mv.db")));
        assertFalse(Files.exists(dataFolder.resolve("data.mv.db")));
    }
}
