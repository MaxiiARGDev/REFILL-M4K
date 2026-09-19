package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.pool.LootPool;
import com.arcraft.lootrefill.pool.LootPoolManager;
import com.arcraft.lootrefill.pool.PoolSelectionMode;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class LootPoolsMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private int page;

    public LootPoolsMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        this(plugin, previousMenu, 0);
    }

    public LootPoolsMenu(LootRefillPlugin plugin, MenuHolder previousMenu, int page) {
        super(54, "✦ LootRefill - Loot Pools");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
        this.page = page;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);

        LootPoolManager poolManager = plugin.getLootPoolManager();
        List<LootPool> pools = new ArrayList<>(poolManager.getAllPools());
        pools.sort(Comparator.comparing(LootPool::getId, String.CASE_INSENSITIVE_ORDER));

        int pageSize = 28;
        int totalPages = (int) Math.ceil((double) Math.max(1, pools.size()) / pageSize);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, pools.size());

        int[] availableSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < availableSlots.length; i++) {
            int slot = availableSlots[i];
            int poolIndex = startIndex + i;

            if (poolIndex < endIndex) {
                LootPool pool = pools.get(poolIndex);
                Material icon = pool.isEnabled() ? Material.ENDER_CHEST : Material.CHEST_MINECART;

                int containersCount = poolManager.getContainerCountUsingPool(pool.getId());

                ItemStack poolItem = new ItemBuilder(icon)
                        .name("§d" + pool.getName() + " §8(§7" + pool.getId() + "§8)")
                        .lore(
                                "§7Estado: " + (pool.isEnabled() ? "§aActivado" : "§cDesactivado"),
                                "§7Modo: §e" + pool.getSelectionMode().name(),
                                "§7Rolls: §f" + pool.getMinRolls() + " - " + pool.getMaxRolls(),
                                "§7Duplicados: §f" + (pool.isAllowDuplicates() ? "§aSÍ" : "§cNO"),
                                "§7Tablas asociadas: §e" + pool.getEntries().size() + " §8(Peso: " + pool.getTotalWeight() + ")",
                                "§7Contenedores asignados: §b" + containersCount,
                                "",
                                "§eClick Izquierdo: §7Editar pool",
                                "§aShift + Click Izq: §7Gestionar tablas",
                                "§bClick Derecho: §7Alternar estado",
                                "§cShift + Click Der: §7Eliminar pool"
                        )
                        .build();

                setItem(slot, poolItem, event -> {
                    if (event.isShiftClick() && event.isRightClick()) {
                        // Diálogo de confirmación seguro
                        int count = poolManager.getContainerCountUsingPool(pool.getId());
                        List<String> desc = List.of(
                                "§7Pool: §e" + pool.getId() + " §8(§f" + pool.getName() + "§8)",
                                "§7Contenedores afectados: §c" + count,
                                "",
                                "§eEsta acción eliminará el pool y desvinculará",
                                "§etodos los contenedores asociados a él.",
                                "§7(Sus tablas de loot individuales se conservan)."
                        );

                        new ConfirmationMenu(
                                "✦ ¿Eliminar Pool " + pool.getId() + "?",
                                "§cEliminar Pool: " + pool.getName(),
                                desc,
                                this,
                                confirmPlayer -> {
                                    int unlinked = poolManager.deletePool(pool.getId());
                                    if (unlinked >= 0) {
                                        MessageUtil.sendMessage(confirmPlayer, "&cPool &e" + pool.getId() + " &celiminado con éxito. Se desvincularon &e" + unlinked + " &ccontenedores.");
                                    } else {
                                        MessageUtil.sendMessage(confirmPlayer, "&cEl pool ya no existe.");
                                    }
                                    open(confirmPlayer);
                                },
                                cancelPlayer -> {
                                    MessageUtil.sendMessage(cancelPlayer, "&7Eliminación cancelada.");
                                    open(cancelPlayer);
                                }
                        ).open(player);
                    } else if (event.isShiftClick() && event.isLeftClick()) {
                        // Ir a la gestión de tablas del pool
                        new LootPoolMembersMenu(plugin, pool, this).open(player);
                    } else if (event.isRightClick()) {
                        // Alternar enabled
                        pool.setEnabled(!pool.isEnabled());
                        poolManager.savePool(pool);
                        MessageUtil.sendMessage(player, "&7Estado de pool &e" + pool.getId() + "&7: " + (pool.isEnabled() ? "&aActivado" : "&cDesactivado"));
                        initialize(player);
                    } else {
                        // Editar propiedades del pool
                        new LootPoolEditorMenu(plugin, pool, this).open(player);
                    }
                });
            } else {
                setItem(slot, BORDER_GRAY);
            }
        }

        // Paginación anterior
        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.ARROW).name("§e« Página anterior (" + page + ")").build();
            setItem(48, prev, event -> {
                page--;
                initialize(player);
            });
        } else {
            setItem(48, BORDER_BLACK);
        }

        // Paginación siguiente
        if (page < totalPages - 1) {
            ItemStack next = new ItemBuilder(Material.ARROW).name("§ePágina siguiente (" + (page + 2) + ") »").build();
            setItem(50, next, event -> {
                page++;
                initialize(player);
            });
        } else {
            setItem(50, BORDER_BLACK);
        }

        // Botón "+ Crear Loot Pool"
        ItemStack createItem = new ItemBuilder(Material.EMERALD)
                .name("§a+ Crear Loot Pool")
                .lore("§7Haz clic para crear un nuevo", "§7Loot Pool dinámico.", "", "§a» Haz clic para comenzar")
                .build();
        setItem(52, createItem, event -> {
            plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                    "&aEscribe el ID único para el nuevo Loot Pool (solo letras, números y _):",
                    input -> {
                        String id = input.toLowerCase().replaceAll("[^a-z0-9_-]", "").trim();
                        if (id.isEmpty()) {
                            MessageUtil.sendMessage(player, "&cID de pool inválido. Usa letras, números y guiones.");
                            open(player);
                            return;
                        }
                        if (poolManager.poolExists(id)) {
                            MessageUtil.sendMessage(player, "&cYa existe un Loot Pool con ID &e" + id + "&c.");
                            open(player);
                            return;
                        }

                        plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                                "&aEscribe el nombre visible para el pool &e" + id + "&a:",
                                nameInput -> {
                                    String name = nameInput.trim();
                                    if (name.isEmpty()) {
                                        name = id;
                                    }
                                    LootPool newPool = new LootPool(id, name, PoolSelectionMode.SINGLE_RANDOM, 1, 1, false, true);
                                    poolManager.savePool(newPool);
                                    MessageUtil.sendMessage(player, "&aLoot Pool &e" + id + " &acreado con éxito. Abriendo editor...");
                                    new LootPoolEditorMenu(plugin, newPool, this).open(player);
                                }
                        );
                    }
            );
        });

        // Botón Volver
        setBackButton(45, previousMenu);
    }
}
