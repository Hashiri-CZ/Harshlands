package cz.hashiri.harshlands.data.foodexpansion;

import cz.hashiri.harshlands.data.HLDataModule;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.db.HLDatabase;
import cz.hashiri.harshlands.foodexpansion.FoodExpansionModule;
import cz.hashiri.harshlands.foodexpansion.PlayerNutritionData;
import cz.hashiri.harshlands.rsv.HLPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.UUID;

public class DataModule implements HLDataModule {

    private final UUID id;
    private final HLDatabase database;
    private final PlayerNutritionData data;

    public DataModule(Player player) {
        FoodExpansionModule module = (FoodExpansionModule) HLModule.getModule(FoodExpansionModule.NAME);
        FileConfiguration config = module.getUserConfig().getConfig();
        HLPlugin plugin = HLPlugin.getPlugin();
        this.database = plugin.getDatabase();
        this.id = player.getUniqueId();

        double defaultProtein = config.getDouble("FoodExpansion.Defaults.Protein", 50.0);
        double defaultCarbs = config.getDouble("FoodExpansion.Defaults.Carbs", 50.0);
        double defaultFats = config.getDouble("FoodExpansion.Defaults.Fats", 50.0);

        this.data = new PlayerNutritionData(defaultProtein, defaultCarbs, defaultFats, 0.0, 0.0, 0.0);
    }

    @Override
    public void retrieveData() {
        database.loadNutritionData(id).thenAccept(optional -> {
            if (optional.isPresent()) {
                HLDatabase.NutritionDataRow row = optional.get();
                data.restoreFromRow(
                    row.protein(), row.carbs(), row.fats(),
                    row.proteinExhaustion(), row.carbsExhaustion(), row.fatsExhaustion()
                );
            } else {
                saveData();
            }
        });
    }

    @Override
    public void saveData() {
        HLDatabase.NutritionDataRow snapshot = new HLDatabase.NutritionDataRow(
            data.getProtein(), data.getCarbs(), data.getFats(),
            data.getProteinExhaustion(), data.getCarbsExhaustion(), data.getFatsExhaustion()
        );
        data.clearDirty();
        database.saveNutritionData(id, snapshot);
    }

    public PlayerNutritionData getData() {
        return data;
    }

    public boolean isDirty() {
        return data.isDirty();
    }
}
