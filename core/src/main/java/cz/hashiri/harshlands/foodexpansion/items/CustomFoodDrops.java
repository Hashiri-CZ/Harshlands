package cz.hashiri.harshlands.foodexpansion.items;

import cz.hashiri.harshlands.foodexpansion.FoodExpansionModule;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;

public class CustomFoodDrops implements Listener {

    private final CustomFoodRegistry registry;
    private final FoodExpansionModule module;
    private final Map<EntityType, DropDefinition> dropMap = new EnumMap<>(EntityType.class);

    public CustomFoodDrops(CustomFoodRegistry registry, FoodExpansionModule module,
                            ConfigurationSection mobDropsSection, Logger logger) {
        this.registry = registry;
        this.module = module;

        if (mobDropsSection != null) {
            loadDrops(mobDropsSection, logger);
        }
    }

    private void loadDrops(ConfigurationSection section, Logger logger) {
        for (String entityName : section.getKeys(false)) {
            ConfigurationSection dropSec = section.getConfigurationSection(entityName);
            if (dropSec == null) continue;

            EntityType entityType;
            try {
                entityType = EntityType.valueOf(entityName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("MobDrops: unknown entity type '" + entityName + "'");
                continue;
            }

            String rawId = dropSec.getString("Item", "");
            String cookedId = dropSec.getString("CookedItem", "");
            int min = dropSec.getInt("MinAmount", 0);
            int max = dropSec.getInt("MaxAmount", 1);

            if (registry.getDefinition(rawId) == null) {
                logger.warning("MobDrops: unknown custom food '" + rawId + "' for " + entityName);
                continue;
            }

            dropMap.put(entityType, new DropDefinition(rawId, cookedId, min, max));
        }
        Utils.logStartup("Loaded " + dropMap.size() + " mob drop definitions");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (entity instanceof org.bukkit.entity.Ageable ageable && !ageable.isAdult()) return;

        if (!module.isEnabled(entity.getWorld())) return;

        DropDefinition drop = dropMap.get(entity.getType());
        if (drop == null) return;

        int lootingLevel = 0;
        Player killer = entity.getKiller();
        if (killer != null) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            lootingLevel = weapon.getEnchantmentLevel(Enchantment.LOOTING);
        }

        int maxWithLooting = drop.maxAmount + lootingLevel;
        int count = drop.minAmount >= maxWithLooting
            ? drop.minAmount
            : ThreadLocalRandom.current().nextInt(drop.minAmount, maxWithLooting + 1);

        if (count <= 0) return;

        boolean onFire = entity.getFireTicks() > 0;
        String itemId = onFire ? drop.cookedId : drop.rawId;

        ItemStack dropStack = registry.createItemStack(itemId, count);
        event.getDrops().add(dropStack);
    }

    private record DropDefinition(String rawId, String cookedId, int minAmount, int maxAmount) {}
}
