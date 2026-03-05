package com.invbackup.request;

import com.invbackup.InvBackup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class RestoreRequestManager {

    private final InvBackup plugin;
    private final File pendingFolder;

    public RestoreRequestManager(InvBackup plugin) {
        this.plugin = plugin;
        this.pendingFolder = plugin.getBackupManager().getPendingFolder();
        pendingFolder.mkdirs();
    }

    /**
     * Create a restore request where the backup owner and the target player
     * are the same UUID (normal case).
     */
    public RestoreRequest createRequestForTarget(String targetUuid, String targetName,
                                                 String snapshotId, String requestedBy,
                                                 String requestedByUuid) {
        return createRequest(targetUuid, targetName,
                targetUuid, targetName, snapshotId, requestedBy, requestedByUuid);
    }

    /**
     * Create a restore request where the backup owned by (sourceUuid/sourceName)
     * will be restored by targetUuid/targetName (can differ for cross-player restore).
     */
    public RestoreRequest createRequest(String sourceUuid, String sourceName,
                                        String targetUuid, String targetName,
                                        String snapshotId, String requestedBy,
                                        String requestedByUuid) {
        RestoreRequest request = new RestoreRequest(
                sourceUuid, sourceName, targetUuid, targetName,
                snapshotId, requestedBy, requestedByUuid);

        saveRequest(targetUuid, request);
        return request;
    }

    public void notifyPlayer(Player player) {
        // Optionally migrate requests saved under a different UUID but targeting this name
        // (e.g. offline/online UUID mode switches).
        if (plugin.getConfig().getBoolean("restore-request.match-by-name", true)) {
            migrateRequestsForPlayer(player);
        }

        String uuid = player.getUniqueId().toString();
        cleanExpired(uuid);
        List<RestoreRequest> pending = getPendingRequests(uuid);

        int windowSeconds = plugin.getConfig()
                .getInt("restore-request.open-window-seconds", 0);
        String overflowMode = plugin.getConfig()
                .getString("restore-request.restore-all-overflow", "drop")
                .toLowerCase();
        boolean dropOverflow = "drop".equals(overflowMode);

        for (RestoreRequest req : pending) {
            if (!"pending".equals(req.status)) {
                continue;
            }

            Component msg = plugin.getMessage("request-received")
                    .replaceText(b -> b.matchLiteral("{admin}")
                            .replacement(req.requestedBy));

            Component accept = plugin.getLanguageManager()
                    .getGuiMessage("request-accept-button")
                    .clickEvent(ClickEvent.runCommand(
                            "/invbackup accept " + req.requestId))
                    .hoverEvent(HoverEvent.showText(
                            plugin.getLanguageManager()
                                    .getGuiMessage("request-accept-hover")));

            Component decline = plugin.getLanguageManager()
                    .getGuiMessage("request-decline-button")
                    .clickEvent(ClickEvent.runCommand(
                            "/invbackup decline " + req.requestId))
                    .hoverEvent(HoverEvent.showText(
                            plugin.getLanguageManager()
                                    .getGuiMessage("request-decline-hover")));

            Component full = msg;

            // Cross-player source hint (A's snapshot -> B restores)
            if (req.sourceUuid != null && req.targetUuid != null
                    && !req.sourceUuid.equals(req.targetUuid)) {
                String src = req.sourceName != null && !req.sourceName.isBlank()
                        ? req.sourceName
                        : req.sourceUuid;
                full = full.append(Component.newline())
                        .append(plugin.getMessage("request-source-tip")
                                .replaceText(b -> b.matchLiteral("{source}")
                                        .replacement(src)));
            }

            if (windowSeconds > 0) {
                full = full.append(Component.newline())
                        .append(plugin.getMessage("request-open-window-tip")
                                .replaceText(b -> b.matchLiteral("{seconds}")
                                        .replacement(String.valueOf(windowSeconds))));
            }
            full = full.append(Component.newline())
                    .append(plugin.getMessage(dropOverflow
                            ? "request-restore-all-drop-tip"
                            : "request-restore-all-keep-tip"));

            // Buttons on the same line after the final tip
            player.sendMessage(full
                    .append(Component.space())
                    .append(accept)
                    .append(Component.space())
                    .append(decline));
        }
    }

    /**
     * If a restore request was saved under a different pending/<uuid>.yml but the
     * request's target-name matches this player, migrate it to the correct file.
     */
    private void migrateRequestsForPlayer(Player player) {
        String currentUuid = player.getUniqueId().toString();
        String currentName = player.getName();
        if (currentName == null || currentName.isEmpty()) return;

        File currentFile = new File(pendingFolder, currentUuid + ".yml");

        File[] files = pendingFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            if (file.equals(currentFile)) continue;

            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection requests = cfg.getConfigurationSection("requests");
            if (requests == null) continue;

            boolean movedAny = false;
            for (String key : new ArrayList<>(requests.getKeys(false))) {
                ConfigurationSection sec = requests.getConfigurationSection(key);
                if (sec == null) continue;

                String targetName = sec.getString("target-name", "");
                String status = sec.getString("status", "pending");
                if (!"pending".equals(status)) continue;

                if (currentName.equalsIgnoreCase(targetName)) {
                    // Re-save to correct file under same request-id
                    RestoreRequest req = new RestoreRequest();
                    req.requestId = sec.getString("request-id", key);
                    req.sourceUuid = sec.getString("source-uuid",
                            sec.getString("target-uuid", currentUuid));
                    req.sourceName = sec.getString("source-name", targetName);
                    req.targetUuid = currentUuid;
                    req.targetName = currentName;
                    req.snapshotId = sec.getString("snapshot-id", "");
                    req.requestedBy = sec.getString("requested-by", "");
                    req.requestedByUuid = sec.getString("requested-by-uuid", "");
                    req.timestamp = sec.getLong("timestamp", 0);
                    req.status = status;
                    req.openExpiredAt = sec.getLong("open-expired-at", 0L);

                    saveRequest(currentUuid, req);

                    // Remove from old file
                    requests.set(key, null);
                    movedAny = true;
                }
            }

            if (movedAny) {
                try {
                    cfg.save(file);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to save migrated pending file " + file.getName(), e);
                }
            }
        }
    }

    public RestoreRequest findRequest(String targetUuid, String requestId) {
        List<RestoreRequest> requests = getAllRequests(targetUuid);
        for (RestoreRequest req : requests) {
            if (req.requestId.equals(requestId)) {
                return req;
            }
        }
        return null;
    }

    public RestoreRequest findRequestById(String requestId) {
        // Search all pending files
        File[] files = pendingFolder.listFiles(
                (dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return null;
        }

        for (File file : files) {
            String uuid = file.getName().replace(".yml", "");
            RestoreRequest req = findRequest(uuid, requestId);
            if (req != null) {
                return req;
            }
        }
        return null;
    }

    /**
     * Revoke a pending request. Only the requester (requestedByUuid) may revoke.
     *
     * @return the revoked request, or null if not revoked
     */
    public RestoreRequest revokeRequest(String requestId, String operatorUuid) {
        RestoreRequest req = findRequestById(requestId);
        if (req == null || !"pending".equals(req.status)) {
            return null;
        }
        if (!req.requestedByUuid.equals(operatorUuid)) {
            return null;
        }
        updateRequestStatus(req.targetUuid, requestId, "revoked");

        Player target = Bukkit.getPlayer(UUID.fromString(req.targetUuid));
        if (target != null && target.isOnline()) {
            target.sendMessage(plugin.getMessage("request-revoked-by-admin"));
        }

        return req;
    }

    public void updateRequestStatus(String targetUuid, String requestId,
                                    String status) {
        File file = new File(pendingFolder, targetUuid + ".yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection requests = config.getConfigurationSection("requests");
        if (requests == null) {
            return;
        }

        for (String key : requests.getKeys(false)) {
            ConfigurationSection sec = requests.getConfigurationSection(key);
            if (sec != null && requestId.equals(sec.getString("request-id"))) {
                sec.set("status", status);
                try {
                    config.save(file);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to update request status", e);
                }
                return;
            }
        }
    }

    public List<RestoreRequest> getPendingRequests(String targetUuid) {
        List<RestoreRequest> result = new ArrayList<>();
        for (RestoreRequest req : getAllRequests(targetUuid)) {
            if ("pending".equals(req.status)) {
                result.add(req);
            }
        }
        return result;
    }

    private List<RestoreRequest> getAllRequests(String targetUuid) {
        List<RestoreRequest> requests = new ArrayList<>();
        File file = new File(pendingFolder, targetUuid + ".yml");
        if (!file.exists()) {
            return requests;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("requests");
        if (section == null) {
            return requests;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection sec = section.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }

            RestoreRequest req = new RestoreRequest();
            req.requestId = sec.getString("request-id", "");
            req.sourceUuid = sec.getString("source-uuid",
                    sec.getString("target-uuid", targetUuid));
            req.sourceName = sec.getString("source-name", "");
            req.targetUuid = sec.getString("target-uuid", targetUuid);
            req.targetName = sec.getString("target-name", "");
            req.snapshotId = sec.getString("snapshot-id", "");
            req.requestedBy = sec.getString("requested-by", "");
            req.requestedByUuid = sec.getString("requested-by-uuid", "");
            req.timestamp = sec.getLong("timestamp", 0);
            req.status = sec.getString("status", "pending");
            req.openExpiredAt = sec.getLong("open-expired-at", 0L);

            requests.add(req);
        }

        return requests;
    }

    private void saveRequest(String targetUuid, RestoreRequest request) {
        File file = new File(pendingFolder, targetUuid + ".yml");
        YamlConfiguration config;
        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            config = new YamlConfiguration();
        }

        String path = "requests." + request.requestId;
        config.set(path + ".request-id", request.requestId);
        config.set(path + ".source-uuid", request.sourceUuid);
        config.set(path + ".source-name", request.sourceName);
        config.set(path + ".target-uuid", request.targetUuid);
        config.set(path + ".target-name", request.targetName);
        config.set(path + ".snapshot-id", request.snapshotId);
        config.set(path + ".requested-by", request.requestedBy);
        config.set(path + ".requested-by-uuid", request.requestedByUuid);
        config.set(path + ".timestamp", request.timestamp);
        config.set(path + ".status", request.status);
        config.set(path + ".open-expired-at", request.openExpiredAt);

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save restore request", e);
        }
    }

    public void cleanExpired(String targetUuid) {
        int expireDays = plugin.getConfig().getInt("restore-request.expire-days", 7);
        if (expireDays <= 0) {
            return;
        }

        long expireMs = TimeUnit.DAYS.toMillis(expireDays);
        long now = System.currentTimeMillis();

        File file = new File(pendingFolder, targetUuid + ".yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection requests = config.getConfigurationSection("requests");
        if (requests == null) {
            return;
        }

        boolean changed = false;
        for (String key : requests.getKeys(false)) {
            ConfigurationSection sec = requests.getConfigurationSection(key);
            if (sec == null) {
                continue;
            }
            String status = sec.getString("status");
            long createdAt = sec.getLong("timestamp", 0);
            long openExpiresAt = sec.getLong("open-expired-at", 0L);

            boolean timeExpired = now - createdAt > expireMs;
            boolean windowExpired = "accepted".equals(status)
                    && openExpiresAt > 0L && now > openExpiresAt;

            if ("pending".equals(status) && timeExpired) {
                sec.set("status", "expired");
                changed = true;
            } else if (windowExpired) {
                sec.set("status", "expired");
                changed = true;
            }
        }

        if (changed) {
            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING,
                        "Failed to clean expired requests", e);
            }
        }
    }

    /**
     * Mark a request as accepted and set its reopen window deadline.
     */
    public void markAccepted(String targetUuid, String requestId, long openExpiredAt) {
        File file = new File(pendingFolder, targetUuid + ".yml");
        if (!file.exists()) {
            return;
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection requests = config.getConfigurationSection("requests");
        if (requests == null) {
            return;
        }

        for (String key : requests.getKeys(false)) {
            ConfigurationSection sec = requests.getConfigurationSection(key);
            if (sec != null && requestId.equals(sec.getString("request-id"))) {
                sec.set("status", "accepted");
                sec.set("open-expired-at", openExpiredAt);
                try {
                    config.save(file);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Failed to update request status on accept", e);
                }
                return;
            }
        }
    }
}
