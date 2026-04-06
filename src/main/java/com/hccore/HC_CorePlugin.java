package com.hccore;

import com.hccore.api.HC_CoreAPI;
import com.hccore.database.DatabaseManager;
import com.hccore.database.ModSettingsRepository;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;

import java.util.logging.Level;

/**
 * HC_Core — Centralized database pool and settings for all HC plugins.
 */
public class HC_CorePlugin extends JavaPlugin {

    public static final String VERSION = "1.0.0";

    private DatabaseManager databaseManager;

    public HC_CorePlugin(@NonNullDecl JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        super.setup();

        getLogger().at(Level.INFO).log("=================================");
        getLogger().at(Level.INFO).log("        HC_CORE " + VERSION);
        getLogger().at(Level.INFO).log("=================================");

        // ═══════════════════════════════════════════════════════
        // DATABASE INITIALIZATION
        // ═══════════════════════════════════════════════════════
        try {
            databaseManager = new DatabaseManager();
            getLogger().at(Level.INFO).log("Shared database pool initialized");
        } catch (Exception e) {
            getLogger().at(Level.SEVERE).log("Failed to initialize database: " + e.getMessage());
            getLogger().at(Level.SEVERE).log("HC_Core cannot function without a database — dependent plugins will fail!");
            return;
        }

        // ═══════════════════════════════════════════════════════
        // SETTINGS REPOSITORY (creates mod_settings table if needed)
        // ═══════════════════════════════════════════════════════
        ModSettingsRepository settingsRepository = new ModSettingsRepository(databaseManager);
        getLogger().at(Level.INFO).log("Settings repository initialized");

        // ═══════════════════════════════════════════════════════
        // STATIC API INITIALIZATION
        // ═══════════════════════════════════════════════════════
        HC_CoreAPI.initialize(databaseManager, settingsRepository);
        getLogger().at(Level.INFO).log("HC_CoreAPI ready for dependent plugins");

        // ═══════════════════════════════════════════════════════
        // COMMANDS
        // ═══════════════════════════════════════════════════════
        getCommandRegistry().registerCommand(new SettingsReloadCommand());
        getLogger().at(Level.INFO).log("Registered /settingsreload command");

        getLogger().at(Level.INFO).log("HC_Core enabled successfully!");
        getLogger().at(Level.INFO).log("=================================");
    }

    @Override
    protected void shutdown() {
        super.shutdown();

        HC_CoreAPI.shutdown();

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().at(Level.INFO).log("HC_Core disabled");
    }

    // ═══════════════════════════════════════════════════════
    // COMMANDS
    // ═══════════════════════════════════════════════════════

    private static class SettingsReloadCommand extends AbstractPlayerCommand {
        SettingsReloadCommand() {
            super("settingsreload", "Refresh the mod settings cache from database");
        }

        @Override
        protected void execute(@NonNullDecl CommandContext ctx, @NonNullDecl Store<EntityStore> store,
                               @NonNullDecl Ref<EntityStore> ref, @NonNullDecl PlayerRef player,
                               @NonNullDecl World world) {
            Player playerEntity = store.getComponent(ref, Player.getComponentType());
            if (playerEntity == null || !playerEntity.hasPermission("*")) {
                player.sendMessage(Message.raw("You must be an operator to use this command.").color("#FF6464"));
                return;
            }
            HC_CoreAPI.refreshSettings();
            player.sendMessage(Message.raw("[HC_Core] Settings cache refreshed from database.").color("#55FF55"));
        }
    }
}
