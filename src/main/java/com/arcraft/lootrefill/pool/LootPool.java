package com.arcraft.lootrefill.pool;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LootPool {

    private final String id;
    private String name;
    private PoolSelectionMode selectionMode;
    private int minRolls;
    private int maxRolls;
    private boolean allowDuplicates;
    private boolean enabled;
    private final long createdAt;
    private long updatedAt;
    private final Map<String, LootPoolEntry> entries;

    public LootPool(String id, String name, PoolSelectionMode selectionMode, int minRolls, int maxRolls, boolean allowDuplicates, boolean enabled, long createdAt, long updatedAt) {
        this.id = id != null ? id.toLowerCase().trim() : "";
        this.name = (name != null && !name.trim().isEmpty()) ? name.trim() : this.id;
        this.selectionMode = selectionMode != null ? selectionMode : PoolSelectionMode.SINGLE_RANDOM;
        this.minRolls = Math.max(1, minRolls);
        this.maxRolls = Math.max(this.minRolls, maxRolls);
        this.allowDuplicates = allowDuplicates;
        this.enabled = enabled;
        this.createdAt = createdAt > 0 ? createdAt : System.currentTimeMillis();
        this.updatedAt = updatedAt > 0 ? updatedAt : this.createdAt;
        this.entries = new LinkedHashMap<>();
    }

    public LootPool(String id, String name, PoolSelectionMode selectionMode, int minRolls, int maxRolls, boolean allowDuplicates, boolean enabled) {
        this(id, name, selectionMode, minRolls, maxRolls, allowDuplicates, enabled, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public LootPool(String id, String name, PoolSelectionMode selectionMode, int minRolls, int maxRolls, boolean enabled, long createdAt, long updatedAt) {
        this(id, name, selectionMode, minRolls, maxRolls, false, enabled, createdAt, updatedAt);
    }

    public LootPool(String id, String name, PoolSelectionMode selectionMode, int minRolls, int maxRolls, boolean enabled) {
        this(id, name, selectionMode, minRolls, maxRolls, false, enabled, System.currentTimeMillis(), System.currentTimeMillis());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.updatedAt = System.currentTimeMillis();
    }

    public PoolSelectionMode getSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(PoolSelectionMode selectionMode) {
        this.selectionMode = selectionMode != null ? selectionMode : PoolSelectionMode.SINGLE_RANDOM;
        this.updatedAt = System.currentTimeMillis();
    }

    public int getMinRolls() {
        return minRolls;
    }

    public void setMinRolls(int minRolls) {
        this.minRolls = Math.max(1, minRolls);
        if (this.maxRolls < this.minRolls) {
            this.maxRolls = this.minRolls;
        }
        this.updatedAt = System.currentTimeMillis();
    }

    public int getMaxRolls() {
        return maxRolls;
    }

    public void setMaxRolls(int maxRolls) {
        this.maxRolls = Math.max(this.minRolls, maxRolls);
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isAllowDuplicates() {
        return allowDuplicates;
    }

    public void setAllowDuplicates(boolean allowDuplicates) {
        this.allowDuplicates = allowDuplicates;
        this.updatedAt = System.currentTimeMillis();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = System.currentTimeMillis();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Collection<LootPoolEntry> getEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public LootPoolEntry getEntry(String tableId) {
        if (tableId == null) return null;
        return entries.get(tableId.toLowerCase().trim());
    }

    public void addEntry(LootPoolEntry entry) {
        if (entry == null) return;
        entries.put(entry.getTableId().toLowerCase().trim(), entry);
        this.updatedAt = System.currentTimeMillis();
    }

    public void removeEntry(String tableId) {
        if (tableId == null) return;
        entries.remove(tableId.toLowerCase().trim());
        this.updatedAt = System.currentTimeMillis();
    }

    public int getTotalWeight() {
        int total = 0;
        for (LootPoolEntry entry : entries.values()) {
            total += entry.getWeight();
        }
        return total;
    }

    public boolean hasEntries() {
        return !entries.isEmpty() && getTotalWeight() > 0;
    }

    public List<String> getTableIds() {
        return new ArrayList<>(entries.keySet());
    }
}
