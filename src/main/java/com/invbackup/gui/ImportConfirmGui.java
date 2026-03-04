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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Import confirmation GUI: preview + select snapshots + confirm import.
 *
 * This GUI never performs import automatically; it only does so after explicit confirmation.
 */
public class ImportConfirmGui implements Listener {

    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_NEXT = 53;

    // Action buttons (nav row)
    private static final int SLOT_SELECT_ALL = 46;
    private static final int SLOT_CLEAR = 47;
    private static final int SLOT_IMPORT_SELECTED = 51;
    private static final int SLOT_IMPORT_ALL = 52;

    private final InvBackup plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ImportConfirmGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, BackupManager.ImportSource source) {
        List<BackupManager.ImportEntry> entries = plugin.getBackupManager()
                .collectImportEntries(source);
        openEntries(viewer, source, entries, 0, new HashSet<>());
    }

    private void openEntries(Player viewer,
                             BackupManager.ImportSource source,
                             List<BackupManager.ImportEntry> entries,
                             int page,
                             Set<String> selected) {
        int totalPages = Math.max(1,
                (int) Math.ceil((double) entries.size() / PAGE_SIZE));
        page = Math.max(0, Math.min(page, totalPages - 1));

        Component title = plugin.getLanguageManager().getGuiMessage(
                "gui.import.title",
                "{source}", source.displayName()
        );

        Inventory gui = Bukkit.createInventory(null, 54, title);

        int start = page * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, entries.size());

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        for (int i = start; i < end; i++) {
            BackupManager.ImportEntry e = entries.get(i);
            gui.setItem(i - start, createEntryItem(e, sdf, selected.contains(e.key())));
        }

        fillNav(gui, page, totalPages, source, entries.size(), selected.size());

        Session session = sessions.getOrDefault(viewer.getUniqueId(), new Session());
        session.inventory = gui;
        session.source = source;
        session.entries = entries;
        session.page = page;
        session.selectedKeys = selected;
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

        // Paging
        if (slot == SLOT_PREV) {
            openEntries(player, session.source, session.entries,
                    session.page - 1, session.selectedKeys);
            return;
        }
        if (slot == SLOT_NEXT) {
            openEntries(player, session.source, session.entries,
                    session.page + 1, session.selectedKeys);
            return;
        }

        // Actions
        if (slot == SLOT_SELECT_ALL) {
            for (BackupManager.ImportEntry e : session.entries) {
                session.selectedKeys.add(e.key());
            }
            openEntries(player, session.source, session.entries,
                    session.page, session.selectedKeys);
            return;
        }
        if (slot == SLOT_CLEAR) {
            session.selectedKeys.clear();
            openEntries(player, session.source, session.entries,
                    session.page, session.selectedKeys);
            return;
        }
        if (slot == SLOT_IMPORT_ALL) {
            confirmAndImport(player, session.source, session.entries);
            return;
        }
        if (slot == SLOT_IMPORT_SELECTED) {
            List<BackupManager.ImportEntry> selected = new ArrayList<>();
            for (BackupManager.ImportEntry e : session.entries) {
                if (session.selectedKeys.contains(e.key())) selected.add(e);
            }
            confirmAndImport(player, session.source, selected);
            return;
        }

        // Entry area (0-44)
        if (slot < PAGE_SIZE) {
            int index = session.page * PAGE_SIZE + slot;
            if (index >= 0 && index < session.entries.size()) {
                BackupManager.ImportEntry e = session.entries.get(index);

                // Shift-click: preview
                if (event.isShiftClick()) {
                    BackupManager.ImportSource src = session.source;
                    List<BackupManager.ImportEntry> entries = session.entries;
                    int page = session.page;
                    Set<String> selected = new HashSet<>(session.selectedKeys);

                    sessions.remove(player.getUniqueId());
                    plugin.getPreviewGui().openPreview(player, e.targetUuid, e.snapshotId,
                            () -> openEntries(player, src, entries, page, selected));
                    return;
                }

                // Normal click: toggle selection
                String key = e.key();
                if (session.selectedKeys.contains(key)) {
                    session.selectedKeys.remove(key);
                } else {
                    session.selectedKeys.add(key);
                }
                openEntries(player, session.source, session.entries,
                        session.page, session.selectedKeys);
            }
        }
    }

    private void confirmAndImport(Player viewer,
                                  BackupManager.ImportSource source,
                                  List<BackupManager.ImportEntry> entriesToImport) {
        if (entriesToImport == null || entriesToImport.isEmpty()) {
            viewer.sendMessage(plugin.getMessage("no-backups"));
            return;
        }

        // Do the import immediately after the explicit click on import buttons.
        // (Two-step "are you sure?" can be added later if desired.)
        String triggerName = viewer.getName();
        String triggerUuid = viewer.getUniqueId().toString();
        int imported = plugin.getBackupManager().importEntriesToHistory(
                source, entriesToImport, triggerName, triggerUuid);

        viewer.closeInventory();
        sessions.remove(viewer.getUniqueId());

        viewer.sendMessage(plugin.getMessage("import-success")
                .replaceText(b -> b.matchLiteral("{count}")
                        .replacement(String.valueOf(imported)))
                .replaceText(b -> b.matchLiteral("{folder}")
                        .replacement(source.displayName())));
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

    private void fillNav(Inventory gui, int page, int totalPages,
                         BackupManager.ImportSource source,
                         int totalEntries, int selectedCount) {
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE,
                    Component.text(" ")));
        }

        gui.setItem(SLOT_INFO, createItem(Material.PAPER,
                plugin.getLanguageManager().getGuiMessage(
                        "gui.import.info",
                        "{page}", String.valueOf(page + 1),
                        "{total}", String.valueOf(totalPages),
                        "{entries}", String.valueOf(totalEntries),
                        "{selected}", String.valueOf(selectedCount),
                        "{source}", source.displayName()
                )));

        if (page > 0) {
            gui.setItem(SLOT_PREV, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.prev-page")));
        }
        if (page < totalPages - 1) {
            gui.setItem(SLOT_NEXT, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.next-page")));
        }

        gui.setItem(SLOT_SELECT_ALL, createItem(Material.LIME_DYE,
                plugin.getLanguageManager().getGuiMessage("gui.import.select-all")));
        gui.setItem(SLOT_CLEAR, createItem(Material.RED_DYE,
                plugin.getLanguageManager().getGuiMessage("gui.import.clear-selection")));

        gui.setItem(SLOT_IMPORT_SELECTED, createItem(Material.CHEST_MINECART,
                plugin.getLanguageManager().getGuiMessage("gui.import.import-selected")));
        gui.setItem(SLOT_IMPORT_ALL, createItem(Material.CHEST,
                plugin.getLanguageManager().getGuiMessage("gui.import.import-all")));
    }

    private ItemStack createEntryItem(BackupManager.ImportEntry e,
                                      SimpleDateFormat sdf,
                                      boolean selected) {
        Material mat = selected ? Material.LIME_STAINED_GLASS : Material.PAPER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String time = e.timestamp > 0 ? sdf.format(e.timestamp) : "?";
        Component name = plugin.getLanguageManager().getGuiMessage(
                "gui.import.entry.name",
                "{player}", e.targetName,
                "{snapshot}", e.snapshotId
        );

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.import.entry.time", "{time}", time));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.import.entry.format", "{format}", e.format));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.import.entry.toggle"));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.import.entry.preview"));

        meta.displayName(name);
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

    private static class Session {
        Inventory inventory;
        BackupManager.ImportSource source;
        List<BackupManager.ImportEntry> entries = Collections.emptyList();
        int page;
        Set<String> selectedKeys = new HashSet<>();
    }
}

