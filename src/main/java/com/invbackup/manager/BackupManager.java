package com.invbackup.manager;

import com.invbackup.InvBackup;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import com.google.common.collect.Multimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class BackupManager {

    private static final DateTimeFormatter SNAPSHOT_ID_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private final InvBackup plugin;
    private final File dataFolder;
    private final File currentFolder;
    private final File historyFolder;
    private final File importsFolder;
    private final File pendingFolder;
    private final Map<UUID, PendingAutoBackup> pendingAutoBackups = new HashMap<>();
    private final Map<UUID, Long> lastAutoBackupMillis = new HashMap<>();
    private final Map<String, Integer> integritySaveCounter = new HashMap<>();

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

    public void requestAutoBackup(Player target, String triggerType, String label) {
        if (target == null) {
            return;
        }

        String normalizedTrigger = triggerType == null
                ? "auto"
                : triggerType.trim().toLowerCase();
        String normalizedLabel = label == null ? "" : label;

        UUID playerId = target.getUniqueId();
        long now = System.currentTimeMillis();

        long windowMs = getCoalesceWindowMillis();
        if (windowMs <= 0 || !shouldCoalesceTrigger(normalizedTrigger)) {
            String id = saveBackup(target, "Server", "CONSOLE",
                    normalizedTrigger, normalizedLabel);
            if (id != null) {
                lastAutoBackupMillis.put(playerId, now);
            }
            return;
        }

        Long lastAt = lastAutoBackupMillis.get(playerId);
        if (lastAt == null || now - lastAt >= windowMs) {
            String id = saveBackup(target, "Server", "CONSOLE",
                    normalizedTrigger, normalizedLabel);
            if (id != null) {
                lastAutoBackupMillis.put(playerId, now);
            }
            return;
        }

        long dueAt = lastAt + windowMs;
        long delayMs = Math.max(1000L, dueAt - now);
        long delayTicks = Math.max(1L, (delayMs + 49L) / 50L);

        PendingAutoBackup previous = pendingAutoBackups.remove(playerId);
        if (previous != null) {
            Bukkit.getScheduler().cancelTask(previous.taskId);
        }

        PendingAutoBackup pending = new PendingAutoBackup();
        pending.triggerType = normalizedTrigger;
        pending.label = normalizedLabel;
        pending.dueAt = dueAt;
        pending.taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            PendingAutoBackup live = pendingAutoBackups.remove(playerId);
            if (live == null) {
                return;
            }
            Player online = Bukkit.getPlayer(playerId);
            if (online == null || !online.isOnline()) {
                return;
            }
            String id = saveBackup(online, "Server", "CONSOLE",
                    live.triggerType, live.label);
            if (id != null) {
                lastAutoBackupMillis.put(playerId, System.currentTimeMillis());
            }
        }, delayTicks);

        pendingAutoBackups.put(playerId, pending);
    }

    public String savePreRestoreSafetyBackup(Player target,
                                             String triggeredByName,
                                             String triggeredByUuid,
                                             String reason) {
        if (target == null) {
            return null;
        }
        if (!plugin.getConfig().getBoolean("backup-strategy.pre-restore.enabled", true)) {
            return "";
        }

        String label = plugin.getConfig()
                .getString("backup-strategy.pre-restore.label",
                        "Pre-restore safety backup");
        if (reason != null && !reason.isBlank()) {
            label = label + " [" + reason + "]";
        }

        return saveBackup(target, triggeredByName, triggeredByUuid,
                "pre-restore", label);
    }

    public boolean isPreRestoreRequireSuccess() {
        return plugin.getConfig().getBoolean("backup-strategy.pre-restore.require-success", false);
    }

    public boolean isPreRestoreNotifySuccess() {
        return plugin.getConfig().getBoolean("backup-strategy.pre-restore.notify-success", false);
    }

    private boolean shouldCoalesceTrigger(String triggerType) {
        if (!plugin.getConfig().getBoolean("backup-strategy.coalesce.enabled", true)) {
            return false;
        }
        List<String> applied = plugin.getConfig()
                .getStringList("backup-strategy.coalesce.apply-triggers");
        if (applied == null || applied.isEmpty()) {
            return false;
        }
        for (String t : applied) {
            if (t != null && triggerType.equalsIgnoreCase(t.trim())) {
                return true;
            }
        }
        return false;
    }

    private long getCoalesceWindowMillis() {
        int seconds = plugin.getConfig()
                .getInt("backup-strategy.coalesce.window-seconds", 45);
        return Math.max(0L, seconds) * 1000L;
    }

    public void shutdown() {
        for (PendingAutoBackup pending : pendingAutoBackups.values()) {
            Bukkit.getScheduler().cancelTask(pending.taskId);
        }
        pendingAutoBackups.clear();
        lastAutoBackupMillis.clear();
        integritySaveCounter.clear();
    }

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

        maybeRunIntegrityCheck(uuid, history, historyFile);

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
                PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                        NamespacedKey.minecraft(parts[0]));
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

    /**
     * Resolve a player UUID from web query input.
     * Accepts:
     * 1) exact UUID,
     * 2) exact name match (case-insensitive),
     * 3) unique partial name / UUID match.
     */
    public String resolvePlayerUuidForWeb(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }

        String trimmed = query.trim();
        try {
            return UUID.fromString(trimmed).toString();
        } catch (IllegalArgumentException ignored) {
        }

        String normalizedQuery = trimmed.toLowerCase(Locale.ROOT);
        String normalizedUuidLike = normalizedQuery.replace("-", "");

        String exactNameMatch = null;
        List<String> partialMatches = new ArrayList<>();

        for (String uuid : getAllPlayerUuids()) {
            String name = resolveStoredPlayerName(uuid);
            String safeName = name == null ? "" : name.trim();
            String nameLower = safeName.toLowerCase(Locale.ROOT);
            String uuidLower = uuid.toLowerCase(Locale.ROOT);
            String uuidNoDash = uuidLower.replace("-", "");

            if (!safeName.isBlank() && nameLower.equals(normalizedQuery)) {
                exactNameMatch = uuid;
                break;
            }

            boolean partialName = !safeName.isBlank() && nameLower.contains(normalizedQuery);
            boolean partialUuid = uuidLower.contains(normalizedQuery)
                    || (!normalizedUuidLike.isBlank()
                    && uuidNoDash.contains(normalizedUuidLike));
            if (partialName || partialUuid) {
                partialMatches.add(uuid);
            }
        }

        if (exactNameMatch != null) {
            return exactNameMatch;
        }
        return partialMatches.size() == 1 ? partialMatches.get(0) : null;
    }

    /**
     * Resolve a player's display name from stored backup data only (no Bukkit lookup).
     */
    public String resolveStoredPlayerName(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }

        String fromHistory = resolveStoredPlayerNameFromFile(
                new File(historyFolder, uuid + ".yml"), uuid);
        if (fromHistory != null && !fromHistory.isBlank()) {
            return fromHistory;
        }

        String fromCurrent = resolveStoredPlayerNameFromFile(
                new File(currentFolder, uuid + ".yml"), uuid);
        if (fromCurrent != null && !fromCurrent.isBlank()) {
            return fromCurrent;
        }

        return uuid;
    }

    /**
     * List player summaries for the embedded web UI.
     */
    public List<WebPlayerSummary> listWebPlayers(String query,
                                                 int limit,
                                                 boolean sortByLatest) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        String qUuid = q.replace("-", "");

        List<WebPlayerSummary> results = new ArrayList<>();
        for (String uuid : getAllPlayerUuids()) {
            List<BackupInfo> backups = listBackups(uuid, null);
            if (backups.isEmpty()) {
                continue;
            }

            long latestTimestamp = 0L;
            String latestSnapshotId = backups.get(0).snapshotId;
            for (BackupInfo info : backups) {
                if (info.timestamp >= latestTimestamp) {
                    latestTimestamp = info.timestamp;
                    latestSnapshotId = info.snapshotId;
                }
            }

            String name = resolveStoredPlayerName(uuid);
            if ((name == null || name.isBlank()) && !backups.isEmpty()) {
                name = backups.get(0).targetName;
            }
            if (name == null || name.isBlank()) {
                name = uuid;
            }

            if (!q.isBlank()) {
                String nameLower = name.toLowerCase(Locale.ROOT);
                String uuidLower = uuid.toLowerCase(Locale.ROOT);
                String uuidNoDash = uuidLower.replace("-", "");
                boolean matches = nameLower.contains(q)
                        || uuidLower.contains(q)
                        || (!qUuid.isBlank() && uuidNoDash.contains(qUuid));
                if (!matches) {
                    continue;
                }
            }

            WebPlayerSummary summary = new WebPlayerSummary();
            summary.uuid = uuid;
            summary.name = name;
            summary.snapshotCount = backups.size();
            summary.latestTimestamp = latestTimestamp;
            summary.latestSnapshotId = latestSnapshotId;
            results.add(summary);
        }

        if (sortByLatest) {
            results.sort(Comparator
                    .comparingLong((WebPlayerSummary p) -> p.latestTimestamp)
                    .reversed()
                    .thenComparing(p -> p.name == null ? "" : p.name,
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(p -> p.uuid));
        } else {
            results.sort(Comparator
                    .comparing((WebPlayerSummary p) -> p.name == null ? "" : p.name,
                            String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(p -> p.uuid));
        }

        if (limit > 0 && results.size() > limit) {
            return new ArrayList<>(results.subList(0, limit));
        }
        return results;
    }

    /**
     * List snapshot summaries for one player for the embedded web UI.
     */
    public List<WebSnapshotSummary> listWebSnapshots(String uuid, int limit) {
        if (uuid == null || uuid.isBlank()) {
            return List.of();
        }

        List<BackupInfo> backups = listBackups(uuid, null);
        backups.sort(Comparator
                .comparingLong((BackupInfo b) -> b.timestamp)
                .reversed()
                .thenComparing((BackupInfo b) -> b.snapshotId, Comparator.reverseOrder()));

        List<WebSnapshotSummary> result = new ArrayList<>();
        for (BackupInfo info : backups) {
            WebSnapshotSummary s = new WebSnapshotSummary();
            s.snapshotId = info.snapshotId;
            s.source = info.source;
            s.targetName = info.targetName;
            s.triggeredBy = info.triggeredBy;
            s.triggerType = info.triggerType;
            s.label = info.label;
            s.timestamp = info.timestamp;
            s.backupLevel = info.backupLevel;
            result.add(s);
        }

        if (limit > 0 && result.size() > limit) {
            return new ArrayList<>(result.subList(0, limit));
        }
        return result;
    }

    /**
     * Build web JSON payload (player + snapshot) for a specific snapshot.
     */
    public Map<String, Object> getWebSnapshotPayload(String uuid, String snapshotId) {
        if (uuid == null || uuid.isBlank()
                || snapshotId == null || snapshotId.isBlank()) {
            return null;
        }

        YamlConfiguration config = loadBackupConfig(uuid, snapshotId);
        if (config == null) {
            return null;
        }

        String playerName = resolveStoredPlayerName(uuid);
        if (playerName == null || playerName.isBlank()) {
            playerName = config.getString("meta.target", uuid);
        }

        Map<String, Object> player = new LinkedHashMap<>();
        player.put("uuid", uuid);
        player.put("name", playerName);

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("player", player);
        root.put("snapshot",
                buildSnapshotJsonFromFlatConfig(config, snapshotId, uuid, playerName));
        return root;
    }

    private String resolveStoredPlayerNameFromFile(File dataFile, String uuid) {
        if (!dataFile.exists() || !dataFile.isFile()) {
            return null;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        String name = config.getString("player-name");
        if (name != null && !name.isBlank()) {
            return name;
        }

        ConfigurationSection snapshots = config.getConfigurationSection("snapshots");
        if (snapshots == null) {
            return null;
        }

        List<String> keys = new ArrayList<>(snapshots.getKeys(false));
        keys.sort(Comparator.reverseOrder());
        for (String key : keys) {
            ConfigurationSection sec = snapshots.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            String target = sec.getString("meta.target");
            if (target != null && !target.isBlank()) {
                return target;
            }
        }
        return uuid;
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
        if (snapshotId.startsWith("importfile:")) {
            return loadImportFileConfig(uuid, snapshotId);
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

    private YamlConfiguration loadImportFileConfig(String uuid, String snapshotId) {
        // Format: "importfile:<fileName>.yml:<snapshotId>" or "importfile:<fileName>.yml:cm:<gamemode>"
        String[] parts = snapshotId.split(":", 4);
        if (parts.length < 3) return null;

        String fileName = parts[1];
        File importFile = new File(importsFolder, fileName);
        if (!importFile.exists()) return null;

        YamlConfiguration importConfig = YamlConfiguration.loadConfiguration(importFile);

        // CM format: "importfile:<fileName>.yml:cm:<gamemode>"
        if (parts.length == 4 && "cm".equals(parts[2])) {
            String gamemode = parts[3];
            return convertCmToConfig(uuid, importConfig, gamemode);
        }

        // InvBackup format: "importfile:<fileName>.yml:<snapshotId>"
        String actualId = parts[2];
        ConfigurationSection sec = importConfig.getConfigurationSection("snapshots." + actualId);
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
        List<SnapshotMeta> all = collectSnapshots(history);
        if (all.isEmpty()) {
            return;
        }

        boolean changed = false;

        if (plugin.getConfig().getBoolean("backup-strategy.retention.enabled", true)) {
            changed = applyTieredRetention(history, all) || changed;
            all = collectSnapshots(history);
        }

        int maxSnapshots = plugin.getConfig().getInt("max-snapshots", 20);
        boolean enforceMax = plugin.getConfig()
                .getBoolean("backup-strategy.retention.enforce-max-snapshots", true);
        if (!enforceMax || maxSnapshots <= 0 || all.size() <= maxSnapshots) {
            if (changed) {
                saveHistoryQuietly(uuid, history, historyFile);
            }
            return;
        }

        all.sort(Comparator.comparingLong(a -> a.timestamp));
        int toDelete = all.size() - maxSnapshots;
        boolean changedByMax = false;

        for (int i = 0; i < toDelete; i++) {
            SnapshotMeta snap = all.get(i);
            history.set("snapshots." + snap.snapshotId, null);
            history.set("restored." + snap.snapshotId, null);
            changedByMax = true;
            plugin.getLogger().info("Cleaned up old backup: " + snap.snapshotId);
        }

        if (changedByMax || changed) {
            saveHistoryQuietly(uuid, history, historyFile);
        }
    }

    private List<SnapshotMeta> collectSnapshots(YamlConfiguration history) {
        ConfigurationSection snapshots = history.getConfigurationSection("snapshots");
        if (snapshots == null) {
            return Collections.emptyList();
        }

        List<SnapshotMeta> all = new ArrayList<>();
        for (String id : snapshots.getKeys(false)) {
            long timestamp = history.getLong("snapshots." + id + ".meta.timestamp", 0L);
            if (timestamp <= 0) {
                timestamp = parseSnapshotIdTimestamp(id);
            }
            all.add(new SnapshotMeta(id, timestamp));
        }
        return all;
    }

    private boolean applyTieredRetention(YamlConfiguration history, List<SnapshotMeta> snapshots) {
        List<RetentionTier> tiers = loadRetentionTiers();
        if (tiers.isEmpty()) {
            return false;
        }

        snapshots.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        long now = System.currentTimeMillis();
        Map<Integer, Long> lastKeptByTier = new HashMap<>();
        boolean changed = false;

        for (SnapshotMeta snap : snapshots) {
            long ageMs = Math.max(0L, now - snap.timestamp);
            int tierIndex = resolveTierIndex(ageMs, tiers);
            RetentionTier tier = tiers.get(tierIndex);

            Long lastKept = lastKeptByTier.get(tierIndex);
            boolean keep = lastKept == null || (lastKept - snap.timestamp) >= tier.minGapMs;
            if (keep) {
                lastKeptByTier.put(tierIndex, snap.timestamp);
                continue;
            }

            history.set("snapshots." + snap.snapshotId, null);
            history.set("restored." + snap.snapshotId, null);
            changed = true;
            plugin.getLogger().info("Tiered retention removed backup: " + snap.snapshotId);
        }

        return changed;
    }

    private List<RetentionTier> loadRetentionTiers() {
        ConfigurationSection tiersSec = plugin.getConfig()
                .getConfigurationSection("backup-strategy.retention.tiers");
        if (tiersSec == null) {
            return Collections.emptyList();
        }

        List<RetentionTier> tiers = new ArrayList<>();
        for (String key : tiersSec.getKeys(false)) {
            ConfigurationSection sec = tiersSec.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }

            long maxAgeMs = readDurationMs(sec,
                    "max-age-millis",
                    "max-age-seconds",
                    "max-age-minutes",
                    "max-age-hours",
                    "max-age-days");
            if (maxAgeMs <= 0L) {
                maxAgeMs = Long.MAX_VALUE;
            }

            long minGapMs = readDurationMs(sec,
                    "min-gap-millis",
                    "min-gap-seconds",
                    "min-gap-minutes",
                    "min-gap-hours",
                    "min-gap-days");
            if (minGapMs < 0L) {
                minGapMs = 0L;
            }

            tiers.add(new RetentionTier(key, maxAgeMs, minGapMs));
        }

        tiers.sort(Comparator.comparingLong(t -> t.maxAgeMs));
        return tiers;
    }

    private int resolveTierIndex(long ageMs, List<RetentionTier> tiers) {
        for (int i = 0; i < tiers.size(); i++) {
            if (ageMs <= tiers.get(i).maxAgeMs) {
                return i;
            }
        }
        return tiers.size() - 1;
    }

    private long readDurationMs(ConfigurationSection sec, String... keys) {
        for (String key : keys) {
            if (!sec.contains(key)) {
                continue;
            }
            long v = sec.getLong(key, 0L);
            if (v <= 0L) {
                continue;
            }
            if (key.endsWith("-millis")) return v;
            if (key.endsWith("-seconds")) return v * 1000L;
            if (key.endsWith("-minutes")) return v * 60_000L;
            if (key.endsWith("-hours")) return v * 3_600_000L;
            if (key.endsWith("-days")) return v * 86_400_000L;
        }
        return 0L;
    }

    private void saveHistoryQuietly(String uuid, YamlConfiguration history, File historyFile) {
        try {
            history.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save after cleanup for " + uuid, e);
        }
    }

    private long parseSnapshotIdTimestamp(String snapshotId) {
        if (snapshotId == null || snapshotId.length() < 19) {
            return 0L;
        }
        String base = snapshotId.substring(0, 19);
        try {
            LocalDateTime local = LocalDateTime.parse(base, SNAPSHOT_ID_TIME_FORMAT);
            return local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ignored) {
            return 0L;
        }
    }

    // ========== Integrity check ==========

    private void maybeRunIntegrityCheck(String uuid, YamlConfiguration history, File historyFile) {
        if (!plugin.getConfig().getBoolean("backup-strategy.integrity-check.enabled", true)) {
            return;
        }

        int every = Math.max(1, plugin.getConfig()
                .getInt("backup-strategy.integrity-check.every-n-saves", 10));
        int count = integritySaveCounter.getOrDefault(uuid, 0) + 1;
        integritySaveCounter.put(uuid, count);
        if (count < every) {
            return;
        }
        integritySaveCounter.put(uuid, 0);

        boolean removeCorrupt = plugin.getConfig()
                .getBoolean("backup-strategy.integrity-check.remove-corrupt", false);
        ConfigurationSection snapshots = history.getConfigurationSection("snapshots");
        if (snapshots == null) {
            return;
        }

        boolean changed = false;
        int corruptCount = 0;
        for (String snapshotId : new ArrayList<>(snapshots.getKeys(false))) {
            ConfigurationSection sec = snapshots.getConfigurationSection(snapshotId);
            String error = validateSnapshot(sec);
            if (error == null) {
                history.set("snapshots." + snapshotId + ".meta.integrity-status", "ok");
                history.set("snapshots." + snapshotId + ".meta.integrity-error", null);
                history.set("snapshots." + snapshotId + ".meta.integrity-checked-at",
                        System.currentTimeMillis());
                continue;
            }

            corruptCount++;
            plugin.getLogger().warning("Integrity check found corrupted snapshot "
                    + snapshotId + " for " + uuid + ": " + error);

            if (removeCorrupt) {
                history.set("snapshots." + snapshotId, null);
                history.set("restored." + snapshotId, null);
            } else {
                history.set("snapshots." + snapshotId + ".meta.integrity-status", "corrupt");
                history.set("snapshots." + snapshotId + ".meta.integrity-error", error);
                history.set("snapshots." + snapshotId + ".meta.integrity-checked-at",
                        System.currentTimeMillis());
            }
            changed = true;
        }

        if (changed) {
            saveHistoryQuietly(uuid, history, historyFile);
        }

        if (corruptCount > 0) {
            plugin.getLogger().warning("Integrity check completed for " + uuid
                    + ": corrupted snapshots=" + corruptCount
                    + (removeCorrupt ? " (removed)." : " (marked)."));
        }
    }

    private String validateSnapshot(ConfigurationSection snapshot) {
        if (snapshot == null) {
            return "missing snapshot section";
        }
        if (!snapshot.isConfigurationSection("meta")) {
            return "missing meta section";
        }

        String content = snapshot.getString("inventory.content", null);
        if (content == null || content.isBlank()) {
            return "missing inventory.content";
        }

        try {
            SerializationUtil.itemStackArrayFromBase64(content);

            if (snapshot.contains("inventory.armor")) {
                SerializationUtil.itemStackArrayFromBase64(
                        snapshot.getString("inventory.armor", ""));
            }
            if (snapshot.contains("inventory.offhand")) {
                SerializationUtil.itemStackArrayFromBase64(
                        snapshot.getString("inventory.offhand", ""));
            }
            if (snapshot.contains("inventory.enderchest")) {
                SerializationUtil.itemStackArrayFromBase64(
                        snapshot.getString("inventory.enderchest", ""));
            }
        } catch (Exception ex) {
            return "invalid serialized inventory data: " + ex.getClass().getSimpleName();
        }

        return null;
    }

    // ========== Import helpers ==========

    public enum ImportSourceType {
        FILE, FOLDER
    }

    public static class ImportSource {
        public ImportSourceType type; // FILE or FOLDER
        public String name; // FILE: "<name>.yml" ; FOLDER: "<folder>"

        public ImportSource(ImportSourceType type, String name) {
            this.type = type;
            this.name = name;
        }

        public String displayName() {
            return switch (type) {
                case FILE -> "file:" + name;
                case FOLDER -> "folder:" + name;
            };
        }
    }

    public static class ImportEntry {
        public ImportSource source;
        public String targetUuid;
        public String targetName;
        public String snapshotId; // "import:<folder>:..." or "importfile:<file>.yml:..."
        public String format; // "InvBackup" / "CreativeManager"
        public long timestamp;

        /** Short, user-friendly label for display in GUIs (e.g. timestamp or "CM [SURVIVAL]"). */
        public String displaySnapshot;

        public String key() {
            return targetUuid + "|" + snapshotId;
        }
    }

    // ========== Web JSON export helpers ==========

    /**
     * Export a single InvBackup/CM-compatible YAML file to a web-friendly JSON format.
     *
     * @param sourceFile Input YAML (history/current/import-style/CM)
     * @param targetFile Output JSON file (will be overwritten)
     * @return true on success
     */
    public boolean exportYamlFileToWebJson(File sourceFile, File targetFile) {
        YamlConfiguration doc = YamlConfiguration.loadConfiguration(sourceFile);

        // Determine player UUID and name
        String playerUuid = doc.getString("player-uuid");
        String fileBase = sourceFile.getName().replace(".yml", "").replace(".yaml", "");
        if (playerUuid == null || playerUuid.isEmpty()) {
            // Try snapshots.<id>.meta.target-uuid
            ConfigurationSection snaps = doc.getConfigurationSection("snapshots");
            if (snaps != null) {
                for (String key : snaps.getKeys(false)) {
                    String u = snaps.getString(key + ".meta.target-uuid");
                    if (u != null && !u.isEmpty()) {
                        playerUuid = u;
                        break;
                    }
                }
            }
        }
        if (playerUuid == null || playerUuid.isEmpty()) {
            playerUuid = fileBase;
        }

        String playerName = doc.getString("player-name");
        if (playerName == null || playerName.isEmpty()) {
            // Try first snapshot meta.target
            ConfigurationSection snaps = doc.getConfigurationSection("snapshots");
            if (snaps != null) {
                for (String key : snaps.getKeys(false)) {
                    String n = snaps.getString(key + ".meta.target");
                    if (n != null && !n.isEmpty()) {
                        playerName = n;
                        break;
                    }
                }
            }
        }
        if (playerName == null || playerName.isEmpty()) {
            playerName = plugin.getIdentityManager().resolveName(playerUuid);
            if (playerName == null || playerName.isEmpty()) {
                playerName = playerUuid.substring(0, Math.min(8, playerUuid.length()));
            }
        }

        // Build snapshots list
        List<Map<String, Object>> snapshots = new ArrayList<>();

        if (doc.contains("snapshots")) {
            ConfigurationSection snaps = doc.getConfigurationSection("snapshots");
            if (snaps != null) {
                for (String snapId : snaps.getKeys(false)) {
                    ConfigurationSection sec = snaps.getConfigurationSection(snapId);
                    if (sec == null) continue;
                    snapshots.add(buildSnapshotJsonFromSection(sec, snapId, playerUuid, playerName));
                }
            }
        } else {
            // CM-style: top-level <gamemode>.content
            for (String key : doc.getKeys(false)) {
                if (doc.contains(key + ".content")) {
                    YamlConfiguration cmConfig = doc;
                    YamlConfiguration config = convertCmToConfig(playerUuid, cmConfig, key);
                    if (config == null) continue;
                    snapshots.add(buildSnapshotJsonFromFlatConfig(config, "CM_" + key, playerUuid, playerName));
                }
            }
        }

        Map<String, Object> root = new HashMap<>();
        Map<String, Object> player = new HashMap<>();
        player.put("uuid", playerUuid);
        player.put("name", playerName);
        root.put("player", player);
        root.put("snapshots", snapshots);

        try {
            String json = toJson(root);
            Path out = targetFile.toPath();
            if (out.getParent() != null) {
                Files.createDirectories(out.getParent());
            }
            Files.write(out, json.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE,
                    "Failed to export web JSON for " + sourceFile.getName(), e);
            return false;
        }
    }

    private Map<String, Object> buildSnapshotJsonFromSection(ConfigurationSection sec,
                                                             String snapId,
                                                             String playerUuid,
                                                             String playerName) {
        YamlConfiguration flat = sectionToConfig(sec);
        return buildSnapshotJsonFromFlatConfig(flat, snapId, playerUuid, playerName);
    }

    private Map<String, Object> buildSnapshotJsonFromFlatConfig(YamlConfiguration flat,
                                                                String snapId,
                                                                String playerUuid,
                                                                String playerName) {
        Map<String, Object> node = new HashMap<>();
        node.put("id", snapId);

        Map<String, Object> meta = new HashMap<>();
        ConfigurationSection metaSec = flat.getConfigurationSection("meta");
        if (metaSec != null) {
            for (String k : metaSec.getKeys(false)) {
                meta.put(k, metaSec.get(k));
            }
        }
        // Ensure some basics
        meta.putIfAbsent("target", playerName);
        meta.putIfAbsent("target-uuid", playerUuid);
        node.put("meta", meta);

        Map<String, Object> invNode = new HashMap<>();
        // content
        String contentStr = flat.getString("inventory.content");
        if (contentStr != null && !contentStr.isEmpty()) {
            try {
                ItemStack[] items = SerializationUtil.itemStackArrayFromBase64(contentStr);
                List<Map<String, Object>> list = new ArrayList<>();
                for (int i = 0; i < items.length; i++) {
                    ItemStack it = items[i];
                    if (it == null || it.getType() == Material.AIR) continue;
                    list.add(itemToJson(it, i));
                }
                invNode.put("content", list);
            } catch (IOException ignored) {
            }
        }
        // armor
        String armorStr = flat.getString("inventory.armor");
        if (armorStr != null && !armorStr.isEmpty()) {
            try {
                ItemStack[] items = SerializationUtil.itemStackArrayFromBase64(armorStr);
                List<Map<String, Object>> list = new ArrayList<>();
                for (int i = 0; i < items.length; i++) {
                    ItemStack it = items[i];
                    if (it == null || it.getType() == Material.AIR) continue;
                    list.add(itemToJson(it, i));
                }
                invNode.put("armor", list);
            } catch (IOException ignored) {
            }
        }
        // offhand
        String offStr = flat.getString("inventory.offhand");
        if (offStr != null && !offStr.isEmpty()) {
            try {
                ItemStack[] items = SerializationUtil.itemStackArrayFromBase64(offStr);
                if (items.length > 0 && items[0] != null && items[0].getType() != Material.AIR) {
                    invNode.put("offhand", itemToJson(items[0], 0));
                }
            } catch (IOException ignored) {
            }
        }
        // ender chest
        String ecStr = flat.getString("inventory.enderchest");
        if (ecStr != null && !ecStr.isEmpty()) {
            try {
                ItemStack[] items = SerializationUtil.itemStackArrayFromBase64(ecStr);
                List<Map<String, Object>> list = new ArrayList<>();
                for (int i = 0; i < items.length; i++) {
                    ItemStack it = items[i];
                    if (it == null || it.getType() == Material.AIR) continue;
                    list.add(itemToJson(it, i));
                }
                invNode.put("enderchest", list);
            } catch (IOException ignored) {
            }
        }
        node.put("inventory", invNode);

        // status: copy flat status.* into a nested object
        Map<String, Object> statusNode = new HashMap<>();
        ConfigurationSection statusSec = flat.getConfigurationSection("status");
        if (statusSec != null) {
            for (String k : statusSec.getKeys(true)) {
                if (!statusSec.isConfigurationSection(k)) {
                    statusNode.put(k, statusSec.get(k));
                }
            }
        }
        node.put("status", statusNode);

        return node;
    }

    private Map<String, Object> itemToJson(ItemStack it, int slot) {
        Map<String, Object> obj = new HashMap<>();
        obj.put("slot", slot);
        NamespacedKey key = it.getType().getKey();
        obj.put("id", key.toString());
        obj.put("amount", it.getAmount());
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName() && meta.displayName() != null) {
                obj.put("displayName", LegacyComponentSerializer.legacySection().serialize(meta.displayName()));
            }
            if (meta.hasLore() && meta.lore() != null) {
                List<String> loreLines = new ArrayList<>();
                for (Component c : meta.lore()) {
                    loreLines.add(LegacyComponentSerializer.legacySection().serialize(c));
                }
                obj.put("lore", loreLines);
            }
            if (meta.hasEnchants()) {
                Map<String, Integer> ench = new HashMap<>();
                meta.getEnchants().forEach((enchantment, level) -> {
                    ench.put(enchantment.getKey().toString(), level);
                });
                obj.put("enchantments", ench);
            }
            if (meta.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
                    || !meta.getItemFlags().isEmpty()) {
                List<String> flags = new ArrayList<>();
                meta.getItemFlags().forEach(flag -> flags.add(flag.name()));
                obj.put("itemFlags", flags);
            }
            if (meta.hasCustomModelData()) {
                obj.put("customModelData", meta.getCustomModelData());
            }

            Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
            if (modifiers != null && !modifiers.isEmpty()) {
                List<Map<String, Object>> list = new ArrayList<>();
                for (Map.Entry<Attribute, AttributeModifier> entry : modifiers.entries()) {
                    Attribute attribute = entry.getKey();
                    AttributeModifier modifier = entry.getValue();
                    if (attribute == null || modifier == null) {
                        continue;
                    }
                    Map<String, Object> node = new LinkedHashMap<>();
                    node.put("attribute", attribute.getKey().toString());
                    node.put("amount", modifier.getAmount());
                    node.put("operation", modifier.getOperation().name());
                    node.put("name", modifier.getName());

                    String slotInfo = resolveModifierSlot(modifier);
                    if (slotInfo != null && !slotInfo.isBlank()) {
                        node.put("slot", slotInfo);
                    }
                    list.add(node);
                }
                if (!list.isEmpty()) {
                    obj.put("attributeModifiers", list);
                }
            }
        }
        return obj;
    }

    private String resolveModifierSlot(AttributeModifier modifier) {
        try {
            EquipmentSlotGroup group = modifier.getSlotGroup();
            if (group != null) {
                return group.toString();
            }
        } catch (Throwable ignored) {
        }
        try {
            EquipmentSlot slot = modifier.getSlot();
            return slot != null ? slot.name() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Very small JSON writer for the limited structures we build here. */
    private String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof String s) {
            return "\"" + escapeJson(s) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!(e.getKey() instanceof String)) continue;
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(escapeJson((String) e.getKey())).append("\":");
                sb.append(toJson(e.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (value instanceof Iterable<?> it) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object v : it) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(v));
            }
            sb.append("]");
            return sb.toString();
        }
        // Fallback to string
        return "\"" + escapeJson(String.valueOf(value)) + "\"";
    }

    private String escapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    public List<ImportEntry> collectImportEntries(ImportSource source) {
        if (source == null) return List.of();
        return switch (source.type) {
            case FILE -> collectImportEntriesFromFile(source);
            case FOLDER -> collectImportEntriesFromFolder(source);
        };
    }

    private List<ImportEntry> collectImportEntriesFromFolder(ImportSource source) {
        File importDir = new File(importsFolder, source.name);
        if (!importDir.exists() || !importDir.isDirectory()) return List.of();

        File[] files = importDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null) return List.of();

        List<ImportEntry> result = new ArrayList<>();
        for (File f : files) {
            String uuid = f.getName().replace(".yml", "");
            if (!isUuid(uuid)) continue;
            result.addAll(collectEntriesForPlayerFile(source, uuid, f));
        }
        return result;
    }

    private List<ImportEntry> collectImportEntriesFromFile(ImportSource source) {
        File importFile = new File(importsFolder, source.name);
        if (!importFile.exists() || !importFile.isFile()) return List.of();

        String base = source.name.endsWith(".yml")
                ? source.name.substring(0, source.name.length() - 4)
                : source.name;

        if (!isUuid(base)) {
            // Only support "<uuid>.yml" single-player files for now
            return List.of();
        }

        return collectEntriesForPlayerFile(source, base, importFile);
    }

    private List<ImportEntry> collectEntriesForPlayerFile(ImportSource source,
                                                          String uuid,
                                                          File importFile) {
        List<ImportEntry> result = new ArrayList<>();
        YamlConfiguration config = YamlConfiguration.loadConfiguration(importFile);
        String fallbackName = plugin.getIdentityManager().resolveName(uuid);

        if (config.contains("snapshots")) {
            ConfigurationSection snapshots = config.getConfigurationSection("snapshots");
            if (snapshots != null) {
                for (String key : snapshots.getKeys(false)) {
                    ConfigurationSection sec = snapshots.getConfigurationSection(key);
                    if (sec == null) continue;

                    ImportEntry e = new ImportEntry();
                    e.source = source;
                    e.targetUuid = uuid;
                    e.targetName = sec.getString("meta.target",
                            fallbackName != null ? fallbackName : uuid);
                    String keyId = key;
                    e.snapshotId = (source.type == ImportSourceType.FOLDER)
                            ? "import:" + source.name + ":" + key
                            : "importfile:" + source.name + ":" + key;
                    e.displaySnapshot = keyId;
                    e.format = "InvBackup";
                    e.timestamp = sec.getLong("meta.timestamp", importFile.lastModified());
                    result.add(e);
                }
            }
        } else {
            for (String gm : config.getKeys(false)) {
                if (config.contains(gm + ".content")) {
                    ImportEntry e = new ImportEntry();
                    e.source = source;
                    e.targetUuid = uuid;
                    e.targetName = fallbackName != null ? fallbackName : uuid;
                    e.snapshotId = (source.type == ImportSourceType.FOLDER)
                            ? "import:" + source.name + ":cm:" + gm
                            : "importfile:" + source.name + ":cm:" + gm;
                    e.displaySnapshot = "CM [" + gm + "]";
                    e.format = "CreativeManager";
                    e.timestamp = importFile.lastModified();
                    result.add(e);
                }
            }
        }

        return result;
    }

    /**
     * Import selected entries into history. Returns number of snapshots imported.
     */
    public int importEntriesToHistory(ImportSource source,
                                      List<ImportEntry> entries,
                                      String triggeredByName,
                                      String triggeredByUuid) {
        if (source == null || entries == null || entries.isEmpty()) return 0;
        int count = 0;

        Map<String, List<ImportEntry>> byUuid = new HashMap<>();
        for (ImportEntry e : entries) {
            byUuid.computeIfAbsent(e.targetUuid, k -> new ArrayList<>()).add(e);
        }

        for (Map.Entry<String, List<ImportEntry>> kv : byUuid.entrySet()) {
            String uuid = kv.getKey();
            for (ImportEntry e : kv.getValue()) {
                YamlConfiguration snap = loadBackupConfig(uuid, e.snapshotId);
                if (snap == null) continue;
                if (appendSnapshotToHistory(uuid, snap, triggeredByName, triggeredByUuid, source.displayName())) {
                    count++;
                }
            }
        }

        return count;
    }

    private boolean appendSnapshotToHistory(String uuid,
                                           YamlConfiguration snap,
                                           String triggeredByName,
                                           String triggeredByUuid,
                                           String labelSource) {
        File historyFile = new File(historyFolder, uuid + ".yml");
        YamlConfiguration history = historyFile.exists()
                ? YamlConfiguration.loadConfiguration(historyFile)
                : new YamlConfiguration();

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        String snapshotId = timestamp;
        int counter = 1;
        while (history.contains("snapshots." + snapshotId)) {
            snapshotId = timestamp + "_" + counter;
            counter++;
        }

        ConfigurationSection root = snap.getRoot();
        if (root != null) {
            for (String k : root.getKeys(false)) {
                history.set("snapshots." + snapshotId + "." + k, root.get(k));
            }
        }

        history.set("snapshots." + snapshotId + ".meta.trigger-type", "import");
        history.set("snapshots." + snapshotId + ".meta.triggered-by", triggeredByName);
        history.set("snapshots." + snapshotId + ".meta.triggered-by-uuid", triggeredByUuid);
        history.set("snapshots." + snapshotId + ".meta.label", "Imported from " + labelSource);

        history.set("player-uuid", uuid);
        if (snap.contains("meta.target")) {
            history.set("player-name", snap.getString("meta.target"));
        }

        try {
            historyFile.getParentFile().mkdirs();
            history.save(historyFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to append imported snapshot", e);
            return false;
        }
    }

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
     * List .yml files directly under imports/ directory.
     * Used for "file:<name>.yml" import sources.
     */
    public List<String> listImportFiles() {
        List<String> files = new ArrayList<>();
        File[] ymls = importsFolder.listFiles((d, name) -> name.endsWith(".yml"));
        if (ymls != null) {
            for (File f : ymls) {
                if (f.isFile()) {
                    files.add(f.getName());
                }
            }
        }
        return files;
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

    private static class PendingAutoBackup {
        int taskId;
        long dueAt;
        String triggerType;
        String label;
    }

    private static class SnapshotMeta {
        final String snapshotId;
        final long timestamp;

        SnapshotMeta(String snapshotId, long timestamp) {
            this.snapshotId = snapshotId;
            this.timestamp = timestamp;
        }
    }

    private static class RetentionTier {
        final String key;
        final long maxAgeMs;
        final long minGapMs;

        RetentionTier(String key, long maxAgeMs, long minGapMs) {
            this.key = key;
            this.maxAgeMs = maxAgeMs;
            this.minGapMs = minGapMs;
        }
    }

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

    public static class WebPlayerSummary {
        public String uuid;
        public String name;
        public int snapshotCount;
        public long latestTimestamp;
        public String latestSnapshotId;
    }

    public static class WebSnapshotSummary {
        public String snapshotId;
        public String source;
        public String targetName;
        public String triggeredBy;
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
