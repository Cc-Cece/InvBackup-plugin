package com.invbackup.request;

import com.invbackup.InvBackup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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

    public RestoreRequest createRequest(String targetUuid, String targetName,
                                        String snapshotId, String requestedBy,
                                        String requestedByUuid) {
        RestoreRequest request = new RestoreRequest(
                targetUuid, targetName, snapshotId, requestedBy, requestedByUuid);

        saveRequest(targetUuid, request);
        return request;
    }

    public void notifyPlayer(Player player) {
        List<RestoreRequest> pending = getPendingRequests(
                player.getUniqueId().toString());
        cleanExpired(player.getUniqueId().toString());

        for (RestoreRequest req : pending) {
            if (!"pending".equals(req.status)) {
                continue;
            }

            Component msg = plugin.getMessage("request-received")
                    .replaceText(b -> b.matchLiteral("{admin}")
                            .replacement(req.requestedBy));

            Component accept = Component.text("[Accept]", NamedTextColor.GREEN)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand(
                            "/invbackup accept " + req.requestId))
                    .append(Component.text(" "));

            Component decline = Component.text("[Decline]", NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD)
                    .clickEvent(ClickEvent.runCommand(
                            "/invbackup decline " + req.requestId));

            player.sendMessage(msg.append(accept).append(decline));
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
            req.targetUuid = sec.getString("target-uuid", targetUuid);
            req.targetName = sec.getString("target-name", "");
            req.snapshotId = sec.getString("snapshot-id", "");
            req.requestedBy = sec.getString("requested-by", "");
            req.requestedByUuid = sec.getString("requested-by-uuid", "");
            req.timestamp = sec.getLong("timestamp", 0);
            req.status = sec.getString("status", "pending");

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
        config.set(path + ".target-uuid", request.targetUuid);
        config.set(path + ".target-name", request.targetName);
        config.set(path + ".snapshot-id", request.snapshotId);
        config.set(path + ".requested-by", request.requestedBy);
        config.set(path + ".requested-by-uuid", request.requestedByUuid);
        config.set(path + ".timestamp", request.timestamp);
        config.set(path + ".status", request.status);

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
            if ("pending".equals(sec.getString("status"))
                    && now - sec.getLong("timestamp", 0) > expireMs) {
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
}
