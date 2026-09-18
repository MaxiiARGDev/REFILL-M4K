package com.arcraft.lootrefill.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Base64;
import java.util.UUID;

public class LootEntry {

    private final UUID id;
    private ItemStack item;
    private int weight;
    private int minAmount;
    private int maxAmount;
    private boolean enabled;
    private String customType; // "VANILLA", "ITEMSADDER", "COMMAND"
    private String command;    // Comandos de recompensa opcionales para futuras etapas

    public LootEntry(UUID id, ItemStack item, int weight, int minAmount, int maxAmount, boolean enabled) {
        this(id, item, weight, minAmount, maxAmount, enabled, "VANILLA", "");
    }

    public LootEntry(UUID id, ItemStack item, int weight, int minAmount, int maxAmount, boolean enabled, String customType, String command) {
        this.id = id != null ? id : UUID.randomUUID();
        this.item = item != null ? item.clone() : new ItemStack(Material.STONE);
        this.weight = Math.max(1, weight);
        this.minAmount = Math.max(1, minAmount);
        this.maxAmount = Math.max(this.minAmount, maxAmount);
        this.enabled = enabled;
        this.customType = customType != null ? customType : "VANILLA";
        this.command = command != null ? command : "";
    }

    public static LootEntry createDefault(ItemStack item) {
        int amount = item != null ? item.getAmount() : 1;
        return new LootEntry(UUID.randomUUID(), item, 100, 1, Math.max(1, amount), true);
    }

    public UUID getId() {
        return id;
    }

    public ItemStack getItem() {
        return item != null ? item.clone() : new ItemStack(Material.STONE);
    }

    public void setItem(ItemStack item) {
        this.item = item != null ? item.clone() : new ItemStack(Material.STONE);
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = Math.max(1, weight);
    }

    public int getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(int minAmount) {
        this.minAmount = Math.max(1, minAmount);
        if (this.maxAmount < this.minAmount) {
            this.maxAmount = this.minAmount;
        }
    }

    public int getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(int maxAmount) {
        this.maxAmount = Math.max(this.minAmount, maxAmount);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCustomType() {
        return customType;
    }

    public void setCustomType(String customType) {
        this.customType = customType;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public String serializeItemToBase64() {
        if (item == null || item.getType() == Material.AIR) {
            return "";
        }
        byte[] bytes = item.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static ItemStack deserializeItemFromBase64(String base64) {
        if (base64 == null || base64.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            return ItemStack.deserializeBytes(bytes);
        } catch (Exception e) {
            return new ItemStack(Material.STONE);
        }
    }
}
