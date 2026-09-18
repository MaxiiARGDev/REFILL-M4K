package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.refill.RefillManager;
import com.arcraft.lootrefill.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class RefillConfigMenu extends MenuHolder {

    private final LootRefillPlugin plugin;

    public RefillConfigMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        super(54, "✦ LootRefill - Condiciones de Refill");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        RefillManager refill = plugin.getRefillManager();

        // 1. Sistema Global Activado/Desactivado
        ItemStack toggleSystem = new ItemBuilder(refill.isEnabled() ? Material.LIME_DYE : Material.RED_DYE)
                .name("§6Sistema Global de Refill: " + (refill.isEnabled() ? "§aActivado" : "§cDesactivado"))
                .lore("§7Haz clic para alternar el sistema global.")
                .build();
        setItem(20, toggleSystem, event -> {
            refill.setEnabled(!refill.isEnabled());
            initialize(player);
        });

        // 2. Requiere Contenedor Vacío
        ItemStack requireEmptyItem = new ItemBuilder(refill.isRequireEmpty() ? Material.HOPPER : Material.CHEST)
                .name("§bRequiere Contenedor Vacío: " + (refill.isRequireEmpty() ? "§aActivado" : "§cDesactivado"))
                .lore(
                        "§7Si está activado, solo rellena",
                        "§7contenedores que no contengan ítems.",
                        "",
                        "§b» Haz clic para alternar"
                )
                .build();
        setItem(22, requireEmptyItem, event -> {
            refill.setRequireEmpty(!refill.isRequireEmpty());
            initialize(player);
        });

        // 3. Radio de Seguridad
        ItemStack radiusItem = new ItemBuilder(Material.COMPASS)
                .name("§dRadio de Seguridad: §f" + (int) refill.getNearbyRadius() + " bloques")
                .lore(
                        "§aClick Izquierdo: §7+5 bloques",
                        "§cClick Derecho: §7-5 bloques",
                        "§eShift + Click: §7Editar en chat"
                )
                .build();
        setItem(24, radiusItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player, "&aIngresa el radio en bloques:", input -> {
                    try {
                        refill.setNearbyRadius(Double.parseDouble(input.trim()));
                    } catch (NumberFormatException ignored) {}
                    open(player);
                });
            } else if (event.isLeftClick()) {
                refill.setNearbyRadius(refill.getNearbyRadius() + 5);
                initialize(player);
            } else if (event.isRightClick()) {
                refill.setNearbyRadius(Math.max(0, refill.getNearbyRadius() - 5));
                initialize(player);
            }
        });

        // 4. Requiere Sin Jugadores Cerca
        ItemStack noPlayersItem = new ItemBuilder(refill.isRequireNoPlayersNearby() ? Material.PLAYER_HEAD : Material.ZOMBIE_HEAD)
                .name("§dFiltro de Jugadores Cerca: " + (refill.isRequireNoPlayersNearby() ? "§aActivado" : "§cDesactivado"))
                .lore(
                        "§7Si está activado, no se regenerará",
                        "§7si hay jugadores en el radio de seguridad.",
                        "",
                        "§d» Haz clic para alternar"
                )
                .build();
        setItem(30, noPlayersItem, event -> {
            refill.setRequireNoPlayersNearby(!refill.isRequireNoPlayersNearby());
            initialize(player);
        });

        // Volver
        setBackButton(49, previousMenu);
    }
}
