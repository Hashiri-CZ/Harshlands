/*
    Copyright (C) 2026  Hashiri_
    GNU GPL v3.
 */
package cz.hashiri.harshlands.hints;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.locale.Messages;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HintSender {

    private static final Pattern ITEM_PLACEHOLDER = Pattern.compile("%item_(\\w+)%");

    private final HLPlugin plugin;
    private final HintsModule module;

    public HintSender(HLPlugin plugin, HintsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void send(Player player, HintKey key) {
        if (player == null || !player.isOnline()) return;

        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hlPlayer == null) return;
        cz.hashiri.harshlands.data.hints.DataModule data = hlPlayer.getHintsDataModule();
        if (data == null || !data.isLoaded()) return;

        FileConfiguration cfg = module.getUserConfig().getConfig();
        if (!cfg.getBoolean("Hints." + key.name() + ".Enabled", true)) return;

        if (!key.isRepeating() && data.hasSeen(key)) return;

        long cooldownMs = key.isRepeating()
            ? cfg.getLong("Hints." + key.name() + ".CooldownSeconds", key.defaultCooldownMs() / 1000L) * 1000L
            : 0L;
        if (key.isRepeating() && data.isOnCooldown(key, cooldownMs)) return;

        String prefix = Messages.get("hints.Prefix");
        String rawBody = Messages.get("hints.Hints." + key.translationKey());

        BaseComponent[] components = renderClickable(prefix + rawBody);
        player.spigot().sendMessage(components);

        String soundName = cfg.getString("Hints." + key.name() + ".Sound", key.defaultSound().name());
        Sound sound = parseSound(soundName, key.defaultSound());
        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);

        if (key.isRepeating()) {
            data.stampCooldown(key);
        } else {
            data.markSeen(key);
        }
    }

    // Parses a legacy-colored string with %item_<key>% tokens and emits a
    // BaseComponent[] where each token becomes a clickable + hoverable tag.
    // Public + static so other callers (e.g. /hl obtain response lines) can
    // render the same clickable item tags.
    public static BaseComponent[] renderClickable(String raw) {
        ComponentBuilder builder = new ComponentBuilder();
        Matcher m = ITEM_PLACEHOLDER.matcher(raw);
        int last = 0;
        while (m.find()) {
            appendLegacy(builder, raw.substring(last, m.start()));
            appendItemTag(builder, m.group(1).toLowerCase());
            last = m.end();
        }
        appendLegacy(builder, raw.substring(last));
        return builder.create();
    }

    /** Variant of {@link #renderClickable(String)} that lets callers override the tag style + hover. */
    public static BaseComponent[] renderClickable(String raw, String tagTemplate, String hoverText) {
        ComponentBuilder builder = new ComponentBuilder();
        Matcher m = ITEM_PLACEHOLDER.matcher(raw);
        int last = 0;
        while (m.find()) {
            appendLegacy(builder, raw.substring(last, m.start()));
            appendItemTag(builder, m.group(1).toLowerCase(), tagTemplate, hoverText);
            last = m.end();
        }
        appendLegacy(builder, raw.substring(last));
        return builder.create();
    }

    private static void appendLegacy(ComponentBuilder builder, String text) {
        if (text.isEmpty()) return;
        for (BaseComponent bc : TextComponent.fromLegacyText(text)) {
            builder.append(bc, ComponentBuilder.FormatRetention.NONE);
        }
    }

    private static void appendItemTag(ComponentBuilder builder, String itemKey) {
        String pretty = displayNameFor(itemKey);
        String tagText = Messages.get("hints.ItemTag", Map.of("name", pretty));
        String hoverText = Messages.get("hints.ClickHint");

        TextComponent wrapper = new TextComponent();
        for (BaseComponent bc : TextComponent.fromLegacyText(tagText)) {
            wrapper.addExtra(bc);
        }
        wrapper.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacyText(hoverText))));
        wrapper.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hl obtain " + itemKey));

        builder.append(wrapper, ComponentBuilder.FormatRetention.NONE);
    }

    private static void appendItemTag(ComponentBuilder builder, String itemKey, String tagTemplate, String hoverText) {
        String pretty = displayNameFor(itemKey);
        // Replace %name% token in the template (same placeholder convention Messages.get uses)
        String tagText = tagTemplate.replace("%name%", pretty);

        TextComponent wrapper = new TextComponent();
        for (BaseComponent bc : TextComponent.fromLegacyText(tagText)) {
            wrapper.addExtra(bc);
        }
        wrapper.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                new Text(TextComponent.fromLegacyText(hoverText))));
        wrapper.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/hl obtain " + itemKey));

        builder.append(wrapper, ComponentBuilder.FormatRetention.NONE);
    }

    private static String displayNameFor(String itemKey) {
        String fromLocale = Messages.get("hints.Obtain." + itemKey + ".Name");
        // LocaleManager returns "[key]" on missing — detect and fall back to title-cased key.
        if (fromLocale != null && !fromLocale.startsWith("[hints.")) {
            return ChatColor.stripColor(fromLocale);
        }
        return prettify(itemKey);
    }

    private static String prettify(String key) {
        String[] parts = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return sb.toString();
    }

    private Sound parseSound(String name, Sound fallback) {
        if (name == null || name.isEmpty()) return fallback;
        try {
            return Sound.valueOf(name);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Hints] Unknown Sound in hints.yml: " + name + " — using " + fallback.name());
            return fallback;
        }
    }
}
