package me.val_mobile.data.baubles;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import me.val_mobile.baubles.BaubleModule;
import me.val_mobile.data.RSVConfig;
import me.val_mobile.data.RSVDataModule;
import me.val_mobile.data.RSVModule;
import me.val_mobile.utils.RSVItem;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Pattern;
import java.util.Base64;

public class DataModule implements RSVDataModule {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

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
            // no saved data, just fill defaults
            baubleBag.fillDefaultItems();
            return;
        }

        try {
            // Deserialize JSON string into Map<String, String> where key=slot, value=Base64 ItemStack
            Type type = new TypeToken<Map<String, String>>(){}.getType();
            Map<String, String> serializedItems = GSON.fromJson(jsonString, type);

            for (Map.Entry<String, String> entry : serializedItems.entrySet()) {
                int slot = Integer.parseInt(entry.getKey());
                String base64 = entry.getValue();
                if (base64 != null && !base64.isEmpty()) {
                    ItemStack item = deserializeItem(base64);
                    inv.setItem(slot, item);
                }
            }

        } catch (Exception e) {
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

        Map<String, String> serializedMap = new HashMap<>();

        for (BaubleSlot slot : values) {
            for (int i : slot.getValues()) {
                ItemStack item = inv.getItem(i);
                if (RSVItem.isRSVItem(item) && !ignoreSlotPattern.matcher(RSVItem.getNameFromItem(item)).find()) {
                    try {
                        String encoded = serializeItem(item);
                        serializedMap.put(String.valueOf(i), encoded);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        // Save the entire map as a single JSON string at UUID.Items
        String jsonString = GSON.toJson(serializedMap);
        config.set(id + ".Items", jsonString);

        saveFile(config);
    }

    public void saveFile(FileConfiguration config) {
        try {
            config.save(this.config.getFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String serializeItem(ItemStack item) throws IOException {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(byteStream);
        dataOutput.writeObject(item);
        dataOutput.close();
        return Base64.getEncoder().encodeToString(byteStream.toByteArray());
    }

    private ItemStack deserializeItem(String base64) throws IOException, ClassNotFoundException {
        byte[] data = Base64.getDecoder().decode(base64);
        BukkitObjectInputStream inputStream = new BukkitObjectInputStream(new ByteArrayInputStream(data));
        ItemStack item = (ItemStack) inputStream.readObject();
        inputStream.close();
        return item;
    }
}
