package cz.hashiri.harshlands.migration;

import cz.hashiri.harshlands.migration.manifests.BaublesManifest;
import cz.hashiri.harshlands.migration.manifests.ComfortManifest;
import cz.hashiri.harshlands.migration.manifests.DynamicSurroundingsManifest;
import cz.hashiri.harshlands.migration.manifests.FearManifest;
import cz.hashiri.harshlands.migration.manifests.FirstAidManifest;
import cz.hashiri.harshlands.migration.manifests.FoodExpansionManifest;
import cz.hashiri.harshlands.migration.manifests.IceAndFireManifest;
import cz.hashiri.harshlands.migration.manifests.IntegrationsManifest;
import cz.hashiri.harshlands.migration.manifests.NoTreePunchingManifest;
import cz.hashiri.harshlands.migration.manifests.SpartanAndFireManifest;
import cz.hashiri.harshlands.migration.manifests.SpartanWeaponryManifest;
import cz.hashiri.harshlands.migration.manifests.ToughAsNailsManifest;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FolderLayoutMigration {

    private static final List<ModuleManifest> MANIFESTS = List.of(
            new ToughAsNailsManifest(),
            new BaublesManifest(),
            new FearManifest(),
            new IceAndFireManifest(),
            new SpartanWeaponryManifest(),
            new SpartanAndFireManifest(),
            new FoodExpansionManifest(),
            new ComfortManifest(),
            new NoTreePunchingManifest(),
            new FirstAidManifest(),
            new DynamicSurroundingsManifest(),
            new IntegrationsManifest()
    );

    private final Path dataFolder;
    private final Logger logger;

    public FolderLayoutMigration(Path dataFolder, Logger logger) {
        this.dataFolder = dataFolder;
        this.logger = logger;
    }

    public boolean needsMigration() {
        if (Files.exists(dataFolder.resolve("data.mv.db"))) return true;
        for (ModuleManifest m : MANIFESTS) {
            if (Files.exists(dataFolder.resolve(m.moduleId() + ".yml"))) return true;
        }
        if (Files.exists(dataFolder.resolve("commands.yml"))) return true;
        if (Files.exists(dataFolder.resolve("lorepresets.yml"))) return true;
        if (Files.exists(dataFolder.resolve("auraskills_requirements.yml"))) return true;
        return Files.isDirectory(dataFolder.resolve("resources"));
    }

    public void run() {
        if (!needsMigration()) return;
        int migratedFiles = 0;
        try {
            for (ModuleManifest m : MANIFESTS) {
                Path legacy = dataFolder.resolve(m.moduleId() + ".yml");
                if (!Files.exists(legacy)) continue;
                splitAndWrite(m, legacy);
                Files.move(legacy, dataFolder.resolve(m.moduleId() + ".yml.migrated"),
                        StandardCopyOption.REPLACE_EXISTING);
                migratedFiles++;
            }
            migrateScalarFiles();
            migrateResourcesDir();
            migrateDatabase();
            logger.info("Harshlands: migrated " + migratedFiles + " config files + database to new folder layout");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Folder layout migration failed", e);
            throw new RuntimeException("Folder layout migration failed: " + e.getMessage(), e);
        }
    }

    private void splitAndWrite(ModuleManifest m, Path legacy) throws IOException {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacy.toFile());
        ModuleSplitResult result = m.split(yaml, dataFolder);

        Files.createDirectories(dataFolder.resolve("Settings"));
        result.settings().save(dataFolder.resolve("Settings/" + m.moduleId() + ".yml").toFile());

        Files.createDirectories(dataFolder.resolve("Translations/en-US"));
        YamlConfiguration translationsYaml = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : result.translations().entrySet()) {
            translationsYaml.set(entry.getKey(), entry.getValue());
        }
        translationsYaml.save(dataFolder.resolve("Translations/en-US/" + m.moduleId() + ".yml").toFile());

        if (!result.mobDrops().getKeys(false).isEmpty()) {
            Files.createDirectories(dataFolder.resolve("Items/" + m.moduleId()));
            result.mobDrops().save(dataFolder.resolve("Items/" + m.moduleId() + "/mob_drops.yml").toFile());
        }
        if (!result.blockDrops().getKeys(false).isEmpty()) {
            Files.createDirectories(dataFolder.resolve("Items/" + m.moduleId()));
            result.blockDrops().save(dataFolder.resolve("Items/" + m.moduleId() + "/block_drops.yml").toFile());
        }
    }

    private void migrateScalarFiles() throws IOException {
        // commands.yml → Settings/commands.yml (legacy file has mixed tuning + messages;
        // Translations/en-US/commands.yml ships separately with the normalized messages)
        Path commands = dataFolder.resolve("commands.yml");
        if (Files.exists(commands)) {
            Files.createDirectories(dataFolder.resolve("Settings"));
            Files.move(commands, dataFolder.resolve("Settings/commands.yml"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(dataFolder.resolve("commands.yml.migrated"),
                    "# Moved to Settings/commands.yml\n");
        }

        // lorepresets.yml → Presets/lore.yml
        Path lorepresets = dataFolder.resolve("lorepresets.yml");
        if (Files.exists(lorepresets)) {
            Files.createDirectories(dataFolder.resolve("Presets"));
            Files.move(lorepresets, dataFolder.resolve("Presets/lore.yml"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(dataFolder.resolve("lorepresets.yml.migrated"),
                    "# Moved to Presets/lore.yml\n");
        }

        // auraskills_requirements.yml → Presets/auraskills_requirements.yml
        Path auraskills = dataFolder.resolve("auraskills_requirements.yml");
        if (Files.exists(auraskills)) {
            Files.createDirectories(dataFolder.resolve("Presets"));
            Files.move(auraskills, dataFolder.resolve("Presets/auraskills_requirements.yml"),
                    StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(dataFolder.resolve("auraskills_requirements.yml.migrated"),
                    "# Moved to Presets/auraskills_requirements.yml\n");
        }
    }

    private void migrateResourcesDir() throws IOException {
        Path resources = dataFolder.resolve("resources");
        if (!Files.isDirectory(resources)) return;

        for (String mod : List.of("baubles", "fear", "firstaid", "iceandfire",
                "notreepunching", "spartanandfire", "spartanweaponry", "toughasnails")) {
            Path src = resources.resolve(mod);
            if (!Files.isDirectory(src)) continue;
            try (var stream = Files.list(src)) {
                stream.forEach(f -> {
                    try {
                        Path target = f.getFileName().toString().equals("torchdata.yml")
                                ? dataFolder.resolve("Data/fear/torchdata.yml")
                                : dataFolder.resolve("Items/" + mod + "/" + f.getFileName());
                        Files.createDirectories(target.getParent());
                        Files.move(f, target, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
            Files.deleteIfExists(src);
        }

        Path miscItems = resources.resolve("misc_items.yml");
        if (Files.exists(miscItems)) {
            Files.createDirectories(dataFolder.resolve("Items/misc"));
            Files.move(miscItems, dataFolder.resolve("Items/misc/items.yml"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        Path miscRecipes = resources.resolve("misc_recipes.yml");
        if (Files.exists(miscRecipes)) {
            Files.createDirectories(dataFolder.resolve("Items/misc"));
            Files.move(miscRecipes, dataFolder.resolve("Items/misc/recipes.yml"),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        // Rename the now-empty (or near-empty) resources/ dir to resources.migrated
        Files.move(resources, dataFolder.resolve("resources.migrated"),
                StandardCopyOption.REPLACE_EXISTING);
    }

    private void migrateDatabase() throws IOException {
        Path legacyDb = dataFolder.resolve("data.mv.db");
        if (!Files.exists(legacyDb)) return;
        Files.createDirectories(dataFolder.resolve("Data"));
        Files.move(legacyDb, dataFolder.resolve("Data/data.mv.db"),
                StandardCopyOption.REPLACE_EXISTING);
        for (String aux : List.of("data.trace.db", "data.lock.db", "data.mv.db.newFile")) {
            Path auxPath = dataFolder.resolve(aux);
            if (Files.exists(auxPath)) {
                Files.move(auxPath, dataFolder.resolve("Data/" + aux),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
