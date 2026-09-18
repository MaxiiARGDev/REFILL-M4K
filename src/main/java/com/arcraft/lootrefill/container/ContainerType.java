package com.arcraft.lootrefill.container;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

public enum ContainerType {
    CHEST(Material.CHEST, "Cofre", "chest"),
    TRAPPED_CHEST(Material.TRAPPED_CHEST, "Cofre Trampa", "trapped-chest"),
    BARREL(Material.BARREL, "Barril", "barrel"),
    FURNACE(Material.FURNACE, "Horno", "furnace"),
    BLAST_FURNACE(Material.BLAST_FURNACE, "Alto Horno", "blast-furnace"),
    SMOKER(Material.SMOKER, "Ahumador", "smoker"),
    DISPENSER(Material.DISPENSER, "Dispensador", "dispenser"),
    DROPPER(Material.DROPPER, "Soltador", "dropper");

    private final Material material;
    private final String displayName;
    private final String configKey;

    ContainerType(Material material, String displayName, String configKey) {
        this.material = material;
        this.displayName = displayName;
        this.configKey = configKey;
    }

    public Material getMaterial() {
        return material;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static ContainerType fromBlock(Block block) {
        if (block == null) return null;
        return fromMaterial(block.getType());
    }

    public static ContainerType fromBlockState(BlockState state) {
        if (state == null) return null;
        return fromMaterial(state.getType());
    }

    public static ContainerType fromMaterial(Material material) {
        if (material == null) return null;
        return switch (material) {
            case CHEST -> CHEST;
            case TRAPPED_CHEST -> TRAPPED_CHEST;
            case BARREL -> BARREL;
            case FURNACE -> FURNACE;
            case BLAST_FURNACE -> BLAST_FURNACE;
            case SMOKER -> SMOKER;
            case DISPENSER -> DISPENSER;
            case DROPPER -> DROPPER;
            default -> null;
        };
    }

    public static ContainerType fromString(String name) {
        if (name == null) return CHEST;
        try {
            return ContainerType.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CHEST;
        }
    }
}
