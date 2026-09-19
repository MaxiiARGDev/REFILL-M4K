package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.util.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * GUI genérica, reutilizable y segura para confirmación de acciones administrativas críticas.
 * Garantiza que la acción de confirmación se ejecute a lo sumo una única vez y que
 * cualquier otra vía (cancelar, botón volver, o cerrar vía ESC) actúe como cancelación.
 */
public class ConfirmationMenu extends MenuHolder {

    private final String descriptionTitle;
    private final List<String> descriptionLines;
    private final Consumer<Player> onConfirm;
    private final Consumer<Player> onCancel;
    private final AtomicBoolean actionExecuted = new AtomicBoolean(false);

    /**
     * Constructor para diálogos de confirmación.
     *
     * @param title Título del menú inventario (e.g. "✦ ¿Confirmar Acción?")
     * @param descriptionTitle Título del ítem central explicativo
     * @param descriptionLines Líneas explicativas de la acción crítica
     * @param previousMenu Menú previo al que regresar tras cancelar (puede ser nulo)
     * @param onConfirm Acción a ejecutar si el usuario pulsa CONFIRMAR
     * @param onCancel Acción a ejecutar si el usuario pulsa CANCELAR o cierra el menú
     */
    public ConfirmationMenu(
            String title,
            String descriptionTitle,
            List<String> descriptionLines,
            MenuHolder previousMenu,
            Consumer<Player> onConfirm,
            Consumer<Player> onCancel
    ) {
        super(27, title != null ? title : "✦ Confirmar Acción");
        this.descriptionTitle = descriptionTitle != null ? descriptionTitle : "§6Confirmación Requerida";
        this.descriptionLines = descriptionLines != null ? descriptionLines : new ArrayList<>();
        this.previousMenu = previousMenu;
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
    }

    /**
     * Constructor simplificado.
     */
    public ConfirmationMenu(
            String title,
            String descriptionTitle,
            List<String> descriptionLines,
            MenuHolder previousMenu,
            Consumer<Player> onConfirm
    ) {
        this(title, descriptionTitle, descriptionLines, previousMenu, onConfirm, null);
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        // Slot 11: Botón CONFIRMAR (Bloque de Esmeralda / Lana Lima)
        ItemStack confirmItem = new ItemBuilder(Material.LIME_WOOL)
                .name("§a§l✔ CONFIRMAR")
                .lore(
                        "§7Haz clic para ejecutar esta acción.",
                        "",
                        "§a» Proceder con la operación"
                )
                .build();

        setItem(11, confirmItem, event -> {
            if (actionExecuted.compareAndSet(false, true)) {
                if (onConfirm != null) {
                    onConfirm.accept(player);
                } else if (previousMenu != null) {
                    previousMenu.open(player);
                } else {
                    player.closeInventory();
                }
            }
        });

        // Slot 13: Ítem explicativo central (Información y advertencias)
        ItemBuilder infoBuilder = new ItemBuilder(Material.PAPER)
                .name(descriptionTitle);
        for (String line : descriptionLines) {
            infoBuilder.addLore(line);
        }
        setItem(13, infoBuilder.build());

        // Slot 15: Botón CANCELAR (Lana Roja)
        ItemStack cancelItem = new ItemBuilder(Material.RED_WOOL)
                .name("§c§l✖ CANCELAR")
                .lore(
                        "§7Haz clic para cancelar y regresar.",
                        "",
                        "§c» Abortar operación"
                )
                .build();

        setItem(15, cancelItem, event -> {
            if (actionExecuted.compareAndSet(false, true)) {
                if (onCancel != null) {
                    onCancel.accept(player);
                } else if (previousMenu != null) {
                    previousMenu.open(player);
                } else {
                    player.closeInventory();
                }
            }
        });
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        // Si el jugador cerró con ESC sin hacer clic ni en Confirmar ni en Cancelar
        if (actionExecuted.compareAndSet(false, true)) {
            if (event.getPlayer() instanceof Player player) {
                if (onCancel != null) {
                    onCancel.accept(player);
                }
            }
        }
    }

    public boolean isActionExecuted() {
        return actionExecuted.get();
    }
}
