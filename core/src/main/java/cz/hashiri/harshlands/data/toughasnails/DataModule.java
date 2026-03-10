/*
    Copyright (C) 2025  Hashiri_

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
package cz.hashiri.harshlands.data.toughasnails;

import cz.hashiri.harshlands.data.HLDataModule;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.db.HLDatabase;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.tan.TanModule;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DataModule implements HLDataModule {

    private final UUID id;
    private final HLDatabase database;
    private final double defaultTemp;
    private final int defaultThirst;
    private final int defaultSaturation;
    private final double defaultExhaustion;
    private final int defaultTickTimer;

    private double temperature;
    private int thirst;
    private double thirstExhaustion;
    private int thirstSaturation;
    private int thirstTickTimer;
    private volatile boolean dirty = false;

    public DataModule(Player player) {
        TanModule module = (TanModule) HLModule.getModule(TanModule.NAME);
        FileConfiguration userConfig = module.getUserConfig().getConfig();
        HLPlugin plugin = HLPlugin.getPlugin();
        this.database = plugin.getDatabase();
        this.id = player.getUniqueId();

        this.defaultTemp = userConfig.getDouble("Temperature.DefaultTemperature");
        this.defaultThirst = userConfig.getInt("Thirst.DefaultThirst");
        this.defaultSaturation = userConfig.getInt("Thirst.DefaultSaturation");
        this.defaultExhaustion = userConfig.getDouble("Thirst.DefaultExhaustion");
        this.defaultTickTimer = userConfig.getInt("Thirst.DefaultExhaustionTickTimer");

        // Set defaults immediately so the player has valid values before async load completes
        this.temperature = defaultTemp;
        this.thirst = defaultThirst;
        this.thirstSaturation = defaultSaturation;
        this.thirstExhaustion = defaultExhaustion;
        this.thirstTickTimer = defaultTickTimer;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
        this.dirty = true;
    }

    public void setThirst(int thirst) {
        this.thirst = thirst;
        this.dirty = true;
    }

    public void setThirstExhaustion(double thirstExhaustion) {
        this.thirstExhaustion = thirstExhaustion;
        this.dirty = true;
    }

    public void setThirstSaturation(double thirstSaturation) {
        this.thirstSaturation = (int) thirstSaturation;
        this.dirty = true;
    }

    public void setThirstTickTimer(int thirstTickTimer) {
        this.thirstTickTimer = thirstTickTimer;
        this.dirty = true;
    }

    public double getTemperature() {
        return temperature;
    }

    public int getThirst() {
        return thirst;
    }

    public double getThirstExhaustion() {
        return thirstExhaustion;
    }

    public int getThirstSaturation() {
        return thirstSaturation;
    }

    public int getThirstTickTimer() {
        return thirstTickTimer;
    }

    public boolean isDirty() {
        return dirty;
    }

    @Override
    public void retrieveData() {
        database.loadTanData(id).thenAccept(optional -> {
            if (optional.isPresent()) {
                HLDatabase.TanDataRow row = optional.get();
                this.temperature = row.temperature();
                this.thirst = row.thirst();
                this.thirstExhaustion = row.thirstExhaustion();
                this.thirstSaturation = row.thirstSaturation();
                this.thirstTickTimer = row.thirstTickTimer();
            } else {
                // No existing row — defaults already set in constructor; persist them
                saveData();
            }
            this.dirty = false;
        });
    }

    @Override
    public void saveData() {
        dirty = false;
        database.saveTanData(id, new HLDatabase.TanDataRow(
            temperature,
            thirst,
            thirstExhaustion,
            thirstSaturation,
            thirstTickTimer
        ));
    }
}
