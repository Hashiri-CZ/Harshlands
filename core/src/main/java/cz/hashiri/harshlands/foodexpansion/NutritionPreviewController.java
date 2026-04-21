package cz.hashiri.harshlands.foodexpansion;

import cz.hashiri.harshlands.data.HLPlayer;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodRegistry;
import cz.hashiri.harshlands.foodexpansion.items.CustomFoodDefinition;
import cz.hashiri.harshlands.locale.Messages;
import cz.hashiri.harshlands.utils.AboveActionBarHUD;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Per-player task that decides whether to show the three-cell nutrient preview
 * strip based on the player's currently-held main-hand item. Runs on a fixed tick
 * cadence (configurable via {@code FoodExpansion.HUD.Preview.RefreshTicks}).
 */
public class NutritionPreviewController extends BukkitRunnable {

    private final Player player;
    private final FoodExpansionModule module;
    private AboveActionBarHUD aboveHud; // lazily obtained
    private String lastSignature = null; // no-op guard

    private final int iconWidth;
    private final int iconTextGap;
    private final int cellSpacing;

    public NutritionPreviewController(Player player, FoodExpansionModule module) {
        this.player = player;
        this.module = module;
        org.bukkit.configuration.file.FileConfiguration cfg = module.getUserConfig().getConfig();
        this.iconWidth   = cfg.getInt("FoodExpansion.HUD.IconWidth", 32);
        this.iconTextGap = cfg.getInt("FoodExpansion.HUD.Preview.IconTextGap", 4);
        this.cellSpacing = cfg.getInt("FoodExpansion.HUD.Preview.CellSpacing", 24);
    }

    @Override
    public void run() {
        if (!player.isOnline()) {
            cancel();
            return;
        }
        if (!module.isEnabled(player)) {
            clear();
            return;
        }
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            clear();
            return;
        }
        // Hide during active eating — isHandRaised() is true during the right-click-hold
        // animation. Comparing the active item to the main-hand stack avoids hiding if the
        // player is, say, raising a shield in the off-hand.
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (player.isHandRaised() && player.getItemInUse() != null
                && player.getItemInUse().isSimilar(mainHand)) {
            clear();
            return;
        }
        if (mainHand == null || mainHand.getType() == Material.AIR) {
            clear();
            return;
        }
        CustomFoodRegistry cfRegistry = module.getCustomFoodRegistry();
        boolean isEdible = mainHand.getType().isEdible()
                || (cfRegistry != null && cfRegistry.isCustomFood(mainHand));
        if (!isEdible) {
            clear();
            return;
        }
        String itemKey;
        if (cfRegistry != null && cfRegistry.isCustomFood(mainHand)) {
            itemKey = cfRegistry.getFoodId(mainHand);
        } else {
            itemKey = mainHand.getType().name();
        }
        NutrientProfile profile = module.getNutrientProfile(itemKey);
        if (profile == null) {
            clear();
            return;
        }

        // Pull current nutrition data
        HLPlayer hl = HLPlayer.getPlayers().get(player.getUniqueId());
        if (hl == null) { clear(); return; }
        cz.hashiri.harshlands.data.foodexpansion.DataModule dm = hl.getNutritionDataModule();
        if (dm == null || dm.getData() == null) { clear(); return; }
        PlayerNutritionData data = dm.getData();

        double comfort = module.getComfortMultiplier(player);

        // No-op guard: build a cheap signature and bail if nothing meaningful changed.
        String sig = itemKey + "|" + (int) data.getProtein() + "|" + (int) data.getCarbs()
                + "|" + (int) data.getFats() + "|" + comfort
                + "|" + profile.protein() + "|" + profile.carbs() + "|" + profile.fats();
        if (sig.equals(lastSignature)) return;
        lastSignature = sig;

        NutritionPreviewLayout.Row row = NutritionPreviewLayout.buildRow(
                profile,
                data.getProtein(), data.getCarbs(), data.getFats(),
                comfort,
                module.getSevereThreshold(), module.getMalnourishedThreshold(),
                module.getWellNourishedThreshold(), module.getPeakThreshold(),
                Messages.get("foodexpansion.food_expansion.preview.protein"),
                Messages.get("foodexpansion.food_expansion.preview.carbs"),
                Messages.get("foodexpansion.food_expansion.preview.fat"),
                iconWidth, iconTextGap, cellSpacing);

        if (aboveHud == null) {
            aboveHud = module.getOrCreateAboveActionBarHud(player);
        }
        aboveHud.setPreviewContent(row.component(), row.advance());
    }

    /** Clear the preview (used by gating failures and on shutdown). */
    public void clear() {
        lastSignature = null;
        if (aboveHud == null) {
            // Avoid creating a HUD just to clear it if one never existed.
            aboveHud = module.getOrCreateAboveActionBarHud(player);
        }
        if (aboveHud.isPreviewActive()) {
            aboveHud.clearPreview();
        }
    }
}
