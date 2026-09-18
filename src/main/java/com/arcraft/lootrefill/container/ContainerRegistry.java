package com.arcraft.lootrefill.container;

import com.arcraft.lootrefill.LootRefillPlugin;
import org.bukkit.Location;
import org.bukkit.block.Block;

public class ContainerRegistry {

    private final LootRefillPlugin plugin;
    private final ContainerManager containerManager;

    public ContainerRegistry(LootRefillPlugin plugin, ContainerManager containerManager) {
        this.plugin = plugin;
        this.containerManager = containerManager;
    }

    /**
     * Registra explícitamente un bloque como almacenamiento MAP administrable.
     * Invocado mediante comando administrativo (/loot register).
     */
    public LootContainer registerMapBlock(Block block, String lootTableId) {
        if (block == null || !containerManager.isAllowedContainerBlock(block)) {
            return null;
        }

        ContainerType type = ContainerType.fromBlock(block);
        if (type == null) return null;

        Location loc = block.getLocation();
        LootContainer existing = containerManager.getContainer(loc);
        long now = System.currentTimeMillis();
        int interval = plugin.getRefillManager().getIntervalForType(type);

        if (existing != null) {
            existing.setContainerType(type);
            existing.setLootTableId(lootTableId);
            existing.setSource(ContainerSource.MAP);
            existing.setStatus(ContainerStatus.ACTIVE);
            existing.setManaged(true);
            existing.setRegistered(true);
            existing.setRefillEnabled(true);
            existing.setRefillIntervalSeconds(interval);
            existing.setNextRefill(now);
            existing.setUpdatedAt(now);
            containerManager.saveContainer(existing);
            return existing;
        }

        LootContainer container = new LootContainer(
                java.util.UUID.randomUUID(),
                loc.getWorld().getName(),
                loc.getBlockX(),
                loc.getBlockY(),
                loc.getBlockZ(),
                type,
                lootTableId,
                true,
                false,
                0L,
                now,
                true,
                interval,
                ContainerSource.MAP,
                ContainerStatus.ACTIVE,
                true,
                true,
                now,
                now
        );
        containerManager.registerContainer(container, true);
        return container;
    }

    /**
     * Registra un bloque colocado por un jugador.
     * Queda explícitamente como source = PLAYER, managed = false, registered = false.
     * Nunca entra a colas de refill ni generación automática de loot.
     */
    public LootContainer registerPlayerBlock(Block block) {
        if (block == null || !containerManager.isAllowedContainerBlock(block)) {
            return null;
        }

        ContainerType type = ContainerType.fromBlock(block);
        if (type == null) return null;

        Location loc = block.getLocation();
        long now = System.currentTimeMillis();
        LootContainer existing = containerManager.getContainer(loc);

        if (existing != null) {
            // Reutilización de coordenada: Si era un MAP que fue roto previamente,
            // al colocar el jugador un nuevo bloque aquí se convierte en PLAYER y NO hereda la tabla previa!
            existing.setContainerType(type);
            existing.setSource(ContainerSource.PLAYER);
            existing.setStatus(ContainerStatus.ACTIVE);
            existing.setManaged(false);
            existing.setRegistered(false);
            existing.setLootTableId(null);
            existing.setNextRefill(null);
            existing.setRefillEnabled(false);
            existing.setUpdatedAt(now);
            containerManager.saveContainer(existing);
            return existing;
        }

        LootContainer container = LootContainer.createPlayerContainer(loc, type);
        containerManager.registerContainer(container, true);
        return container;
    }

    /**
     * Maneja la destrucción de un almacenamiento.
     * Si es MAP, se marca como BROKEN y se conserva en SQLite sin borrarlo.
     */
    public boolean handleBlockBreak(Block block) {
        if (block == null) return false;
        Location loc = block.getLocation();
        LootContainer container = containerManager.getContainer(loc);
        if (container == null) return false;

        boolean debug = plugin.getConfig().getBoolean("plugin.debug", false);

        if (container.getSource() == ContainerSource.MAP) {
            container.setStatus(ContainerStatus.BROKEN);
            container.setManaged(false);
            container.setRefillEnabled(false);
            container.setUpdatedAt(System.currentTimeMillis());
            containerManager.saveContainer(container);

            if (debug) {
                plugin.getLogger().info("[DEBUG] MAP container broken: " + container.getWorld() + " "
                        + container.getX() + " " + container.getY() + " " + container.getZ()
                        + " Type: " + container.getContainerType().name());
            }
            return true;
        } else if (container.getSource() == ContainerSource.PLAYER) {
            containerManager.unregisterContainer(container.getId());
            if (debug) {
                plugin.getLogger().info("[DEBUG] PLAYER container broken: " + container.getWorld() + " "
                        + container.getX() + " " + container.getY() + " " + container.getZ()
                        + " Type: " + container.getContainerType().name());
            }
            return true;
        }

        return false;
    }

    public LootContainer registerBlock(Block block, String lootTableId) {
        return registerMapBlock(block, lootTableId);
    }

    public boolean unregisterBlock(Block block) {
        return handleBlockBreak(block);
    }
}
