package com.invbackup.gui;

import com.invbackup.InvBackup;
import com.invbackup.manager.BackupManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bulk restore request GUI:
 * - Rows 1-4 (slots 0-35): player heads, 36 per page.
 * - Row 5 (36-44): prev, next, back, info, select-all, select-page, clear.
 * - Row 6 (45-53): filter toggles, send, cancel.
 */
public class BulkRestoreGui implements Listener {

    private static final int PAGE_SIZE = 36;
    private static final int SLOT_PLAYERS_START = 0;
    private static final int SLOT_PLAYERS_END = 35;

    private static final int SLOT_PREV = 36;
    private static final int SLOT_NEXT = 37;
    private static final int SLOT_BACK = 38;
    private static final int SLOT_INFO = 39;
    private static final int SLOT_SELECT_ALL = 40;
    private static final int SLOT_SELECT_PAGE = 41;
    private static final int SLOT_CLEAR = 42;

    private static final int SLOT_FILTER_LATEST = 45;
    private static final int SLOT_FILTER_SURVIVAL = 46;
    private static final int SLOT_FILTER_RECENT = 47;
    private static final int SLOT_SEND = 51;
    private static final int SLOT_CANCEL = 53;

    private final InvBackup plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public BulkRestoreGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        open(viewer, null);
    }

    public void open(Player viewer, Runnable onBack) {
        Inventory gui = Bukkit.createInventory(null, 54,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.title"));

        Set<String> allUuids = plugin.getBackupManager().getAllPlayerUuids();
        List<String> uuidList = new ArrayList<>(allUuids);

        Session session = new Session();
        session.inventory = gui;
        session.uuidList = uuidList;
        session.selected = new HashSet<>();
        session.page = 0;
        session.useLatest = true;
        session.onlySurvival = true;
        session.recentHours = 0;
        session.onBack = onBack;

        refreshPlayerSlots(gui, session);
        fillNavRow(gui, session);
        updateFilterButtons(gui, session);
        updateInfo(gui, session);

        sessions.put(viewer.getUniqueId(), session);
        viewer.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (event.getView().getTopInventory() != session.inventory) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= session.inventory.getSize()) return;

        // Player area (0-35)
        if (slot >= SLOT_PLAYERS_START && slot <= SLOT_PLAYERS_END) {
            int index = session.page * PAGE_SIZE + (slot - SLOT_PLAYERS_START);
            if (index >= 0 && index < session.uuidList.size()) {
                String uuid = session.uuidList.get(index);
                boolean nowSelected = !session.selected.contains(uuid);
                if (nowSelected) {
                    session.selected.add(uuid);
                } else {
                    session.selected.remove(uuid);
                }
                updateInfo(session.inventory, session);
                session.inventory.setItem(slot, createPlayerItem(uuid, session.selected.contains(uuid)));
            }
            return;
        }

        // Nav row
        if (slot == SLOT_PREV) {
            if (session.page > 0) {
                session.page--;
                refreshPlayerSlots(session.inventory, session);
                fillNavRow(session.inventory, session);
                updateInfo(session.inventory, session);
            }
            return;
        }
        if (slot == SLOT_NEXT) {
            int totalPages = Math.max(1, (session.uuidList.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            if (session.page < totalPages - 1) {
                session.page++;
                refreshPlayerSlots(session.inventory, session);
                fillNavRow(session.inventory, session);
                updateInfo(session.inventory, session);
            }
            return;
        }
        if (slot == SLOT_BACK && session.onBack != null) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, session.onBack);
            return;
        }
        if (slot == SLOT_SELECT_ALL) {
            session.selected.addAll(session.uuidList);
            refreshPlayerSlots(session.inventory, session);
            updateInfo(session.inventory, session);
            return;
        }
        if (slot == SLOT_SELECT_PAGE) {
            int start = session.page * PAGE_SIZE;
            int end = Math.min(start + PAGE_SIZE, session.uuidList.size());
            for (int i = start; i < end; i++) {
                session.selected.add(session.uuidList.get(i));
            }
            refreshPlayerSlots(session.inventory, session);
            updateInfo(session.inventory, session);
            return;
        }
        if (slot == SLOT_CLEAR) {
            session.selected.clear();
            refreshPlayerSlots(session.inventory, session);
            updateInfo(session.inventory, session);
            return;
        }

        // Filter toggles
        if (slot == SLOT_FILTER_LATEST) {
            session.useLatest = !session.useLatest;
            updateFilterButtons(session.inventory, session);
            updateInfo(session.inventory, session);
            return;
        }
        if (slot == SLOT_FILTER_SURVIVAL) {
            session.onlySurvival = !session.onlySurvival;
            updateFilterButtons(session.inventory, session);
            updateInfo(session.inventory, session);
            return;
        }
        if (slot == SLOT_FILTER_RECENT) {
            if (session.recentHours == 0) session.recentHours = 24;
            else if (session.recentHours == 24) session.recentHours = 72;
            else session.recentHours = 0;
            updateFilterButtons(session.inventory, session);
            updateInfo(session.inventory, session);
            return;
        }

        if (slot == SLOT_CANCEL) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
            if (session.onBack != null) {
                Bukkit.getScheduler().runTask(plugin, session.onBack);
            }
            return;
        }

        if (slot == SLOT_SEND) {
            performBulkSend(player, session);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = sessions.get(player.getUniqueId());
        if (session != null && event.getInventory() == session.inventory) {
            sessions.remove(player.getUniqueId());
        }
    }

    public void removeSession(UUID playerId) {
        sessions.remove(playerId);
    }

    private void refreshPlayerSlots(Inventory gui, Session session) {
        for (int i = SLOT_PLAYERS_START; i <= SLOT_PLAYERS_END; i++) {
            gui.setItem(i, null);
        }
        int start = session.page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, session.uuidList.size());
        for (int i = start; i < end; i++) {
            String uuid = session.uuidList.get(i);
            int slot = SLOT_PLAYERS_START + (i - start);
            gui.setItem(slot, createPlayerItem(uuid, session.selected.contains(uuid)));
        }
    }

    private void fillNavRow(Inventory gui, Session session) {
        int totalPages = Math.max(1, (session.uuidList.size() + PAGE_SIZE - 1) / PAGE_SIZE);

        if (session.page > 0) {
            gui.setItem(SLOT_PREV, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.prev-page")));
        } else {
            gui.setItem(SLOT_PREV, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
        }
        if (session.page < totalPages - 1) {
            gui.setItem(SLOT_NEXT, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.next-page")));
        } else {
            gui.setItem(SLOT_NEXT, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
        }

        if (session.onBack != null) {
            gui.setItem(SLOT_BACK, createItem(Material.OAK_DOOR,
                    plugin.getLanguageManager().getGuiMessage("gui.common.back")));
        } else {
            gui.setItem(SLOT_BACK, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
        }

        gui.setItem(SLOT_SELECT_ALL, createItem(Material.LIME_DYE,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.select-all")));
        gui.setItem(SLOT_SELECT_PAGE, createItem(Material.PAPER,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.select-page")));
        gui.setItem(SLOT_CLEAR, createItem(Material.RED_DYE,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.clear")));

        for (int i = 43; i <= 44; i++) {
            gui.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
        }
    }

    private void performBulkSend(Player admin, Session session) {
        if (!admin.hasPermission("invbackup.admin")) {
            admin.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        if (session.selected.isEmpty()) {
            admin.sendMessage(plugin.getMessage("no-backups"));
            return;
        }

        int success = 0;
        int skipped = 0;
        String adminName = admin.getName();
        String adminUuid = admin.getUniqueId().toString();
        long now = System.currentTimeMillis();
        long minTime = session.recentHours > 0
                ? now - session.recentHours * 60L * 60L * 1000L
                : 0L;

        for (String uuid : session.selected) {
            List<BackupManager.BackupInfo> backups =
                    plugin.getBackupManager().listBackups(uuid, null);
            BackupManager.BackupInfo chosen = pickSnapshot(backups, session, minTime);
            if (chosen == null) {
                skipped++;
                continue;
            }
            String targetName = plugin.getIdentityManager().resolveName(uuid);
            if (targetName == null) {
                targetName = chosen.targetName != null ? chosen.targetName : uuid.substring(0, 8) + "...";
            }
            plugin.getRequestManager().createRequestForTarget(
                    uuid, targetName, chosen.snapshotId, adminName, adminUuid);
            Player target = Bukkit.getPlayer(UUID.fromString(uuid));
            if (target != null && target.isOnline()) {
                plugin.getRequestManager().notifyPlayer(target);
            }
            success++;
        }

        admin.closeInventory();
        sessions.remove(admin.getUniqueId());

        Component msg = plugin.getLanguageManager().getGuiMessage(
                "gui.bulk.result",
                "{sent}", String.valueOf(success),
                "{skipped}", String.valueOf(skipped),
                "{total}", String.valueOf(session.selected.size())
        );
        admin.sendMessage(msg);

        if (session.onBack != null) {
            Bukkit.getScheduler().runTask(plugin, session.onBack);
        }
    }

    private BackupManager.BackupInfo pickSnapshot(List<BackupManager.BackupInfo> backups,
                                                  Session session,
                                                  long minTime) {
        if (backups == null || backups.isEmpty()) return null;
        BackupManager.BackupInfo best = null;
        for (BackupManager.BackupInfo info : backups) {
            if (minTime > 0 && info.timestamp < minTime) continue;
            if (session.onlySurvival && !"full".equalsIgnoreCase(info.backupLevel)) continue;
            best = info;
            break;
        }
        if (best == null && session.useLatest) {
            best = backups.get(0);
        }
        return best;
    }

    private void updateFilterButtons(Inventory gui, Session session) {
        Component latestName = plugin.getLanguageManager().getGuiMessage("gui.bulk.filter.latest");
        Component survivalName = plugin.getLanguageManager().getGuiMessage("gui.bulk.filter.survival");
        Component stateOn = plugin.getLanguageManager().getGuiMessage("gui.bulk.filter.state-on");
        Component stateOff = plugin.getLanguageManager().getGuiMessage("gui.bulk.filter.state-off");

        gui.setItem(SLOT_FILTER_LATEST, createToggleItemWithLore(
                session.useLatest, latestName, stateOn, stateOff));
        gui.setItem(SLOT_FILTER_SURVIVAL, createToggleItemWithLore(
                session.onlySurvival, survivalName, stateOn, stateOff));

        ItemStack recent = new ItemStack(Material.CLOCK);
        ItemMeta meta = recent.getItemMeta();
        meta.displayName(session.recentHours == 0
                ? plugin.getLanguageManager().getGuiMessage("gui.bulk.filter.recent.any")
                : plugin.getLanguageManager().getGuiMessage("gui.bulk.filter.recent.hours",
                        "{hours}", String.valueOf(session.recentHours)));
        meta.lore(List.of(session.recentHours == 0 ? stateOff : stateOn));
        recent.setItemMeta(meta);
        gui.setItem(SLOT_FILTER_RECENT, recent);

        gui.setItem(SLOT_SEND, createItem(Material.EMERALD_BLOCK,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.send")));
        gui.setItem(SLOT_CANCEL, createItem(Material.BARRIER,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.cancel")));

        for (int i = 48; i <= 50; i++) {
            if (i != SLOT_SEND && i != SLOT_CANCEL) {
                gui.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
            }
        }
        gui.setItem(52, createItem(Material.GRAY_STAINED_GLASS_PANE, Component.text(" ")));
    }

    private void updateInfo(Inventory gui, Session session) {
        int totalPlayers = session.uuidList.size();
        int selectedCount = session.selected.size();

        String latestStr = session.useLatest
                ? plugin.getLanguageManager().getRawMessage("gui.bulk.filter.state-on")
                : plugin.getLanguageManager().getRawMessage("gui.bulk.filter.state-off");
        String survivalStr = session.onlySurvival
                ? plugin.getLanguageManager().getRawMessage("gui.bulk.filter.state-on")
                : plugin.getLanguageManager().getRawMessage("gui.bulk.filter.state-off");
        String recentStr = session.recentHours == 0
                ? plugin.getLanguageManager().getRawMessage("gui.bulk.filter.recent.any")
                : plugin.getLanguageManager().getRawMessage("gui.bulk.filter.recent.hours")
                        .replace("{hours}", String.valueOf(session.recentHours));

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getGuiMessage(
                "gui.bulk.info",
                "{selected}", String.valueOf(selectedCount),
                "{total}", String.valueOf(totalPlayers)
        ));
        meta.lore(List.of(
                plugin.getLanguageManager().getGuiMessage("gui.bulk.info-filter",
                        "{latest}", latestStr,
                        "{survival}", survivalStr,
                        "{recent}", recentStr)
        ));
        info.setItemMeta(meta);
        gui.setItem(SLOT_INFO, info);
    }

    private ItemStack createPlayerItem(String uuid, boolean selected) {
        String name = plugin.getIdentityManager().resolveName(uuid);
        if (name == null) name = uuid.substring(0, 8) + "...";
        Material mat = selected ? Material.LIME_STAINED_GLASS : Material.PLAYER_HEAD;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name));
        List<Component> lore = new ArrayList<>();
        lore.add(selected
                ? plugin.getLanguageManager().getGuiMessage("gui.bulk.player.selected")
                : plugin.getLanguageManager().getGuiMessage("gui.bulk.player.unselected"));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createToggleItemWithLore(boolean enabled, Component name, Component stateOn, Component stateOff) {
        Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(List.of(enabled ? stateOn : stateOff));
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

    private static class Session {
        Inventory inventory;
        List<String> uuidList = new ArrayList<>();
        Set<String> selected = new HashSet<>();
        int page;
        boolean useLatest;
        boolean onlySurvival;
        int recentHours;
        Runnable onBack;
    }
}
