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
package cz.hashiri.harshlands.disease.trigger;

import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.data.disease.DataModule;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Applies the {@code hldiseased} item tag that {@link InfectedItemTrigger} reads in its
 * {@code RequireDiseasedTag} mode. Without this, a tag-borne disease (Wasting Blight) has
 * no contraction vector at all: nothing else in the plugin ever writes the tag.
 *
 * <p>Two sources, both config-gated:
 * <ul>
 *   <li><b>Mob drops</b> — the bootstrap vector. Configured mobs (undead by default) drop
 *       contaminated loot, so the disease can enter a world with no infected player in it.</li>
 *   <li><b>Infected player drops</b> — the onward vector. Loot dropped by a player already
 *       carrying a tag-borne disease stays contaminated, so a corpse can start an outbreak.
 *       This one cannot bootstrap on its own, hence the mob source above.</li>
 * </ul>
 *
 * <p>Runs at {@link EventPriority#HIGHEST} so it sees the final drop list after other plugins
 * have added to or filtered it — but not at MONITOR, which is reserved for handlers that make
 * no modifications, and this one tags the stacks in place.
 */
public final class TaintedItemSource implements Listener {

    private final boolean mobDropsEnabled;
    private final Set<EntityType> mobDropEntities;
    private final double mobDropChance;

    private final boolean playerDropsEnabled;
    private final double playerDropChance;

    /** Disease ids whose trigger keys off the tag; empty means the player vector is pointless. */
    private final Set<String> tagBorneDiseaseIds;

    private final Random random = new Random();

    public TaintedItemSource(boolean mobDropsEnabled, Set<EntityType> mobDropEntities, double mobDropChance,
                             boolean playerDropsEnabled, double playerDropChance,
                             Set<String> tagBorneDiseaseIds) {
        this.mobDropsEnabled = mobDropsEnabled;
        this.mobDropEntities = mobDropEntities;
        this.mobDropChance = mobDropChance;
        this.playerDropsEnabled = playerDropsEnabled;
        this.playerDropChance = playerDropChance;
        this.tagBorneDiseaseIds = tagBorneDiseaseIds;
    }

    /** True when neither source can ever fire, so the listener need not be registered. */
    public boolean isInert() {
        boolean mobs = mobDropsEnabled && !mobDropEntities.isEmpty() && mobDropChance > 0;
        boolean players = playerDropsEnabled && playerDropChance > 0 && !tagBorneDiseaseIds.isEmpty();
        return !mobs && !players;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        // PlayerDeathEvent is an EntityDeathEvent; the player vector has its own rules below.
        if (event instanceof PlayerDeathEvent) return;
        if (!mobDropsEnabled || mobDropChance <= 0) return;
        if (!mobDropEntities.contains(event.getEntityType())) return;
        taintDrops(event.getDrops(), mobDropChance);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!playerDropsEnabled || playerDropChance <= 0) return;
        if (tagBorneDiseaseIds.isEmpty()) return;
        if (!carriesTagBorneDisease(event.getEntity())) return;
        taintDrops(event.getDrops(), playerDropChance);
    }

    private boolean carriesTagBorneDisease(Player player) {
        HLPlayer hlPlayer = HLPlayer.getPlayers().get(player.getUniqueId());
        DataModule dm = hlPlayer != null ? hlPlayer.getDiseaseDataModule() : null;
        if (dm == null) return false;
        for (String diseaseId : tagBorneDiseaseIds) {
            if (dm.hasInfection(diseaseId)) return true;
        }
        return false;
    }

    private void taintDrops(List<ItemStack> drops, double chance) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) continue;
            if (random.nextDouble() >= chance) continue;
            taint(drop);
        }
    }

    /**
     * Marks one stack as contaminated. Shared with {@code /hl disease taint} so the command and
     * the natural sources produce identical items.
     */
    public static void taint(ItemStack item) {
        Utils.addNbtTag(item, InfectedItemTrigger.DISEASED_NBT_KEY, (byte) 1, PersistentDataType.BYTE);
    }

    /** True if this stack already carries the contamination tag. */
    public static boolean isTainted(ItemStack item) {
        return item != null && !item.getType().isAir()
            && Utils.hasNbtTag(item, InfectedItemTrigger.DISEASED_NBT_KEY);
    }
}
