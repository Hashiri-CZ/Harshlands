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
package cz.hashiri.harshlands.data;

import cz.hashiri.harshlands.baubles.BaubleModule;
import cz.hashiri.harshlands.data.toughasnails.DataModule;
import cz.hashiri.harshlands.tan.TanModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class HLPlayer {

    private final UUID uuid;
    private final DataModule tanDataModule;
    private final cz.hashiri.harshlands.data.baubles.DataModule baubleDataModule;
    private static final Map<UUID, HLPlayer> players = new HashMap<>();

    public HLPlayer(Player player) {
        this.uuid = player.getUniqueId();
        baubleDataModule = HLModule.getModule(BaubleModule.NAME).isGloballyEnabled() ? new cz.hashiri.harshlands.data.baubles.DataModule(player) : null;

        tanDataModule = HLModule.getModule(TanModule.NAME).isGloballyEnabled() ? new DataModule(player) : null;

        players.put(uuid, this);
    }

    @Nonnull
    public static Map<UUID, HLPlayer> getPlayers() {
        return players;
    }

    @Nullable
    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }

    public void retrieveData() {
        if (tanDataModule != null) {
            tanDataModule.retrieveData();
        }
        if (baubleDataModule != null) {
            baubleDataModule.retrieveData();
        }
    }

    public void saveData() {
        if (tanDataModule != null) {
            tanDataModule.saveData();
        }
        if (baubleDataModule != null) {
            baubleDataModule.saveData();
        }
    }

    @Nullable
    public DataModule getTanDataModule() {
        return tanDataModule;
    }

    @Nullable
    public cz.hashiri.harshlands.data.baubles.DataModule getBaubleDataModule() {
        return baubleDataModule;
    }

    public static boolean isValidPlayer(@Nullable Player player) {
        return player != null && isValidPlayer(player.getUniqueId());
    }

    public static boolean isValidPlayer(@Nullable UUID id) {
        return id != null && players.containsKey(id) && players.get(id) != null;
    }
}

