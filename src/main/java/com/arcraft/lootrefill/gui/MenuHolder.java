package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.util.GuiItem;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class MenuHolder implements InventoryHolder {

    protected final Inventory inventory;
    protected final int size;
    protected final Component title;
    protected final Map<Integer, GuiItem> guiItems;
    protected MenuHolder previousMenu;

    public static final ItemStack BORDER_BLACK = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).name(" ").build();
    public static final ItemStack BORDER_GRAY = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).name(" ").build();

    public MenuHolder(int size, String title) {
        this(size, MessageUtil.color(title));
    }

    public MenuHolder(int size, Component title) {
        this.size = size;
        this.title = title;
        this.guiItems = new HashMap<>();
        this.inventory = Bukkit.createInventory(this, size, title);
    }

    public abstract void initialize(Player player);

    public void setItem(int slot, GuiItem guiItem) {
        if (slot >= 0 && slot < size) {
            guiItems.put(slot, guiItem);
            inventory.setItem(slot, guiItem != null ? guiItem.getItem() : null);
        }
    }

    public void setItem(int slot, ItemStack item) {
        setItem(slot, new GuiItem(item, null));
    }

    public void setItem(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        setItem(slot, new GuiItem(item, action));
    }

    public void fillBorders(ItemStack item) {
        int rows = size / 9;
        for (int col = 0; col < 9; col++) {
            setItem(col, item); // fila superior
            setItem((rows - 1) * 9 + col, item); // fila inferior
        }
        for (int row = 1; row < rows - 1; row++) {
            setItem(row * 9, item); // columna izquierda
            setItem(row * 9 + 8, item); // columna derecha
        }
    }

    public void fillBackground(ItemStack item) {
        for (int i = 0; i < size; i++) {
            if (!guiItems.containsKey(i) || inventory.getItem(i) == null) {
                setItem(i, item);
            }
        }
    }

    public void setBackButton(int slot, MenuHolder previousMenu) {
        this.previousMenu = previousMenu;
        ItemStack backItem = new ItemBuilder(Material.ARROW)
                .name("§c« Volver")
                .lore("§7Haz clic para regresar al menú anterior.")
                .build();

        setItem(slot, backItem, event -> {
            if (previousMenu != null && event.getWhoClicked() instanceof Player player) {
                previousMenu.open(player);
            } else {
                event.getWhoClicked().closeInventory();
            }
        });
    }

    public void open(Player player) {
        if (!player.hasPermission("lootrefill.admin")) {
            MessageUtil.sendMessage(player, "&cNo tienes permiso para acceder a esta interfaz.");
            return;
        }
        initialize(player);
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot >= 0 && slot < size) {
            GuiItem item = guiItems.get(slot);
            if (item != null) {
                item.execute(event);
            }
        }
    }

    public boolean allowPlayerInventoryClick(InventoryClickEvent event) {
        return false; // Por seguridad, por defecto nadie puede alterar su inventario dentro de la GUI
    }

    /**
     * Llamado cuando el inventario es cerrado por el jugador (ESC o evento close).
     * Por defecto no realiza ninguna acción.
     */
    public void handleClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        // Implementación opcional para subclases
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public MenuHolder getPreviousMenu() {
        return previousMenu;
    }

    public void setPreviousMenu(MenuHolder previousMenu) {
        this.previousMenu = previousMenu;
    }
}
