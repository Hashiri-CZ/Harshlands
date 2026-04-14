package cz.hashiri.harshlands.locale;

import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.Map;

public final class Messages {

    private static volatile LocaleManager manager;

    private Messages() {}

    public static void bind(LocaleManager m) {
        manager = m;
    }

    public static void reset() {
        manager = null;
    }

    public static void reload() {
        LocaleManager m = manager;
        if (m != null) m.reload();
    }

    public static String get(String key) {
        LocaleManager m = manager;
        if (m == null) return "[" + key + "]";
        return m.get(key);
    }

    public static String get(String key, Map<String, ?> placeholders) {
        String raw = get(key);
        if (placeholders == null || placeholders.isEmpty()) return raw;
        String out = raw;
        for (Map.Entry<String, ?> e : placeholders.entrySet()) {
            out = out.replace("%" + e.getKey() + "%", String.valueOf(e.getValue()));
        }
        return out;
    }

    public static List<String> getList(String key) {
        LocaleManager m = manager;
        if (m == null) return List.of();
        return m.getList(key);
    }

    public static Builder of(String key) {
        return new Builder(key);
    }

    public static final class Builder {
        private final String key;
        private final java.util.LinkedHashMap<String, Object> placeholders = new java.util.LinkedHashMap<>();

        private Builder(String key) {
            this.key = key;
        }

        public Builder with(String name, Object value) {
            placeholders.put(name, value);
            return this;
        }

        public String build() {
            return Messages.get(key, placeholders);
        }

        public void send(CommandSender recipient) {
            if (recipient != null) recipient.sendMessage(build());
        }
    }
}
