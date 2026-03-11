package com.invbackup.manager;

import com.invbackup.InvBackup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class LanguageManager {

    private final InvBackup plugin;
    private YamlConfiguration langConfig;
    private String currentLang;
    private final Set<String> warnedInvalidTypeKeys = new HashSet<>();

    public LanguageManager(InvBackup plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        warnedInvalidTypeKeys.clear();
        currentLang = plugin.getConfig().getString("language", "zh_CN");

        // Save built-in language files
        saveDefaultLang("zh_CN");
        saveDefaultLang("en_US");
        saveDefaultLang("zh_TW");

        // Load the configured language file
        File langFile = new File(plugin.getDataFolder(),
                "lang/" + currentLang + ".yml");
        if (!langFile.exists()) {
            plugin.getLogger().warning(
                    "Language file not found: " + currentLang
                            + ".yml, falling back to zh_CN");
            currentLang = "zh_CN";
            langFile = new File(plugin.getDataFolder(), "lang/zh_CN.yml");
        }

        langConfig = loadRuntimeLanguageFile(langFile, currentLang);

        // Merge with defaults to fill missing keys
        InputStream defaultStream = plugin.getResource(
                "lang/" + currentLang + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            langConfig.setDefaults(defaults);
            boolean changed = reconcileStringTypeMismatch(defaults);
            if (changed) {
                try {
                    langConfig.save(langFile);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to persist repaired language file: "
                            + langFile.getName());
                }
            }
        }
    }

    private void saveDefaultLang(String lang) {
        File langFile = new File(plugin.getDataFolder(),
                "lang/" + lang + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + lang + ".yml", false);
        }
    }

    private YamlConfiguration loadRuntimeLanguageFile(File langFile, String langCode) {
        try {
            return loadYamlStrict(langFile);
        } catch (Exception first) {
            plugin.getLogger().warning("Cannot parse " + langFile.getPath()
                    + ". Backing it up and restoring bundled " + langCode + ".yml");
            backupBrokenLanguageFile(langFile);
            restoreBundledLanguage(langCode);
            try {
                return loadYamlStrict(langFile);
            } catch (Exception second) {
                plugin.getLogger().severe("Failed to recover language file "
                        + langFile.getPath() + ": " + second.getMessage());
                return new YamlConfiguration();
            }
        }
    }

    private static YamlConfiguration loadYamlStrict(File file) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            config.load(reader);
        }
        return config;
    }

    private void backupBrokenLanguageFile(File langFile) {
        if (!langFile.exists()) {
            return;
        }

        File backup = new File(langFile.getParentFile(),
                langFile.getName() + ".broken." + System.currentTimeMillis());
        if (!langFile.renameTo(backup)) {
            plugin.getLogger().warning("Failed to backup broken language file: "
                    + langFile.getName());
            if (!langFile.delete()) {
                plugin.getLogger().warning("Also failed to delete broken language file: "
                        + langFile.getName());
            }
            return;
        }

        plugin.getLogger().warning("Backed up broken language file to: "
                + backup.getName());
    }

    private void restoreBundledLanguage(String langCode) {
        try {
            plugin.saveResource("lang/" + langCode + ".yml", true);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to restore bundled language file "
                    + langCode + ".yml: " + e.getMessage());
        }
    }

    public Component getMessage(String key) {
        String prefix = resolveLangString("prefix",
                "&7[&bInvBackup&7] ");
        String msg = resolveLangString(key, key);
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(prefix + msg);
    }

    /**
     * GUI texts should NOT include the chat prefix.
     * Supports simple {placeholder} replacements via string pairs.
     * Example: getGuiMessage("gui.admin.title-players", "{page}","1","{total}","3")
     */
    public Component getGuiMessage(String key, String... replacements) {
        String msg = resolveLangString(key, key);
        msg = applyReplacements(msg, replacements);
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(msg)
                .decoration(TextDecoration.ITALIC, false);
    }

    public String getRawMessage(String key) {
        return resolveLangString(key, key);
    }

    public String getCurrentLang() {
        return currentLang;
    }

    private static String applyReplacements(String msg, String... replacements) {
        if (msg == null) return "";
        if (replacements == null) return msg;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            String from = replacements[i];
            String to = replacements[i + 1];
            if (from != null && to != null) {
                msg = msg.replace(from, to);
            }
        }
        return msg;
    }

    private String resolveLangString(String key, String fallback) {
        if (langConfig == null) {
            return fallback;
        }

        Object raw = langConfig.get(key);
        if (raw instanceof String s) {
            return s;
        }

        if (raw instanceof ConfigurationSection section) {
            String nested = pickSectionText(section);
            if (nested != null && !nested.isBlank()) {
                return nested;
            }

            warnInvalidTypeOnce(key);
            return fallback;
        }

        if (raw != null) {
            warnInvalidTypeOnce(key);
            return String.valueOf(raw);
        }

        String fromDefaults = langConfig.getString(key);
        return fromDefaults != null ? fromDefaults : fallback;
    }

    private static String pickSectionText(ConfigurationSection section) {
        String[] preferredKeys = {"name", "text", "title", "value", "label"};
        for (String k : preferredKeys) {
            if (section.isString(k)) {
                return section.getString(k);
            }
        }
        return null;
    }

    private void warnInvalidTypeOnce(String key) {
        if (warnedInvalidTypeKeys.add(key)) {
            plugin.getLogger().warning("Language key '" + key
                    + "' is not a string in " + currentLang
                    + ".yml; using fallback.");
        }
    }

    /**
     * Repair runtime lang config when an existing key has wrong type
     * (e.g. section instead of string), which otherwise blocks defaults.
     */
    private boolean reconcileStringTypeMismatch(YamlConfiguration defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (!defaults.isString(key)) {
                continue;
            }
            if (langConfig.isConfigurationSection(key)) {
                String def = defaults.getString(key);
                if (def != null) {
                    langConfig.set(key, def);
                    changed = true;
                    warnInvalidTypeOnce(key);
                }
            }
        }
        return changed;
    }
}
