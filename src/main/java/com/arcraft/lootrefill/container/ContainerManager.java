package com.arcraft.lootrefill.container;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class ContainerManager {

    private final LootRefillPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Map<String, LootContainer> containersByKey;
    private final Map<UUID, LootContainer> containersById;

    public ContainerManager(LootRefillPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.containersByKey = new ConcurrentHashMap<>();
        this.containersById = new ConcurrentHashMap<>();
    }

    public void load() {
        containersByKey.clear();
        containersById.clear();

        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM containers")) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String world = rs.getString("world");
                int x = rs.getInt("x");
                int y = rs.getInt("y");
                int z = rs.getInt("z");
                ContainerType type = ContainerType.fromString(rs.getString("container_type"));
                String tableId = rs.getString("loot_table_id");
                if (tableId != null && tableId.trim().isEmpty()) {
                    tableId = null;
                }
                String poolId = rs.getString("loot_pool_id");
                if (poolId != null && poolId.trim().isEmpty()) {
                    poolId = null;
                }
                boolean enabled = rs.getInt("enabled") == 1;
                boolean looted = rs.getInt("looted") == 1;
                long lastLoot = rs.getLong("last_loot");
                Long nextRefill = rs.getObject("next_refill") != null ? rs.getLong("next_refill") : null;
                boolean refillEnabled = rs.getInt("refill_enabled") == 1;
                int refillInterval = rs.getInt("refill_interval_seconds");

                ContainerSource source = ContainerSource.fromString(rs.getString("source"));
                ContainerStatus status = ContainerStatus.fromString(rs.getString("status"));
                boolean managed = rs.getInt("managed") == 1;
                boolean registered = rs.getInt("registered") == 1;
                long createdAt = rs.getLong("created_at");
                long updatedAt = rs.getLong("updated_at");

                LootContainer container = new LootContainer(id, world, x, y, z, type, tableId, poolId, enabled, looted, lastLoot, nextRefill, refillEnabled, refillInterval, source, status, managed, registered, createdAt, updatedAt);
                containersById.put(id, container);
                containersByKey.put(container.getLocationKey(), container);
            }
            plugin.getLogger().info("Se cargaron " + containersById.size() + " contenedores registrados en memoria (Etapa 2.1).");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al cargar los contenedores desde SQLite", e);
        }
    }

    public LootContainer getContainer(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        String key = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        return containersByKey.get(key);
    }

    public LootContainer getContainer(UUID id) {
        return containersById.get(id);
    }

    public Collection<LootContainer> getAllContainers() {
        return Collections.unmodifiableCollection(containersById.values());
    }

    public void registerContainer(LootContainer container, boolean saveAsync) {
        if (container == null) return;
        containersById.put(container.getId(), container);
        containersByKey.put(container.getLocationKey(), container);

        if (saveAsync) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveContainer(container));
        } else {
            saveContainer(container);
        }
    }

    public void unregisterContainer(UUID id) {
        LootContainer c = containersById.remove(id);
        if (c != null) {
            containersByKey.remove(c.getLocationKey());
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteContainerFromDb(id));
        }
    }

    public void saveContainer(LootContainer container) {
        String sql = """
            INSERT INTO containers (id, world, x, y, z, container_type, loot_table_id, loot_pool_id, enabled, looted, last_loot, next_refill, refill_enabled, refill_interval_seconds, source, status, managed, registered, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(world, x, y, z) DO UPDATE SET
                container_type = excluded.container_type,
                loot_table_id = excluded.loot_table_id,
                loot_pool_id = excluded.loot_pool_id,
                enabled = excluded.enabled,
                looted = excluded.looted,
                last_loot = excluded.last_loot,
                next_refill = excluded.next_refill,
                refill_enabled = excluded.refill_enabled,
                refill_interval_seconds = excluded.refill_interval_seconds,
                source = excluded.source,
                status = excluded.status,
                managed = excluded.managed,
                registered = excluded.registered,
                updated_at = excluded.updated_at;
        """;

        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            bindContainerStatement(stmt, container);
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar el contenedor " + container.getId(), e);
        }
    }

    /**
     * Inserción y actualización masiva por lotes durante escaneos.
     * Protección estricta: Jamás sobreescribe contenedores de tipo PLAYER ni contenedores BROKEN a MAP.
     */
    public int saveContainersBatch(List<LootContainer> batch) {
        if (batch == null || batch.isEmpty()) return 0;

        String sql = """
            INSERT INTO containers (id, world, x, y, z, container_type, loot_table_id, loot_pool_id, enabled, looted, last_loot, next_refill, refill_enabled, refill_interval_seconds, source, status, managed, registered, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(world, x, y, z) DO UPDATE SET
                container_type = excluded.container_type,
                loot_table_id = CASE WHEN containers.source = 'PLAYER' THEN NULL ELSE excluded.loot_table_id END,
                loot_pool_id = CASE WHEN containers.source = 'PLAYER' THEN NULL ELSE excluded.loot_pool_id END,
                enabled = CASE WHEN containers.status = 'BROKEN' THEN 0 ELSE excluded.enabled END,
                looted = excluded.looted,
                last_loot = excluded.last_loot,
                next_refill = excluded.next_refill,
                refill_enabled = CASE WHEN containers.source = 'PLAYER' OR containers.status = 'BROKEN' THEN 0 ELSE excluded.refill_enabled END,
                refill_interval_seconds = excluded.refill_interval_seconds,
                source = CASE WHEN containers.source = 'PLAYER' THEN 'PLAYER' ELSE excluded.source END,
                status = CASE WHEN containers.status = 'BROKEN' THEN 'BROKEN' ELSE excluded.status END,
                managed = CASE WHEN containers.source = 'PLAYER' OR containers.status = 'BROKEN' THEN 0 ELSE excluded.managed END,
                registered = CASE WHEN containers.source = 'PLAYER' THEN 0 ELSE excluded.registered END,
                updated_at = excluded.updated_at;
        """;

        int count = 0;
        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (LootContainer container : batch) {
                    bindContainerStatement(stmt, container);
                    stmt.addBatch();
                    count++;

                    // Preservar en memoria si ya era PLAYER o BROKEN
                    LootContainer existing = containersByKey.get(container.getLocationKey());
                    if (existing != null) {
                        if (existing.getSource() == ContainerSource.PLAYER) {
                            container.setSource(ContainerSource.PLAYER);
                            container.setManaged(false);
                            container.setRegistered(false);
                        }
                        if (existing.getStatus() == ContainerStatus.BROKEN) {
                            container.setStatus(ContainerStatus.BROKEN);
                            container.setManaged(false);
                            container.setRefillEnabled(false);
                        }
                    }

                    containersById.put(container.getId(), container);
                    containersByKey.put(container.getLocationKey(), container);
                }
                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error en guardado por lotes de contenedores", e);
        }
        return count;
    }

    /**
     * Convierte explícitamente contenedores PLAYER a MAP bajo orden administrativa.
     * Conserva el inventario intacto, no limpia ítems y no genera loot durante la conversión.
     */
    public int convertPlayerContainersToMap(List<LootContainer> containers) {
        if (containers == null || containers.isEmpty()) return 0;

        String sql = """
            UPDATE containers SET
                source = 'MAP',
                status = 'ACTIVE',
                managed = 1,
                registered = 1,
                loot_table_id = NULL,
                loot_pool_id = NULL,
                next_refill = NULL,
                updated_at = ?
            WHERE id = ?
        """;

        int count = 0;
        long now = System.currentTimeMillis();

        try (Connection conn = databaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (LootContainer c : containers) {
                    c.setSource(ContainerSource.MAP);
                    c.setStatus(ContainerStatus.ACTIVE);
                    c.setManaged(true);
                    c.setRegistered(true);
                    c.setLootTableId(null);
                    c.setLootPoolId(null);
                    c.setNextRefill(null);
                    c.setUpdatedAt(now);

                    stmt.setLong(1, now);
                    stmt.setString(2, c.getId().toString());
                    stmt.addBatch();
                    count++;

                    containersById.put(c.getId(), c);
                    containersByKey.put(c.getLocationKey(), c);
                }
                stmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al convertir contenedores PLAYER a MAP", e);
        }
        return count;
    }

    private void bindContainerStatement(PreparedStatement stmt, LootContainer container) throws SQLException {
        stmt.setString(1, container.getId().toString());
        stmt.setString(2, container.getWorld());
        stmt.setInt(3, container.getX());
        stmt.setInt(4, container.getY());
        stmt.setInt(5, container.getZ());
        stmt.setString(6, container.getContainerType().name());
        if (container.getLootTableId() == null || container.getLootTableId().trim().isEmpty()) {
            stmt.setNull(7, java.sql.Types.VARCHAR);
        } else {
            stmt.setString(7, container.getLootTableId());
        }
        if (container.getLootPoolId() == null || container.getLootPoolId().trim().isEmpty()) {
            stmt.setNull(8, java.sql.Types.VARCHAR);
        } else {
            stmt.setString(8, container.getLootPoolId());
        }
        stmt.setInt(9, container.isEnabled() ? 1 : 0);
        stmt.setInt(10, container.isLooted() ? 1 : 0);
        stmt.setLong(11, container.getLastLoot());
        if (container.getNextRefill() == null) {
            stmt.setNull(12, java.sql.Types.BIGINT);
        } else {
            stmt.setLong(12, container.getNextRefill());
        }
        stmt.setInt(13, container.isRefillEnabled() ? 1 : 0);
        stmt.setInt(14, container.getRefillIntervalSeconds());
        stmt.setString(15, container.getSource().name());
        stmt.setString(16, container.getStatus().name());
        stmt.setInt(17, container.isManaged() ? 1 : 0);
        stmt.setInt(18, container.isRegistered() ? 1 : 0);
        stmt.setLong(19, container.getCreatedAt());
        stmt.setLong(20, container.getUpdatedAt());
    }

    private void deleteContainerFromDb(UUID id) {
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM containers WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al eliminar el contenedor " + id, e);
        }
    }

    public boolean isAllowedContainerBlock(Block block) {
        if (block == null) return false;
        ContainerType type = ContainerType.fromBlock(block);
        if (type == null) return false;
        return isContainerTypeEnabled(type);
    }

    public boolean isContainerTypeEnabled(ContainerType type) {
        if (type == null) return false;
        return plugin.getConfig().getBoolean("containers." + type.getConfigKey() + ".enabled", true);
    }

    public int getCountByType(ContainerType type) {
        int count = 0;
        for (LootContainer c : containersById.values()) {
            if (c.getContainerType() == type) {
                count++;
            }
        }
        return count;
    }

    public int getCountBySourceAndType(ContainerSource source, ContainerType type) {
        int count = 0;
        for (LootContainer c : containersById.values()) {
            if (c.getSource() == source && c.getContainerType() == type) {
                count++;
            }
        }
        return count;
    }

    public int getMapContainerCount() {
        int count = 0;
        for (LootContainer c : containersById.values()) {
            if (c.getSource() == ContainerSource.MAP) {
                count++;
            }
        }
        return count;
    }

    public int getPlayerContainerCount() {
        int count = 0;
        for (LootContainer c : containersById.values()) {
            if (c.getSource() == ContainerSource.PLAYER) {
                count++;
            }
        }
        return count;
    }

    public int getBrokenContainerCount() {
        int count = 0;
        for (LootContainer c : containersById.values()) {
            if (c.getStatus() == ContainerStatus.BROKEN) {
                count++;
            }
        }
        return count;
    }

    public int getDisabledContainerCount() {
        int count = 0;
        for (LootContainer c : containersById.values()) {
            if (c.getStatus() == ContainerStatus.DISABLED) {
                count++;
            }
        }
        return count;
    }

    public int getAdministrableCount() {
        int count = 0;
        for (LootContainer c : containersById.values()) {
            if (c.isAdministrable()) {
                count++;
            }
        }
        return count;
    }

    public int getChestCount() {
        return getCountByType(ContainerType.CHEST) + getCountByType(ContainerType.TRAPPED_CHEST);
    }

    public int getBarrelCount() {
        return getCountByType(ContainerType.BARREL);
    }

    public int getFurnaceCount() {
        return getCountByType(ContainerType.FURNACE) + getCountByType(ContainerType.BLAST_FURNACE) + getCountByType(ContainerType.SMOKER);
    }

    public int getDispenserCount() {
        return getCountByType(ContainerType.DISPENSER) + getCountByType(ContainerType.DROPPER);
    }

    /**
     * Solo devuelve contenedores que cumplen estrictamente:
     * source = 'MAP', status = 'ACTIVE', managed = 1, registered = 1, refill_enabled = 1, loot_table_id no nulo.
     * Los contenedores PLAYER, BROKEN, DISABLED o UNKNOWN quedan 100% excluidos.
     */
    public List<LootContainer> getContainersDueForRefill(ContainerType type, long currentTime, int limit) {
        List<LootContainer> list = new ArrayList<>();
        String sql = """
            SELECT id FROM containers
            WHERE container_type = ?
            AND source = 'MAP'
            AND status = 'ACTIVE'
            AND managed = 1
            AND registered = 1
            AND refill_enabled = 1
            AND ((loot_table_id IS NOT NULL AND loot_table_id != '') OR (loot_pool_id IS NOT NULL AND loot_pool_id != ''))
            AND next_refill IS NOT NULL
            AND next_refill <= ?
            ORDER BY next_refill ASC
            LIMIT ?
        """;

        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, type.name());
            stmt.setLong(2, currentTime);
            stmt.setInt(3, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                LootContainer c = containersById.get(id);
                if (c != null && c.isEligibleForRefill()) {
                    list.add(c);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error consultando contenedores para refill de tipo " + type, e);
        }
        return list;
    }

    /**
     * Devuelve la cantidad de contenedores MAP vencidos esperando refill.
     */
    public int getDueContainersCount(long currentTime) {
        String sql = """
            SELECT COUNT(*) FROM containers
            WHERE source = 'MAP'
            AND status = 'ACTIVE'
            AND managed = 1
            AND registered = 1
            AND refill_enabled = 1
            AND ((loot_table_id IS NOT NULL AND loot_table_id != '') OR (loot_pool_id IS NOT NULL AND loot_pool_id != ''))
            AND next_refill IS NOT NULL
            AND next_refill <= ?
        """;
        try (Connection conn = databaseManager.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, currentTime);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error contando contenedores vencidos para refill", e);
        }
        return 0;
    }

    /**
     * Obtiene el desglose de estadísticas de contenedores (MAP, PLAYER, BROKEN, Total) para un mundo específico.
     */
    public ContainerWorldStats getContainerStatsByWorld(String worldName) {
        return databaseManager.getContainerStatsByWorld(worldName);
    }

    /**
     * Elimina exclusivamente los contenedores del mundo indicado en SQLite y limpia la memoria.
     */
    public int resetContainersByWorld(String worldName) {
        int count = databaseManager.resetContainersByWorld(worldName);

        // Limpiar de la memoria únicamente los contenedores de ese mundo
        List<UUID> toRemove = new ArrayList<>();
        for (LootContainer c : containersById.values()) {
            if (c.getWorld().equalsIgnoreCase(worldName)) {
                toRemove.add(c.getId());
            }
        }

        for (UUID id : toRemove) {
            LootContainer c = containersById.remove(id);
            if (c != null) {
                containersByKey.remove(c.getLocationKey());
            }
        }

        return count;
    }
}
