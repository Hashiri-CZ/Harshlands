package cz.hashiri.harshlands.iceandfire;

import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.utils.HLItem;
import cz.hashiri.harshlands.utils.HLMob;
import cz.hashiri.harshlands.utils.Utils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ElderGuardian;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Guardian;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class SirenUtils {

    private static final FileConfiguration CONFIG = HLModule.getModule(IceFireModule.NAME).getUserConfig().getConfig();

    public static void convertToSiren(ElderGuardian elderGuardian) {
        Utils.addNbtTag(elderGuardian, "rsvmob", "siren", PersistentDataType.STRING);
    }

    public static boolean isSiren(Entity entity) {
        if (HLMob.isMob(entity)) {
            return HLMob.getMob(entity).equals("siren");
        }
        return false;
    }

    public static Collection<ItemStack> generateLoot(Guardian siren) {
        Set<String> keys = CONFIG.getConfigurationSection("Siren.LootTable").getKeys(false);
        Collection<ItemStack> items = new ArrayList<>();
        ItemStack tool = siren.getKiller() == null ? null : siren.getKiller().getInventory().getItemInMainHand();

        for (String key : keys) {
            items.add(Utils.getMobLoot(CONFIG.getConfigurationSection("Siren.LootTable." + key), HLItem.getItem(key), tool, true));
        }
        return items;
    }
}

