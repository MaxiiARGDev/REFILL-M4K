package com.arcraft.lootrefill.pool;

import java.util.UUID;

public class LootPoolEntry {

    private final UUID id;
    private final String poolId;
    private final String tableId;
    private int weight;

    public LootPoolEntry(UUID id, String poolId, String tableId, int weight) {
        this.id = id != null ? id : UUID.randomUUID();
        this.poolId = poolId != null ? poolId.toLowerCase().trim() : "";
        this.tableId = tableId != null ? tableId.toLowerCase().trim() : "";
        this.weight = Math.max(1, weight);
    }

    public LootPoolEntry(String poolId, String tableId, int weight) {
        this(UUID.randomUUID(), poolId, tableId, weight);
    }

    public UUID getId() {
        return id;
    }

    public String getPoolId() {
        return poolId;
    }

    public String getTableId() {
        return tableId;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = Math.max(1, weight);
    }
}
