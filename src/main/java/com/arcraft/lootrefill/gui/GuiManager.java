package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public class GuiManager implements Listener {

    private final LootRefillPlugin plugin;
    private final ChatInputHandler chatInputHandler;

    public GuiManager(LootRefillPlugin plugin) {
        this.plugin = plugin;
        this.chatInputHandler = new ChatInputHandler(plugin);
    }

    public ChatInputHandler getChatInputHandler() {
        return chatInputHandler;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }

        // Validación de permisos estricta
        if (!player.hasPermission("lootrefill.admin")) {
            event.setCancelled(true);
            player.closeInventory();
            MessageUtil.sendMessage(player, "&cNo tienes permisos administrativos.");
            return;
        }

        int rawSlot = event.getRawSlot();

        // Prevención de exploits de doble click (colectar items hacia el cursor)
        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        // Si el click fue en la parte superior (el menu GUI)
        if (rawSlot >= 0 && rawSlot < holder.getInventory().getSize()) {
            event.setCancelled(true);
            try {
                holder.handleClick(event);
            } catch (Exception e) {
                plugin.getLogger().severe("Error al procesar click en GUI: " + e.getMessage());
                e.printStackTrace();
            }
            return;
        }

        // Si el click fue en el inventario inferior del jugador
        if (rawSlot >= holder.getInventory().getSize()) {
            // Prevenir shift-click hacia el inventario superior
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }

            // Prevenir mover items a menos que el menu especificamente lo permita
            if (!holder.allowPlayerInventoryClick(event)) {
                if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                        || event.getAction() == InventoryAction.HOTBAR_SWAP) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof MenuHolder holder)) {
            return;
        }

        // Prevenir arrastrar items hacia cualquier slot del menu superior
        for (int slot : event.getRawSlots()) {
            if (slot < holder.getInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
