package com.invbackup.manager;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Logger;

public final class SerializationUtil {

    private static final Base64.Encoder ENCODER = Base64.getMimeEncoder(76, new byte[]{'\n'});
    private static final Base64.Decoder DECODER = Base64.getMimeDecoder();

    private SerializationUtil() {
    }

    public static String itemStackArrayToBase64(ItemStack[] items) {
        Logger logger = Bukkit.getLogger();
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(items.length);
            for (int i = 0; i < items.length; i++) {
                ItemStack item = items[i];
                try {
                    dataOutput.writeObject(item);
                } catch (Exception e) {
                    if (logger != null) {
                        logger.warning("Failed to serialize item at index " + i + ": " + e.getMessage());
                    }
                    dataOutput.writeObject(null);
                }
            }

            dataOutput.close();
            return ENCODER.encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException {
        Logger logger = Bukkit.getLogger();
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(DECODER.decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack[] items = new ItemStack[dataInput.readInt()];

            for (int i = 0; i < items.length; i++) {
                try {
                    Object obj = dataInput.readObject();
                    items[i] = (obj instanceof ItemStack) ? (ItemStack) obj : null;
                } catch (ClassNotFoundException e) {
                    if (logger != null) {
                        logger.warning("Failed to deserialize item at index " + i + ": " + e.getMessage());
                    }
                    items[i] = null;
                }
            }

            dataInput.close();
            return items;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Unable to decode item stacks.", e);
        }
    }

    public static String inventoryToBase64(Inventory inventory) {
        Logger logger = Bukkit.getLogger();
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            dataOutput.writeInt(inventory.getSize());
            for (int i = 0; i < inventory.getSize(); i++) {
                try {
                    dataOutput.writeObject(inventory.getItem(i));
                } catch (Exception e) {
                    if (logger != null) {
                        logger.warning("Failed to serialize inventory item at index " + i + ": " + e.getMessage());
                    }
                    dataOutput.writeObject(null);
                }
            }

            dataOutput.close();
            return ENCODER.encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save inventory.", e);
        }
    }
}
