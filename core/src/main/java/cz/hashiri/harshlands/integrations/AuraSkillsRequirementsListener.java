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
package cz.hashiri.harshlands.integrations;

import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class AuraSkillsRequirementsListener implements Listener {

    private final HLPlugin plugin;

    public AuraSkillsRequirementsListener(@Nonnull HLPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        String identifier = toIdentifier(player.getInventory().getItemInMainHand());

        RequirementFailure failure = firstMissingRequirement(player, identifier, "using");
        if (failure != null) {
            event.setCancelled(true);
            player.sendMessage(buildFailureMessage(player, "use", identifier, failure));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }

        String identifier = toIdentifier(player.getInventory().getItemInMainHand());
        RequirementFailure failure = firstMissingRequirement(player, identifier, "using");
        if (failure != null) {
            event.setCancelled(true);
            player.sendMessage(buildFailureMessage(player, "use", identifier, failure));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) {
            return;
        }

        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }

        String identifier = toIdentifier(recipe.getResult());
        RequirementFailure failure = firstMissingRequirement(player, identifier, "crafting");
        if (failure != null) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        String identifier = toIdentifier(event.getCurrentItem());
        RequirementFailure failure = firstMissingRequirement(player, identifier, "crafting");
        if (failure != null) {
            event.setCancelled(true);
            player.updateInventory();
            player.sendMessage(buildFailureMessage(player, "craft", identifier, failure));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorEquipClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Swap key (F) while hovering an item.
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack movingToOffhand = event.getCurrentItem();
            RequirementFailure failure = firstMissingRequirement(player, toIdentifier(movingToOffhand), "using");
            if (failure != null) {
                event.setCancelled(true);
                player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(movingToOffhand), failure));
            }
            return;
        }

        // Direct click into offhand slot.
        if (isOffhandClickedSlot(event)) {
            RequirementFailure failure = firstMissingRequirement(player, toIdentifier(event.getCursor()), "using");
            if (failure != null) {
                event.setCancelled(true);
                player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(event.getCursor()), failure));
            }
            return;
        }

        // Direct equip into armor slot.
        if (event.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
            RequirementFailure failure = firstMissingRequirement(player, toIdentifier(event.getCursor()), "using");
            if (failure != null) {
                event.setCancelled(true);
                player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(event.getCursor()), failure));
            }
            return;
        }

        // Shift-click auto-equip.
        if (event.isShiftClick()) {
            ItemStack current = event.getCurrentItem();
            if (!isArmorItem(current)) {
                return;
            }

            RequirementFailure failure = firstMissingRequirement(player, toIdentifier(current), "using");
            if (failure != null) {
                event.setCancelled(true);
                player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(current), failure));
            }
            return;
        }

        // Number-key / hotbar swap into armor slot.
        if (event.getAction() == InventoryAction.HOTBAR_SWAP || event.getAction() == InventoryAction.HOTBAR_MOVE_AND_READD) {
            if (event.getSlotType() != org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
                return;
            }

            int hotbarButton = event.getHotbarButton();
            if (hotbarButton < 0) {
                return;
            }

            ItemStack hotbarItem = player.getInventory().getItem(hotbarButton);
            RequirementFailure failure = firstMissingRequirement(player, toIdentifier(hotbarItem), "using");
            if (failure != null) {
                event.setCancelled(true);
                player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(hotbarItem), failure));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorEquipDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        for (int rawSlot : event.getRawSlots()) {
            if (isOffhandRawSlot(event.getView(), rawSlot)) {
                ItemStack newItem = event.getNewItems().get(rawSlot);
                RequirementFailure failure = firstMissingRequirement(player, toIdentifier(newItem), "using");
                if (failure != null) {
                    event.setCancelled(true);
                    player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(newItem), failure));
                    return;
                }
                continue;
            }

            if (event.getView().getSlotType(rawSlot) != org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
                continue;
            }

            ItemStack newItem = event.getNewItems().get(rawSlot);
            RequirementFailure failure = firstMissingRequirement(player, toIdentifier(newItem), "using");
            if (failure != null) {
                event.setCancelled(true);
                player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(newItem), failure));
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorEquipRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!isArmorItem(item)) {
            return;
        }

        RequirementFailure failure = firstMissingRequirement(player, toIdentifier(item), "using");
        if (failure != null) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
            event.setUseInteractedBlock(Event.Result.DENY);
            player.updateInventory();
            player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(item), failure));
            Bukkit.getScheduler().runTask(plugin, () -> enforceEquipmentRequirements(player));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        ItemStack movingToOffhand = event.getMainHandItem();
        RequirementFailure failure = firstMissingRequirement(player, toIdentifier(movingToOffhand), "using");
        if (failure != null) {
            event.setCancelled(true);
            player.sendMessage(buildFailureMessage(player, "equip", toIdentifier(movingToOffhand), failure));
        }
    }

    public void enforceEquipmentRequirements(@Nonnull Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        removeIfNotAllowed(player, helmet, () -> player.getInventory().setHelmet(null));

        ItemStack chestplate = player.getInventory().getChestplate();
        removeIfNotAllowed(player, chestplate, () -> player.getInventory().setChestplate(null));

        ItemStack leggings = player.getInventory().getLeggings();
        removeIfNotAllowed(player, leggings, () -> player.getInventory().setLeggings(null));

        ItemStack boots = player.getInventory().getBoots();
        removeIfNotAllowed(player, boots, () -> player.getInventory().setBoots(null));

        ItemStack offhand = player.getInventory().getItemInOffHand();
        removeIfNotAllowed(player, offhand, () -> player.getInventory().setItemInOffHand(null));
    }

    @Nullable
    private RequirementFailure firstMissingRequirement(@Nonnull Player player, @Nullable String identifier, @Nonnull String action) {
        if (identifier == null || identifier.isEmpty()) {
            return null;
        }

        Map<String, Integer> requirements = getRequirements(identifier, action);
        for (Map.Entry<String, Integer> entry : requirements.entrySet()) {
            String skill = entry.getKey();
            int requiredLevel = entry.getValue();

            if (!Utils.hasAuraSkillLevel(player, skill, requiredLevel)) {
                int current = Utils.getAuraSkillLevel(player, skill);
                return new RequirementFailure(skill, requiredLevel, current);
            }
        }

        return null;
    }

    @Nonnull
    private Map<String, Integer> getRequirements(@Nonnull String identifier, @Nonnull String action) {
        FileConfiguration config = plugin.getAuraSkillsRequirementsConfig();
        Map<String, Integer> requirements = new LinkedHashMap<>();

        String normalizedAction = action.toLowerCase(Locale.ROOT);

        // New format:
        // identifier:
        //   crafting:
        //     FARMING: 5
        //   using:
        //     FARMING: 0
        ConfigurationSection section = config.getConfigurationSection(identifier + "." + normalizedAction);

        // Legacy format fallback:
        // Crafting:
        //   identifier:
        //     FARMING: 5
        if (section == null) {
            String legacyRoot = normalizedAction.equals("crafting") ? "Crafting" : "Using";
            section = config.getConfigurationSection(legacyRoot + "." + identifier);
        }

        if (section == null) {
            return requirements;
        }

        for (String skill : section.getKeys(false)) {
            requirements.put(skill, section.getInt(skill));
        }

        return requirements;
    }

    @Nullable
    private String toIdentifier(@Nullable ItemStack item) {
        if (!Utils.isItemReal(item)) {
            return null;
        }

        if (HLItem.isHLItem(item)) {
            return HLItem.getNameFromItem(item);
        }

        return item.getType().name().toLowerCase(Locale.ROOT);
    }

    @Nonnull
    private String buildFailureMessage(@Nonnull Player player, @Nonnull String action, @Nullable String identifier, @Nonnull RequirementFailure failure) {
        FileConfiguration integrationConfig = plugin.getIntegrationsConfig();
        String path = "AuraSkills.Requirements.Message";
        String raw = integrationConfig.getString(path, "&cYou need %SKILL% level %REQUIRED_LEVEL% to %ACTION% %ITEM%. Current level: %CURRENT_LEVEL%.");
        return Utils.translateMsg(raw, player, Map.of(
                "ACTION", action,
                "ITEM", identifier == null || identifier.isBlank() ? "item" : identifier,
                "SKILL", failure.skill,
                "REQUIRED_LEVEL", failure.requiredLevel,
                "CURRENT_LEVEL", failure.currentLevel
        ));
    }

    private boolean isArmorItem(@Nullable ItemStack item) {
        if (!Utils.isItemReal(item)) {
            return false;
        }

        Material type = item.getType();
        String name = type.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || type == Material.ELYTRA
                || name.equals("WOLF_ARMOR");
    }

    private boolean isOffhandClickedSlot(@Nonnull InventoryClickEvent event) {
        return event.getClickedInventory() instanceof PlayerInventory && event.getSlot() == 40;
    }

    private boolean isOffhandRawSlot(@Nonnull InventoryView view, int rawSlot) {
        return rawSlot >= view.getTopInventory().getSize() && view.convertSlot(rawSlot) == 40;
    }

    private void removeIfNotAllowed(@Nonnull Player player, @Nullable ItemStack item, @Nonnull Runnable removeAction) {
        if (!Utils.isItemReal(item)) {
            return;
        }

        String identifier = toIdentifier(item);
        RequirementFailure failure = firstMissingRequirement(player, identifier, "using");
        if (failure == null) {
            return;
        }

        ItemStack removed = item.clone();
        removeAction.run();

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(removed);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }

        player.updateInventory();
        player.sendMessage(buildFailureMessage(player, "equip", identifier, failure));
    }

    private record RequirementFailure(String skill, int requiredLevel, int currentLevel) { }
}

