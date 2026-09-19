package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.pool.LootPool;
import com.arcraft.lootrefill.pool.PoolSelectionMode;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LootPoolEditorMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private final LootPool pool;

    public LootPoolEditorMenu(LootRefillPlugin plugin, LootPool pool, MenuHolder previousMenu) {
        super(45, "✦ Editando Pool: " + pool.getName());
        this.plugin = plugin;
        this.pool = pool;
        this.previousMenu = previousMenu;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        // Slot 10: Nombre
        ItemStack nameItem = new ItemBuilder(Material.NAME_TAG)
                .name("§eNombre: §f" + pool.getName())
                .lore(
                        "§7ID: §8" + pool.getId(),
                        "",
                        "§e» Haz clic para renombrar en el chat"
                )
                .build();
        setItem(10, nameItem, event -> {
            plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                    "&aEscribe el nuevo nombre para el pool &e" + pool.getId() + "&a:",
                    input -> {
                        String name = input.trim();
                        if (name.isEmpty()) {
                            MessageUtil.sendMessage(player, "&cEl nombre no puede estar vacío.");
                        } else {
                            pool.setName(name);
                            plugin.getLootPoolManager().savePool(pool);
                            MessageUtil.sendMessage(player, "&aNombre del pool actualizado a: &f" + name);
                        }
                        open(player);
                    }
            );
        });

        // Slot 12: Estado (Enabled / Disabled)
        ItemStack toggleItem = new ItemBuilder(pool.isEnabled() ? Material.LIME_DYE : Material.RED_DYE)
                .name(pool.isEnabled() ? "§aEstado: Activado" : "§cEstado: Desactivado")
                .lore(
                        "§7Indica si este pool puede usarse para generar loot.",
                        "",
                        "§b» Haz clic para alternar"
                )
                .build();
        setItem(12, toggleItem, event -> {
            pool.setEnabled(!pool.isEnabled());
            plugin.getLootPoolManager().savePool(pool);
            initialize(player);
        });

        // Slot 14: Selection Mode
        ItemStack modeItem = new ItemBuilder(Material.REPEATER)
                .name("§6Modo de Selección: §e" + pool.getSelectionMode().name())
                .lore(
                        "§7SINGLE_RANDOM: 1 tabla por refill.",
                        "§7MIXED_RANDOM: múltiples tablas (min-max rolls).",
                        "",
                        "§6» Haz clic para alternar modo"
                )
                .build();
        setItem(14, modeItem, event -> {
            if (pool.getSelectionMode() == PoolSelectionMode.SINGLE_RANDOM) {
                pool.setSelectionMode(PoolSelectionMode.MIXED_RANDOM);
                // Asegurar rolls razonables para MIXED_RANDOM
                if (pool.getMaxRolls() <= 1) {
                    pool.setMinRolls(2);
                    pool.setMaxRolls(3);
                }
            } else {
                pool.setSelectionMode(PoolSelectionMode.SINGLE_RANDOM);
                pool.setMinRolls(1);
                pool.setMaxRolls(1);
            }
            plugin.getLootPoolManager().savePool(pool);
            initialize(player);
        });

        // Slot 16: Duplicados (solo relevante en MIXED_RANDOM)
        ItemStack duplicatesItem = new ItemBuilder(pool.isAllowDuplicates() ? Material.GLOWSTONE_DUST : Material.GUNPOWDER)
                .name("§dPermitir Duplicados: " + (pool.isAllowDuplicates() ? "§aSÍ" : "§cNO"))
                .lore(
                        "§7Aplica para modo MIXED_RANDOM.",
                        "§7SÍ: con reemplazo (mismo ítem puede salir varias veces).",
                        "§7NO: sin reemplazo (sin repetir tablas).",
                        "",
                        "§d» Haz clic para alternar"
                )
                .build();
        setItem(16, duplicatesItem, event -> {
            pool.setAllowDuplicates(!pool.isAllowDuplicates());
            plugin.getLootPoolManager().savePool(pool);
            initialize(player);
        });

        // Slot 20: Min Rolls
        ItemStack minRollsItem = new ItemBuilder(Material.HOPPER)
                .name("§bMínimo de Rolls: §f" + pool.getMinRolls())
                .lore(
                        "§aClick Izquierdo: §7+1",
                        "§cClick Derecho: §7-1",
                        "§eShift + Click: §7Ingresar por chat"
                )
                .build();
        setItem(20, minRollsItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                        "&aIngresa la cantidad mínima de rolls (&e>= 1&a):",
                        input -> {
                            try {
                                int val = Integer.parseInt(input.trim());
                                if (val >= 1 && val <= pool.getMaxRolls()) {
                                    pool.setMinRolls(val);
                                    plugin.getLootPoolManager().savePool(pool);
                                    MessageUtil.sendMessage(player, "&aMin rolls actualizado a: &f" + val);
                                } else {
                                    MessageUtil.sendMessage(player, "&cValor inválido. Debe ser >= 1 y <= max rolls (" + pool.getMaxRolls() + ").");
                                }
                            } catch (NumberFormatException e) {
                                MessageUtil.sendMessage(player, "&cDebes ingresar un número entero.");
                            }
                            open(player);
                        }
                );
            } else if (event.isLeftClick()) {
                if (pool.getMinRolls() < pool.getMaxRolls()) {
                    pool.setMinRolls(pool.getMinRolls() + 1);
                    plugin.getLootPoolManager().savePool(pool);
                    initialize(player);
                }
            } else if (event.isRightClick()) {
                if (pool.getMinRolls() > 1) {
                    pool.setMinRolls(pool.getMinRolls() - 1);
                    plugin.getLootPoolManager().savePool(pool);
                    initialize(player);
                }
            }
        });

        // Slot 24: Max Rolls
        ItemStack maxRollsItem = new ItemBuilder(Material.DISPENSER)
                .name("§bMáximo de Rolls: §f" + pool.getMaxRolls())
                .lore(
                        "§aClick Izquierdo: §7+1",
                        "§cClick Derecho: §7-1",
                        "§eShift + Click: §7Ingresar por chat"
                )
                .build();
        setItem(24, maxRollsItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                        "&aIngresa la cantidad máxima de rolls (&e>= " + pool.getMinRolls() + "&a):",
                        input -> {
                            try {
                                int val = Integer.parseInt(input.trim());
                                if (val >= pool.getMinRolls()) {
                                    pool.setMaxRolls(val);
                                    plugin.getLootPoolManager().savePool(pool);
                                    MessageUtil.sendMessage(player, "&aMax rolls actualizado a: &f" + val);
                                } else {
                                    MessageUtil.sendMessage(player, "&cValor inválido. Debe ser >= min rolls (" + pool.getMinRolls() + ").");
                                }
                            } catch (NumberFormatException e) {
                                MessageUtil.sendMessage(player, "&cDebes ingresar un número entero.");
                            }
                            open(player);
                        }
                );
            } else if (event.isLeftClick()) {
                pool.setMaxRolls(pool.getMaxRolls() + 1);
                plugin.getLootPoolManager().savePool(pool);
                initialize(player);
            } else if (event.isRightClick()) {
                if (pool.getMaxRolls() > pool.getMinRolls()) {
                    pool.setMaxRolls(pool.getMaxRolls() - 1);
                    plugin.getLootPoolManager().savePool(pool);
                    initialize(player);
                }
            }
        });

        // Slot 31: Gestión de Tablas / Members
        ItemStack membersItem = new ItemBuilder(Material.CHEST)
                .name("§6Gestionar Tablas del Pool §8(§e" + pool.getEntries().size() + "§8)")
                .lore(
                        "§7Peso total acumulado: §f" + pool.getTotalWeight(),
                        "",
                        "§e» Haz clic para ver, agregar o remover tablas"
                )
                .build();
        setItem(31, membersItem, event -> new LootPoolMembersMenu(plugin, pool, this).open(player));

        // Botón Volver (Slot 36)
        setBackButton(36, previousMenu);
    }
}
