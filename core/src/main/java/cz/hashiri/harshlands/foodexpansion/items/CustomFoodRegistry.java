package cz.hashiri.harshlands.foodexpansion.items;

import cz.hashiri.harshlands.foodexpansion.NutrientProfile;
import cz.hashiri.harshlands.locale.Messages;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;

public class CustomFoodRegistry {

    public static final String PDC_KEY = "hl_food";

    private final Map<String, CustomFoodDefinition> definitions = new LinkedHashMap<>();
    private final Logger logger;

    public CustomFoodRegistry(ConfigurationSection customFoodsSection, Logger logger) {
        this.logger = logger;
        if (customFoodsSection != null) {
            loadFoods(customFoodsSection);
        }
    }

    private void loadFoods(ConfigurationSection section) {
        for (String id : section.getKeys(false)) {
            ConfigurationSection foodSec = section.getConfigurationSection(id);
            if (foodSec == null) continue;

            String matName = foodSec.getString("BaseMaterial", "PAPER");
            Material baseMaterial;
            try {
                baseMaterial = Material.valueOf(matName.toUpperCase());
            } catch (IllegalArgumentException e) {
                logger.warning("Custom food '" + id + "': invalid BaseMaterial '" + matName + "', skipping");
                continue;
            }

            String displayName = Messages.get("foodexpansion.food_expansion.custom_foods." + id + ".display_name");
            if (displayName.startsWith("[")) {
                displayName = id;  // fallback for unrecognized/admin-added foods
            }
            int cmd = foodSec.getInt("CustomModelData", 0);

            boolean isFood = foodSec.contains("Nutrition");
            int hunger = foodSec.getInt("Nutrition.Hunger", 0);
            float saturation = (float) foodSec.getDouble("Nutrition.Saturation", 0.0);

            NutrientProfile macros = null;
            if (foodSec.contains("Macros")) {
                double p = foodSec.getDouble("Macros.Protein", 0.0);
                double c = foodSec.getDouble("Macros.Carbs", 0.0);
                double f = foodSec.getDouble("Macros.Fats", 0.0);
                macros = new NutrientProfile(p, c, f);
            }

            EnumSet<FoodFlag> flags = EnumSet.noneOf(FoodFlag.class);
            List<String> flagList = foodSec.getStringList("Flags");
            for (String flagStr : flagList) {
                try {
                    flags.add(FoodFlag.valueOf(flagStr.toUpperCase()));
                } catch (IllegalArgumentException e) {
                    logger.warning("Custom food '" + id + "': unknown flag '" + flagStr + "'");
                }
            }

            List<FoodEffect> effects = new ArrayList<>();
            List<Map<?, ?>> effectList = foodSec.getMapList("Effects");
            for (Map<?, ?> effectMap : effectList) {
                String typeName = String.valueOf(effectMap.get("Type"));
                PotionEffectType effectType = Registry.EFFECT.get(NamespacedKey.minecraft(typeName.toLowerCase()));
                if (effectType == null) {
                    logger.warning("Custom food '" + id + "': unknown effect type '" + typeName + "'");
                    continue;
                }
                int duration = effectMap.containsKey("Duration") ? ((Number) effectMap.get("Duration")).intValue() : 200;
                int amplifier = effectMap.containsKey("Amplifier") ? ((Number) effectMap.get("Amplifier")).intValue() : 0;
                double chance = effectMap.containsKey("Chance") ? ((Number) effectMap.get("Chance")).doubleValue() : 1.0;
                effects.add(new FoodEffect(effectType, duration, amplifier, chance));
            }

            List<String> warningLore = foodSec.getStringList("WarningLore");

            CustomFoodDefinition def = new CustomFoodDefinition(
                id, displayName, baseMaterial, cmd, hunger, saturation,
                macros, flags, effects, warningLore, isFood
            );
            definitions.put(id.toLowerCase(), def);
        }
        Utils.logStartup("Loaded " + definitions.size() + " custom food definitions");
    }

    public ItemStack createItemStack(String foodId, int count) {
        CustomFoodDefinition def = definitions.get(foodId.toLowerCase());
        if (def == null) throw new IllegalArgumentException("Unknown custom food: " + foodId);

        ItemStack stack = new ItemStack(def.getBaseMaterial(), count);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return stack;

        meta.setDisplayName(def.getDisplayName());

        List<String> warningLines = def.getWarningLore();
        if (!warningLines.isEmpty()) {
            List<String> lore = new ArrayList<>();
            for (String line : warningLines) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line));
            }
            meta.setLore(lore);
        }

        if (def.getCustomModelData() > 0) {
            Utils.setCustomModelData(meta, def.getCustomModelData());
        }

        // Set ItemModel for 1.21+ resource pack lookup
        Utils.setItemModel(meta, new NamespacedKey("harshlands", "foodexpansion/" + def.getId()));

        stack.setItemMeta(meta);

        Utils.addNbtTag(stack, PDC_KEY, def.getId(), PersistentDataType.STRING);

        return stack;
    }

    public boolean isCustomFood(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return Utils.hasNbtTag(item, PDC_KEY);
    }

    @Nullable
    public String getFoodId(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return Utils.getNbtTag(item, PDC_KEY, PersistentDataType.STRING);
    }

    @Nullable
    public CustomFoodDefinition getDefinition(String foodId) {
        return definitions.get(foodId.toLowerCase());
    }

    public Collection<CustomFoodDefinition> getAllDefinitions() {
        return Collections.unmodifiableCollection(definitions.values());
    }

    public Set<String> getAllIds() {
        return Collections.unmodifiableSet(definitions.keySet());
    }
}
