package com.invbackup.gui;

import com.invbackup.InvBackup;
import com.invbackup.manager.RestoredTracker;
import com.invbackup.manager.SerializationUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RestoreGui implements Listener {

    // Row 6 layout
    private static final int SLOT_HELMET = 45;
    private static final int SLOT_CHEST = 46;
    private static final int SLOT_LEGS = 47;
    private static final int SLOT_BOOTS = 48;
    private static final int SLOT_OFFHAND = 49;
    private static final int SLOT_HEALTH = 36;
    private static final int SLOT_FOOD = 37;
    private static final int SLOT_EXP = 38;
    private static final int SLOT_LOCATION = 39;
    private static final int SLOT_EFFECTS = 40;
    private static final int SLOT_GAMEMODE = 41;
    private static final int SLOT_ENDERCHEST = 42;
    private static final int SLOT_RESTORE_ALL = 44;
    private static final int SLOT_NAME_INPUT = 43;

    private final InvBackup plugin;
    private final Map<UUID, RestoreSession> activeSessions = new HashMap<>();
    private final Map<UUID, NameInputSession> nameInputSessions = new HashMap<>();

    public RestoreGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    public void openRestoreGui(Player player, String targetUuid, String snapshotId) {
        YamlConfiguration config = plugin.getBackupManager()
                .loadBackupConfig(targetUuid, snapshotId);
        if (config == null) {
            player.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        boolean isAdmin = player.hasPermission("invbackup.admin");
        RestoredTracker tracker = plugin.getBackupManager()
                .getTracker(targetUuid, snapshotId);

        Component title = plugin.getLanguageManager().getGuiMessage("gui.restore.title");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        RestoreSession session = new RestoreSession(targetUuid, snapshotId, gui);

        try {
            // Row 1-4: Inventory content (slots 0-35)
            if (config.contains("inventory.content")) {
                ItemStack[] contents = SerializationUtil.itemStackArrayFromBase64(
                        config.getString("inventory.content"));
                for (int i = 0; i < Math.min(contents.length, 36); i++) {
                    if (contents[i] != null) {
                        if (!isAdmin && tracker.isSlotRestored(i)) {
                            gui.setItem(i, createClaimedMarker());
                        } else {
                            gui.setItem(i, contents[i]);
                            session.inventoryItems.put(i, contents[i].clone());
                        }
                    }
                }
            }

            // Row 5 slots 36-44: Status buttons + separator
            fillStatusButtons(gui, config, tracker, isAdmin);

            // Row 6 slots 45-49: Armor + offhand
            if (config.contains("inventory.armor")) {
                ItemStack[] armor = SerializationUtil.itemStackArrayFromBase64(
                        config.getString("inventory.armor"));
                // armor: boots[0], leggings[1], chestplate[2], helmet[3]
                int[] displaySlots = {SLOT_HELMET, SLOT_CHEST, SLOT_LEGS, SLOT_BOOTS};
                for (int i = 0; i < Math.min(armor.length, 4); i++) {
                    int displayIdx = 3 - i; // reverse for display
                    if (armor[i] != null) {
                        if (!isAdmin && tracker.isArmorRestored(i)) {
                            gui.setItem(displaySlots[displayIdx], createClaimedMarker());
                        } else {
                            gui.setItem(displaySlots[displayIdx], armor[i]);
                            session.armorItems.put(i, armor[i].clone());
                        }
                    }
                }
            }

            // Offhand
            if (config.contains("inventory.offhand")) {
                ItemStack[] offhand = SerializationUtil.itemStackArrayFromBase64(
                        config.getString("inventory.offhand"));
                if (offhand.length > 0 && offhand[0] != null) {
                    if (!isAdmin && tracker.isOffhandRestored()) {
                        gui.setItem(SLOT_OFFHAND, createClaimedMarker());
                    } else {
                        gui.setItem(SLOT_OFFHAND, offhand[0]);
                        session.offhandItem = offhand[0].clone();
                    }
                }
            }

            // Restore All button
            gui.setItem(SLOT_RESTORE_ALL, createItem(Material.EMERALD_BLOCK,
                    plugin.getLanguageManager().getGuiMessage("gui.restore.restore-all.name"),
                    plugin.getLanguageManager().getGuiMessage("gui.restore.restore-all.lore1"),
                    plugin.getLanguageManager().getGuiMessage("gui.restore.restore-all.lore2")));

            // Fill remaining empty slots in row 6 with glass
            for (int i = 50; i < 54; i++) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, createItem(Material.GRAY_STAINED_GLASS_PANE,
                            Component.text(" ")));
                }
            }

        } catch (IOException e) {
            player.sendMessage(plugin.getMessage("backup-not-found"));
            return;
        }

        session.config = config;
        activeSessions.put(player.getUniqueId(), session);
        player.openInventory(gui);
    }

    private void fillStatusButtons(Inventory gui, YamlConfiguration config,
                                   RestoredTracker tracker, boolean isAdmin) {
        boolean hasFull = config.contains("status");
        String na = plugin.getLanguageManager().getRawMessage("gui.common.na");

        // Health
        gui.setItem(SLOT_HEALTH, createStatusButton(
                Material.RED_DYE,
                plugin.getLanguageManager().getGuiMessage("gui.restore.status.health"),
                hasFull ? String.format("%.1f/%.1f",
                        config.getDouble("status.health", 0),
                        config.getDouble("status.max-health", 20)) : na,
                "health", tracker, isAdmin));

        // Food
        gui.setItem(SLOT_FOOD, createStatusButton(
                Material.BREAD,
                plugin.getLanguageManager().getGuiMessage("gui.restore.status.food"),
                hasFull ? String.valueOf(config.getInt("status.food", 0)) : na,
                "food", tracker, isAdmin));

        // Exp
        gui.setItem(SLOT_EXP, createStatusButton(
                Material.EXPERIENCE_BOTTLE,
                plugin.getLanguageManager().getGuiMessage("gui.restore.status.exp"),
                hasFull ? plugin.getLanguageManager().getRawMessage("gui.restore.status.exp-value")
                        .replace("{level}", String.valueOf(config.getInt("status.level", 0)))
                        : na,
                "exp", tracker, isAdmin));

        // Location
        String locStr = na;
        if (config.contains("status.location")) {
            locStr = String.format("%.0f, %.0f, %.0f",
                    config.getDouble("status.location.x"),
                    config.getDouble("status.location.y"),
                    config.getDouble("status.location.z"));
        }
        if (!hasFull) locStr = na;
        gui.setItem(SLOT_LOCATION, createStatusButton(
                Material.COMPASS,
                plugin.getLanguageManager().getGuiMessage("gui.restore.status.location"),
                locStr,
                "location", tracker, isAdmin));

        // Effects
        gui.setItem(SLOT_EFFECTS, createStatusButton(
                Material.POTION,
                plugin.getLanguageManager().getGuiMessage("gui.restore.status.effects"),
                hasFull ? plugin.getLanguageManager().getRawMessage("gui.restore.status.effects-value")
                        .replace("{count}", String.valueOf(config.getStringList("status.effects").size()))
                        : na,
                "effects", tracker, isAdmin));

        // Gamemode
        gui.setItem(SLOT_GAMEMODE, createStatusButton(
                Material.COMMAND_BLOCK,
                plugin.getLanguageManager().getGuiMessage("gui.restore.status.gamemode"),
                hasFull ? config.getString("status.gamemode", na) : na,
                "gamemode", tracker, isAdmin));

        // Enderchest
        gui.setItem(SLOT_ENDERCHEST, createItem(Material.ENDER_CHEST,
                plugin.getLanguageManager().getGuiMessage("gui.restore.enderchest.name"),
                plugin.getLanguageManager().getGuiMessage("gui.restore.enderchest.lore")));

        // Slot 43: optional manual name input (anvil)
        if (isAdmin && plugin.getConfig().getBoolean("restore-request.manual-name-input.enabled", false)) {
            gui.setItem(SLOT_NAME_INPUT, createItem(Material.NAME_TAG,
                    plugin.getLanguageManager().getGuiMessage("gui.restore.name-input.name"),
                    plugin.getLanguageManager().getGuiMessage("gui.restore.name-input.lore1"),
                    plugin.getLanguageManager().getGuiMessage("gui.restore.name-input.lore2")));
        } else {
            gui.setItem(SLOT_NAME_INPUT, createItem(Material.GRAY_STAINED_GLASS_PANE,
                    Component.text(" ")));
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Anvil name input handling
        NameInputSession nameSession = nameInputSessions.get(player.getUniqueId());
        if (nameSession != null &&
                event.getView().getTopInventory().getType() == org.bukkit.event.inventory.InventoryType.ANVIL) {
            handleNameInputClick(event, player, nameSession);
            return;
        }

        RestoreSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (event.getView().getTopInventory() != session.inventory) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return; // Player clicked their own inventory area
        }

        boolean isAdmin = player.hasPermission("invbackup.admin");
        RestoredTracker tracker = plugin.getBackupManager()
                .getTracker(session.targetUuid, session.snapshotId);

        // Inventory item claim (slots 0-35)
        if (slot >= 0 && slot < 36) {
            handleItemClaim(player, session, tracker, slot, isAdmin);
            return;
        }

        // Armor claim (slots 45-48)
        if (slot >= SLOT_HELMET && slot <= SLOT_BOOTS) {
            handleArmorClaim(player, session, tracker, slot, isAdmin);
            return;
        }

        // Offhand claim (slot 49)
        if (slot == SLOT_OFFHAND) {
            handleOffhandClaim(player, session, tracker, isAdmin);
            return;
        }

        // Status buttons
        if (slot == SLOT_HEALTH) {
            handleStatusRestore(player, session, tracker, "health", isAdmin);
        } else if (slot == SLOT_FOOD) {
            handleStatusRestore(player, session, tracker, "food", isAdmin);
        } else if (slot == SLOT_EXP) {
            handleStatusRestore(player, session, tracker, "exp", isAdmin);
        } else if (slot == SLOT_LOCATION) {
            handleStatusRestore(player, session, tracker, "location", isAdmin);
        } else if (slot == SLOT_EFFECTS) {
            handleStatusRestore(player, session, tracker, "effects", isAdmin);
        } else if (slot == SLOT_GAMEMODE) {
            handleStatusRestore(player, session, tracker, "gamemode", isAdmin);
        } else if (slot == SLOT_ENDERCHEST) {
            // Open ender chest sub-GUI (future: could be interactive too)
            activeSessions.remove(player.getUniqueId());
            player.closeInventory();
            plugin.getPreviewGui().openEnderChestPreview(
                    player, session.targetUuid, session.snapshotId,
                    () -> openRestoreGui(player, session.targetUuid, session.snapshotId));
        } else if (slot == SLOT_RESTORE_ALL) {
            handleRestoreAll(player, session, tracker, isAdmin);
        } else if (slot == SLOT_NAME_INPUT && isAdmin &&
                plugin.getConfig().getBoolean("restore-request.manual-name-input.enabled", false)) {
            openNameInputGui(player, session);
        }
    }

    private void handleItemClaim(Player player, RestoreSession session,
                                 RestoredTracker tracker, int slot, boolean isAdmin) {
        ItemStack item = session.inventoryItems.get(slot);
        if (item == null) {
            return;
        }

        if (!isAdmin && tracker.isSlotRestored(slot)) {
            player.sendMessage(plugin.getMessage("status-already-restored"));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(plugin.getMessage("inventory-full"));
            return;
        }

        player.getInventory().addItem(item.clone());
        tracker.markSlotRestored(slot);
        session.inventoryItems.remove(slot);

        // Update GUI
        event_setItem(player, slot, createClaimedMarker());
        player.sendMessage(plugin.getMessage("item-restored"));
    }

    private void handleArmorClaim(Player player, RestoreSession session,
                                  RestoredTracker tracker, int guiSlot, boolean isAdmin) {
        // Map GUI slot to armor index: helmet=45→3, chest=46→2, legs=47→1, boots=48→0
        int armorIndex = SLOT_BOOTS - guiSlot + 3;
        // Actually: 45→3, 46→2, 47→1, 48→0
        armorIndex = 3 - (guiSlot - SLOT_HELMET);

        ItemStack item = session.armorItems.get(armorIndex);
        if (item == null) {
            return;
        }

        if (!isAdmin && tracker.isArmorRestored(armorIndex)) {
            player.sendMessage(plugin.getMessage("status-already-restored"));
            return;
        }

        // Put armor directly on player, move existing to inventory
        ItemStack[] currentArmor = player.getInventory().getArmorContents();
        ItemStack existing = currentArmor[armorIndex];
        if (existing != null && existing.getType() != Material.AIR) {
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(plugin.getMessage("inventory-full"));
                return;
            }
            player.getInventory().addItem(existing);
        }

        currentArmor[armorIndex] = item.clone();
        player.getInventory().setArmorContents(currentArmor);
        tracker.markArmorRestored(armorIndex);
        session.armorItems.remove(armorIndex);

        event_setItem(player, guiSlot, createClaimedMarker());
        player.sendMessage(plugin.getMessage("item-restored"));
    }

    private void handleOffhandClaim(Player player, RestoreSession session,
                                    RestoredTracker tracker, boolean isAdmin) {
        ItemStack item = session.offhandItem;
        if (item == null) {
            return;
        }

        if (!isAdmin && tracker.isOffhandRestored()) {
            player.sendMessage(plugin.getMessage("status-already-restored"));
            return;
        }

        ItemStack existing = player.getInventory().getItemInOffHand();
        if (existing.getType() != Material.AIR) {
            if (player.getInventory().firstEmpty() == -1) {
                player.sendMessage(plugin.getMessage("inventory-full"));
                return;
            }
            player.getInventory().addItem(existing);
        }

        player.getInventory().setItemInOffHand(item.clone());
        tracker.markOffhandRestored();
        session.offhandItem = null;

        event_setItem(player, SLOT_OFFHAND, createClaimedMarker());
        player.sendMessage(plugin.getMessage("item-restored"));
    }

    private void handleStatusRestore(Player player, RestoreSession session,
                                     RestoredTracker tracker, String key,
                                     boolean isAdmin) {
        if (!session.config.contains("status")) {
            return;
        }

        if (!isAdmin && tracker.isStatusRestored(key)) {
            player.sendMessage(plugin.getMessage("status-already-restored"));
            return;
        }

        YamlConfiguration config = session.config;

        switch (key) {
            case "health" -> {
                player.setHealth(config.getDouble("status.health"));
                if (config.contains("status.saturation")) {
                    player.setSaturation(
                            (float) config.getDouble("status.saturation"));
                }
            }
            case "food" -> player.setFoodLevel(config.getInt("status.food"));
            case "exp" -> {
                player.setExp((float) config.getDouble("status.exp"));
                player.setLevel(config.getInt("status.level"));
            }
            case "location" -> {
                if (config.contains("status.location")) {
                    org.bukkit.World world = Bukkit.getWorld(
                            config.getString("status.location.world", ""));
                    if (world != null) {
                        player.closeInventory();
                        activeSessions.remove(player.getUniqueId());
                        player.teleport(new Location(world,
                                config.getDouble("status.location.x"),
                                config.getDouble("status.location.y"),
                                config.getDouble("status.location.z"),
                                (float) config.getDouble("status.location.yaw"),
                                (float) config.getDouble("status.location.pitch")));
                        tracker.markStatusRestored(key);
                        player.sendMessage(plugin.getMessage("status-restored")
                                .replaceText(b -> b.matchLiteral("{status}")
                                        .replacement(plugin.getLanguageManager()
                                                .getRawMessage("gui.restore.status.location-name"))));
                        return;
                    }
                }
            }
            case "effects" -> {
                for (PotionEffect eff : player.getActivePotionEffects()) {
                    player.removePotionEffect(eff.getType());
                }
                for (String effectStr : config.getStringList("status.effects")) {
                    String[] parts = effectStr.split(":");
                    if (parts.length == 3) {
                        PotionEffectType type = org.bukkit.Registry.EFFECT.get(
                                NamespacedKey.minecraft(parts[0]));
                        if (type != null) {
                            player.addPotionEffect(new PotionEffect(type,
                                    Integer.parseInt(parts[1]),
                                    Integer.parseInt(parts[2])));
                        }
                    }
                }
            }
            case "gamemode" -> {
                try {
                    player.setGameMode(GameMode.valueOf(
                            config.getString("status.gamemode")));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        tracker.markStatusRestored(key);
        player.sendMessage(plugin.getMessage("status-restored")
                .replaceText(b -> b.matchLiteral("{status}")
                        .replacement(plugin.getLanguageManager()
                                .getRawMessage("gui.restore.status." + key + "-name"))));
    }

    private void handleRestoreAll(Player player, RestoreSession session,
                                  RestoredTracker tracker, boolean isAdmin) {
        // First, auto-backup current inventory
        plugin.getBackupManager().saveBackup(
                player, "InvBackup", "CONSOLE", "auto",
                "Pre-restore backup");

        // Restore everything directly
        String restoreLevel = session.config.contains("status") ? "full" : "minimal";
        boolean success = plugin.getBackupManager().restoreFromConfig(
                player, session.config, restoreLevel);

        if (success) {
            // Mark everything as restored
            tracker.markAllRestored(36, 4,
                    session.offhandItem != null,
                    27);

            player.closeInventory();
            activeSessions.remove(player.getUniqueId());
            player.sendMessage(plugin.getMessage("all-restored"));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        RestoreSession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory) {
            event.setCancelled(true);
            return;
        }
        if (nameInputSessions.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        RestoreSession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getInventory() == session.inventory) {
            activeSessions.remove(player.getUniqueId());
        }
        nameInputSessions.remove(player.getUniqueId());
    }

    public void removeSession(UUID playerId) {
        activeSessions.remove(playerId);
        nameInputSessions.remove(playerId);
    }

    private void openNameInputGui(Player admin, RestoreSession session) {
        Inventory anvil = Bukkit.createInventory(admin,
                org.bukkit.event.inventory.InventoryType.ANVIL,
                plugin.getLanguageManager().getGuiMessage("gui.restore.name-input.title"));

        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getGuiMessage("gui.restore.name-input.placeholder"));
        paper.setItemMeta(meta);
        anvil.setItem(0, paper);

        NameInputSession ns = new NameInputSession();
        ns.config = session.config;
        nameInputSessions.put(admin.getUniqueId(), ns);

        admin.openInventory(anvil);
    }

    private void handleNameInputClick(InventoryClickEvent event,
                                      Player admin,
                                      NameInputSession ns) {
        if (event.getRawSlot() != 2) {
            // Only handle clicks on the output slot
            return;
        }
        event.setCancelled(true);

        ItemStack result = event.getCurrentItem();
        if (result == null || !result.hasItemMeta()) {
            return;
        }
        ItemMeta meta = result.getItemMeta();
        Component display = meta.displayName();
        String name = display != null ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(display) : null;
        if (name == null || name.isBlank()) {
            admin.sendMessage(plugin.getMessage("player-not-found"));
            return;
        }

        Player target = Bukkit.getPlayerExact(name.trim());
        if (target == null) {
            admin.sendMessage(plugin.getMessage("player-not-online"));
            return;
        }

        // Determine restore level based on snapshot meta
        String restoreLevel = ns.config != null && ns.config.contains("status")
                ? "full" : "minimal";

        boolean success = plugin.getBackupManager().restoreFromConfig(
                target, ns.config, restoreLevel);
        nameInputSessions.remove(admin.getUniqueId());
        admin.closeInventory();

        if (success) {
            admin.sendMessage(plugin.getMessage("backup-restored")
                    .replaceText(b -> b.matchLiteral("{player}")
                            .replacement(target.getName())));
        } else {
            admin.sendMessage(plugin.getMessage("backup-not-found"));
        }
    }

    private void event_setItem(Player player, int slot, ItemStack item) {
        Inventory topInv = player.getOpenInventory().getTopInventory();
        if (topInv != null && slot < topInv.getSize()) {
            topInv.setItem(slot, item);
        }
    }

    private ItemStack createClaimedMarker() {
        return createItem(Material.LIME_STAINED_GLASS_PANE,
                plugin.getLanguageManager().getGuiMessage("gui.restore.claimed"));
    }

    private ItemStack createStatusButton(Material material, Component name,
                                         String value, String key,
                                         RestoredTracker tracker, boolean isAdmin) {
        boolean restored = !isAdmin && tracker.isStatusRestored(key);
        NamedTextColor color = restored ? NamedTextColor.DARK_GRAY : NamedTextColor.AQUA;

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getGuiMessage(
                "gui.restore.status.value",
                "{value}", value
        ));
        if (restored) {
            lore.add(plugin.getLanguageManager().getGuiMessage("gui.restore.status.already-restored"));
        } else {
            lore.add(plugin.getLanguageManager().getGuiMessage("gui.restore.status.click-restore"));
        }

        Material display = restored ? Material.GRAY_DYE : material;
        ItemStack item = new ItemStack(display);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.color(color).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createItem(Material material, Component name,
                                 Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        if (lore.length > 0) {
            meta.lore(List.of(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private static class RestoreSession {
        final String targetUuid;
        final String snapshotId;
        final Map<Integer, ItemStack> inventoryItems = new HashMap<>();
        final Map<Integer, ItemStack> armorItems = new HashMap<>();
        ItemStack offhandItem;
        YamlConfiguration config;
        final Inventory inventory;

        RestoreSession(String targetUuid, String snapshotId, Inventory inventory) {
            this.targetUuid = targetUuid;
            this.snapshotId = snapshotId;
            this.inventory = inventory;
        }
    }

    private static class NameInputSession {
        YamlConfiguration config;
    }
}
