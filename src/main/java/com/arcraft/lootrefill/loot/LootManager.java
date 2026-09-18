package com.arcraft.lootrefill.loot;

import com.arcraft.lootrefill.LootRefillPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LootManager {

    private final LootRefillPlugin plugin;
    private final LootTableStorage storage;
    private final Map<String, LootTable> tables;

    public LootManager(LootRefillPlugin plugin, LootTableStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.tables = new HashMap<>();
    }

    public void load() {
        tables.clear();
        Map<String, LootTable> loaded = storage.loadAllTables();
        tables.putAll(loaded);

        if (tables.isEmpty()) {
            createDefaultTables();
        }

        plugin.getLogger().info("Se cargaron " + tables.size() + " tablas de loot.");
    }

    private void createDefaultTables() {
        // Tablas por defecto para el servidor Arcraft
        LootTable common = new LootTable("common", "§7Común", "Items básicos de supervivencia", true, 2, 5);
        common.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.BREAD), 100, 1, 3, true));
        common.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.APPLE), 80, 1, 2, true));
        common.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.WOODEN_PICKAXE), 50, 1, 1, true));
        common.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.TORCH), 90, 4, 8, true));
        registerTable(common, true);

        LootTable food = new LootTable("food", "§aComida", "Raciones y alimentos variados", true, 3, 6);
        food.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.COOKED_BEEF), 100, 2, 5, true));
        food.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.GOLDEN_CARROT), 40, 1, 2, true));
        food.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.COOKED_PORKCHOP), 90, 2, 4, true));
        registerTable(food, true);

        LootTable medical = new LootTable("medical", "§cMédico", "Insumos de curación y salud", true, 1, 3);
        medical.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.POTION), 70, 1, 1, true));
        medical.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.PAPER), 100, 1, 3, true));
        registerTable(medical, true);

        LootTable military = new LootTable("military", "§6Militar", "Armamento y municiones tácticas", true, 2, 4);
        military.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.IRON_SWORD), 80, 1, 1, true));
        military.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.CROSSBOW), 60, 1, 1, true));
        military.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.ARROW), 100, 8, 24, true));
        registerTable(military, true);

        LootTable highTier = new LootTable("high_tier", "§dAlto Nivel", "Equipamiento de alta gama", true, 1, 3);
        highTier.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.DIAMOND), 50, 1, 2, true));
        highTier.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.IRON_CHESTPLATE), 70, 1, 1, true));
        registerTable(highTier, true);

        LootTable rare = new LootTable("rare", "§eRaro", "Botín exclusivo y valioso", true, 1, 2);
        rare.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE), 20, 1, 1, true));
        rare.addEntry(new LootEntry(UUID.randomUUID(), new ItemStack(Material.EMERALD), 60, 2, 6, true));
        registerTable(rare, true);
    }

    public LootTable getTable(String id) {
        if (id == null) return null;
        return tables.get(id.toLowerCase());
    }

    public Collection<LootTable> getTables() {
        return Collections.unmodifiableCollection(tables.values());
    }

    public void registerTable(LootTable table, boolean saveAsync) {
        if (table == null) return;
        tables.put(table.getId().toLowerCase(), table);
        if (saveAsync) {
            saveTableAsync(table);
        } else {
            storage.saveTable(table);
        }
    }

    public void saveTableAsync(LootTable table) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.saveTable(table));
    }

    public void deleteTable(String id) {
        if (id == null) return;
        tables.remove(id.toLowerCase());
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> storage.deleteTable(id));
    }

    public boolean tableExists(String id) {
        return id != null && tables.containsKey(id.toLowerCase());
    }

    public List<String> getTableIds() {
        return new ArrayList<>(tables.keySet());
    }
}
