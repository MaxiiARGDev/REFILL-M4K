package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ContainerMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private int page;

    public ContainerMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        this(plugin, previousMenu, 0);
    }

    public ContainerMenu(LootRefillPlugin plugin, MenuHolder previousMenu, int page) {
        super(54, "✦ LootRefill - Contenedores");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
        this.page = page;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);

        ContainerManager containerManager = plugin.getContainerManager();
        List<LootContainer> containers = new ArrayList<>(containerManager.getAllContainers());

        int pageSize = 28;
        int totalPages = (int) Math.ceil((double) Math.max(1, containers.size()) / pageSize);
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;

        int startIndex = page * pageSize;
        int endIndex = Math.min(startIndex + pageSize, containers.size());

        int[] availableSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < availableSlots.length; i++) {
            int slot = availableSlots[i];
            int index = startIndex + i;

            if (index < endIndex) {
                LootContainer container = containers.get(index);
                Material icon = container.getContainerType().getMaterial();

                String sourceStr = container.getSource() == ContainerSource.MAP ? "§aMAP" : (container.getSource() == ContainerSource.PLAYER ? "§cPLAYER" : "§7UNKNOWN");
                String statusStr = switch (container.getStatus()) {
                    case ACTIVE -> "§aACTIVE";
                    case BROKEN -> "§cBROKEN";
                    case DISABLED -> "§eDISABLED";
                };
                String managedStr = container.isManaged() ? "§aSÍ" : "§cNO";
                String refillStr = container.isRefillEnabled() ? "§aACTIVADO" : "§cDESACTIVADO";

                String lootDesc = "§cSin Loot configurado";
                if (container.hasLootConfigured()) {
                    lootDesc = container.getLootPoolId() != null
                            ? "§d[Pool] " + container.getLootPoolId()
                            : "§e" + container.getLootTableId();
                }

                ItemBuilder builder = new ItemBuilder(icon)
                        .name("§6" + container.getContainerType().name() + " §8(§f" + container.getWorld() + "§8)")
                        .addLore(
                                "§7Coordenadas: §f" + container.getX() + ", " + container.getY() + ", " + container.getZ(),
                                "§7Origen: " + sourceStr,
                                "§7Estado: " + statusStr,
                                "§7Administrado: " + managedStr,
                                "§7Loot: " + lootDesc,
                                "§7Refill: " + refillStr
                        );

                if (container.getSource() == ContainerSource.PLAYER) {
                    builder.addLore("", "§cLootRefill no administra este almacenamiento.");
                } else if (container.getStatus() == ContainerStatus.BROKEN) {
                    builder.addLore("", "§cEl almacenamiento original fue destruido.");
                } else {
                    builder.addLore(
                            "",
                            "§eClick Izquierdo: §7Asignar tabla de loot",
                            "§bClick Derecho: §7Alternar habilitado",
                            "§cShift + Click: §7Desregistrar"
                    );
                }

                setItem(slot, builder.build(), event -> {
                    if (event.isShiftClick()) {
                        containerManager.unregisterContainer(container.getId());
                        MessageUtil.sendMessage(player, "&cContenedor desregistrado.");
                        initialize(player);
                    } else if (event.isRightClick()) {
                        if (container.getSource() == ContainerSource.PLAYER) {
                            MessageUtil.sendMessage(player, "&cNo puedes modificar contenedores de jugadores.");
                            return;
                        }
                        container.setEnabled(!container.isEnabled());
                        container.setManaged(container.isEnabled() && container.getStatus() == ContainerStatus.ACTIVE);
                        containerManager.saveContainer(container);
                        initialize(player);
                    } else if (event.isLeftClick()) {
                        if (container.getSource() == ContainerSource.PLAYER) {
                            MessageUtil.sendMessage(player, "&cNo puedes asignar tablas a contenedores de jugadores.");
                            return;
                        }
                        plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                                "&aIngresa el ID de la tabla de loot para este contenedor MAP:",
                                input -> {
                                    String tableId = input.trim().toLowerCase();
                                    if (!plugin.getLootManager().tableExists(tableId)) {
                                        MessageUtil.sendMessage(player, "&cNo existe ninguna tabla con ID &e" + tableId + "&c.");
                                        open(player);
                                        return;
                                    }
                                    container.setLootTableId(tableId);
                                    container.setManaged(true);
                                    containerManager.saveContainer(container);
                                    MessageUtil.sendMessage(player, "&aTabla &e" + tableId + " &aasignada al contenedor MAP.");
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

        // Información de registro y desglose
        int mapCount = containerManager.getMapContainerCount();
        int playerCount = containerManager.getPlayerContainerCount();
        int brokenCount = containerManager.getBrokenContainerCount();

        ItemStack infoItem = new ItemBuilder(Material.PAPER)
                .name("§dFicha de Almacenamiento")
                .lore(
                        "§7Contenedores MAP: §a" + mapCount,
                        "§7Contenedores PLAYER: §c" + playerCount,
                        "§7Contenedores BROKEN: §6" + brokenCount,
                        "§7Administrables activos: §e" + containerManager.getAdministrableCount(),
                        "",
                        "§8LootRefill jamás toca almacenadores PLAYER."
                )
                .build();
        setItem(52, infoItem);

        // Volver
        setBackButton(45, previousMenu);
    }
}
