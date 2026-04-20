/*
    Copyright (C) 2026  Hashiri_
    GNU GPL v3.
 */
package cz.hashiri.harshlands.data.hints;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.data.HLDataModule;
import cz.hashiri.harshlands.data.db.HLDatabase;
import cz.hashiri.harshlands.hints.HintKey;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;

public class DataModule implements HLDataModule {

    private final UUID id;
    private final HLDatabase database;

    private final EnumSet<HintKey> seen = EnumSet.noneOf(HintKey.class);
    private final Map<HintKey, Long> cooldowns = new EnumMap<>(HintKey.class);

    private volatile boolean dirty = false;
    private volatile boolean loaded = false;

    public DataModule(Player player) {
        this.id = player.getUniqueId();
        this.database = HLPlugin.getPlugin().getDatabase();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public synchronized boolean hasSeen(HintKey key) {
        return seen.contains(key);
    }

    public synchronized void markSeen(HintKey key) {
        if (seen.add(key)) {
            dirty = true;
        }
    }

    public synchronized void clearAllSeen() {
        if (!seen.isEmpty()) {
            seen.clear();
            dirty = true;
        }
        cooldowns.clear();
    }

    public synchronized boolean isOnCooldown(HintKey key, long cooldownMs) {
        Long last = cooldowns.get(key);
        if (last == null) return false;
        return System.currentTimeMillis() - last < cooldownMs;
    }

    public synchronized void stampCooldown(HintKey key) {
        cooldowns.put(key, System.currentTimeMillis());
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void retrieveData() {
        database.loadHintsData(id).thenAccept(optional -> {
            synchronized (this) {
                seen.clear();
                if (optional.isPresent()) {
                    String csv = optional.get().seenHintsCsv();
                    if (csv != null && !csv.isEmpty()) {
                        for (String name : csv.split(",")) {
                            String trimmed = name.trim();
                            if (trimmed.isEmpty()) continue;
                            try {
                                seen.add(HintKey.valueOf(trimmed));
                            } catch (IllegalArgumentException ignored) {
                                // enum renamed/removed — self-healing: skip unknown
                            }
                        }
                    }
                }
                dirty = false;
                loaded = true;
            }
        });
    }

    @Override
    public void saveData() {
        String csv;
        synchronized (this) {
            if (seen.isEmpty()) {
                csv = "";
            } else {
                StringBuilder sb = new StringBuilder();
                boolean first = true;
                for (HintKey k : seen) {
                    if (!first) sb.append(',');
                    sb.append(k.name());
                    first = false;
                }
                csv = sb.toString();
            }
            dirty = false;
        }
        database.saveHintsData(id, new cz.hashiri.harshlands.data.db.HLDatabase.HintsDataRow(csv));
    }
}
