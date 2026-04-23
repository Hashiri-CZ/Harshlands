/*
    Copyright (C) 2026  Hashiri_
    GNU GPL v3.
 */
package cz.hashiri.harshlands.data.guide;

import cz.hashiri.harshlands.HLPlugin;
import cz.hashiri.harshlands.data.HLDataModule;
import cz.hashiri.harshlands.data.db.HLDatabase;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DataModule implements HLDataModule {

    private final UUID id;
    private final HLDatabase database;

    private volatile int lastSeenVersion = 0;
    private volatile long lastOpenedAt = 0L;
    private volatile boolean dirty = false;
    private volatile boolean loaded = false;

    public DataModule(Player player) {
        this.id = player.getUniqueId();
        this.database = HLPlugin.getPlugin().getDatabase();
    }

    public boolean isLoaded() {
        return loaded;
    }

    public synchronized int getLastSeenVersion() {
        return lastSeenVersion;
    }

    public synchronized long getLastOpenedAt() {
        return lastOpenedAt;
    }

    public synchronized void recordDelivery(int guideVersion) {
        this.lastSeenVersion = guideVersion;
        this.lastOpenedAt = System.currentTimeMillis();
        this.dirty = true;
    }

    public synchronized void resetSeen() {
        this.lastSeenVersion = 0;
        this.lastOpenedAt = 0L;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void retrieveData() {
        database.loadGuideData(id).thenAccept(optional -> {
            synchronized (this) {
                if (optional.isPresent()) {
                    lastSeenVersion = optional.get().lastSeenVersion();
                    lastOpenedAt = optional.get().lastOpenedAt();
                } else {
                    lastSeenVersion = 0;
                    lastOpenedAt = 0L;
                }
                dirty = false;
                loaded = true;
            }
        });
    }

    @Override
    public void saveData() {
        int ver;
        long ts;
        synchronized (this) {
            ver = lastSeenVersion;
            ts = lastOpenedAt;
            dirty = false;
        }
        database.saveGuideData(id, new HLDatabase.GuideDataRow(ver, ts));
    }
}
