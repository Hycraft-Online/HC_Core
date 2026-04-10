# HC_Core

Centralized database connection pool, settings management, and schema migrations for all HC plugins. HC_Core provides a shared PostgreSQL connection pool via HikariCP and a key-value settings API backed by a `mod_settings` database table. Other HC plugins depend on HC_Core to avoid each managing their own database connections.

## Features

- Shared PostgreSQL connection pool (HikariCP) accessible via `HC_CoreAPI.getConnection()`
- Key-value settings API with per-plugin namespacing and typed getters (string, int, boolean, double)
- Default settings registration so plugins can declare their configuration on startup
- In-memory settings cache with on-demand refresh via the `/settingsreload` command
- Automatic `mod_settings` table creation on first run

## Dependencies

- EntityModule (Hytale built-in)

## Building

```
./gradlew build
```
