package cz.hashiri.harshlands.migration;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Map;

public record ModuleSplitResult(
        YamlConfiguration settings,
        Map<String, Object> translations,
        YamlConfiguration mobDrops,
        YamlConfiguration blockDrops
) {
}
