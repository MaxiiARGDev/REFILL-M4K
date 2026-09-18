package com.arcraft.lootrefill.populate;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public class PopulateQueue {

    private final LootRefillPlugin plugin;
    private final ContainerPopulator populator;

    private PopulateJob activeJob;
    private PopulateStats stats;
    private Deque<LootContainer> queue;
    private BukkitTask task;

    public PopulateQueue(LootRefillPlugin plugin, ContainerPopulator populator) {
        this.plugin = plugin;
        this.populator = populator;
        this.queue = new ArrayDeque<>();
        this.stats = new PopulateStats();
    }

    public boolean isRunning() {
        return activeJob != null && activeJob.getStatus() == PopulateJob.Status.RUNNING;
    }

    public PopulateJob getActiveJob() {
        return activeJob;
    }

    public PopulateStats getStats() {
        return stats;
    }

    public void start(World world, List<LootContainer> candidates) {
        cancel();

        this.queue = new ArrayDeque<>(candidates);
        this.stats = new PopulateStats();
        this.stats.setTotal(candidates.size());
        this.activeJob = PopulateJob.createNew(world.getName(), candidates.size());
        this.populator.resetDoubleChestTracker();

        plugin.getDatabaseManager().savePopulateJob(activeJob);

        int delay = Math.max(1, plugin.getConfig().getInt("populate.processing.batch-delay-ticks", 1));
        long maxNanos = plugin.getConfig().getLong("populate.processing.max-ms-per-tick", 2) * 1_000_000L;
        int maxPerTick = Math.max(1, plugin.getConfig().getInt("populate.processing.containers-per-tick", 5));

        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeJob == null || activeJob.getStatus() != PopulateJob.Status.RUNNING) {
                    cancel();
                    return;
                }

                long startNanos = System.nanoTime();
                int processedInTick = 0;

                while (!queue.isEmpty() && processedInTick < maxPerTick) {
                    LootContainer container = queue.poll();
                    if (container != null) {
                        processContainer(world, container);
                        processedInTick++;
                    }

                    if (System.nanoTime() - startNanos >= maxNanos) {
                        break;
                    }
                }

                // Sincronizar estadísticas en el Job
                activeJob.setProcessed(stats.getProcessed());
                activeJob.setPopulated(stats.getPopulated());
                activeJob.setSkipped(stats.getTotalSkipped());

                if (queue.isEmpty()) {
                    activeJob.setStatus(PopulateJob.Status.COMPLETED);
                    plugin.getDatabaseManager().savePopulateJob(activeJob);
                    cancel();

                    Bukkit.broadcast(MessageUtil.color("&6&lLootRefill &8» &a¡Populate completado en &e" + world.getName() + "&a!"), "lootrefill.admin");
                    Bukkit.broadcast(MessageUtil.color("&7Procesados: &f" + stats.getProcessed()
                            + " &8| &7Poblados: &a" + stats.getPopulated()
                            + " &8| &7Saltados: &e" + stats.getTotalSkipped()
                            + " &8| &7Errores: &c" + stats.getErrors()), "lootrefill.admin");
                }
            }
        }.runTaskTimer(plugin, delay, delay);
    }

    private void processContainer(World world, LootContainer container) {
        Location loc = container.toLocation();
        if (loc == null) {
            stats.addResult(PopulateResult.SKIPPED_INVALID);
            return;
        }

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;

        if (world.isChunkLoaded(chunkX, chunkZ)) {
            PopulateResult result = populator.populate(container);
            stats.addResult(result);
        } else {
            // Carga controlada asíncrona sin generar chunks artificiales
            world.getChunkAtAsync(chunkX, chunkZ, false).thenAccept(chunk -> {
                if (chunk != null) {
                    PopulateResult result = populator.populate(container);
                    stats.addResult(result);
                    world.unloadChunkRequest(chunkX, chunkZ);
                } else {
                    stats.addResult(PopulateResult.SKIPPED_INVALID);
                }
            });
        }
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        if (activeJob != null && activeJob.getStatus() == PopulateJob.Status.RUNNING) {
            activeJob.setStatus(PopulateJob.Status.CANCELLED);
            plugin.getDatabaseManager().savePopulateJob(activeJob);
        }
        queue.clear();
    }
}
