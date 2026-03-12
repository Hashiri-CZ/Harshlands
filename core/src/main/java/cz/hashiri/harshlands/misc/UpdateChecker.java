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
package cz.hashiri.harshlands.misc;

import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class UpdateChecker {

    private final JavaPlugin plugin;
    private final int resourceId;

    // This is cuz of my lazyness... The update checker will work later tho
    private static final boolean MUTE_UPDATES = true;

    public UpdateChecker(JavaPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void getVersion(final Consumer<String> consumer) {
        Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
            try (InputStream inputStream = URI.create("https://api.spigotmc.org/legacy/update.php?resource=" + this.resourceId).toURL().openStream();
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    consumer.accept(scanner.next());
                }
            } catch (IOException exception) {
                if (!MUTE_UPDATES) {
                    this.plugin.getLogger().info(Utils.translateMsg("&cCannot look for updates: " + exception.getMessage(), null, null));
                }
            }
        });
    }

    public void checkUpdate() {
        if (MUTE_UPDATES) return;

        Logger logger = plugin.getLogger();

        getVersion(latestVersion -> {
            String currentVersion = plugin.getDescription().getVersion();

            int compareTo = currentVersion.compareTo(latestVersion);

            List<String> messages = compareTo == 0
                    ? plugin.getConfig().getStringList("CorrectVersion")
                    : compareTo < 0
                        ? plugin.getConfig().getStringList("OutdatedVersion")
                        : plugin.getConfig().getStringList("DeveloperBuildVersion");

            for (String message : messages) {
                logger.info(Utils.translateMsg(message, null, null));
            }
        });
    }
}

