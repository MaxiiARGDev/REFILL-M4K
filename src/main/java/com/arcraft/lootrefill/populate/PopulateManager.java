package com.arcraft.lootrefill.populate;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PopulateManager {

    private final LootRefillPlugin plugin;
    private final ContainerManager containerManager;
    private final ContainerPopulator populator;
    private final PopulateQueue populateQueue;

    public PopulateManager(LootRefillPlugin plugin, ContainerManager containerManager) {
        this.plugin = plugin;
        this.containerManager = containerManager;
        this.populator = new ContainerPopulator(plugin, containerManager);
        this.populateQueue = new PopulateQueue(plugin, populator);
    }

    public PopulateQueue getPopulateQueue() {
        return populateQueue;
    }

    public ContainerPopulator getPopulator() {
        return populator;
    }

    public boolean isPopulating() {
        return populateQueue.isRunning();
    }

    /**
     * Genera una vista previa (Preview) sin modificar ningún inventario del mundo.
     */
    public void showPreview(World world, CommandSender sender) {
        if (world == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo especificado no existe.");
            return;
        }

        String worldName = world.getName();
        int mapContainers = 0;
        int eligible = 0;
        int skippedNoTable = 0;
        int skippedUnsupported = 0;
        int skippedBroken = 0;

        Map<ContainerType, Integer> typeCounts = new EnumMap<>(ContainerType.class);
        Map<String, Integer> tableCounts = new HashMap<>();

        for (LootContainer c : containerManager.getAllContainers()) {
            if (!c.getWorld().equalsIgnoreCase(worldName)) {
                continue;
            }

            if (c.getSource() != ContainerSource.MAP) {
                continue; // Los contenedores PLAYER se excluyen silenciosamente
            }

            mapContainers++;

            if (c.getStatus() == ContainerStatus.BROKEN) {
                skippedBroken++;
                continue;
            }

            if (c.getStatus() != ContainerStatus.ACTIVE || !c.isManaged() || !c.isRegistered()) {
                continue;
            }

            boolean typeEnabled = plugin.getConfig().getBoolean("populate.container-types." + c.getContainerType().getConfigKey() + ".enabled", true);
            if (!typeEnabled) {
                skippedUnsupported++;
                continue;
            }

            String tableId = c.getLootTableId();
            if (tableId == null || tableId.trim().isEmpty()) {
                skippedNoTable++;
                continue;
            }

            eligible++;
            typeCounts.merge(c.getContainerType(), 1, Integer::sum);
            tableCounts.merge(tableId.toUpperCase(), 1, Integer::sum);
        }

        int totalSkipped = skippedNoTable + skippedUnsupported + skippedBroken;

        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        MessageUtil.sendRaw(sender, "&6&lLootRefill Populate Preview &8» &e" + worldName);
        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "&7Contenedores MAP totales: &f" + mapContainers);
        MessageUtil.sendRaw(sender, "&aElegibles para Populate: &e" + eligible);
        MessageUtil.sendRaw(sender, "&cSaltados / Inelegibles: &f" + totalSkipped);
        if (skippedNoTable > 0) MessageUtil.sendRaw(sender, "  &8• &7Sin tabla asignada: &e" + skippedNoTable);
        if (skippedUnsupported > 0) MessageUtil.sendRaw(sender, "  &8• &7Tipo no soportado (Hornos, etc.): &e" + skippedUnsupported);
        if (skippedBroken > 0) MessageUtil.sendRaw(sender, "  &8• &7Bloque roto (BROKEN): &e" + skippedBroken);
        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "&7Distribución por Tipo:");
        for (Map.Entry<ContainerType, Integer> entry : typeCounts.entrySet()) {
            MessageUtil.sendRaw(sender, "  &8• &f" + entry.getKey().name() + ": &a" + entry.getValue());
        }
        MessageUtil.sendRaw(sender, "&7Distribución por Loot Table:");
        for (Map.Entry<String, Integer> entry : tableCounts.entrySet()) {
            MessageUtil.sendRaw(sender, "  &8• &f" + entry.getKey() + ": &e" + entry.getValue());
        }
        MessageUtil.sendRaw(sender, "");
        MessageUtil.sendRaw(sender, "&aNingún contenedor fue modificado. &7(Modo Preview)");
        MessageUtil.sendRaw(sender, "&7Para ejecutar populate real usa: &e/loot populate " + worldName + " confirm");
        MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    /**
     * Inicia el proceso de Populate real sobre los contenedores MAP elegibles del mundo.
     */
    public void startPopulate(World world, CommandSender sender) {
        if (world == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo especificado no existe.");
            return;
        }

        if (isPopulating()) {
            MessageUtil.sendMessage(sender, "&cYa existe un proceso de Populate en ejecución para &e" + populateQueue.getActiveJob().getWorld());
            MessageUtil.sendMessage(sender, "&7Usa &e/loot populate cancel &7para detenerlo.");
            return;
        }

        String worldName = world.getName();
        List<LootContainer> candidates = new ArrayList<>();

        for (LootContainer c : containerManager.getAllContainers()) {
            if (c.getWorld().equalsIgnoreCase(worldName)
                    && c.getSource() == ContainerSource.MAP
                    && c.getStatus() == ContainerStatus.ACTIVE
                    && c.isManaged()
                    && c.isRegistered()
                    && c.getLootTableId() != null
                    && !c.getLootTableId().trim().isEmpty()) {

                boolean typeEnabled = plugin.getConfig().getBoolean("populate.container-types." + c.getContainerType().getConfigKey() + ".enabled", true);
                if (typeEnabled) {
                    candidates.add(c);
                }
            }
        }

        if (candidates.isEmpty()) {
            MessageUtil.sendMessage(sender, "&cNo se encontraron contenedores MAP elegibles con tabla asignada en &e" + worldName + "&c.");
            MessageUtil.sendMessage(sender, "&7Usa &e/loot assign " + worldName + " <tipo> <tabla> &7para asignar tablas primero.");
            return;
        }

        MessageUtil.sendMessage(sender, "&6Iniciando Populate en &e" + worldName + " &6con &e" + candidates.size() + " &6contenedores elegibles...");
        populateQueue.start(world, candidates);
    }

    public void cancelPopulate(CommandSender sender) {
        if (!isPopulating()) {
            MessageUtil.sendMessage(sender, "&cNo hay ningún proceso de Populate activo para cancelar.");
            return;
        }
        populateQueue.cancel();
        MessageUtil.sendMessage(sender, "&cProceso de Populate cancelado exitosamente.");
    }

    /**
     * Asignación controlada de tablas de loot por tipo de contenedor en un mundo (/loot assign).
     */
    public void assignLootTable(World world, ContainerType type, String lootTableId, boolean force, boolean preview, CommandSender sender) {
        if (world == null) {
            MessageUtil.sendMessage(sender, "&cEl mundo especificado no existe.");
            return;
        }

        if (!plugin.getLootManager().tableExists(lootTableId)) {
            MessageUtil.sendMessage(sender, "&cLa tabla de loot '&e" + lootTableId + "&c' no existe.");
            return;
        }

        String worldName = world.getName();
        int foundMap = 0;
        int alreadyAssigned = 0;
        List<LootContainer> toUpdate = new ArrayList<>();

        for (LootContainer c : containerManager.getAllContainers()) {
            if (c.getWorld().equalsIgnoreCase(worldName)
                    && c.getContainerType() == type
                    && c.getSource() == ContainerSource.MAP
                    && c.getStatus() == ContainerStatus.ACTIVE
                    && c.isManaged()) {

                foundMap++;

                boolean hasTable = c.getLootTableId() != null && !c.getLootTableId().trim().isEmpty();
                if (hasTable && !force) {
                    alreadyAssigned++;
                    continue;
                }

                toUpdate.add(c);
            }
        }

        if (preview) {
            MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            MessageUtil.sendRaw(sender, "&6&lLootRefill Assignment Preview");
            MessageUtil.sendRaw(sender, "&7Mundo: &e" + worldName);
            MessageUtil.sendRaw(sender, "&7Tipo: &f" + type.name());
            MessageUtil.sendRaw(sender, "&7Loot Table objetivo: &e" + lootTableId.toUpperCase());
            MessageUtil.sendRaw(sender, "");
            MessageUtil.sendRaw(sender, "&7Contenedores MAP encontrados: &f" + foundMap);
            MessageUtil.sendRaw(sender, "&aElegibles para asignación: &e" + toUpdate.size());
            MessageUtil.sendRaw(sender, "&7Ya tenían tabla asignada: &f" + alreadyAssigned + (force ? " &c(Serán sobreescritos por --force)" : " &7(Se conservarán)"));
            MessageUtil.sendRaw(sender, "");
            MessageUtil.sendRaw(sender, "&aNo se realizaron cambios. &7(Modo Preview)");
            MessageUtil.sendRaw(sender, "&7Para confirmar ejecuta:");
            MessageUtil.sendRaw(sender, "&e/loot assign " + worldName + " " + type.name().toLowerCase() + " " + lootTableId + (force ? " --force" : "") + " confirm");
            MessageUtil.sendRaw(sender, "&6&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            return;
        }

        // Ejecución real de la asignación
        long now = System.currentTimeMillis();
        for (LootContainer c : toUpdate) {
            c.setLootTableId(lootTableId);
            c.setManaged(true);
            c.setRefillEnabled(true);
            c.setNextRefill(now);
            c.setUpdatedAt(now);
        }

        containerManager.saveContainersBatch(toUpdate);

        MessageUtil.sendMessage(sender, "&aAsignación completada exitosamente.");
        MessageUtil.sendMessage(sender, "&7Se asignó la tabla &e" + lootTableId.toUpperCase() + " &7a &a" + toUpdate.size() + " &7contenedores de tipo &f" + type.name() + " &7en &e" + worldName + "&7.");
    }
}
