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
package cz.hashiri.harshlands.firstaid;

import cz.hashiri.harshlands.data.ModuleEvents;
import cz.hashiri.harshlands.HLPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FaEvents extends ModuleEvents implements Listener {

    private final HLPlugin plugin;
    private final FaModule faModule;

    // Per-player Body instances — one body per online player
    private final Map<UUID, Body> bodies = new HashMap<>();

    public FaEvents(FaModule module, HLPlugin plugin) {
        super(module, plugin);
        this.plugin = plugin;
        this.faModule = module;
    }

    /**
     * Gets or creates the Body for the given player.
     * Body is lazily initialised here; in the future a join/quit listener can manage lifecycle.
     */
    private Body getBody(Player player) {
        return bodies.computeIfAbsent(player.getUniqueId(),
                id -> new Body(faModule, player.getLocation()));
    }

    /**
     * Dispatches incoming player damage to the appropriate body part.
     * Uses the player's current location to determine which part was hit.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Body body = getBody(player);
        Location loc = player.getLocation();
        Body.BodyPart part = body.getHitBodyPart(loc);

        // getHitBodyPart returns null until the hit-detection logic is implemented;
        // guard to prevent NPE while the system is still being built out.
        if (part == null) return;

        double damage = event.getFinalDamage();
        body.applyDamage(part, damage, player);
    }
}
