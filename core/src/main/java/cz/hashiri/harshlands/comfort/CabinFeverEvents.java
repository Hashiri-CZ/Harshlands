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
package cz.hashiri.harshlands.comfort;

import cz.hashiri.harshlands.locale.Messages;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import javax.annotation.Nonnull;

public class CabinFeverEvents implements Listener {

    @Nonnull
    private final CabinFeverSubsystem subsystem;
    @Nonnull
    private final ComfortModule module;

    public CabinFeverEvents(@Nonnull CabinFeverSubsystem subsystem,
                            @Nonnull ComfortModule module) {
        this.subsystem = subsystem;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBedEnter(@Nonnull PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        Player player = event.getPlayer();
        if (!module.isEnabled(player.getWorld())) {
            return;
        }

        CabinFeverSubsystem.CabinFeverStage stage = subsystem.getPlayerStage(player);
        if (stage == CabinFeverSubsystem.CabinFeverStage.FULL) {
            event.setCancelled(true);
            Messages.of("comfort.cabin_fever.messages.sleep_denied").send(player);
        }
    }

    @EventHandler
    public void onQuit(@Nonnull PlayerQuitEvent event) {
        subsystem.removeEffects(event.getPlayer());
    }

    @EventHandler
    public void onDeath(@Nonnull PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!module.isEnabled(player.getWorld())) {
            return;
        }

        if (subsystem.isResetOnDeath()) {
            subsystem.resetPlayer(player);
        }
    }
}
