package com.arcraft.lootrefill.container;

import com.arcraft.lootrefill.LootRefillPlugin;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class ContainerBlockListener implements Listener {

    private final LootRefillPlugin plugin;
    private final ContainerManager containerManager;
    private final ContainerRegistry containerRegistry;

    public ContainerBlockListener(LootRefillPlugin plugin, ContainerManager containerManager, ContainerRegistry containerRegistry) {
        this.plugin = plugin;
        this.containerManager = containerManager;
        this.containerRegistry = containerRegistry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (!containerManager.isAllowedContainerBlock(block)) {
            return;
        }

        containerRegistry.registerPlayerBlock(block);

        if (plugin.getConfig().getBoolean("plugin.debug", false)) {
            ContainerType type = ContainerType.fromBlock(block);
            String typeName = type != null ? type.name() : block.getType().name();
            plugin.getLogger().info("[DEBUG] PLAYER container placed: "
                    + block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ()
                    + " Type: " + typeName);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!containerManager.isAllowedContainerBlock(block)) {
            return;
        }

        containerRegistry.handleBlockBreak(block);
    }
}
