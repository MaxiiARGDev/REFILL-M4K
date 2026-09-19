package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.loot.LootManager;
import com.arcraft.lootrefill.refill.RefillManager;
import com.arcraft.lootrefill.scanner.ScanJob;
import com.arcraft.lootrefill.scanner.WorldScanner;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AdminMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public AdminMenu(LootRefillPlugin plugin) {
        super(54, "✦ LootRefill - Administración");
        this.plugin = plugin;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        LootManager lootManager = plugin.getLootManager();
        ContainerManager containerManager = plugin.getContainerManager();
        RefillManager refillManager = plugin.getRefillManager();
        WorldScanner scanner = plugin.getWorldScanner();

        // 1. Loot Types
        ItemStack lootTypesItem = new ItemBuilder(Material.CHEST)
                .name("§6Tipos de Loot")
                .lore("§7Administrá las tablas de loot", "§7disponibles para los contenedores.", "", "§e» Haz clic para abrir")
                .build();
        setItem(20, lootTypesItem, event -> new LootTypesMenu(plugin, this).open(player));

        // 1.5 Loot Pools (Etapa 4.2 / Container Manager 2.0)
        int poolsCount = plugin.getLootPoolManager() != null ? plugin.getLootPoolManager().getAllPools().size() : 0;
        ItemStack lootPoolsItem = new ItemBuilder(Material.ENDER_CHEST)
                .name("§dLoot Pools")
                .lore(
                        "§7Administrá grupos dinámicos y",
                        "§7selección aleatoria de tablas de loot.",
                        "§7Pools registrados: §f" + poolsCount,
                        "",
                        "§d» Haz clic para abrir"
                )
                .build();
        setItem(21, lootPoolsItem, event -> new LootPoolsMenu(plugin, this).open(player));

        // 2. Containers
        ItemStack containersItem = new ItemBuilder(Material.BARREL)
                .name("§eContenedores")
                .lore("§7Administrá los cofres, barriles y", "§7hornos registrados en el sistema.", "", "§e» Haz clic para abrir")
                .build();
        setItem(22, containersItem, event -> new ContainerMenu(plugin, this).open(player));

        // 3. Refill
        ItemStack refillItem = new ItemBuilder(Material.CLOCK)
                .name("§bSistema de Refill")
                .lore("§7Configurá y ejecutá refills.", "§7Administrá las colas de refill.", "", "§e» Haz clic para abrir")
                .build();
        setItem(24, refillItem, event -> new RefillMenu(plugin, this).open(player));

        // 4. Scanner del Mundo
        ScanJob activeJob = scanner.getActiveJob();
        String scanStatus = activeJob != null ? activeJob.getStatus().name() : "IDLE";
        ItemStack scannerItem = new ItemBuilder(Material.COMPASS)
                .name("§aScanner del Mundo")
                .lore(
                        "§7Escanear y registrar contenedores",
                        "§7de los mundos de forma ultra-ligera.",
                        "§7Estado actual: §e" + scanStatus,
                        "",
                        "§a» Haz clic para abrir el panel del Scanner"
                )
                .build();
        setItem(28, scannerItem, event -> new ScannerMenu(plugin, this).open(player));

        // 5. Populate / Generación de Loot (Etapa 3)
        boolean isPopulating = plugin.getPopulateManager() != null && plugin.getPopulateManager().isPopulating();
        ItemStack populateItem = new ItemBuilder(Material.NETHER_STAR)
                .name("§6Populate / Generar Loot")
                .lore(
                        "§7Poblar contenedores MAP de forma segura",
                        "§7y progresiva con Loot ponderado.",
                        "§7Estado: " + (isPopulating ? "§a§lEN PROCESO" : "§7En espera"),
                        "",
                        "§6» Haz clic para abrir el panel de Populate"
                )
                .build();
        setItem(30, populateItem, event -> new PopulateMenu(plugin, this).open(player));

        // 6. Statistics (Separación estricta MAP vs PLAYER requerida en Etapa 2.1)
        int mapChests = containerManager.getCountBySourceAndType(ContainerSource.MAP, ContainerType.CHEST)
                + containerManager.getCountBySourceAndType(ContainerSource.MAP, ContainerType.TRAPPED_CHEST);
        int mapBarrels = containerManager.getCountBySourceAndType(ContainerSource.MAP, ContainerType.BARREL);
        int mapFurnaces = containerManager.getCountBySourceAndType(ContainerSource.MAP, ContainerType.FURNACE)
                + containerManager.getCountBySourceAndType(ContainerSource.MAP, ContainerType.BLAST_FURNACE)
                + containerManager.getCountBySourceAndType(ContainerSource.MAP, ContainerType.SMOKER);
        int mapDispensers = containerManager.getCountBySourceAndType(ContainerSource.MAP, ContainerType.DISPENSER)
                + containerManager.getCountBySourceAndType(ContainerSource.MAP, ContainerType.DROPPER);

        int playerChests = containerManager.getCountBySourceAndType(ContainerSource.PLAYER, ContainerType.CHEST)
                + containerManager.getCountBySourceAndType(ContainerSource.PLAYER, ContainerType.TRAPPED_CHEST);
        int playerBarrels = containerManager.getCountBySourceAndType(ContainerSource.PLAYER, ContainerType.BARREL);
        int playerFurnaces = containerManager.getCountBySourceAndType(ContainerSource.PLAYER, ContainerType.FURNACE)
                + containerManager.getCountBySourceAndType(ContainerSource.PLAYER, ContainerType.BLAST_FURNACE)
                + containerManager.getCountBySourceAndType(ContainerSource.PLAYER, ContainerType.SMOKER);

        int mapTotal = containerManager.getMapContainerCount();
        int playerTotal = containerManager.getPlayerContainerCount();
        int brokenTotal = containerManager.getBrokenContainerCount();
        int administrables = containerManager.getAdministrableCount();
        int pending = refillManager.getPendingContainers().size();

        ItemStack statsItem = new ItemBuilder(Material.BOOK)
                .name("§dEstadísticas del Servidor")
                .lore(
                        "§aContenedores MAP: §f" + mapTotal,
                        "  §8• §7Cofres: §f" + mapChests + " §8| §7Barriles: §f" + mapBarrels,
                        "  §8• §7Hornos: §f" + mapFurnaces + " §8| §7Dispensers: §f" + mapDispensers,
                        "§cContenedores PLAYER: §f" + playerTotal,
                        "  §8• §7Cofres: §f" + playerChests + " §8| §7Barriles: §f" + playerBarrels + " §8| §7Hornos: §f" + playerFurnaces,
                        "§6Contenedores BROKEN: §f" + brokenTotal,
                        "§eAdministrables activos: §f" + administrables,
                        "§7Contenedores pendientes refill: §f" + pending,
                        "§7Loot tables: §f" + lootManager.getTables().size()
                )
                .build();
        setItem(32, statsItem);

        // 7. Reload
        ItemStack reloadItem = new ItemBuilder(Material.REDSTONE)
                .name("§cRecargar configuración")
                .lore("§7Recarga config.yml y re-sincroniza", "§7las tablas y datos en memoria.", "", "§c» Haz clic para recargar")
                .build();
        setItem(34, reloadItem, event -> {
            plugin.reloadPluginConfig();
            MessageUtil.sendMessage(player, plugin.getConfig().getString("messages.reload-success", "&a¡Configuración recargada!"));
            initialize(player);
        });

        // Botón Cerrar
        ItemStack closeItem = new ItemBuilder(Material.BARRIER)
                .name("§cCerrar menú")
                .lore("§7Haz clic para salir.")
                .build();
        setItem(49, closeItem, event -> player.closeInventory());
    }
}
