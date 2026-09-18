package com.arcraft.lootrefill.loot;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class LootTable {

    private final String id;
    private String displayName;
    private String description;
    private boolean enabled;
    private int minItems;
    private int maxItems;
    private final List<LootEntry> entries;

    public LootTable(String id, String displayName, String description, boolean enabled, int minItems, int maxItems) {
        this.id = id;
        this.displayName = displayName != null ? displayName : id;
        this.description = description != null ? description : "";
        this.enabled = enabled;
        this.minItems = Math.max(0, minItems);
        this.maxItems = Math.max(this.minItems, maxItems);
        this.entries = new ArrayList<>();
    }

    public LootTable(String id, String displayName, String description, boolean enabled, int minItems, int maxItems, List<LootEntry> entries) {
        this.id = id;
        this.displayName = displayName != null ? displayName : id;
        this.description = description != null ? description : "";
        this.enabled = enabled;
        this.minItems = Math.max(0, minItems);
        this.maxItems = Math.max(this.minItems, maxItems);
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMinItems() {
        return minItems;
    }

    public void setMinItems(int minItems) {
        this.minItems = Math.max(0, minItems);
        if (this.maxItems < this.minItems) {
            this.maxItems = this.minItems;
        }
    }

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = Math.max(this.minItems, maxItems);
    }

    public List<LootEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public synchronized void addEntry(LootEntry entry) {
        if (entry != null) {
            entries.removeIf(e -> e.getId().equals(entry.getId()));
            entries.add(entry);
        }
    }

    public synchronized boolean removeEntry(UUID entryId) {
        return entries.removeIf(e -> e.getId().equals(entryId));
    }

    public synchronized void clearEntries() {
        entries.clear();
    }

    /**
     * Genera una lista de items basada en los pesos y rangos configurados sin acoplarse a ningun inventario Bukkit.
     */
    public List<ItemStack> generateLoot() {
        if (!enabled || entries.isEmpty() || maxItems <= 0) {
            return Collections.emptyList();
        }

        List<LootEntry> activeEntries = new ArrayList<>();
        int totalWeight = 0;
        for (LootEntry entry : entries) {
            if (entry.isEnabled() && entry.getWeight() > 0) {
                activeEntries.add(entry);
                totalWeight += entry.getWeight();
            }
        }

        if (activeEntries.isEmpty() || totalWeight <= 0) {
            return Collections.emptyList();
        }

        Random random = ThreadLocalRandom.current();
        int rolls = minItems == maxItems ? minItems : minItems + random.nextInt(maxItems - minItems + 1);
        List<ItemStack> result = new ArrayList<>(rolls);

        for (int i = 0; i < rolls; i++) {
            int pick = random.nextInt(totalWeight);
            int current = 0;
            for (LootEntry entry : activeEntries) {
                current += entry.getWeight();
                if (pick < current) {
                    ItemStack item = entry.getItem();
                    int minAmount = entry.getMinAmount();
                    int maxAmount = entry.getMaxAmount();
                    int amount = minAmount == maxAmount ? minAmount : minAmount + random.nextInt(maxAmount - minAmount + 1);
                    item.setAmount(Math.max(1, Math.min(amount, item.getMaxStackSize())));
                    result.add(item);
                    break;
                }
            }
        }

        return result;
    }
}
