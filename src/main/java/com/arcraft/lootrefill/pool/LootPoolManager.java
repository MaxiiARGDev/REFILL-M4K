package com.arcraft.lootrefill.pool;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.loot.LootTable;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LootPoolManager {

    private final LootRefillPlugin plugin;
    private final LootPoolStorage storage;
    private final Map<String, LootPool> pools;

    public LootPoolManager(LootRefillPlugin plugin, LootPoolStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.pools = new ConcurrentHashMap<>();
    }

    public void load() {
        pools.clear();
        Map<String, LootPool> loaded = storage.loadAllPools();
        pools.putAll(loaded);

        if (pools.isEmpty()) {
            loadDefaultsFromConfig();
        }

        plugin.getLogger().info("Se cargaron " + pools.size() + " Loot Pools en memoria (Etapa 4.2).");
    }

    private void loadDefaultsFromConfig() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("loot-pools");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection poolSec = section.getConfigurationSection(key);
                if (poolSec == null) continue;

                String poolId = key.toLowerCase();
                String name = poolSec.getString("name", key);
                boolean enabled = poolSec.getBoolean("enabled", true);
                String modeStr = poolSec.getString("selection.mode", "SINGLE_RANDOM");
                PoolSelectionMode mode = PoolSelectionMode.fromString(modeStr);
                int minRolls = poolSec.getInt("selection.rolls.min", 1);
                int maxRolls = poolSec.getInt("selection.rolls.max", 1);
                boolean allowDuplicates = poolSec.getBoolean("selection.allow-duplicates", false);

                LootPool pool = new LootPool(poolId, name, mode, minRolls, maxRolls, allowDuplicates, enabled);

                ConfigurationSection tablesSec = poolSec.getConfigurationSection("tables");
                if (tablesSec != null) {
                    for (String tableKey : tablesSec.getKeys(false)) {
                        int weight = tablesSec.getInt(tableKey + ".weight", 10);
                        pool.addEntry(new LootPoolEntry(poolId, tableKey.toLowerCase(), weight));
                    }
                }

                pools.put(poolId, pool);
                storage.savePool(pool);
            }
        }

        // Si aún no hay ninguno, crear por defecto 'house' y 'military'
        if (pools.isEmpty()) {
            createBuiltinDefaults();
        }
    }

    private void createBuiltinDefaults() {
        // Pool: house
        LootPool housePool = new LootPool("house", "House Pool", PoolSelectionMode.SINGLE_RANDOM, 1, 1, false, true);
        housePool.addEntry(new LootPoolEntry("house", "common", 40));
        housePool.addEntry(new LootPoolEntry("house", "food", 30));
        housePool.addEntry(new LootPoolEntry("house", "medical", 20));
        housePool.addEntry(new LootPoolEntry("house", "rare", 10));
        pools.put("house", housePool);
        storage.savePool(housePool);

        // Pool: military
        LootPool militaryPool = new LootPool("military", "Military Pool", PoolSelectionMode.MIXED_RANDOM, 2, 4, false, true);
        militaryPool.addEntry(new LootPoolEntry("military", "common", 10));
        militaryPool.addEntry(new LootPoolEntry("military", "food", 10));
        militaryPool.addEntry(new LootPoolEntry("military", "medical", 20));
        militaryPool.addEntry(new LootPoolEntry("military", "military", 45));
        militaryPool.addEntry(new LootPoolEntry("military", "high_tier", 10));
        militaryPool.addEntry(new LootPoolEntry("military", "rare", 5));
        pools.put("military", militaryPool);
        storage.savePool(militaryPool);

        plugin.getLogger().info("Se inicializaron y guardaron en SQLite los Loot Pools por defecto (house, military).");
    }

    public LootPool getPool(String id) {
        if (id == null) return null;
        return pools.get(id.toLowerCase().trim());
    }

    public boolean poolExists(String id) {
        if (id == null) return false;
        return pools.containsKey(id.toLowerCase().trim());
    }

    public void savePool(LootPool pool) {
        if (pool == null) return;
        pools.put(pool.getId().toLowerCase(), pool);
        storage.savePool(pool);
    }

    /**
     * Cuenta cuántos contenedores registrados utilizan este pool.
     */
    public int getContainerCountUsingPool(String id) {
        if (id == null) return 0;
        return storage.countContainersUsingPool(id.toLowerCase().trim());
    }

    /**
     * Elimina el Loot Pool desvinculando de forma segura y transaccional todos los contenedores que lo referencian.
     * Actualiza simultáneamente el estado en SQLite y en la caché de memoria de {@link com.arcraft.lootrefill.container.ContainerManager}.
     *
     * @param id ID del pool a eliminar
     * @return cantidad de contenedores desvinculados, o -1 si el pool no existía
     */
    public int deletePool(String id) {
        if (id == null || !poolExists(id)) {
            return -1;
        }
        String normalizedId = id.toLowerCase().trim();

        // 1. Eliminar de la base de datos y desvincular containers atómicamente
        int unlinkedDb = storage.deletePoolAndUnlink(normalizedId);

        // 2. Limpiar de la memoria de LootPoolManager
        pools.remove(normalizedId);

        // 3. Limpiar referencias en memoria de ContainerManager
        if (plugin.getContainerManager() != null) {
            plugin.getContainerManager().clearPoolFromContainersInMemory(normalizedId);
        }

        return Math.max(0, unlinkedDb);
    }

    /**
     * Desvincula el Loot Pool de todos los contenedores sin eliminar la definición del Pool.
     *
     * @param id ID del pool a desvincular
     * @return cantidad de contenedores desvinculados
     */
    public int clearPoolFromContainers(String id) {
        if (id == null) return 0;
        String normalizedId = id.toLowerCase().trim();

        int unlinked = 0;
        String unlinkSql = "UPDATE containers SET loot_pool_id = NULL, updated_at = ? WHERE loot_pool_id = ?";
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement stmt = conn.prepareStatement(unlinkSql)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.setString(2, normalizedId);
            unlinked = stmt.executeUpdate();
        } catch (java.sql.SQLException e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error al desvincular contenedores del pool: " + id, e);
        }

        if (plugin.getContainerManager() != null) {
            plugin.getContainerManager().clearPoolFromContainersInMemory(normalizedId);
        }

        return unlinked;
    }

    public Collection<LootPool> getAllPools() {
        return Collections.unmodifiableCollection(pools.values());
    }

    public List<String> getPoolIds() {
        return new ArrayList<>(pools.keySet());
    }

    /**
     * Realiza la selección estocástica de tablas según el modo del pool (SINGLE_RANDOM o MIXED_RANDOM).
     * Devuelve una lista de LootTables válidas.
     * Si el pool es inválido, está deshabilitado o no tiene tablas activas, devuelve una lista vacía.
     */
    public List<LootTable> selectTables(LootPool pool) {
        if (pool == null || !pool.isEnabled() || !pool.hasEntries()) {
            return Collections.emptyList();
        }

        // Filtrar entradas cuyas LootTable existan y estén habilitadas
        List<LootPoolEntry> validEntries = new ArrayList<>();
        int totalValidWeight = 0;

        for (LootPoolEntry entry : pool.getEntries()) {
            LootTable table = plugin.getLootManager().getTable(entry.getTableId());
            if (table != null && table.isEnabled()) {
                validEntries.add(entry);
                totalValidWeight += entry.getWeight();
            }
        }

        if (validEntries.isEmpty() || totalValidWeight <= 0) {
            return Collections.emptyList();
        }

        List<LootTable> selectedTables = new ArrayList<>();

        if (pool.getSelectionMode() == PoolSelectionMode.SINGLE_RANDOM) {
            LootPoolEntry picked = selectWeightedEntry(validEntries, totalValidWeight);
            if (picked != null) {
                LootTable table = plugin.getLootManager().getTable(picked.getTableId());
                if (table != null) {
                    selectedTables.add(table);
                }
            }
        } else if (pool.getSelectionMode() == PoolSelectionMode.MIXED_RANDOM) {
            int min = pool.getMinRolls();
            int max = pool.getMaxRolls();
            int rolls = (min >= max) ? min : ThreadLocalRandom.current().nextInt(min, max + 1);

            if (pool.isAllowDuplicates()) {
                for (int i = 0; i < rolls; i++) {
                    LootPoolEntry picked = selectWeightedEntry(validEntries, totalValidWeight);
                    if (picked != null) {
                        LootTable table = plugin.getLootManager().getTable(picked.getTableId());
                        if (table != null) {
                            selectedTables.add(table);
                        }
                    }
                }
            } else {
                List<LootPoolEntry> poolCandidates = new ArrayList<>(validEntries);
                int currentWeight = totalValidWeight;
                int actualRolls = Math.min(rolls, poolCandidates.size());

                for (int i = 0; i < actualRolls; i++) {
                    if (poolCandidates.isEmpty() || currentWeight <= 0) {
                        break;
                    }
                    LootPoolEntry picked = selectWeightedEntry(poolCandidates, currentWeight);
                    if (picked != null) {
                        LootTable table = plugin.getLootManager().getTable(picked.getTableId());
                        if (table != null) {
                            selectedTables.add(table);
                        }
                        poolCandidates.remove(picked);
                        currentWeight -= picked.getWeight();
                    }
                }
            }
        }

        return selectedTables;
    }

    private LootPoolEntry selectWeightedEntry(List<LootPoolEntry> entries, int totalWeight) {
        if (entries.isEmpty() || totalWeight <= 0) return null;

        int randomPoint = ThreadLocalRandom.current().nextInt(totalWeight);
        int runningSum = 0;

        for (LootPoolEntry entry : entries) {
            runningSum += entry.getWeight();
            if (randomPoint < runningSum) {
                return entry;
            }
        }

        return entries.get(entries.size() - 1);
    }
}
