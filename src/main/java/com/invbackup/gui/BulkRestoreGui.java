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
 * - Select players
 * - Configure snapshot picking options
 * - Send restore requests in batch
 */
public class BulkRestoreGui implements Listener {

    private static final int SLOT_PLAYERS_START = 0;
    private static final int SLOT_PLAYERS_END = 44;
    private static final int SLOT_INFO = 45;
    private static final int SLOT_FILTER_LATEST = 46;
    private static final int SLOT_FILTER_SURVIVAL = 47;
    private static final int SLOT_FILTER_RECENT = 48;
    private static final int SLOT_SEND = 51;
    private static final int SLOT_CANCEL = 53;

    private final InvBackup plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public BulkRestoreGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        Inventory gui = Bukkit.createInventory(null, 54,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.title"));

        // Preload all players with backups as selectable targets.
        Set<String> allUuids = plugin.getBackupManager().getAllPlayerUuids();
        List<String> uuidList = new ArrayList<>(allUuids);

        for (int i = 0; i < Math.min(uuidList.size(), SLOT_PLAYERS_END - SLOT_PLAYERS_START + 1); i++) {
            String uuid = uuidList.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            String name = plugin.getIdentityManager().resolveName(uuid);
            if (name == null) name = uuid.substring(0, 8) + "...";
            meta.displayName(Component.text(name));
            head.setItemMeta(meta);
            gui.setItem(SLOT_PLAYERS_START + i, head);
        }

        Session session = new Session();
        session.inventory = gui;
        session.uuidList = uuidList;
        session.selected = new HashSet<>();
        session.useLatest = true;
        session.onlySurvival = true;
        session.recentHours = 0;

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

        if (slot >= SLOT_PLAYERS_START && slot <= SLOT_PLAYERS_END) {
            int index = slot - SLOT_PLAYERS_START;
            if (index >= 0 && index < session.uuidList.size()) {
                String uuid = session.uuidList.get(index);
                if (session.selected.contains(uuid)) {
                    session.selected.remove(uuid);
                } else {
                    session.selected.add(uuid);
                }
                updateInfo(session.inventory, session);
            }
            return;
        }

        if (slot == SLOT_FILTER_LATEST) {
            session.useLatest = !session.useLatest;
            updateFilterButtons(session.inventory, session);
            return;
        }
        if (slot == SLOT_FILTER_SURVIVAL) {
            session.onlySurvival = !session.onlySurvival;
            updateFilterButtons(session.inventory, session);
            return;
        }
        if (slot == SLOT_FILTER_RECENT) {
            // Toggle between 0 / 24 / 72 hours for simplicity
            if (session.recentHours == 0) {
                session.recentHours = 24;
            } else if (session.recentHours == 24) {
                session.recentHours = 72;
            } else {
                session.recentHours = 0;
            }
            updateFilterButtons(session.inventory, session);
            return;
        }

        if (slot == SLOT_CANCEL) {
            sessions.remove(player.getUniqueId());
            player.closeInventory();
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
                targetName = chosen.targetName != null
                        ? chosen.targetName
                        : uuid.substring(0, 8) + "...";
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
    }

    private BackupManager.BackupInfo pickSnapshot(List<BackupManager.BackupInfo> backups,
                                                  Session session,
                                                  long minTime) {
        if (backups == null || backups.isEmpty()) return null;

        BackupManager.BackupInfo best = null;
        for (BackupManager.BackupInfo info : backups) {
            if (minTime > 0 && info.timestamp < minTime) {
                continue;
            }
            if (session.onlySurvival && !"full".equalsIgnoreCase(info.backupLevel)) {
                // Non-full backups don't have status.gamemode; we conservatively skip them
                continue;
            }
            // listBackups already returns newest-first; the first that matches wins
            best = info;
            break;
        }

        if (best == null && session.useLatest) {
            // Fallback: take newest regardless of filters
            best = backups.get(0);
        }
        return best;
    }

    private void updateFilterButtons(Inventory gui, Session session) {
        // Latest toggle
        gui.setItem(SLOT_FILTER_LATEST, createToggleItem(
                session.useLatest,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.filter.latest")));

        // Survival-only toggle
        gui.setItem(SLOT_FILTER_SURVIVAL, createToggleItem(
                session.onlySurvival,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.filter.survival")));

        // Recent-hours toggle
        String hoursText;
        if (session.recentHours == 0) {
            hoursText = plugin.getLanguageManager()
                    .getRawMessage("gui.bulk.filter.recent.any");
        } else {
            hoursText = plugin.getLanguageManager()
                    .getRawMessage("gui.bulk.filter.recent.hours")
                    .replace("{hours}", String.valueOf(session.recentHours));
        }
        ItemStack recent = new ItemStack(Material.CLOCK);
        ItemMeta meta = recent.getItemMeta();
        meta.displayName(Component.text(hoursText));
        recent.setItemMeta(meta);
        gui.setItem(SLOT_FILTER_RECENT, recent);

        // Send / cancel buttons
        gui.setItem(SLOT_SEND, createItem(Material.EMERALD_BLOCK,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.send")));
        gui.setItem(SLOT_CANCEL, createItem(Material.BARRIER,
                plugin.getLanguageManager().getGuiMessage("gui.bulk.cancel")));
    }

    private void updateInfo(Inventory gui, Session session) {
        int totalPlayers = session.uuidList.size();
        int selected = session.selected.size();

        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta meta = info.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getGuiMessage(
                "gui.bulk.info",
                "{selected}", String.valueOf(selected),
                "{total}", String.valueOf(totalPlayers)
        ));
        info.setItemMeta(meta);
        gui.setItem(SLOT_INFO, info);
    }

    private ItemStack createToggleItem(boolean enabled, Component name) {
        Material mat = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
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
        boolean useLatest;
        boolean onlySurvival;
        int recentHours;
    }
}

