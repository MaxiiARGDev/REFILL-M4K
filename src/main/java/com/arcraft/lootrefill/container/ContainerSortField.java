package com.arcraft.lootrefill.container;

import java.util.Comparator;

/**
 * Criterios de ordenamiento seguros y tipados para contenedores.
 */
public enum ContainerSortField {
    CREATED_AT("created_at", Comparator.comparingLong(LootContainer::getCreatedAt)),
    UPDATED_AT("updated_at", Comparator.comparingLong(LootContainer::getUpdatedAt)),
    WORLD("world", Comparator.comparing(LootContainer::getWorld, String.CASE_INSENSITIVE_ORDER)),
    CONTAINER_TYPE("container_type", Comparator.comparing(c -> c.getContainerType().name())),
    STATUS("status", Comparator.comparing(c -> c.getStatus().name())),
    SOURCE("source", Comparator.comparing(c -> c.getSource().name())),
    NEXT_REFILL("next_refill", (c1, c2) -> {
        if (c1.getNextRefill() == null && c2.getNextRefill() == null) return 0;
        if (c1.getNextRefill() == null) return 1;
        if (c2.getNextRefill() == null) return -1;
        return Long.compare(c1.getNextRefill(), c2.getNextRefill());
    }),
    LOOT_POOL_ID("loot_pool_id", (c1, c2) -> {
        String p1 = c1.getLootPoolId() != null ? c1.getLootPoolId() : "";
        String p2 = c2.getLootPoolId() != null ? c2.getLootPoolId() : "";
        return p1.compareToIgnoreCase(p2);
    }),
    LOOT_TABLE_ID("loot_table_id", (c1, c2) -> {
        String t1 = c1.getLootTableId() != null ? c1.getLootTableId() : "";
        String t2 = c2.getLootTableId() != null ? c2.getLootTableId() : "";
        return t1.compareToIgnoreCase(t2);
    });

    private final String columnName;
    private final Comparator<LootContainer> comparator;

    ContainerSortField(String columnName, Comparator<LootContainer> comparator) {
        this.columnName = columnName;
        this.comparator = comparator;
    }

    public String getColumnName() {
        return columnName;
    }

    public Comparator<LootContainer> getComparator() {
        return comparator;
    }

    public static ContainerSortField fromString(String name) {
        if (name == null) return CREATED_AT;
        for (ContainerSortField field : values()) {
            if (field.name().equalsIgnoreCase(name) || field.columnName.equalsIgnoreCase(name)) {
                return field;
            }
        }
        return CREATED_AT;
    }
}
