package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.loot.LootManager;
import com.arcraft.lootrefill.loot.LootTable;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LootTypesMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private int page;

    public LootTypesMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        this(plugin, previousMenu, 0);
    }

    public LootTypesMenu(LootRefillPlugin plugin, MenuHolder previousMenu, int page) {
        super(54, "✦ LootRefill - Tipos de Loot");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
        this.page = page;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);

        LootManager lootManager = plugin.getLootManager();
        List<LootTable> tables = new ArrayList<>(lootManager.getTables());

        int pageSize = 28;
        int totalPages = (int) Math.ceil((double) Math.max(1, tables.size()) / pageSize);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, tables.size());

        // Slots disponibles para items en 54 slots (sin bordes)
        int[] availableSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < availableSlots.length; i++) {
            int slot = availableSlots[i];
            int tableIndex = startIndex + i;

            if (tableIndex < endIndex) {
                LootTable table = tables.get(tableIndex);
                Material icon = getIconForTable(table.getId());

                ItemStack tableItem = new ItemBuilder(icon)
                        .name("§6" + table.getDisplayName() + " §8(§7" + table.getId() + "§8)")
                        .lore(
                                "§7Estado: " + (table.isEnabled() ? "§aActivado" : "§cDesactivado"),
                                "§7Items por cofre: §f" + table.getMinItems() + " - " + table.getMaxItems(),
                                "§7Total entradas: §e" + table.getEntries().size(),
                                "§7Descripción: §f" + table.getDescription(),
                                "",
                                "§eClick Izquierdo: §7Editar tabla",
                                "§bClick Derecho: §7Alternar estado",
                                "§cShift + Click: §7Eliminar tabla"
                        )
                        .build();

                setItem(slot, tableItem, event -> {
                    if (event.isShiftClick()) {
                        lootManager.deleteTable(table.getId());
                        MessageUtil.sendMessage(player, "&cSe ha eliminado la tabla &e" + table.getId() + "&c.");
                        initialize(player);
                    } else if (event.isRightClick()) {
                        table.setEnabled(!table.isEnabled());
                        lootManager.saveTableAsync(table);
                        MessageUtil.sendMessage(player, "&7Estado de &e" + table.getId() + "&7: " + (table.isEnabled() ? "&aActivado" : "&cDesactivado"));
                        initialize(player);
                    } else {
                        new LootEditorMenu(plugin, table, this).open(player);
                    }
                });
            } else {
                setItem(slot, BORDER_GRAY);
            }
        }

        // Botón Anterior
        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.ARROW).name("§e« Página anterior (" + page + ")").build();
            setItem(48, prev, event -> {
                page--;
                initialize(player);
            });
        } else {
            setItem(48, BORDER_BLACK);
        }

        // Botón Siguiente
        if (page < totalPages - 1) {
            ItemStack next = new ItemBuilder(Material.ARROW).name("§ePágina siguiente (" + (page + 2) + ") »").build();
            setItem(50, next, event -> {
                page++;
                initialize(player);
            });
        } else {
            setItem(50, BORDER_BLACK);
        }

        // Botón "+ Crear Loot Table"
        ItemStack createItem = new ItemBuilder(Material.EMERALD)
                .name("§a+ Crear Loot Table")
                .lore("§7Haz clic para crear una nueva", "§7tabla de recompensas.", "", "§a» Haz clic para comenzar")
                .build();
        setItem(52, createItem, event -> {
            plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                    "&aEscribe el ID único para la nueva tabla de loot:",
                    input -> {
                        String id = input.toLowerCase().replaceAll("[^a-z0-9_-]", "");
                        if (id.isEmpty()) {
                            MessageUtil.sendMessage(player, "&cID inválido. Usa solo letras y números.");
                            open(player);
                            return;
                        }
                        if (lootManager.tableExists(id)) {
                            MessageUtil.sendMessage(player, "&cYa existe una tabla con el ID &e" + id + "&c.");
                            open(player);
                            return;
                        }
                        LootTable newTable = new LootTable(id, id.toUpperCase(), "Tabla personalizada", true, 1, 4);
                        lootManager.registerTable(newTable, true);
                        MessageUtil.sendMessage(player, "&aTabla &e" + id + " &acreada con éxito. Abriendo editor...");
                        new LootEditorMenu(plugin, newTable, this).open(player);
                    }
            );
        });

        // Botón Volver
        setBackButton(45, previousMenu);
    }

    private Material getIconForTable(String id) {
        String lower = id.toLowerCase();
        if (lower.contains("food")) return Material.COOKED_BEEF;
        if (lower.contains("medical") || lower.contains("med")) return Material.POTION;
        if (lower.contains("military") || lower.contains("weapon")) return Material.IRON_SWORD;
        if (lower.contains("high") || lower.contains("diamond")) return Material.DIAMOND;
        if (lower.contains("rare")) return Material.EMERALD;
        return Material.CHEST;
    }
}
