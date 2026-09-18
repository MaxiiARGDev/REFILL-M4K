package com.arcraft.lootrefill.pool;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.storage.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class LootPoolStorage {

    private final LootRefillPlugin plugin;
    private final DatabaseManager databaseManager;

    public LootPoolStorage(LootRefillPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    public Map<String, LootPool> loadAllPools() {
        Map<String, LootPool> pools = new HashMap<>();

        try (Connection conn = databaseManager.getConnection()) {
            // Cargar loot_pools
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM loot_pools")) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String id = rs.getString("id");
                    String name = rs.getString("name");
                    PoolSelectionMode mode = PoolSelectionMode.fromString(rs.getString("selection_mode"));
                    int minRolls = rs.getInt("min_rolls");
                    int maxRolls = rs.getInt("max_rolls");
                    boolean allowDuplicates = false;
                    try {
                        allowDuplicates = rs.getInt("allow_duplicates") == 1;
                    } catch (SQLException ignored) {}
                    boolean enabled = rs.getInt("enabled") == 1;
                    long createdAt = rs.getLong("created_at");
                    long updatedAt = rs.getLong("updated_at");

                    LootPool pool = new LootPool(id, name, mode, minRolls, maxRolls, allowDuplicates, enabled, createdAt, updatedAt);
                    pools.put(id.toLowerCase(), pool);
                }
            }

            // Cargar loot_pool_entries
            try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM loot_pool_entries")) {
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String idStr = rs.getString("id");
                    String poolId = rs.getString("pool_id");
                    String tableId = rs.getString("table_id");
                    int weight = rs.getInt("weight");

                    LootPool pool = pools.get(poolId.toLowerCase());
                    if (pool != null) {
                        UUID uuid;
                        try {
                            uuid = UUID.fromString(idStr);
                        } catch (IllegalArgumentException e) {
                            uuid = UUID.randomUUID();
                        }
                        LootPoolEntry entry = new LootPoolEntry(uuid, poolId, tableId, weight);
                        pool.addEntry(entry);
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al cargar los Loot Pools desde SQLite", e);
        }

        return pools;
    }

    public void savePool(LootPool pool) {
        if (pool == null) return;

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try {
                // Upsert loot_pools
                String sqlPool = """
                    INSERT INTO loot_pools (id, name, selection_mode, min_rolls, max_rolls, allow_duplicates, enabled, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        name = excluded.name,
                        selection_mode = excluded.selection_mode,
                        min_rolls = excluded.min_rolls,
                        max_rolls = excluded.max_rolls,
                        allow_duplicates = excluded.allow_duplicates,
                        enabled = excluded.enabled,
                        updated_at = excluded.updated_at;
                """;
                try (PreparedStatement stmt = conn.prepareStatement(sqlPool)) {
                    stmt.setString(1, pool.getId());
                    stmt.setString(2, pool.getName());
                    stmt.setString(3, pool.getSelectionMode().name());
                    stmt.setInt(4, pool.getMinRolls());
                    stmt.setInt(5, pool.getMaxRolls());
                    stmt.setInt(6, pool.isAllowDuplicates() ? 1 : 0);
                    stmt.setInt(7, pool.isEnabled() ? 1 : 0);
                    stmt.setLong(8, pool.getCreatedAt());
                    stmt.setLong(9, pool.getUpdatedAt());
                    stmt.executeUpdate();
                }

                // Borrar entradas anteriores y reinsertar las actuales
                try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM loot_pool_entries WHERE pool_id = ?")) {
                    stmt.setString(1, pool.getId());
                    stmt.executeUpdate();
                }

                String sqlEntry = """
                    INSERT INTO loot_pool_entries (id, pool_id, table_id, weight)
                    VALUES (?, ?, ?, ?);
                """;
                try (PreparedStatement stmt = conn.prepareStatement(sqlEntry)) {
                    for (LootPoolEntry entry : pool.getEntries()) {
                        stmt.setString(1, entry.getId().toString());
                        stmt.setString(2, pool.getId());
                        stmt.setString(3, entry.getTableId());
                        stmt.setInt(4, entry.getWeight());
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
            plugin.getLogger().log(Level.SEVERE, "Error al guardar el Loot Pool: " + pool.getId(), e);
        }
    }

    public void deletePool(String poolId) {
        if (poolId == null) return;
        try (Connection conn = databaseManager.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM loot_pools WHERE id = ?")) {
                stmt.setString(1, poolId.toLowerCase());
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM loot_pool_entries WHERE pool_id = ?")) {
                stmt.setString(1, poolId.toLowerCase());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al eliminar el Loot Pool: " + poolId, e);
        }
    }
}
