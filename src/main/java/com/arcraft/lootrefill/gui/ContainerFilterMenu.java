package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerFilter;
import com.arcraft.lootrefill.container.ContainerSortField;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootFilterType;
import com.arcraft.lootrefill.loot.LootTable;
import com.arcraft.lootrefill.pool.LootPool;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Menú interactivo para configurar visualmente todos los criterios de {@link ContainerFilter}.
 * Permite filtrar por mundo, tipo de contenedor, origen, estado, administración, registro,
 * refill, tipo de loot, LootPool, LootTable y definir ordenamiento y dirección.
 */
public class ContainerFilterMenu extends MenuHolder {

    private final LootRefillPlugin plugin;
    private final BiConsumer<Player, ContainerFilter> onApply;
    private ContainerFilter.Builder builder;

    public ContainerFilterMenu(LootRefillPlugin plugin, ContainerMenu containerMenu, ContainerFilter currentFilter) {
        this(plugin, (MenuHolder) containerMenu, currentFilter, (player, applied) -> {
            containerMenu.setFilter(applied);
            containerMenu.setPage(0);
            containerMenu.open(player);
        });
    }

    public ContainerFilterMenu(
            LootRefillPlugin plugin,
            MenuHolder previousMenu,
            ContainerFilter currentFilter,
            BiConsumer<Player, ContainerFilter> onApply
    ) {
        super(54, "✦ Filtros - Contenedores");
        this.plugin = plugin;
        this.previousMenu = previousMenu;
        this.onApply = onApply;
        this.builder = (currentFilter != null ? currentFilter : ContainerFilter.empty()).toBuilder();
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        // --- FILA 1: UBICACIÓN Y TIPOLOGÍA (Slots 10 a 16) ---

        // Slot 10: Mundo
        String currentWorld = builder.getWorld();
        ItemStack worldItem = new ItemBuilder(Material.GRASS_BLOCK)
                .name("§aMundo: §f" + (currentWorld != null ? currentWorld : "§8(Todos)"))
                .lore(
                        "§7Filtra por nombre de mundo conocido.",
                        "",
                        "§eClick Izquierdo: §7Siguiente mundo",
                        "§bClick Derecho: §7Limpiar (Todos)",
                        "§6Shift + Click: §7Ingresar por chat"
                )
                .build();
        setItem(10, worldItem, event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                        "&aIngresa el nombre del mundo a filtrar (o escribe &etodos&a):",
                        input -> {
                            String trimmed = input.trim();
                            if (trimmed.equalsIgnoreCase("todos") || trimmed.equalsIgnoreCase("all") || trimmed.equalsIgnoreCase("ninguno")) {
                                builder.world(null);
                            } else {
                                builder.world(trimmed);
                            }
                            open(player);
                        }
                );
                return;
            }
            if (event.isRightClick()) {
                builder.world(null);
            } else {
                List<String> worlds = Bukkit.getWorlds().stream().map(World::getName).toList();
                if (!worlds.isEmpty()) {
                    if (currentWorld == null) {
                        builder.world(worlds.get(0));
                    } else {
                        int idx = -1;
                        for (int i = 0; i < worlds.size(); i++) {
                            if (worlds.get(i).equalsIgnoreCase(currentWorld)) {
                                idx = i;
                                break;
                            }
                        }
                        if (idx >= 0 && idx < worlds.size() - 1) {
                            builder.world(worlds.get(idx + 1));
                        } else {
                            builder.world(null);
                        }
                    }
                }
            }
            initialize(player);
        });

        // Slot 11: Tipo de Contenedor
        ContainerType currentType = builder.getContainerType();
        Material typeIcon = currentType != null ? currentType.getMaterial() : Material.CHEST;
        ItemStack typeItem = new ItemBuilder(typeIcon)
                .name("§eTipo: §f" + (currentType != null ? currentType.name() : "§8(Todos)"))
                .lore(
                        "§7Filtra por cofre, barril, horno, tolva, etc.",
                        "",
                        "§eClick Izquierdo: §7Siguiente tipo",
                        "§bClick Derecho: §7Limpiar (Todos)"
                )
                .build();
        setItem(11, typeItem, event -> {
            if (event.isRightClick()) {
                builder.containerType(null);
            } else {
                ContainerType[] types = ContainerType.values();
                if (currentType == null) {
                    builder.containerType(types[0]);
                } else {
                    int idx = currentType.ordinal();
                    if (idx < types.length - 1) {
                        builder.containerType(types[idx + 1]);
                    } else {
                        builder.containerType(null);
                    }
                }
            }
            initialize(player);
        });

        // Slot 12: Origen (Source)
        ContainerSource currentSource = builder.getSource();
        ItemStack sourceItem = new ItemBuilder(Material.ENDER_EYE)
                .name("§bOrigen: §f" + (currentSource != null ? currentSource.name() : "§8(Todos)"))
                .lore(
                        "§7MAP (servidor), PLAYER (jugador) o UNKNOWN.",
                        "",
                        "§eClick Izquierdo: §7Siguiente origen",
                        "§bClick Derecho: §7Limpiar (Todos)"
                )
                .build();
        setItem(12, sourceItem, event -> {
            if (event.isRightClick()) {
                builder.source(null);
            } else {
                ContainerSource[] sources = ContainerSource.values();
                if (currentSource == null) {
                    builder.source(sources[0]);
                } else {
                    int idx = currentSource.ordinal();
                    if (idx < sources.length - 1) {
                        builder.source(sources[idx + 1]);
                    } else {
                        builder.source(null);
                    }
                }
            }
            initialize(player);
        });

        // Slot 13: Estado (Status)
        ContainerStatus currentStatus = builder.getStatus();
        ItemStack statusItem = new ItemBuilder(Material.IRON_DOOR)
                .name("§6Estado: §f" + (currentStatus != null ? currentStatus.name() : "§8(Todos)"))
                .lore(
                        "§7ACTIVE (activo), BROKEN (roto) o DISABLED (desactivado).",
                        "",
                        "§eClick Izquierdo: §7Siguiente estado",
                        "§bClick Derecho: §7Limpiar (Todos)"
                )
                .build();
        setItem(13, statusItem, event -> {
            if (event.isRightClick()) {
                builder.status(null);
            } else {
                ContainerStatus[] statuses = ContainerStatus.values();
                if (currentStatus == null) {
                    builder.status(statuses[0]);
                } else {
                    int idx = currentStatus.ordinal();
                    if (idx < statuses.length - 1) {
                        builder.status(statuses[idx + 1]);
                    } else {
                        builder.status(null);
                    }
                }
            }
            initialize(player);
        });

        // Slot 14: Administrado (Managed)
        Boolean currentManaged = builder.getManaged();
        String managedStr = currentManaged == null ? "§8(Todos)" : (currentManaged ? "§aSÍ" : "§cNO");
        ItemStack managedItem = new ItemBuilder(Material.NAME_TAG)
                .name("§dAdministrado: " + managedStr)
                .lore(
                        "§7Contenedores administrados activamente por LootRefill.",
                        "",
                        "§eClick Izquierdo: §7Alternar (Todos / Sí / No)",
                        "§bClick Derecho: §7Limpiar (Todos)"
                )
                .build();
        setItem(14, managedItem, event -> {
            if (event.isRightClick()) {
                builder.managed(null);
            } else {
                if (currentManaged == null) {
                    builder.managed(Boolean.TRUE);
                } else if (currentManaged) {
                    builder.managed(Boolean.FALSE);
                } else {
                    builder.managed(null);
                }
            }
            initialize(player);
        });

        // Slot 15: Registrado (Registered)
        Boolean currentRegistered = builder.getRegistered();
        String registeredStr = currentRegistered == null ? "§8(Todos)" : (currentRegistered ? "§aSÍ" : "§cNO");
        ItemStack registeredItem = new ItemBuilder(Material.WRITABLE_BOOK)
                .name("§3Registrado: " + registeredStr)
                .lore(
                        "§7Contenedores registrados en la base de datos.",
                        "",
                        "§eClick Izquierdo: §7Alternar (Todos / Sí / No)",
                        "§bClick Derecho: §7Limpiar (Todos)"
                )
                .build();
        setItem(15, registeredItem, event -> {
            if (event.isRightClick()) {
                builder.registered(null);
            } else {
                if (currentRegistered == null) {
                    builder.registered(Boolean.TRUE);
                } else if (currentRegistered) {
                    builder.registered(Boolean.FALSE);
                } else {
                    builder.registered(null);
                }
            }
            initialize(player);
        });

        // Slot 16: Refill Habilitado (RefillEnabled)
        Boolean currentRefill = builder.getRefillEnabled();
        String refillStr = currentRefill == null ? "§8(Todos)" : (currentRefill ? "§aACTIVADO" : "§cDESACTIVADO");
        ItemStack refillItem = new ItemBuilder(Material.CLOCK)
                .name("§eRefill: " + refillStr)
                .lore(
                        "§7Si el contenedor tiene refill automático activado.",
                        "",
                        "§eClick Izquierdo: §7Alternar (Todos / Activado / Desactivado)",
                        "§bClick Derecho: §7Limpiar (Todos)"
                )
                .build();
        setItem(16, refillItem, event -> {
            if (event.isRightClick()) {
                builder.refillEnabled(null);
            } else {
                if (currentRefill == null) {
                    builder.refillEnabled(Boolean.TRUE);
                } else if (currentRefill) {
                    builder.refillEnabled(Boolean.FALSE);
                } else {
                    builder.refillEnabled(null);
                }
            }
            initialize(player);
        });

        // --- FILA 3: LOOT, BÚSQUEDA Y ORDENAMIENTO (Slots 28 a 34) ---

        // Slot 28: Tipo de Loot
        LootFilterType currentLootFilter = builder.getLootFilterType();
        ItemStack lootFilterItem = new ItemBuilder(Material.GOLD_INGOT)
                .name("§6Tipo de Loot: §f" + currentLootFilter.name())
                .lore(
                        "§7ALL: Sin restricción de loot.",
                        "§7HAS_LOOT: Tiene tabla o pool asignado.",
                        "§7NO_LOOT: Sin ningún loot configurado.",
                        "§7LOOT_POOL: Asignado a un Loot Pool.",
                        "§7LOOT_TABLE: Asignado a Loot Table legacy.",
                        "",
                        "§eClick Izquierdo: §7Siguiente modo",
                        "§bClick Derecho: §7Restablecer a ALL"
                )
                .build();
        setItem(28, lootFilterItem, event -> {
            if (event.isRightClick()) {
                builder.lootFilterType(LootFilterType.ALL);
            } else {
                LootFilterType[] lootTypes = LootFilterType.values();
                int next = (currentLootFilter.ordinal() + 1) % lootTypes.length;
                builder.lootFilterType(lootTypes[next]);
            }
            initialize(player);
        });

        // Slot 29: Loot Pool Específico
        String currentPoolId = builder.getLootPoolId();
        ItemStack poolItem = new ItemBuilder(Material.ENDER_CHEST)
                .name("§dLoot Pool: §f" + (currentPoolId != null ? currentPoolId : "§8(Sin filtro)"))
                .lore(
                        "§7Filtra contenedores con este pool específico.",
                        "",
                        "§eClick Izquierdo: §7Seleccionar de la lista",
                        "§bClick Derecho: §7Quitar filtro de pool"
                )
                .build();
        setItem(29, poolItem, event -> {
            if (event.isRightClick()) {
                builder.lootPoolId(null);
                initialize(player);
            } else {
                new SelectPoolFilterMenu(plugin, this, currentPoolId, selectedId -> {
                    builder.lootPoolId(selectedId);
                    open(player);
                }).open(player);
            }
        });

        // Slot 30: Loot Table Específica
        String currentTableId = builder.getLootTableId();
        ItemStack tableItem = new ItemBuilder(Material.BOOKSHELF)
                .name("§eLoot Table: §f" + (currentTableId != null ? currentTableId : "§8(Sin filtro)"))
                .lore(
                        "§7Filtra contenedores con esta tabla legacy.",
                        "",
                        "§eClick Izquierdo: §7Seleccionar de la lista",
                        "§bClick Derecho: §7Quitar filtro de tabla"
                )
                .build();
        setItem(30, tableItem, event -> {
            if (event.isRightClick()) {
                builder.lootTableId(null);
                initialize(player);
            } else {
                new SelectTableFilterMenu(plugin, this, currentTableId, selectedId -> {
                    builder.lootTableId(selectedId);
                    open(player);
                }).open(player);
            }
        });

        // Slot 31: Búsqueda Textual (Query)
        String currentQuery = builder.getQuery();
        ItemStack queryItem = new ItemBuilder(Material.SPYGLASS)
                .name("§eBúsqueda Textual: " + (currentQuery != null ? "§f\"§6" + currentQuery + "§f\"" : "§8(Ninguna)"))
                .lore(
                        "§7Filtra por ID, coordenadas (x,y,z), mundo o nombres.",
                        "",
                        "§eClick Izquierdo: §7Ingresar texto en chat",
                        "§bClick Derecho: §7Limpiar búsqueda"
                )
                .build();
        setItem(31, queryItem, event -> {
            if (event.isRightClick()) {
                builder.query(null);
                initialize(player);
            } else {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                        "&aIngresa el término de búsqueda (ID, coordenadas, mundo, tabla o pool):",
                        input -> {
                            String trimmed = input.trim();
                            builder.query(trimmed.isEmpty() ? null : trimmed);
                            open(player);
                        }
                );
            }
        });

        // Slot 32: Campo de Ordenamiento
        ContainerSortField currentSortField = builder.getSortField();
        ItemStack sortItem = new ItemBuilder(Material.COMPARATOR)
                .name("§a⇅ Ordenar por: §e" + currentSortField.getColumnName())
                .lore(
                        "§7Criterio para ordenar la lista de contenedores.",
                        "",
                        "§eClick Izquierdo: §7Siguiente campo",
                        "§bClick Derecho: §7Campo anterior"
                )
                .build();
        setItem(32, sortItem, event -> {
            ContainerSortField[] fields = ContainerSortField.values();
            int idx = currentSortField.ordinal();
            if (event.isRightClick()) {
                int prev = (idx - 1 + fields.length) % fields.length;
                builder.sortField(fields[prev]);
            } else {
                int next = (idx + 1) % fields.length;
                builder.sortField(fields[next]);
            }
            initialize(player);
        });

        // Slot 33: Dirección (ASC / DESC)
        boolean ascending = builder.isAscending();
        ItemStack dirItem = new ItemBuilder(Material.REPEATER)
                .name("§aDirección: " + (ascending ? "§f▲ ASCENDENTE (A-Z / Antiguos)" : "§f▼ DESCENDENTE (Z-A / Recientes)"))
                .lore(
                        "§7Alterna la dirección del ordenamiento.",
                        "",
                        "§e» Haz clic para alternar"
                )
                .build();
        setItem(33, dirItem, event -> {
            builder.ascending(!ascending);
            initialize(player);
        });

        // --- FILA 5: CONTROLES DE APLICACIÓN Y NAVEGACIÓN (Slots 45, 48, 50, 52) ---

        // Slot 45: Cancelar y Volver
        setBackButton(45, previousMenu);

        // Slot 48: Limpiar Todos los Filtros
        ItemStack clearItem = new ItemBuilder(Material.MILK_BUCKET)
                .name("§c✖ Limpiar Todos los Filtros")
                .lore(
                        "§7Restablece todos los filtros activos",
                        "§7y la búsqueda a los valores por defecto.",
                        "",
                        "§c» Haz clic para restablecer"
                )
                .build();
        setItem(48, clearItem, event -> {
            builder = ContainerFilter.empty().toBuilder();
            MessageUtil.sendMessage(player, "&eFiltros restablecidos.");
            initialize(player);
        });

        // Slot 50: Aplicar Filtros
        ItemStack applyItem = new ItemBuilder(Material.EMERALD)
                .name("§a✔ Aplicar Filtros")
                .lore(
                        "§7Guarda los criterios configurados y",
                        "§7actualiza la lista de contenedores.",
                        "",
                        "§a» Haz clic para aplicar"
                )
                .build();
        setItem(50, applyItem, event -> {
            ContainerFilter finalFilter = builder.build();
            if (onApply != null) {
                onApply.accept(player, finalFilter);
            } else if (previousMenu instanceof ContainerMenu cm) {
                cm.setFilter(finalFilter);
                cm.setPage(0);
                cm.open(player);
            } else if (previousMenu != null) {
                previousMenu.open(player);
            }
        });

        // Slot 52: Resumen en vivo de Resultados
        ContainerFilter previewFilter = builder.build();
        int matchingCount = plugin.getContainerManager().countContainers(previewFilter);
        int totalContainers = plugin.getContainerManager().getTotalContainers();
        int activeCount = previewFilter.getActiveFilterCount();

        ItemStack previewItem = new ItemBuilder(Material.PAPER)
                .name("§dResultados con este Filtro")
                .lore(
                        "§7Contenedores coincidentes: §a" + matchingCount,
                        "§7Total en servidor: §f" + totalContainers,
                        "§7Filtros activos: §e" + activeCount,
                        "",
                        "§8Los filtros se aplican instantáneamente al hacer clic en Aplicar."
                )
                .build();
        setItem(52, previewItem);
    }

    /**
     * Sub-menú para seleccionar interactivamente un Loot Pool para el filtro.
     */
    private static class SelectPoolFilterMenu extends MenuHolder {
        private final LootRefillPlugin plugin;
        private final MenuHolder parentMenu;
        private final String currentSelectedId;
        private final Consumer<String> onSelected;
        private int page;

        public SelectPoolFilterMenu(
                LootRefillPlugin plugin,
                MenuHolder parentMenu,
                String currentSelectedId,
                Consumer<String> onSelected
        ) {
            super(54, "✦ Seleccionar Loot Pool");
            this.plugin = plugin;
            this.parentMenu = parentMenu;
            this.currentSelectedId = currentSelectedId;
            this.onSelected = onSelected;
            this.page = 0;
        }

        @Override
        public void initialize(Player player) {
            fillBorders(BORDER_BLACK);

            // Opción para quitar filtro en slot 4
            ItemStack removeFilterItem = new ItemBuilder(Material.BARRIER)
                    .name("§c✖ Sin Filtro de Pool (Todos)")
                    .lore("§7Elimina el filtro por Loot Pool específico.", "", "§c» Haz clic para seleccionar")
                    .build();
            setItem(4, removeFilterItem, event -> {
                onSelected.accept(null);
            });

            List<LootPool> pools = new ArrayList<>(plugin.getLootPoolManager().getAllPools());
            pools.sort(Comparator.comparing(LootPool::getId, String.CASE_INSENSITIVE_ORDER));

            int pageSize = 28;
            int totalPages = (int) Math.ceil((double) Math.max(1, pools.size()) / pageSize);
            if (page >= totalPages) page = totalPages - 1;
            if (page < 0) page = 0;

            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, pools.size());

            int[] slots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43
            };

            for (int i = 0; i < slots.length; i++) {
                int slot = slots[i];
                int poolIdx = startIndex + i;

                if (poolIdx < endIndex) {
                    LootPool pool = pools.get(poolIdx);
                    boolean isSelected = pool.getId().equalsIgnoreCase(currentSelectedId);

                    ItemBuilder item = new ItemBuilder(isSelected ? Material.ENDER_CHEST : Material.CHEST)
                            .name((isSelected ? "§a✔ " : "§d") + pool.getName() + " §8(§7" + pool.getId() + "§8)")
                            .lore(
                                    "§7Modo: §f" + pool.getSelectionMode().name(),
                                    "§7Tablas miembro: §f" + pool.getEntries().size(),
                                    "§7Estado: " + (pool.isEnabled() ? "§aActivado" : "§cDesactivado"),
                                    "",
                                    "§e» Haz clic para filtrar por este pool"
                            );

                    setItem(slot, item.build(), event -> {
                        onSelected.accept(pool.getId());
                    });
                } else {
                    setItem(slot, BORDER_GRAY);
                }
            }

            // Paginación
            if (page > 0) {
                setItem(48, new ItemBuilder(Material.ARROW).name("§e« Anterior").build(), event -> {
                    page--;
                    initialize(player);
                });
            } else {
                setItem(48, BORDER_BLACK);
            }

            if (page < totalPages - 1) {
                setItem(50, new ItemBuilder(Material.ARROW).name("§eSiguiente »").build(), event -> {
                    page++;
                    initialize(player);
                });
            } else {
                setItem(50, BORDER_BLACK);
            }

            setBackButton(45, parentMenu);
        }
    }

    /**
     * Sub-menú para seleccionar interactivamente una Loot Table para el filtro.
     */
    private static class SelectTableFilterMenu extends MenuHolder {
        private final LootRefillPlugin plugin;
        private final MenuHolder parentMenu;
        private final String currentSelectedId;
        private final Consumer<String> onSelected;
        private int page;

        public SelectTableFilterMenu(
                LootRefillPlugin plugin,
                MenuHolder parentMenu,
                String currentSelectedId,
                Consumer<String> onSelected
        ) {
            super(54, "✦ Seleccionar Loot Table");
            this.plugin = plugin;
            this.parentMenu = parentMenu;
            this.currentSelectedId = currentSelectedId;
            this.onSelected = onSelected;
            this.page = 0;
        }

        @Override
        public void initialize(Player player) {
            fillBorders(BORDER_BLACK);

            // Opción para quitar filtro en slot 4
            ItemStack removeFilterItem = new ItemBuilder(Material.BARRIER)
                    .name("§c✖ Sin Filtro de Tabla (Todas)")
                    .lore("§7Elimina el filtro por Loot Table específica.", "", "§c» Haz clic para seleccionar")
                    .build();
            setItem(4, removeFilterItem, event -> {
                onSelected.accept(null);
            });

            List<LootTable> tables = new ArrayList<>(plugin.getLootManager().getTables());
            tables.sort(Comparator.comparing(LootTable::getId, String.CASE_INSENSITIVE_ORDER));

            int pageSize = 28;
            int totalPages = (int) Math.ceil((double) Math.max(1, tables.size()) / pageSize);
            if (page >= totalPages) page = totalPages - 1;
            if (page < 0) page = 0;

            int startIndex = page * pageSize;
            int endIndex = Math.min(startIndex + pageSize, tables.size());

            int[] slots = {
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34,
                    37, 38, 39, 40, 41, 42, 43
            };

            for (int i = 0; i < slots.length; i++) {
                int slot = slots[i];
                int tableIdx = startIndex + i;

                if (tableIdx < endIndex) {
                    LootTable table = tables.get(tableIdx);
                    boolean isSelected = table.getId().equalsIgnoreCase(currentSelectedId);

                    ItemBuilder item = new ItemBuilder(isSelected ? Material.BOOKSHELF : Material.BOOK)
                            .name((isSelected ? "§a✔ " : "§e") + table.getDisplayName() + " §8(§7" + table.getId() + "§8)")
                            .lore(
                                    "§7Descripción: §f" + table.getDescription(),
                                    "§7Items por roll: §f" + table.getMinItems() + " - " + table.getMaxItems(),
                                    "§7Entradas: §f" + table.getEntries().size(),
                                    "",
                                    "§e» Haz clic para filtrar por esta tabla"
                            );

                    setItem(slot, item.build(), event -> {
                        onSelected.accept(table.getId());
                    });
                } else {
                    setItem(slot, BORDER_GRAY);
                }
            }

            // Paginación
            if (page > 0) {
                setItem(48, new ItemBuilder(Material.ARROW).name("§e« Anterior").build(), event -> {
                    page--;
                    initialize(player);
                });
            } else {
                setItem(48, BORDER_BLACK);
            }

            if (page < totalPages - 1) {
                setItem(50, new ItemBuilder(Material.ARROW).name("§eSiguiente »").build(), event -> {
                    page++;
                    initialize(player);
                });
            } else {
                setItem(50, BORDER_BLACK);
            }

            setBackButton(45, parentMenu);
        }
    }
}
