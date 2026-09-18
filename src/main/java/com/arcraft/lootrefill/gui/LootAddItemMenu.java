package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.loot.LootEntry;
import com.arcraft.lootrefill.loot.LootTable;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class LootAddItemMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private final LootTable table;
    private final LootEntry existingEntry;
    private ItemStack selectedItem;
    private int weight;
    private int minAmount;
    private int maxAmount;

    public LootAddItemMenu(LootRefillPlugin plugin, LootTable table, LootEntry existingEntry, MenuHolder previousMenu) {
        super(54, existingEntry != null ? "✦ Configurando Ítem" : "✦ Agregar Ítem");
        this.plugin = plugin;
        this.table = table;
        this.existingEntry = existingEntry;
        this.previousMenu = previousMenu;

        if (existingEntry != null) {
            this.selectedItem = existingEntry.getItem();
            this.weight = existingEntry.getWeight();
            this.minAmount = existingEntry.getMinAmount();
            this.maxAmount = existingEntry.getMaxAmount();
        } else {
            this.selectedItem = new ItemStack(Material.STONE);
            this.weight = 100;
            this.minAmount = 1;
            this.maxAmount = 1;
        }
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        // Slot especial 13: El item seleccionado
        ItemStack displayItem = new ItemBuilder(selectedItem)
                .addLore(
                        "§8-----------------",
                        "§7Haz clic sobre un ítem de tu",
                        "§7inventario inferior para seleccionarlo."
                )
                .build();
        setItem(13, displayItem);

        // Slot 29: Peso / Chance
        ItemStack weightItem = new ItemBuilder(Material.ANVIL)
                .name("§6Peso / Probabilidad: §e" + weight)
                .lore(
                        "§7Determina qué tan probable es",
                        "§7que este ítem sea elegido.",
                        "",
                        "§aClick Izquierdo: §7+10",
                        "§cClick Derecho: §7-10",
                        "§eShift + Click: §7Ingresar valor exacto"
                )
                .build();
        setItem(29, weightItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player, "&aIngresa el nuevo peso para el item:", input -> {
                    try {
                        this.weight = Math.max(1, Integer.parseInt(input.trim()));
                    } catch (NumberFormatException ignored) {}
                    open(player);
                });
            } else if (event.isLeftClick()) {
                this.weight += 10;
                initialize(player);
            } else if (event.isRightClick()) {
                this.weight = Math.max(1, this.weight - 10);
                initialize(player);
            }
        });

        // Slot 31: Min Amount
        ItemStack minAmountItem = new ItemBuilder(Material.HOPPER)
                .name("§bCantidad Mínima: §f" + minAmount)
                .lore(
                        "§aClick Izquierdo: §7+1",
                        "§cClick Derecho: §7-1",
                        "§eShift + Click: §7Ingresar valor"
                )
                .build();
        setItem(31, minAmountItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player, "&aIngresa la cantidad mínima:", input -> {
                    try {
                        this.minAmount = Math.max(1, Integer.parseInt(input.trim()));
                        if (this.maxAmount < this.minAmount) this.maxAmount = this.minAmount;
                    } catch (NumberFormatException ignored) {}
                    open(player);
                });
            } else if (event.isLeftClick()) {
                this.minAmount++;
                if (this.maxAmount < this.minAmount) this.maxAmount = this.minAmount;
                initialize(player);
            } else if (event.isRightClick()) {
                this.minAmount = Math.max(1, this.minAmount - 1);
                initialize(player);
            }
        });

        // Slot 33: Max Amount
        ItemStack maxAmountItem = new ItemBuilder(Material.CHEST)
                .name("§bCantidad Máxima: §f" + maxAmount)
                .lore(
                        "§aClick Izquierdo: §7+1",
                        "§cClick Derecho: §7-1",
                        "§eShift + Click: §7Ingresar valor"
                )
                .build();
        setItem(33, maxAmountItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player, "&aIngresa la cantidad máxima:", input -> {
                    try {
                        this.maxAmount = Math.max(this.minAmount, Integer.parseInt(input.trim()));
                    } catch (NumberFormatException ignored) {}
                    open(player);
                });
            } else if (event.isLeftClick()) {
                this.maxAmount++;
                initialize(player);
            } else if (event.isRightClick()) {
                this.maxAmount = Math.max(this.minAmount, this.maxAmount - 1);
                initialize(player);
            }
        });

        // Slot 48: Confirmar
        ItemStack confirmItem = new ItemBuilder(Material.EMERALD_BLOCK)
                .name("§aConfirmar y Guardar Ítem")
                .lore("§7Agrega este ítem con la", "§7configuración establecida.")
                .build();
        setItem(48, confirmItem, event -> {
            if (selectedItem == null || selectedItem.getType().isAir()) {
                MessageUtil.sendMessage(player, "&cDebes seleccionar un ítem válido primero.");
                return;
            }

            if (existingEntry != null) {
                existingEntry.setItem(selectedItem);
                existingEntry.setWeight(weight);
                existingEntry.setMinAmount(minAmount);
                existingEntry.setMaxAmount(maxAmount);
            } else {
                LootEntry entry = new LootEntry(UUID.randomUUID(), selectedItem, weight, minAmount, maxAmount, true);
                table.addEntry(entry);
            }

            MessageUtil.sendMessage(player, "&a¡Ítem registrado con éxito en la tabla &e" + table.getId() + "&a!");
            if (previousMenu != null) {
                previousMenu.open(player);
            } else {
                player.closeInventory();
            }
        });

        // Slot 50: Cancelar
        ItemStack cancelItem = new ItemBuilder(Material.REDSTONE_BLOCK)
                .name("§cCancelar")
                .lore("§7Descarta la configuración del ítem.")
                .build();
        setItem(50, cancelItem, event -> {
            if (previousMenu != null) {
                previousMenu.open(player);
            } else {
                player.closeInventory();
            }
        });
    }

    @Override
    public boolean allowPlayerInventoryClick(InventoryClickEvent event) {
        // Permitir al jugador hacer click sobre un item de su inventario para seleccionarlo
        if (event.getCurrentItem() != null && !event.getCurrentItem().getType().isAir()) {
            this.selectedItem = event.getCurrentItem().clone();
            this.selectedItem.setAmount(1);
            if (event.getWhoClicked() instanceof Player p) {
                initialize(p);
                MessageUtil.sendMessage(p, "&aÍtem seleccionado: &e" + selectedItem.getType().name());
            }
        }
        event.setCancelled(true);
        return false;
    }
}
