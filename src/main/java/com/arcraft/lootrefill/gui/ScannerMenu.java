package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.scanner.ScanJob;
import com.arcraft.lootrefill.scanner.WorldScanner;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public class ScannerMenu extends MenuHolder {

    private final LootRefillPlugin plugin;

    public ScannerMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        super(54, "✦ LootRefill - Scanner del Mundo");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        WorldScanner scanner = plugin.getWorldScanner();
        ScanJob job = scanner.getActiveJob();
        if (job == null) {
            job = plugin.getDatabaseManager().loadIncompleteScanJob();
        }

        ScanJob.Status status = job != null ? job.getStatus() : ScanJob.Status.IDLE;
        double percent = job != null ? job.getProgressPercentage() : 0.0;
        int progressBlocks = (int) (percent / 5.0);
        String bar = "§a" + "█".repeat(Math.max(0, progressBlocks)) + "§8" + "░".repeat(Math.max(0, 20 - progressBlocks));

        long elapsed = job != null ? job.getElapsedTimeSeconds() : 0;
        long eta = job != null ? job.getEstimatedTimeRemainingSeconds() : 0;
        String elapsedStr = String.format("%02d:%02d", elapsed / 60, elapsed % 60);
        String etaStr = String.format("%02d:%02d", eta / 60, eta % 60);
        double tps = Bukkit.getTPS()[0];

        Map<ContainerType, Integer> counters = scanner.getFoundTypeCounters();

        // 1. Monitor Central de Progreso
        ItemStack monitorItem = new ItemBuilder(Material.COMPASS)
                .name("§6Monitor de Escaneo: §e" + (job != null ? job.getWorld() : "Ninguno"))
                .lore(
                        "§7Estado: " + getStatusFormatted(status),
                        "§7Progreso: [" + bar + "§7] §f" + String.format("%.1f", percent) + "%",
                        "§7Chunks analizados: §e" + (job != null ? job.getProcessedChunks() : 0) + " §7/ §f" + (job != null ? job.getTotalChunks() : 0),
                        "§7Contenedores encontrados: §a" + (job != null ? job.getFoundContainers() : 0),
                        "  §8• §7Cofres: §f" + (counters.get(ContainerType.CHEST) + counters.get(ContainerType.TRAPPED_CHEST)),
                        "  §8• §7Barriles: §f" + counters.get(ContainerType.BARREL),
                        "  §8• §7Hornos: §f" + (counters.get(ContainerType.FURNACE) + counters.get(ContainerType.BLAST_FURNACE) + counters.get(ContainerType.SMOKER)),
                        "  §8• §7Dispensers/Droppers: §f" + (counters.get(ContainerType.DISPENSER) + counters.get(ContainerType.DROPPER)),
                        "§7Tiempo: §f" + elapsedStr + " §8| §7ETA: §e" + etaStr,
                        "§7TPS Servidor: §a" + String.format("%.2f", tps),
                        "",
                        "§7Haz clic para refrescar estadísticas."
                )
                .build();
        setItem(13, monitorItem, event -> initialize(player));

        // 2. [ INICIAR SCAN ]
        ItemStack startItem = new ItemBuilder(Material.EMERALD)
                .name("§a[ INICIAR ESCANEO ]")
                .lore(
                        "§7Inicia el escaneo progresivo",
                        "§7del mundo actual.",
                        "",
                        "§a» Haz clic para comenzar"
                )
                .build();
        setItem(29, startItem, event -> {
            if (scanner.isScanning()) {
                MessageUtil.sendMessage(player, "&cYa hay un escaneo en curso.");
                return;
            }
            scanner.startScan(player.getWorld(), player);
            initialize(player);
        });

        // 3. [ PAUSAR ]
        ItemStack pauseItem = new ItemBuilder(Material.ORANGE_DYE)
                .name("§6[ PAUSAR ]")
                .lore("§7Pausa el escaneo actual y guarda", "§7el progreso en la base de datos.", "", "§6» Haz clic para pausar")
                .build();
        setItem(30, pauseItem, event -> {
            scanner.pauseScan(player);
            initialize(player);
        });

        // 4. [ REANUDAR ]
        ItemStack resumeItem = new ItemBuilder(Material.LIME_DYE)
                .name("§e[ REANUDAR ]")
                .lore("§7Reanuda un escaneo pausado o", "§7incompleto desde el último chunk.", "", "§e» Haz clic para reanudar")
                .build();
        setItem(32, resumeItem, event -> {
            scanner.resumeScan(player);
            initialize(player);
        });

        // 5. [ CANCELAR ]
        ItemStack cancelItem = new ItemBuilder(Material.RED_DYE)
                .name("§c[ CANCELAR ]")
                .lore("§7Detiene y cancela el escaneo,", "§7conservando los datos encontrados.", "", "§c» Haz clic para cancelar")
                .build();
        setItem(33, cancelItem, event -> {
            scanner.cancelScan(player);
            initialize(player);
        });

        // Volver
        setBackButton(49, previousMenu);
    }

    private String getStatusFormatted(ScanJob.Status status) {
        return switch (status) {
            case IDLE -> "§7Inactivo";
            case RUNNING -> "§aEscaneando...";
            case PAUSED -> "§6Pausado";
            case COMPLETED -> "§bCompletado";
            case CANCELLED -> "§cCancelado";
            case ERROR -> "§4Error";
        };
    }
}
