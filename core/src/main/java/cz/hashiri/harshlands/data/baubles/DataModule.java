package cz.hashiri.harshlands.data.baubles;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import cz.hashiri.harshlands.baubles.BaubleModule;
import cz.hashiri.harshlands.data.HLDataModule;
import cz.hashiri.harshlands.data.HLModule;
import cz.hashiri.harshlands.data.db.HLDatabase;
import cz.hashiri.harshlands.rsv.HLPlugin;
import cz.hashiri.harshlands.utils.HLItem;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class DataModule implements HLDataModule {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Logger LOGGER = Bukkit.getLogger();

    private final HLDatabase database;
    private final BaubleInventory baubleBag;
    private final UUID id;

    public DataModule(Player player) {
        this.baubleBag = new BaubleInventory(player);
        this.database = HLPlugin.getPlugin().getDatabase();
        this.id = player.getUniqueId();
    }

    public BaubleInventory getBaubleBag() {
        return baubleBag;
    }

    public boolean hasBauble(String name) {
        return baubleBag.hasBauble(name);
    }

    public int getBaubleAmount(String name) {
        return baubleBag.getBaubleAmount(name);
    }

    @Override
    public void retrieveData() {
        database.loadBaublesJson(id).thenAccept(optional -> {
            if (optional.isEmpty()) {
                LOGGER.info("[Baubles] No existing data found for " + id + ". Filling default items.");
                baubleBag.fillDefaultItems();
                return;
            }

            String jsonString = optional.get();
            if (jsonString.isEmpty()) {
                LOGGER.warning("[Baubles] JSON was empty for " + id + ". Filling defaults.");
                baubleBag.fillDefaultItems();
                return;
            }

            Inventory inv = baubleBag.getInventory();
            try {
                Type newType = new TypeToken<Map<String, Map<String, Object>>>() {}.getType();
                Map<String, Map<String, Object>> serializedItems = GSON.fromJson(jsonString, newType);

                if (serializedItems == null || serializedItems.isEmpty()) {
                    LOGGER.warning("[Baubles] JSON was parsed but empty for " + id + ". Filling defaults.");
                    baubleBag.fillDefaultItems();
                    return;
                }

                for (Map.Entry<String, Map<String, Object>> entry : serializedItems.entrySet()) {
                    int slot = Integer.parseInt(entry.getKey());
                    Map<String, Object> itemData = entry.getValue();
                    if (itemData != null && !itemData.isEmpty()) {
                        ItemStack item = ItemStack.deserialize(itemData);
                        inv.setItem(slot, item);
                    }
                }

                LOGGER.info("[Baubles] Successfully loaded data for " + id + " (JSON format)");

            } catch (JsonSyntaxException ex) {
                LOGGER.warning("[Baubles] Detected legacy Base64 format for " + id + ". Converting...");
                try {
                    Type legacyType = new TypeToken<Map<String, String>>() {}.getType();
                    Map<String, String> legacyItems = GSON.fromJson(jsonString, legacyType);
                    Map<String, Map<String, Object>> convertedMap = new HashMap<>();

                    if (legacyItems != null) {
                        for (Map.Entry<String, String> entry : legacyItems.entrySet()) {
                            int slot = Integer.parseInt(entry.getKey());
                            String base64 = entry.getValue();
                            if (base64 != null && !base64.isEmpty()) {
                                ItemStack item = deserializeItem(base64);
                                inv.setItem(slot, item);
                                convertedMap.put(entry.getKey(), item.serialize());
                            }
                        }
                        // Persist the converted format
                        String newJson = GSON.toJson(convertedMap);
                        database.saveBaublesJson(id, newJson);
                        LOGGER.info("[Baubles] Successfully converted Base64 -> JSON for " + id);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "[Baubles] Failed to convert Base64 data for " + id, e);
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "[Baubles] Unexpected error loading data for " + id, e);
            }

            baubleBag.fillDefaultItems();
        });
    }

    @Override
    public void saveData() {
        Inventory inv = baubleBag.getInventory();
        BaubleSlot[] values = BaubleSlot.values();
        Pattern ignoreSlotPattern = Pattern.compile("^(charm|body|ring|belt|amulet|head)_slot$");

        Map<String, Map<String, Object>> serializedMap = new HashMap<>();
        for (BaubleSlot slot : values) {
            for (int i : slot.getValues()) {
                ItemStack item = inv.getItem(i);
                if (HLItem.isHLItem(item) && !ignoreSlotPattern.matcher(HLItem.getNameFromItem(item)).find()) {
                    serializedMap.put(String.valueOf(i), item.serialize());
                }
            }
        }

        String jsonString = GSON.toJson(serializedMap);
        database.saveBaublesJson(id, jsonString);
        LOGGER.info("[Baubles] Saved bauble data for " + id);
    }

    private ItemStack deserializeItem(String base64) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(base64);
        try (ObjectInputStream inputStream = new org.bukkit.util.io.BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return (ItemStack) inputStream.readObject();
        }
    }
}
