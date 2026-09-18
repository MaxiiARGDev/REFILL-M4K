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
        List<LootContainer> playerCandidates = Collections.synchronizedList(new ArrayList<>());
        Set<String> processedDoubleChests = Collections.synchronizedSet(new HashSet<>());
        Map<ContainerType, Integer> countersByType = new EnumMap<>(ContainerType.class);
        for (ContainerType type : ContainerType.values()) {
            countersByType.put(type, 0);
        }

        int[] statsCounters = new int[2]; // [0] = alreadyMap, [1] = broken

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
                        inspectChunk(chunk, world, selection, newCandidates, playerCandidates, processedDoubleChests, countersByType, statsCounters, now);
                        chunkIndex++;
                    } else {
                        chunkLoading.set(true);
                        world.getChunkAtAsync(cx, cz, false).thenAccept(loadedChunk -> {
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                try {
                                    if (loadedChunk != null && loadedChunk.isLoaded()) {
                                        inspectChunk(loadedChunk, world, selection, newCandidates, playerCandidates, processedDoubleChests, countersByType, statsCounters, now);
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
                        playerCandidates,
                        statsCounters[0],
                        statsCounters[1],
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
                              List<LootContainer> playerCandidates,
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
                    playerCandidates.add(existing);
                } else if (existing.getStatus() == ContainerStatus.BROKEN) {
                    statsCounters[1]++; // BROKEN
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
                        null, // NULL loot table per Ajuste 4.1.1
                        true,
                        false,
                        0L,
                        null, // NULL nextRefill per Ajuste 4.1.1 (hasta que se configure loot)
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
        MessageUtil.sendRaw(player, "  &8• &cProtegidos de jugadores (PLAYER): &f" + result.getPlayerCount() + " &7(Protegidos por defecto)");
        MessageUtil.sendRaw(player, "  &8• &8Marcados previamente como rotos (BROKEN): &f" + result.getBrokenCount() + " &7(Intactos)");
        MessageUtil.sendRaw(player, "  &8• &eNuevos candidatos a registrar como MAP: &6" + result.getNewCandidates().size());
        MessageUtil.sendRaw(player, "");

        if (result.getNewCandidates().isEmpty() && result.getPlayerCount() == 0) {
            MessageUtil.sendRaw(player, "&eNo se detectaron contenedores nuevos ni PLAYER para procesar en esta región.");
        } else {
            if (!result.getNewCandidates().isEmpty()) {
                MessageUtil.sendRaw(player, "&aPara registrar los &e" + result.getNewCandidates().size() + " &anuevos contenedores como MAP:");
                MessageUtil.sendRaw(player, "  &8» &e/loot region assign confirm");
            }
            if (result.getPlayerCount() > 0) {
                MessageUtil.sendRaw(player, "&cOpción administrativa PLAYER → MAP:");
                MessageUtil.sendRaw(player, "&7Para convertir explícitamente los &e" + result.getPlayerCount() + " &7PLAYER a MAP (conservando inventarios):");
                MessageUtil.sendRaw(player, "  &8» &e/loot region assign confirm-player");
            }
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

        boolean confirmPlayer = args.length >= 3 && args[2].equalsIgnoreCase("confirm-player");
        if (confirmPlayer) {
            List<LootContainer> playerList = result.getPlayerCandidates();
            if (playerList.isEmpty()) {
                MessageUtil.sendMessage(player, "&eNo hay contenedores PLAYER para convertir en la región seleccionada.");
                return;
            }

            int converted = containerManager.convertPlayerContainersToMap(playerList);

            MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendRaw(player, "&6&lLootRefill » Conversión PLAYER → MAP Confirmada");
            MessageUtil.sendRaw(player, "&a¡Se convirtieron exitosamente &e" + converted + " &acontenedores PLAYER a MAP!");
            MessageUtil.sendRaw(player, "&7Estado: &fACTIVE &8| &7Managed: &ftrue &8| &7Registered: &ftrue");
            MessageUtil.sendRaw(player, "&7Loot Table: &cSin Loot configurado &7(next_refill: NULL)");
            MessageUtil.sendRaw(player, "&aEl inventario existente de cada contenedor fue conservado intacto.");
            MessageUtil.sendRaw(player, "&7Usa &e/loot assign &7para asignarles tablas de loot cuando lo desees.");
            MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
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
            MessageUtil.sendRaw(player, "&7Contenedores PLAYER: &c" + result.getPlayerCount() + " &7(Protegidos por defecto)");
            MessageUtil.sendRaw(player, "&7Contenedores BROKEN: &8" + result.getBrokenCount() + " &7(Permanecerán rotos)");
            MessageUtil.sendRaw(player, "&7Nuevos contenedores a registrar como MAP: &e" + result.getNewCandidates().size());
            MessageUtil.sendRaw(player, "");

            if (result.getNewCandidates().isEmpty() && result.getPlayerCount() == 0) {
                MessageUtil.sendRaw(player, "&eNo hay nuevos contenedores ni PLAYER para procesar en esta región.");
            } else {
                MessageUtil.sendRaw(player, "&aNingún contenedor fue modificado todavía. &7(Modo Preview)");
                if (!result.getNewCandidates().isEmpty()) {
                    MessageUtil.sendRaw(player, "&7Para registrar los &e" + result.getNewCandidates().size() + " &7nuevos candidatos como MAP ejecuta:");
                    MessageUtil.sendRaw(player, "  &8» &e/loot region assign confirm");
                }
                if (result.getPlayerCount() > 0) {
                    MessageUtil.sendRaw(player, "");
                    MessageUtil.sendRaw(player, "&cOpción administrativa PLAYER → MAP:");
                    MessageUtil.sendRaw(player, "&7Para convertir los &e" + result.getPlayerCount() + " &7PLAYER a MAP (conservando inventarios):");
                    MessageUtil.sendRaw(player, "  &8» &e/loot region assign confirm-player");
                }
            }
            MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return;
        }

        // Modo Confirmación Real (registra únicamente newCandidates)
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
        MessageUtil.sendRaw(player, "&7Loot Table: &cSin Loot configurado &7(next_refill: NULL)");
        MessageUtil.sendRaw(player, "&7Usa &e/loot assign &7para asignarles tablas de loot.");
        MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        // Limpiar candidatos asignados
        regionManager.setLastScanResult(player.getUniqueId(), null);
    }

    public void handleAssignPoolCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&cEste comando solo puede ser ejecutado por un jugador.");
            return;
        }

        if (args.length < 3) {
            MessageUtil.sendMessage(player, "&cUso: &e/loot region assign-pool <pool> [preview|confirm]");
            return;
        }

        String poolId = args[2].toLowerCase();
        if (!plugin.getLootPoolManager().poolExists(poolId)) {
            MessageUtil.sendMessage(player, "&cEl Loot Pool '&e" + poolId + "&c' no existe. Pools disponibles: &f"
                    + String.join(", ", plugin.getLootPoolManager().getPoolIds()));
            return;
        }

        RegionScanResult result = regionManager.getLastScanResult(player.getUniqueId());
        if (result == null) {
            MessageUtil.sendMessage(player, "&cNo tienes ningún escaneo regional reciente.");
            MessageUtil.sendMessage(player, "&7Primero selecciona la región con la varita (&e/loot wand&7) y ejecuta &e/loot region scan&7.");
            return;
        }

        RegionSelection sel = result.getSelection();
        List<LootContainer> targetContainers = new ArrayList<>(result.getNewCandidates());

        for (LootContainer c : containerManager.getAllContainers()) {
            if (c.getWorld().equalsIgnoreCase(sel.getWorld().getName())
                    && c.getSource() == ContainerSource.MAP
                    && c.getStatus() == ContainerStatus.ACTIVE
                    && c.isManaged()
                    && sel.contains(c.getX(), c.getY(), c.getZ())) {
                if (!targetContainers.contains(c)) {
                    targetContainers.add(c);
                }
            }
        }

        boolean confirm = args.length >= 4 && args[3].equalsIgnoreCase("confirm");
        if (!confirm) {
            // Modo Preview
            MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendRaw(player, "&6&lLootRefill » Vista Previa Asignación de Pool Regional");
            MessageUtil.sendRaw(player, "&7Mundo: &e" + sel.getWorld().getName());
            MessageUtil.sendRaw(player, "&7Loot Pool objetivo: &e" + poolId.toUpperCase());
            MessageUtil.sendRaw(player, "&7Total contenedores MAP elegibles en región: &a" + targetContainers.size());
            MessageUtil.sendRaw(player, "&7Nuevos candidatos: &f" + result.getNewCandidates().size() + " &8| &7Existentes: &f" + (targetContainers.size() - result.getNewCandidates().size()));
            MessageUtil.sendRaw(player, "");
            MessageUtil.sendRaw(player, "&aNingún contenedor fue modificado todavía. &7(Modo Preview)");
            MessageUtil.sendRaw(player, "&7Para confirmar y asignar el pool ejecuta:");
            MessageUtil.sendRaw(player, "  &8» &e/loot region assign-pool " + poolId + " confirm");
            MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return;
        }

        // Modo Confirmación Real
        if (targetContainers.isEmpty()) {
            MessageUtil.sendMessage(player, "&eNo hay contenedores MAP elegibles en la región para asignar el pool.");
            return;
        }

        long now = System.currentTimeMillis();
        for (LootContainer c : targetContainers) {
            c.setLootPoolId(poolId);
            c.setManaged(true);
            c.setRegistered(true);
            c.setRefillEnabled(true);
            c.setNextRefill(now);
            c.setUpdatedAt(now);
        }

        int savedCount = containerManager.saveContainersBatch(targetContainers);

        MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendRaw(player, "&6&lLootRefill » Asignación de Pool Regional Confirmada");
        MessageUtil.sendRaw(player, "&a¡Se asignó el Loot Pool &e" + poolId.toUpperCase() + " &aa &e" + savedCount + " &acontenedores MAP en la región!");
        MessageUtil.sendRaw(player, "&7Estado: &fACTIVE &8| &7Managed: &ftrue &8| &7Refill Enabled: &atrue");
        MessageUtil.sendRaw(player, "&7Próximo refill: &aProgramado de inmediato (vencido)");
        MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        regionManager.setLastScanResult(player.getUniqueId(), null);
    }
}
