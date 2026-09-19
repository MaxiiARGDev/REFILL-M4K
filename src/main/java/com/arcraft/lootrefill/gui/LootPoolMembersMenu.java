package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.loot.LootTable;
import com.arcraft.lootrefill.pool.LootPool;
import com.arcraft.lootrefill.pool.LootPoolEntry;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LootPoolMembersMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private final LootPool pool;
    private int page;

    public LootPoolMembersMenu(LootRefillPlugin plugin, LootPool pool, MenuHolder previousMenu) {
        this(plugin, pool, previousMenu, 0);
    }

    public LootPoolMembersMenu(LootRefillPlugin plugin, LootPool pool, MenuHolder previousMenu, int page) {
        super(54, "✦ Tablas: " + pool.getName());
        this.plugin = plugin;
        this.pool = pool;
        this.previousMenu = previousMenu;
        this.page = page;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);

        List<LootPoolEntry> entries = new ArrayList<>(pool.getEntries());
        entries.sort(Comparator.comparing(LootPoolEntry::getTableId, String.CASE_INSENSITIVE_ORDER));

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
                LootPoolEntry entry = entries.get(entryIndex);
                LootTable table = plugin.getLootManager().getTable(entry.getTableId());

                boolean tableExists = table != null;
                boolean tableEnabled = tableExists && table.isEnabled();

                Material icon = tableExists ? Material.CHEST : Material.BARRIER;
                String displayName = tableExists ? table.getDisplayName() : "§c[Tabla No Encontrada]";

                double percentage = pool.getTotalWeight() > 0 ? (entry.getWeight() * 100.0) / pool.getTotalWeight() : 0.0;

                ItemStack entryItem = new ItemBuilder(icon)
                        .name("§6" + displayName + " §8(§e" + entry.getTableId() + "§8)")
                        .lore(
                                "§7Peso relativo: §f" + entry.getWeight(),
                                "§7Probabilidad aproximada: §a" + String.format("%.1f", percentage) + "%",
                                "§7Estado de la tabla: " + (tableEnabled ? "§aActivada" : (tableExists ? "§cDesactivada" : "§4Inexistente")),
                                "",
                                "§eClick Izquierdo: §7Modificar peso por chat",
                                "§bClick Derecho: §7+1 peso / Shift+Der: -1 peso",
                                "§cShift + Click Izq: §7Eliminar de este pool"
                        )
                        .build();

                setItem(slot, entryItem, event -> {
                    if (event.isShiftClick() && event.isLeftClick()) {
                        // Diálogo de confirmación para remover membresía
                        List<String> desc = List.of(
                                "§7Tabla: §e" + entry.getTableId(),
                                "§7Pool: §d" + pool.getName(),
                                "",
                                "§eEsta acción quitará la tabla del pool.",
                                "§7(La LootTable global seguirá existiendo)."
                        );

                        new ConfirmationMenu(
                                "✦ ¿Quitar tabla del pool?",
                                "§cQuitar " + entry.getTableId() + " del pool",
                                desc,
                                this,
                                confirmPlayer -> {
                                    pool.removeEntry(entry.getTableId());
                                    plugin.getLootPoolManager().savePool(pool);
                                    MessageUtil.sendMessage(confirmPlayer, "&cTabla &e" + entry.getTableId() + " &cremovida del pool &e" + pool.getId() + "&c.");
                                    open(confirmPlayer);
                                },
                                cancelPlayer -> {
                                    MessageUtil.sendMessage(cancelPlayer, "&7Operación cancelada.");
                                    open(cancelPlayer);
                                }
                        ).open(player);
                    } else if (event.isRightClick()) {
                        int delta = event.isShiftClick() ? -1 : 1;
                        int newWeight = Math.max(1, entry.getWeight() + delta);
                        entry.setWeight(newWeight);
                        plugin.getLootPoolManager().savePool(pool);
                        initialize(player);
                    } else if (event.isLeftClick()) {
                        plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                                "&aIngresa el nuevo peso relativo para &e" + entry.getTableId() + " &a(&e> 0&a):",
                                input -> {
                                    try {
                                        int weight = Integer.parseInt(input.trim());
                                        if (weight > 0) {
                                            entry.setWeight(weight);
                                            plugin.getLootPoolManager().savePool(pool);
                                            MessageUtil.sendMessage(player, "&aPeso para &e" + entry.getTableId() + " &aactualizado a: &f" + weight);
                                        } else {
                                            MessageUtil.sendMessage(player, "&cEl peso debe ser mayor a 0.");
                                        }
                                    } catch (NumberFormatException e) {
                                        MessageUtil.sendMessage(player, "&cDebes ingresar un número entero válido.");
                                    }
                                    open(player);
                                }
                        );
                    }
                });
            } else {
                setItem(slot, BORDER_GRAY);
            }
        }

        // Paginación
        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.ARROW).name("§e« Página anterior (" + page + ")").build();
            setItem(48, prev, event -> {
                page--;
                initialize(player);
            });
        } else {
            setItem(48, BORDER_BLACK);
        }

        if (page < totalPages - 1) {
            ItemStack next = new ItemBuilder(Material.ARROW).name("§ePágina siguiente (" + (page + 2) + ") »").build();
            setItem(50, next, event -> {
                page++;
                initialize(player);
            });
        } else {
            setItem(50, BORDER_BLACK);
        }

        // Botón "+ Añadir Loot Table"
        ItemStack addItem = new ItemBuilder(Material.EMERALD)
                .name("§a+ Añadir Tabla al Pool")
                .lore("§7Haz clic para seleccionar una tabla", "§7existente e incorporar al pool.", "", "§a» Haz clic para añadir")
                .build();
        setItem(52, addItem, event -> openSelectTableMenu(player));

        // Botón Volver
        setBackButton(45, previousMenu);
    }

    /**
     * Sub-menú para seleccionar una tabla de loot existente para agregar al pool.
     */
    private void openSelectTableMenu(Player player) {
        new SelectTableToAddMenu(plugin, pool, this, 0).open(player);
    }

    /**
     * Menú interno para listar y elegir qué tabla agregar.
     */
    private static class SelectTableToAddMenu extends MenuHolder {
        private final LootRefillPlugin plugin;
        private final LootPool pool;
        private final MenuHolder parentMenu;
        private int page;

        public SelectTableToAddMenu(LootRefillPlugin plugin, LootPool pool, MenuHolder parentMenu, int page) {
            super(54, "✦ Selecciona Tabla para Pool");
            this.plugin = plugin;
            this.pool = pool;
            this.parentMenu = parentMenu;
            this.page = page;
        }

        @Override
        public void initialize(Player player) {
            fillBorders(BORDER_BLACK);

            // Tablas globales que aún NO están en el pool
            List<LootTable> availableTables = plugin.getLootManager().getTables().stream()
                    .filter(t -> pool.getEntry(t.getId()) == null)
                    .sorted(Comparator.comparing(LootTable::getId, String.CASE_INSENSITIVE_ORDER))
                    .toList();

            int pageSize = 28;
            int totalPages = (int) Math.ceil((double) Math.max(1, availableTables.size()) / pageSize);
            if (page >= totalPages) page = totalPages - 1;
            if (page < 0) page = 0;

            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, availableTables.size());

            int[] slots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43
            };

            for (int i = 0; i < slots.length; i++) {
                int slot = slots[i];
                int tableIdx = startIndex + i;

                if (tableIdx < endIndex) {
                    LootTable table = availableTables.get(tableIdx);
                    ItemStack item = new ItemBuilder(Material.CHEST)
                            .name("§6" + table.getDisplayName() + " §8(§7" + table.getId() + "§8)")
                            .lore(
                                    "§7Items: §f" + table.getMinItems() + " - " + table.getMaxItems(),
                                    "§7Entradas: §f" + table.getEntries().size(),
                                    "",
                                    "§a» Haz clic para seleccionar y pedir peso"
                            )
                            .build();

                    setItem(slot, item, event -> {
                        plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                                "&aIngresa el peso relativo para la tabla &e" + table.getId() + " &aen el pool &e" + pool.getId() + " &a(&e> 0&a, sugerido: 10 a 50):",
                                input -> {
                                    try {
                                        int weight = Integer.parseInt(input.trim());
                                        if (weight <= 0) {
                                            MessageUtil.sendMessage(player, "&cEl peso debe ser un número entero mayor a 0.");
                                            parentMenu.open(player);
                                            return;
                                        }

                                        // Validar que no se haya agregado concurrentemente
                                        if (pool.getEntry(table.getId()) != null) {
                                            MessageUtil.sendMessage(player, "&cEsta tabla ya forma parte del pool.");
                                            parentMenu.open(player);
                                            return;
                                        }

                                        pool.addEntry(new LootPoolEntry(pool.getId(), table.getId(), weight));
                                        plugin.getLootPoolManager().savePool(pool);
                                        MessageUtil.sendMessage(player, "&aTabla &e" + table.getId() + " &aagregada con éxito al pool con peso &f" + weight + "&a.");
                                    } catch (NumberFormatException e) {
                                        MessageUtil.sendMessage(player, "&cDebes ingresar un número entero válido.");
                                    }
                                    parentMenu.open(player);
                                }
                        );
                    });
                } else {
                    setItem(slot, BORDER_GRAY);
                }
            }

            if (page > 0) {
                setItem(48, new ItemBuilder(Material.ARROW).name("§e« Anterior").build(), event -> {
                    page--;
                    initialize(player);
                });
            } else {
                setItem(48, BORDER_BLACK);
            }

            if (page < totalPages - 1) {
                setItem(50, new ItemBuilder(Material.ARROW).name("§eSiguiente »").build(), event -> {
                    page++;
                    initialize(player);
                });
            } else {
                setItem(50, BORDER_BLACK);
            }

            setBackButton(45, parentMenu);
        }
    }
}
