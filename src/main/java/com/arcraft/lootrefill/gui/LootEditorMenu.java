package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.loot.LootEntry;
import com.arcraft.lootrefill.loot.LootTable;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class LootEditorMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private final LootTable table;
    private int page;

    public LootEditorMenu(LootRefillPlugin plugin, LootTable table, MenuHolder previousMenu) {
        this(plugin, table, previousMenu, 0);
    }

    public LootEditorMenu(LootRefillPlugin plugin, LootTable table, MenuHolder previousMenu, int page) {
        super(54, "✦ Editando: " + table.getDisplayName());
        this.plugin = plugin;
        this.table = table;
        this.previousMenu = previousMenu;
        this.page = page;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);

        // Header de controles (fila 0: slots 1 a 7)
        // 1. Info / Renombrar
        ItemStack renameItem = new ItemBuilder(Material.NAME_TAG)
                .name("§eNombre Visible: " + table.getDisplayName())
                .lore("§7ID: §8" + table.getId(), "§7Haz clic para renombrar en el chat.")
                .build();
        setItem(1, renameItem, event -> {
            plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                    "&aEscribe el nuevo nombre para la tabla &e" + table.getId() + "&a:",
                    input -> {
                        table.setDisplayName(input);
                        MessageUtil.sendMessage(player, "&aNombre cambiado a: " + input);
                        open(player);
                    }
            );
        });

        // 2. Activar / Desactivar
        ItemStack toggleItem = new ItemBuilder(table.isEnabled() ? Material.LIME_DYE : Material.RED_DYE)
                .name(table.isEnabled() ? "§aEstado: Activado" : "§cEstado: Desactivado")
                .lore("§7Haz clic para alternar.")
                .build();
        setItem(2, toggleItem, event -> {
            table.setEnabled(!table.isEnabled());
            initialize(player);
        });

        // 3. Min Items
        ItemStack minItemsItem = new ItemBuilder(Material.HOPPER)
                .name("§bMínimo de objetos: §f" + table.getMinItems())
                .lore("§aClick Izquierdo: §7+1", "§cClick Derecho: §7-1", "§eShift + Click: §7Ingresar por chat")
                .build();
        setItem(3, minItemsItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player, "&aIngresa la cantidad mínima de objetos:", input -> {
                    try {
                        table.setMinItems(Integer.parseInt(input.trim()));
                    } catch (NumberFormatException ignored) {}
                    open(player);
                });
            } else if (event.isLeftClick()) {
                table.setMinItems(table.getMinItems() + 1);
                initialize(player);
            } else if (event.isRightClick()) {
                table.setMinItems(Math.max(0, table.getMinItems() - 1));
                initialize(player);
            }
        });

        // 4. Max Items
        ItemStack maxItemsItem = new ItemBuilder(Material.CHEST)
                .name("§bMáximo de objetos: §f" + table.getMaxItems())
                .lore("§aClick Izquierdo: §7+1", "§cClick Derecho: §7-1", "§eShift + Click: §7Ingresar por chat")
                .build();
        setItem(4, maxItemsItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player, "&aIngresa la cantidad máxima de objetos:", input -> {
                    try {
                        table.setMaxItems(Integer.parseInt(input.trim()));
                    } catch (NumberFormatException ignored) {}
                    open(player);
                });
            } else if (event.isLeftClick()) {
                table.setMaxItems(table.getMaxItems() + 1);
                initialize(player);
            } else if (event.isRightClick()) {
                table.setMaxItems(Math.max(table.getMinItems(), table.getMaxItems() - 1));
                initialize(player);
            }
        });

        // 5. Guardar Cambios
        ItemStack saveItem = new ItemBuilder(Material.EMERALD_BLOCK)
                .name("§aGuardar cambios")
                .lore("§7Guarda todas las modificaciones", "§7en la base de datos.")
                .build();
        setItem(6, saveItem, event -> {
            plugin.getLootManager().saveTableAsync(table);
            MessageUtil.sendMessage(player, "&a¡Cambios guardados con éxito para la tabla &e" + table.getId() + "&a!");
            if (previousMenu != null) {
                previousMenu.open(player);
            } else {
                player.closeInventory();
            }
        });

        // 6. Cancelar
        ItemStack cancelItem = new ItemBuilder(Material.REDSTONE_BLOCK)
                .name("§cCancelar")
                .lore("§7Descarta cambios no guardados", "§7y regresa al menú anterior.")
                .build();
        setItem(7, cancelItem, event -> {
            if (previousMenu != null) {
                previousMenu.open(player);
            } else {
                player.closeInventory();
            }
        });

        // Renderizado de las entradas (LootEntry)
        List<LootEntry> entries = new ArrayList<>(table.getEntries());
        int pageSize = 28;
        int totalPages = (int) Math.ceil((double) Math.max(1, entries.size()) / pageSize);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, entries.size());

        int[] availableSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < availableSlots.length; i++) {
            int slot = availableSlots[i];
            int entryIndex = startIndex + i;

            if (entryIndex < endIndex) {
                LootEntry entry = entries.get(entryIndex);
                ItemStack display = entry.getItem();

                ItemStack entryItem = new ItemBuilder(display)
                        .addLore(
                                "§8-----------------------",
                                "§ePeso / Chance: §f" + entry.getWeight(),
                                "§eCantidad Mínima: §f" + entry.getMinAmount(),
                                "§eCantidad Máxima: §f" + entry.getMaxAmount(),
                                "§eEstado: " + (entry.isEnabled() ? "§aHabilitado" : "§cDeshabilitado"),
                                "",
                                "§aClick Izquierdo: §7+10 Peso",
                                "§bClick Derecho: §7Alternar estado",
                                "§eClick Central: §7Editar valores",
                                "§cShift + Click: §7Eliminar item"
                        )
                        .build();

                setItem(slot, entryItem, event -> {
                    if (event.isShiftClick()) {
                        table.removeEntry(entry.getId());
                        MessageUtil.sendMessage(player, "&cItem removido de la tabla.");
                        initialize(player);
                    } else if (event.isRightClick()) {
                        entry.setEnabled(!entry.isEnabled());
                        initialize(player);
                    } else if (event.isLeftClick()) {
                        entry.setWeight(entry.getWeight() + 10);
                        initialize(player);
                    } else {
                        // Click medio u otro -> abrir configurador del item
                        new LootAddItemMenu(plugin, table, entry, this).open(player);
                    }
                });
            } else {
                setItem(slot, BORDER_GRAY);
            }
        }

        // Botón "+ Agregar item"
        ItemStack addItem = new ItemBuilder(Material.NETHER_STAR)
                .name("§a+ Agregar item")
                .lore("§7Haz clic para configurar y agregar", "§7un nuevo item a esta tabla.", "", "§a» Haz clic para abrir el configurador")
                .build();
        setItem(49, addItem, event -> new LootAddItemMenu(plugin, table, null, this).open(player));

        // Botón Volver
        setBackButton(45, previousMenu);
    }
}
