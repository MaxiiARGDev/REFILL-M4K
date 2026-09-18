package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.refill.RefillManager;
import com.arcraft.lootrefill.refill.RefillStats;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class RefillMenu extends MenuHolder {

    private final LootRefillPlugin plugin;

    public RefillMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        super(54, "✦ LootRefill - Sistema de Refill");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        RefillManager refill = plugin.getRefillManager();
        RefillStats stats = refill.getStats();
        long now = System.currentTimeMillis();
        int pendingDue = plugin.getContainerManager().getDueContainersCount(now);
        boolean isActive = refill.isAutoRefillActive();
        boolean isPaused = refill.isPaused();

        // 1. Monitor General de Auto Refill (Slot 4)
        String stateText = !refill.isEnabled() ? "§cDESACTIVADO (Config)" : (isPaused ? "§6PAUSADO" : "§aACTIVADO");
        Material monitorMat = isActive ? Material.BEACON : (isPaused ? Material.CLOCK : Material.REDSTONE_BLOCK);

        ItemStack monitorItem = new ItemBuilder(monitorMat)
                .name("§6Monitor de Auto Refill: " + stateText)
                .lore(
                        "§7Contenedores MAP vencidos: §e" + pendingDue,
                        "§7En cola de procesamiento: §f" + refill.getRefillQueue().getTotalQueueSize(),
                        "§7Procesados último ciclo: §a" + stats.getLastCycleProcessedCount() + " §7(Exitosos: §a" + stats.getLastCycleRefilledCount() + "§7)",
                        "§7TPS Servidor: §a" + String.format("%.2f", Bukkit.getTPS()[0]),
                        "",
                        "§7Estadísticas acumuladas:",
                        "  §8• §aExitosos: §f" + stats.getSuccessfulRefills(),
                        "  §8• §cNo vacíos: §f" + stats.getSkippedNotEmpty(),
                        "  §8• §cJugador cerca: §f" + stats.getSkippedPlayerNearby(),
                        "  §8• §eChunk no cargado: §f" + stats.getSkippedChunkNotLoaded(),
                        "  §8• §8Deshabilitados/Hornos: §f" + (stats.getSkippedDisabled() + stats.getSkippedBurning()),
                        "  §8• §bChunks cargados/descargados: §f" + stats.getChunksLoadedByRefill() + " / " + stats.getChunksUnloadedByRefill(),
                        "",
                        "§e» Haz clic para alternar Pausa / Reanudar"
                )
                .build();
        setItem(4, monitorItem, event -> {
            if (isPaused) {
                refill.resume();
                MessageUtil.sendMessage(player, "&aAuto Refill reanudado.");
            } else {
                refill.pause();
                MessageUtil.sendMessage(player, "&6Auto Refill pausado.");
            }
            initialize(player);
        });

        // 2. Tipos de contenedores y sus intervalos
        ContainerType[] types = {
                ContainerType.CHEST,
                ContainerType.TRAPPED_CHEST,
                ContainerType.BARREL,
                ContainerType.FURNACE,
                ContainerType.BLAST_FURNACE,
                ContainerType.SMOKER,
                ContainerType.DISPENSER,
                ContainerType.DROPPER
        };

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 22};

        for (int i = 0; i < types.length; i++) {
            ContainerType type = types[i];
            int slot = slots[i];

            boolean isEnabled = refill.isTypeRefillEnabled(type);
            int interval = refill.getIntervalForType(type);
            int pending = refill.getRefillQueue().getQueueSize(type);

            ItemStack item = new ItemBuilder(type.getMaterial())
                    .name("§6" + type.getDisplayName())
                    .lore(
                            "§7Estado Refill: " + (isEnabled ? "§aACTIVADO" : "§cDESACTIVADO"),
                            "§7Intervalo: §e" + (interval / 60) + " minutos §8(" + interval + "s)",
                            "§7En cola actual: §f" + pending,
                            "",
                            "§aClick Izquierdo: §7Alternar estado",
                            "§bClick Derecho: §7+5 min intervalo",
                            "§cShift + Click Der: §7-5 min intervalo",
                            "§eShift + Click Izq: §7Ingresar por chat"
                    )
                    .build();

            setItem(slot, item, event -> {
                if (event.isShiftClick()) {
                    if (event.isRightClick()) {
                        refill.setIntervalForType(type, Math.max(10, interval - 300));
                        initialize(player);
                    } else {
                        plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                                "&aIngresa el nuevo intervalo para " + type.getDisplayName() + " (en segundos):",
                                input -> {
                                    try {
                                        refill.setIntervalForType(type, Math.max(10, Integer.parseInt(input.trim())));
                                    } catch (NumberFormatException ignored) {}
                                    open(player);
                                }
                        );
                    }
                } else if (event.isLeftClick()) {
                    refill.setTypeRefillEnabled(type, !isEnabled);
                    initialize(player);
                } else if (event.isRightClick()) {
                    refill.setIntervalForType(type, interval + 300);
                    initialize(player);
                }
            });
        }

        // 3. Acciones inferiores
        // Botón [ EJECUTAR CICLO MANUAL ] (Slot 37)
        ItemStack runCycleItem = new ItemBuilder(Material.EMERALD)
                .name("§a[ EJECUTAR CICLO DE REFILL ]")
                .lore(
                        "§7Inicia el procesamiento escalonado",
                        "§7de los contenedores vencidos.",
                        "§7(Respeta el límite de 2ms por tick)",
                        "",
                        "§a» Haz clic para ejecutar"
                )
                .build();
        setItem(37, runCycleItem, event -> {
            refill.triggerManualCycle(player);
            initialize(player);
        });

        // Botón [ PAUSAR / REANUDAR ] (Slot 39)
        ItemStack pauseResumeItem = new ItemBuilder(isPaused ? Material.LIME_DYE : Material.ORANGE_DYE)
                .name(isPaused ? "§a[ REANUDAR AUTO REFILL ]" : "§6[ PAUSAR AUTO REFILL ]")
                .lore(
                        isPaused ? "§7Reanuda el procesamiento automático." : "§7Pausa temporalmente el Auto Refill.",
                        "",
                        "§e» Haz clic para cambiar estado"
                )
                .build();
        setItem(39, pauseResumeItem, event -> {
            if (isPaused) {
                refill.resume();
                MessageUtil.sendMessage(player, "&aAuto Refill reanudado.");
            } else {
                refill.pause();
                MessageUtil.sendMessage(player, "&6Auto Refill pausado.");
            }
            initialize(player);
        });

        // Botón [ COLA DE REFILL ] (Slot 41)
        ItemStack queueItem = new ItemBuilder(Material.HOPPER)
                .name("§e[ VER COLA DE REFILL ]")
                .lore("§7Muestra los contenedores en cola y", "§7permite forzar su recarga individual.", "", "§e» Haz clic para ver detalles")
                .build();
        setItem(41, queueItem, event -> new RefillQueueMenu(plugin, this).open(player));

        // Botón [ CONDICIONES Y SEGURIDAD ] (Slot 43)
        ItemStack configItem = new ItemBuilder(Material.REPEATER)
                .name("§b[ CONDICIONES Y SEGURIDAD ]")
                .lore("§7Configurar radio de jugadores,", "§7requerimiento de cofre vacío, etc.", "", "§b» Haz clic para configurar")
                .build();
        setItem(43, configItem, event -> new RefillConfigMenu(plugin, this).open(player));

        // Volver (Slot 49)
        setBackButton(49, previousMenu);
    }
}
