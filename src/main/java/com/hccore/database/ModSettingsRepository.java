package com.hccore.database;

import com.hccore.models.SettingDef;
import com.hypixel.hytale.logger.HytaleLogger;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * CRUD operations for the mod_settings table with an in-memory cache.
 * Cache is per-plugin namespace with a configurable TTL.
 */
public class ModSettingsRepository {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Core-Settings");
    private static final long CACHE_TTL_MS = 60_000; // 60 seconds

    private final DatabaseManager databaseManager;

    // Cache: plugin -> (key -> value)
    private final ConcurrentHashMap<String, Map<String, String>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheTimestamps = new ConcurrentHashMap<>();

    public ModSettingsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
        ensureTable();
    }

    /**
     * Bootstrap: creates mod_settings table if it doesn't exist.
     * This runs before any migrations or settings access, so we don't depend
     * on the migration runner (which itself needs a working DB).
     */
    private void ensureTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS mod_settings (
                plugin      VARCHAR(64)  NOT NULL,
                key         VARCHAR(128) NOT NULL,
                value       TEXT,
                value_type  VARCHAR(16)  DEFAULT 'STRING',
                description TEXT,
                updated_at  TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (plugin, key)
            )
            """;
        try (Connection conn = databaseManager.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to create mod_settings table: " + e.getMessage());
            throw new RuntimeException("Cannot bootstrap mod_settings table", e);
        }
    }

    /**
     * Gets a setting value, returning defaultValue if not found.
     */
    public String getSetting(String plugin, String key, String defaultValue) {
        Map<String, String> pluginSettings = getCachedSettings(plugin);
        return pluginSettings.getOrDefault(key, defaultValue);
    }

    /**
     * Gets a setting value, returning null if not found.
     */
    public String getSetting(String plugin, String key) {
        return getSetting(plugin, key, null);
    }

    /**
     * Gets all settings for a plugin.
     */
    public Map<String, String> getAllSettings(String plugin) {
        return new HashMap<>(getCachedSettings(plugin));
    }

    /**
     * Sets a single setting value and updates the cache.
     */
    public void setSetting(String plugin, String key, String value) {
        String upsertSql = """
            INSERT INTO mod_settings (plugin, key, value, updated_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (plugin, key) DO UPDATE SET
                value = EXCLUDED.value,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {
            stmt.setString(1, plugin);
            stmt.setString(2, key);
            stmt.setString(3, value);
            stmt.executeUpdate();

            // Update cache
            cache.computeIfAbsent(plugin, k -> new ConcurrentHashMap<>()).put(key, value);

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to set setting " + plugin + "." + key + ": " + e.getMessage());
        }
    }

    /**
     * Registers default settings for a plugin. Only inserts if the key doesn't already exist.
     */
    public void registerDefaults(String plugin, Map<String, SettingDef> defaults) {
        String upsertSql = """
            INSERT INTO mod_settings (plugin, key, value, value_type, description, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (plugin, key) DO UPDATE SET
                value_type = COALESCE(EXCLUDED.value_type, mod_settings.value_type),
                description = COALESCE(EXCLUDED.description, mod_settings.description)
            """;

        int inserted = 0;
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(upsertSql)) {

            for (Map.Entry<String, SettingDef> entry : defaults.entrySet()) {
                stmt.setString(1, plugin);
                stmt.setString(2, entry.getKey());
                stmt.setString(3, entry.getValue().getDefaultValue());
                stmt.setString(4, entry.getValue().getValueType());
                stmt.setString(5, entry.getValue().getDescription());
                stmt.executeUpdate();
                inserted++;
            }

            LOGGER.at(Level.INFO).log("Registered " + inserted + " default settings for " + plugin);

        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to register defaults for " + plugin + ": " + e.getMessage());
        }

        // Invalidate cache to pick up DB values
        cache.remove(plugin);
        cacheTimestamps.remove(plugin);
    }

    /**
     * Forces a cache reload for all plugins.
     */
    public void refreshAll() {
        cache.clear();
        cacheTimestamps.clear();
        LOGGER.at(Level.INFO).log("Settings cache cleared — will reload on next access");
    }

    /**
     * Returns cached settings for a plugin, reloading from DB if stale.
     */
    private Map<String, String> getCachedSettings(String plugin) {
        Long timestamp = cacheTimestamps.get(plugin);
        if (timestamp != null && (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS && cache.containsKey(plugin)) {
            return cache.get(plugin);
        }
        return loadFromDb(plugin);
    }

    private Map<String, String> loadFromDb(String plugin) {
        Map<String, String> settings = new ConcurrentHashMap<>();
        String sql = "SELECT key, value FROM mod_settings WHERE plugin = ?";

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, plugin);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String val = rs.getString("value");
                    if (val != null) {
                        settings.put(rs.getString("key"), val);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Failed to load settings for " + plugin + ": " + e.getMessage());
            // Return whatever is in cache if DB fails
            Map<String, String> existing = cache.get(plugin);
            if (existing != null) return existing;
        }

        cache.put(plugin, settings);
        cacheTimestamps.put(plugin, System.currentTimeMillis());
        return settings;
    }
}
