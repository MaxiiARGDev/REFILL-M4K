package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.populate.PopulateJob;
import com.arcraft.lootrefill.populate.PopulateManager;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class PopulateMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private World selectedWorld;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    public PopulateMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        this(plugin, previousMenu, null);
    }

    public PopulateMenu(LootRefillPlugin plugin, MenuHolder previousMenu, World selectedWorld) {
        super(54, "✦ LootRefill - Generación de Loot");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
        this.selectedWorld = selectedWorld != null ? selectedWorld : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
    }

    @Override
    public void initialize(Player player) {
        if (selectedWorld == null) {
            selectedWorld = player.getWorld();
        }

        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        PopulateManager populateManager = plugin.getPopulateManager();
        ContainerManager containerManager = plugin.getContainerManager();
        String worldName = selectedWorld.getName();

        // Métricas del mundo seleccionado
        int totalMap = 0;
        int eligible = 0;
        int withoutTable = 0;
        int broken = 0;
        int disabledTypes = 0;

        for (LootContainer c : containerManager.getAllContainers()) {
            if (!c.getWorld().equalsIgnoreCase(worldName) || c.getSource() != ContainerSource.MAP) {
                continue;
            }
            totalMap++;

            if (c.getStatus() == ContainerStatus.BROKEN) {
                broken++;
                continue;
            }

            if (c.getStatus() != ContainerStatus.ACTIVE || !c.isManaged() || !c.isRegistered()) {
                continue;
            }

            boolean typeEnabled = plugin.getConfig().getBoolean("populate.container-types." + c.getContainerType().getConfigKey() + ".enabled", true);
            if (!typeEnabled) {
                disabledTypes++;
                continue;
            }

            if (c.getLootTableId() == null || c.getLootTableId().trim().isEmpty()) {
                withoutTable++;
            } else {
                eligible++;
            }
        }

        // Monitor central (Slot 13)
        boolean isRunning = populateManager.isPopulating();
        PopulateJob activeJob = isRunning ? populateManager.getPopulateQueue().getActiveJob() : null;
        PopulateJob lastJob = plugin.getDatabaseManager().loadLastPopulateJob();

        ItemBuilder monitorBuilder = new ItemBuilder(isRunning ? Material.CLOCK : Material.BEACON)
                .name("§6Monitor de Populate: §e" + worldName);

        if (isRunning && activeJob != null) {
            double percent = activeJob.getProgressPercentage();
            int blocks = (int) (percent / 5.0);
            String bar = "§a" + "█".repeat(Math.max(0, blocks)) + "§8" + "░".repeat(Math.max(0, 20 - blocks));
            long elapsed = (System.currentTimeMillis() - activeJob.getStartedAt()) / 1000;

            monitorBuilder.lore(
                    "§7Estado: §a§lEN PROCESO",
                    "§7Progreso: [" + bar + "§7] §f" + String.format("%.1f", percent) + "%",
                    "§7Procesados: §e" + activeJob.getProcessed() + " §7/ §f" + activeJob.getTotal(),
                    "§7Poblados con éxito: §a" + activeJob.getPopulated(),
                    "§7Saltados: §c" + activeJob.getSkipped(),
                    "§7Tiempo transcurrido: §f" + String.format("%02d:%02d", elapsed / 60, elapsed % 60),
                    "",
                    "§e» Haz clic para refrescar el monitor"
            );
        } else {
            String lastJobInfo = "§8Ninguno registrado";
            if (lastJob != null) {
                lastJobInfo = "§e" + lastJob.getWorld() + " §7(" + DATE_FORMAT.format(new Date(lastJob.getStartedAt())) + ") - §a" + lastJob.getPopulated() + " poblados";
            }

            monitorBuilder.lore(
                    "§7Estado: §7EN ESPERA (IDLE)",
                    "§7Contenedores MAP totales: §f" + totalMap,
                    "§aElegibles para Populate: §e" + eligible,
                    "§cSin tabla asignada: §f" + withoutTable,
                    "§7Deshabilitados/Hornos: §f" + disabledTypes,
                    "§8Rotos (BROKEN): §f" + broken,
                    "",
                    "§7Último Populate: " + lastJobInfo,
                    "",
                    "§e» Haz clic para refrescar estadísticas"
            );
        }

        setItem(13, monitorBuilder.build(), event -> initialize(player));

        // Selector de Mundo (Slot 19)
        ItemStack worldSelector = new ItemBuilder(Material.FILLED_MAP)
                .name("§eMundo Seleccionado: §a" + worldName)
                .lore(
                        "§7Mundos cargados en el servidor.",
                        "",
                        "§e» Haz clic para cambiar de mundo"
                )
                .build();
        setItem(19, worldSelector, event -> {
            List<World> worlds = Bukkit.getWorlds();
            if (!worlds.isEmpty()) {
                int currentIndex = worlds.indexOf(selectedWorld);
                int nextIndex = (currentIndex + 1) % worlds.size();
                selectedWorld = worlds.get(nextIndex);
                initialize(player);
            }
        });

        // Botón [ PREVIEW ] (Slot 21)
        ItemStack previewItem = new ItemBuilder(Material.SPYGLASS)
                .name("§b[ VISTA PREVIA (PREVIEW) ]")
                .lore(
                        "§7Analiza los contenedores del mundo",
                        "§7y genera un reporte detallado en el chat.",
                        "§a¡No modifica ningún contenedor!",
                        "",
                        "§b» Haz clic para ejecutar Preview"
                )
                .build();
        setItem(21, previewItem, event -> {
            populateManager.showPreview(selectedWorld, player);
            player.closeInventory();
        });

        // Botón [ INICIAR POPULATE ] (Slot 23)
        ItemStack startPopulateItem = new ItemBuilder(Material.NETHER_STAR)
                .name("§a[ INICIAR POPULATE ]")
                .lore(
                        "§7Puebla de forma progresiva y segura",
                        "§7todos los contenedores MAP elegibles.",
                        "",
                        "§c⚠ Requiere Shift + Clic para confirmar.",
                        "§7(Usa colas por tick respetando el TPS)"
                )
                .build();
        setItem(23, startPopulateItem, event -> {
            if (!event.isShiftClick()) {
                MessageUtil.sendMessage(player, "&cDebes hacer &eShift + Clic &cpara confirmar el inicio del Populate en &e" + worldName + "&c.");
                return;
            }
            populateManager.startPopulate(selectedWorld, player);
            initialize(player);
        });

        // Botón [ CANCELAR POPULATE ] (Slot 25)
        ItemStack cancelItem = new ItemBuilder(isRunning ? Material.BARRIER : Material.GRAY_DYE)
                .name(isRunning ? "§c[ CANCELAR POPULATE ]" : "§8[ SIN POPULATE ACTIVO ]")
                .lore(
                        isRunning ? "§7Detiene de forma segura el proceso actual." : "§7No hay ningún proceso activo.",
                        "",
                        isRunning ? "§c» Haz clic para cancelar" : ""
                )
                .build();
        setItem(25, cancelItem, event -> {
            if (isRunning) {
                populateManager.cancelPopulate(player);
                initialize(player);
            }
        });

        // Botón Volver (Slot 49)
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("§7« Volver al menú principal")
                .build();
        setItem(49, backItem, event -> {
            if (previousMenu != null) {
                previousMenu.open(player);
            } else {
                new AdminMenu(plugin).open(player);
            }
        });
    }
}
