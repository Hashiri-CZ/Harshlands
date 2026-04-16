package cz.hashiri.harshlands.locale;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class LocaleManager {

    private final Path translationsRoot;
    private final String locale;
    private final Logger logger;
    private Map<String, Object> flatMap = new HashMap<>();
    private final java.util.Set<String> reportedMissingKeys = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public LocaleManager(Path translationsRoot, String locale) {
        this(translationsRoot, locale, Logger.getLogger(LocaleManager.class.getName()));
    }

    public LocaleManager(Path translationsRoot, String locale, Logger logger) {
        this.translationsRoot = translationsRoot;
        this.locale = locale;
        this.logger = logger;
    }

    public void load() {
        Map<String, Object> accumulator = new HashMap<>();
        File localeDir = translationsRoot.resolve(locale).toFile();
        if (!localeDir.isDirectory()) {
            logger.warning("Translations folder missing for locale '" + locale + "' at " + localeDir);
            this.flatMap = accumulator;
            return;
        }
        File[] yamls = localeDir.listFiles((d, name) -> name.endsWith(".yml"));
        if (yamls == null) {
            this.flatMap = accumulator;
            return;
        }
        for (File f : yamls) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
            flatten(yaml, "", accumulator);
        }
        this.flatMap = accumulator;
    }

    public void reload() {
        reportedMissingKeys.clear();
        load();
    }

    private void flatten(ConfigurationSection section, String prefix, Map<String, Object> out) {
        for (String key : section.getKeys(false)) {
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = section.get(key);
            if (value instanceof ConfigurationSection subsection) {
                flatten(subsection, fullKey, out);
            } else {
                out.put(fullKey, value);
            }
        }
    }

    public String get(String key) {
        Object value = flatMap.get(key);
        if (value == null) {
            if (reportedMissingKeys.add(key)) {
                logger.warning("Missing translation for key: " + key);
            }
            return "[" + key + "]";
        }
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', value.toString());
    }

    public List<String> getList(String key) {
        Object value = flatMap.get(key);
        if (!(value instanceof List<?> raw)) {
            if (value == null) {
                if (reportedMissingKeys.add(key)) {
                    logger.warning("Missing translation for key: " + key);
                }
            }
            return List.of();
        }
        List<String> translated = new ArrayList<>(raw.size());
        for (Object item : raw) {
            translated.add(org.bukkit.ChatColor.translateAlternateColorCodes('&', String.valueOf(item)));
        }
        return translated;
    }
}
