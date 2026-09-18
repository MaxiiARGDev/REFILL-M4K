package com.arcraft.lootrefill.storage;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerWorldStats;
import com.arcraft.lootrefill.scanner.ScanJob;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final LootRefillPlugin plugin;
    private final File databaseFile;
    private String url;

    public DatabaseManager(LootRefillPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "data.db");
    }

    public void initialize() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
        }

        this.url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA foreign_keys = ON;");

            // Tabla loot_tables
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS loot_tables (
                    id TEXT PRIMARY KEY,
                    display_name TEXT NOT NULL,
                    description TEXT,
                    enabled INTEGER NOT NULL,
                    min_items INTEGER NOT NULL,
                    max_items INTEGER NOT NULL
                );
            """);

            // Tabla loot_entries
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS loot_entries (
                    id TEXT PRIMARY KEY,
                    table_id TEXT NOT NULL,
                    item_data TEXT NOT NULL,
                    weight INTEGER NOT NULL,
                    min_amount INTEGER NOT NULL,
                    max_amount INTEGER NOT NULL,
                    enabled INTEGER NOT NULL,
                    custom_type TEXT,
                    command TEXT,
                    FOREIGN KEY(table_id) REFERENCES loot_tables(id) ON DELETE CASCADE
                );
            """);

            // Tabla loot_pools (Etapa 4.2)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS loot_pools (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    selection_mode TEXT NOT NULL DEFAULT 'SINGLE_RANDOM',
                    min_rolls INTEGER NOT NULL DEFAULT 1,
                    max_rolls INTEGER NOT NULL DEFAULT 1,
                    allow_duplicates INTEGER NOT NULL DEFAULT 0,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0
                );
            """);

            // Tabla loot_pool_entries (Etapa 4.2)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS loot_pool_entries (
                    id TEXT PRIMARY KEY,
                    pool_id TEXT NOT NULL,
                    table_id TEXT NOT NULL,
                    weight INTEGER NOT NULL DEFAULT 10,
                    FOREIGN KEY(pool_id) REFERENCES loot_pools(id) ON DELETE CASCADE,
                    FOREIGN KEY(table_id) REFERENCES loot_tables(id) ON DELETE CASCADE
                );
            """);

            // Tabla containers con campos completos de Etapa 2.1 y Etapa 4.2
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS containers (
                    id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    x INTEGER NOT NULL,
                    y INTEGER NOT NULL,
                    z INTEGER NOT NULL,
                    container_type TEXT NOT NULL,
                    loot_table_id TEXT,
                    loot_pool_id TEXT,
                    enabled INTEGER NOT NULL,
                    looted INTEGER NOT NULL,
                    last_loot INTEGER NOT NULL,
                    next_refill INTEGER,
                    refill_enabled INTEGER NOT NULL DEFAULT 1,
                    refill_interval_seconds INTEGER NOT NULL DEFAULT 1800,
                    source TEXT NOT NULL DEFAULT 'MAP',
                    status TEXT NOT NULL DEFAULT 'ACTIVE',
                    managed INTEGER NOT NULL DEFAULT 1,
                    registered INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL DEFAULT 0,
                    updated_at INTEGER NOT NULL DEFAULT 0
                );
            """);

            // Migración automática de columnas para bases de datos existentes
            migrateContainersTable(conn);
            migrateLootPoolsTable(conn);

            // Índices espaciales y de rendimiento requeridos
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_containers_loc ON containers(world, x, y, z);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_world ON containers(world);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_world_xz ON containers(world, x, z);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_type ON containers(container_type);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_next_refill ON containers(next_refill);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_loot_table ON containers(loot_table_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_loot_pool ON containers(loot_pool_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_source_status ON containers(source, status, managed);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_refill_eligible ON containers(container_type, source, status, managed, refill_enabled, next_refill);");

            // Tabla scan_jobs para escaneo incremental persistente
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS scan_jobs (
                    id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    current_chunk_x INTEGER NOT NULL,
                    current_chunk_z INTEGER NOT NULL,
                    total_chunks INTEGER NOT NULL,
                    processed_chunks INTEGER NOT NULL,
                    found_containers INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    started_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                );
            """);

            // Tabla populate_jobs para auditoría e historial de Populate
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS populate_jobs (
                    id TEXT PRIMARY KEY,
                    world TEXT NOT NULL,
                    status TEXT NOT NULL,
                    total INTEGER NOT NULL,
                    processed INTEGER NOT NULL,
                    populated INTEGER NOT NULL,
                    skipped INTEGER NOT NULL,
                    started_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    completed_at INTEGER NOT NULL
                );
            """);

            plugin.getLogger().info("Base de datos SQLite y esquema 3.0 inicializados: " + databaseFile.getName());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al inicializar la base de datos SQLite", e);
        }
    }

    private void migrateContainersTable(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(containers);");
            boolean hasRefillEnabled = false;
            boolean hasRefillInterval = false;
            boolean hasSource = false;
            boolean hasStatus = false;
            boolean hasManaged = false;
            boolean hasRegistered = false;
            boolean hasCreatedAt = false;
            boolean hasUpdatedAt = false;
            boolean hasLootPoolId = false;
            boolean nextRefillNotNull = false;

            while (rs.next()) {
                String col = rs.getString("name");
                if ("refill_enabled".equalsIgnoreCase(col)) hasRefillEnabled = true;
                if ("refill_interval_seconds".equalsIgnoreCase(col)) hasRefillInterval = true;
                if ("source".equalsIgnoreCase(col)) hasSource = true;
                if ("status".equalsIgnoreCase(col)) hasStatus = true;
                if ("managed".equalsIgnoreCase(col)) hasManaged = true;
                if ("registered".equalsIgnoreCase(col)) hasRegistered = true;
                if ("created_at".equalsIgnoreCase(col)) hasCreatedAt = true;
                if ("updated_at".equalsIgnoreCase(col)) hasUpdatedAt = true;
                if ("loot_pool_id".equalsIgnoreCase(col)) hasLootPoolId = true;
                if ("next_refill".equalsIgnoreCase(col) && rs.getInt("notnull") == 1) nextRefillNotNull = true;
            }

            if (!hasRefillEnabled) {
                stmt.execute("ALTER TABLE containers ADD COLUMN refill_enabled INTEGER NOT NULL DEFAULT 1;");
            }
            if (!hasRefillInterval) {
                stmt.execute("ALTER TABLE containers ADD COLUMN refill_interval_seconds INTEGER NOT NULL DEFAULT 1800;");
            }
            if (!hasSource) {
                stmt.execute("ALTER TABLE containers ADD COLUMN source TEXT NOT NULL DEFAULT 'MAP';");
            }
            if (!hasStatus) {
                stmt.execute("ALTER TABLE containers ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE';");
            }
            if (!hasManaged) {
                stmt.execute("ALTER TABLE containers ADD COLUMN managed INTEGER NOT NULL DEFAULT 1;");
            }
            if (!hasRegistered) {
                stmt.execute("ALTER TABLE containers ADD COLUMN registered INTEGER NOT NULL DEFAULT 1;");
            }
            if (!hasCreatedAt) {
                stmt.execute("ALTER TABLE containers ADD COLUMN created_at INTEGER NOT NULL DEFAULT 0;");
            }
            if (!hasUpdatedAt) {
                stmt.execute("ALTER TABLE containers ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0;");
            }
            if (!hasLootPoolId) {
                stmt.execute("ALTER TABLE containers ADD COLUMN loot_pool_id TEXT;");
            }

            // Migrar next_refill si tiene restricción NOT NULL
            if (nextRefillNotNull) {
                stmt.execute("PRAGMA foreign_keys = OFF;");
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS containers_temp (
                        id TEXT PRIMARY KEY,
                        world TEXT NOT NULL,
                        x INTEGER NOT NULL,
                        y INTEGER NOT NULL,
                        z INTEGER NOT NULL,
                        container_type TEXT NOT NULL,
                        loot_table_id TEXT,
                        loot_pool_id TEXT,
                        enabled INTEGER NOT NULL,
                        looted INTEGER NOT NULL,
                        last_loot INTEGER NOT NULL,
                        next_refill INTEGER,
                        refill_enabled INTEGER NOT NULL DEFAULT 1,
                        refill_interval_seconds INTEGER NOT NULL DEFAULT 1800,
                        source TEXT NOT NULL DEFAULT 'MAP',
                        status TEXT NOT NULL DEFAULT 'ACTIVE',
                        managed INTEGER NOT NULL DEFAULT 1,
                        registered INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0
                    );
                """);
                stmt.execute("INSERT OR REPLACE INTO containers_temp SELECT * FROM containers;");
                stmt.execute("DROP TABLE containers;");
                stmt.execute("ALTER TABLE containers_temp RENAME TO containers;");
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_containers_loc ON containers(world, x, y, z);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_world ON containers(world);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_world_xz ON containers(world, x, z);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_type ON containers(container_type);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_next_refill ON containers(next_refill);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_loot_table ON containers(loot_table_id);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_loot_pool ON containers(loot_pool_id);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_source_status ON containers(source, status, managed);");
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_containers_refill_eligible ON containers(container_type, source, status, managed, refill_enabled, next_refill);");
                stmt.execute("PRAGMA foreign_keys = ON;");
            }

            // Normalizar cadenas vacías a NULL en loot_table_id y loot_pool_id
            stmt.execute("UPDATE containers SET loot_table_id = NULL WHERE loot_table_id = '';");
            stmt.execute("UPDATE containers SET loot_pool_id = NULL WHERE loot_pool_id = '';");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error al verificar migración de columnas en containers", e);
        }
    }

    private void migrateLootPoolsTable(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(loot_pools);");
            boolean hasAllowDuplicates = false;
            while (rs.next()) {
                String col = rs.getString("name");
                if ("allow_duplicates".equalsIgnoreCase(col)) {
                    hasAllowDuplicates = true;
                    break;
                }
            }
            if (!hasAllowDuplicates) {
                stmt.execute("ALTER TABLE loot_pools ADD COLUMN allow_duplicates INTEGER NOT NULL DEFAULT 0;");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error al verificar migración de columnas en loot_pools", e);
        }
    }

    public void saveScanJob(ScanJob job) {
        if (job == null) return;
        String sql = """
            INSERT INTO scan_jobs (id, world, current_chunk_x, current_chunk_z, total_chunks, processed_chunks, found_containers, status, started_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                current_chunk_x = excluded.current_chunk_x,
                current_chunk_z = excluded.current_chunk_z,
                total_chunks = excluded.total_chunks,
                processed_chunks = excluded.processed_chunks,
                found_containers = excluded.found_containers,
                status = excluded.status,
                updated_at = excluded.updated_at;
        """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, job.getId().toString());
            stmt.setString(2, job.getWorld());
            stmt.setInt(3, job.getCurrentChunkX());
            stmt.setInt(4, job.getCurrentChunkZ());
            stmt.setInt(5, job.getTotalChunks());
            stmt.setInt(6, job.getProcessedChunks());
            stmt.setInt(7, job.getFoundContainers());
            stmt.setString(8, job.getStatus().name());
            stmt.setLong(9, job.getStartedAt());
            stmt.setLong(10, job.getUpdatedAt());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar el trabajo de escaneo " + job.getId(), e);
        }
    }

    public ScanJob loadIncompleteScanJob() {
        String sql = "SELECT * FROM scan_jobs WHERE status IN ('RUNNING', 'PAUSED') ORDER BY updated_at DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String world = rs.getString("world");
                int curX = rs.getInt("current_chunk_x");
                int curZ = rs.getInt("current_chunk_z");
                int total = rs.getInt("total_chunks");
                int processed = rs.getInt("processed_chunks");
                int found = rs.getInt("found_containers");
                ScanJob.Status status = ScanJob.Status.valueOf(rs.getString("status"));
                long startedAt = rs.getLong("started_at");
                long updatedAt = rs.getLong("updated_at");

                return new ScanJob(id, world, curX, curZ, total, processed, found, status, startedAt, updatedAt);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error al cargar trabajo de escaneo incompleto", e);
        }
        return null;
    }

    public void deleteScanJob(UUID id) {
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement("DELETE FROM scan_jobs WHERE id = ?")) {
            stmt.setString(1, id.toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al eliminar scan_job " + id, e);
        }
    }

    public void savePopulateJob(com.arcraft.lootrefill.populate.PopulateJob job) {
        if (job == null) return;
        String sql = """
            INSERT INTO populate_jobs (id, world, status, total, processed, populated, skipped, started_at, updated_at, completed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                status = excluded.status,
                total = excluded.total,
                processed = excluded.processed,
                populated = excluded.populated,
                skipped = excluded.skipped,
                updated_at = excluded.updated_at,
                completed_at = excluded.completed_at;
        """;

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, job.getId().toString());
            stmt.setString(2, job.getWorld());
            stmt.setString(3, job.getStatus().name());
            stmt.setInt(4, job.getTotal());
            stmt.setInt(5, job.getProcessed());
            stmt.setInt(6, job.getPopulated());
            stmt.setInt(7, job.getSkipped());
            stmt.setLong(8, job.getStartedAt());
            stmt.setLong(9, job.getUpdatedAt());
            stmt.setLong(10, job.getCompletedAt());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al guardar populate_job " + job.getId(), e);
        }
    }

    public com.arcraft.lootrefill.populate.PopulateJob loadLastPopulateJob(String world) {
        String sql = "SELECT * FROM populate_jobs WHERE world = ? ORDER BY started_at DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, world);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String w = rs.getString("world");
                com.arcraft.lootrefill.populate.PopulateJob.Status status = com.arcraft.lootrefill.populate.PopulateJob.Status.valueOf(rs.getString("status"));
                int total = rs.getInt("total");
                int processed = rs.getInt("processed");
                int populated = rs.getInt("populated");
                int skipped = rs.getInt("skipped");
                long startedAt = rs.getLong("started_at");
                long updatedAt = rs.getLong("updated_at");
                long completedAt = rs.getLong("completed_at");

                return new com.arcraft.lootrefill.populate.PopulateJob(id, w, status, total, processed, populated, skipped, startedAt, updatedAt, completedAt);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error al cargar último populate_job de " + world, e);
        }
        return null;
    }

    public com.arcraft.lootrefill.populate.PopulateJob loadLastPopulateJob() {
        String sql = "SELECT * FROM populate_jobs ORDER BY started_at DESC LIMIT 1";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                UUID id = UUID.fromString(rs.getString("id"));
                String w = rs.getString("world");
                com.arcraft.lootrefill.populate.PopulateJob.Status status = com.arcraft.lootrefill.populate.PopulateJob.Status.valueOf(rs.getString("status"));
                int total = rs.getInt("total");
                int processed = rs.getInt("processed");
                int populated = rs.getInt("populated");
                int skipped = rs.getInt("skipped");
                long startedAt = rs.getLong("started_at");
                long updatedAt = rs.getLong("updated_at");
                long completedAt = rs.getLong("completed_at");

                return new com.arcraft.lootrefill.populate.PopulateJob(id, w, status, total, processed, populated, skipped, startedAt, updatedAt, completedAt);
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error al cargar último populate_job global", e);
        }
        return null;
    }

    /**
     * Obtiene el desglose de estadísticas de contenedores (MAP, PLAYER, BROKEN, Total) para un mundo específico.
     */
    public ContainerWorldStats getContainerStatsByWorld(String worldName) {
        int map = 0;
        int player = 0;
        int broken = 0;
        int total = 0;
        String sql = "SELECT source, status, COUNT(*) FROM containers WHERE LOWER(world) = LOWER(?) GROUP BY source, status";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, worldName);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String source = rs.getString(1);
                String status = rs.getString(2);
                int count = rs.getInt(3);
                total += count;
                if ("BROKEN".equalsIgnoreCase(status)) {
                    broken += count;
                } else if ("PLAYER".equalsIgnoreCase(source)) {
                    player += count;
                } else if ("MAP".equalsIgnoreCase(source)) {
                    map += count;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Error consultando estadísticas de contenedores para mundo: " + worldName, e);
        }
        return new ContainerWorldStats(map, player, broken, total);
    }

    /**
     * Elimina exclusivamente los registros de la tabla containers cuyo world corresponda al mundo indicado.
     */
    public int resetContainersByWorld(String worldName) {
        String sql = "DELETE FROM containers WHERE LOWER(world) = LOWER(?)";
        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, worldName);
            return stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error al reiniciar contenedores del mundo " + worldName, e);
        }
        return 0;
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }
}
