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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class AdminGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_SORT = 47;
    private static final int SLOT_BACK_TO_CATEGORY = 48;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_BULK_RESTORE = 50;
    private static final int SLOT_NEXT = 53;

    private final InvBackup plugin;
    private final Map<UUID, AdminSession> activeSessions = new HashMap<>();

    public AdminGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    // ========== Level 1: Player List ==========

    public void openPlayerList(Player viewer, int page) {
        openPlayerList(viewer, page, PlayerSortMode.NAME_ASC);
    }

    public void openPlayerList(Player viewer, int page, PlayerSortMode sortMode) {
        List<String> uuidList = buildSortedPlayerList(sortMode);

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

        fillNavRow(gui, page, totalPages, 1, sortMode);

        AdminSession session = new AdminSession();
        session.level = 1;
        session.page = page;
        session.sortMode = sortMode;
        session.uuidList = uuidList;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);
        viewer.openInventory(gui);
    }

    // ========== Level 2: Backup List ==========

    public void openBackupList(Player viewer, String targetUuid, int page) {
        openBackupList(viewer, targetUuid, page, 0, PlayerSortMode.NAME_ASC);
    }

    public void openBackupList(Player viewer, String targetUuid, int page,
                               int returnPlayerPage, PlayerSortMode returnSortMode) {
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

        fillNavRow(gui, page, totalPages, 2, returnSortMode);

        gui.setItem(SLOT_PREV, createItem(Material.ARROW,
                plugin.getLanguageManager().getGuiMessage(
                        "gui.admin.back-to-players")));

        AdminSession session = new AdminSession();
        session.level = 2;
        session.page = page;
        session.selectedUuid = targetUuid;
        session.parentPlayerPage = Math.max(0, returnPlayerPage);
        session.sortMode = returnSortMode;
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
            openPlayerList(player, session.page + 1, session.sortMode);
            return;
        }
        if (slot == SLOT_PREV) {
            openPlayerList(player, session.page - 1, session.sortMode);
            return;
        }

        if (slot == SLOT_SORT) {
            openPlayerList(player, 0, session.sortMode.next());
            return;
        }

        if (slot == SLOT_BACK_TO_CATEGORY) {
            activeSessions.remove(player.getUniqueId());
            plugin.getCategoryGui().openCategoryMenu(player);
            return;
        }

        if (slot == SLOT_BULK_RESTORE) {
            activeSessions.remove(player.getUniqueId());
            plugin.getBulkRestoreGui().open(player, () -> openPlayerList(player, session.page, session.sortMode));
            return;
        }

        int index = session.page * PAGE_SIZE + slot;
        if (slot < PAGE_SIZE && index < session.uuidList.size()) {
            String uuid = session.uuidList.get(index);
            openBackupList(player, uuid, 0, session.page, session.sortMode);
        }
    }

    private void handleBackupListClick(Player player, AdminSession session,
                                       int slot) {
        if (slot == SLOT_PREV) {
            openPlayerList(player, session.parentPlayerPage, session.sortMode);
            return;
        }
        if (slot == SLOT_NEXT) {
            openBackupList(player, session.selectedUuid, session.page + 1,
                    session.parentPlayerPage, session.sortMode);
            return;
        }

        int index = session.page * PAGE_SIZE + slot;
        if (slot < PAGE_SIZE && session.backups != null
                && index < session.backups.size()) {
            BackupManager.BackupInfo info = session.backups.get(index);
            activeSessions.remove(player.getUniqueId());
            plugin.getPreviewGui().openPreview(
                    player, session.selectedUuid, info.snapshotId,
                    () -> openBackupList(player, session.selectedUuid, session.page,
                            session.parentPlayerPage, session.sortMode));
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

    private void fillNavRow(Inventory gui, int page, int totalPages,
                            int level, PlayerSortMode sortMode) {
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

        if (level == 1) {
            gui.setItem(SLOT_SORT, createItem(Material.HOPPER,
                    plugin.getLanguageManager().getGuiMessage(
                            "gui.admin.sort-toggle",
                            "{mode}", plugin.getLanguageManager().getRawMessage(sortMode.langKey))));
            gui.setItem(SLOT_BACK_TO_CATEGORY, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.admin.back-to-category")));
            gui.setItem(SLOT_BULK_RESTORE, createItem(Material.EMERALD,
                    plugin.getLanguageManager().getGuiMessage("gui.admin.bulk-restore")));
        }

        if (page > 0) {
            gui.setItem(SLOT_PREV, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.prev-page")));
        }

        if (page < totalPages - 1) {
            gui.setItem(SLOT_NEXT, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.next-page")));
        }
    }

    private List<String> buildSortedPlayerList(PlayerSortMode sortMode) {
        Set<String> allUuids = plugin.getBackupManager().getAllPlayerUuids();
        List<String> uuidList = new ArrayList<>(allUuids);

        switch (sortMode) {
            case NAME_ASC -> uuidList.sort(Comparator.comparing(this::resolvePlayerName,
                    String.CASE_INSENSITIVE_ORDER));
            case LAST_PLAYED_DESC -> uuidList.sort((a, b) -> {
                int cmp = Long.compare(getLastPlayedSafe(b), getLastPlayedSafe(a));
                if (cmp != 0) return cmp;
                return resolvePlayerName(a).compareToIgnoreCase(resolvePlayerName(b));
            });
        }

        return uuidList;
    }

    private long getLastPlayedSafe(String uuid) {
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            return op.getLastPlayed();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public ItemStack createPlayerHead(String uuid) {
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
        int parentPlayerPage;
        PlayerSortMode sortMode = PlayerSortMode.NAME_ASC;
        List<String> uuidList;
        String selectedUuid;
        List<BackupManager.BackupInfo> backups;
        Inventory inventory;
    }

    public enum PlayerSortMode {
        NAME_ASC("gui.admin.sort.name"),
        LAST_PLAYED_DESC("gui.admin.sort.last-played");

        private final String langKey;

        PlayerSortMode(String langKey) {
            this.langKey = langKey;
        }

        public PlayerSortMode next() {
            return this == NAME_ASC ? LAST_PLAYED_DESC : NAME_ASC;
        }
    }
}
