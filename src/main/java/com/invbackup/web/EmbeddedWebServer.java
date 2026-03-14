package com.invbackup.web;

import com.invbackup.InvBackup;
import com.invbackup.manager.BackupManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Embedded, read-only web server for browsing snapshots and generating commands.
 */
public class EmbeddedWebServer {

    private static final String AUTH_HEADER = "X-InvBackup-Token";

    private final InvBackup plugin;
    private final AtomicInteger threadIndex = new AtomicInteger(1);

    private HttpServer server;
    private ExecutorService executor;

    private String host;
    private int port;
    private boolean authEnabled;
    private String authToken;
    private boolean generatedAuthToken;
    private boolean allowQueryToken;
    private int defaultLimit;
    private int maxLimit;
    private String iconRemoteBase;
    private long iconCacheTtlMs;
    private int iconCacheMaxEntries;
    private String minecraftVersion;

    private final Map<String, CachedIcon> iconCache = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public EmbeddedWebServer(InvBackup plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean start() {
        if (server != null) {
            return true;
        }

        readConfig();

        try {
            InetSocketAddress bindAddress = new InetSocketAddress(host, port);
            server = HttpServer.create(bindAddress, 0);
            executor = Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "InvBackup-Web-" + threadIndex.getAndIncrement());
                t.setDaemon(true);
                return t;
            });
            server.setExecutor(executor);
            server.createContext("/api", this::handleApi);
            server.createContext("/", this::handleStatic);
            server.start();

            plugin.getLogger().info("InvBackup web UI started at " + getBaseUrl());
            if (authEnabled) {
                plugin.getLogger().info("InvBackup web auth enabled. Header: "
                        + AUTH_HEADER + " (or Authorization: Bearer <token>)");
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start InvBackup web UI", e);
            stop();
            return false;
        }
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public synchronized boolean isRunning() {
        return server != null;
    }

    public String getBaseUrl() {
        return "http://" + host + ":" + port + "/";
    }

    public boolean isAuthEnabled() {
        return authEnabled;
    }

    public String getAuthToken() {
        return authToken == null ? "" : authToken;
    }

    public boolean isUsingGeneratedAuthToken() {
        return generatedAuthToken;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    private void readConfig() {
        host = plugin.getConfig().getString("web.host", "127.0.0.1");
        if (host == null || host.isBlank()) {
            host = "127.0.0.1";
        }
        port = plugin.getConfig().getInt("web.port", 5800);
        if (port < 1 || port > 65535) {
            port = 5800;
        }

        authEnabled = plugin.getConfig().getBoolean("web.auth.enabled", true);
        authToken = plugin.getConfig().getString("web.auth.token", "");
        generatedAuthToken = false;
        if (authEnabled && (authToken == null || authToken.isBlank())) {
            authToken = UUID.randomUUID().toString().replace("-", "");
            generatedAuthToken = true;
            plugin.getLogger().warning(
                    "web.auth.token is empty. Generated a runtime token. "
                            + "Set web.auth.token in config.yml and run /ib reload.");
        }
        allowQueryToken = plugin.getConfig().getBoolean("web.auth.allow-query-token", true);

        defaultLimit = plugin.getConfig().getInt("web.api.default-limit", 200);
        maxLimit = plugin.getConfig().getInt("web.api.max-limit", 1000);
        defaultLimit = Math.max(1, defaultLimit);
        maxLimit = Math.max(defaultLimit, maxLimit);

        minecraftVersion = Bukkit.getMinecraftVersion();
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            minecraftVersion = "1.21.4";
        }

        iconRemoteBase = plugin.getConfig().getString(
                "web.icons.remote-base",
                "https://assets.mcasset.cloud/{mc}/assets/minecraft/textures");
        if (iconRemoteBase == null || iconRemoteBase.isBlank()) {
            iconRemoteBase = "https://assets.mcasset.cloud/{mc}/assets/minecraft/textures";
        }
        iconRemoteBase = iconRemoteBase
                .replace("{mc}", minecraftVersion)
                .replace("{mc_version}", minecraftVersion)
                .replace("{minecraft}", minecraftVersion);
        iconRemoteBase = iconRemoteBase.replaceAll("/+$", "");

        int iconCacheTtlSeconds = plugin.getConfig().getInt("web.icons.cache-ttl-seconds", 600);
        iconCacheTtlMs = Math.max(10L, iconCacheTtlSeconds) * 1000L;
        iconCacheMaxEntries = Math.max(64, plugin.getConfig().getInt("web.icons.cache-max-entries", 1024));
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendApiError(exchange, 405, "method_not_allowed", "Only GET is supported.");
                return;
            }

            String apiPath = normalizeApiPath(exchange.getRequestURI().getPath());
            if (!isPublicApi(apiPath) && authEnabled && !isAuthorized(exchange)) {
                sendApiError(exchange, 401, "unauthorized",
                        "Missing or invalid token. Provide " + AUTH_HEADER + " header.");
                return;
            }

            Map<String, String> query = parseQuery(exchange.getRequestURI());
            switch (apiPath) {
                case "/health" -> handleHealth(exchange);
                case "/settings" -> handleSettings(exchange);
                case "/players", "/search" -> handlePlayers(exchange, query);
                case "/snapshots" -> handleSnapshots(exchange, query);
                case "/snapshot" -> handleSnapshot(exchange, query);
                case "/icon" -> handleIcon(exchange, query);
                default -> sendApiError(exchange, 404, "not_found", "API endpoint not found.");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Web API error", e);
            sendApiError(exchange, 500, "server_error", e.getMessage());
        } finally {
            exchange.close();
        }
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            boolean head = "HEAD".equalsIgnoreCase(method);
            if (!head && !"GET".equalsIgnoreCase(method)) {
                sendText(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
                return;
            }

            String requestPath = exchange.getRequestURI().getPath();
            String path = normalizeStaticPath(requestPath);
            if (path == null) {
                sendText(exchange, 403, "Forbidden", "text/plain; charset=utf-8");
                return;
            }

            String resourcePath = "web" + path;
            byte[] body = readResource(resourcePath);
            if (body == null) {
                sendText(exchange, 404, "Not Found", "text/plain; charset=utf-8");
                return;
            }

            String contentType = detectContentType(path);
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            if (head) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Web static handler error", e);
            sendText(exchange, 500, "Internal Server Error", "text/plain; charset=utf-8");
        } finally {
            exchange.close();
        }
    }

    private void handleHealth(HttpExchange exchange) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("name", plugin.getDescription().getName());
        body.put("version", plugin.getDescription().getVersion());
        body.put("baseUrl", getBaseUrl());
        body.put("timestamp", System.currentTimeMillis());
        sendJson(exchange, 200, body);
    }

    private void handleSettings(HttpExchange exchange) throws IOException {
        Map<String, Object> icons = new LinkedHashMap<>();
        icons.put("remoteBase", iconRemoteBase);
        icons.put("itemPattern", iconRemoteBase + "/item/{id}.png");
        icons.put("blockPattern", iconRemoteBase + "/block/{id}.png");
        icons.put("apiPattern", "/api/icon?id={id}");
        icons.put("placeholderPath", "/assets/item_placeholder.svg");
        icons.put("cacheTtlSeconds", iconCacheTtlMs / 1000L);
        icons.put("cacheMaxEntries", iconCacheMaxEntries);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("authEnabled", authEnabled);
        body.put("authHeader", AUTH_HEADER);
        body.put("allowQueryToken", allowQueryToken);
        body.put("defaultLimit", defaultLimit);
        body.put("maxLimit", maxLimit);
        body.put("baseUrl", getBaseUrl());
        body.put("minecraftVersion", minecraftVersion);
        body.put("icons", icons);
        sendJson(exchange, 200, body);
    }

    private void handleIcon(HttpExchange exchange, Map<String, String> query) throws IOException {
        String inputId = firstNonBlank(query.get("id"), query.get("item"), query.get("key"));
        if (inputId == null || inputId.isBlank()) {
            sendApiError(exchange, 400, "bad_request", "Missing icon id.");
            return;
        }
        boolean forceReload = "1".equals(query.get("force"))
                || "true".equalsIgnoreCase(query.get("force"));

        String itemId = normalizeItemId(inputId);
        if (!itemId.matches("[a-z0-9_./-]+")) {
            sendApiError(exchange, 400, "bad_request", "Invalid icon id.");
            return;
        }

        String preferredType = trimToEmpty(query.get("type")).toLowerCase(Locale.ROOT);
        boolean preferBlock = "block".equals(preferredType);
        boolean preferItem = preferredType.isBlank() || "item".equals(preferredType);

        String cacheKey = itemId + "|" + (preferBlock ? "block" : "auto");
        CachedIcon cached = forceReload ? null : iconCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt > now) {
            sendCachedIcon(exchange, cached);
            return;
        }

        CachedIcon fresh = fetchIcon(itemId, preferItem, preferBlock);
        if (fresh == null) {
            CachedIcon fallback = buildPlaceholderIcon(now + iconCacheTtlMs / 2);
            iconCache.put(cacheKey, fallback);
            sendCachedIcon(exchange, fallback);
            return;
        }

        fresh.expiresAt = now + iconCacheTtlMs;
        iconCache.put(cacheKey, fresh);
        cleanupIconCache(now);
        sendCachedIcon(exchange, fresh);
    }

    private void handlePlayers(HttpExchange exchange, Map<String, String> query) throws IOException {
        String keyword = trimToEmpty(query.get("q"));
        String sort = trimToEmpty(query.get("sort"));
        int limit = resolveLimit(query.get("limit"));
        boolean sortByLatest = !"name".equalsIgnoreCase(sort);

        List<BackupManager.WebPlayerSummary> players = plugin.getBackupManager()
                .listWebPlayers(keyword, limit, sortByLatest);

        List<Map<String, Object>> items = new ArrayList<>();
        for (BackupManager.WebPlayerSummary p : players) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("uuid", p.uuid);
            node.put("name", p.name);
            node.put("snapshotCount", p.snapshotCount);
            node.put("latestSnapshotId", p.latestSnapshotId);
            node.put("latestTimestamp", p.latestTimestamp);
            items.add(node);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("query", keyword);
        body.put("sort", sortByLatest ? "latest" : "name");
        body.put("count", items.size());
        body.put("items", items);
        sendJson(exchange, 200, body);
    }

    private void handleSnapshots(HttpExchange exchange, Map<String, String> query) throws IOException {
        String playerInput = firstNonBlank(
                query.get("player"),
                query.get("uuid"),
                query.get("name"));
        if (playerInput == null || playerInput.isBlank()) {
            sendApiError(exchange, 400, "bad_request", "Missing player parameter.");
            return;
        }

        String uuid = plugin.getBackupManager().resolvePlayerUuidForWeb(playerInput);
        if (uuid == null || uuid.isBlank()) {
            sendApiError(exchange, 404, "player_not_found", "Player not found in backups.");
            return;
        }

        int limit = resolveLimit(query.get("limit"));
        List<BackupManager.WebSnapshotSummary> snapshots =
                plugin.getBackupManager().listWebSnapshots(uuid, limit);

        List<Map<String, Object>> items = new ArrayList<>();
        for (BackupManager.WebSnapshotSummary snap : snapshots) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", snap.snapshotId);
            node.put("targetName", snap.targetName);
            node.put("triggeredBy", snap.triggeredBy);
            node.put("triggerType", snap.triggerType);
            node.put("label", snap.label);
            node.put("timestamp", snap.timestamp);
            node.put("backupLevel", snap.backupLevel);
            node.put("source", snap.source);
            items.add(node);
        }

        Map<String, Object> player = new LinkedHashMap<>();
        player.put("uuid", uuid);
        player.put("name", plugin.getBackupManager().resolveStoredPlayerName(uuid));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("player", player);
        body.put("count", items.size());
        body.put("items", items);
        sendJson(exchange, 200, body);
    }

    private void handleSnapshot(HttpExchange exchange, Map<String, String> query) throws IOException {
        String playerInput = firstNonBlank(
                query.get("player"),
                query.get("uuid"),
                query.get("name"));
        String snapshotId = trimToEmpty(query.get("id"));

        if (playerInput == null || playerInput.isBlank() || snapshotId.isBlank()) {
            sendApiError(exchange, 400, "bad_request", "Missing player or id parameter.");
            return;
        }

        String uuid = plugin.getBackupManager().resolvePlayerUuidForWeb(playerInput);
        if (uuid == null || uuid.isBlank()) {
            sendApiError(exchange, 404, "player_not_found", "Player not found in backups.");
            return;
        }

        Map<String, Object> payload = plugin.getBackupManager()
                .getWebSnapshotPayload(uuid, snapshotId);
        if (payload == null) {
            sendApiError(exchange, 404, "snapshot_not_found", "Snapshot not found.");
            return;
        }
        payload.put("ok", true);
        sendJson(exchange, 200, payload);
    }

    private boolean isPublicApi(String apiPath) {
        return "/health".equals(apiPath) || "/settings".equals(apiPath);
    }

    private boolean isAuthorized(HttpExchange exchange) {
        String token = tokenFromHeaders(exchange);
        if ((token == null || token.isBlank()) && allowQueryToken) {
            token = parseQuery(exchange.getRequestURI()).get("token");
        }
        return token != null && !token.isBlank() && token.equals(authToken);
    }

    private String tokenFromHeaders(HttpExchange exchange) {
        String token = exchange.getRequestHeaders().getFirst(AUTH_HEADER);
        if (token != null && !token.isBlank()) {
            return token.trim();
        }

        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null) {
            String lower = authorization.toLowerCase(Locale.ROOT);
            if (lower.startsWith("bearer ")) {
                return authorization.substring(7).trim();
            }
        }
        return null;
    }

    private String normalizeApiPath(String rawPath) {
        String path = rawPath == null ? "/" : rawPath;
        if (path.startsWith("/api")) {
            path = path.substring(4);
        }
        if (path.isBlank()) {
            path = "/";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private String normalizeStaticPath(String rawPath) {
        String path = rawPath == null ? "/" : rawPath;
        if (path.startsWith("/api")) {
            return null;
        }
        if (path.equals("/") || path.isBlank()) {
            path = "/index.html";
        }
        if (path.endsWith("/")) {
            path += "index.html";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (path.contains("..")) {
            return null;
        }
        return path;
    }

    private byte[] readResource(String classpathPath) throws IOException {
        try (InputStream in = plugin.getResource(classpathPath)) {
            if (in == null) {
                return null;
            }
            return in.readAllBytes();
        }
    }

    private String detectContentType(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        return "application/octet-stream";
    }

    private void sendJson(HttpExchange exchange, int status, Map<String, Object> body)
            throws IOException {
        String json = WebJsonUtil.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private void sendApiError(HttpExchange exchange,
                              int status,
                              String code,
                              String message) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("error", code);
        body.put("message", message);
        sendJson(exchange, status, body);
    }

    private void sendText(HttpExchange exchange,
                          int status,
                          String message,
                          String contentType) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private int resolveLimit(String raw) {
        int requested;
        try {
            requested = raw == null ? defaultLimit : Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            requested = defaultLimit;
        }
        if (requested <= 0) {
            requested = defaultLimit;
        }
        return Math.min(requested, maxLimit);
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> query = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isBlank()) {
            return query;
        }

        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            if (pair == null || pair.isBlank()) {
                continue;
            }
            String[] kv = pair.split("=", 2);
            String key = urlDecode(kv[0]);
            String value = kv.length > 1 ? urlDecode(kv[1]) : "";
            query.put(key, value);
        }
        return query;
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String normalizeItemId(String inputId) {
        String id = inputId.trim().toLowerCase(Locale.ROOT);
        if (id.startsWith("minecraft:")) {
            id = id.substring("minecraft:".length());
        }
        return id;
    }

    private CachedIcon fetchIcon(String itemId, boolean tryItem, boolean preferBlock) {
        List<String> candidates = new ArrayList<>();
        if (preferBlock) {
            candidates.add(iconUrl("block", itemId));
            candidates.add(iconUrl("item", itemId));
        } else {
            if (tryItem) {
                candidates.add(iconUrl("item", itemId));
            }
            candidates.add(iconUrl("block", itemId));
        }

        for (String url : candidates) {
            CachedIcon icon = requestRemoteIcon(url);
            if (icon != null) {
                return icon;
            }
        }
        return null;
    }

    private String iconUrl(String type, String itemId) {
        String encoded = URLEncoder.encode(itemId, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return iconRemoteBase + "/" + type + "/" + encoded + ".png";
    }

    private CachedIcon requestRemoteIcon(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<byte[]> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                return null;
            }
            byte[] bytes = response.body();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            CachedIcon icon = new CachedIcon();
            icon.bytes = bytes;
            icon.contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("image/png");
            icon.fromRemote = true;
            return icon;
        } catch (Exception e) {
            return null;
        }
    }

    private CachedIcon buildPlaceholderIcon(long expiresAt) {
        try {
            byte[] bytes = readResource("web/assets/item_placeholder.svg");
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            CachedIcon icon = new CachedIcon();
            icon.bytes = bytes;
            icon.contentType = "image/svg+xml";
            icon.fromRemote = false;
            icon.expiresAt = expiresAt;
            return icon;
        } catch (IOException e) {
            return null;
        }
    }

    private void sendCachedIcon(HttpExchange exchange, CachedIcon icon) throws IOException {
        if (icon == null || icon.bytes == null || icon.bytes.length == 0) {
            sendApiError(exchange, 404, "icon_not_found", "Icon not found.");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", icon.contentType);
        exchange.getResponseHeaders().set("Cache-Control", "private, max-age=60");
        exchange.getResponseHeaders().set("X-InvBackup-Icon-Source",
                icon.fromRemote ? "remote" : "fallback");
        exchange.sendResponseHeaders(200, icon.bytes.length);
        exchange.getResponseBody().write(icon.bytes);
    }

    private void cleanupIconCache(long now) {
        if (iconCache.size() <= iconCacheMaxEntries) {
            return;
        }

        iconCache.entrySet().removeIf(e -> e.getValue().expiresAt <= now);
        if (iconCache.size() <= iconCacheMaxEntries) {
            return;
        }

        List<Map.Entry<String, CachedIcon>> entries = new ArrayList<>(iconCache.entrySet());
        entries.sort(Comparator.comparingLong(e -> e.getValue().expiresAt));
        int remove = iconCache.size() - iconCacheMaxEntries;
        for (int i = 0; i < remove && i < entries.size(); i++) {
            iconCache.remove(entries.get(i).getKey());
        }
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static class CachedIcon {
        byte[] bytes;
        String contentType;
        long expiresAt;
        boolean fromRemote;
    }
}
