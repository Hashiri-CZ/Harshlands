package cz.hashiri.harshlands.foodexpansion.items;

public enum FoodFlag {
    MEAT,      // Wolves can be fed with it
    FAST_EAT,  // Informational: base material should be DRIED_KELP (16 ticks)
    BOWL,      // Returns bowl after eating
    ALWAYS_EAT // Can eat when hunger bar is full
}
