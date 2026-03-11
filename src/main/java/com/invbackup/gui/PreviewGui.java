package com.invbackup.gui;

import com.invbackup.InvBackup;
import com.invbackup.manager.SerializationUtil;
import com.invbackup.request.RestoreRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
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
    private static final int SLOT_CROSS_RESTORE = 44;
    private static final int SLOT_TOGGLE_INVENTORY = 37;
    private static final int SLOT_TOGGLE_ARMOR = 38;
    private static final int SLOT_TOGGLE_EXP = 39;
    private static final int SLOT_TOGGLE_LOCATION = 40;
    private static final int SLOT_TOGGLE_ENDERCHEST = 41;
    private static final int SLOT_TOGGLE_HEALTH_FOOD = 42;
    private static final int SLOT_TOGGLE_EFFECTS = 43;

    private static final String PART_INVENTORY = "inventory";
    private static final String PART_ARMOR = "armor";
    private static final String PART_EXP = "exp";
    private static final String PART_LOCATION = "location";
    private static final String PART_ENDERCHEST = "enderchest";
    private static final String PART_HEALTH_FOOD = "health_food";
    private static final String PART_EFFECTS = "effects";

    private final InvBackup plugin;
    private final Map<UUID, PreviewSession> activeSessions = new HashMap<>();
    private final Map<UUID, CrossNameInputSession> nameInputSessions = new HashMap<>();

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

            if (viewer.hasPermission("invbackup.admin")) {
                gui.setItem(SLOT_TOGGLE_INVENTORY, createToggleItem(PART_INVENTORY, true));
                gui.setItem(SLOT_TOGGLE_ARMOR, createToggleItem(PART_ARMOR, true));
                gui.setItem(SLOT_TOGGLE_EXP, createToggleItem(PART_EXP, true));
                gui.setItem(SLOT_TOGGLE_LOCATION, createToggleItem(PART_LOCATION, false));
                gui.setItem(SLOT_TOGGLE_ENDERCHEST, createToggleItem(PART_ENDERCHEST, true));
                gui.setItem(SLOT_TOGGLE_HEALTH_FOOD, createToggleItem(PART_HEALTH_FOOD, false));
                gui.setItem(SLOT_TOGGLE_EFFECTS, createToggleItem(PART_EFFECTS, false));
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

                // Slot 53: Custom restore button
                if ("full".equals(config.getString("meta.backup-level"))) {
                    gui.setItem(SLOT_RESTORE_FULL, createItem(Material.LIME_WOOL,
                            plugin.getLanguageManager().getGuiMessage("gui.preview.full-restore.name"),
                            plugin.getLanguageManager().getGuiMessage("gui.preview.full-restore.lore")));
                }

                // Slot 44: Cross-player restore request (A -> B) via name input
                if (plugin.getConfig().getBoolean("restore-request.manual-name-input.enabled", false)) {
                    gui.setItem(SLOT_CROSS_RESTORE, createItem(Material.NAME_TAG,
                            plugin.getLanguageManager().getGuiMessage("gui.preview.cross-restore.name"),
                            plugin.getLanguageManager().getGuiMessage("gui.preview.cross-restore.lore1"),
                            plugin.getLanguageManager().getGuiMessage("gui.preview.cross-restore.lore2")));
                }
            }

        } catch (IOException e) {
            viewer.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        activeSessions.put(viewer.getUniqueId(),
                new PreviewSession(targetUuid, snapshotId, gui, onBack, defaultRestoreParts()));
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

        // Cross-player restore request from preview (admin only) — prompt for name in chat
        if (slot == SLOT_CROSS_RESTORE
                && player.hasPermission("invbackup.admin")
                && plugin.getConfig().getBoolean("restore-request.manual-name-input.enabled", false)) {
            startCrossRestoreChatSession(player, session);
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
            handleRestore(player, session, defaultRestoreParts());
            return;
        }

        // Restore custom
        if (slot == SLOT_RESTORE_FULL && player.hasPermission("invbackup.admin")) {
            handleRestore(player, session, session.selectedParts);
            return;
        }

        if (player.hasPermission("invbackup.admin")) {
            switch (slot) {
                case SLOT_TOGGLE_INVENTORY -> togglePart(player, session, PART_INVENTORY);
                case SLOT_TOGGLE_ARMOR -> togglePart(player, session, PART_ARMOR);
                case SLOT_TOGGLE_EXP -> togglePart(player, session, PART_EXP);
                case SLOT_TOGGLE_LOCATION -> togglePart(player, session, PART_LOCATION);
                case SLOT_TOGGLE_ENDERCHEST -> togglePart(player, session, PART_ENDERCHEST);
                case SLOT_TOGGLE_HEALTH_FOOD -> togglePart(player, session, PART_HEALTH_FOOD);
                case SLOT_TOGGLE_EFFECTS -> togglePart(player, session, PART_EFFECTS);
                default -> {
                }
            }
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
        nameInputSessions.remove(playerId);
    }

    private void handleRestore(Player viewer, PreviewSession session, List<String> allowedPartsRaw) {
        viewer.closeInventory();
        activeSessions.remove(viewer.getUniqueId());

        if (!viewer.hasPermission("invbackup.admin")) {
            viewer.sendMessage(plugin.getMessage("no-permission"));
            return;
        }

        List<String> allowedParts = normalizeParts(allowedPartsRaw);
        if (allowedParts.isEmpty()) {
            viewer.sendMessage(plugin.getLanguageManager()
                    .getGuiMessage("gui.preview.custom-restore-empty"));
            return;
        }

        UUID targetUuid = UUID.fromString(session.targetUuid);
        final String resolvedName = plugin.getIdentityManager().resolveName(targetUuid);
        final String targetName = resolvedName != null ? resolvedName : targetUuid.toString();

        String adminName = viewer.getName();
        String adminUuid = viewer.getUniqueId().toString();

        // Queue a restore request instead of restoring immediately.
        RestoreRequest request = plugin.getRequestManager().createRequestForTarget(
                targetUuid.toString(), targetName, session.snapshotId,
                adminName, adminUuid, allowedParts);

        Player target = Bukkit.getPlayer(targetUuid);
        if (target != null && target.isOnline()) {
            plugin.getRequestManager().notifyPlayer(target);
            Component revoke = plugin.getLanguageManager()
                    .getGuiMessage("request-revoke-button")
                    .clickEvent(ClickEvent.runCommand("/invbackup revoke " + request.requestId))
                    .hoverEvent(HoverEvent.showText(
                            plugin.getLanguageManager()
                                    .getGuiMessage("request-revoke-hover")));
            viewer.sendMessage(plugin.getMessage("request-sent")
                    .replaceText(b -> b.matchLiteral("{player}")
                            .replacement(target.getName()))
                    .append(Component.space())
                    .append(revoke));
        } else {
            viewer.sendMessage(plugin.getMessage("request-sent-offline")
                    .replaceText(b -> b.matchLiteral("{player}")
                            .replacement(targetName)));
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
            session = new PreviewSession(targetUuid, snapshotId, gui, onBack, null, defaultRestoreParts());
            activeSessions.put(viewer.getUniqueId(), session);
        } else {
            session.inventory = gui;
        }
        // Add a back arrow (to main preview if present, otherwise to onBack)
        gui.setItem(26, createItem(Material.ARROW,
                plugin.getLanguageManager().getGuiMessage("gui.common.back")));
        viewer.openInventory(gui);
    }

    private void startCrossRestoreChatSession(Player admin, PreviewSession session) {
        List<String> selected = normalizeParts(session.selectedParts);
        if (selected.isEmpty()) {
            admin.sendMessage(plugin.getLanguageManager()
                    .getGuiMessage("gui.preview.custom-restore-empty"));
            return;
        }

        admin.closeInventory();
        activeSessions.remove(admin.getUniqueId());

        CrossNameInputSession ns = new CrossNameInputSession();
        ns.sourceUuid = session.targetUuid;
        ns.snapshotId = session.snapshotId;
        ns.allowedParts = selected;
        nameInputSessions.put(admin.getUniqueId(), ns);

        admin.sendMessage(plugin.getLanguageManager().getGuiMessage("gui.preview.cross-restore.chat-prompt"));
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onCrossRestoreChat(AsyncPlayerChatEvent event) {
        Player admin = event.getPlayer();
        CrossNameInputSession ns = nameInputSessions.remove(admin.getUniqueId());
        if (ns == null) {
            return;
        }
        event.setCancelled(true);

        String name = event.getMessage().trim();
        if (name.isEmpty()) {
            admin.sendMessage(plugin.getMessage("player-not-found"));
            return;
        }
        if (name.equalsIgnoreCase("cancel")) {
            admin.sendMessage(plugin.getLanguageManager().getGuiMessage("gui.preview.cross-restore.chat-cancelled"));
            return;
        }

        // Resolve target: online first, then offline (allow queueing for offline players).
        java.util.UUID targetUuidObj = null;
        String targetName = null;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            targetUuidObj = online.getUniqueId();
            targetName = online.getName();
        } else {
            org.bukkit.OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
            if (offline.getUniqueId() != null) {
                targetUuidObj = offline.getUniqueId();
                targetName = offline.getName() != null ? offline.getName() : name;
            }
        }
        if (targetUuidObj == null || targetName == null) {
            admin.sendMessage(plugin.getMessage("player-not-found"));
            return;
        }
        String targetUuid = targetUuidObj.toString();

        String snapshotId = ns.snapshotId;
        if (snapshotId == null || snapshotId.isEmpty()) {
            admin.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        String sourceUuid = ns.sourceUuid;
        String sourceName = plugin.getIdentityManager().resolveName(java.util.UUID.fromString(sourceUuid));
        if (sourceName == null || sourceName.isEmpty()) {
            sourceName = sourceUuid;
        }

        String adminName = admin.getName();
        String adminUuid = admin.getUniqueId().toString();

        RestoreRequest request = plugin.getRequestManager().createRequest(
                sourceUuid, sourceName,
                targetUuid, targetName,
                snapshotId, adminName, adminUuid,
                ns.allowedParts);

        final String resolvedTargetName = targetName;
        final Player onlineRef = online;
        final String requestId = request.requestId;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (onlineRef != null && onlineRef.isOnline()) {
                plugin.getRequestManager().notifyPlayer(onlineRef);
                Component revoke = plugin.getLanguageManager()
                        .getGuiMessage("request-revoke-button")
                        .clickEvent(ClickEvent.runCommand("/invbackup revoke " + requestId))
                        .hoverEvent(HoverEvent.showText(
                                plugin.getLanguageManager()
                                        .getGuiMessage("request-revoke-hover")));
                admin.sendMessage(plugin.getMessage("request-sent")
                        .replaceText(b -> b.matchLiteral("{player}")
                                .replacement(onlineRef.getName()))
                        .append(Component.space())
                        .append(revoke));
            } else {
                admin.sendMessage(plugin.getMessage("request-sent-offline")
                        .replaceText(b -> b.matchLiteral("{player}")
                                .replacement(resolvedTargetName)));
            }
        });
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
        final List<String> selectedParts = new ArrayList<>();

        PreviewSession(String targetUuid, String snapshotId, Inventory inventory, Runnable onBack,
                       List<String> selectedParts) {
            this(targetUuid, snapshotId, inventory, onBack, inventory, selectedParts);
        }

        PreviewSession(String targetUuid, String snapshotId, Inventory inventory, Runnable onBack,
                       Inventory mainInventory, List<String> selectedParts) {
            this.targetUuid = targetUuid;
            this.snapshotId = snapshotId;
            this.inventory = inventory;
            this.mainInventory = mainInventory;
            this.onBack = onBack;
            this.selectedParts.addAll(selectedParts);
        }
    }

    private static class CrossNameInputSession {
        String sourceUuid;
        String snapshotId;
        List<String> allowedParts = new ArrayList<>();
    }

    private void togglePart(Player player, PreviewSession session, String part) {
        if (session.selectedParts.contains(part)) {
            session.selectedParts.remove(part);
        } else {
            session.selectedParts.add(part);
        }
        updateToggleButtons(session);
        player.updateInventory();
    }

    private void updateToggleButtons(PreviewSession session) {
        Inventory inv = session.inventory;
        if (inv == null) {
            return;
        }
        inv.setItem(SLOT_TOGGLE_INVENTORY, createToggleItem(PART_INVENTORY,
                session.selectedParts.contains(PART_INVENTORY)));
        inv.setItem(SLOT_TOGGLE_ARMOR, createToggleItem(PART_ARMOR,
                session.selectedParts.contains(PART_ARMOR)));
        inv.setItem(SLOT_TOGGLE_EXP, createToggleItem(PART_EXP,
                session.selectedParts.contains(PART_EXP)));
        inv.setItem(SLOT_TOGGLE_LOCATION, createToggleItem(PART_LOCATION,
                session.selectedParts.contains(PART_LOCATION)));
        inv.setItem(SLOT_TOGGLE_ENDERCHEST, createToggleItem(PART_ENDERCHEST,
                session.selectedParts.contains(PART_ENDERCHEST)));
        inv.setItem(SLOT_TOGGLE_HEALTH_FOOD, createToggleItem(PART_HEALTH_FOOD,
                session.selectedParts.contains(PART_HEALTH_FOOD)));
        inv.setItem(SLOT_TOGGLE_EFFECTS, createToggleItem(PART_EFFECTS,
                session.selectedParts.contains(PART_EFFECTS)));
    }

    private ItemStack createToggleItem(String part, boolean selected) {
        Material mat = selected ? Material.LIME_WOOL : toggleBaseMaterial(part);
        Component state = plugin.getLanguageManager().getGuiMessage(
                selected ? "gui.preview.custom-toggle.selected"
                        : "gui.preview.custom-toggle.unselected");
        return createItem(mat,
                plugin.getLanguageManager().getGuiMessage("gui.preview.custom-toggle." + part),
                state);
    }

    private Material toggleBaseMaterial(String part) {
        return switch (part) {
            case PART_INVENTORY -> Material.CHEST;
            case PART_ARMOR -> Material.IRON_CHESTPLATE;
            case PART_EXP -> Material.EXPERIENCE_BOTTLE;
            case PART_LOCATION -> Material.COMPASS;
            case PART_ENDERCHEST -> Material.ENDER_CHEST;
            case PART_HEALTH_FOOD -> Material.GOLDEN_APPLE;
            case PART_EFFECTS -> Material.POTION;
            default -> Material.PAPER;
        };
    }

    private static List<String> defaultRestoreParts() {
        return List.of(PART_INVENTORY, PART_ARMOR, PART_EXP, PART_ENDERCHEST);
    }

    private static List<String> normalizeParts(List<String> source) {
        List<String> normalized = new ArrayList<>();
        if (source == null) {
            return normalized;
        }
        for (String s : source) {
            if (s == null) continue;
            String key = s.trim().toLowerCase();
            if (key.isEmpty()) continue;
            if (!normalized.contains(key)) {
                normalized.add(key);
            }
        }
        return normalized;
    }
}
