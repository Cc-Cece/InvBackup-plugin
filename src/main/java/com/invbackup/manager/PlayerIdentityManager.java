package com.invbackup.manager;

import com.invbackup.InvBackup;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves player display names from UUIDs.
 *
 * In offline-mode servers, UUIDs are deterministic for a given player name,
 * but players that rename will get a different offline-UUID; therefore we keep
 * a best-effort cache based on server's offline player data and usercache.json.
 */
public class PlayerIdentityManager {

    private static final Pattern USERCACHE_ENTRY = Pattern.compile(
            "\"name\"\\s*:\\s*\"(?<name>[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"[\\s\\S]*?"
                    + "\"uuid\"\\s*:\\s*\"(?<uuid>[0-9a-fA-F\\-]{32,36})\"",
            Pattern.MULTILINE);

    private final InvBackup plugin;
    private final Map<UUID, String> uuidToName = new HashMap<>();

    public PlayerIdentityManager(InvBackup plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        uuidToName.clear();

        // 1) Online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName() != null) {
                uuidToName.put(p.getUniqueId(), p.getName());
            }
        }

        // 2) Offline players (server data)
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            String name = op.getName();
            if (name != null) {
                uuidToName.put(op.getUniqueId(), name);
            }
        }

        // 3) usercache.json (best effort)
        loadUsercache();
    }

    public void onJoin(Player player) {
        if (player != null && player.getName() != null) {
            uuidToName.put(player.getUniqueId(), player.getName());
        }
    }

    public String resolveName(UUID uuid) {
        if (uuid == null) return null;
        String cached = uuidToName.get(uuid);
        if (cached != null) return cached;

        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = op.getName();
        if (name != null) {
            uuidToName.put(uuid, name);
            return name;
        }
        return null;
    }

    public String resolveName(String uuidStr) {
        try {
            return resolveName(UUID.fromString(uuidStr));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void loadUsercache() {
        try {
            File root = plugin.getServer().getWorldContainer();
            File file = new File(root, "usercache.json");
            if (!file.exists() || !file.isFile()) return;

            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Matcher m = USERCACHE_ENTRY.matcher(json);
            while (m.find()) {
                String name = unescapeJson(m.group("name"));
                String uuidRaw = m.group("uuid");
                if (name == null || uuidRaw == null) continue;
                try {
                    UUID uuid = normalizeUuid(uuidRaw);
                    if (uuid != null) {
                        uuidToName.putIfAbsent(uuid, name);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read usercache.json: " + e.getMessage());
        }
    }

    private static UUID normalizeUuid(String uuidRaw) {
        String s = uuidRaw.trim();
        if (s.length() == 32) {
            // Insert dashes: 8-4-4-4-12
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-"
                    + s.substring(12, 16) + "-" + s.substring(16, 20) + "-"
                    + s.substring(20);
        }
        return UUID.fromString(s);
    }

    private static String unescapeJson(String s) {
        if (s == null) return null;
        // Minimal JSON string unescape for common sequences.
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }
}

