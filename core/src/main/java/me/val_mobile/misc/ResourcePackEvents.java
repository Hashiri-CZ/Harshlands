/*
    Copyright (C) 2025  Val_Mobile

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
package me.val_mobile.misc;

import me.val_mobile.rsv.RSVPlugin;
import me.val_mobile.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class ResourcePackEvents implements Listener {

    private final RSVPlugin plugin;

    public ResourcePackEvents(RSVPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        String mcVersion = Utils.getMinecraftVersion(false);
        String packUrl;

        // Pick correct pack
        if (compareVersions(mcVersion, "1.21.10") >= 0) {
            packUrl = plugin.getConfig().getString("ResourcePack.Url_1_21_10");
        } else if (compareVersions(mcVersion, "1.21.3") >= 0) {
            packUrl = plugin.getConfig().getString("ResourcePack.Url_1_21_3");
        } else {
            packUrl = plugin.getConfig().getString("ResourcePack.Url");
        }

        // Log to console
        plugin.getLogger().info("Sending pack for version: " + mcVersion);

        // Apply pack to player
        player.setResourcePack(packUrl);
    }

    /**
     * Safe version comparator: 1.21.10 > 1.21.9
     */
    private int compareVersions(String v1, String v2) {
        String[] a = v1.split("\\.");
        String[] b = v2.split("\\.");

        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int n1 = (i < a.length) ? Integer.parseInt(a[i]) : 0;
            int n2 = (i < b.length) ? Integer.parseInt(b[i]) : 0;

            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }
        return 0;
    }
}
