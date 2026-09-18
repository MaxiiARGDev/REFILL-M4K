package com.arcraft.lootrefill.populate;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.loot.LootGenerator;
import com.arcraft.lootrefill.loot.LootTable;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ContainerPopulator {

    private final LootRefillPlugin plugin;
    private final ContainerManager containerManager;
    private final Set<String> processedDoubleChests;

    public ContainerPopulator(LootRefillPlugin plugin, ContainerManager containerManager) {
        this.plugin = plugin;
        this.containerManager = containerManager;
        this.processedDoubleChests = ConcurrentHashMap.newKeySet();
    }

    public void resetDoubleChestTracker() {
        processedDoubleChests.clear();
    }

    /**
     * Intenta poblar un contenedor de forma segura, respetando todas las reglas de la Etapa 3.
     * Debe ejecutarse en el HILO PRINCIPAL de Bukkit para operaciones de bloque e inventario.
     */
    public PopulateResult populate(LootContainer container) {
        if (container == null) {
            return PopulateResult.SKIPPED_INVALID;
        }

        // 1. Protección estricta de origen y estado
        if (container.getSource() == ContainerSource.PLAYER) {
            return PopulateResult.SKIPPED_PLAYER;
        }
        if (container.getStatus() == ContainerStatus.BROKEN) {
            return PopulateResult.SKIPPED_BROKEN;
        }
        if (container.getStatus() == ContainerStatus.DISABLED) {
            return PopulateResult.SKIPPED_DISABLED;
        }
        if (container.getSource() != ContainerSource.MAP || !container.isManaged() || !container.isRegistered()) {
            return PopulateResult.SKIPPED_INVALID;
        }

        // 2. Validación y resolución de Loot Source (Pool o Tabla legada)
        com.arcraft.lootrefill.pool.LootPool pool = null;
        List<LootTable> selectedTables = null;
        LootTable legacyTable = null;

        if (container.getLootPoolId() != null && !container.getLootPoolId().trim().isEmpty()) {
            pool = plugin.getLootPoolManager().getPool(container.getLootPoolId());
            if (pool == null || !pool.isEnabled() || !pool.hasEntries()) {
                plugin.getLogger().warning("[Populate] Loot Pool inexistente, deshabilitado o sin tablas para el contenedor "
                        + container.getId() + " (Pool: " + container.getLootPoolId() + ")");
                return PopulateResult.SKIPPED_INVALID_LOOT_POOL;
            }
            selectedTables = plugin.getLootPoolManager().selectTables(pool);
            if (selectedTables.isEmpty()) {
                plugin.getLogger().warning("[Populate] No se pudieron seleccionar tablas válidas del pool " + pool.getId());
                return PopulateResult.SKIPPED_INVALID_LOOT_POOL;
            }
        } else if (container.getLootTableId() != null && !container.getLootTableId().trim().isEmpty()) {
            String tableId = container.getLootTableId();
            legacyTable = plugin.getLootManager().getTable(tableId);
            if (legacyTable == null || !legacyTable.isEnabled()) {
                return PopulateResult.SKIPPED_NO_LOOT_TABLE;
            }
        } else {
            return PopulateResult.SKIPPED_NO_LOOT_TABLE;
        }

        // 3. Validación de tipo habilitado para Populate en config.yml
        ContainerType type = container.getContainerType();
        boolean typeEnabledForPopulate = plugin.getConfig().getBoolean("populate.container-types." + type.getConfigKey() + ".enabled", true);
        if (!typeEnabledForPopulate) {
            return PopulateResult.SKIPPED_UNSUPPORTED_TYPE;
        }

        Location loc = container.toLocation();
        if (loc == null || loc.getWorld() == null) {
            return PopulateResult.SKIPPED_INVALID;
        }

        Block block = loc.getBlock();
        if (!(block.getState() instanceof Container blockContainer)) {
            // El bloque físico fue destruido o alterado
            container.setStatus(ContainerStatus.BROKEN);
            container.setManaged(false);
            container.setRefillEnabled(false);
            container.setUpdatedAt(System.currentTimeMillis());
            containerManager.saveContainer(container);
            return PopulateResult.SKIPPED_BROKEN;
        }

        ContainerType actualType = ContainerType.fromBlock(block);
        if (actualType != type) {
            return PopulateResult.SKIPPED_INVALID;
        }

        Inventory inv = blockContainer.getInventory();

        // 4. Tratamiento seguro de Double Chest para evitar duplicación
        if (block.getState() instanceof Chest chest && inv.getHolder() instanceof DoubleChest doubleChest) {
            Location leftLoc = ((Chest) doubleChest.getLeftSide()).getLocation();
            Location rightLoc = ((Chest) doubleChest.getRightSide()).getLocation();
            String chestPairKey = loc.getWorld().getName() + ":" + Math.min(leftLoc.getBlockX(), rightLoc.getBlockX()) + ":" + Math.min(leftLoc.getBlockZ(), rightLoc.getBlockZ());

            if (processedDoubleChests.contains(chestPairKey)) {
                return PopulateResult.SKIPPED_DOUBLE_CHEST_ALREADY_POPULATED;
            }
            processedDoubleChests.add(chestPairKey);
        }

        // 5. Verificación de require-empty (NUNCA usar inv.clear() en populate)
        boolean requireEmpty = plugin.getConfig().getBoolean("populate.require-empty", true);
        if (requireEmpty && !isInventoryEmpty(inv)) {
            return PopulateResult.SKIPPED_NOT_EMPTY;
        }

        // 6. Generación de loot ponderado mediante LootGenerator
        List<ItemStack> loot = new ArrayList<>();
        String selectionSummary;

        if (pool != null && selectedTables != null) {
            List<String> names = new ArrayList<>();
            for (LootTable t : selectedTables) {
                loot.addAll(LootGenerator.generate(t));
                names.add(t.getId().toUpperCase());
            }
            selectionSummary = String.join(" + ", names);
        } else if (legacyTable != null) {
            loot = LootGenerator.generate(legacyTable);
            selectionSummary = legacyTable.getId().toUpperCase();
        } else {
            return PopulateResult.SKIPPED_INVALID;
        }

        if (loot.isEmpty()) {
            return PopulateResult.SKIPPED_INVALID;
        }

        container.addRecentSelection(selectionSummary);

        // 7. Distribución aleatoria en slots disponibles
        distributeLootRandomSlots(inv, loot);

        // 8. Actualización de timestamps del contenedor
        long now = System.currentTimeMillis();
        int interval = plugin.getRefillManager().getIntervalForType(type);

        container.setLooted(false);
        container.setLastLoot(now);
        container.setNextRefill(now + (interval * 1000L));
        container.setUpdatedAt(now);
        containerManager.saveContainer(container);

        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
            plugin.getLogger().info("[DEBUG] Populated MAP " + type.name() + " at "
                    + container.getWorld() + " " + container.getX() + " " + container.getY() + " " + container.getZ()
                    + " with loot: " + selectionSummary);
        }

        return PopulateResult.POPULATED;
    }

    private void distributeLootRandomSlots(Inventory inv, List<ItemStack> items) {
        int size = inv.getSize();
        List<Integer> slots = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            slots.add(i);
        }
        Collections.shuffle(slots, ThreadLocalRandom.current());

        for (int i = 0; i < items.size() && i < slots.size(); i++) {
            inv.setItem(slots.get(i), items.get(i));
        }
    }

    public boolean isInventoryEmpty(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item != null && !item.getType().isAir()) {
                return false;
            }
        }
        return true;
    }
}
