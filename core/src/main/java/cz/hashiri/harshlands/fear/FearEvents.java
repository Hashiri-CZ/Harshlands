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
package cz.hashiri.harshlands.fear;

import cz.hashiri.harshlands.data.ModuleEvents;
import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.Bukkit;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import java.util.List;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldSaveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import javax.annotation.Nonnull;
import java.util.UUID;

public class FearEvents extends ModuleEvents {

    private final HLPlugin plugin;
    private final FearTorchManager torchManager;
    private final FearUnlitTorchService unlitTorchService;

    public FearEvents(@Nonnull FearModule module, @Nonnull HLPlugin plugin, @Nonnull FearTorchManager torchManager, @Nonnull FearUnlitTorchService unlitTorchService) {
        super(module, plugin);
        this.plugin = plugin;
        this.torchManager = torchManager;
        this.unlitTorchService = unlitTorchService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnlitTorchPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!shouldEventBeRan(player)) {
            return;
        }

        ItemStack item = event.getPlayer().getInventory().getItemInMainHand();
        if (isLitTorchItem(item)) {
            torchManager.registerPlacedLitTorch(event.getBlockPlaced());
        } else if (isUnlitTorchItem(item)) {
            unlitTorchService.convertPlacedUnlitTorch(event.getBlockPlaced());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onIgniteUnlitTorchBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!shouldEventBeRan(player) || !isIgniter(player.getInventory().getItemInMainHand())) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (!unlitTorchService.isManagedUnlitTorch(clickedBlock)) {
            return;
        }

        if (unlitTorchService.igniteUnlitTorchBlock(clickedBlock, torchManager)) {
            Utils.changeDurability(player.getInventory().getItemInMainHand(), -1, true, true, player);
            event.setCancelled(true);
            event.setUseInteractedBlock(Event.Result.DENY);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakUnlitTorchBlock(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!unlitTorchService.isManagedUnlitTorch(block)) {
            return;
        }

        if (!shouldEventBeRan(event.getPlayer())) {
            return;
        }

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), HLItem.getItem("unlit_torch"));
        }

        unlitTorchService.unregisterUnlitTorch(block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnlitTorchPhysics(BlockPhysicsEvent event) {
        // P3: early exit for non-redstone-torch materials to avoid LocationKey allocation on every physics event
        Material m = event.getBlock().getType();
        if (m != Material.REDSTONE_TORCH && m != Material.REDSTONE_WALL_TORCH) return;
        if (unlitTorchService.suppressManagedUnlitUpdate(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onUnlitTorchRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();
        if (!unlitTorchService.suppressManagedUnlitUpdate(block)) {
            return;
        }

        event.setNewCurrent(0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTorchBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!torchManager.isManagedTorch(block)) {
            return;
        }

        torchManager.unregisterTorch(block);

        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation(), HLItem.getItem("unlit_torch"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldSave(WorldSaveEvent event) {
        if (unlitTorchService != null) {
            unlitTorchService.flushToDatabase();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        UUID worldId = event.getWorld().getUID();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        if (!unlitTorchService.hasTorchesInChunk(worldId, chunkX, chunkZ)) return;
        Bukkit.getScheduler().runTask(plugin,
                () -> unlitTorchService.enforceChunk(worldId, chunkX, chunkZ));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> blocks = event.blockList();
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (torchManager.isManagedTorch(block)) {
                torchManager.unregisterTorch(block);
            } else if (unlitTorchService.isManagedUnlitTorch(block)) {
                unlitTorchService.unregisterUnlitTorch(block);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> blocks = event.blockList();
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            if (torchManager.isManagedTorch(block)) {
                torchManager.unregisterTorch(block);
            } else if (unlitTorchService.isManagedUnlitTorch(block)) {
                unlitTorchService.unregisterUnlitTorch(block);
            }
        }
    }

    private boolean isIgniter(ItemStack item) {
        if (!Utils.isItemReal(item)) {
            return false;
        }

        if (item.getType() == Material.FLINT_AND_STEEL) {
            return true;
        }

        return HLItem.isHLItem(item) && "matchbox".equals(HLItem.getNameFromItem(item));
    }

    private boolean isUnlitTorchItem(ItemStack item) {
        return matchesFearItem(item, "unlit_torch") && !matchesFearItem(item, "lit_torch");
    }

    private boolean isLitTorchItem(ItemStack item) {
        return matchesFearItem(item, "lit_torch");
    }

    private boolean matchesFearItem(ItemStack item, String id) {
        if (!Utils.isItemReal(item)) {
            return false;
        }

        if (HLItem.isHLItem(item)) {
            return id.equals(HLItem.getNameFromItem(item));
        }

        ItemStack reference = HLItem.getItem(id);
        if (!Utils.isItemReal(reference) || item.getType() != reference.getType()) {
            return false;
        }

        ItemMeta currentMeta = item.getItemMeta();
        ItemMeta referenceMeta = reference.getItemMeta();
        if (currentMeta == null || referenceMeta == null) {
            return false;
        }

        NamespacedKey currentModel = Utils.getItemModel(currentMeta);
        NamespacedKey referenceModel = Utils.getItemModel(referenceMeta);
        if (referenceModel != null && referenceModel.equals(currentModel)) {
            return true;
        }

        if (currentMeta.hasDisplayName() && referenceMeta.hasDisplayName()) {
            return currentMeta.getDisplayName().equals(referenceMeta.getDisplayName());
        }

        return false;
    }
}

