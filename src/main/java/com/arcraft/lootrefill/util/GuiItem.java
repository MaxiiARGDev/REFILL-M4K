package com.arcraft.lootrefill.util;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class GuiItem {

    private ItemStack item;
    private Consumer<InventoryClickEvent> action;

    public GuiItem(ItemStack item) {
        this(item, null);
    }

    public GuiItem(ItemStack item, Consumer<InventoryClickEvent> action) {
        this.item = item;
        this.action = action;
    }

    public ItemStack getItem() {
        return item;
    }

    public void setItem(ItemStack item) {
        this.item = item;
    }

    public Consumer<InventoryClickEvent> getAction() {
        return action;
    }

    public void setAction(Consumer<InventoryClickEvent> action) {
        this.action = action;
    }

    public void execute(InventoryClickEvent event) {
        if (action != null) {
            action.accept(event);
        }
    }
}
