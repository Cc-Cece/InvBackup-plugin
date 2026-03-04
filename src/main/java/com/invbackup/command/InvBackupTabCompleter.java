package com.invbackup.command;

import com.invbackup.InvBackup;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.io.File;

public class InvBackupTabCompleter implements TabCompleter {

    private final InvBackup plugin;

    public InvBackupTabCompleter(InvBackup plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                      String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("invbackup.save")) {
                completions.add("save");
            }
            if (sender.hasPermission("invbackup.view")) {
                completions.add("list");
                completions.add("preview");
            }
            if (sender.hasPermission("invbackup.admin")) {
                completions.addAll(Arrays.asList(
                        "gui", "saveall", "restore", "forcerestore",
                        "delete", "import", "export", "exportjson", "migrate",
                        "search", "reload"));
                if (!completions.contains("list")) completions.add("list");
                if (!completions.contains("preview")) completions.add("preview");
            }
            return filter(completions, args[0]);
        }

        String sub = args[0].toLowerCase();

        if (args.length == 2) {
            switch (sub) {
                case "list", "restore", "forcerestore", "delete" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.addAll(getOnlinePlayerNames());
                    }
                }
                case "preview" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.addAll(getOnlinePlayerNames());
                    }
                    if (sender instanceof Player player
                            && sender.hasPermission("invbackup.view")) {
                        completions.addAll(
                                getSnapshotIds(player.getUniqueId().toString()));
                    }
                }
                case "import" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.add("file:");
                        completions.add("folder:");
                        for (String f : plugin.getBackupManager().listImportFiles()) {
                            completions.add("file:" + f);
                        }
                        for (String folder : plugin.getBackupManager().listImportFolders()) {
                            completions.add("folder:" + folder);
                        }
                    }
                }
                case "export" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.addAll(
                                plugin.getBackupManager().listImportFolders());
                    }
                }
                case "exportjson" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        File baseDir = new File(plugin.getDataFolder(), "json-tool");
                        File importDir = new File(baseDir, "import");
                        if (importDir.exists() && importDir.isDirectory()) {
                            File[] children = importDir.listFiles();
                            if (children != null) {
                                for (File f : children) {
                                    String name = f.getName();
                                    if (f.isFile() && name.endsWith(".yml")) {
                                        completions.add("file:" + name);
                                    } else if (f.isDirectory()) {
                                        completions.add("folder:" + name);
                                    }
                                }
                            }
                        }
                        // Fallback prefixes to guide usage even when目录为空
                        if (completions.isEmpty()) {
                            completions.add("file:");
                            completions.add("folder:");
                        }
                    }
                }
                case "migrate", "search" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.addAll(getOnlinePlayerNames());
                    }
                }
            }
            return filter(completions, args[1]);
        }

        if (args.length == 3) {
            switch (sub) {
                case "preview", "delete", "restore" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        UUID uuid = resolveUuid(args[1]);
                        if (uuid != null) {
                            completions.addAll(
                                    getSnapshotIds(uuid.toString()));
                        }
                    }
                }
                case "forcerestore" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        UUID uuid = resolveUuid(args[1]);
                        if (uuid != null) {
                            completions.addAll(
                                    getSnapshotIds(uuid.toString()));
                        }
                        completions.add("minimal");
                        completions.add("full");
                    }
                }
                case "import" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.addAll(getOnlinePlayerNames());
                        completions.add("--by-name");
                    }
                }
                case "export" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.addAll(getOnlinePlayerNames());
                    }
                }
                case "migrate" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.addAll(getOnlinePlayerNames());
                    }
                }
                case "list" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.add("--all");
                    }
                }
            }
            return filter(completions, args[2]);
        }

        if (args.length == 4) {
            switch (sub) {
                case "forcerestore" -> {
                    completions.add("minimal");
                    completions.add("full");
                }
                case "import" -> {
                    if (sender.hasPermission("invbackup.admin")) {
                        completions.add("--by-name");
                    }
                }
            }
            return filter(completions, args[3]);
        }

        return completions;
    }

    private List<String> getOnlinePlayerNames() {
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.toList());
    }

    private List<String> getSnapshotIds(String uuid) {
        return plugin.getBackupManager().listBackups(uuid, null).stream()
                .map(b -> b.snapshotId)
                .limit(20)
                .collect(Collectors.toList());
    }

    private UUID resolveUuid(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) return player.getUniqueId();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline.hasPlayedBefore()) return offline.getUniqueId();

        return null;
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
