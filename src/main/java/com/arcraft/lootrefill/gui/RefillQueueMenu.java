package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.refill.RefillResult;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class RefillQueueMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private int page;

    public RefillQueueMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        this(plugin, previousMenu, 0);
    }

    public RefillQueueMenu(LootRefillPlugin plugin, MenuHolder previousMenu, int page) {
        super(54, "✦ LootRefill - Cola de Refill");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
        this.page = page;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);

        List<LootContainer> pending = plugin.getRefillManager().getPendingContainers();
        int pageSize = 28;
        int totalPages = (int) Math.ceil((double) Math.max(1, pending.size()) / pageSize);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, pending.size());

        int[] availableSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        long now = System.currentTimeMillis();

        for (int i = 0; i < availableSlots.length; i++) {
            int slot = availableSlots[i];
            int index = startIndex + i;

            if (index < endIndex) {
                LootContainer container = pending.get(index);
                long timeLeft = container.getNextRefill() != null ? Math.max(0, (container.getNextRefill() - now) / 1000L) : 0L;

                Material mat = container.getContainerType().getMaterial();
                ItemStack item = new ItemBuilder(mat)
                        .name("§e" + container.getContainerType().name() + " §8(§f" + container.getWorld() + "§8)")
                        .lore(
                                "§7Coordenadas: §f" + container.getX() + ", " + container.getY() + ", " + container.getZ(),
                                "§7Tabla de Loot: " + (container.hasLootConfigured() ? "§a" + container.getLootTableId() : "§cSin Loot configurado"),
                                "§7Tiempo restante: §e" + (!container.hasLootConfigured() ? "§cSin Loot" : (timeLeft == 0 ? "¡Listo para refill!" : timeLeft + "s")),
                                "§7Saqueado: " + (container.isLooted() ? "§cSí" : "§aNo"),
                                "",
                                "§aClick Izquierdo: §7Refill ahora",
                                "§cClick Derecho: §7Cancelar refill",
                                "§eShift + Click: §7Resetear estado"
                        )
                        .build();

                setItem(slot, item, event -> {
                    if (event.isLeftClick()) {
                        plugin.getRefillManager().refillContainer(container);
                        MessageUtil.sendMessage(player, "&aRefill ejecutado forzosamente para el contenedor.");
                        initialize(player);
                    } else if (event.isRightClick()) {
                        container.setNextRefill(System.currentTimeMillis() + (container.getRefillIntervalSeconds() * 1000L));
                        plugin.getContainerManager().saveContainer(container);
                        MessageUtil.sendMessage(player, "&eRefill pospuesto para el contenedor.");
                        initialize(player);
                    } else if (event.isShiftClick()) {
                        container.setLooted(false);
                        container.setNextRefill(container.hasLootConfigured() ? 0L : null);
                        plugin.getContainerManager().saveContainer(container);
                        MessageUtil.sendMessage(player, "&bEstado del contenedor reseteado.");
                        initialize(player);
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

        // Volver
        setBackButton(45, previousMenu);
    }
}
