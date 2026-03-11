package com.invbackup;

import com.invbackup.command.InvBackupCommand;
import com.invbackup.command.InvBackupTabCompleter;
import com.invbackup.gui.AdminGui;
import com.invbackup.gui.BulkRestoreGui;
import com.invbackup.gui.CategoryGui;
import com.invbackup.gui.ImportConfirmGui;
import com.invbackup.gui.PreviewGui;
import com.invbackup.gui.RestoreGui;
import com.invbackup.gui.SearchGui;
import com.invbackup.manager.BackupManager;
import com.invbackup.manager.LanguageManager;
import com.invbackup.manager.PlayerIdentityManager;
import com.invbackup.request.RestoreRequestManager;
import io.papermc.lib.PaperLib;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class InvBackup extends JavaPlugin implements Listener {

    private BackupManager backupManager;
    private RestoreRequestManager requestManager;
    private LanguageManager languageManager;
    private PlayerIdentityManager identityManager;
    private PreviewGui previewGui;
    private RestoreGui restoreGui;
    private AdminGui adminGui;
    private ImportConfirmGui importConfirmGui;
    private BulkRestoreGui bulkRestoreGui;
    private CategoryGui categoryGui;
    private SearchGui searchGui;
    private int autoSaveTaskId = -1;

    @Override
    public void onEnable() {
        PaperLib.suggestPaper(this);
        saveDefaultConfig();

        backupManager = new BackupManager(this);
        languageManager = new LanguageManager(this);
        identityManager = new PlayerIdentityManager(this);
        identityManager.reload();
        requestManager = new RestoreRequestManager(this);
        previewGui = new PreviewGui(this);
        restoreGui = new RestoreGui(this);
        adminGui = new AdminGui(this);
        importConfirmGui = new ImportConfirmGui(this);
        bulkRestoreGui = new BulkRestoreGui(this);
        categoryGui = new CategoryGui(this);
        searchGui = new SearchGui(this);

        var cmd = getCommand("invbackup");
        if (cmd != null) {
            cmd.setExecutor(new InvBackupCommand(this));
            cmd.setTabCompleter(new InvBackupTabCompleter(this));
        }

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(previewGui, this);
        getServer().getPluginManager().registerEvents(restoreGui, this);
        getServer().getPluginManager().registerEvents(adminGui, this);
        getServer().getPluginManager().registerEvents(importConfirmGui, this);
        getServer().getPluginManager().registerEvents(bulkRestoreGui, this);
        getServer().getPluginManager().registerEvents(categoryGui, this);
        getServer().getPluginManager().registerEvents(searchGui, this);

        startAutoSaveTask();

        getLogger().info("InvBackup enabled!");
    }

    @Override
    public void onDisable() {
        if (isEventEnabled("on-quit", "auto-backup.on-quit", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                backupManager.saveBackup(
                        player, "Server", "CONSOLE", "auto", "Server shutdown");
            }
        }

        stopAutoSaveTask();
        backupManager.shutdown();

        getLogger().info("InvBackup disabled!");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (isEventEnabled("on-quit", "auto-backup.on-quit", true)) {
            backupManager.saveBackup(
                    event.getPlayer(), "Server", "CONSOLE", "quit", "Auto quit");
        }
        Player p = event.getPlayer();
        previewGui.removeSession(p.getUniqueId());
        restoreGui.removeSession(p.getUniqueId());
        adminGui.removeSession(p.getUniqueId());
        importConfirmGui.removeSession(p.getUniqueId());
        bulkRestoreGui.removeSession(p.getUniqueId());
        categoryGui.removeSession(p.getUniqueId());
        searchGui.removeSession(p.getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        identityManager.onJoin(player);

        if (isEventEnabled("on-join", "auto-backup.on-join", false)) {
            backupManager.requestAutoBackup(
                    player, "join", "Auto join");
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                requestManager.notifyPlayer(player);
            }
        }, 40L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!isEventEnabled("on-death", "", true)) {
            return;
        }
        Player player = event.getEntity();
        backupManager.requestAutoBackup(player, "death", "Auto death");
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!isEventEnabled("on-world-change", "", true)) {
            return;
        }

        Player player = event.getPlayer();
        World.Environment to = player.getWorld().getEnvironment();

        boolean onlyNetherEnd = getConfig().getBoolean(
                "backup-strategy.events.world-change-only-nether-end", false);
        if (onlyNetherEnd
                && to != World.Environment.NETHER
                && to != World.Environment.THE_END) {
            return;
        }

        backupManager.requestAutoBackup(player, "world-change",
                "Auto world change");
    }

    private void startAutoSaveTask() {
        if (!isEventEnabled("on-interval", "", true)) {
            return;
        }

        int interval = getIntervalSeconds();
        if (interval > 0) {
            autoSaveTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    this,
                    () -> {
                        for (Player player : Bukkit.getOnlinePlayers()) {
                            backupManager.requestAutoBackup(
                                    player, "interval", "Auto interval");
                        }
                    },
                    interval * 20L,
                    interval * 20L
            );
        }
    }

    private void stopAutoSaveTask() {
        if (autoSaveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(autoSaveTaskId);
            autoSaveTaskId = -1;
        }
    }

    public void restartAutoSaveTask() {
        stopAutoSaveTask();
        startAutoSaveTask();
    }

    private int getIntervalSeconds() {
        if (getConfig().contains("backup-strategy.events.interval-seconds")) {
            return Math.max(0,
                    getConfig().getInt("backup-strategy.events.interval-seconds", 0));
        }
        return Math.max(0, getConfig().getInt("auto-backup.interval", 0));
    }

    private boolean isEventEnabled(String key, String legacyPath, boolean defaultValue) {
        String path = "backup-strategy.events." + key;
        if (getConfig().contains(path)) {
            return getConfig().getBoolean(path, defaultValue);
        }
        if (legacyPath != null && !legacyPath.isBlank() && getConfig().contains(legacyPath)) {
            return getConfig().getBoolean(legacyPath, defaultValue);
        }
        return defaultValue;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public RestoreRequestManager getRequestManager() {
        return requestManager;
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    public PlayerIdentityManager getIdentityManager() {
        return identityManager;
    }

    public PreviewGui getPreviewGui() {
        return previewGui;
    }

    public RestoreGui getRestoreGui() {
        return restoreGui;
    }

    public AdminGui getAdminGui() {
        return adminGui;
    }

    public ImportConfirmGui getImportConfirmGui() {
        return importConfirmGui;
    }

    public BulkRestoreGui getBulkRestoreGui() {
        return bulkRestoreGui;
    }

    public CategoryGui getCategoryGui() {
        return categoryGui;
    }

    public SearchGui getSearchGui() {
        return searchGui;
    }

    public Component getMessage(String key) {
        return languageManager.getMessage(key);
    }
}
