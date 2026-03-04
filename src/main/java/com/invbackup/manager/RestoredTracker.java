package com.invbackup.manager;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Tracks which items/status have been restored from a specific snapshot.
 * Data is stored inside the history file under "restored.<snapshotId>".
 */
public class RestoredTracker {

    private final File historyFile;
    private final String snapshotId;
    private final Logger logger;
    private final YamlConfiguration config;
    private final String basePath;

    public RestoredTracker(File historyFile, String snapshotId, Logger logger) {
        this.historyFile = historyFile;
        this.snapshotId = snapshotId;
        this.logger = logger;
        this.basePath = "restored." + snapshotId;

        if (historyFile.exists()) {
            config = YamlConfiguration.loadConfiguration(historyFile);
        } else {
            config = new YamlConfiguration();
        }
    }

    public boolean isSlotRestored(int slot) {
        return getRestoredSlots().contains(slot);
    }

    public void markSlotRestored(int slot) {
        Set<Integer> slots = getRestoredSlots();
        slots.add(slot);
        config.set(basePath + ".slots", new ArrayList<>(slots));
        save();
    }

    public Set<Integer> getRestoredSlots() {
        return new HashSet<>(config.getIntegerList(basePath + ".slots"));
    }

    public boolean isArmorRestored(int index) {
        List<Boolean> armor = getArmorRestored();
        return index < armor.size() && armor.get(index);
    }

    public void markArmorRestored(int index) {
        List<Boolean> armor = getArmorRestored();
        while (armor.size() <= index) {
            armor.add(false);
        }
        armor.set(index, true);
        config.set(basePath + ".armor", armor);
        save();
    }

    private List<Boolean> getArmorRestored() {
        return new ArrayList<>(config.getBooleanList(basePath + ".armor"));
    }

    public boolean isOffhandRestored() {
        return config.getBoolean(basePath + ".offhand", false);
    }

    public void markOffhandRestored() {
        config.set(basePath + ".offhand", true);
        save();
    }

    public boolean isEnderchestSlotRestored(int slot) {
        return getRestoredEnderchestSlots().contains(slot);
    }

    public void markEnderchestSlotRestored(int slot) {
        Set<Integer> slots = getRestoredEnderchestSlots();
        slots.add(slot);
        config.set(basePath + ".enderchest-slots", new ArrayList<>(slots));
        save();
    }

    public Set<Integer> getRestoredEnderchestSlots() {
        return new HashSet<>(
                config.getIntegerList(basePath + ".enderchest-slots"));
    }

    public boolean isStatusRestored(String key) {
        return config.getBoolean(basePath + ".status." + key, false);
    }

    public void markStatusRestored(String key) {
        config.set(basePath + ".status." + key, true);
        save();
    }

    public void markAllRestored(int inventorySize, int armorSize,
                                boolean hasOffhand, int enderchestSize) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inventorySize; i++) {
            slots.add(i);
        }
        config.set(basePath + ".slots", slots);

        List<Boolean> armor = new ArrayList<>();
        for (int i = 0; i < armorSize; i++) {
            armor.add(true);
        }
        config.set(basePath + ".armor", armor);

        if (hasOffhand) {
            config.set(basePath + ".offhand", true);
        }

        List<Integer> ecSlots = new ArrayList<>();
        for (int i = 0; i < enderchestSize; i++) {
            ecSlots.add(i);
        }
        config.set(basePath + ".enderchest-slots", ecSlots);

        config.set(basePath + ".status.health", true);
        config.set(basePath + ".status.exp", true);
        config.set(basePath + ".status.food", true);
        config.set(basePath + ".status.location", true);
        config.set(basePath + ".status.effects", true);
        config.set(basePath + ".status.gamemode", true);

        save();
    }

    public boolean hasAnyRestored() {
        return config.contains(basePath);
    }

    private void save() {
        config.set(basePath + ".restored-at", System.currentTimeMillis());
        try {
            historyFile.getParentFile().mkdirs();
            config.save(historyFile);
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to save restore tracker", e);
        }
    }
}
