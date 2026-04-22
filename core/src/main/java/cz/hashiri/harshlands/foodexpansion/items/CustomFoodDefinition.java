package cz.hashiri.harshlands.foodexpansion.items;

import cz.hashiri.harshlands.foodexpansion.NutrientProfile;
import org.bukkit.Material;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

public class CustomFoodDefinition {
    private final String id;
    private final String displayName;
    private final Material baseMaterial;
    private final int customModelData;
    private final int hunger;
    private final float saturation;
    private final NutrientProfile macros;
    private final EnumSet<FoodFlag> flags;
    private final List<FoodEffect> effects;
    private final List<String> warningLore;
    private final boolean isFood; // false for ingredients like dough

    public CustomFoodDefinition(String id, String displayName, Material baseMaterial,
                                 int customModelData, int hunger, float saturation,
                                 @Nullable NutrientProfile macros, EnumSet<FoodFlag> flags,
                                 List<FoodEffect> effects, List<String> warningLore, boolean isFood) {
        this.id = id;
        this.displayName = displayName;
        this.baseMaterial = baseMaterial;
        this.customModelData = customModelData;
        this.hunger = hunger;
        this.saturation = saturation;
        this.macros = macros;
        this.flags = flags;
        this.effects = effects;
        this.warningLore = warningLore;
        this.isFood = isFood;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public Material getBaseMaterial() { return baseMaterial; }
    public int getCustomModelData() { return customModelData; }
    public int getHunger() { return hunger; }
    public float getSaturation() { return saturation; }
    @Nullable public NutrientProfile getMacros() { return macros; }
    public EnumSet<FoodFlag> getFlags() { return flags; }
    public List<FoodEffect> getEffects() { return effects; }
    @Nonnull public List<String> getWarningLore() { return warningLore; }
    public boolean isFood() { return isFood; }
    public boolean hasFlag(FoodFlag flag) { return flags.contains(flag); }
}
