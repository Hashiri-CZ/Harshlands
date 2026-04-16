package cz.hashiri.harshlands.migration;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class ModuleManifest {

    public abstract String moduleId();

    /**
     * Paths (relative to the legacy YAML root) whose entire subtree is user-facing
     * text and should be routed to Translations/&lt;locale&gt;/&lt;moduleId&gt;.yml with keys
     * normalized to "&lt;moduleId&gt;.&lt;lowercase_dot_snake_case_path&gt;".
     */
    protected abstract Set<String> translationRoots();

    public ModuleSplitResult split(YamlConfiguration legacy, Path dataFolder) {
        YamlConfiguration settings = cloneYaml(legacy);
        YamlConfiguration mobDrops = new YamlConfiguration();
        YamlConfiguration blockDrops = new YamlConfiguration();
        Map<String, Object> translations = new HashMap<>();

        // Pull MobDrops + BlockDrops into their own files
        if (legacy.contains("MobDrops")) {
            mobDrops.set("MobDrops", legacy.get("MobDrops"));
            settings.set("MobDrops", null);
        }
        if (legacy.contains("BlockDrops")) {
            blockDrops.set("BlockDrops", legacy.get("BlockDrops"));
            settings.set("BlockDrops", null);
        }

        // Walk declared translation roots, move each to translations map, strip from settings
        for (String root : translationRoots()) {
            Object subtree = legacy.get(root);
            if (subtree == null) continue;
            if (subtree instanceof ConfigurationSection sec) {
                collectStringsFromSection(sec, moduleId() + "." + normalize(root), translations, settings, root);
            } else {
                // Scalar or list directly at root
                translations.put(moduleId() + "." + normalize(root), subtree);
                settings.set(root, null);
            }
        }

        return new ModuleSplitResult(settings, translations, mobDrops, blockDrops);
    }

    private void collectStringsFromSection(ConfigurationSection sec, String prefix,
                                           Map<String, Object> translations,
                                           YamlConfiguration settings,
                                           String settingsPath) {
        for (String key : sec.getKeys(false)) {
            Object value = sec.get(key);
            String flatKey = prefix + "." + normalize(key);
            String legacyPath = settingsPath + "." + key;
            if (value instanceof ConfigurationSection sub) {
                collectStringsFromSection(sub, flatKey, translations, settings, legacyPath);
            } else if (value instanceof String || value instanceof java.util.List<?>) {
                translations.put(flatKey, value);
                settings.set(legacyPath, null);
            }
        }
    }

    private YamlConfiguration cloneYaml(YamlConfiguration src) {
        YamlConfiguration copy = new YamlConfiguration();
        try {
            copy.loadFromString(src.saveToString());
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new RuntimeException("Failed to clone YAML", e);
        }
        return copy;
    }

    protected String normalize(String key) {
        return key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
