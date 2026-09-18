package com.arcraft.lootrefill.scanner;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldScanner {

    private final LootRefillPlugin plugin;
    private final ContainerManager containerManager;

    private ScanJob activeJob;
    private List<RegionFileScanner.ChunkCoord> chunkQueue;
    private int currentChunkIndex;
    private BukkitTask scanTask;

    private final List<LootContainer> pendingSaveBatch;
    private final Map<ContainerType, Integer> foundTypeCounters;

    public WorldScanner(LootRefillPlugin plugin, ContainerManager containerManager) {
        this.plugin = plugin;
        this.containerManager = containerManager;
        this.chunkQueue = Collections.synchronizedList(new ArrayList<>());
        this.pendingSaveBatch = Collections.synchronizedList(new ArrayList<>());
        this.foundTypeCounters = new ConcurrentHashMap<>();
        for (ContainerType type : ContainerType.values()) {
            foundTypeCounters.put(type, 0);
        }
    }

    public boolean isScanning() {
        return activeJob != null && activeJob.getStatus() == ScanJob.Status.RUNNING;
    }

    public ScanJob getActiveJob() {
        return activeJob;
    }

    public void startScan(World world, CommandSender sender) {
        if (world == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo especificado no existe.");
            return;
        }

        if (isScanning()) {
            MessageUtil.sendMessage(sender, "&cYa existe un escaneo en curso para el mundo: &e" + activeJob.getWorld());
            MessageUtil.sendMessage(sender, "&7Usa &e/loot scan status &7o &e/loot scan pause&7.");
            return;
        }

        MessageUtil.sendMessage(sender, "&6Iniciando escaneo de &e" + world.getName() + "&6...");
        MessageUtil.sendMessage(sender, "&7Indexando chunks generados en segundo plano...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<RegionFileScanner.ChunkCoord> coords = RegionFileScanner.findGeneratedChunks(world);
            if (coords.isEmpty()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    coords.add(new RegionFileScanner.ChunkCoord(chunk.getX(), chunk.getZ()));
                }
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (coords.isEmpty()) {
                    MessageUtil.sendMessage(sender, "&cNo se encontraron chunks generados en el mundo &e" + world.getName() + "&c.");
                    return;
                }

                this.chunkQueue = Collections.synchronizedList(coords);
                this.currentChunkIndex = 0;
                this.pendingSaveBatch.clear();
                for (ContainerType type : ContainerType.values()) {
                    foundTypeCounters.put(type, 0);
                }

                this.activeJob = ScanJob.createNew(world.getName(), coords.size());
                plugin.getDatabaseManager().saveScanJob(activeJob);

                MessageUtil.sendMessage(sender, "&aIndexados &e" + coords.size() + " &achunks. Comenzando análisis progresivo.");
                launchScanTask(world);
            });
        });
    }

    public void resumeScan(CommandSender sender) {
        if (activeJob == null) {
            activeJob = plugin.getDatabaseManager().loadIncompleteScanJob();
        }

        if (activeJob == null || (activeJob.getStatus() != ScanJob.Status.PAUSED && activeJob.getStatus() != ScanJob.Status.RUNNING)) {
            MessageUtil.sendMessage(sender, "&cNo hay ningún escaneo pausado o incompleto para reanudar.");
            return;
        }

        World world = Bukkit.getWorld(activeJob.getWorld());
        if (world == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo del escaneo (&e" + activeJob.getWorld() + "&c) no está cargado.");
            return;
        }

        activeJob.setStatus(ScanJob.Status.RUNNING);
        plugin.getDatabaseManager().saveScanJob(activeJob);

        if (chunkQueue.isEmpty()) {
            MessageUtil.sendMessage(sender, "&7Re-indexando chunks para continuar desde el chunk &e" + activeJob.getProcessedChunks() + "&7...");
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<RegionFileScanner.ChunkCoord> coords = RegionFileScanner.findGeneratedChunks(world);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    this.chunkQueue = Collections.synchronizedList(coords);
                    this.currentChunkIndex = Math.min(activeJob.getProcessedChunks(), coords.size());
                    launchScanTask(world);
                    MessageUtil.sendMessage(sender, "&aEscaneo reanudado en &e" + world.getName() + "&a.");
                });
            });
        } else {
            launchScanTask(world);
            MessageUtil.sendMessage(sender, "&aEscaneo reanudado en &e" + world.getName() + "&a.");
        }
    }

    public void pauseScan(CommandSender sender) {
        if (!isScanning()) {
            MessageUtil.sendMessage(sender, "&cNo hay ningún escaneo activo para pausar.");
            return;
        }

        stopTask();
        flushBatchAsync();
        activeJob.setStatus(ScanJob.Status.PAUSED);
        plugin.getDatabaseManager().saveScanJob(activeJob);

        MessageUtil.sendMessage(sender, "&eEscaneo pausado en el chunk &f" + activeJob.getProcessedChunks() + " / " + activeJob.getTotalChunks());
    }

    public void cancelScan(CommandSender sender) {
        if (activeJob == null || (activeJob.getStatus() != ScanJob.Status.RUNNING && activeJob.getStatus() != ScanJob.Status.PAUSED)) {
            MessageUtil.sendMessage(sender, "&cNo hay ningún escaneo activo o pausado para cancelar.");
            return;
        }

        stopTask();
        flushBatchAsync();
        activeJob.setStatus(ScanJob.Status.CANCELLED);
        plugin.getDatabaseManager().saveScanJob(activeJob);

        MessageUtil.sendMessage(sender, "&cEscaneo cancelado. Se guardaron los contenedores descubiertos hasta el momento.");
        activeJob = null;
    }

    private void launchScanTask(World world) {
        stopTask();

        long maxNanosPerTick = plugin.getConfig().getLong("scanner.max-ms-per-tick", 3) * 1_000_000L;
        int chunksPerTick = Math.max(1, plugin.getConfig().getInt("scanner.chunks-per-tick", 1));
        int pauseTicks = Math.max(1, plugin.getConfig().getInt("scanner.pause-between-batches", 1));
        int saveEvery = Math.max(10, plugin.getConfig().getInt("scanner.save-every", 100));

        scanTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activeJob == null || activeJob.getStatus() != ScanJob.Status.RUNNING) {
                    cancel();
                    return;
                }

                long startNanos = System.nanoTime();
                int processedInTick = 0;

                while (currentChunkIndex < chunkQueue.size() && processedInTick < chunksPerTick) {
                    RegionFileScanner.ChunkCoord coord = chunkQueue.get(currentChunkIndex);
                    activeJob.setCurrentChunkX(coord.x());
                    activeJob.setCurrentChunkZ(coord.z());

                    boolean wasLoaded = world.isChunkLoaded(coord.x(), coord.z());

                    if (wasLoaded) {
                        Chunk chunk = world.getChunkAt(coord.x(), coord.z());
                        inspectChunk(chunk, world);
                    } else {
                        world.getChunkAtAsync(coord.x(), coord.z(), false).thenAccept(chunk -> {
                            if (chunk != null) {
                                inspectChunk(chunk, world);
                                world.unloadChunkRequest(coord.x(), coord.z());
                            }
                        });
                    }

                    currentChunkIndex++;
                    activeJob.incrementProcessedChunks();
                    processedInTick++;

                    if (System.nanoTime() - startNanos >= maxNanosPerTick) {
                        break;
                    }
                }

                if (pendingSaveBatch.size() >= saveEvery || currentChunkIndex >= chunkQueue.size()) {
                    flushBatchAsync();
                    plugin.getDatabaseManager().saveScanJob(activeJob);
                }

                if (currentChunkIndex >= chunkQueue.size()) {
                    activeJob.setStatus(ScanJob.Status.COMPLETED);
                    plugin.getDatabaseManager().saveScanJob(activeJob);
                    cancel();

                    Bukkit.broadcast(MessageUtil.color("&6&lLootRefill &8» &a¡Escaneo del mundo &e" + world.getName() + " &acompletado con éxito!"), "lootrefill.admin");
                    Bukkit.broadcast(MessageUtil.color("&7Total chunks: &f" + activeJob.getProcessedChunks() + " &8| &7Contenedores MAP registrados: &e" + activeJob.getFoundContainers()), "lootrefill.admin");
                }
            }
        }.runTaskTimer(plugin, pauseTicks, pauseTicks);
    }

    private void inspectChunk(Chunk chunk, World world) {
        if (chunk == null) return;

        BlockState[] tileEntities = chunk.getTileEntities(false);
        if (tileEntities == null || tileEntities.length == 0) return;

        int foundInChunk = 0;
        long now = System.currentTimeMillis();

        for (BlockState state : tileEntities) {
            ContainerType type = ContainerType.fromBlockState(state);
            if (type == null || !containerManager.isContainerTypeEnabled(type)) {
                continue;
            }

            Location loc = new Location(world, state.getX(), state.getY(), state.getZ());
            LootContainer existing = containerManager.getContainer(loc);

            // Regla Etapa 2.1: Nunca convertir PLAYER en MAP ni recuperar contenedores BROKEN
            if (existing != null) {
                if (existing.getSource() == ContainerSource.PLAYER || existing.getStatus() == ContainerStatus.BROKEN) {
                    continue;
                }
            }

            int interval = plugin.getConfig().getInt("refill.intervals." + type.getConfigKey() + ".interval", 1800);
            boolean refillEnabled = plugin.getConfig().getBoolean("refill.intervals." + type.getConfigKey() + ".enabled", true);

            LootContainer container = new LootContainer(
                    existing != null ? existing.getId() : UUID.randomUUID(),
                    world.getName(),
                    state.getX(),
                    state.getY(),
                    state.getZ(),
                    type,
                    existing != null ? existing.getLootTableId() : "",
                    true,
                    false,
                    existing != null ? existing.getLastLoot() : 0L,
                    now + (interval * 1000L),
                    refillEnabled,
                    interval,
                    ContainerSource.MAP,
                    ContainerStatus.ACTIVE,
                    true,
                    true,
                    existing != null ? existing.getCreatedAt() : now,
                    now
            );

            pendingSaveBatch.add(container);
            foundTypeCounters.merge(type, 1, Integer::sum);
            foundInChunk++;
        }

        if (foundInChunk > 0 && activeJob != null) {
            activeJob.addFoundContainers(foundInChunk);
        }
    }

    private void flushBatchAsync() {
        if (pendingSaveBatch.isEmpty()) return;

        List<LootContainer> copy;
        synchronized (pendingSaveBatch) {
            copy = new ArrayList<>(pendingSaveBatch);
            pendingSaveBatch.clear();
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            containerManager.saveContainersBatch(copy);
        });
    }

    public void stopTask() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }
    }

    public void showStatus(CommandSender sender) {
        if (activeJob == null) {
            activeJob = plugin.getDatabaseManager().loadIncompleteScanJob();
        }

        if (activeJob == null) {
            MessageUtil.sendMessage(sender, "&7No hay ningún trabajo de escaneo registrado o activo.");
            return;
        }

        double percent = activeJob.getProgressPercentage();
        int progressBlocks = (int) (percent / 5.0);
        String bar = "§a" + "█".repeat(Math.max(0, progressBlocks)) + "§8" + "░".repeat(Math.max(0, 20 - progressBlocks));

        long elapsed = activeJob.getElapsedTimeSeconds();
        long eta = activeJob.getEstimatedTimeRemainingSeconds();
        String elapsedStr = String.format("%02d:%02d", elapsed / 60, elapsed % 60);
        String etaStr = String.format("%02d:%02d", eta / 60, eta % 60);

        double tps = Bukkit.getTPS()[0];
        String tpsColor = tps >= 18.0 ? "&a" : (tps >= 15.0 ? "&e" : "&c");

        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendRaw(sender, "&6&lLootRefill World Scanner &8» &e" + activeJob.getWorld());
        MessageUtil.sendRaw(sender, "&7Estado: &f" + activeJob.getStatus().name());
        MessageUtil.sendRaw(sender, "&7Progreso: [" + bar + "&7] &f" + String.format("%.1f", percent) + "%");
        MessageUtil.sendRaw(sender, "&7Chunks: &e" + activeJob.getProcessedChunks() + " &7/ &f" + activeJob.getTotalChunks());
        MessageUtil.sendRaw(sender, "&7Contenedores MAP encontrados: &a" + activeJob.getFoundContainers());
        MessageUtil.sendRaw(sender, "  &8• &7Cofres: &f" + (foundTypeCounters.get(ContainerType.CHEST) + foundTypeCounters.get(ContainerType.TRAPPED_CHEST)));
        MessageUtil.sendRaw(sender, "  &8• &7Barriles: &f" + foundTypeCounters.get(ContainerType.BARREL));
        MessageUtil.sendRaw(sender, "  &8• &7Hornos: &f" + (foundTypeCounters.get(ContainerType.FURNACE) + foundTypeCounters.get(ContainerType.BLAST_FURNACE) + foundTypeCounters.get(ContainerType.SMOKER)));
        MessageUtil.sendRaw(sender, "  &8• &7Dispensers/Droppers: &f" + (foundTypeCounters.get(ContainerType.DISPENSER) + foundTypeCounters.get(ContainerType.DROPPER)));
        MessageUtil.sendRaw(sender, "&7Tiempo transcurrido: &f" + elapsedStr + " &8| &7ETA: &e" + etaStr);
        MessageUtil.sendRaw(sender, "&7TPS actual: " + tpsColor + String.format("%.2f", tps));
        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    public Map<ContainerType, Integer> getFoundTypeCounters() {
        return foundTypeCounters;
    }
}
