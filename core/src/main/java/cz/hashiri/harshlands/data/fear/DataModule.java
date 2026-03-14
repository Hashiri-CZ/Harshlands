/*
    Copyright (C) 2026  Hashiri_

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package cz.hashiri.harshlands.data.fear;

import cz.hashiri.harshlands.data.HLDataModule;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.db.HLDatabase;
import cz.hashiri.harshlands.fear.FearModule;
import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DataModule implements HLDataModule {

    private final UUID id;
    private final HLDatabase database;
    private final double defaultFear;
    private double fearLevel;
    private volatile boolean dirty = false;
    private int lowHealthTicks = 0;
    private int fearDirection = 0; // 0 = unknown/stable, 1 = increasing, -1 = decreasing

    public DataModule(Player player) {
        FearModule module = (FearModule) HLModule.getModule(FearModule.NAME);
        FileConfiguration config = module.getUserConfig().getConfig();
        this.database = HLPlugin.getPlugin().getDatabase();
        this.id = player.getUniqueId();
        this.defaultFear = config.getDouble("FearMeter.DefaultFear", 0.0);
        this.fearLevel = defaultFear;
    }

    public double getFearLevel() {
        return fearLevel;
    }

    public int getFearDirection() {
        return fearDirection;
    }

    public void setFearLevel(double value) {
        double clamped = Math.max(0.0, Math.min(100.0, value));
        if (clamped > this.fearLevel) this.fearDirection = 1;
        else if (clamped < this.fearLevel) this.fearDirection = -1;
        // if equal: keep fearDirection unchanged (sticky)
        this.fearLevel = clamped;
        this.dirty = true;
    }

    public void increaseFear(double amount) {
        setFearLevel(fearLevel + amount);
    }

    public void decreaseFear(double amount) {
        setFearLevel(fearLevel - amount);
    }

    public int incrementLowHealthTicks() { return ++lowHealthTicks; }
    public void resetLowHealthTicks()    { lowHealthTicks = 0; }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void retrieveData() {
        database.loadFearData(id).thenAccept(optional -> {
            if (optional.isPresent()) {
                this.fearLevel = optional.get().fearLevel();
            } else {
                saveData();
            }
            this.dirty = false;
        });
    }

    @Override
    public void saveData() {
        dirty = false;
        database.saveFearData(id, new HLDatabase.FearDataRow(fearLevel));
    }
}
