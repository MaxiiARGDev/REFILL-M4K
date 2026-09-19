package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerFilter;
import com.arcraft.lootrefill.container.ContainerManager;
import com.arcraft.lootrefill.container.ContainerSortField;
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

/**
 * Listado administrativo principal para Container Manager 2.0.
 * Incorpora filtrado en vivo con {@link ContainerFilter}, búsqueda textual,
 * ordenamiento configurable con {@link ContainerSortField}, navegación paginada
 * y protección de acciones críticas con {@link ConfirmationMenu}.
 */
public class ContainerMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private int page;
    private ContainerFilter filter;

    public ContainerMenu(LootRefillPlugin plugin, MenuHolder previousMenu) {
        this(plugin, previousMenu, 0, ContainerFilter.empty());
    }

    public ContainerMenu(LootRefillPlugin plugin, MenuHolder previousMenu, int page) {
        this(plugin, previousMenu, page, ContainerFilter.empty());
    }

    public ContainerMenu(LootRefillPlugin plugin, MenuHolder previousMenu, int page, ContainerFilter filter) {
        super(54, "✦ LootRefill - Contenedores");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
        this.page = Math.max(0, page);
        this.filter = filter != null ? filter : ContainerFilter.empty();
    }

    public ContainerFilter getFilter() {
        return filter;
    }

    public void setFilter(ContainerFilter filter) {
        this.filter = filter != null ? filter : ContainerFilter.empty();
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);

        ContainerManager containerManager = plugin.getContainerManager();
        int pageSize = 28;

        // Paginación y conteo sin cargar la totalidad de contenedores en colecciones intermedias
        int totalContainers = containerManager.countContainers(filter);
        int totalPages = (int) Math.ceil((double) Math.max(1, totalContainers) / pageSize);
        if (page >= totalPages) page = Math.max(0, totalPages - 1);
        if (page < 0) page = 0;

        List<LootContainer> containers = containerManager.findContainers(filter, page, pageSize);

        // --- BARRA SUPERIOR: FILTROS, BÚSQUEDA Y ORDENAMIENTO (Slots 2, 3, 4, 5) ---

        // Slot 2: Búsqueda Rápida
        String currentQuery = filter.getQuery();
        ItemBuilder searchBuilder = new ItemBuilder(Material.SPYGLASS)
                .name("§e🔍 Búsqueda rápida")
                .addLore(
                        "§7Término actual: " + (currentQuery != null ? "§f\"§6" + currentQuery + "§f\"" : "§8(Ninguno)"),
                        "",
                        "§eClick Izquierdo: §7Buscar por chat"
                );
        if (currentQuery != null) {
            searchBuilder.addLore("§cClick Derecho: §7Limpiar búsqueda");
        }
        setItem(2, searchBuilder.build(), event -> {
            if (event.isRightClick() && currentQuery != null) {
                this.filter = this.filter.toBuilder().query(null).build();
                this.page = 0;
                initialize(player);
            } else {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                        "&aIngresa el término a buscar (ID, coordenadas x,y,z, mundo, tabla o pool):",
                        input -> {
                            String trimmed = input.trim();
                            this.filter = this.filter.toBuilder().query(trimmed.isEmpty() ? null : trimmed).build();
                            this.page = 0;
                            open(player);
                        }
                );
            }
        });

        // Slot 3: Limpiar Filtros
        boolean hasFilters = filter.hasActiveFilters();
        ItemStack clearFiltersItem = new ItemBuilder(Material.MILK_BUCKET)
                .name("§c✖ Limpiar Filtros")
                .lore(
                        "§7Restablece todos los filtros activos",
                        "§7y la búsqueda actual.",
                        "",
                        hasFilters ? "§c» Haz clic para limpiar filtros" : "§8No hay filtros activos"
                )
                .build();
        setItem(3, clearFiltersItem, event -> {
            if (filter.hasActiveFilters()) {
                this.filter = ContainerFilter.empty();
                this.page = 0;
                MessageUtil.sendMessage(player, "&eFiltros restablecidos a valores por defecto.");
                initialize(player);
            }
        });

        // Slot 4: Configurar Filtros
        int activeCount = filter.getActiveFilterCount();
        ItemBuilder filterBtn = new ItemBuilder(hasFilters ? Material.HOPPER : Material.CAULDRON)
                .name("§6⚙ Filtros: " + (hasFilters ? "§e" + activeCount + " activo(s)" : "§7Ninguno"))
                .addLore("§7Abre el panel avanzado de filtros.", "");
        if (hasFilters) {
            if (filter.getWorld() != null) filterBtn.addLore("§7• Mundo: §f" + filter.getWorld());
            if (filter.getContainerType() != null) filterBtn.addLore("§7• Tipo: §f" + filter.getContainerType().name());
            if (filter.getSource() != null) filterBtn.addLore("§7• Origen: §f" + filter.getSource().name());
            if (filter.getStatus() != null) filterBtn.addLore("§7• Estado: §f" + filter.getStatus().name());
            if (filter.getManaged() != null) filterBtn.addLore("§7• Managed: §f" + (filter.getManaged() ? "SÍ" : "NO"));
            if (filter.getRegistered() != null) filterBtn.addLore("§7• Registered: §f" + (filter.getRegistered() ? "SÍ" : "NO"));
            if (filter.getRefillEnabled() != null) filterBtn.addLore("§7• Refill: §f" + (filter.getRefillEnabled() ? "ON" : "OFF"));
            if (filter.getLootFilterType() != null && filter.getLootFilterType() != com.arcraft.lootrefill.container.LootFilterType.ALL) {
                filterBtn.addLore("§7• Modo Loot: §f" + filter.getLootFilterType().name());
            }
            if (filter.getLootPoolId() != null) filterBtn.addLore("§7• Pool: §f" + filter.getLootPoolId());
            if (filter.getLootTableId() != null) filterBtn.addLore("§7• Tabla: §f" + filter.getLootTableId());
            if (filter.getQuery() != null) filterBtn.addLore("§7• Query: §f\"" + filter.getQuery() + "\"");
            filterBtn.addLore("");
        }
        filterBtn.addLore("§e» Haz clic para configurar filtros");
        setItem(4, filterBtn.build(), event -> {
            new ContainerFilterMenu(plugin, this, this.filter).open(player);
        });

        // Slot 5: Ordenamiento Rápido
        ContainerSortField sortField = filter.getSortField();
        boolean ascending = filter.isAscending();
        ItemStack sortBtn = new ItemBuilder(Material.COMPARATOR)
                .name("§a⇅ Orden: §e" + sortField.getColumnName())
                .lore(
                        "§7Dirección: §f" + (ascending ? "▲ Ascendente (A-Z / Antiguos)" : "▼ Descendente (Z-A / Recientes)"),
                        "",
                        "§eClick Izquierdo: §7Siguiente criterio",
                        "§bClick Derecho: §7Alternar dirección (ASC/DESC)"
                )
                .build();
        setItem(5, sortBtn, event -> {
            if (event.isRightClick()) {
                this.filter = this.filter.toBuilder().ascending(!ascending).build();
            } else {
                ContainerSortField[] fields = ContainerSortField.values();
                int nextIdx = (sortField.ordinal() + 1) % fields.length;
                this.filter = this.filter.toBuilder().sortField(fields[nextIdx]).build();
            }
            this.page = 0;
            initialize(player);
        });

        // --- CONTENEDORES CENTRALES (28 slots) ---
        int[] availableSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        for (int i = 0; i < availableSlots.length; i++) {
            int slot = availableSlots[i];

            if (i < containers.size()) {
                LootContainer container = containers.get(i);
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
                    builder.addLore(
                            "",
                            "§cLootRefill no administra este almacenamiento.",
                            "§eClick Izquierdo: §7Ver detalles y opciones"
                    );
                } else if (container.getStatus() == ContainerStatus.BROKEN) {
                    builder.addLore(
                            "",
                            "§cEl almacenamiento original fue destruido.",
                            "§eClick Izquierdo: §7Ver detalles técnicos"
                    );
                } else {
                    builder.addLore(
                            "",
                            "§eClick Izquierdo: §7Ver detalle e inspeccionar",
                            "§bClick Derecho: §7Alternar habilitado",
                            "§cShift + Click: §7Desregistrar (requiere confirmación)"
                    );
                }

                setItem(slot, builder.build(), event -> {
                    if (event.isShiftClick()) {
                        // Confirmación segura con ConfirmationMenu
                        new ConfirmationMenu(
                                "§c¿Desregistrar Contenedor?",
                                "§e" + container.getContainerType().name() + " §8(§f" + container.getWorld() + "§8)",
                                List.of(
                                        "§7Posición: §f" + container.getX() + ", " + container.getY() + ", " + container.getZ(),
                                        "§7Origen: §f" + container.getSource().name() + " §8| §7Estado: §f" + container.getStatus().name(),
                                        "",
                                        "§c⚠ Esta acción desregistrará permanentemente",
                                        "§cel contenedor del sistema de LootRefill."
                                ),
                                this,
                                p -> {
                                    containerManager.unregisterContainer(container.getId());
                                    MessageUtil.sendMessage(p, "&cContenedor desregistrado correctamente.");
                                    open(p);
                                },
                                p -> open(p)
                        ).open(player);
                    } else if (event.isRightClick()) {
                        if (container.getSource() == ContainerSource.PLAYER) {
                            MessageUtil.sendMessage(player, "&cNo puedes modificar contenedores de jugadores directamente.");
                            return;
                        }
                        container.setEnabled(!container.isEnabled());
                        container.setManaged(container.isEnabled() && container.getStatus() == ContainerStatus.ACTIVE);
                        containerManager.saveContainer(container);
                        initialize(player);
                    } else if (event.isLeftClick()) {
                        // Fase 5: abrir ficha detallada del contenedor
                        new ContainerDetailMenu(plugin, container, this).open(player);
                    }
                });
            } else {
                setItem(slot, BORDER_GRAY);
            }
        }

        // --- BARRA INFERIOR: PAGINACIÓN Y NAVEGACIÓN (Slots 45, 48, 49, 50, 53) ---

        // Slot 45: Volver
        setBackButton(45, previousMenu);

        // Slot 48: Página Anterior
        if (page > 0) {
            ItemStack prev = new ItemBuilder(Material.ARROW)
                    .name("§e« Página anterior (" + page + ")")
                    .build();
            setItem(48, prev, event -> {
                page--;
                initialize(player);
            });
        } else {
            setItem(48, BORDER_BLACK);
        }

        // Slot 49: Información de Página
        ItemStack pageIndicator = new ItemBuilder(Material.BOOK)
                .name("§ePágina " + (page + 1) + " de " + Math.max(1, totalPages))
                .lore(
                        "§7Resultados coincidentes: §a" + totalContainers,
                        "§7Total en servidor: §f" + containerManager.getTotalContainers(),
                        "§7Mostrando: §f" + (totalContainers > 0 ? (page * pageSize + 1) + " - " + Math.min((page + 1) * pageSize, totalContainers) : "0")
                )
                .build();
        setItem(49, pageIndicator);

        // Slot 50: Página Siguiente
        if (page < totalPages - 1) {
            ItemStack next = new ItemBuilder(Material.ARROW)
                    .name("§ePágina siguiente (" + (page + 2) + ") »")
                    .build();
            setItem(50, next, event -> {
                page++;
                initialize(player);
            });
        } else {
            setItem(50, BORDER_BLACK);
        }

        // Slot 53: Ficha general de almacenamiento
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
                        "§8LootRefill jamás modifica almacenadores PLAYER."
                )
                .build();
        setItem(53, infoItem);
    }
}
