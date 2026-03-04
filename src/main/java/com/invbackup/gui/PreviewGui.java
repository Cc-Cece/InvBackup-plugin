package com.invbackup.gui;

import com.invbackup.InvBackup;
import com.invbackup.manager.SerializationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
        YamlConfiguration config = plugin.getBackupManager()
                .loadBackupConfig(targetUuid, snapshotId);
        if (config == null) {
            viewer.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        String targetName = config.getString("meta.target", "Unknown");
        long timestamp = config.getLong("meta.timestamp", 0);
        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));

        Component title = Component.text("Backup: " + targetName + " | " + timeStr)
                .color(NamedTextColor.DARK_AQUA);

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
            for (int i = 36; i < 45; i++) {
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
                        Component.text("Ender Chest", NamedTextColor.DARK_PURPLE)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Click to preview", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)));
            }

            // Slot 52: Restore items only button
            if (viewer.hasPermission("invbackup.admin")) {
                gui.setItem(SLOT_RESTORE_MINIMAL, createItem(Material.YELLOW_WOOL,
                        Component.text("Restore Items Only", NamedTextColor.YELLOW)
                                .decoration(TextDecoration.ITALIC, false),
                        Component.text("Only restores inventory", NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)));

                // Slot 53: Full restore button
                if ("full".equals(config.getString("meta.backup-level"))) {
                    gui.setItem(SLOT_RESTORE_FULL, createItem(Material.LIME_WOOL,
                            Component.text("Full Restore", NamedTextColor.GREEN)
                                    .decoration(TextDecoration.ITALIC, false),
                            Component.text("Restores items + status + location",
                                    NamedTextColor.GRAY)
                                    .decoration(TextDecoration.ITALIC, false)));
                }
            }

        } catch (IOException e) {
            viewer.sendMessage(Component.text(
                    "Failed to load backup data.", NamedTextColor.RED));
            return;
        }

        activeSessions.put(viewer.getUniqueId(),
                new PreviewSession(targetUuid, snapshotId));
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

        event.setCancelled(true);

        int slot = event.getRawSlot();

        // Ender chest preview
        if (slot == SLOT_ENDERCHEST) {
            openEnderChestPreview(player, session.targetUuid, session.snapshotId);
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
        if (activeSessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
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
        YamlConfiguration config = plugin.getBackupManager()
                .loadBackupConfig(targetUuid, snapshotId);
        if (config == null || !config.contains("inventory.enderchest")) {
            return;
        }

        Component title = Component.text("Ender Chest Preview")
                .color(NamedTextColor.DARK_PURPLE);
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
            viewer.sendMessage(Component.text(
                    "Failed to load enderchest data.", NamedTextColor.RED));
            return;
        }

        // Keep the session active for ender chest preview
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
        meta.displayName(Component.text("Player Status", NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Health: "
                        + String.format("%.1f", config.getDouble("status.health", 0))
                        + "/" + String.format("%.1f", config.getDouble("status.max-health", 20)),
                NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Food: " + config.getInt("status.food", 0),
                NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Level: " + config.getInt("status.level", 0),
                NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Exp: " + String.format("%.2f", config.getDouble("status.exp", 0)),
                NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Gamemode: "
                        + config.getString("status.gamemode", "N/A"),
                NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        if (config.contains("status.location")) {
            String loc = String.format("%.1f, %.1f, %.1f (%s)",
                    config.getDouble("status.location.x"),
                    config.getDouble("status.location.y"),
                    config.getDouble("status.location.z"),
                    config.getString("status.location.world", "?"));
            lore.add(Component.text("Location: " + loc,
                    NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        }

        List<String> effects = config.getStringList("status.effects");
        if (!effects.isEmpty()) {
            lore.add(Component.text("Effects:", NamedTextColor.LIGHT_PURPLE)
                    .decoration(TextDecoration.ITALIC, false));
            for (String effect : effects) {
                lore.add(Component.text("  " + effect, NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static class PreviewSession {
        final String targetUuid;
        final String snapshotId;

        PreviewSession(String targetUuid, String snapshotId) {
            this.targetUuid = targetUuid;
            this.snapshotId = snapshotId;
        }
    }
}
