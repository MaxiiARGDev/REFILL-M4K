package com.arcraft.lootrefill.region;

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
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class RegionScanner {

    private final LootRefillPlugin plugin;
    private final ContainerManager containerManager;
    private final RegionManager regionManager;
    private final Set<UUID> activeScanningPlayers;

    public RegionScanner(LootRefillPlugin plugin, ContainerManager containerManager, RegionManager regionManager) {
        this.plugin = plugin;
        this.containerManager = containerManager;
        this.regionManager = regionManager;
        this.activeScanningPlayers = ConcurrentHashMap.newKeySet();
    }

    public boolean isPlayerScanning(UUID playerId) {
        return activeScanningPlayers.contains(playerId);
    }

    public void startScan(Player player) {
        UUID playerId = player.getUniqueId();
        if (isPlayerScanning(playerId)) {
            MessageUtil.sendMessage(player, "&cYa tienes un escaneo regional en proceso. Espera a que finalice.");
            return;
        }

        RegionSelection selection = regionManager.getSelectionOrNull(playerId);
        if (selection == null || !selection.isComplete()) {
            MessageUtil.sendMessage(player, "&cDebes definir ambos puntos (A y B) con la varita antes de escanear.");
            MessageUtil.sendMessage(player, "&7Usa &e/loot wand &7para obtener la varita y selecciona con click izquierdo y derecho.");
            return;
        }

        World world = selection.getWorld();
        if (world == null) {
            MessageUtil.sendMessage(player, "&cEl mundo de la selección no está disponible o cargado.");
            return;
        }

        activeScanningPlayers.add(playerId);

        List<int[]> chunkCoords = new ArrayList<>();
        for (int cx = selection.getMinChunkX(); cx <= selection.getMaxChunkX(); cx++) {
            for (int cz = selection.getMinChunkZ(); cz <= selection.getMaxChunkZ(); cz++) {
                chunkCoords.add(new int[]{cx, cz});
            }
        }

        int totalChunks = chunkCoords.size();
        MessageUtil.sendMessage(player, "&6Iniciando escaneo regional en &e" + world.getName() + "&6...");
        MessageUtil.sendMessage(player, "&7Inspeccionando &e" + totalChunks + " &7chunks intersecantes de forma progresiva...");

        List<LootContainer> newCandidates = Collections.synchronizedList(new ArrayList<>());
        Set<String> processedDoubleChests = Collections.synchronizedSet(new HashSet<>());
        Map<ContainerType, Integer> countersByType = new EnumMap<>(ContainerType.class);
        for (ContainerType type : ContainerType.values()) {
            countersByType.put(type, 0);
        }

        int[] statsCounters = new int[3]; // [0] = alreadyMap, [1] = player, [2] = broken

        long maxNanosPerTick = plugin.getConfig().getLong("scanner.max-ms-per-tick", 5) * 1_000_000L;
        long now = System.currentTimeMillis();

        new BukkitRunnable() {
            private int chunkIndex = 0;
            private final AtomicBoolean chunkLoading = new AtomicBoolean(false);

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeScanningPlayers.remove(playerId);
                    cancel();
                    return;
                }

                if (chunkLoading.get()) {
                    return;
                }

                long startNanos = System.nanoTime();

                while (chunkIndex < chunkCoords.size()) {
                    if (System.nanoTime() - startNanos >= maxNanosPerTick) {
                        return;
                    }

                    int[] coord = chunkCoords.get(chunkIndex);
                    int cx = coord[0];
                    int cz = coord[1];

                    if (world.isChunkLoaded(cx, cz)) {
                        Chunk chunk = world.getChunkAt(cx, cz);
                        inspectChunk(chunk, world, selection, newCandidates, processedDoubleChests, countersByType, statsCounters, now);
                        chunkIndex++;
                    } else {
                        chunkLoading.set(true);
                        world.getChunkAtAsync(cx, cz, false).thenAccept(loadedChunk -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    if (loadedChunk != null && loadedChunk.isLoaded()) {
                                        inspectChunk(loadedChunk, world, selection, newCandidates, processedDoubleChests, countersByType, statsCounters, now);
                                        world.unloadChunkRequest(cx, cz);
                                    }
                                } finally {
                                    chunkIndex++;
                                    chunkLoading.set(false);
                                }
                            });
                        }).exceptionally(ex -> {
                            chunkIndex++;
                            chunkLoading.set(false);
                            return null;
                        });
                        return;
                    }
                }

                // Escaneo completado
                activeScanningPlayers.remove(playerId);
                cancel();

                RegionScanResult result = new RegionScanResult(
                        selection,
                        newCandidates,
                        statsCounters[0],
                        statsCounters[1],
                        statsCounters[2],
                        countersByType
                );

                regionManager.setLastScanResult(playerId, result);
                sendScanCompleteReport(player, result);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void inspectChunk(Chunk chunk,
                              World world,
                              RegionSelection selection,
                              List<LootContainer> newCandidates,
                              Set<String> processedDoubleChests,
                              Map<ContainerType, Integer> countersByType,
                              int[] statsCounters,
                              long now) {
        if (chunk == null) return;

        BlockState[] tileEntities = chunk.getTileEntities(false);
        if (tileEntities == null || tileEntities.length == 0) return;

        for (BlockState state : tileEntities) {
            int bx = state.getX();
            int by = state.getY();
            int bz = state.getZ();

            // Filtrar estrictamente si está dentro de la caja seleccionada
            if (!selection.contains(bx, by, bz)) {
                continue;
            }

            ContainerType type = ContainerType.fromBlockState(state);
            if (type == null || !containerManager.isContainerTypeEnabled(type)) {
                continue;
            }

            // Deduplicación física estricta de Double Chest
            if (state instanceof Chest && ((Chest) state).getInventory().getHolder() instanceof DoubleChest doubleChest) {
                Location leftLoc = ((Chest) doubleChest.getLeftSide()).getLocation();
                Location rightLoc = ((Chest) doubleChest.getRightSide()).getLocation();
                String pairKey = world.getName() + ":" + Math.min(leftLoc.getBlockX(), rightLoc.getBlockX()) + ":" + Math.min(leftLoc.getBlockZ(), rightLoc.getBlockZ());

                if (processedDoubleChests.contains(pairKey)) {
                    continue;
                }
                processedDoubleChests.add(pairKey);
            }

            countersByType.merge(type, 1, Integer::sum);

            Location loc = new Location(world, bx, by, bz);
            LootContainer existing = containerManager.getContainer(loc);

            if (existing != null) {
                if (existing.getSource() == ContainerSource.PLAYER) {
                    statsCounters[1]++; // PLAYER
                } else if (existing.getStatus() == ContainerStatus.BROKEN) {
                    statsCounters[2]++; // BROKEN
                } else if (existing.getSource() == ContainerSource.MAP) {
                    statsCounters[0]++; // ALREADY_MAP
                }
            } else {
                // Nuevo contenedor candidato para registrar como MAP
                int interval = plugin.getConfig().getInt("refill.intervals." + type.getConfigKey() + ".interval", 1800);
                boolean refillEnabled = plugin.getConfig().getBoolean("refill.intervals." + type.getConfigKey() + ".enabled", true);

                LootContainer candidate = new LootContainer(
                        UUID.randomUUID(),
                        world.getName(),
                        bx,
                        by,
                        bz,
                        type,
                        "", // Sin loot table asignada aún (Etapa 4.1)
                        true,
                        false,
                        0L,
                        now, // Inmediatamente elegible para futuro refill
                        refillEnabled,
                        interval,
                        ContainerSource.MAP,
                        ContainerStatus.ACTIVE,
                        true,
                        true,
                        now,
                        now
                );
                newCandidates.add(candidate);
            }
        }
    }

    private void sendScanCompleteReport(Player player, RegionScanResult result) {
        RegionSelection sel = result.getSelection();

        MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendRaw(player, "&6&lLootRefill » Escaneo Regional Completado");
        MessageUtil.sendRaw(player, "&7Mundo: &e" + sel.getWorld().getName() + " &8| &7Volumen: &f" + sel.getVolume() + " &7bloques (&b" + sel.getTotalChunks() + " &7chunks)");
        MessageUtil.sendRaw(player, "&7Límites: &f(" + sel.getMinX() + ", " + sel.getMinY() + ", " + sel.getMinZ() + ") &7a &f(" + sel.getMaxX() + ", " + sel.getMaxY() + ", " + sel.getMaxZ() + ")");
        MessageUtil.sendRaw(player, "");
        MessageUtil.sendRaw(player, "&7Total de contenedores detectados: &e" + result.getTotalContainers());

        for (Map.Entry<ContainerType, Integer> entry : result.getCountersByType().entrySet()) {
            if (entry.getValue() > 0) {
                MessageUtil.sendRaw(player, "  &8• &f" + entry.getKey().name() + ": &a" + entry.getValue());
            }
        }

        MessageUtil.sendRaw(player, "");
        MessageUtil.sendRaw(player, "&7Estado de registro en la región:");
        MessageUtil.sendRaw(player, "  &8• &7Ya registrados como MAP: &a" + result.getAlreadyMapCount() + " &7(Se conservan intactos)");
        MessageUtil.sendRaw(player, "  &8• &cProtegidos de jugadores (PLAYER): &f" + result.getPlayerCount() + " &7(Inmodificables)");
        MessageUtil.sendRaw(player, "  &8• &8Marcados previamente como rotos (BROKEN): &f" + result.getBrokenCount() + " &7(Intactos)");
        MessageUtil.sendRaw(player, "  &8• &eNuevos candidatos a registrar como MAP: &6" + result.getNewCandidates().size());
        MessageUtil.sendRaw(player, "");

        if (result.getNewCandidates().isEmpty()) {
            MessageUtil.sendRaw(player, "&eNo se detectaron contenedores nuevos para registrar en esta región.");
        } else {
            MessageUtil.sendRaw(player, "&aPara registrar los &e" + result.getNewCandidates().size() + " &anuevos contenedores como MAP:");
            MessageUtil.sendRaw(player, "&e/loot region assign");
        }
        MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    public void handleAssignCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&cEste comando solo puede ser ejecutado por un jugador.");
            return;
        }

        RegionScanResult result = regionManager.getLastScanResult(player.getUniqueId());
        if (result == null) {
            MessageUtil.sendMessage(player, "&cNo tienes ningún escaneo regional reciente.");
            MessageUtil.sendMessage(player, "&7Primero selecciona la región con la varita (&e/loot wand&7) y ejecuta &e/loot region scan&7.");
            return;
        }

        boolean confirm = args.length >= 3 && args[2].equalsIgnoreCase("confirm");

        if (!confirm) {
            // Modo Preview
            MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendRaw(player, "&6&lLootRefill » Vista Previa de Asignación Regional");
            MessageUtil.sendRaw(player, "&7Mundo: &e" + result.getSelection().getWorld().getName());
            MessageUtil.sendRaw(player, "&7Total en región: &f" + result.getTotalContainers());
            MessageUtil.sendRaw(player, "");
            MessageUtil.sendRaw(player, "&7Contenedores MAP existentes: &a" + result.getAlreadyMapCount() + " &7(Se mantendrán)");
            MessageUtil.sendRaw(player, "&7Contenedores PLAYER: &c" + result.getPlayerCount() + " &7(Protegidos, JAMÁS se convertirán a MAP)");
            MessageUtil.sendRaw(player, "&7Contenedores BROKEN: &8" + result.getBrokenCount() + " &7(Permanecerán rotos)");
            MessageUtil.sendRaw(player, "&7Nuevos contenedores a registrar como MAP: &e" + result.getNewCandidates().size());
            MessageUtil.sendRaw(player, "");

            if (result.getNewCandidates().isEmpty()) {
                MessageUtil.sendRaw(player, "&eNo hay nuevos contenedores para registrar.");
            } else {
                MessageUtil.sendRaw(player, "&aNingún contenedor fue modificado todavía. &7(Modo Preview)");
                MessageUtil.sendRaw(player, "&7Para registrar definitivamente los &e" + result.getNewCandidates().size() + " &7contenedores nuevos ejecuta:");
                MessageUtil.sendRaw(player, "&e/loot region assign confirm");
            }
            MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return;
        }

        // Modo Confirmación Real
        List<LootContainer> candidates = result.getNewCandidates();
        if (candidates.isEmpty()) {
            MessageUtil.sendMessage(player, "&eNo hay contenedores nuevos para registrar en la región.");
            return;
        }

        int savedCount = containerManager.saveContainersBatch(candidates);

        MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendRaw(player, "&6&lLootRefill » Asignación Regional Confirmada");
        MessageUtil.sendRaw(player, "&a¡Se registraron exitosamente &e" + savedCount + " &anuevos contenedores como MAP!");
        MessageUtil.sendRaw(player, "&7Estado: &fACTIVE &8| &7Managed: &ftrue &8| &7Registered: &ftrue");
        MessageUtil.sendRaw(player, "&7Loot Table: &eSin tabla aún &7(Usa &e/loot assign &7para asignarles loot)");
        MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Limpiar candidatos asignados
        regionManager.setLastScanResult(player.getUniqueId(), null);
    }
}
