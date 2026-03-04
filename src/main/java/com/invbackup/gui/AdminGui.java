package com.invbackup.gui;

import com.invbackup.InvBackup;
import com.invbackup.manager.BackupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AdminGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private final InvBackup plugin;
    private final Map<UUID, AdminSession> activeSessions = new HashMap<>();

    public AdminGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    // ========== Level 1: Player List ==========

    public void openPlayerList(Player viewer, int page) {
        Set<String> allUuids = plugin.getBackupManager().getAllPlayerUuids();
        List<String> uuidList = new ArrayList<>(allUuids);

        int totalPages = Math.max(1,
                (int) Math.ceil((double) uuidList.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        Component title = plugin.getLanguageManager().getGuiMessage(
                "gui.admin.title-players",
                "{page}", String.valueOf(page + 1),
                "{total}", String.valueOf(totalPages)
        );
        Inventory gui = Bukkit.createInventory(null, 54, title);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, uuidList.size());

        for (int i = start; i < end; i++) {
            String uuid = uuidList.get(i);
            gui.setItem(i - start, createPlayerHead(uuid));
        }

        fillNavRow(gui, page, totalPages);

        AdminSession session = new AdminSession();
        session.level = 1;
        session.page = page;
        session.uuidList = uuidList;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);
        viewer.openInventory(gui);
    }

    // ========== Level 2: Backup List ==========

    private void openBackupList(Player viewer, String targetUuid, int page) {
        List<BackupManager.BackupInfo> backups = plugin.getBackupManager()
                .listBackups(targetUuid, null);

        int totalPages = Math.max(1,
                (int) Math.ceil((double) backups.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        String playerName = resolvePlayerName(targetUuid);
        Component title = plugin.getLanguageManager().getGuiMessage(
                "gui.admin.title-backups",
                "{player}", playerName,
                "{page}", String.valueOf(page + 1),
                "{total}", String.valueOf(totalPages)
        );
        Inventory gui = Bukkit.createInventory(null, 54, title);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, backups.size());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (int i = start; i < end; i++) {
            BackupManager.BackupInfo info = backups.get(i);
            gui.setItem(i - start, createBackupItem(info, sdf));
        }

        fillNavRow(gui, page, totalPages);

        gui.setItem(SLOT_PREV, createItem(Material.ARROW,
                plugin.getLanguageManager().getGuiMessage(
                        "gui.admin.back-to-players")));

        AdminSession session = new AdminSession();
        session.level = 2;
        session.page = page;
        session.selectedUuid = targetUuid;
        session.backups = backups;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);
        viewer.openInventory(gui);
    }

    // ========== Event Handler ==========

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        AdminSession session = activeSessions.get(player.getUniqueId());
        if (session == null) return;

        if (event.getView().getTopInventory() != session.inventory) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;

        if (session.level == 1) {
            handlePlayerListClick(player, session, slot);
        } else if (session.level == 2) {
            handleBackupListClick(player, session, slot);
        }
    }

    private void handlePlayerListClick(Player player, AdminSession session,
                                       int slot) {
        if (slot == SLOT_NEXT) {
            openPlayerList(player, session.page + 1);
            return;
        }
        if (slot == SLOT_PREV) {
            openPlayerList(player, session.page - 1);
            return;
        }

        // Bulk restore button lives at SLOT_INFO + 1 on the player list page.
        if (slot == SLOT_INFO + 1) {
            activeSessions.remove(player.getUniqueId());
            plugin.getBulkRestoreGui().open(player);
            return;
        }

        int index = session.page * PAGE_SIZE + slot;
        if (slot < PAGE_SIZE && index < session.uuidList.size()) {
            String uuid = session.uuidList.get(index);
            openBackupList(player, uuid, 0);
        }
    }

    private void handleBackupListClick(Player player, AdminSession session,
                                       int slot) {
        if (slot == SLOT_PREV) {
            openPlayerList(player, 0);
            return;
        }
        if (slot == SLOT_NEXT) {
            openBackupList(player, session.selectedUuid, session.page + 1);
            return;
        }

        int index = session.page * PAGE_SIZE + slot;
        if (slot < PAGE_SIZE && session.backups != null
                && index < session.backups.size()) {
            BackupManager.BackupInfo info = session.backups.get(index);
            activeSessions.remove(player.getUniqueId());
            plugin.getPreviewGui().openPreview(
                    player, session.selectedUuid, info.snapshotId,
                    () -> openBackupList(player, session.selectedUuid, session.page));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        AdminSession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        AdminSession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getInventory() == session.inventory) {
            activeSessions.remove(player.getUniqueId());
        }
    }

    public void removeSession(UUID playerId) {
        activeSessions.remove(playerId);
    }

    // ========== Helper Methods ==========

    private void fillNavRow(Inventory gui, int page, int totalPages) {
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE,
                    Component.text(" ")));
        }

        Component info = plugin.getLanguageManager().getGuiMessage(
                "gui.common.page",
                "{page}", String.valueOf(page + 1),
                "{total}", String.valueOf(totalPages)
        );
        gui.setItem(SLOT_INFO, createItem(Material.PAPER, info));

        // Add a bulk restore button on the player list page (level 1)
        // using the same nav row, next to page info.
        gui.setItem(SLOT_INFO + 1, createItem(Material.EMERALD,
                plugin.getLanguageManager().getGuiMessage("gui.admin.bulk-restore")));

        if (page > 0) {
            gui.setItem(SLOT_PREV, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.prev-page")));
        }

        if (page < totalPages - 1) {
            gui.setItem(SLOT_NEXT, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.next-page")));
        }
    }

    private ItemStack createPlayerHead(String uuid) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        String name = resolvePlayerName(uuid);
        meta.displayName(Component.text(name, NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            meta.setOwningPlayer(op);
        } catch (Exception ignored) {
        }

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.admin.player.uuid", "{uuid}", uuid));

        int count = plugin.getBackupManager()
                .listBackups(uuid, null).size();
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.admin.player.backup-count",
                "{count}", String.valueOf(count)
        ));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.admin.player.click-view"));

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createBackupItem(BackupManager.BackupInfo info,
                                       SimpleDateFormat sdf) {
        Material mat = "import".equals(info.source)
                ? Material.ENDER_EYE : Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String time = sdf.format(new Date(info.timestamp));
        meta.displayName(Component.text(info.snapshotId, NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.admin.backup.time", "{time}", time));
        if (!info.label.isEmpty()) {
            lore.add(plugin.getLanguageManager().getGuiMessage(
                    "gui.admin.backup.label", "{label}", info.label));
        }
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.admin.backup.by", "{by}", info.triggeredBy));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.admin.backup.type", "{type}", info.triggerType));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.admin.backup.level", "{level}", info.backupLevel));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.admin.backup.click-preview"));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private String resolvePlayerName(String uuid) {
        String mapped = plugin.getIdentityManager().resolveName(uuid);
        if (mapped != null) return mapped;
        return uuid.substring(0, 8) + "...";
    }

    private static class AdminSession {
        int level;
        int page;
        List<String> uuidList;
        String selectedUuid;
        List<BackupManager.BackupInfo> backups;
        Inventory inventory;
    }
}
