package com.arcraft.lootrefill.region;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RegionManager {

    private final LootRefillPlugin plugin;
    private final NamespacedKey wandKey;
    private final Map<UUID, RegionSelection> selections;
    private final Map<UUID, RegionScanResult> lastScanResults;

    public RegionManager(LootRefillPlugin plugin) {
        this.plugin = plugin;
        this.wandKey = new NamespacedKey(plugin, "lootrefill_wand");
        this.selections = new ConcurrentHashMap<>();
        this.lastScanResults = new ConcurrentHashMap<>();
    }

    public NamespacedKey getWandKey() {
        return wandKey;
    }

    public ItemStack createWandItem() {
        ItemStack wand = new com.arcraft.lootrefill.util.ItemBuilder(Material.BLAZE_ROD)
                .name("&6&lLootRefill Wand &7(Etapa 4.1)")
                .lore(
                        "&7Herramienta administrativa de selección de región.",
                        "&8• &eClick Izquierdo: &fEstablece Punto A",
                        "&8• &eClick Derecho: &fEstablece Punto B",
                        "",
                        "&7Comandos disponibles:",
                        "  &8» &e/loot region info",
                        "  &8» &e/loot region scan",
                        "  &8» &e/loot region assign",
                        "  &8» &e/loot region clear"
                )
                .build();

        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isWandItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    public RegionSelection getSelection(UUID playerId) {
        return selections.computeIfAbsent(playerId, k -> new RegionSelection());
    }

    public RegionSelection getSelectionOrNull(UUID playerId) {
        return selections.get(playerId);
    }

    public void clearSelection(UUID playerId) {
        selections.remove(playerId);
        lastScanResults.remove(playerId);
    }

    public void setLastScanResult(UUID playerId, RegionScanResult result) {
        if (result != null) {
            lastScanResults.put(playerId, result);
        } else {
            lastScanResults.remove(playerId);
        }
    }

    public RegionScanResult getLastScanResult(UUID playerId) {
        return lastScanResults.get(playerId);
    }

    public void giveWand(Player player) {
        ItemStack wand = createWandItem();
        player.getInventory().addItem(wand);
        MessageUtil.sendMessage(player, "&a¡Has recibido la &6LootRefill Wand&a!");
        MessageUtil.sendMessage(player, "&7Selecciona dos esquinas con click izquierdo (Punto A) y derecho (Punto B).");
    }
}
