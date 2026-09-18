package com.arcraft.lootrefill.command;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.ContainerWorldStats;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.gui.AdminMenu;
import com.arcraft.lootrefill.gui.ScannerMenu;
import com.arcraft.lootrefill.refill.RefillManager;
import com.arcraft.lootrefill.refill.RefillStats;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class LootCommand implements CommandExecutor, TabCompleter {

    private final LootRefillPlugin plugin;

    public LootCommand(LootRefillPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lootrefill.admin")) {
            MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.no-permission", "&cNo tienes permisos para ejecutar este comando."));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("admin")) {
            if (!(sender instanceof Player player)) {
                MessageUtil.sendMessage(sender, "&cEste comando solo puede ser ejecutado por jugadores.");
                return true;
            }
            new AdminMenu(plugin).open(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                plugin.reloadPluginConfig();
                MessageUtil.sendMessage(sender, plugin.getConfig().getString("messages.reload-success", "&a¡Configuración y datos recargados correctamente!"));
            }
            case "scan" -> handleScanCommand(sender, args);
            case "populate" -> handlePopulateCommand(sender, args);
            case "assign" -> handleAssignCommand(sender, args);
            case "assign-pool" -> handleAssignPoolCommand(sender, args);
            case "refill" -> handleRefillCommand(sender, args);
            case "register-pool", "set-pool" -> handleRegisterPoolCommand(sender, args);
            case "register", "set" -> {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, "&cEste comando solo puede ser ejecutado por un jugador.");
                    return true;
                }
                if (args.length < 2) {
                    MessageUtil.sendMessage(player, "&cUso: /loot register <tabla_id>");
                    return true;
                }
                String tableId = args[1].toLowerCase();
                if (!plugin.getLootManager().tableExists(tableId)) {
                    MessageUtil.sendMessage(player, "&cLa tabla &e" + tableId + " &cno existe. Tablas disponibles: &f" + String.join(", ", plugin.getLootManager().getTableIds()));
                    return true;
                }
                Block targetBlock = player.getTargetBlockExact(5);
                if (targetBlock == null || !plugin.getContainerManager().isAllowedContainerBlock(targetBlock)) {
                    MessageUtil.sendMessage(player, "&cDebes estar mirando un contenedor válido habilitado en la configuración.");
                    return true;
                }
                var container = plugin.getContainerRegistry().registerMapBlock(targetBlock, tableId);
                if (container != null) {
                    MessageUtil.sendMessage(player, "&aContenedor registrado como MAP.");
                    MessageUtil.sendMessage(player, "&7Tipo: &f" + container.getContainerType().name() + " &8| &7Loot Table: &e" + tableId.toUpperCase());
                } else {
                    MessageUtil.sendMessage(player, "&cNo se pudo registrar el contenedor.");
                }
            }
            case "wand" -> {
                if (!(sender instanceof Player player)) {
                    MessageUtil.sendMessage(sender, "&cEste comando solo puede ser ejecutado por un jugador.");
                    return true;
                }
                plugin.getRegionManager().giveWand(player);
            }
            case "region" -> handleRegionCommand(sender, args);
            case "container" -> handleContainerDebugCommand(sender, args);
            case "containers" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("debug")) {
                    handleContainerDebugCommand(sender, args);
                } else {
                    handleContainersCommand(sender, args);
                }
            }
            case "help" -> sendHelp(sender);
            default -> {
                MessageUtil.sendMessage(sender, "&cSubcomando desconocido. Escribe &e/loot help &cpara ver la lista de comandos.");
            }
        }

        return true;
    }

    private void handlePopulateCommand(CommandSender sender, String[] args) {
        if (args.length < 2) {
            MessageUtil.sendMessage(sender, "&cUso: &e/loot populate <mundo> [preview|confirm]");
            MessageUtil.sendMessage(sender, "&7O usa: &e/loot populate cancel &7para detener una tarea en curso.");
            return;
        }

        if (args[1].equalsIgnoreCase("cancel")) {
            plugin.getPopulateManager().cancelPopulate(sender);
            return;
        }

        World targetWorld = Bukkit.getWorld(args[1]);
        if (targetWorld == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo '&e" + args[1] + "&c' no existe o no está cargado.");
            return;
        }

        if (args.length == 2) {
            MessageUtil.sendMessage(sender, "&6Para evitar modificaciones accidentales, confirma la operación:");
            MessageUtil.sendMessage(sender, "&7• Ver reporte sin cambios: &e/loot populate " + targetWorld.getName() + " preview");
            MessageUtil.sendMessage(sender, "&7• Confirmar y poblar: &e/loot populate " + targetWorld.getName() + " confirm");
            return;
        }

        String mode = args[2].toLowerCase();
        switch (mode) {
            case "preview" -> plugin.getPopulateManager().showPreview(targetWorld, sender);
            case "confirm" -> plugin.getPopulateManager().startPopulate(targetWorld, sender);
            default -> {
                MessageUtil.sendMessage(sender, "&cOpción desconocida '&e" + args[2] + "&c'. Usa &e[preview|confirm]&c.");
            }
        }
    }

    private void handleAssignCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "&cUso: &e/loot assign <mundo> <tipo_contenedor> <tabla_loot> [preview|confirm|--force]");
            MessageUtil.sendMessage(sender, "&7Ejemplo: &e/loot assign lobby chest common preview");
            return;
        }

        World targetWorld = Bukkit.getWorld(args[1]);
        if (targetWorld == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo '&e" + args[1] + "&c' no existe o no está cargado.");
            return;
        }

        ContainerType type;
        try {
            type = ContainerType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            MessageUtil.sendMessage(sender, "&cTipo de contenedor inválido '&e" + args[2] + "&c'.");
            MessageUtil.sendMessage(sender, "&7Tipos disponibles: &f" + Arrays.toString(ContainerType.values()));
            return;
        }

        String tableId = args[3].toLowerCase();
        if (!plugin.getLootManager().tableExists(tableId)) {
            MessageUtil.sendMessage(sender, "&cLa tabla de loot '&e" + tableId + "&c' no existe.");
            MessageUtil.sendMessage(sender, "&7Tablas disponibles: &f" + String.join(", ", plugin.getLootManager().getTableIds()));
            return;
        }

        boolean force = false;
        boolean confirm = false;
        boolean preview = false;

        for (int i = 4; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if (arg.equals("--force") || arg.equals("force")) {
                force = true;
            } else if (arg.equals("confirm")) {
                confirm = true;
            } else if (arg.equals("preview")) {
                preview = true;
            }
        }

        if (!confirm && !preview) {
            MessageUtil.sendMessage(sender, "&6Debes especificar si deseas ver una vista previa o confirmar:");
            MessageUtil.sendMessage(sender, "&7• Ver preview: &e/loot assign " + targetWorld.getName() + " " + type.name().toLowerCase() + " " + tableId + (force ? " --force" : "") + " preview");
            MessageUtil.sendMessage(sender, "&7• Confirmar: &e/loot assign " + targetWorld.getName() + " " + type.name().toLowerCase() + " " + tableId + (force ? " --force" : "") + " confirm");
            return;
        }

        plugin.getPopulateManager().assignLootTable(targetWorld, type, tableId, force, preview, sender);
    }

    private void handleAssignPoolCommand(CommandSender sender, String[] args) {
        if (args.length < 4) {
            MessageUtil.sendMessage(sender, "&cUso: &e/loot assign-pool <mundo> <tipo> <pool> [preview|confirm|--force]");
            return;
        }

        World targetWorld = Bukkit.getWorld(args[1]);
        if (targetWorld == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo '&e" + args[1] + "&c' no existe o no está cargado.");
            return;
        }

        ContainerType type;
        try {
            type = ContainerType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            MessageUtil.sendMessage(sender, "&cTipo de contenedor inválido '&e" + args[2] + "&c'.");
            MessageUtil.sendMessage(sender, "&7Tipos disponibles: &f" + Arrays.toString(ContainerType.values()));
            return;
        }

        String poolId = args[3].toLowerCase();
        if (!plugin.getLootPoolManager().poolExists(poolId)) {
            MessageUtil.sendMessage(sender, "&cEl Loot Pool '&e" + poolId + "&c' no existe.");
            MessageUtil.sendMessage(sender, "&7Pools disponibles: &f" + String.join(", ", plugin.getLootPoolManager().getPoolIds()));
            return;
        }

        boolean force = false;
        boolean confirm = false;
        boolean preview = false;

        for (int i = 4; i < args.length; i++) {
            String arg = args[i].toLowerCase();
            if (arg.equals("--force") || arg.equals("force")) {
                force = true;
            } else if (arg.equals("confirm")) {
                confirm = true;
            } else if (arg.equals("preview")) {
                preview = true;
            }
        }

        if (!confirm && !preview) {
            MessageUtil.sendMessage(sender, "&6Debes especificar si deseas ver una vista previa o confirmar:");
            MessageUtil.sendMessage(sender, "&7• Ver preview: &e/loot assign-pool " + targetWorld.getName() + " " + type.name().toLowerCase() + " " + poolId + (force ? " --force" : "") + " preview");
            MessageUtil.sendMessage(sender, "&7• Confirmar: &e/loot assign-pool " + targetWorld.getName() + " " + type.name().toLowerCase() + " " + poolId + (force ? " --force" : "") + " confirm");
            return;
        }

        plugin.getPopulateManager().assignLootPool(targetWorld, type, poolId, force, preview, sender);
    }

    private void handleRegisterPoolCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&cEste comando solo puede ser ejecutado por un jugador.");
            return;
        }
        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUso: &e/loot register-pool <pool_id>");
            return;
        }
        String poolId = args[1].toLowerCase();
        if (!plugin.getLootPoolManager().poolExists(poolId)) {
            MessageUtil.sendMessage(player, "&cEl Loot Pool &e" + poolId + " &cno existe. Pools disponibles: &f" + String.join(", ", plugin.getLootPoolManager().getPoolIds()));
            return;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || !plugin.getContainerManager().isAllowedContainerBlock(targetBlock)) {
            MessageUtil.sendMessage(player, "&cDebes estar mirando un contenedor válido habilitado en la configuración.");
            return;
        }

        ContainerType type = ContainerType.fromBlock(targetBlock);
        LootContainer container = plugin.getContainerManager().getContainer(targetBlock.getLocation());
        long now = System.currentTimeMillis();
        if (container == null) {
            container = LootContainer.fromLocationWithPool(targetBlock.getLocation(), type, poolId);
            container.setNextRefill(now);
            plugin.getContainerManager().registerContainer(container, true);
        } else {
            container.setContainerType(type);
            container.setLootPoolId(poolId);
            container.setSource(ContainerSource.MAP);
            container.setStatus(ContainerStatus.ACTIVE);
            container.setManaged(true);
            container.setRegistered(true);
            container.setRefillEnabled(true);
            container.setNextRefill(now);
            container.setUpdatedAt(now);
            plugin.getContainerManager().saveContainer(container);
        }

        MessageUtil.sendMessage(player, "&aContenedor registrado como MAP con Loot Pool.");
        MessageUtil.sendMessage(player, "&7Tipo: &f" + container.getContainerType().name() + " &8| &7Loot Pool: &e" + poolId.toUpperCase());
    }

    private void handleScanCommand(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (sender instanceof Player player) {
                new ScannerMenu(plugin, null).open(player);
            } else {
                plugin.getWorldScanner().showStatus(sender);
            }
            return;
        }

        String action = args[1].toLowerCase();

        switch (action) {
            case "status" -> plugin.getWorldScanner().showStatus(sender);
            case "pause" -> plugin.getWorldScanner().pauseScan(sender);
            case "resume" -> plugin.getWorldScanner().resumeScan(sender);
            case "cancel" -> plugin.getWorldScanner().cancelScan(sender);
            default -> {
                // Se asume que el argumento es el nombre del mundo a escanear
                World targetWorld = Bukkit.getWorld(args[1]);
                if (targetWorld == null) {
                    MessageUtil.sendMessage(sender, "&cEl mundo '&e" + args[1] + "&c' no existe o no está cargado.");
                    return;
                }
                plugin.getWorldScanner().startScan(targetWorld, sender);
            }
        }
    }

    private void handleRefillCommand(CommandSender sender, String[] args) {
        RefillManager refill = plugin.getRefillManager();

        if (args.length == 1 || args[1].equalsIgnoreCase("status")) {
            long now = System.currentTimeMillis();
            int pendingDue = plugin.getContainerManager().getDueContainersCount(now);
            RefillStats stats = refill.getStats();

            String statusStr = !refill.isEnabled() ? "§cDESACTIVADO EN CONFIG" : (refill.isPaused() ? "§6PAUSADO" : "§aACTIVADO");
            double tps = Bukkit.getTPS()[0];

            MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendRaw(sender, "&6&lLootRefill » Estado del Auto Refill");
            MessageUtil.sendRaw(sender, "&7Auto Refill: " + statusStr);
            MessageUtil.sendRaw(sender, "&7Contenedores MAP vencidos esperando: &e" + pendingDue);
            MessageUtil.sendRaw(sender, "&7En cola por procesar: &f" + refill.getRefillQueue().getTotalQueueSize());
            MessageUtil.sendRaw(sender, "&7Procesados en último ciclo: &a" + stats.getLastCycleProcessedCount() + " &7(Exitosos: &a" + stats.getLastCycleRefilledCount() + "&7)");
            MessageUtil.sendRaw(sender, "&7TPS Servidor: §a" + String.format("%.2f", tps));
            MessageUtil.sendRaw(sender, "");
            MessageUtil.sendRaw(sender, "&7Estadísticas acumuladas:");
            MessageUtil.sendRaw(sender, "  &8• &aExitosos: &f" + stats.getSuccessfulRefills());
            MessageUtil.sendRaw(sender, "  &8• &cNo vacíos: &f" + stats.getSkippedNotEmpty());
            MessageUtil.sendRaw(sender, "  &8• &cJugador cerca: &f" + stats.getSkippedPlayerNearby());
            MessageUtil.sendRaw(sender, "  &8• &eChunk no cargado: &f" + stats.getSkippedChunkNotLoaded());
            MessageUtil.sendRaw(sender, "  &8• &8Deshabilitados/Hornos: &f" + (stats.getSkippedDisabled() + stats.getSkippedBurning()));
            MessageUtil.sendRaw(sender, "  &8• &bChunks cargados/descargados: &f" + stats.getChunksLoadedByRefill() + " / " + stats.getChunksUnloadedByRefill());
            MessageUtil.sendRaw(sender, "");
            MessageUtil.sendRaw(sender, "&7Comandos: &e/loot refill pause &8| &e/loot refill resume &8| &e/loot refill run");
            MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "pause" -> {
                if (refill.isPaused()) {
                    MessageUtil.sendMessage(sender, "&eEl Auto Refill ya está pausado.");
                    return;
                }
                refill.pause();
                MessageUtil.sendMessage(sender, "&6Auto Refill pausado temporalmente. Usa &e/loot refill resume &6para reanudar.");
            }
            case "resume" -> {
                if (!refill.isPaused()) {
                    MessageUtil.sendMessage(sender, "&aEl Auto Refill ya se encuentra activo.");
                    return;
                }
                refill.resume();
                MessageUtil.sendMessage(sender, "&aAuto Refill reanudado correctamente.");
            }
            case "run" -> {
                refill.triggerManualCycle(sender);
            }
            case "debug" -> {
                handleRefillDebugCommand(sender);
            }
            default -> {
                MessageUtil.sendMessage(sender, "&cAcción desconocida '&e" + args[1] + "&c'. Usa &e[status|pause|resume|run|debug]&c.");
            }
        }
    }

    private void handleRegionCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "&cEste comando solo puede ser ejecutado por un jugador.");
            return;
        }

        if (args.length < 2) {
            MessageUtil.sendMessage(player, "&cUso: &e/loot region <info|clear|scan|assign>");
            return;
        }

        String action = args[1].toLowerCase();
        switch (action) {
            case "info" -> {
                var selection = plugin.getRegionManager().getSelectionOrNull(player.getUniqueId());
                if (selection == null || (!selection.isComplete() && selection.getPointA() == null && selection.getPointB() == null)) {
                    MessageUtil.sendMessage(player, "&eNo tienes ningún punto seleccionado actualmente.");
                    MessageUtil.sendMessage(player, "&7Usa &e/loot wand &7para obtener la varita y seleccionar los Puntos A y B.");
                    return;
                }

                MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                MessageUtil.sendRaw(player, "&6&lLootRefill » Información de Región Seleccionada");
                if (selection.getPointA() != null) {
                    var a = selection.getPointA();
                    MessageUtil.sendRaw(player, "&7Punto A: &a" + a.getBlockX() + ", " + a.getBlockY() + ", " + a.getBlockZ() + " &8(" + a.getWorld().getName() + ")");
                } else {
                    MessageUtil.sendRaw(player, "&7Punto A: &cNo establecido");
                }

                if (selection.getPointB() != null) {
                    var b = selection.getPointB();
                    MessageUtil.sendRaw(player, "&7Punto B: &a" + b.getBlockX() + ", " + b.getBlockY() + ", " + b.getBlockZ() + " &8(" + b.getWorld().getName() + ")");
                } else {
                    MessageUtil.sendRaw(player, "&7Punto B: &cNo establecido");
                }

                if (selection.isComplete()) {
                    MessageUtil.sendRaw(player, "");
                    MessageUtil.sendRaw(player, "&7Mundo: &e" + selection.getWorld().getName());
                    MessageUtil.sendRaw(player, "&7Límites: &f(" + selection.getMinX() + ", " + selection.getMinY() + ", " + selection.getMinZ() + ") &7a &f(" + selection.getMaxX() + ", " + selection.getMaxY() + ", " + selection.getMaxZ() + ")");
                    MessageUtil.sendRaw(player, "&7Dimensiones: &e" + selection.getWidthX() + " &7x &e" + selection.getHeightY() + " &7x &e" + selection.getLengthZ());
                    MessageUtil.sendRaw(player, "&7Volumen: &f" + selection.getVolume() + " &7bloques");
                    MessageUtil.sendRaw(player, "&7Chunks intersecantes: &b" + selection.getTotalChunks());
                    MessageUtil.sendRaw(player, "");
                    MessageUtil.sendRaw(player, "&7Usa &e/loot region scan &7para escanear contenedores en la región.");
                } else {
                    MessageUtil.sendRaw(player, "&cSelección incompleta. Debes definir ambos puntos con la varita.");
                }
                MessageUtil.sendRaw(player, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            }
            case "clear" -> {
                plugin.getRegionManager().clearSelection(player.getUniqueId());
                MessageUtil.sendMessage(player, "&aSelección de región limpiada correctamente.");
            }
            case "scan" -> {
                plugin.getRegionScanner().startScan(player);
            }
            case "assign" -> {
                plugin.getRegionScanner().handleAssignCommand(player, args);
            }
            case "assign-pool" -> {
                plugin.getRegionScanner().handleAssignPoolCommand(player, args);
            }
            default -> {
                MessageUtil.sendMessage(player, "&cAcción desconocida '&e" + args[1] + "&c'. Usa: &einfo, clear, scan, assign, assign-pool&c.");
            }
        }
    }

    private void handleContainersCommand(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("reset")) {
            MessageUtil.sendMessage(sender, "&cUso: &e/loot containers reset <mundo> [confirm]");
            return;
        }

        String worldName = args[2];
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo &e" + worldName + " &cno existe.");
            return;
        }

        // Obtener desglose exclusivo del mundo
        ContainerWorldStats stats = plugin.getContainerManager().getContainerStatsByWorld(world.getName());

        if (args.length >= 4 && args[3].equalsIgnoreCase("confirm")) {
            int deleted = plugin.getContainerManager().resetContainersByWorld(world.getName());
            plugin.getRefillManager().clearRefillStateForWorld(world.getName());
            plugin.getRegionManager().clearScanResultsForWorld(world.getName());

            MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendRaw(sender, "&6&lLootRefill » Reset de Contenedores");
            MessageUtil.sendRaw(sender, "&aBase de containers del mundo &e" + world.getName() + " &areiniciada correctamente.");
            MessageUtil.sendRaw(sender, "&7Registros eliminados: &f" + deleted + " &8(MAP: " + stats.mapCount() + ", PLAYER: " + stats.playerCount() + ", BROKEN: " + stats.brokenCount() + ")");
            MessageUtil.sendRaw(sender, "&aLootTables, LootEntries y otros mundos permanecen intactos.");
            MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            plugin.getLogger().info("El administrador " + sender.getName() + " ejecutó '/loot containers reset " + world.getName() + " confirm'. Se eliminaron " + deleted + " contenedores de ese mundo.");
            return;
        }

        // Modo Advertencia previa obligatoria
        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendRaw(sender, "&6&lLootRefill » Reset de Contenedores");
        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "&c&l⚠ ADVERTENCIA");
        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "&7Mundo: &e" + world.getName());
        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "&7Se eliminarán:");
        MessageUtil.sendRaw(sender, "  &8• &7MAP: &f" + stats.mapCount());
        MessageUtil.sendRaw(sender, "  &8• &7PLAYER: &f" + stats.playerCount());
        MessageUtil.sendRaw(sender, "  &8• &7BROKEN: &f" + stats.brokenCount());
        MessageUtil.sendRaw(sender, "  &8• &eTotal: &6" + stats.totalCount());
        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "&7Únicamente se eliminarán registros del mundo &e" + world.getName() + "&7.");
        MessageUtil.sendRaw(sender, "&aLootTables y LootEntries NO serán afectados.");
        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "&cPara confirmar:");
        MessageUtil.sendRaw(sender, "  &8» &e/loot containers reset " + world.getName() + " confirm");
        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void handleRefillDebugCommand(CommandSender sender) {
        RefillManager refill = plugin.getRefillManager();
        var queue = refill.getRefillQueue();
        long now = System.currentTimeMillis();
        int dueCount = plugin.getContainerManager().getDueContainersCount(now);
        int totalMem = plugin.getContainerManager().getAllContainers().size();
        int totalDb = 0;
        try (var conn = plugin.getDatabaseManager().getConnection();
             var st = conn.createStatement();
             var rs = st.executeQuery("SELECT COUNT(*) FROM containers")) {
            if (rs.next()) totalDb = rs.getInt(1);
        } catch (Exception ignored) {}

        int batchDelay = plugin.getConfig().getInt("refill.processing.batch-delay-ticks", 1);
        int perTick = plugin.getConfig().getInt("refill.processing.containers-per-tick", 5);
        long maxMs = plugin.getConfig().getLong("refill.processing.max-ms-per-tick", 2);

        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendRaw(sender, "&6&lLootRefill » Auto Refill Scheduler Debug");
        MessageUtil.sendRaw(sender, "&71. Scheduler ejecutándose: " + (queue.isTaskRunning() ? "&aSÍ" : "&cNO")
                + " &8(Task ID: &f" + queue.getTaskId() + "&8, AutoRefillActive: &f" + refill.isAutoRefillActive()
                + "&8, Paused: &f" + refill.isPaused() + "&8)");
        MessageUtil.sendRaw(sender, "&72. Frecuencia de ciclo: &eCada " + batchDelay + " ticks &8(&f" + (batchDelay * 50) + "ms&8)");
        MessageUtil.sendRaw(sender, "&73. Límites: &f" + perTick + " containers/tick &8| &f" + maxMs + "ms max/tick");
        MessageUtil.sendRaw(sender, "&74. Total contenedores: &f" + totalMem + " en memoria &8| &f" + totalDb + " en SQLite");
        MessageUtil.sendRaw(sender, "&75. Contenedores MAP vencidos (isDue): &e" + dueCount);
        MessageUtil.sendRaw(sender, "&76. En cola RefillQueue actualmente: &b" + queue.getTotalQueueSize());

        MessageUtil.sendRaw(sender, "&77. Desglose y consultas de candidatos en DB:");
        for (ContainerType type : com.arcraft.lootrefill.refill.RefillQueue.ROUND_ROBIN_TYPES) {
            boolean enabled = refill.isTypeRefillEnabled(type);
            int inQ = queue.getQueueSize(type);
            List<LootContainer> candidates = plugin.getContainerManager().getContainersDueForRefill(type, now, 25);
            MessageUtil.sendRaw(sender, "  &8• &f" + type.name() + ": &7enCola=&b" + inQ
                    + " &8| &7enDB_Due=&e" + candidates.size()
                    + " &8| &7enabledEnConfig=" + (enabled ? "&atrue" : "&cfalse"));
        }
        MessageUtil.sendRaw(sender, "&78. Chunks en carga simultánea: &f" + queue.getLoadingChunksCount()
                + " &8/ &f" + refill.getMaxChunksLoadedByRefill());
        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void handleContainerDebugCommand(CommandSender sender, String[] args) {
        LootContainer target = null;

        if (args.length >= 3) {
            String targetId = args[2];
            try {
                UUID id = UUID.fromString(targetId);
                target = plugin.getContainerManager().getContainer(id);
            } catch (IllegalArgumentException ignored) {
            }
        } else if (args.length == 2 && !args[1].equalsIgnoreCase("debug")) {
            try {
                UUID id = UUID.fromString(args[1]);
                target = plugin.getContainerManager().getContainer(id);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (target == null && sender instanceof Player player) {
            Block block = player.getTargetBlockExact(5);
            if (block != null && plugin.getContainerManager().isAllowedContainerBlock(block)) {
                target = plugin.getContainerManager().getContainer(block.getLocation());
            }
        }

        if (target == null) {
            MessageUtil.sendMessage(sender, "&cUso: &e/loot container debug <id>");
            MessageUtil.sendMessage(sender, "&7O mira directamente a un contenedor y ejecuta: &e/loot container debug");
            return;
        }

        long now = System.currentTimeMillis();
        ContainerType type = target.getContainerType();
        int configInterval = plugin.getRefillManager().getIntervalForType(type);

        String lastLootStr = target.getLastLoot() <= 0 ? "Nunca (0)" : target.getLastLoot() + " ms (" + ((now - target.getLastLoot()) / 1000) + "s atrás)";
        String nextRefillStr;
        if (target.getNextRefill() == null) {
            nextRefillStr = "&cnull (sin programar)";
        } else {
            long diff = target.getNextRefill() - now;
            if (diff <= 0) {
                nextRefillStr = "&a" + target.getNextRefill() + " ms (VENCIDO hace " + Math.abs(diff / 1000) + "s)";
            } else {
                nextRefillStr = "&e" + target.getNextRefill() + " ms (vence en " + (diff / 1000) + "s / " + (diff / 60) + "m)";
            }
        }

        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendRaw(sender, "&6&lLootRefill » Container Debug");
        MessageUtil.sendRaw(sender, "&7ID: &e" + target.getId());
        MessageUtil.sendRaw(sender, "&7Mundo: &f" + target.getWorld() + " &8(&7X:&f" + target.getX() + " &7Y:&f" + target.getY() + " &7Z:&f" + target.getZ() + "&8)");
        MessageUtil.sendRaw(sender, "&7Tipo: &f" + type.name());
        MessageUtil.sendRaw(sender, "&7Source: " + (target.getSource() == ContainerSource.MAP ? "&aMAP" : "&c" + target.getSource()));
        MessageUtil.sendRaw(sender, "&7Status: " + (target.getStatus() == ContainerStatus.ACTIVE ? "&aACTIVE" : "&c" + target.getStatus()));
        MessageUtil.sendRaw(sender, "&7Managed: &f" + target.isManaged() + " &8| &7Registered: &f" + target.isRegistered());
        MessageUtil.sendRaw(sender, "&7Loot Table ID: " + (target.getLootTableId() != null ? "&e" + target.getLootTableId() : "&cSin Loot Table (null)"));
        String poolId = target.getLootPoolId();
        if (poolId != null) {
            com.arcraft.lootrefill.pool.LootPool pool = plugin.getLootPoolManager().getPool(poolId);
            if (pool != null) {
                MessageUtil.sendRaw(sender, "&7Loot Pool ID: &a" + pool.getId() + " &8(&e" + pool.getSelectionMode().name() + "&8, Rolls: &f" + pool.getMinRolls() + "-" + pool.getMaxRolls() + "&8, TotalWeight: &f" + pool.getTotalWeight() + "&8)");
                List<String> entryStrs = new ArrayList<>();
                for (var e : pool.getEntries()) {
                    entryStrs.add(e.getTableId() + "(" + e.getWeight() + ")");
                }
                MessageUtil.sendRaw(sender, "  &8• &7Tablas del Pool: &f[" + String.join(", ", entryStrs) + "]");
            } else {
                MessageUtil.sendRaw(sender, "&7Loot Pool ID: &c" + poolId + " (No encontrado en memoria/DB)");
            }
        } else {
            MessageUtil.sendRaw(sender, "&7Loot Pool ID: &8null");
        }
        List<String> history = target.getRecentSelections();
        if (!history.isEmpty()) {
            MessageUtil.sendRaw(sender, "&7Historial selecciones: &b[" + String.join(" &8| &b", history) + "&b]");
        } else {
            MessageUtil.sendRaw(sender, "&7Historial selecciones: &8(Vacío)");
        }
        MessageUtil.sendRaw(sender, "&7Refill Enabled: &f" + target.isRefillEnabled());
        MessageUtil.sendRaw(sender, "&7Last Loot: &f" + lastLootStr);
        MessageUtil.sendRaw(sender, "&7Next Refill: " + nextRefillStr);
        int effectiveInterval = plugin.getRefillManager().getEffectiveIntervalForContainer(target);
        MessageUtil.sendRaw(sender, "&7Intervalo almacenado: &f" + target.getRefillIntervalSeconds() + "s");
        MessageUtil.sendRaw(sender, "&7Intervalo config: &f" + configInterval + "s");
        MessageUtil.sendRaw(sender, "&7Intervalo efectivo: &a" + effectiveInterval + "s");
        MessageUtil.sendRaw(sender, "&7isDue(): " + (target.isDue() ? "&aTRUE" : "&cFALSE"));
        MessageUtil.sendRaw(sender, "&7isEligibleForRefill(): " + (target.isEligibleForRefill() ? "&aTRUE" : "&cFALSE"));
        MessageUtil.sendRaw(sender, "&7Diagnóstico: " + (target.isEligibleForRefill() ? "&aELEGIBLE" : "&c" + target.getEligibilityReason()));
        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.sendRaw(sender, "&6&m----------------------------------------");
        MessageUtil.sendRaw(sender, "&6&lLootRefill &7- Comandos Administrativos");
        MessageUtil.sendRaw(sender, "&e/loot admin &7- Abre el menú principal de administración");
        MessageUtil.sendRaw(sender, "&e/loot wand &7- Entrega la varita para selección de regiones (A/B)");
        MessageUtil.sendRaw(sender, "&e/loot region info &7- Muestra información de la región seleccionada");
        MessageUtil.sendRaw(sender, "&e/loot region clear &7- Limpia la selección actual de región");
        MessageUtil.sendRaw(sender, "&e/loot region scan &7- Escanea contenedores en la región seleccionada");
        MessageUtil.sendRaw(sender, "&e/loot region assign [confirm] &7- Vista previa y registro de contenedores como MAP");
        MessageUtil.sendRaw(sender, "&e/loot region assign-pool <pool> [confirm] &7- Asigna un Loot Pool a contenedores de la región");
        MessageUtil.sendRaw(sender, "&e/loot reload &7- Recarga configuraciones y tablas");
        MessageUtil.sendRaw(sender, "&e/loot scan <mundo> &7- Inicia el escaneo del mundo indicado");
        MessageUtil.sendRaw(sender, "&e/loot scan [status|pause|resume|cancel] &7- Control del escáner");
        MessageUtil.sendRaw(sender, "&e/loot populate <mundo> [preview|confirm] &7- Genera loot ponderado progresivo");
        MessageUtil.sendRaw(sender, "&e/loot populate cancel &7- Detiene el populate activo");
        MessageUtil.sendRaw(sender, "&e/loot assign <mundo> <tipo> <tabla> [preview|confirm|--force] &7- Asigna tablas");
        MessageUtil.sendRaw(sender, "&e/loot assign-pool <mundo> <tipo> <pool> [preview|confirm|--force] &7- Asigna Loot Pools");
        MessageUtil.sendRaw(sender, "&e/loot refill [status|pause|resume|run] &7- Control y estado del Auto Refill");
        MessageUtil.sendRaw(sender, "&e/loot register <tabla> &7- Registra el contenedor que estás mirando con tabla");
        MessageUtil.sendRaw(sender, "&e/loot register-pool <pool> &7- Registra el contenedor que estás mirando con Loot Pool");
        MessageUtil.sendRaw(sender, "&e/loot containers reset <mundo> [confirm] &7- Reinicio administrativo de contenedores por mundo");
        MessageUtil.sendRaw(sender, "&e/loot container debug [id] &7- Diagnóstico detallado de un contenedor");
        MessageUtil.sendRaw(sender, "&e/loot refill debug &7- Diagnóstico detallado del scheduler de Auto Refill");
        MessageUtil.sendRaw(sender, "&e/loot help &7- Muestra esta lista de ayuda");
        MessageUtil.sendRaw(sender, "&6&m----------------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lootrefill.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> subs = Arrays.asList("admin", "wand", "region", "container", "containers", "reload", "scan", "populate", "assign", "assign-pool", "refill", "register", "register-pool", "help");
            return subs.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args[0].equalsIgnoreCase("region")) {
            if (args.length == 2) {
                return List.of("info", "clear", "scan", "assign", "assign-pool").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("assign")) {
                return List.of("confirm", "confirm-player").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("assign-pool")) {
                return plugin.getLootPoolManager().getPoolIds().stream()
                        .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("assign-pool")) {
                return List.of("preview", "confirm").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase()))
                        .toList();
            }
        }

        if (args[0].equalsIgnoreCase("container")) {
            if (args.length == 2) {
                return List.of("debug").stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("debug")) {
                return plugin.getContainerManager().getAllContainers().stream()
                        .map(c -> c.getId().toString())
                        .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                        .limit(15)
                        .toList();
            }
        }

        if (args[0].equalsIgnoreCase("containers")) {
            if (args.length == 2) {
                return List.of("reset", "debug").stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("debug")) {
                return plugin.getContainerManager().getAllContainers().stream()
                        .map(c -> c.getId().toString())
                        .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                        .limit(15)
                        .toList();
            }
            if (args.length == 3 && args[1].equalsIgnoreCase("reset")) {
                return Bukkit.getWorlds().stream()
                        .map(World::getName)
                        .filter(w -> w.toLowerCase().startsWith(args[2].toLowerCase()))
                        .toList();
            }
            if (args.length == 4 && args[1].equalsIgnoreCase("reset")) {
                return List.of("confirm").stream().filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).toList();
            }
        }

        if (args[0].equalsIgnoreCase("refill")) {
            if (args.length == 2) {
                return List.of("status", "pause", "resume", "run", "debug").stream()
                        .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                        .toList();
            }
        }

        if (args[0].equalsIgnoreCase("populate")) {
            if (args.length == 2) {
                List<String> options = new ArrayList<>();
                options.add("cancel");
                for (World w : Bukkit.getWorlds()) {
                    options.add(w.getName());
                }
                return options.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
            if (args.length == 3 && !args[1].equalsIgnoreCase("cancel")) {
                return List.of("preview", "confirm").stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
            }
        }

        if (args[0].equalsIgnoreCase("assign")) {
            if (args.length == 2) {
                return Bukkit.getWorlds().stream().map(World::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
            if (args.length == 3) {
                return Arrays.stream(ContainerType.values()).map(Enum::name).map(String::toLowerCase).filter(s -> s.startsWith(args[2].toLowerCase())).toList();
            }
            if (args.length == 4) {
                return plugin.getLootManager().getTableIds().stream().filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).toList();
            }
            if (args.length >= 5) {
                List<String> options = List.of("preview", "confirm", "--force");
                return options.stream().filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).toList();
            }
        }

        if (args[0].equalsIgnoreCase("assign-pool")) {
            if (args.length == 2) {
                return Bukkit.getWorlds().stream().map(World::getName).filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
            }
            if (args.length == 3) {
                return Arrays.stream(ContainerType.values()).map(Enum::name).map(String::toLowerCase).filter(s -> s.startsWith(args[2].toLowerCase())).toList();
            }
            if (args.length == 4) {
                return plugin.getLootPoolManager().getPoolIds().stream().filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).toList();
            }
            if (args.length >= 5) {
                List<String> options = List.of("preview", "confirm", "--force");
                return options.stream().filter(s -> s.toLowerCase().startsWith(args[args.length - 1].toLowerCase())).toList();
            }
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("scan")) {
            List<String> scanActions = new ArrayList<>(Arrays.asList("status", "pause", "resume", "cancel"));
            for (World w : Bukkit.getWorlds()) {
                scanActions.add(w.getName());
            }
            return scanActions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("register") || args[0].equalsIgnoreCase("set"))) {
            return plugin.getLootManager().getTableIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("register-pool") || args[0].equalsIgnoreCase("set-pool"))) {
            return plugin.getLootPoolManager().getPoolIds().stream()
                    .filter(id -> id.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }

        return List.of();
    }
}
