package com.invbackup.gui;

import com.invbackup.InvBackup;
import com.invbackup.manager.SerializationUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PreviewGui implements Listener {

    private static final int SLOT_BACK = 36;
    private static final int SLOT_RESTORE_MINIMAL = 52;
    private static final int SLOT_RESTORE_FULL = 53;
    private static final int SLOT_ENDERCHEST = 51;
    private static final int SLOT_STATUS = 50;

    private final InvBackup plugin;
    private final Map<UUID, PreviewSession> activeSessions = new HashMap<>();

    public PreviewGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    public void openPreview(Player viewer, String targetUuid, String snapshotId) {
        openPreview(viewer, targetUuid, snapshotId, null);
    }

    public void openPreview(Player viewer, String targetUuid, String snapshotId, Runnable onBack) {
        YamlConfiguration config = plugin.getBackupManager()
                .loadBackupConfig(targetUuid, snapshotId);
        if (config == null) {
            viewer.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        String targetName = config.getString("meta.target", "Unknown");
        long timestamp = config.getLong("meta.timestamp", 0);
        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));

        Component title = plugin.getLanguageManager().getGuiMessage(
                "gui.preview.title",
                "{player}", targetName,
                "{time}", timeStr
        );

        Inventory gui = Bukkit.createInventory(null, 54, title);

        try {
            // Row 1-4: Main inventory (slots 0-35)
            if (config.contains("inventory.content")) {
                ItemStack[] contents = SerializationUtil.itemStackArrayFromBase64(
                        config.getString("inventory.content"));
                for (int i = 0; i < Math.min(contents.length, 36); i++) {
                    if (contents[i] != null) {
                        gui.setItem(i, contents[i]);
                    }
                }
            }

            // Row 5: Separator
            ItemStack separator = createItem(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text(" "));
            int sepStart = 36;
            if (onBack != null) {
                gui.setItem(SLOT_BACK, createItem(Material.ARROW,
                        plugin.getLanguageManager().getGuiMessage("gui.common.back")));
                sepStart = 37;
            }
            for (int i = sepStart; i < 45; i++) {
                gui.setItem(i, separator);
            }

            // Row 6 slots 45-48: Armor (helmet, chestplate, leggings, boots)
            if (config.contains("inventory.armor")) {
                ItemStack[] armor = SerializationUtil.itemStackArrayFromBase64(
                        config.getString("inventory.armor"));
                // armor order: boots[0], leggings[1], chestplate[2], helmet[3]
                // display order: helmet, chestplate, leggings, boots
                if (armor.length >= 4) {
                    if (armor[3] != null) gui.setItem(45, armor[3]); // helmet
                    if (armor[2] != null) gui.setItem(46, armor[2]); // chestplate
                    if (armor[1] != null) gui.setItem(47, armor[1]); // leggings
                    if (armor[0] != null) gui.setItem(48, armor[0]); // boots
                }
            }

            // Slot 49: Offhand
            if (config.contains("inventory.offhand")) {
                ItemStack[] offhand = SerializationUtil.itemStackArrayFromBase64(
                        config.getString("inventory.offhand"));
                if (offhand.length > 0 && offhand[0] != null) {
                    gui.setItem(49, offhand[0]);
                }
            }

            // Slot 50: Status info
            if (config.contains("status")) {
                gui.setItem(SLOT_STATUS, createStatusItem(config));
            }

            // Slot 51: Ender chest indicator
            if (config.contains("inventory.enderchest")) {
                gui.setItem(SLOT_ENDERCHEST, createItem(Material.ENDER_CHEST,
                        plugin.getLanguageManager().getGuiMessage("gui.preview.enderchest.name"),
                        plugin.getLanguageManager().getGuiMessage("gui.preview.enderchest.lore")));
            }

            // Slot 52: Restore items only button
            if (viewer.hasPermission("invbackup.admin")) {
                gui.setItem(SLOT_RESTORE_MINIMAL, createItem(Material.YELLOW_WOOL,
                        plugin.getLanguageManager().getGuiMessage("gui.preview.restore-items.name"),
                        plugin.getLanguageManager().getGuiMessage("gui.preview.restore-items.lore")));

                // Slot 53: Full restore button
                if ("full".equals(config.getString("meta.backup-level"))) {
                    gui.setItem(SLOT_RESTORE_FULL, createItem(Material.LIME_WOOL,
                            plugin.getLanguageManager().getGuiMessage("gui.preview.full-restore.name"),
                            plugin.getLanguageManager().getGuiMessage("gui.preview.full-restore.lore")));
                }
            }

        } catch (IOException e) {
            viewer.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        activeSessions.put(viewer.getUniqueId(),
                new PreviewSession(targetUuid, snapshotId, gui, onBack));
        viewer.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        PreviewSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (event.getView().getTopInventory() != session.inventory) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return; // Player clicked their own inventory area
        }

        // Back (only present when opened with onBack)
        if (slot == SLOT_BACK && session.onBack != null) {
            player.closeInventory();
            activeSessions.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, session.onBack);
            return;
        }

        // Ender chest preview back (27 size preview)
        if (event.getView().getTopInventory().getSize() == 27 && slot == 26) {
            if (session.mainInventory != null) {
                session.inventory = session.mainInventory;
                player.openInventory(session.mainInventory);
            } else if (session.onBack != null) {
                player.closeInventory();
                activeSessions.remove(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, session.onBack);
            }
            return;
        }

        // Ender chest preview
        if (slot == SLOT_ENDERCHEST) {
            openEnderChestPreview(player, session.targetUuid, session.snapshotId, session.onBack);
            return;
        }

        // Restore minimal
        if (slot == SLOT_RESTORE_MINIMAL && player.hasPermission("invbackup.admin")) {
            handleRestore(player, session, "minimal");
            return;
        }

        // Restore full
        if (slot == SLOT_RESTORE_FULL && player.hasPermission("invbackup.admin")) {
            handleRestore(player, session, "full");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PreviewSession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        PreviewSession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getInventory() == session.inventory) {
            activeSessions.remove(player.getUniqueId());
        }
    }

    public void removeSession(UUID playerId) {
        activeSessions.remove(playerId);
    }

    private void handleRestore(Player viewer, PreviewSession session, String level) {
        viewer.closeInventory();
        activeSessions.remove(viewer.getUniqueId());

        Player target = Bukkit.getPlayer(UUID.fromString(session.targetUuid));
        if (target == null) {
            viewer.sendMessage(plugin.getMessage("player-not-online"));
            return;
        }

        boolean success = plugin.getBackupManager()
                .restoreBackup(target, session.targetUuid, session.snapshotId, level);
        if (success) {
            viewer.sendMessage(plugin.getMessage("backup-restored")
                    .replaceText(b -> b.matchLiteral("{player}")
                            .replacement(target.getName())));
        } else {
            viewer.sendMessage(plugin.getMessage("backup-not-found"));
        }
    }

    public void openEnderChestPreview(Player viewer, String targetUuid, String snapshotId) {
        openEnderChestPreview(viewer, targetUuid, snapshotId, null);
    }

    public void openEnderChestPreview(Player viewer, String targetUuid, String snapshotId, Runnable onBack) {
        YamlConfiguration config = plugin.getBackupManager()
                .loadBackupConfig(targetUuid, snapshotId);
        if (config == null || !config.contains("inventory.enderchest")) {
            return;
        }

        Component title = plugin.getLanguageManager().getGuiMessage("gui.preview.enderchest-preview.title");
        Inventory gui = Bukkit.createInventory(null, 27, title);

        try {
            ItemStack[] items = SerializationUtil.itemStackArrayFromBase64(
                    config.getString("inventory.enderchest"));
            for (int i = 0; i < Math.min(items.length, 27); i++) {
                if (items[i] != null) {
                    gui.setItem(i, items[i]);
                }
            }
        } catch (IOException e) {
            viewer.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        // Switch the tracked inventory so clicks remain cancelled and
        // we don't accidentally drop the session on inventory switch.
        PreviewSession session = activeSessions.get(viewer.getUniqueId());
        if (session == null) {
            // Opened from external GUI (e.g. RestoreGui) - create a lightweight session
            session = new PreviewSession(targetUuid, snapshotId, gui, onBack, null);
            activeSessions.put(viewer.getUniqueId(), session);
        } else {
            session.inventory = gui;
        }
        // Add a back arrow (to main preview if present, otherwise to onBack)
        gui.setItem(26, createItem(Material.ARROW,
                plugin.getLanguageManager().getGuiMessage("gui.common.back")));
        viewer.openInventory(gui);
    }

    private ItemStack createItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) {
            meta.lore(List.of(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createStatusItem(YamlConfiguration config) {
        ItemStack item = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getGuiMessage("gui.preview.status.name"));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.preview.status.health",
                "{cur}", String.format("%.1f", config.getDouble("status.health", 0)),
                "{max}", String.format("%.1f", config.getDouble("status.max-health", 20))
        ));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.preview.status.food",
                "{food}", String.valueOf(config.getInt("status.food", 0))
        ));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.preview.status.level",
                "{level}", String.valueOf(config.getInt("status.level", 0))
        ));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.preview.status.exp",
                "{exp}", String.format("%.2f", config.getDouble("status.exp", 0))
        ));
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.preview.status.gamemode",
                "{gamemode}", config.getString("status.gamemode", "N/A")
        ));

        if (config.contains("status.location")) {
            String loc = String.format("%.1f, %.1f, %.1f (%s)",
                    config.getDouble("status.location.x"),
                    config.getDouble("status.location.y"),
                    config.getDouble("status.location.z"),
                    config.getString("status.location.world", "?"));
            lore.add(plugin.getLanguageManager().getGuiMessage(
                    "gui.preview.status.location",
                    "{location}", loc
            ));
        }

        List<String> effects = config.getStringList("status.effects");
        if (!effects.isEmpty()) {
            lore.add(plugin.getLanguageManager().getGuiMessage("gui.preview.status.effects.header"));
            for (String effect : effects) {
                lore.add(plugin.getLanguageManager().getGuiMessage(
                        "gui.preview.status.effects.item",
                        "{effect}", effect
                ));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static class PreviewSession {
        final String targetUuid;
        final String snapshotId;
        Inventory inventory;
        final Inventory mainInventory;
        final Runnable onBack;

        PreviewSession(String targetUuid, String snapshotId, Inventory inventory, Runnable onBack) {
            this(targetUuid, snapshotId, inventory, onBack, inventory);
        }

        PreviewSession(String targetUuid, String snapshotId, Inventory inventory, Runnable onBack, Inventory mainInventory) {
            this.targetUuid = targetUuid;
            this.snapshotId = snapshotId;
            this.inventory = inventory;
            this.mainInventory = mainInventory;
            this.onBack = onBack;
        }
    }
}
