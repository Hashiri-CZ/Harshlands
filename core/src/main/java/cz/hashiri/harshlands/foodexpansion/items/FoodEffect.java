package cz.hashiri.harshlands.foodexpansion.items;

import org.bukkit.potion.PotionEffectType;

public record FoodEffect(PotionEffectType type, int durationTicks, int amplifier, double chance) {}
