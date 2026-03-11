package com.invbackup.gui;

import com.invbackup.InvBackup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CategoryGui implements Listener {

    private static final int SLOT_ONLINE = 10;
    private static final int SLOT_RECENT = 12;
    private static final int SLOT_HISTORY = 14;
    private static final int SLOT_INACTIVE = 16;

    private static final int SLOT_NEVER = 28;
    private static final int SLOT_WITH_BACKUPS = 30;
    private static final int SLOT_NO_BACKUPS = 32;

    private static final int SLOT_SEARCH = 48;
    private static final int SLOT_ALL_PLAYERS = 49;
    private static final int SLOT_CLOSE = 50;

    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_SORT = 50;
    private static final int SLOT_NEXT = 53;

    private final InvBackup plugin;
    private final Map<UUID, CategorySession> activeSessions = new HashMap<>();

    public CategoryGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    public void openCategoryMenu(Player viewer) {
        Component title = plugin.getLanguageManager().getGuiMessage("ui.category.title");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        fillBorder(gui);

        List<PlayerProfile> profiles = collectPlayerProfiles();
        Map<String, Integer> counts = calculateCategoryCounts(profiles);

        gui.setItem(SLOT_ONLINE, createCategoryItem(
                Material.EMERALD_BLOCK, "ui.category.label.online", counts.getOrDefault("online", 0),
                NamedTextColor.GREEN, List.of("ui.category.desc.online")));

        gui.setItem(SLOT_RECENT, createCategoryItem(
                Material.GOLD_BLOCK, "ui.category.label.recent", counts.getOrDefault("recent", 0),
                NamedTextColor.GOLD, List.of("ui.category.desc.recent")));

        gui.setItem(SLOT_HISTORY, createCategoryItem(
                Material.IRON_BLOCK, "ui.category.label.history", counts.getOrDefault("history", 0),
                NamedTextColor.AQUA, List.of("ui.category.desc.history")));

        gui.setItem(SLOT_INACTIVE, createCategoryItem(
                Material.STONE, "ui.category.label.inactive", counts.getOrDefault("inactive", 0),
                NamedTextColor.YELLOW, List.of("ui.category.desc.inactive")));

        gui.setItem(SLOT_NEVER, createCategoryItem(
                Material.BARRIER, "ui.category.label.never-online", counts.getOrDefault("never", 0),
                NamedTextColor.LIGHT_PURPLE, List.of("ui.category.desc.never-online")));

        gui.setItem(SLOT_WITH_BACKUPS, createCategoryItem(
                Material.BOOKSHELF, "ui.category.label.with-backups", counts.getOrDefault("withBackups", 0),
                NamedTextColor.AQUA, List.of("ui.category.desc.with-backups")));

        gui.setItem(SLOT_NO_BACKUPS, createCategoryItem(
                Material.CHEST, "ui.category.label.no-backups", counts.getOrDefault("noBackups", 0),
                NamedTextColor.RED, List.of("ui.category.desc.no-backups")));

        gui.setItem(SLOT_SEARCH, createFunctionItem(
                Material.COMPASS, "ui.category.label.search", NamedTextColor.AQUA, List.of("ui.category.desc.search")));

        gui.setItem(SLOT_ALL_PLAYERS, createFunctionItem(
                Material.PLAYER_HEAD, "ui.category.label.all-players", NamedTextColor.WHITE,
                List.of("ui.category.desc.all-players")));

        gui.setItem(SLOT_CLOSE, createFunctionItem(
                Material.BARRIER, "ui.category.label.close", NamedTextColor.RED, List.of("ui.category.desc.close")));

        CategorySession session = new CategorySession();
        session.mode = SessionMode.CATEGORY_MENU;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);

        viewer.openInventory(gui);
    }

    public void openPlayerListByCategory(Player viewer, String category, int page) {
        openPlayerListByCategory(viewer, category, page, PlayerSortMode.NAME_ASC);
    }

    public void openPlayerListByCategory(Player viewer, String category, int page, PlayerSortMode sortMode) {
        List<PlayerProfile> profiles = collectPlayerProfiles();
        List<String> filtered = filterPlayersByCategory(profiles, category, sortMode);

        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / 45.0));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        Component title = plugin.getLanguageManager().getGuiMessage(
                "ui.category.player-list-title",
                "{category}", getCategoryName(category),
                "{page}", String.valueOf(safePage + 1),
                "{total}", String.valueOf(totalPages)
        );

        Inventory gui = Bukkit.createInventory(null, 54, title);

        int start = safePage * 45;
        int end = Math.min(start + 45, filtered.size());
        for (int i = start; i < end; i++) {
            gui.setItem(i - start, createPlayerHead(filtered.get(i)));
        }

        fillNavRow(gui, safePage, totalPages, sortMode);
        gui.setItem(SLOT_BACK, createItem(Material.ARROW,
                plugin.getLanguageManager().getGuiMessage("gui.common.back")));

        CategorySession session = new CategorySession();
        session.mode = SessionMode.PLAYER_LIST;
        session.category = category;
        session.page = safePage;
        session.sortMode = sortMode;
        session.filteredUuids = filtered;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);

        viewer.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        CategorySession session = activeSessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }

        if (session.mode == SessionMode.CATEGORY_MENU) {
            handleCategoryMenuClick(player, slot);
        } else {
            handlePlayerListClick(player, session, slot);
        }
    }

    private void handleCategoryMenuClick(Player player, int slot) {
        switch (slot) {
            case SLOT_ONLINE -> openPlayerListByCategory(player, "online", 0);
            case SLOT_RECENT -> openPlayerListByCategory(player, "recent", 0);
            case SLOT_HISTORY -> openPlayerListByCategory(player, "history", 0);
            case SLOT_INACTIVE -> openPlayerListByCategory(player, "inactive", 0);
            case SLOT_NEVER -> openPlayerListByCategory(player, "never", 0);
            case SLOT_WITH_BACKUPS -> openPlayerListByCategory(player, "withBackups", 0);
            case SLOT_NO_BACKUPS -> openPlayerListByCategory(player, "noBackups", 0);
            case SLOT_SEARCH -> {
                activeSessions.remove(player.getUniqueId());
                plugin.getSearchGui().openSearchMenu(player);
            }
            case SLOT_ALL_PLAYERS -> {
                activeSessions.remove(player.getUniqueId());
                plugin.getAdminGui().openPlayerList(player, 0);
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handlePlayerListClick(Player player, CategorySession session, int slot) {
        int totalPages = Math.max(1, (int) Math.ceil(session.filteredUuids.size() / 45.0));

        if (slot == SLOT_BACK) {
            openCategoryMenu(player);
            return;
        }

        if (slot == SLOT_SORT) {
            openPlayerListByCategory(player, session.category, 0, session.sortMode.next());
            return;
        }

        if (slot == SLOT_PREV && session.page > 0) {
            openPlayerListByCategory(player, session.category, session.page - 1, session.sortMode);
            return;
        }

        if (slot == SLOT_NEXT && session.page < totalPages - 1) {
            openPlayerListByCategory(player, session.category, session.page + 1, session.sortMode);
            return;
        }

        int index = session.page * 45 + slot;
        if (slot < 45 && index < session.filteredUuids.size()) {
            String uuid = session.filteredUuids.get(index);
            activeSessions.remove(player.getUniqueId());
            plugin.getAdminGui().openBackupList(player, uuid, 0);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        CategorySession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        CategorySession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getInventory() == session.inventory) {
            activeSessions.remove(player.getUniqueId());
        }
    }

    public void removeSession(UUID playerId) {
        activeSessions.remove(playerId);
    }

    private List<PlayerProfile> collectPlayerProfiles() {
        Set<String> allUuids = new LinkedHashSet<>(plugin.getBackupManager().getAllPlayerUuids());

        for (Player online : Bukkit.getOnlinePlayers()) {
            allUuids.add(online.getUniqueId().toString());
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getUniqueId() != null) {
                allUuids.add(offline.getUniqueId().toString());
            }
        }

        List<PlayerProfile> profiles = new ArrayList<>();
        for (String uuidStr : allUuids) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException ignored) {
                continue;
            }

            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            String name = plugin.getIdentityManager().resolveName(uuid);
            if (name == null || name.isBlank()) {
                name = uuidStr;
            }

            PlayerProfile profile = new PlayerProfile();
            profile.uuid = uuidStr;
            profile.name = name;
            profile.online = op.isOnline();
            profile.hasPlayedBefore = op.hasPlayedBefore();
            profile.lastPlayed = op.getLastPlayed();
            profile.backupCount = plugin.getBackupManager().listBackups(uuidStr, null).size();
            profiles.add(profile);
        }

        return profiles;
    }

    private Map<String, Integer> calculateCategoryCounts(List<PlayerProfile> profiles) {
        Map<String, Integer> counts = new HashMap<>();
        counts.put("online", 0);
        counts.put("recent", 0);
        counts.put("history", 0);
        counts.put("inactive", 0);
        counts.put("never", 0);
        counts.put("withBackups", 0);
        counts.put("noBackups", 0);

        long now = System.currentTimeMillis();
        long oneDayMs = 24L * 60 * 60 * 1000;

        for (PlayerProfile p : profiles) {
            if (p.backupCount > 0) {
                counts.computeIfPresent("withBackups", (k, v) -> v + 1);
            } else {
                counts.computeIfPresent("noBackups", (k, v) -> v + 1);
            }

            if (p.online) {
                counts.computeIfPresent("online", (k, v) -> v + 1);
                continue;
            }

            if (!p.hasPlayedBefore) {
                counts.computeIfPresent("never", (k, v) -> v + 1);
                continue;
            }

            long daysSince = (now - p.lastPlayed) / oneDayMs;
            if (daysSince < 2) {
                counts.computeIfPresent("recent", (k, v) -> v + 1);
            } else if (daysSince < 7) {
                counts.computeIfPresent("history", (k, v) -> v + 1);
            } else {
                counts.computeIfPresent("inactive", (k, v) -> v + 1);
            }
        }

        return counts;
    }

    private List<String> filterPlayersByCategory(List<PlayerProfile> profiles, String category,
                                                 PlayerSortMode sortMode) {
        List<String> result = new ArrayList<>();
        Map<String, PlayerProfile> profileByUuid = new HashMap<>();
        long now = System.currentTimeMillis();
        long oneDayMs = 24L * 60 * 60 * 1000;

        for (PlayerProfile p : profiles) {
            profileByUuid.put(p.uuid, p);
            long daysSince = p.hasPlayedBefore ? (now - p.lastPlayed) / oneDayMs : Long.MAX_VALUE;

            boolean match = switch (category) {
                case "online" -> p.online;
                case "recent" -> !p.online && p.hasPlayedBefore && daysSince < 2;
                case "history" -> !p.online && p.hasPlayedBefore && daysSince >= 2 && daysSince < 7;
                case "inactive" -> !p.online && p.hasPlayedBefore && daysSince >= 7;
                case "never" -> !p.hasPlayedBefore;
                case "withBackups" -> p.backupCount > 0;
                case "noBackups" -> p.backupCount == 0;
                default -> false;
            };

            if (match) {
                result.add(p.uuid);
            }
        }

        switch (sortMode) {
            case NAME_ASC -> result.sort(Comparator.comparing(this::resolveNameSafe, String.CASE_INSENSITIVE_ORDER));
            case LAST_PLAYED_DESC -> result.sort((a, b) -> {
                PlayerProfile pa = profileByUuid.get(a);
                PlayerProfile pb = profileByUuid.get(b);
                long la = pa != null ? pa.lastPlayed : 0L;
                long lb = pb != null ? pb.lastPlayed : 0L;
                int cmp = Long.compare(lb, la);
                if (cmp != 0) return cmp;
                return resolveNameSafe(a).compareToIgnoreCase(resolveNameSafe(b));
            });
        }
        return result;
    }

    private String getCategoryName(String category) {
        return switch (category) {
            case "online" -> plainLanguage("ui.category.label.online");
            case "recent" -> plainLanguage("ui.category.label.recent");
            case "history" -> plainLanguage("ui.category.label.history");
            case "inactive" -> plainLanguage("ui.category.label.inactive");
            case "never" -> plainLanguage("ui.category.label.never-online");
            case "withBackups" -> plainLanguage("ui.category.label.with-backups");
            case "noBackups" -> plainLanguage("ui.category.label.no-backups");
            default -> category;
        };
    }

    private String plainLanguage(String key) {
        String raw = plugin.getLanguageManager().getRawMessage(key);
        if (raw == null || raw.isBlank()) {
            return key;
        }
        return raw.replaceAll("(?i)&[0-9A-FK-OR]", "").trim();
    }

    private String resolveNameSafe(String uuid) {
        String name = plugin.getIdentityManager().resolveName(uuid);
        return (name == null || name.isBlank()) ? uuid.toLowerCase(Locale.ROOT) : name;
    }

    private ItemStack createCategoryItem(Material material, String nameKey, int count,
                                         NamedTextColor color, List<String> loreKeys) {
        ItemStack item = new ItemStack(material);
        item.setAmount(Math.min(Math.max(count, 1), 64));

        ItemMeta meta = item.getItemMeta();
        String displayLabel = resolveGuiLabel(nameKey);
        Component displayName = Component.text(displayLabel)
                .color(color)
                .append(Component.text(" (" + count + ")", NamedTextColor.WHITE));
        meta.displayName(displayName);

        List<Component> lore = new ArrayList<>();
        for (String key : loreKeys) {
            lore.add(plugin.getLanguageManager().getGuiMessage(key));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFunctionItem(Material material, String nameKey,
                                         NamedTextColor color, List<String> loreKeys) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(resolveGuiLabel(nameKey)).color(color));

        List<Component> lore = new ArrayList<>();
        for (String key : loreKeys) {
            lore.add(plugin.getLanguageManager().getGuiMessage(key));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createPlayerHead(String uuid) {
        try {
            return plugin.getAdminGui().createPlayerHead(uuid);
        } catch (Exception ignored) {
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            meta.displayName(Component.text(resolveNameSafe(uuid), NamedTextColor.GOLD));
            head.setItemMeta(meta);
            return head;
        }
    }

    private ItemStack createItem(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        item.setItemMeta(meta);
        return item;
    }

    private void fillBorder(Inventory gui) {
        ItemStack border = createItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" "));
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                if (gui.getItem(i) == null) {
                    gui.setItem(i, border);
                }
            }
        }
    }

    private void fillNavRow(Inventory gui, int page, int totalPages, PlayerSortMode sortMode) {
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ")));
        }

        Component info = plugin.getLanguageManager().getGuiMessage(
                "gui.common.page",
                "{page}", String.valueOf(page + 1),
                "{total}", String.valueOf(totalPages)
        );
        gui.setItem(SLOT_PAGE_INFO, createItem(Material.PAPER, info));
        gui.setItem(SLOT_SORT, createItem(Material.HOPPER,
                plugin.getLanguageManager().getGuiMessage("gui.admin.sort-toggle",
                        "{mode}", plugin.getLanguageManager().getRawMessage(sortMode.langKey))));

        if (page > 0) {
            gui.setItem(SLOT_PREV, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.prev-page")));
        }

        if (page < totalPages - 1) {
            gui.setItem(SLOT_NEXT, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.next-page")));
        }
    }

    private String resolveGuiLabel(String key) {
        String raw = plugin.getLanguageManager().getRawMessage(key);
        if (raw != null && !raw.isBlank() && !raw.equals(key)) {
            return stripLegacyColor(raw);
        }

        String nestedName = plugin.getLanguageManager().getRawMessage(key + ".name");
        if (nestedName != null && !nestedName.isBlank() && !nestedName.equals(key + ".name")) {
            return stripLegacyColor(nestedName);
        }

        String nestedTitle = plugin.getLanguageManager().getRawMessage(key + ".title");
        if (nestedTitle != null && !nestedTitle.isBlank() && !nestedTitle.equals(key + ".title")) {
            return stripLegacyColor(nestedTitle);
        }

        return humanizeKey(key);
    }

    private static String stripLegacyColor(String text) {
        return text.replaceAll("(?i)&[0-9A-FK-OR]", "").trim();
    }

    private static String humanizeKey(String fullKey) {
        String tail = fullKey;
        int dot = fullKey.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < fullKey.length()) {
            tail = fullKey.substring(dot + 1);
        }
        String[] parts = tail.replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.length() > 0 ? sb.toString() : fullKey;
    }

    private enum SessionMode {
        CATEGORY_MENU,
        PLAYER_LIST
    }

    private static class CategorySession {
        SessionMode mode;
        String category;
        int page;
        PlayerSortMode sortMode = PlayerSortMode.NAME_ASC;
        List<String> filteredUuids = List.of();
        Inventory inventory;
    }

    private static class PlayerProfile {
        String uuid;
        String name;
        boolean online;
        boolean hasPlayedBefore;
        long lastPlayed;
        int backupCount;
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
