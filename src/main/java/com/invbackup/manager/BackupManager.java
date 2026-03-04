package com.invbackup.manager;

import com.invbackup.InvBackup;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class BackupManager {

    private final InvBackup plugin;
    private final File dataFolder;
    private final File currentFolder;
    private final File historyFolder;
    private final File importsFolder;
    private final File pendingFolder;

    public BackupManager(InvBackup plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "data");
        this.currentFolder = new File(dataFolder, "current");
        this.historyFolder = new File(dataFolder, "history");
        this.importsFolder = new File(dataFolder, "imports");
        this.pendingFolder = new File(dataFolder, "pending");

        migrateOldStructure();

        currentFolder.mkdirs();
        historyFolder.mkdirs();
        importsFolder.mkdirs();
        pendingFolder.mkdirs();
    }

    // ========== Migration ==========

    private void migrateOldStructure() {
        // Migrate from old snapshots/ + profiles/ structure
        File oldSnapshots = new File(dataFolder, "snapshots");
        File oldProfiles = new File(dataFolder, "profiles");

        if (oldSnapshots.exists() && oldSnapshots.isDirectory()) {
            plugin.getLogger().info("Migrating old snapshots/ to history/ ...");
            historyFolder.mkdirs();

            File[] playerDirs = oldSnapshots.listFiles(File::isDirectory);
            if (playerDirs != null) {
                for (File playerDir : playerDirs) {
                    if (!isUuid(playerDir.getName())) continue;
                    migratePlayerSnapshots(playerDir);
                }
            }

            // Clean up empty snapshots dir
            deleteEmptyDir(oldSnapshots);
            plugin.getLogger().info("Snapshots migration complete.");
        }

        if (oldProfiles.exists() && oldProfiles.isDirectory()) {
            plugin.getLogger().info("Migrating old profiles/ to imports/profiles/ ...");
            File importDest = new File(importsFolder, "profiles");
            if (!importDest.exists()) {
                if (oldProfiles.renameTo(importDest)) {
                    plugin.getLogger().info("Profiles migrated to imports/profiles/");
                } else {
                    plugin.getLogger().warning("Failed to migrate profiles/");
                }
            }
        }

        // Migrate from even older data/{UUID}/ structure
        if (!dataFolder.exists()) return;
        File[] files = dataFolder.listFiles();
        if (files == null) return;

        boolean hasUuidFolders = false;
        for (File f : files) {
            if (f.isDirectory() && isUuid(f.getName())
                    && !f.getName().equals("current") && !f.getName().equals("history")
                    && !f.getName().equals("imports") && !f.getName().equals("pending")) {
                hasUuidFolders = true;
                break;
            }
        }

        if (hasUuidFolders) {
            plugin.getLogger().info("Migrating legacy data/{UUID}/ structure...");
            historyFolder.mkdirs();
            for (File f : files) {
                if (f.isDirectory() && isUuid(f.getName())) {
                    migratePlayerSnapshots(f);
                    deleteEmptyDir(f);
                }
            }
            plugin.getLogger().info("Legacy migration complete.");
        }
    }

    private void migratePlayerSnapshots(File playerDir) {
        String uuid = playerDir.getName();
        File historyFile = new File(historyFolder, uuid + ".yml");

        YamlConfiguration history;
        if (historyFile.exists()) {
            history = YamlConfiguration.loadConfiguration(historyFile);
        } else {
            history = new YamlConfiguration();
        }

        File[] snapFiles = playerDir.listFiles((d, name) ->
                name.endsWith(".yml") && !name.endsWith(".restored.yml"));
        if (snapFiles == null) return;

        String playerName = null;

        for (File snapFile : snapFiles) {
            String snapshotId = snapFile.getName().replace(".yml", "");
            if (history.contains("snapshots." + snapshotId)) continue;

            YamlConfiguration snapConfig = YamlConfiguration.loadConfiguration(snapFile);
            if (playerName == null) {
                playerName = snapConfig.getString("meta.target", uuid);
            }

            // Copy all data under snapshots.<id>
            history.set("snapshots." + snapshotId + ".meta",
                    snapConfig.getConfigurationSection("meta"));
            if (snapConfig.contains("inventory")) {
                history.set("snapshots." + snapshotId + ".inventory",
                        snapConfig.getConfigurationSection("inventory"));
            }
            if (snapConfig.contains("status")) {
                history.set("snapshots." + snapshotId + ".status",
                        snapConfig.getConfigurationSection("status"));
            }

            snapFile.delete();

            // Also delete .restored.yml sidecar
            File restoredFile = new File(playerDir,
                    snapshotId + ".restored.yml");
            if (restoredFile.exists()) {
                // Migrate restored tracking into history file
                YamlConfiguration restoredConfig =
                        YamlConfiguration.loadConfiguration(restoredFile);
                history.set("restored." + snapshotId, restoredConfig);
                restoredFile.delete();
            }
        }

        if (playerName != null && !history.contains("player-name")) {
            history.set("player-name", playerName);
        }
        history.set("player-uuid", uuid);

        try {
            history.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to save migrated history for " + uuid, e);
        }
    }

    private void deleteEmptyDir(File dir) {
        File[] remaining = dir.listFiles();
        if (remaining != null && remaining.length == 0) {
            dir.delete();
        }
    }

    // ========== Save ==========

    public String saveBackup(Player target, String triggeredByName,
                             String triggeredByUuid, String triggerType, String label) {
        String uuid = target.getUniqueId().toString();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                .format(new Date());

        // Build snapshot data
        YamlConfiguration snapData = new YamlConfiguration();

        snapData.set("meta.target", target.getName());
        snapData.set("meta.target-uuid", uuid);
        snapData.set("meta.triggered-by", triggeredByName);
        snapData.set("meta.triggered-by-uuid", triggeredByUuid);
        snapData.set("meta.trigger-type", triggerType);
        snapData.set("meta.label", label != null ? label : "");
        snapData.set("meta.timestamp", System.currentTimeMillis());

        String backupLevel = plugin.getConfig().getString("backup-level", "full");
        snapData.set("meta.backup-level", backupLevel);

        snapData.set("inventory.content",
                SerializationUtil.itemStackArrayToBase64(
                        target.getInventory().getContents()));
        snapData.set("inventory.armor",
                SerializationUtil.itemStackArrayToBase64(
                        target.getInventory().getArmorContents()));
        snapData.set("inventory.offhand",
                SerializationUtil.itemStackArrayToBase64(
                        new ItemStack[]{target.getInventory().getItemInOffHand()}));
        snapData.set("inventory.enderchest",
                SerializationUtil.itemStackArrayToBase64(
                        target.getEnderChest().getContents()));

        if ("full".equalsIgnoreCase(backupLevel)) {
            saveStatusData(snapData, target);
        }

        // Ensure unique snapshot ID
        String snapshotId = timestamp;
        File historyFile = new File(historyFolder, uuid + ".yml");
        YamlConfiguration history;
        if (historyFile.exists()) {
            history = YamlConfiguration.loadConfiguration(historyFile);
        } else {
            history = new YamlConfiguration();
        }

        int counter = 1;
        while (history.contains("snapshots." + snapshotId)) {
            snapshotId = timestamp + "_" + counter;
            counter++;
        }

        // Write to history file
        history.set("player-name", target.getName());
        history.set("player-uuid", uuid);

        ConfigurationSection snapSection = snapData.getRoot();
        for (String key : snapSection.getKeys(false)) {
            Object value = snapSection.get(key);
            history.set("snapshots." + snapshotId + "." + key, value);
        }

        try {
            historyFile.getParentFile().mkdirs();
            history.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to save backup for " + target.getName(), e);
            return null;
        }

        // Write to current/ (latest snapshot only)
        saveCurrentSnapshot(uuid, target.getName(), snapshotId, snapData);

        // Cleanup old snapshots
        cleanupOldBackups(uuid, history, historyFile);

        plugin.getLogger().info("Saved backup for " + target.getName()
                + ": " + snapshotId);
        return snapshotId;
    }

    private void saveCurrentSnapshot(String uuid, String playerName,
                                     String snapshotId,
                                     YamlConfiguration snapData) {
        File currentFile = new File(currentFolder, uuid + ".yml");
        YamlConfiguration current = new YamlConfiguration();
        current.set("player-name", playerName);
        current.set("player-uuid", uuid);

        ConfigurationSection snapSection = snapData.getRoot();
        for (String key : snapSection.getKeys(false)) {
            current.set("snapshots." + snapshotId + "." + key,
                    snapSection.get(key));
        }

        try {
            currentFile.getParentFile().mkdirs();
            // Write to temp file first, then rename for safety
            File tmpFile = new File(currentFile.getParent(),
                    currentFile.getName() + ".tmp");
            current.save(tmpFile);
            if (currentFile.exists()) currentFile.delete();
            tmpFile.renameTo(currentFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to update current backup for " + playerName, e);
        }
    }

    private void saveStatusData(YamlConfiguration config, Player target) {
        config.set("status.exp", target.getExp());
        config.set("status.level", target.getLevel());
        config.set("status.total-experience", target.getTotalExperience());
        config.set("status.health", target.getHealth());

        var maxHealthAttr = target.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttr != null) {
            config.set("status.max-health", maxHealthAttr.getValue());
        }

        config.set("status.food", target.getFoodLevel());
        config.set("status.saturation", target.getSaturation());
        config.set("status.gamemode", target.getGameMode().name());
        config.set("status.fly", target.isFlying());

        List<String> effects = new ArrayList<>();
        for (PotionEffect effect : target.getActivePotionEffects()) {
            effects.add(effect.getType().getKey().getKey()
                    + ":" + effect.getDuration()
                    + ":" + effect.getAmplifier());
        }
        config.set("status.effects", effects);

        Location loc = target.getLocation();
        config.set("status.location.world", loc.getWorld().getName());
        config.set("status.location.x", loc.getX());
        config.set("status.location.y", loc.getY());
        config.set("status.location.z", loc.getZ());
        config.set("status.location.yaw", (double) loc.getYaw());
        config.set("status.location.pitch", (double) loc.getPitch());
    }

    // ========== Restore ==========

    public boolean restoreBackup(Player target, String uuid,
                                 String snapshotId, String restoreLevel) {
        YamlConfiguration config = loadBackupConfig(uuid, snapshotId);
        if (config == null) {
            return false;
        }
        return restoreFromConfig(target, config, restoreLevel);
    }

    public boolean restoreFromConfig(Player target, YamlConfiguration config,
                                     String restoreLevel) {
        String backupLevel = config.getString("meta.backup-level", "minimal");
        boolean restoreFull = "full".equalsIgnoreCase(restoreLevel)
                && "full".equalsIgnoreCase(backupLevel);

        try {
            if (config.contains("inventory.content")) {
                target.getInventory().setContents(
                        SerializationUtil.itemStackArrayFromBase64(
                                config.getString("inventory.content")));
            }
            if (config.contains("inventory.armor")) {
                target.getInventory().setArmorContents(
                        SerializationUtil.itemStackArrayFromBase64(
                                config.getString("inventory.armor")));
            }
            if (config.contains("inventory.offhand")) {
                ItemStack[] offhand = SerializationUtil.itemStackArrayFromBase64(
                        config.getString("inventory.offhand"));
                if (offhand.length > 0) {
                    target.getInventory().setItemInOffHand(offhand[0]);
                }
            }
            if (config.contains("inventory.enderchest")) {
                target.getEnderChest().setContents(
                        SerializationUtil.itemStackArrayFromBase64(
                                config.getString("inventory.enderchest")));
            }

            if (restoreFull && config.contains("status")) {
                restoreStatusData(target, config);
            }

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to restore backup for " + target.getName(), e);
            return false;
        }
    }

    private void restoreStatusData(Player target, YamlConfiguration config) {
        target.setExp((float) config.getDouble("status.exp"));
        target.setLevel(config.getInt("status.level"));
        target.setHealth(config.getDouble("status.health"));
        target.setFoodLevel(config.getInt("status.food"));
        target.setSaturation((float) config.getDouble("status.saturation"));

        if (config.contains("status.gamemode")) {
            try {
                target.setGameMode(GameMode.valueOf(
                        config.getString("status.gamemode")));
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (config.contains("status.fly")) {
            boolean fly = config.getBoolean("status.fly");
            target.setAllowFlight(fly);
            if (fly) target.setFlying(true);
        }

        for (PotionEffect effect : target.getActivePotionEffects()) {
            target.removePotionEffect(effect.getType());
        }
        for (String effectStr : config.getStringList("status.effects")) {
            String[] parts = effectStr.split(":");
            if (parts.length == 3) {
                PotionEffectType type = org.bukkit.Registry.EFFECT.match(parts[0]);
                if (type != null) {
                    target.addPotionEffect(new PotionEffect(type,
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2])));
                }
            }
        }

        if (config.contains("status.location")) {
            org.bukkit.World world = Bukkit.getWorld(
                    config.getString("status.location.world", ""));
            if (world != null) {
                target.teleport(new Location(world,
                        config.getDouble("status.location.x"),
                        config.getDouble("status.location.y"),
                        config.getDouble("status.location.z"),
                        (float) config.getDouble("status.location.yaw"),
                        (float) config.getDouble("status.location.pitch")));
            }
        }
    }

    // ========== List ==========

    public List<BackupInfo> listBackups(String uuid, String filterTriggerUuid) {
        List<BackupInfo> backups = new ArrayList<>();

        // From history file
        File historyFile = new File(historyFolder, uuid + ".yml");
        if (historyFile.exists()) {
            YamlConfiguration history =
                    YamlConfiguration.loadConfiguration(historyFile);
            ConfigurationSection snapshots =
                    history.getConfigurationSection("snapshots");
            if (snapshots != null) {
                List<String> keys = new ArrayList<>(snapshots.getKeys(false));
                keys.sort(Comparator.reverseOrder());

                for (String snapshotId : keys) {
                    ConfigurationSection sec =
                            snapshots.getConfigurationSection(snapshotId);
                    if (sec == null) continue;

                    String triggeredByUuid = sec.getString(
                            "meta.triggered-by-uuid", "");
                    if (filterTriggerUuid != null
                            && !filterTriggerUuid.equals(triggeredByUuid)) {
                        continue;
                    }

                    BackupInfo info = new BackupInfo();
                    info.snapshotId = snapshotId;
                    info.source = "history";
                    info.targetName = sec.getString("meta.target", "Unknown");
                    info.triggeredBy = sec.getString(
                            "meta.triggered-by", "Unknown");
                    info.triggeredByUuid = triggeredByUuid;
                    info.triggerType = sec.getString(
                            "meta.trigger-type", "unknown");
                    info.label = sec.getString("meta.label", "");
                    info.timestamp = sec.getLong("meta.timestamp", 0);
                    info.backupLevel = sec.getString(
                            "meta.backup-level", "minimal");

                    backups.add(info);
                }
            }
        }

        return backups;
    }

    // ========== Load config ==========

    /**
     * Load a single snapshot's config as a flat YamlConfiguration.
     * Supports snapshotIds from history, current, and import sources.
     * Import format: "import:<folder>:<snapshotId>" or "import:<folder>:cm:<gamemode>"
     */
    public YamlConfiguration loadBackupConfig(String uuid, String snapshotId) {
        // Import source
        if (snapshotId.startsWith("import:")) {
            return loadImportConfig(uuid, snapshotId);
        }

        // History source
        File historyFile = new File(historyFolder, uuid + ".yml");
        if (historyFile.exists()) {
            YamlConfiguration history =
                    YamlConfiguration.loadConfiguration(historyFile);
            ConfigurationSection sec =
                    history.getConfigurationSection("snapshots." + snapshotId);
            if (sec != null) {
                return sectionToConfig(sec);
            }
        }

        // Current source (fallback)
        File currentFile = new File(currentFolder, uuid + ".yml");
        if (currentFile.exists()) {
            YamlConfiguration current =
                    YamlConfiguration.loadConfiguration(currentFile);
            ConfigurationSection sec =
                    current.getConfigurationSection("snapshots." + snapshotId);
            if (sec != null) {
                return sectionToConfig(sec);
            }
        }

        return null;
    }

    private YamlConfiguration loadImportConfig(String uuid, String snapshotId) {
        // Format: "import:<folder>:<snapshotId>" or "import:<folder>:cm:<gamemode>"
        String[] parts = snapshotId.split(":", 4);
        if (parts.length < 3) return null;

        String folderName = parts[1];
        File importDir = new File(importsFolder, folderName);
        File importFile = new File(importDir, uuid + ".yml");
        if (!importFile.exists()) return null;

        YamlConfiguration importConfig =
                YamlConfiguration.loadConfiguration(importFile);

        // CM format: "import:<folder>:cm:<gamemode>"
        if (parts.length == 4 && "cm".equals(parts[2])) {
            String gamemode = parts[3];
            return convertCmToConfig(uuid, importConfig, gamemode);
        }

        // InvBackup format: "import:<folder>:<snapshotId>"
        String actualId = parts[2];
        ConfigurationSection sec =
                importConfig.getConfigurationSection("snapshots." + actualId);
        if (sec != null) {
            return sectionToConfig(sec);
        }

        return null;
    }

    private YamlConfiguration convertCmToConfig(String uuid,
                                                 YamlConfiguration cmConfig,
                                                 String gamemode) {
        if (!cmConfig.contains(gamemode + ".content")) {
            return null;
        }

        YamlConfiguration result = new YamlConfiguration();
        result.set("meta.target", uuid);
        result.set("meta.target-uuid", uuid);
        result.set("meta.triggered-by", "Import");
        result.set("meta.triggered-by-uuid", "");
        result.set("meta.trigger-type", "import");
        result.set("meta.label", "CM Import [" + gamemode + "]");
        result.set("meta.timestamp", System.currentTimeMillis());
        result.set("meta.backup-level", "minimal");
        result.set("meta.source", "CreativeManager");

        result.set("inventory.content",
                cmConfig.getString(gamemode + ".content"));
        result.set("inventory.armor",
                cmConfig.getString(gamemode + ".armor"));

        return result;
    }

    /** Convert a ConfigurationSection to a standalone YamlConfiguration. */
    private YamlConfiguration sectionToConfig(ConfigurationSection section) {
        YamlConfiguration config = new YamlConfiguration();
        for (String key : section.getKeys(true)) {
            if (!section.isConfigurationSection(key)) {
                config.set(key, section.get(key));
            }
        }
        return config;
    }

    // ========== Delete ==========

    public boolean deleteBackup(String uuid, String snapshotId) {
        if (snapshotId.startsWith("import:")) {
            return false;
        }

        File historyFile = new File(historyFolder, uuid + ".yml");
        if (!historyFile.exists()) return false;

        YamlConfiguration history =
                YamlConfiguration.loadConfiguration(historyFile);
        if (!history.contains("snapshots." + snapshotId)) {
            return false;
        }

        history.set("snapshots." + snapshotId, null);
        // Also remove restored tracking
        history.set("restored." + snapshotId, null);

        try {
            history.save(historyFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to delete backup " + snapshotId, e);
            return false;
        }
    }

    // ========== Cleanup ==========

    private void cleanupOldBackups(String uuid, YamlConfiguration history,
                                   File historyFile) {
        int maxSnapshots = plugin.getConfig().getInt("max-snapshots", 20);
        if (maxSnapshots <= 0) return;

        ConfigurationSection snapshots =
                history.getConfigurationSection("snapshots");
        if (snapshots == null) return;

        List<String> keys = new ArrayList<>(snapshots.getKeys(false));
        if (keys.size() <= maxSnapshots) return;

        keys.sort(Comparator.naturalOrder());
        int toDelete = keys.size() - maxSnapshots;
        boolean changed = false;

        for (int i = 0; i < toDelete; i++) {
            String key = keys.get(i);
            history.set("snapshots." + key, null);
            history.set("restored." + key, null);
            changed = true;
            plugin.getLogger().info("Cleaned up old backup: " + key);
        }

        if (changed) {
            try {
                history.save(historyFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to save after cleanup for " + uuid, e);
            }
        }
    }

    // ========== Import helpers ==========

    /**
     * List sub-folders in imports/ directory.
     */
    public List<String> listImportFolders() {
        List<String> folders = new ArrayList<>();
        File[] dirs = importsFolder.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File dir : dirs) {
                folders.add(dir.getName());
            }
        }
        return folders;
    }

    /**
     * List backups available in a specific import folder for a player UUID.
     * Auto-detects CM vs InvBackup format.
     */
    public List<BackupInfo> listImportBackups(String folderName, String uuid) {
        List<BackupInfo> backups = new ArrayList<>();
        File importDir = new File(importsFolder, folderName);
        File importFile = new File(importDir, uuid + ".yml");
        if (!importFile.exists()) return backups;

        YamlConfiguration config =
                YamlConfiguration.loadConfiguration(importFile);

        if (config.contains("snapshots")) {
            // InvBackup format
            ConfigurationSection snapshots =
                    config.getConfigurationSection("snapshots");
            if (snapshots != null) {
                for (String key : snapshots.getKeys(false)) {
                    ConfigurationSection sec =
                            snapshots.getConfigurationSection(key);
                    if (sec == null) continue;

                    BackupInfo info = new BackupInfo();
                    info.snapshotId = "import:" + folderName + ":" + key;
                    info.source = "import";
                    info.targetName = sec.getString("meta.target", uuid);
                    info.triggeredBy = sec.getString(
                            "meta.triggered-by", "Import");
                    info.triggeredByUuid = "";
                    info.triggerType = "import";
                    info.label = sec.getString("meta.label", "");
                    info.timestamp = sec.getLong("meta.timestamp",
                            importFile.lastModified());
                    info.backupLevel = sec.getString(
                            "meta.backup-level", "minimal");
                    backups.add(info);
                }
            }
        } else {
            // CM format: top-level keys with .content
            for (String key : config.getKeys(false)) {
                if (config.contains(key + ".content")) {
                    BackupInfo info = new BackupInfo();
                    info.snapshotId = "import:" + folderName + ":cm:" + key;
                    info.source = "import";
                    info.targetName = uuid;
                    info.triggeredBy = "CreativeManager";
                    info.triggeredByUuid = "";
                    info.triggerType = "import";
                    info.label = "CM [" + key + "]";
                    info.timestamp = importFile.lastModified();
                    info.backupLevel = "minimal";
                    backups.add(info);
                }
            }
        }

        return backups;
    }

    /**
     * Import a single player's data from an import folder into history.
     * Returns the number of snapshots imported.
     */
    public int importToHistory(String folderName, String uuid,
                               String triggeredByName, String triggeredByUuid) {
        return importToHistory(folderName, uuid, triggeredByName,
                triggeredByUuid, false, null);
    }

    /**
     * Import with optional --by-name matching.
     * When byName is true, scans import files by player-name / meta.target
     * and remaps to the provided uuid.
     */
    public int importToHistory(String folderName, String uuid,
                               String triggeredByName, String triggeredByUuid,
                               boolean byName, String playerName) {
        File importDir = new File(importsFolder, folderName);
        if (!importDir.exists() || !importDir.isDirectory()) return 0;

        File importFile;
        if (byName && playerName != null) {
            importFile = findImportFileByName(importDir, playerName);
        } else {
            importFile = new File(importDir, uuid + ".yml");
        }

        if (importFile == null || !importFile.exists()) return 0;

        YamlConfiguration importConfig =
                YamlConfiguration.loadConfiguration(importFile);

        File historyFile = new File(historyFolder, uuid + ".yml");
        YamlConfiguration history;
        if (historyFile.exists()) {
            history = YamlConfiguration.loadConfiguration(historyFile);
        } else {
            history = new YamlConfiguration();
        }

        int count = 0;
        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss")
                .format(new Date());

        if (importConfig.contains("snapshots")) {
            // InvBackup format
            ConfigurationSection snapshots =
                    importConfig.getConfigurationSection("snapshots");
            if (snapshots != null) {
                for (String key : snapshots.getKeys(false)) {
                    String newId = timestamp + "_import_" + count;
                    ConfigurationSection sec =
                            snapshots.getConfigurationSection(key);
                    if (sec == null) continue;

                    for (String k : sec.getKeys(true)) {
                        if (!sec.isConfigurationSection(k)) {
                            history.set("snapshots." + newId + "." + k,
                                    sec.get(k));
                        }
                    }
                    // Override meta
                    history.set("snapshots." + newId
                            + ".meta.trigger-type", "import");
                    history.set("snapshots." + newId
                            + ".meta.label", "Imported from " + folderName);
                    count++;
                }
            }
        } else {
            // CM format
            for (String gamemode : importConfig.getKeys(false)) {
                String content = importConfig.getString(gamemode + ".content");
                String armor = importConfig.getString(gamemode + ".armor");
                if (content == null) continue;

                String newId = timestamp + "_CM_" + gamemode;
                history.set("snapshots." + newId + ".meta.target",
                        playerName != null ? playerName : uuid);
                history.set("snapshots." + newId + ".meta.target-uuid", uuid);
                history.set("snapshots." + newId + ".meta.triggered-by",
                        triggeredByName);
                history.set("snapshots." + newId + ".meta.triggered-by-uuid",
                        triggeredByUuid);
                history.set("snapshots." + newId + ".meta.trigger-type",
                        "import");
                history.set("snapshots." + newId + ".meta.label",
                        "CM Import [" + gamemode + "]");
                history.set("snapshots." + newId + ".meta.timestamp",
                        System.currentTimeMillis());
                history.set("snapshots." + newId + ".meta.backup-level",
                        "minimal");
                history.set("snapshots." + newId + ".meta.source",
                        "CreativeManager");
                history.set("snapshots." + newId + ".inventory.content",
                        content);
                if (armor != null) {
                    history.set("snapshots." + newId + ".inventory.armor",
                            armor);
                }
                count++;
            }
        }

        if (count > 0) {
            history.set("player-uuid", uuid);
            if (playerName != null) {
                history.set("player-name", playerName);
            }
            try {
                historyFile.getParentFile().mkdirs();
                history.save(historyFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to save imported history for " + uuid, e);
                return 0;
            }
        }

        return count;
    }

    /**
     * Import all players from an import folder into history.
     */
    public int importAllFromFolder(String folderName,
                                   String triggeredByName,
                                   String triggeredByUuid) {
        File importDir = new File(importsFolder, folderName);
        if (!importDir.exists() || !importDir.isDirectory()) return 0;

        File[] files = importDir.listFiles(
                (d, name) -> name.endsWith(".yml"));
        if (files == null) return 0;

        int totalCount = 0;
        for (File file : files) {
            String fileName = file.getName().replace(".yml", "");
            if (!isUuid(fileName)) continue;

            int count = importToHistory(folderName, fileName,
                    triggeredByName, triggeredByUuid);
            if (count > 0) totalCount++;
        }
        return totalCount;
    }

    /**
     * Find an import file by player name (scans file contents).
     */
    private File findImportFileByName(File importDir, String playerName) {
        File[] files = importDir.listFiles(
                (d, name) -> name.endsWith(".yml"));
        if (files == null) return null;

        for (File file : files) {
            YamlConfiguration config =
                    YamlConfiguration.loadConfiguration(file);
            // Check InvBackup format
            String name = config.getString("player-name");
            if (playerName.equalsIgnoreCase(name)) return file;

            // Check snapshots meta
            ConfigurationSection snapshots =
                    config.getConfigurationSection("snapshots");
            if (snapshots != null) {
                for (String key : snapshots.getKeys(false)) {
                    String target = snapshots.getString(
                            key + ".meta.target");
                    if (playerName.equalsIgnoreCase(target)) return file;
                }
            }
        }
        return null;
    }

    // ========== Export ==========

    /**
     * Export a player's current backup to imports/ folder.
     */
    public boolean exportPlayer(String uuid, String folderName) {
        File currentFile = new File(currentFolder, uuid + ".yml");
        if (!currentFile.exists()) return false;

        File exportDir = new File(importsFolder, folderName);
        exportDir.mkdirs();
        File exportFile = new File(exportDir, uuid + ".yml");

        YamlConfiguration current =
                YamlConfiguration.loadConfiguration(currentFile);
        try {
            current.save(exportFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to export " + uuid, e);
            return false;
        }
    }

    /**
     * Export all players' current backups.
     */
    public int exportAll(String folderName) {
        File[] files = currentFolder.listFiles(
                (d, name) -> name.endsWith(".yml"));
        if (files == null) return 0;

        int count = 0;
        for (File file : files) {
            String uuid = file.getName().replace(".yml", "");
            if (isUuid(uuid) && exportPlayer(uuid, folderName)) {
                count++;
            }
        }
        return count;
    }

    // ========== UUID Migration ==========

    /**
     * Migrate all backup data from oldUuid to newUuid.
     */
    public boolean migrateUuid(String oldUuid, String newUuid) {
        boolean changed = false;

        // Migrate history
        File oldHistory = new File(historyFolder, oldUuid + ".yml");
        if (oldHistory.exists()) {
            File newHistory = new File(historyFolder, newUuid + ".yml");
            YamlConfiguration config =
                    YamlConfiguration.loadConfiguration(oldHistory);
            config.set("player-uuid", newUuid);
            updateMetaUuid(config, newUuid);
            try {
                config.save(newHistory);
                oldHistory.delete();
                changed = true;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to migrate history", e);
            }
        }

        // Migrate current
        File oldCurrent = new File(currentFolder, oldUuid + ".yml");
        if (oldCurrent.exists()) {
            File newCurrent = new File(currentFolder, newUuid + ".yml");
            YamlConfiguration config =
                    YamlConfiguration.loadConfiguration(oldCurrent);
            config.set("player-uuid", newUuid);
            updateMetaUuid(config, newUuid);
            try {
                config.save(newCurrent);
                oldCurrent.delete();
                changed = true;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to migrate current", e);
            }
        }

        // Migrate pending
        File oldPending = new File(pendingFolder, oldUuid + ".yml");
        if (oldPending.exists()) {
            File newPending = new File(pendingFolder, newUuid + ".yml");
            if (oldPending.renameTo(newPending)) {
                changed = true;
            }
        }

        return changed;
    }

    private void updateMetaUuid(YamlConfiguration config, String newUuid) {
        ConfigurationSection snapshots =
                config.getConfigurationSection("snapshots");
        if (snapshots == null) return;
        for (String key : snapshots.getKeys(false)) {
            ConfigurationSection sec =
                    snapshots.getConfigurationSection(key);
            if (sec != null && sec.contains("meta.target-uuid")) {
                sec.set("meta.target-uuid", newUuid);
            }
        }
    }

    // ========== Search ==========

    /**
     * Search all backups by player name.
     * Returns a list of matching UUIDs with player names.
     */
    public List<SearchResult> searchByName(String playerName) {
        List<SearchResult> results = new ArrayList<>();

        // Search history
        searchInFolder(historyFolder, playerName, results);
        // Search current
        searchInFolder(currentFolder, playerName, results);

        return results;
    }

    private void searchInFolder(File folder, String playerName,
                                List<SearchResult> results) {
        File[] files = folder.listFiles(
                (d, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String uuid = file.getName().replace(".yml", "");
            if (!isUuid(uuid)) continue;

            // Check if already in results
            boolean already = false;
            for (SearchResult r : results) {
                if (r.uuid.equals(uuid)) {
                    already = true;
                    break;
                }
            }
            if (already) continue;

            YamlConfiguration config =
                    YamlConfiguration.loadConfiguration(file);
            String name = config.getString("player-name");

            if (playerName.equalsIgnoreCase(name)) {
                SearchResult result = new SearchResult();
                result.uuid = uuid;
                result.playerName = name;
                result.folder = folder.getName();
                results.add(result);
                continue;
            }

            // Also search in snapshot meta
            ConfigurationSection snapshots =
                    config.getConfigurationSection("snapshots");
            if (snapshots != null) {
                for (String key : snapshots.getKeys(false)) {
                    String target = snapshots.getString(
                            key + ".meta.target");
                    if (playerName.equalsIgnoreCase(target)) {
                        SearchResult result = new SearchResult();
                        result.uuid = uuid;
                        result.playerName = target;
                        result.folder = folder.getName();
                        results.add(result);
                        break;
                    }
                }
            }
        }
    }

    // ========== Utility ==========

    public RestoredTracker getTracker(String uuid, String snapshotId) {
        File historyFile = new File(historyFolder, uuid + ".yml");
        return new RestoredTracker(historyFile, snapshotId, plugin.getLogger());
    }

    public Set<String> getAllPlayerUuids() {
        Set<String> uuids = new LinkedHashSet<>();

        // From history
        addUuidsFromFolder(historyFolder, uuids);
        // From current
        addUuidsFromFolder(currentFolder, uuids);

        return uuids;
    }

    private void addUuidsFromFolder(File folder, Set<String> uuids) {
        File[] files = folder.listFiles(
                (d, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File f : files) {
                String name = f.getName().replace(".yml", "");
                if (isUuid(name)) {
                    uuids.add(name);
                }
            }
        }
    }

    public File getCurrentFolder() {
        return currentFolder;
    }

    public File getHistoryFolder() {
        return historyFolder;
    }

    public File getImportsFolder() {
        return importsFolder;
    }

    public File getPendingFolder() {
        return pendingFolder;
    }

    private static boolean isUuid(String str) {
        try {
            UUID.fromString(str);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ========== Data classes ==========

    public static class BackupInfo {
        public String snapshotId;
        public String source; // "history", "import"
        public String targetName;
        public String triggeredBy;
        public String triggeredByUuid;
        public String triggerType;
        public String label;
        public long timestamp;
        public String backupLevel;
    }

    public static class SearchResult {
        public String uuid;
        public String playerName;
        public String folder;
    }
}
