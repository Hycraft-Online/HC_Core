package com.hccore.database;

import com.hypixel.hytale.logger.HytaleLogger;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

/**
 * Shared HikariCP connection pool for all HC plugins.
 * Configured via environment variables (HC_DB_URL, HC_DB_USER, HC_DB_PASSWORD, HC_DB_POOL_SIZE).
 */
public class DatabaseManager {

    private static final HytaleLogger LOGGER = HytaleLogger.getLogger().getSubLogger("HC_Core-DB");

    private static final String DEFAULT_URL = "jdbc:postgresql://postgres:5432/factionwars";
    private static final String DEFAULT_USER = "factionwars";
    private static final String DEFAULT_PASSWORD = "factionwars_secret";
    private static final int DEFAULT_POOL_SIZE = 20;

    private final HikariDataSource dataSource;

    public DatabaseManager() {
        String jdbcUrl = env("HC_DB_URL", DEFAULT_URL);
        String username = env("HC_DB_USER", DEFAULT_USER);
        String password = env("HC_DB_PASSWORD", DEFAULT_PASSWORD);
        int poolSize = envInt("HC_DB_POOL_SIZE", DEFAULT_POOL_SIZE);

        LOGGER.at(Level.INFO).log("Initializing shared connection pool (url=" + jdbcUrl + ", user=" + username + ", poolSize=" + poolSize + ")");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setDriverClassName("org.postgresql.Driver");
        config.setConnectionTimeout(10000);
        config.setValidationTimeout(5000);
        config.setInitializationFailTimeout(10000);
        config.setPoolName("HC-Core-Pool");

        this.dataSource = new HikariDataSource(config);

        // Test connection
        try (Connection conn = dataSource.getConnection()) {
            if (conn.isValid(2)) {
                LOGGER.at(Level.INFO).log("Database connection test successful");
            }
        } catch (SQLException e) {
            LOGGER.at(Level.SEVERE).log("Database connection test failed: " + e.getMessage());
            throw new RuntimeException("Failed to initialize database connection pool", e);
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            LOGGER.at(Level.INFO).log("Closing shared connection pool...");
            dataSource.close();
        }
    }

    private static String env(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val != null && !val.isBlank()) ? val : defaultValue;
    }

    private static int envInt(String key, int defaultValue) {
        String val = System.getenv(key);
        if (val != null && !val.isBlank()) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                LOGGER.at(Level.WARNING).log("Invalid integer for " + key + ": " + val + ", using default " + defaultValue);
            }
        }
        return defaultValue;
    }
}
