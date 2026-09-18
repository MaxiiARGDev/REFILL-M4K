package com.arcraft.lootrefill.refill;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RefillQueue {

    public static final ContainerType[] ROUND_ROBIN_TYPES = {
        ContainerType.CHEST,
        ContainerType.BARREL,
        ContainerType.TRAPPED_CHEST,
        ContainerType.DISPENSER,
        ContainerType.DROPPER,
        ContainerType.FURNACE,
        ContainerType.BLAST_FURNACE,
        ContainerType.SMOKER
    };

    private final LootRefillPlugin plugin;
    private final RefillManager refillManager;
    private final ContainerManager containerManager;

    private final Map<ContainerType, Deque<LootContainer>> queuesByType;
    private final AtomicInteger currentlyLoadingChunks;
    private BukkitTask queueTask;
    private int currentTypeIndex = 0;

    public RefillQueue(LootRefillPlugin plugin, RefillManager refillManager, ContainerManager containerManager) {
        this.plugin = plugin;
        this.refillManager = refillManager;
        this.containerManager = containerManager;
        this.currentlyLoadingChunks = new AtomicInteger(0);
        this.queuesByType = new EnumMap<>(ContainerType.class);
        for (ContainerType type : ContainerType.values()) {
            queuesByType.put(type, new ArrayDeque<>());
        }
    }

    public void start() {
        stop();
        int delay = Math.max(1, plugin.getConfig().getInt("refill.processing.batch-delay-ticks", 1));

        queueTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!refillManager.isAutoRefillActive()) {
                    return;
                }

                long maxNanos = plugin.getConfig().getLong("refill.processing.max-ms-per-tick", 2) * 1_000_000L;
                int maxPerTick = Math.max(1, plugin.getConfig().getInt("refill.processing.containers-per-tick", 5));
                long startNanos = System.nanoTime();
                int processedCount = 0;
                int refilledCount = 0;

                // Procesamiento incremental respetando límites por tick
                while (processedCount < maxPerTick && (System.nanoTime() - startNanos < maxNanos)) {
                    LootContainer container = null;
                    int typesChecked = 0;

                    // Alternar entre tipos mediante Round-Robin REAL
                    while (typesChecked < ROUND_ROBIN_TYPES.length) {
                        ContainerType type = ROUND_ROBIN_TYPES[currentTypeIndex];
                        currentTypeIndex = (currentTypeIndex + 1) % ROUND_ROBIN_TYPES.length;
                        typesChecked++;

                        // Si el tipo de contenedor está deshabilitado para refill en config, continuar
                        if (!refillManager.isTypeRefillEnabled(type)) {
                            continue;
                        }

                        Deque<LootContainer> queue = queuesByType.get(type);
                        if (queue.isEmpty()) {
                            pollFromDatabase(type);
                        }

                        // Obtener el siguiente candidato válido que no esté en cooldown temporal
                        while (!queue.isEmpty()) {
                            LootContainer candidate = queue.poll();
                            if (candidate == null) {
                                continue;
                            }
                            if (refillManager.isOnRetryCooldown(candidate.getId())) {
                                continue;
                            }
                            container = candidate;
                            break;
                        }

                        if (container != null) {
                            break;
                        }
                    }

                    // Si ningún tipo tiene candidatos disponibles, finalizar este ciclo de tick
                    if (container == null) {
                        break;
                    }

                    Location loc = container.toLocation();
                    if (loc == null || loc.getWorld() == null) {
                        refillManager.refillContainer(container);
                        processedCount++;
                        continue;
                    }

                    World world = loc.getWorld();
                    int chunkX = loc.getBlockX() >> 4;
                    int chunkZ = loc.getBlockZ() >> 4;

                    if (world.isChunkLoaded(chunkX, chunkZ)) {
                        // Chunk cargado -> procesar de inmediato en el main thread
                        RefillResult res = refillManager.refillContainer(container);
                        processedCount++;
                        if (res == RefillResult.SUCCESS) {
                            refilledCount++;
                        }
                    } else if (refillManager.isRefillLoadedChunksOnly()) {
                        // Si solo se procesan chunks cargados, procesar y descartar de forma segura
                        refillManager.refillContainer(container);
                        processedCount++;
                    } else {
                        // Carga asíncrona controlada respetando el límite de chunks simultáneos
                        int maxLoading = refillManager.getMaxChunksLoadedByRefill();
                        if (currentlyLoadingChunks.get() >= maxLoading) {
                            // Reencolar al frente para no perder el turno si los chunks están saturados
                            queuesByType.get(container.getContainerType()).addFirst(container);
                            break;
                        }

                        processedCount++;
                        currentlyLoadingChunks.incrementAndGet();
                        refillManager.getStats().recordChunkLoaded();

                        final LootContainer finalContainer = container;
                        world.getChunkAtAsync(chunkX, chunkZ, false).thenAccept(loadedChunk -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    if (loadedChunk != null && loadedChunk.isLoaded()) {
                                        refillManager.refillContainer(finalContainer);
                                        if (refillManager.isUnloadAfterRefill() && world.getPlayers().isEmpty()) {
                                            world.unloadChunkRequest(chunkX, chunkZ);
                                            refillManager.getStats().recordChunkUnloaded();
                                        }
                                    }
                                } finally {
                                    currentlyLoadingChunks.decrementAndGet();
                                }
                            });
                        }).exceptionally(ex -> {
                            currentlyLoadingChunks.decrementAndGet();
                            return null;
                        });
                    }
                }

                if (processedCount > 0) {
                    long duration = System.nanoTime() - startNanos;
                    refillManager.getStats().recordCycle(processedCount, refilledCount, duration);
                }
            }
        }.runTaskTimer(plugin, delay, delay);
    }

    public void stop() {
        if (queueTask != null) {
            queueTask.cancel();
            queueTask = null;
        }
        for (Deque<LootContainer> q : queuesByType.values()) {
            q.clear();
        }
        currentlyLoadingChunks.set(0);
    }

    public void clear() {
        for (Deque<LootContainer> q : queuesByType.values()) {
            q.clear();
        }
        currentlyLoadingChunks.set(0);
    }

    public void removeContainersForWorld(String worldName) {
        for (Deque<LootContainer> q : queuesByType.values()) {
            q.removeIf(c -> c.getWorld().equalsIgnoreCase(worldName));
        }
    }

    public void pollAllDueContainers() {
        for (ContainerType type : ROUND_ROBIN_TYPES) {
            queuesByType.get(type).clear();
            pollFromDatabase(type);
        }
    }

    private void pollFromDatabase(ContainerType type) {
        Deque<LootContainer> queue = queuesByType.get(type);
        if (!queue.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<LootContainer> due = containerManager.getContainersDueForRefill(type, now, 25);
        for (LootContainer c : due) {
            if (!refillManager.isOnRetryCooldown(c.getId())) {
                queue.add(c);
            }
        }
    }

    public int getQueueSize(ContainerType type) {
        Deque<LootContainer> q = queuesByType.get(type);
        return q != null ? q.size() : 0;
    }

    public int getTotalQueueSize() {
        int total = 0;
        for (Deque<LootContainer> q : queuesByType.values()) {
            total += q.size();
        }
        return total;
    }

    public boolean isTaskRunning() {
        return queueTask != null && !queueTask.isCancelled();
    }

    public int getTaskId() {
        return queueTask != null ? queueTask.getTaskId() : -1;
    }

    public int getLoadingChunksCount() {
        return currentlyLoadingChunks.get();
    }
}
