package com.hccore.api;

import com.hccore.database.DatabaseManager;
import com.hccore.database.ModSettingsRepository;
import com.hccore.models.SettingDef;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Static API for other HC plugins to access shared database and settings.
 * <p>
 * Usage from other plugins:
 * <pre>
 *   Connection conn = HC_CoreAPI.getConnection();
 *   String value = HC_CoreAPI.getSetting("HC_Factions", "guild.maxMembers", "30");
 * </pre>
 */
public class HC_CoreAPI {

    private static volatile DatabaseManager databaseManager;
    private static volatile ModSettingsRepository settingsRepository;

    // Called by HC_CorePlugin during setup — not for external plugin use
    public static void initialize(DatabaseManager dbManager, ModSettingsRepository settingsRepo) {
        databaseManager = dbManager;
        settingsRepository = settingsRepo;
    }

    public static void shutdown() {
        databaseManager = null;
        settingsRepository = null;
    }

    // ═══════════════════════════════════════════════════════
    // CONNECTION POOL
    // ═══════════════════════════════════════════════════════

    /**
     * Gets a connection from the shared pool.
     * Callers MUST close the connection when done (use try-with-resources).
     */
    public static Connection getConnection() throws SQLException {
        if (databaseManager == null) {
            throw new IllegalStateException("HC_Core is not initialized — ensure HC_Core is loaded before your plugin");
        }
        return databaseManager.getConnection();
    }

    // ═══════════════════════════════════════════════════════
    // SETTINGS
    // ═══════════════════════════════════════════════════════

    /**
     * Gets a setting value, returning null if not found.
     */
    public static String getSetting(String plugin, String key) {
        ensureSettingsReady();
        return settingsRepository.getSetting(plugin, key);
    }

    /**
     * Gets a setting value, returning defaultValue if not found.
     */
    public static String getSetting(String plugin, String key, String defaultValue) {
        ensureSettingsReady();
        return settingsRepository.getSetting(plugin, key, defaultValue);
    }

    /**
     * Gets an integer setting, returning defaultValue if not found or not parseable.
     */
    public static int getSettingInt(String plugin, String key, int defaultValue) {
        String val = getSetting(plugin, key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Gets a boolean setting, returning defaultValue if not found.
     */
    public static boolean getSettingBool(String plugin, String key, boolean defaultValue) {
        String val = getSetting(plugin, key);
        if (val == null) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    /**
     * Gets a double setting, returning defaultValue if not found or not parseable.
     */
    public static double getSettingDouble(String plugin, String key, double defaultValue) {
        String val = getSetting(plugin, key);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Sets a setting value (writes through to DB immediately).
     */
    public static void setSetting(String plugin, String key, String value) {
        ensureSettingsReady();
        settingsRepository.setSetting(plugin, key, value);
    }

    /**
     * Registers default settings for a plugin. Keys that already exist in DB keep their current value;
     * only missing keys are inserted with the default.
     */
    public static void registerDefaults(String plugin, Map<String, SettingDef> defaults) {
        ensureSettingsReady();
        settingsRepository.registerDefaults(plugin, defaults);
    }

    /**
     * Gets all settings for a plugin as a map.
     */
    public static Map<String, String> getAllSettings(String plugin) {
        ensureSettingsReady();
        return settingsRepository.getAllSettings(plugin);
    }

    /**
     * Forces an immediate cache reload (called by /settingsreload command).
     */
    public static void refreshSettings() {
        ensureSettingsReady();
        settingsRepository.refreshAll();
    }

    private static void ensureSettingsReady() {
        if (settingsRepository == null) {
            throw new IllegalStateException("HC_Core is not initialized — ensure HC_Core is loaded before your plugin");
        }
    }
}
