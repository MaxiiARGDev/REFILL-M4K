package com.arcraft.lootrefill.refill;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.loot.LootGenerator;
import com.arcraft.lootrefill.loot.LootManager;
import com.arcraft.lootrefill.loot.LootTable;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.Furnace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RefillManager {

    private final LootRefillPlugin plugin;
    private final ContainerManager containerManager;
    private final LootManager lootManager;
    private final RefillQueue refillQueue;
    private final RefillStats stats;

    private boolean enabled;
    private boolean paused;
    private boolean requireEmpty;
    private boolean requireNoPlayersNearby;
    private double nearbyRadius;
    private boolean refillLoadedChunksOnly;
    private int maxChunksLoadedByRefill;
    private boolean unloadAfterRefill;
    private boolean onlyLoadNeededChunks;

    private long lastGlobalRefill;

    // Cooldown temporal en memoria para contenedores omitidos (evita re-consultarlos cada tick sin alterar su next_refill)
    private final Map<UUID, Long> retryCooldowns;
    private final Set<String> processedDoubleChests;

    private static final long RETRY_COOLDOWN_MS = 30_000L; // 30 segundos de espera tras un skip temporal

    public RefillManager(LootRefillPlugin plugin, ContainerManager containerManager, LootManager lootManager) {
        this.plugin = plugin;
        this.containerManager = containerManager;
        this.lootManager = lootManager;
        this.stats = new RefillStats();
        this.refillQueue = new RefillQueue(plugin, this, containerManager);
        this.lastGlobalRefill = 0;
        this.paused = false;
        this.retryCooldowns = new ConcurrentHashMap<>();
        this.processedDoubleChests = ConcurrentHashMap.newKeySet();
    }

    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("refill.enabled", true);
        this.requireEmpty = plugin.getConfig().getBoolean("refill.conditions.require-empty", true);
        this.requireNoPlayersNearby = plugin.getConfig().getBoolean("refill.conditions.require-no-players-nearby", true);
        this.nearbyRadius = plugin.getConfig().getDouble("refill.conditions.nearby-radius", 32.0);
        this.refillLoadedChunksOnly = plugin.getConfig().getBoolean("refill.conditions.refill-loaded-chunks-only", false);
        this.maxChunksLoadedByRefill = Math.max(1, plugin.getConfig().getInt("refill.conditions.max-chunks-loaded-by-refill", 1));
        this.unloadAfterRefill = plugin.getConfig().getBoolean("refill.chunks.unload-after-refill", true);
        this.onlyLoadNeededChunks = plugin.getConfig().getBoolean("refill.chunks.only-load-needed-chunks", true);
    }

    public void start() {
        stop();
        loadConfig();
        refillQueue.start();
    }

    public void stop() {
        if (refillQueue != null) {
            refillQueue.stop();
        }
        retryCooldowns.clear();
        processedDoubleChests.clear();
    }

    /**
     * Limpia completamente las colas en memoria y listas de cooldown / doble cofres.
     */
    public void clearRefillState() {
        if (refillQueue != null) {
            refillQueue.clear();
        }
        retryCooldowns.clear();
        processedDoubleChests.clear();
    }

    /**
     * Invalida y remueve de las colas de refill y estados en memoria los contenedores pertenecientes al mundo indicado.
     */
    public void clearRefillStateForWorld(String worldName) {
        if (refillQueue != null) {
            refillQueue.removeContainersForWorld(worldName);
        }
        processedDoubleChests.removeIf(key -> key.toLowerCase().startsWith(worldName.toLowerCase() + ":"));
    }

    public void pause() {
        this.paused = true;
    }

    public void resume() {
        this.paused = false;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isAutoRefillActive() {
        return enabled && !paused;
    }

    public RefillStats getStats() {
        return stats;
    }

    public void clearDoubleChestTracker() {
        processedDoubleChests.clear();
    }

    /**
     * Ejecuta un ciclo manual progresivo a través de la cola de refill sin saturar el servidor.
     */
    public void triggerManualCycle(CommandSender sender) {
        long now = System.currentTimeMillis();
        int dueCount = containerManager.getDueContainersCount(now);
        if (dueCount == 0) {
            MessageUtil.sendMessage(sender, "&eNo hay contenedores MAP vencidos esperando refill en este momento.");
            return;
        }

        MessageUtil.sendMessage(sender, "&6Iniciando ciclo de refill escalonado para &e" + dueCount + " &6contenedores vencidos...");
        refillQueue.pollAllDueContainers();
        this.lastGlobalRefill = now;
    }

    public int refillAllContainers() {
        int refilled = 0;
        for (LootContainer container : containerManager.getAllContainers()) {
            if (container.getSource() == ContainerSource.MAP && container.getStatus() == ContainerStatus.ACTIVE && container.isManaged()) {
                RefillResult result = refillContainer(container);
                if (result == RefillResult.SUCCESS) {
                    refilled++;
                }
            }
        }
        this.lastGlobalRefill = System.currentTimeMillis();
        return refilled;
    }

    /**
     * Ejecuta el refill de un contenedor con validaciones estrictas en el hilo principal.
     * Rechaza terminantemente contenedores PLAYER, BROKEN, DISABLED o UNKNOWN.
     */
    public RefillResult refillContainer(LootContainer container) {
        if (container == null) {
            stats.recordResult(RefillResult.ERROR);
            return RefillResult.ERROR;
        }

        // Comprobar cooldown temporal de reintento en memoria
        long now = System.currentTimeMillis();
        Long cooldownUntil = retryCooldowns.get(container.getId());
        if (cooldownUntil != null && now < cooldownUntil) {
            return RefillResult.SKIPPED_DISABLED;
        }

        // 1. Reglas fundamentales de seguridad e identidad
        if (container.getSource() != ContainerSource.MAP) {
            stats.recordResult(RefillResult.SKIPPED_DISABLED);
            return RefillResult.SKIPPED_DISABLED;
        }
        if (container.getStatus() != ContainerStatus.ACTIVE || !container.isManaged() || !container.isRegistered()) {
            stats.recordResult(RefillResult.SKIPPED_INVALID_BLOCK);
            return RefillResult.SKIPPED_INVALID_BLOCK;
        }
        if (!container.isRefillEnabled()) {
            stats.recordResult(RefillResult.SKIPPED_DISABLED);
            return RefillResult.SKIPPED_DISABLED;
        }

        // 2. Validación y resolución de Loot Source (Pool o Tabla legada)
        com.arcraft.lootrefill.pool.LootPool pool = null;
        List<LootTable> selectedTables = null;
        LootTable legacyTable = null;

        if (container.getLootPoolId() != null && !container.getLootPoolId().trim().isEmpty()) {
            pool = plugin.getLootPoolManager().getPool(container.getLootPoolId());
            if (pool == null || !pool.isEnabled() || !pool.hasEntries()) {
                stats.recordResult(RefillResult.SKIPPED_INVALID_LOOT_POOL);
                plugin.getLogger().warning("[Refill] Loot Pool inexistente, deshabilitado o sin tablas para el contenedor "
                        + container.getId() + " (Pool: " + container.getLootPoolId() + ")");
                return RefillResult.SKIPPED_INVALID_LOOT_POOL;
            }
            selectedTables = plugin.getLootPoolManager().selectTables(pool);
            if (selectedTables.isEmpty()) {
                stats.recordResult(RefillResult.SKIPPED_INVALID_LOOT_POOL);
                plugin.getLogger().warning("[Refill] No se pudieron seleccionar tablas válidas del pool " + pool.getId());
                return RefillResult.SKIPPED_INVALID_LOOT_POOL;
            }
        } else if (container.getLootTableId() != null && !container.getLootTableId().trim().isEmpty()) {
            String tableId = container.getLootTableId();
            legacyTable = lootManager.getTable(tableId);
            if (legacyTable == null || !legacyTable.isEnabled()) {
                stats.recordResult(RefillResult.SKIPPED_NO_LOOT_TABLE);
                return RefillResult.SKIPPED_NO_LOOT_TABLE;
            }
        } else {
            stats.recordResult(RefillResult.SKIPPED_NO_LOOT_TABLE);
            return RefillResult.SKIPPED_NO_LOOT_TABLE;
        }

        // 3. Validación de tipo habilitado para refill en config.yml
        ContainerType type = container.getContainerType();
        if (!isTypeRefillEnabled(type)) {
            stats.recordResult(RefillResult.SKIPPED_DISABLED);
            return RefillResult.SKIPPED_DISABLED;
        }

        Location loc = container.toLocation();
        if (loc == null || loc.getWorld() == null) {
            stats.recordResult(RefillResult.SKIPPED_INVALID_BLOCK);
            return RefillResult.SKIPPED_INVALID_BLOCK;
        }

        World world = loc.getWorld();
        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        if (refillLoadedChunksOnly && !world.isChunkLoaded(chunkX, chunkZ)) {
            retryCooldowns.put(container.getId(), now + RETRY_COOLDOWN_MS);
            stats.recordResult(RefillResult.SKIPPED_CHUNK_NOT_LOADED);
            return RefillResult.SKIPPED_CHUNK_NOT_LOADED;
        }

        Block block = loc.getBlock();

        // Si el bloque físico no existe o ya no es un contenedor
        if (!(block.getState() instanceof Container blockContainer)) {
            container.setStatus(ContainerStatus.BROKEN);
            container.setManaged(false);
            container.setRefillEnabled(false);
            container.setUpdatedAt(now);
            containerManager.saveContainer(container);
            stats.recordResult(RefillResult.SKIPPED_INVALID_BLOCK);
            return RefillResult.SKIPPED_INVALID_BLOCK;
        }

        // Si el tipo de bloque físico no coincide con el tipo registrado
        ContainerType actualType = ContainerType.fromBlock(block);
        if (actualType != type) {
            stats.recordResult(RefillResult.SKIPPED_TYPE_MISMATCH);
            return RefillResult.SKIPPED_TYPE_MISMATCH;
        }

        // Protección especial para hornos / ahumadores
        if (blockContainer instanceof Furnace furnace && furnace.getBurnTime() > 0) {
            retryCooldowns.put(container.getId(), now + RETRY_COOLDOWN_MS);
            stats.recordResult(RefillResult.SKIPPED_BURNING);
            return RefillResult.SKIPPED_BURNING;
        }

        Inventory inv = blockContainer.getInventory();

        // 4. Tratamiento seguro de Double Chest para evitar duplicación
        if (block.getState() instanceof Chest && inv.getHolder() instanceof DoubleChest doubleChest) {
            Location leftLoc = ((Chest) doubleChest.getLeftSide()).getLocation();
            Location rightLoc = ((Chest) doubleChest.getRightSide()).getLocation();
            String chestPairKey = world.getName() + ":" + Math.min(leftLoc.getBlockX(), rightLoc.getBlockX()) + ":" + Math.min(leftLoc.getBlockZ(), rightLoc.getBlockZ());

            if (processedDoubleChests.contains(chestPairKey)) {
                // Actualizar timestamp para mantener sincronizado el par sin generar loot duplicado
                int interval = getIntervalForContainer(container, type);
                container.setLooted(false);
                container.setLastLoot(now);
                container.setNextRefill(now + (interval * 1000L));
                container.setUpdatedAt(now);
                containerManager.saveContainer(container);
                stats.recordResult(RefillResult.SKIPPED_DOUBLE_CHEST_PAIR);
                return RefillResult.SKIPPED_DOUBLE_CHEST_PAIR;
            }
            processedDoubleChests.add(chestPairKey);
        }

        // 5. Validación de jugadores cercanos (si está activo)
        if (requireNoPlayersNearby && hasPlayersNearby(loc, nearbyRadius)) {
            retryCooldowns.put(container.getId(), now + RETRY_COOLDOWN_MS);
            stats.recordResult(RefillResult.SKIPPED_PLAYER_NEARBY);
            logDebug(container, loc, RefillResult.SKIPPED_PLAYER_NEARBY);
            return RefillResult.SKIPPED_PLAYER_NEARBY;
        }

        // 6. Validación de inventario vacío (NUNCA usar inv.clear())
        if (requireEmpty && !isInventoryEmpty(inv)) {
            retryCooldowns.put(container.getId(), now + RETRY_COOLDOWN_MS);
            stats.recordResult(RefillResult.SKIPPED_NOT_EMPTY);
            logDebug(container, loc, RefillResult.SKIPPED_NOT_EMPTY);
            return RefillResult.SKIPPED_NOT_EMPTY;
        }

        // 7. Generación de loot mediante LootGenerator
        List<ItemStack> lootItems = new ArrayList<>();
        String selectionSummary;

        if (pool != null && selectedTables != null) {
            List<String> names = new ArrayList<>();
            for (LootTable t : selectedTables) {
                lootItems.addAll(LootGenerator.generate(t));
                names.add(t.getId().toUpperCase());
            }
            selectionSummary = String.join(" + ", names);
        } else if (legacyTable != null) {
            lootItems = LootGenerator.generate(legacyTable);
            selectionSummary = legacyTable.getId().toUpperCase();
        } else {
            stats.recordResult(RefillResult.ERROR);
            logDebug(container, loc, RefillResult.ERROR);
            return RefillResult.ERROR;
        }

        if (lootItems.isEmpty()) {
            stats.recordResult(RefillResult.ERROR);
            logDebug(container, loc, RefillResult.ERROR);
            return RefillResult.ERROR;
        }

        container.addRecentSelection(selectionSummary);

        // 8. Distribución en slots aleatorios
        distributeLootRandomly(inv, lootItems);

        // 9. Actualización y persistencia de timestamps
        int interval = getIntervalForContainer(container, type);
        container.setLooted(false);
        container.setLastLoot(now);
        container.setNextRefill(now + (interval * 1000L));
        container.setUpdatedAt(now);
        containerManager.saveContainer(container);
        retryCooldowns.remove(container.getId());

        stats.recordResult(RefillResult.SUCCESS);
        logDebug(container, loc, RefillResult.SUCCESS);

        return RefillResult.SUCCESS;
    }

    private void logDebug(LootContainer container, Location loc, RefillResult result) {
        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
            String typeName = (container != null && container.getContainerType() != null) ? container.getContainerType().name() : "UNKNOWN";
            String uuid = container != null ? container.getId().toString() : "null";
            String coords = loc != null ? (loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()) : "unknown";
            plugin.getLogger().info("[Refill] " + typeName + " | " + uuid + " | " + coords + " -> " + result.name());
        }
    }

    public boolean isOnRetryCooldown(UUID id) {
        if (id == null) return false;
        Long until = retryCooldowns.get(id);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            retryCooldowns.remove(id);
            return false;
        }
        return true;
    }

    public int getEffectiveIntervalForContainer(LootContainer container) {
        if (container == null) return 1800;
        return getIntervalForType(container.getContainerType());
    }

    private int getIntervalForContainer(LootContainer container, ContainerType type) {
        int interval = getIntervalForType(type);
        return Math.max(10, interval);
    }

    private void distributeLootRandomly(Inventory inv, List<ItemStack> items) {
        int size = inv.getSize();
        List<Integer> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, ThreadLocalRandom.current());

        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            inv.setItem(slots.get(i), items.get(i));
        }
    }

    public boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack is : inv.getContents()) {
            if (is != null && !is.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasPlayersNearby(Location loc, double radius) {
        World world = loc.getWorld();
        if (world == null) return false;
        double radiusSquared = radius * radius;
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    public int getIntervalForType(ContainerType type) {
        if (type == null) return 1800;
        return plugin.getConfig().getInt("refill.intervals." + type.getConfigKey() + ".interval", 1800);
    }

    public void setIntervalForType(ContainerType type, int seconds) {
        if (type == null) return;
        plugin.getConfig().set("refill.intervals." + type.getConfigKey() + ".interval", Math.max(10, seconds));
        plugin.saveConfig();
    }

    public boolean isTypeRefillEnabled(ContainerType type) {
        if (type == null) return true;
        return plugin.getConfig().getBoolean("refill.intervals." + type.getConfigKey() + ".enabled", true);
    }

    public void setTypeRefillEnabled(ContainerType type, boolean enabled) {
        if (type == null) return;
        plugin.getConfig().set("refill.intervals." + type.getConfigKey() + ".enabled", enabled);
        plugin.saveConfig();
    }

    public List<LootContainer> getPendingContainers() {
        List<LootContainer> list = new ArrayList<>();
        for (LootContainer c : containerManager.getAllContainers()) {
            if (c.isEligibleForRefill()) {
                list.add(c);
            }
        }
        return list;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        plugin.getConfig().set("refill.enabled", enabled);
        plugin.saveConfig();
    }

    public boolean isRequireEmpty() {
        return requireEmpty;
    }

    public void setRequireEmpty(boolean requireEmpty) {
        this.requireEmpty = requireEmpty;
        plugin.getConfig().set("refill.conditions.require-empty", requireEmpty);
        plugin.saveConfig();
    }

    public boolean isRequireNoPlayersNearby() {
        return requireNoPlayersNearby;
    }

    public void setRequireNoPlayersNearby(boolean requireNoPlayersNearby) {
        this.requireNoPlayersNearby = requireNoPlayersNearby;
        plugin.getConfig().set("refill.conditions.require-no-players-nearby", requireNoPlayersNearby);
        plugin.saveConfig();
    }

    public double getNearbyRadius() {
        return nearbyRadius;
    }

    public void setNearbyRadius(double nearbyRadius) {
        this.nearbyRadius = Math.max(0, nearbyRadius);
        plugin.getConfig().set("refill.conditions.nearby-radius", this.nearbyRadius);
        plugin.saveConfig();
    }

    public boolean isRefillLoadedChunksOnly() {
        return refillLoadedChunksOnly;
    }

    public void setRefillLoadedChunksOnly(boolean refillLoadedChunksOnly) {
        this.refillLoadedChunksOnly = refillLoadedChunksOnly;
        plugin.getConfig().set("refill.conditions.refill-loaded-chunks-only", refillLoadedChunksOnly);
        plugin.saveConfig();
    }

    public int getMaxChunksLoadedByRefill() {
        return maxChunksLoadedByRefill;
    }

    public boolean isUnloadAfterRefill() {
        return unloadAfterRefill;
    }

    public long getLastGlobalRefill() {
        return lastGlobalRefill;
    }

    public RefillQueue getRefillQueue() {
        return refillQueue;
    }
}
