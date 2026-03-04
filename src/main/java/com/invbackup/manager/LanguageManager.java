package com.invbackup.manager;

import com.invbackup.InvBackup;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

public class LanguageManager {

    private final InvBackup plugin;
    private YamlConfiguration langConfig;
    private String currentLang;

    public LanguageManager(InvBackup plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        currentLang = plugin.getConfig().getString("language", "zh_CN");

        // Save built-in language files
        saveDefaultLang("zh_CN");
        saveDefaultLang("en_US");

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

        langConfig = YamlConfiguration.loadConfiguration(langFile);

        // Merge with defaults to fill missing keys
        InputStream defaultStream = plugin.getResource(
                "lang/" + currentLang + ".yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            langConfig.setDefaults(defaults);
        }
    }

    private void saveDefaultLang(String lang) {
        File langFile = new File(plugin.getDataFolder(),
                "lang/" + lang + ".yml");
        if (!langFile.exists()) {
            plugin.saveResource("lang/" + lang + ".yml", false);
        }
    }

    public Component getMessage(String key) {
        String prefix = langConfig.getString("prefix",
                "&7[&bInvBackup&7] ");
        String msg = langConfig.getString(key, key);
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(prefix + msg);
    }

    public String getRawMessage(String key) {
        return langConfig.getString(key, key);
    }

    public String getCurrentLang() {
        return currentLang;
    }
}
