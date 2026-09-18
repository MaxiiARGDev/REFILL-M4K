package com.arcraft.lootrefill.loot;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.storage.DatabaseManager;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class LootTableStorage {

    private final LootRefillPlugin plugin;
    private final DatabaseManager databaseManager;

    public LootTableStorage(LootRefillPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public Map<String, LootTable> loadAllTables() {
        Map<String, LootTable> tables = new HashMap<>();

        try (Connection conn = databaseManager.getConnection()) {
            // Cargar tablas
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM loot_tables")) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String id = rs.getString("id");
                    String displayName = rs.getString("display_name");
                    String description = rs.getString("description");
                    boolean enabled = rs.getInt("enabled") == 1;
                    int minItems = rs.getInt("min_items");
                    int maxItems = rs.getInt("max_items");

                    LootTable table = new LootTable(id, displayName, description, enabled, minItems, maxItems);
                    tables.put(id.toLowerCase(), table);
                }
            }

            // Cargar entradas asociadas
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM loot_entries")) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String idStr = rs.getString("id");
                    String tableId = rs.getString("table_id");
                    String itemData = rs.getString("item_data");
                    int weight = rs.getInt("weight");
                    int minAmount = rs.getInt("min_amount");
                    int maxAmount = rs.getInt("max_amount");
                    boolean enabled = rs.getInt("enabled") == 1;
                    String customType = rs.getString("custom_type");
                    String command = rs.getString("command");

                    LootTable table = tables.get(tableId.toLowerCase());
                    if (table != null) {
                        UUID uuid = UUID.fromString(idStr);
                        ItemStack item = LootEntry.deserializeItemFromBase64(itemData);
                        LootEntry entry = new LootEntry(uuid, item, weight, minAmount, maxAmount, enabled, customType, command);
                        table.addEntry(entry);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al cargar las Loot Tables desde la base de datos", e);
        }

        return tables;
    }

    public void saveTable(LootTable table) {
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Upsert tabla
                String sqlTable = """
                    INSERT INTO loot_tables (id, display_name, description, enabled, min_items, max_items)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        display_name = excluded.display_name,
                        description = excluded.description,
                        enabled = excluded.enabled,
                        min_items = excluded.min_items,
                        max_items = excluded.max_items;
                """;
                try (PreparedStatement stmt = conn.prepareStatement(sqlTable)) {
                    stmt.setString(1, table.getId());
                    stmt.setString(2, table.getDisplayName());
                    stmt.setString(3, table.getDescription());
                    stmt.setInt(4, table.isEnabled() ? 1 : 0);
                    stmt.setInt(5, table.getMinItems());
                    stmt.setInt(6, table.getMaxItems());
                    stmt.executeUpdate();
                }

                // Borrar entradas antiguas de la tabla y reinsertar actuales
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM loot_entries WHERE table_id = ?")) {
                    stmt.setString(1, table.getId());
                    stmt.executeUpdate();
                }

                String sqlEntry = """
                    INSERT INTO loot_entries (id, table_id, item_data, weight, min_amount, max_amount, enabled, custom_type, command)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;
                try (PreparedStatement stmt = conn.prepareStatement(sqlEntry)) {
                    for (LootEntry entry : table.getEntries()) {
                        stmt.setString(1, entry.getId().toString());
                        stmt.setString(2, table.getId());
                        stmt.setString(3, entry.serializeItemToBase64());
                        stmt.setInt(4, entry.getWeight());
                        stmt.setInt(5, entry.getMinAmount());
                        stmt.setInt(6, entry.getMaxAmount());
                        stmt.setInt(7, entry.isEnabled() ? 1 : 0);
                        stmt.setString(8, entry.getCustomType());
                        stmt.setString(9, entry.getCommand());
                        stmt.addBatch();
                    }
                    stmt.executeBatch();
                }

                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar la Loot Table: " + table.getId(), e);
        }
    }

    public void deleteTable(String tableId) {
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM loot_tables WHERE id = ?")) {
                stmt.setString(1, tableId);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM loot_entries WHERE table_id = ?")) {
                stmt.setString(1, tableId);
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al eliminar la Loot Table: " + tableId, e);
        }
    }
}
