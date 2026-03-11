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
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SearchGui implements Listener {

    private static final int SLOT_PREV = 45;
    private static final int SLOT_BACK = 48;
    private static final int SLOT_PAGE_INFO = 49;
    private static final int SLOT_NEXT = 53;

    private static final String NAME_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final String UUID_CHARS = "0123456789ABCDEF-";

    private final InvBackup plugin;
    private final Map<UUID, SearchSession> activeSessions = new HashMap<>();
    private final Map<UUID, ChatInputSession> chatInputSessions = new HashMap<>();
    private final Map<UUID, Deque<String>> searchHistory = new HashMap<>();

    public SearchGui(InvBackup plugin) {
        this.plugin = plugin;
    }

    public void openSearchMenu(Player viewer) {
        Component title = plugin.getLanguageManager().getGuiMessage("ui.search.menu-title");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        fillBorder(gui);

        gui.setItem(20, createLabeledItem(Material.NAME_TAG,
                "ui.search.by-name", NamedTextColor.GREEN, List.of("ui.search.by-name-desc")));
        gui.setItem(22, createLabeledItem(Material.PAPER,
                "ui.search.by-uuid", NamedTextColor.BLUE, List.of("ui.search.by-uuid-desc")));
        gui.setItem(24, createLabeledItem(Material.BOOK,
                "ui.search.advanced", NamedTextColor.GOLD, List.of("ui.search.advanced-desc")));

        gui.setItem(48, createLabeledItem(Material.COMPASS,
                "ui.search.quick-search", NamedTextColor.AQUA, List.of("ui.search.quick-search-desc")));
        gui.setItem(49, createLabeledItem(Material.CLOCK,
                "ui.search.history", NamedTextColor.YELLOW, List.of("ui.search.history-desc")));
        gui.setItem(50, createLabeledItem(Material.ARROW,
                "ui.search.back", NamedTextColor.WHITE, List.of("ui.search.back-desc")));
        gui.setItem(51, createLabeledItem(Material.WRITABLE_BOOK,
                "ui.search.direct-chat", NamedTextColor.GREEN, List.of("ui.search.direct-chat-desc")));

        SearchSession session = new SearchSession();
        session.mode = SearchMode.MENU;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);

        viewer.openInventory(gui);
    }

    public void openNameSearch(Player viewer, String currentInput) {
        Component title = plugin.getLanguageManager().getGuiMessage(
                "ui.search.name-title", "{input}", currentInput.isEmpty() ? "..." : currentInput);
        Inventory gui = Bukkit.createInventory(null, 54, title);

        for (int i = 0; i < NAME_CHARS.length(); i++) {
            gui.setItem(9 + i, createCharButton(NAME_CHARS.charAt(i), Material.OAK_BUTTON));
        }

        gui.setItem(4, createInputDisplay(currentInput));

        gui.setItem(45, createFunctionButton(Material.OAK_SIGN, "ui.search.button-space"));
        gui.setItem(46, createFunctionButton(Material.REDSTONE_TORCH, "ui.search.button-backspace"));
        gui.setItem(47, createFunctionButton(Material.TNT, "ui.search.button-clear"));
        gui.setItem(51, createFunctionButton(Material.WRITABLE_BOOK, "ui.search.button-chat"));
        gui.setItem(52, createFunctionButton(Material.COMPASS, "ui.search.button-go"));
        gui.setItem(53, createFunctionButton(Material.ARROW, "ui.search.button-back"));

        SearchSession session = new SearchSession();
        session.mode = SearchMode.NAME_INPUT;
        session.input = currentInput;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);

        viewer.openInventory(gui);
    }

    public void openUuidSearch(Player viewer, String currentInput) {
        Component title = plugin.getLanguageManager().getGuiMessage(
                "ui.search.uuid-title", "{input}", currentInput.isEmpty() ? "..." : currentInput);
        Inventory gui = Bukkit.createInventory(null, 54, title);

        for (int i = 0; i < UUID_CHARS.length(); i++) {
            gui.setItem(19 + i, createCharButton(UUID_CHARS.charAt(i), Material.STONE_BUTTON));
        }

        gui.setItem(4, createInputDisplay(currentInput));

        gui.setItem(45, createFunctionButton(Material.REDSTONE_TORCH, "ui.search.button-backspace"));
        gui.setItem(46, createFunctionButton(Material.TNT, "ui.search.button-clear"));
        gui.setItem(47, createFunctionButton(Material.BARRIER, "ui.search.button-normalize"));
        gui.setItem(51, createFunctionButton(Material.WRITABLE_BOOK, "ui.search.button-chat"));
        gui.setItem(52, createFunctionButton(Material.COMPASS, "ui.search.button-go"));
        gui.setItem(53, createFunctionButton(Material.ARROW, "ui.search.button-back"));

        SearchSession session = new SearchSession();
        session.mode = SearchMode.UUID_INPUT;
        session.input = currentInput;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);

        viewer.openInventory(gui);
    }

    public void openSearchResults(Player viewer, String query, List<String> results, int page) {
        int totalPages = Math.max(1, (int) Math.ceil(results.size() / 45.0));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        Component title = plugin.getLanguageManager().getGuiMessage(
                "ui.search.results-title",
                "{query}", query,
                "{page}", String.valueOf(safePage + 1),
                "{total}", String.valueOf(totalPages),
                "{count}", String.valueOf(results.size())
        );
        Inventory gui = Bukkit.createInventory(null, 54, title);

        int start = safePage * 45;
        int end = Math.min(start + 45, results.size());
        for (int i = start; i < end; i++) {
            gui.setItem(i - start, createResultPlayerHead(results.get(i)));
        }

        fillNavRow(gui, safePage, totalPages);
        gui.setItem(SLOT_BACK, createItem(Material.ARROW,
                plugin.getLanguageManager().getGuiMessage("ui.search.back-to-search")));

        SearchSession session = new SearchSession();
        session.mode = SearchMode.RESULTS;
        session.query = query;
        session.results = new ArrayList<>(results);
        session.page = safePage;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);

        viewer.openInventory(gui);
    }

    private void openSearchHistory(Player viewer, int page) {
        List<String> history = new ArrayList<>(searchHistory
                .getOrDefault(viewer.getUniqueId(), new ArrayDeque<>()));

        if (history.isEmpty()) {
            viewer.sendMessage(plugin.getMessage("command.search.no-history"));
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(history.size() / 45.0));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));

        Component title = plugin.getLanguageManager().getGuiMessage("ui.search.history-title");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        int start = safePage * 45;
        int end = Math.min(start + 45, history.size());
        for (int i = start; i < end; i++) {
            String query = history.get(i);
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(query, NamedTextColor.GOLD));
            meta.lore(List.of(plugin.getLanguageManager().getGuiMessage("ui.search.history-click-again")));
            item.setItemMeta(meta);
            gui.setItem(i - start, item);
        }

        fillNavRow(gui, safePage, totalPages);
        gui.setItem(SLOT_BACK, createItem(Material.ARROW,
                plugin.getLanguageManager().getGuiMessage("ui.search.back")));

        SearchSession session = new SearchSession();
        session.mode = SearchMode.HISTORY;
        session.historyQueries = history;
        session.page = safePage;
        session.inventory = gui;
        activeSessions.put(viewer.getUniqueId(), session);

        viewer.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        SearchSession session = activeSessions.get(player.getUniqueId());
        if (session == null || event.getView().getTopInventory() != session.inventory) {
            return;
        }

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) {
            return;
        }

        switch (session.mode) {
            case MENU -> handleMenuClick(player, slot);
            case NAME_INPUT -> handleNameInputClick(player, session, slot);
            case UUID_INPUT -> handleUuidInputClick(player, session, slot);
            case RESULTS -> handleResultsClick(player, session, slot);
            case HISTORY -> handleHistoryClick(player, session, slot);
        }
    }

    private void handleMenuClick(Player player, int slot) {
        switch (slot) {
            case 20, 24 -> openNameSearch(player, "");
            case 22 -> openUuidSearch(player, "");
            case 48 -> openQuickSearch(player);
            case 49 -> openSearchHistory(player, 0);
            case 50 -> {
                activeSessions.remove(player.getUniqueId());
                plugin.getCategoryGui().openCategoryMenu(player);
            }
            case 51 -> openChatInput(player, SearchMode.DIRECT_INPUT, "");
            default -> {
            }
        }
    }

    private void handleNameInputClick(Player player, SearchSession session, int slot) {
        if (slot >= 9 && slot <= 44) {
            int idx = slot - 9;
            if (idx >= 0 && idx < NAME_CHARS.length()) {
                openNameSearch(player, session.input + NAME_CHARS.charAt(idx));
                return;
            }
        }

        switch (slot) {
            case 45 -> openNameSearch(player, session.input + " ");
            case 46 -> {
                if (!session.input.isEmpty()) {
                    openNameSearch(player, session.input.substring(0, session.input.length() - 1));
                }
            }
            case 47 -> openNameSearch(player, "");
            case 51 -> openChatInput(player, SearchMode.NAME_INPUT, session.input);
            case 52 -> {
                if (!session.input.isBlank()) {
                    performNameSearch(player, session.input);
                }
            }
            case 53 -> openSearchMenu(player);
            default -> {
            }
        }
    }

    private void handleUuidInputClick(Player player, SearchSession session, int slot) {
        if (slot >= 19 && slot < 19 + UUID_CHARS.length()) {
            int idx = slot - 19;
            if (idx >= 0 && idx < UUID_CHARS.length()) {
                openUuidSearch(player, session.input + UUID_CHARS.charAt(idx));
                return;
            }
        }

        switch (slot) {
            case 45 -> {
                if (!session.input.isEmpty()) {
                    openUuidSearch(player, session.input.substring(0, session.input.length() - 1));
                }
            }
            case 46 -> openUuidSearch(player, "");
            case 47 -> openUuidSearch(player, session.input.replace("-", "").toUpperCase(Locale.ROOT));
            case 51 -> openChatInput(player, SearchMode.UUID_INPUT, session.input);
            case 52 -> {
                if (session.input.length() >= 4) {
                    performUuidSearch(player, session.input);
                }
            }
            case 53 -> openSearchMenu(player);
            default -> {
            }
        }
    }

    private void handleResultsClick(Player player, SearchSession session, int slot) {
        int totalPages = Math.max(1, (int) Math.ceil(session.results.size() / 45.0));

        if (slot == SLOT_BACK) {
            if (session.query.matches("[0-9A-Fa-f-]+")) {
                openUuidSearch(player, session.query);
            } else {
                openNameSearch(player, session.query);
            }
            return;
        }

        if (slot == SLOT_PREV && session.page > 0) {
            openSearchResults(player, session.query, session.results, session.page - 1);
            return;
        }

        if (slot == SLOT_NEXT && session.page < totalPages - 1) {
            openSearchResults(player, session.query, session.results, session.page + 1);
            return;
        }

        int index = session.page * 45 + slot;
        if (slot < 45 && index < session.results.size()) {
            String uuid = session.results.get(index);
            activeSessions.remove(player.getUniqueId());
            plugin.getAdminGui().openBackupList(player, uuid, 0);
        }
    }

    private void handleHistoryClick(Player player, SearchSession session, int slot) {
        int totalPages = Math.max(1, (int) Math.ceil(session.historyQueries.size() / 45.0));

        if (slot == SLOT_BACK) {
            openSearchMenu(player);
            return;
        }

        if (slot == SLOT_PREV && session.page > 0) {
            openSearchHistory(player, session.page - 1);
            return;
        }

        if (slot == SLOT_NEXT && session.page < totalPages - 1) {
            openSearchHistory(player, session.page + 1);
            return;
        }

        int index = session.page * 45 + slot;
        if (slot < 45 && index < session.historyQueries.size()) {
            String query = session.historyQueries.get(index);
            if (query.matches("[0-9A-Fa-f-]+")) {
                performUuidSearch(player, query);
            } else {
                performNameSearch(player, query);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        SearchSession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getView().getTopInventory() == session.inventory) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        SearchSession session = activeSessions.get(player.getUniqueId());
        if (session != null && event.getInventory() == session.inventory) {
            activeSessions.remove(player.getUniqueId());
        }
    }

    @EventHandler
    @SuppressWarnings("deprecation")
    public void onSearchChatInput(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        ChatInputSession input = chatInputSessions.remove(player.getUniqueId());
        if (input == null) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage().trim();

        if (message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(plugin.getLanguageManager().getGuiMessage("gui.preview.cross-restore.chat-cancelled"));
                if (input.mode == SearchMode.DIRECT_INPUT) {
                    openSearchMenu(player);
                } else if (input.mode == SearchMode.UUID_INPUT) {
                    openUuidSearch(player, input.previousInput);
                } else {
                    openNameSearch(player, input.previousInput);
                }
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (input.mode == SearchMode.DIRECT_INPUT) {
                performDirectSearch(player, message);
            } else if (input.mode == SearchMode.UUID_INPUT) {
                openUuidSearch(player, message.toUpperCase(Locale.ROOT));
            } else {
                openNameSearch(player, message);
            }
        });
    }

    public void removeSession(UUID playerId) {
        activeSessions.remove(playerId);
        chatInputSessions.remove(playerId);
    }

    private void performNameSearch(Player player, String query) {
        List<String> results = searchByName(query);

        addHistory(player.getUniqueId(), query);

        if (results.isEmpty()) {
            player.sendMessage(plugin.getMessage("search-no-results"));
            return;
        }

        openSearchResults(player, query, results, 0);
    }

    private void performUuidSearch(Player player, String query) {
        String cleanQuery = query.replace("-", "").toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();

        for (String uuid : getAllKnownPlayerUuids()) {
            if (uuid.replace("-", "").toLowerCase(Locale.ROOT).contains(cleanQuery)) {
                results.add(uuid);
            }
        }

        results.sort(Comparator.comparing(this::resolveNameSafe, String.CASE_INSENSITIVE_ORDER));
        addHistory(player.getUniqueId(), query);

        if (results.isEmpty()) {
            player.sendMessage(plugin.getMessage("search-no-results"));
            return;
        }

        openSearchResults(player, query, results, 0);
    }

    private List<String> searchByName(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        List<String> results = new ArrayList<>();

        for (String uuid : getAllKnownPlayerUuids()) {
            String name = resolveNameSafe(uuid);
            if (name.toLowerCase(Locale.ROOT).contains(needle)) {
                results.add(uuid);
            }
        }

        results.sort(Comparator.comparing(this::resolveNameSafe, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    private void openQuickSearch(Player player) {
        List<String> results = new ArrayList<>(plugin.getBackupManager().getAllPlayerUuids());
        results.sort(Comparator.comparing(this::resolveNameSafe, String.CASE_INSENSITIVE_ORDER));

        if (results.isEmpty()) {
            player.sendMessage(plugin.getMessage("no-backups"));
            return;
        }

        openSearchResults(player, "*", results, 0);
    }

    private void performDirectSearch(Player player, String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.isEmpty()) {
            openSearchMenu(player);
            return;
        }

        String nameNeedle = normalized.toLowerCase(Locale.ROOT);
        String uuidNeedle = normalized.replace("-", "").toLowerCase(Locale.ROOT);
        Set<String> matched = new LinkedHashSet<>();

        for (String uuid : getAllKnownPlayerUuids()) {
            String name = resolveNameSafe(uuid).toLowerCase(Locale.ROOT);
            boolean byName = name.contains(nameNeedle);
            boolean byUuid = uuid.replace("-", "").toLowerCase(Locale.ROOT).contains(uuidNeedle);
            if (byName || byUuid) {
                matched.add(uuid);
            }
        }

        List<String> results = new ArrayList<>(matched);
        results.sort(Comparator.comparing(this::resolveNameSafe, String.CASE_INSENSITIVE_ORDER));
        addHistory(player.getUniqueId(), normalized);

        if (results.isEmpty()) {
            player.sendMessage(plugin.getMessage("search-no-results"));
            return;
        }

        openSearchResults(player, normalized, results, 0);
    }

    private void openChatInput(Player player, SearchMode mode, String currentInput) {
        activeSessions.remove(player.getUniqueId());
        player.closeInventory();

        ChatInputSession session = new ChatInputSession();
        session.mode = mode;
        session.previousInput = currentInput;
        chatInputSessions.put(player.getUniqueId(), session);

        player.sendMessage(plugin.getLanguageManager().getGuiMessage("ui.search.chat-input-prompt"));
    }

    private void addHistory(UUID playerId, String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        Deque<String> history = searchHistory.computeIfAbsent(playerId, k -> new ArrayDeque<>());
        history.remove(query);
        history.addFirst(query);
        while (history.size() > 50) {
            history.removeLast();
        }
    }

    private Set<String> getAllKnownPlayerUuids() {
        Set<String> uuids = new LinkedHashSet<>(plugin.getBackupManager().getAllPlayerUuids());
        for (Player online : Bukkit.getOnlinePlayers()) {
            uuids.add(online.getUniqueId().toString());
        }
        for (OfflinePlayer offline : Bukkit.getOfflinePlayers()) {
            if (offline.getUniqueId() != null) {
                uuids.add(offline.getUniqueId().toString());
            }
        }
        return uuids;
    }

    private String resolveNameSafe(String uuid) {
        String name = plugin.getIdentityManager().resolveName(uuid);
        return (name == null || name.isBlank()) ? uuid : name;
    }

    private ItemStack createLabeledItem(Material material, String nameKey,
                                        NamedTextColor color, List<String> loreKeys) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getGuiMessage(nameKey).color(color));

        List<Component> lore = new ArrayList<>();
        for (String key : loreKeys) {
            lore.add(plugin.getLanguageManager().getGuiMessage(key));
        }
        meta.lore(lore);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createCharButton(char value, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(String.valueOf(value), NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFunctionButton(Material material, String textKey) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.getLanguageManager().getGuiMessage(textKey));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createInputDisplay(String input) {
        ItemStack item = new ItemStack(Material.WRITABLE_BOOK);
        ItemMeta meta = item.getItemMeta();

        if (input == null || input.isEmpty()) {
            meta.displayName(Component.text("...", NamedTextColor.GRAY));
        } else {
            meta.displayName(Component.text(input, NamedTextColor.GREEN));
        }
        meta.lore(List.of(
                plugin.getLanguageManager().getGuiMessage("ui.search.input-hint-1"),
                plugin.getLanguageManager().getGuiMessage("ui.search.input-hint-2")
        ));

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createResultPlayerHead(String uuid) {
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

    private void fillNavRow(Inventory gui, int page, int totalPages) {
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, createItem(Material.BLACK_STAINED_GLASS_PANE, Component.text(" ")));
        }

        Component info = plugin.getLanguageManager().getGuiMessage(
                "gui.common.page",
                "{page}", String.valueOf(page + 1),
                "{total}", String.valueOf(totalPages)
        );

        gui.setItem(SLOT_PAGE_INFO, createItem(Material.PAPER, info));

        if (page > 0) {
            gui.setItem(SLOT_PREV, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.prev-page")));
        }

        if (page < totalPages - 1) {
            gui.setItem(SLOT_NEXT, createItem(Material.ARROW,
                    plugin.getLanguageManager().getGuiMessage("gui.common.next-page")));
        }
    }

    private enum SearchMode {
        MENU,
        NAME_INPUT,
        UUID_INPUT,
        DIRECT_INPUT,
        RESULTS,
        HISTORY
    }

    private static class SearchSession {
        SearchMode mode;
        String input = "";
        String query = "";
        List<String> results = List.of();
        List<String> historyQueries = List.of();
        int page;
        Inventory inventory;
    }

    private static class ChatInputSession {
        SearchMode mode;
        String previousInput = "";
    }
}
