/*
    Copyright (C) 2026  Hashiri_
    GNU GPL v3.
 */
package cz.hashiri.harshlands.guide;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.data.HLConfig;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.utils.Utils;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GuideModule extends HLModule {

    public static final String NAME = "Guide";

    private final HLPlugin plugin;
    private final Map<String, ItemStack> cachedBooks = new ConcurrentHashMap<>();

    public GuideModule(HLPlugin plugin) {
        super(NAME, plugin, Map.of(), Map.of());
        this.plugin = plugin;
    }

    @Override
    public void initialize() {
        setUserConfig(new HLConfig(plugin, "Settings/guide.yml"));
        FileConfiguration config = getUserConfig().getConfig();

        if (!config.getBoolean("Enabled", true)) {
            return;
        }

        if (config.getBoolean("Initialize.Enabled", true)) {
            Utils.logModuleInit("guide", NAME);
        }

        GuideListener listener = new GuideListener(plugin, this);
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        if (config.getBoolean("Validation.WarnOnLongPages", true)) {
            validateCurrentLocaleBook();
        }
    }

    /** Produce (or return cached) book for the currently-configured server locale. */
    public ItemStack buildBook() {
        String locale = resolveActiveLocale();
        int version = getGuideVersion();
        String cacheKey = locale + "@" + version;

        return cachedBooks.computeIfAbsent(cacheKey, k -> {
            FileConfiguration translations = loadTranslationConfig(locale);
            return GuideBookBuilder.buildBook(translations);
        });
    }

    public int getGuideVersion() {
        return getUserConfig().getConfig().getInt("Guide.Version", 1);
    }

    /** Called by /hl reload — drops the cache so next buildBook() rebuilds from disk. */
    public void clearCache() {
        cachedBooks.clear();
    }

    // ── Locale plumbing ──────────────────────────────────────────────────────
    // Locale is read from the top-level config.yml "Locale" key, matching how
    // HLPlugin bootstraps LocaleManager (HLPlugin.onEnable line ~119).

    private String resolveActiveLocale() {
        return plugin.getConfig().getString("Locale", "en-US");
    }

    private FileConfiguration loadTranslationConfig(String locale) {
        File translationFile = new File(plugin.getDataFolder(), "Translations/" + locale + "/guide.yml");
        if (translationFile.exists()) {
            return YamlConfiguration.loadConfiguration(translationFile);
        }
        // Fallback to jar-embedded en-US if the locale file is missing
        File fallback = new File(plugin.getDataFolder(), "Translations/en-US/guide.yml");
        return YamlConfiguration.loadConfiguration(fallback);
    }

    // ── Validator ────────────────────────────────────────────────────────────

    private void validateCurrentLocaleBook() {
        FileConfiguration translations = loadTranslationConfig(resolveActiveLocale());
        List<BaseComponent[]> pages = GuideBookBuilder.buildPages(translations);

        for (int i = 0; i < pages.size(); i++) {
            String plain = new TextComponent(pages.get(i)).toPlainText();
            String[] lines = plain.split("\n", -1);
            if (lines.length > 14) {
                plugin.getLogger().warning("[Guide] Page " + (i + 1) + " has " + lines.length + " lines (book max is 14)");
            }
            for (int l = 0; l < lines.length; l++) {
                if (BookLineMetrics.exceedsBookLine(lines[l])) {
                    plugin.getLogger().warning("[Guide] Page " + (i + 1) + " line " + (l + 1)
                        + " exceeds 114px width: \"" + lines[l] + "\" ("
                        + BookLineMetrics.pixelWidth(lines[l]) + "px)");
                }
            }
        }
    }

    @Override
    public void shutdown() {
        cachedBooks.clear();
    }
}
