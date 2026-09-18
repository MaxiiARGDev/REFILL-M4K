package com.arcraft.lootrefill.command;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerType;
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
            case "refill" -> handleRefillCommand(sender, args);
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
            default -> {
                MessageUtil.sendMessage(sender, "&cAcción desconocida '&e" + args[1] + "&c'. Usa &e[status|pause|resume|run]&c.");
            }
        }
    }

    private void sendHelp(CommandSender sender) {
        MessageUtil.sendRaw(sender, "&6&m----------------------------------------");
        MessageUtil.sendRaw(sender, "&6&lLootRefill &7- Comandos Administrativos");
        MessageUtil.sendRaw(sender, "&e/loot admin &7- Abre el menú principal de administración");
        MessageUtil.sendRaw(sender, "&e/loot reload &7- Recarga configuraciones y tablas");
        MessageUtil.sendRaw(sender, "&e/loot scan <mundo> &7- Inicia el escaneo del mundo indicado");
        MessageUtil.sendRaw(sender, "&e/loot scan [status|pause|resume|cancel] &7- Control del escáner");
        MessageUtil.sendRaw(sender, "&e/loot populate <mundo> [preview|confirm] &7- Genera loot ponderado progresivo");
        MessageUtil.sendRaw(sender, "&e/loot populate cancel &7- Detiene el populate activo");
        MessageUtil.sendRaw(sender, "&e/loot assign <mundo> <tipo> <tabla> [preview|confirm|--force] &7- Asigna tablas");
        MessageUtil.sendRaw(sender, "&e/loot refill [status|pause|resume|run] &7- Control y estado del Auto Refill");
        MessageUtil.sendRaw(sender, "&e/loot register <tabla> &7- Registra el contenedor que estás mirando");
        MessageUtil.sendRaw(sender, "&e/loot help &7- Muestra esta lista de ayuda");
        MessageUtil.sendRaw(sender, "&6&m----------------------------------------");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("lootrefill.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            List<String> subs = Arrays.asList("admin", "reload", "scan", "populate", "assign", "refill", "register", "help");
            return subs.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }

        if (args[0].equalsIgnoreCase("refill")) {
            if (args.length == 2) {
                return List.of("status", "pause", "resume", "run").stream()
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

        return List.of();
    }
}
