package me.val_mobile.data.baubles;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import me.val_mobile.baubles.BaubleModule;
import me.val_mobile.data.RSVConfig;
import me.val_mobile.data.RSVDataModule;
import me.val_mobile.data.RSVModule;
import me.val_mobile.utils.RSVItem;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class DataModule implements RSVDataModule {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Logger LOGGER = Bukkit.getLogger();

    private final RSVConfig config;
    private final BaubleInventory baubleBag;
    private final UUID id;

    public DataModule(Player player) {
        this.baubleBag = new BaubleInventory(player);
        this.config = ((BaubleModule) RSVModule.getModule(BaubleModule.NAME)).getPlayerDataConfig();
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
        FileConfiguration config = this.config.getConfig();
        Inventory inv = baubleBag.getInventory();

        String jsonString = config.getString(id + ".Items");
        if (jsonString == null || jsonString.isEmpty()) {
            LOGGER.info("[Baubles] No existing data found for " + id + ". Filling default items.");
            baubleBag.fillDefaultItems();
            return;
        }

        try {
            // Try deserializing as new format first
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
                // Fall back to Base64 format
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

                    // Save converted data
                    String newJson = GSON.toJson(convertedMap);
                    config.set(id + ".Items", newJson);
                    saveFile(config);

                    LOGGER.info("[Baubles] Successfully converted Base64 -> JSON for " + id);
                }
            } catch (Exception e) {
                LOGGER.severe("[Baubles] Failed to convert Base64 data for " + id);
                e.printStackTrace();
            }
        } catch (Exception e) {
            LOGGER.severe("[Baubles] Unexpected error loading data for " + id);
            e.printStackTrace();
        }

        baubleBag.fillDefaultItems();
    }

    @Override
    public void saveData() {
        FileConfiguration config = this.config.getConfig();
        Inventory inv = baubleBag.getInventory();

        BaubleSlot[] values = BaubleSlot.values();
        Pattern ignoreSlotPattern = Pattern.compile("^(charm|body|ring|belt|amulet|head)_slot$");

        Map<String, Map<String, Object>> serializedMap = new HashMap<>();

        for (BaubleSlot slot : values) {
            for (int i : slot.getValues()) {
                ItemStack item = inv.getItem(i);
                if (RSVItem.isRSVItem(item) && !ignoreSlotPattern.matcher(RSVItem.getNameFromItem(item)).find()) {
                    serializedMap.put(String.valueOf(i), item.serialize());
                }
            }
        }

        String jsonString = GSON.toJson(serializedMap);
        config.set(id + ".Items", jsonString);
        saveFile(config);

        LOGGER.info("[Baubles] Saved bauble data for " + id);
    }

    public void saveFile(FileConfiguration config) {
        try {
            config.save(this.config.getFile());
        } catch (IOException e) {
            LOGGER.severe("[Baubles] Failed to save config for " + id);
            e.printStackTrace();
        }
    }

    private ItemStack deserializeItem(String base64) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(base64);
        try (ObjectInputStream inputStream = new org.bukkit.util.io.BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            return (ItemStack) inputStream.readObject();
        }
    }
}
