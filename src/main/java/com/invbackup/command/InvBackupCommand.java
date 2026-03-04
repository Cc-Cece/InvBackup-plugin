package com.invbackup.command;

import com.invbackup.InvBackup;
import com.invbackup.manager.BackupManager;
import com.invbackup.request.RestoreRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class InvBackupCommand implements CommandExecutor {

    private final InvBackup plugin;

    public InvBackupCommand(InvBackup plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                             String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "save" -> handleSave(sender, args);
            case "list" -> handleList(sender, args);
            case "preview" -> handlePreview(sender, args);
            case "restore" -> handleRestore(sender, args);
            case "forcerestore" -> handleForceRestore(sender, args);
            case "accept" -> handleAccept(sender, args);
            case "decline" -> handleDecline(sender, args);
            case "delete" -> handleDelete(sender, args);
            case "saveall" -> handleSaveAll(sender, args);
            case "import" -> handleImport(sender, args);
            case "export" -> handleExport(sender, args);
            case "exportjson" -> handleExportJson(sender, args);
            case "migrate" -> handleMigrate(sender, args);
            case "search" -> handleSearch(sender, args);
            case "gui" -> handleGui(sender);
            case "reload" -> handleReload(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    // ========== save ==========

    private void handleSave(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "This command can only be used by players.",
                    NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("invbackup.save")) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        String playerLabel = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Manual backup";

        String id = plugin.getBackupManager().saveBackup(
                player, player.getName(), player.getUniqueId().toString(),
                "player", playerLabel);

        if (id != null) {
            player.sendMessage(plugin.getMessage("backup-saved")
                    .replaceText(b -> b.matchLiteral("{id}").replacement(id)));
        }
    }

    // ========== list ==========

    private void handleList(CommandSender sender, String[] args) {
        String targetUuid;
        String targetName;
        String filterTriggerUuid = null;

        if (args.length >= 2 && !args[1].equals("--all")
                && sender.hasPermission("invbackup.admin")) {
            UUID uuid = resolvePlayerUuid(args[1]);
            if (uuid == null) {
                sender.sendMessage(plugin.getMessage("player-not-found"));
                return;
            }
            targetUuid = uuid.toString();
            targetName = args[1];

            boolean showAll = args.length >= 3
                    && args[args.length - 1].equals("--all");
            if (!showAll) {
                filterTriggerUuid = targetUuid;
            }
        } else {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text(
                        "Usage: /ib list <player> [--all]",
                        NamedTextColor.RED));
                return;
            }
            if (!player.hasPermission("invbackup.view")) {
                player.sendMessage(plugin.getMessage("no-permission"));
                return;
            }
            targetUuid = player.getUniqueId().toString();
            targetName = player.getName();
            filterTriggerUuid = targetUuid;
        }

        List<BackupManager.BackupInfo> backups = plugin.getBackupManager()
                .listBackups(targetUuid, filterTriggerUuid);

        if (backups.isEmpty()) {
            sender.sendMessage(plugin.getMessage("no-backups"));
            return;
        }

        sender.sendMessage(Component.text(
                "=== Backups for " + targetName + " ("
                        + backups.size() + ") ===",
                NamedTextColor.AQUA));

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (BackupManager.BackupInfo info : backups) {
            String time = sdf.format(new Date(info.timestamp));
            String displayLabel = info.label.isEmpty()
                    ? "" : " [" + info.label + "]";

            Component line = Component.text(" ", NamedTextColor.GRAY)
                    .append(Component.text(info.snapshotId,
                            NamedTextColor.YELLOW))
                    .append(Component.text(" " + time, NamedTextColor.GRAY))
                    .append(Component.text(displayLabel, NamedTextColor.WHITE))
                    .append(Component.text(" by ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(info.triggeredBy,
                            NamedTextColor.GRAY))
                    .append(Component.text(
                            " [" + info.backupLevel + "]",
                            NamedTextColor.DARK_AQUA))
                    .hoverEvent(HoverEvent.showText(
                            Component.text("Click to preview",
                                    NamedTextColor.GREEN)))
                    .clickEvent(ClickEvent.runCommand(
                            "/invbackup preview " + targetName + " "
                                    + info.snapshotId));

            sender.sendMessage(line);
        }
    }

    // ========== preview ==========

    private void handlePreview(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "This command can only be used by players.",
                    NamedTextColor.RED));
            return;
        }

        String targetUuid;
        String snapshotId;

        if (args.length == 2) {
            if (!player.hasPermission("invbackup.view")) {
                player.sendMessage(plugin.getMessage("no-permission"));
                return;
            }
            targetUuid = player.getUniqueId().toString();
            snapshotId = args[1];
        } else if (args.length >= 3 && player.hasPermission("invbackup.admin")) {
            UUID uuid = resolvePlayerUuid(args[1]);
            if (uuid == null) {
                player.sendMessage(plugin.getMessage("player-not-found"));
                return;
            }
            targetUuid = uuid.toString();
            snapshotId = args[2];
        } else {
            player.sendMessage(Component.text(
                    "Usage: /ib preview [player] <snapshotId>",
                    NamedTextColor.RED));
            return;
        }

        plugin.getPreviewGui().openPreview(player, targetUuid, snapshotId);
    }

    // ========== restore (creates request) ==========

    private void handleRestore(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ib restore <player> [snapshotId]",
                    NamedTextColor.RED));
            return;
        }

        UUID targetUuid = resolvePlayerUuid(args[1]);
        if (targetUuid == null) {
            sender.sendMessage(plugin.getMessage("player-not-found"));
            return;
        }

        String snapshotId = null;
        if (args.length >= 3) {
            snapshotId = args[2];
        }

        if (snapshotId == null) {
            List<BackupManager.BackupInfo> backups = plugin.getBackupManager()
                    .listBackups(targetUuid.toString(), null);
            if (backups.isEmpty()) {
                sender.sendMessage(plugin.getMessage("no-backups"));
                return;
            }
            snapshotId = backups.get(0).snapshotId;
        }

        String adminName = sender instanceof Player p
                ? p.getName() : "Console";
        String adminUuid = sender instanceof Player p
                ? p.getUniqueId().toString() : "CONSOLE";
        String targetName = resolvePlayerName(targetUuid);

        plugin.getRequestManager().createRequest(
                targetUuid.toString(), targetName, snapshotId,
                adminName, adminUuid);

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            plugin.getRequestManager().notifyPlayer(target);
            sender.sendMessage(plugin.getMessage("request-sent")
                    .replaceText(b -> b.matchLiteral("{player}")
                            .replacement(targetName)));
        } else {
            sender.sendMessage(plugin.getMessage("request-sent-offline")
                    .replaceText(b -> b.matchLiteral("{player}")
                            .replacement(targetName)));
        }
    }

    // ========== forcerestore ==========

    private void handleForceRestore(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ib forcerestore <player> [snapshotId] [minimal|full]",
                    NamedTextColor.RED));
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(plugin.getMessage("player-not-online"));
            return;
        }

        String snapshotId = null;
        String restoreLevel = null;

        if (args.length >= 3) {
            if (isRestoreLevel(args[2])) {
                restoreLevel = args[2].toLowerCase();
            } else {
                snapshotId = args[2];
            }
        }
        if (args.length >= 4) {
            restoreLevel = args[3].toLowerCase();
        }

        if (snapshotId == null) {
            List<BackupManager.BackupInfo> backups = plugin.getBackupManager()
                    .listBackups(target.getUniqueId().toString(), null);
            if (backups.isEmpty()) {
                sender.sendMessage(plugin.getMessage("no-backups"));
                return;
            }
            snapshotId = backups.get(0).snapshotId;
        }

        if (restoreLevel == null) {
            var config = plugin.getBackupManager().loadBackupConfig(
                    target.getUniqueId().toString(), snapshotId);
            restoreLevel = config != null
                    ? config.getString("meta.backup-level", "minimal")
                    : "minimal";
        }

        boolean success = plugin.getBackupManager().restoreBackup(
                target, target.getUniqueId().toString(),
                snapshotId, restoreLevel);
        if (success) {
            sender.sendMessage(plugin.getMessage("backup-restored")
                    .replaceText(b -> b.matchLiteral("{player}")
                            .replacement(target.getName())));
        } else {
            sender.sendMessage(plugin.getMessage("backup-not-found"));
        }
    }

    // ========== accept / decline ==========

    private void handleAccept(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) return;

        String requestId = args[1];
        RestoreRequest request = plugin.getRequestManager()
                .findRequest(player.getUniqueId().toString(), requestId);
        if (request == null || !"pending".equals(request.status)) {
            player.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        plugin.getRequestManager().updateRequestStatus(
                player.getUniqueId().toString(), requestId, "accepted");
        player.sendMessage(plugin.getMessage("request-accepted"));

        plugin.getRestoreGui().openRestoreGui(
                player, request.targetUuid, request.snapshotId);
    }

    private void handleDecline(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return;
        if (args.length < 2) return;

        String requestId = args[1];
        plugin.getRequestManager().updateRequestStatus(
                player.getUniqueId().toString(), requestId, "declined");
        player.sendMessage(plugin.getMessage("request-declined"));
    }

    // ========== delete ==========

    private void handleDelete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /ib delete <player> <snapshotId>",
                    NamedTextColor.RED));
            return;
        }

        UUID uuid = resolvePlayerUuid(args[1]);
        if (uuid == null) {
            sender.sendMessage(plugin.getMessage("player-not-found"));
            return;
        }

        boolean success = plugin.getBackupManager()
                .deleteBackup(uuid.toString(), args[2]);
        if (success) {
            sender.sendMessage(plugin.getMessage("backup-deleted")
                    .replaceText(b -> b.matchLiteral("{id}")
                            .replacement(args[2])));
        } else {
            sender.sendMessage(plugin.getMessage("backup-not-found"));
        }
    }

    // ========== saveall ==========

    private void handleSaveAll(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        String saveLabel = args.length > 1
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length))
                : "Batch backup";

        String triggerName = sender instanceof Player p
                ? p.getName() : "Console";
        String triggerUuid = sender instanceof Player p
                ? p.getUniqueId().toString() : "CONSOLE";

        int count = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            String id = plugin.getBackupManager().saveBackup(
                    player, triggerName, triggerUuid, "admin", saveLabel);
            if (id != null) count++;
        }

        final int finalCount = count;
        sender.sendMessage(plugin.getMessage("saveall-success")
                .replaceText(b -> b.matchLiteral("{count}")
                        .replacement(String.valueOf(finalCount))));
    }

    // ========== import ==========

    private void handleImport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (!(sender instanceof Player viewer)) {
            sender.sendMessage(Component.text(
                    "This command can only be used by players.",
                    NamedTextColor.RED));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ib import file:<name>.yml | folder:<name>",
                    NamedTextColor.GRAY));
            return;
        }

        String spec = args[1];
        BackupManager.ImportSource source = parseImportSource(spec);
        if (source == null) {
            viewer.sendMessage(Component.text(
                    "Usage: /ib import file:<name>.yml | folder:<name>",
                    NamedTextColor.RED));
            return;
        }

        plugin.getImportConfirmGui().open(viewer, source);
    }

    private BackupManager.ImportSource parseImportSource(String spec) {
        if (spec == null) return null;
        String s = spec.trim();
        if (s.startsWith("file:")) {
            String name = s.substring("file:".length());
            if (!name.endsWith(".yml")) return null;
            return new BackupManager.ImportSource(BackupManager.ImportSourceType.FILE, name);
        }
        if (s.startsWith("folder:")) {
            String name = s.substring("folder:".length());
            if (name.isEmpty()) return null;
            return new BackupManager.ImportSource(BackupManager.ImportSourceType.FOLDER, name);
        }
        return null;
    }

    // ========== export ==========

    private void handleExport(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ib export <folder> [player]",
                    NamedTextColor.RED));
            return;
        }

        String folderName = args[1];

        if (args.length == 2) {
            // Export all
            int count = plugin.getBackupManager().exportAll(folderName);
            sender.sendMessage(plugin.getMessage("export-success")
                    .replaceText(b -> b.matchLiteral("{count}")
                            .replacement(String.valueOf(count)))
                    .replaceText(b -> b.matchLiteral("{folder}")
                            .replacement(folderName)));
        } else {
            // Export specific player
            UUID uuid = resolvePlayerUuid(args[2]);
            if (uuid == null) {
                sender.sendMessage(plugin.getMessage("player-not-found"));
                return;
            }
            boolean success = plugin.getBackupManager()
                    .exportPlayer(uuid.toString(), folderName);
            if (success) {
                sender.sendMessage(
                        plugin.getMessage("export-player-success")
                                .replaceText(b -> b.matchLiteral("{player}")
                                        .replacement(args[2]))
                                .replaceText(b -> b.matchLiteral("{folder}")
                                        .replacement(folderName)));
            } else {
                sender.sendMessage(plugin.getMessage("export-no-data"));
            }
        }
    }

    // ========== exportjson ==========

    private void handleExportJson(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ib exportjson file:<uuid>.yml | folder:<name>",
                    NamedTextColor.RED));
            return;
        }

        String spec = args[1];
        File baseDir = new File(plugin.getDataFolder(), "json-tool");
        File importDir = new File(baseDir, "import");
        File exportDir = new File(baseDir, "export");
        importDir.mkdirs();
        exportDir.mkdirs();

        if (spec.startsWith("file:")) {
            String name = spec.substring("file:".length());
            if (!name.endsWith(".yml")) {
                sender.sendMessage(Component.text(
                        "For file: mode, please use file:<uuid>.yml",
                        NamedTextColor.RED));
                return;
            }
            File in = new File(importDir, name);
            if (!in.exists() || !in.isFile()) {
                sender.sendMessage(Component.text(
                        "Input file not found under json-tool/import/: " + name,
                        NamedTextColor.RED));
                return;
            }
            File out = new File(exportDir, name.replace(".yml", ".json"));
            boolean ok = plugin.getBackupManager().exportYamlFileToWebJson(in, out);
            if (ok) {
                sender.sendMessage(Component.text(
                        "Exported to json-tool/export/" + out.getName(),
                        NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text(
                        "Failed to export JSON for " + name,
                        NamedTextColor.RED));
            }
        } else if (spec.startsWith("folder:")) {
            String folder = spec.substring("folder:".length());
            if (folder.isEmpty()) {
                sender.sendMessage(Component.text(
                        "Usage: /ib exportjson folder:<name>",
                        NamedTextColor.RED));
                return;
            }
            File inFolder = new File(importDir, folder);
            if (!inFolder.exists() || !inFolder.isDirectory()) {
                sender.sendMessage(Component.text(
                        "Input folder not found under json-tool/import/: " + folder,
                        NamedTextColor.RED));
                return;
            }
            File outFolder = new File(exportDir, folder);
            outFolder.mkdirs();

            File[] files = inFolder.listFiles((d, n) -> n.endsWith(".yml"));
            if (files == null || files.length == 0) {
                sender.sendMessage(Component.text(
                        "No .yml files found in folder " + folder,
                        NamedTextColor.RED));
                return;
            }

            int success = 0;
            for (File f : files) {
                File out = new File(outFolder,
                        f.getName().replace(".yml", ".json").replace(".yaml", ".json"));
                if (plugin.getBackupManager().exportYamlFileToWebJson(f, out)) {
                    success++;
                }
            }

            sender.sendMessage(Component.text(
                    "Exported " + success + " file(s) to json-tool/export/" + folder,
                    success > 0 ? NamedTextColor.GREEN : NamedTextColor.RED));
        } else {
            sender.sendMessage(Component.text(
                    "Usage: /ib exportjson file:<uuid>.yml | folder:<name>",
                    NamedTextColor.RED));
        }
    }

    // ========== migrate ==========

    private void handleMigrate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(Component.text(
                    "Usage: /ib migrate <oldUUID> <newUUID>",
                    NamedTextColor.RED));
            return;
        }

        String oldUuid = args[1];
        String newUuid = args[2];

        // Validate UUIDs
        try {
            UUID.fromString(oldUuid);
            UUID.fromString(newUuid);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(
                    "Invalid UUID format.", NamedTextColor.RED));
            return;
        }

        boolean success = plugin.getBackupManager()
                .migrateUuid(oldUuid, newUuid);
        if (success) {
            sender.sendMessage(plugin.getMessage("migrate-success")
                    .replaceText(b -> b.matchLiteral("{old}")
                            .replacement(oldUuid))
                    .replaceText(b -> b.matchLiteral("{new}")
                            .replacement(newUuid)));
        } else {
            sender.sendMessage(plugin.getMessage("migrate-no-data"));
        }
    }

    // ========== search ==========

    private void handleSearch(CommandSender sender, String[] args) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(Component.text(
                    "Usage: /ib search <playerName>", NamedTextColor.RED));
            return;
        }

        String playerName = args[1];
        List<BackupManager.SearchResult> results = plugin.getBackupManager()
                .searchByName(playerName);

        if (results.isEmpty()) {
            sender.sendMessage(plugin.getMessage("search-no-results"));
            return;
        }

        sender.sendMessage(plugin.getMessage("search-results")
                .replaceText(b -> b.matchLiteral("{count}")
                        .replacement(String.valueOf(results.size()))));

        for (BackupManager.SearchResult result : results) {
            sender.sendMessage(Component.text("  ", NamedTextColor.GRAY)
                    .append(Component.text(result.playerName,
                            NamedTextColor.GOLD))
                    .append(Component.text(" -> ", NamedTextColor.GRAY))
                    .append(Component.text(result.uuid,
                            NamedTextColor.YELLOW))
                    .append(Component.text(
                            " (" + result.folder + ")",
                            NamedTextColor.DARK_GRAY)));
        }
    }

    // ========== gui ==========

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "This command can only be used by players.",
                    NamedTextColor.RED));
            return;
        }
        if (!player.hasPermission("invbackup.admin")) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        plugin.getAdminGui().openPlayerList(player, 0);
    }

    // ========== reload ==========

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        plugin.reloadConfig();
        plugin.getLanguageManager().reload();
        plugin.getIdentityManager().reload();
        sender.sendMessage(plugin.getMessage("reload-success"));
    }

    // ========== help ==========

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text(
                "=== InvBackup Help ===", NamedTextColor.AQUA));

        if (sender.hasPermission("invbackup.save")) {
            sender.sendMessage(helpLine("/ib save [label]",
                    "Backup your inventory"));
        }
        if (sender.hasPermission("invbackup.view")) {
            sender.sendMessage(helpLine("/ib list",
                    "List your backups"));
            sender.sendMessage(helpLine("/ib preview <id>",
                    "Preview a backup"));
        }
        if (sender.hasPermission("invbackup.admin")) {
            sender.sendMessage(helpLine("/ib gui",
                    "Open admin management GUI"));
            sender.sendMessage(helpLine("/ib saveall [label]",
                    "Backup all online players"));
            sender.sendMessage(helpLine("/ib restore <player> [id]",
                    "Send restore request to player"));
            sender.sendMessage(helpLine(
                    "/ib forcerestore <player> [id] [minimal|full]",
                    "Direct restore (no request)"));
            sender.sendMessage(helpLine("/ib list <player> [--all]",
                    "List player's backups"));
            sender.sendMessage(helpLine("/ib preview <player> <id>",
                    "Preview any backup"));
            sender.sendMessage(helpLine("/ib delete <player> <id>",
                    "Delete a backup"));
            sender.sendMessage(helpLine("/ib import file:<name>.yml | folder:<name>",
                    "Import with confirmation GUI"));
            sender.sendMessage(helpLine("/ib export <folder> [player]",
                    "Export backups"));
            sender.sendMessage(helpLine("/ib migrate <oldUUID> <newUUID>",
                    "Migrate UUID"));
            sender.sendMessage(helpLine("/ib search <name>",
                    "Search by player name"));
            sender.sendMessage(helpLine("/ib reload", "Reload config"));
        }
    }

    private Component helpLine(String cmd, String desc) {
        return Component.text(cmd, NamedTextColor.YELLOW)
                .append(Component.text(" - " + desc, NamedTextColor.GRAY));
    }

    private boolean isRestoreLevel(String s) {
        return "minimal".equalsIgnoreCase(s) || "full".equalsIgnoreCase(s);
    }

    private UUID resolvePlayerUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();

        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        // In offline-mode servers, UUID is deterministic by name.
        // We intentionally allow never-joined names to be resolved so restore requests
        // can be queued and delivered when they eventually join.
        if (offline.getUniqueId() != null) return offline.getUniqueId();

        try {
            return UUID.fromString(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String resolvePlayerName(UUID uuid) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        return op.getName() != null ? op.getName() : uuid.toString();
    }
}
