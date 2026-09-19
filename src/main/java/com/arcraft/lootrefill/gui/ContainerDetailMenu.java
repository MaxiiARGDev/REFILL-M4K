package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.LootRefillPlugin;
import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootContainer;
import com.arcraft.lootrefill.loot.LootTable;
import com.arcraft.lootrefill.pool.LootPool;
import com.arcraft.lootrefill.populate.PopulateResult;
import com.arcraft.lootrefill.refill.RefillResult;
import com.arcraft.lootrefill.util.ItemBuilder;
import com.arcraft.lootrefill.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ficha técnica y panel administrativo detallado para un contenedor individual (Fase 5).
 * Permite inspeccionar identidad, estado, prioridad de loot, refill y ejecutar acciones
 * como asignación de Pool/Tabla, Forzar Refill, Forzar Populate, Teletransporte,
 * PLAYER -> MAP y Desregistro protegido con ConfirmationMenu.
 */
public class ContainerDetailMenu extends MenuHolder {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private final LootRefillPlugin plugin;
    private final LootContainer container;

    public ContainerDetailMenu(LootRefillPlugin plugin, LootContainer container, MenuHolder previousMenu) {
        super(54, "✦ Detalle: " + container.getContainerType().name() + " (" + container.getWorld() + ")");
        this.plugin = plugin;
        this.container = container;
        this.previousMenu = previousMenu;
    }

    public LootContainer getContainer() {
        return container;
    }

    @Override
    public void initialize(Player player) {
        fillBorders(BORDER_BLACK);
        fillBackground(BORDER_GRAY);

        long now = System.currentTimeMillis();

        // --- FILA 0: RESUMEN / CABECERA PRINCIPAL (Slot 4) ---
        ItemStack headerItem = new ItemBuilder(container.getContainerType().getMaterial())
                .name("§6✦ Ficha de Almacenamiento")
                .lore(
                        "§7ID: §f" + container.getId(),
                        "§7Mundo: §f" + container.getWorld(),
                        "§7Coordenadas: §f" + container.getX() + ", " + container.getY() + ", " + container.getZ(),
                        "§7Tipo: §e" + container.getContainerType().name(),
                        "",
                        "§7Creado: §8" + DATE_FORMAT.format(new Date(container.getCreatedAt())),
                        "§7Actualizado: §8" + DATE_FORMAT.format(new Date(container.getUpdatedAt()))
                )
                .build();
        setItem(4, headerItem);

        // --- FILA 1: TARJETAS INFORMATIVAS (Slots 10, 12, 14, 16) ---

        // Slot 10: Identidad y Origen
        String sourceColor = switch (container.getSource()) {
            case MAP -> "§aMAP";
            case PLAYER -> "§cPLAYER";
            case UNKNOWN -> "§7UNKNOWN";
        };
        ItemStack identityItem = new ItemBuilder(Material.ENDER_EYE)
                .name("§bOrigen e Identidad")
                .lore(
                        "§7Origen: " + sourceColor,
                        "§7Administrado: " + (container.isManaged() ? "§aSÍ" : "§cNO"),
                        "§7Registrado: " + (container.isRegistered() ? "§aSÍ" : "§cNO"),
                        "§7Habilitado: " + (container.isEnabled() ? "§aSÍ" : "§cNO"),
                        "§7Administrable: " + (container.isAdministrable() ? "§aSÍ" : "§cNO"),
                        "",
                        container.getSource() == ContainerSource.PLAYER
                                ? "§c⚠ Contenedor privado de jugador."
                                : "§a✔ Contenedor de mapa apto para LootRefill."
                )
                .build();
        setItem(10, identityItem);

        // Slot 12: Estado Operativo
        String statusColor = switch (container.getStatus()) {
            case ACTIVE -> "§aACTIVE";
            case BROKEN -> "§cBROKEN";
            case DISABLED -> "§eDISABLED";
        };
        Material statusMaterial = container.getStatus() == ContainerStatus.ACTIVE ? Material.IRON_DOOR : Material.REDSTONE_TORCH;
        ItemStack statusItem = new ItemBuilder(statusMaterial)
                .name("§6Estado Operativo: " + statusColor)
                .lore(
                        "§7Estado: " + statusColor,
                        "",
                        container.getStatus() == ContainerStatus.BROKEN
                                ? "§cEl contenedor físico fue destruido."
                                : (container.getStatus() == ContainerStatus.DISABLED
                                ? "§eEl contenedor está temporalmente deshabilitado."
                                : "§aEl contenedor opera normalmente.")
                )
                .build();
        setItem(12, statusItem);

        // Slot 14: Configuración y Prioridad de Loot
        String poolStr = container.getLootPoolId() != null ? "§d" + container.getLootPoolId() : "§8(Ninguno)";
        String tableStr = container.getLootTableId() != null ? "§e" + container.getLootTableId() : "§8(Ninguna)";
        String priorityDesc;
        if (container.getLootPoolId() != null && container.getLootTableId() != null) {
            priorityDesc = "§d★ LootPool activo (prioridad sobre la tabla)";
        } else if (container.getLootPoolId() != null) {
            priorityDesc = "§d★ LootPool activo";
        } else if (container.getLootTableId() != null) {
            priorityDesc = "§e★ LootTable legacy activa";
        } else {
            priorityDesc = "§cSin loot configurado";
        }

        ItemBuilder lootCard = new ItemBuilder(Material.CHEST)
                .name("§6Configuración de Loot")
                .addLore(
                        "§7Loot Pool: " + poolStr,
                        "§7Loot Table: " + tableStr,
                        "§7Prioridad: " + priorityDesc,
                        ""
                );

        List<String> recentSelections = container.getRecentSelections();
        if (!recentSelections.isEmpty()) {
            lootCard.addLore("§7Últimas selecciones de pool:");
            for (int i = 0; i < Math.min(3, recentSelections.size()); i++) {
                lootCard.addLore("  §8• §f" + recentSelections.get(i));
            }
            lootCard.addLore("");
        }
        lootCard.addLore("§8Pool > LootTable (Regla de prioridad)");
        setItem(14, lootCard.build());

        // Slot 16: Sistema de Refill
        int effectiveInterval = plugin.getRefillManager().getEffectiveIntervalForContainer(container);
        String nextRefillStr = "§8(No programado)";
        if (container.getNextRefill() != null) {
            if (container.getNextRefill() <= now) {
                long diffSec = (now - container.getNextRefill()) / 1000;
                nextRefillStr = "§cVencido (hace " + diffSec + "s)";
            } else {
                long diffSec = (container.getNextRefill() - now) / 1000;
                nextRefillStr = "§aEn " + diffSec + "s (" + (diffSec / 60) + "m)";
            }
        }

        String lastLootStr = container.getLastLoot() > 0
                ? "§f" + DATE_FORMAT.format(new Date(container.getLastLoot()))
                : "§8(Nunca)";

        ItemStack refillCard = new ItemBuilder(Material.CLOCK)
                .name("§eSistema de Refill")
                .lore(
                        "§7Refill activado: " + (container.isRefillEnabled() ? "§aACTIVADO" : "§cDESACTIVADO"),
                        "§7Intervalo efectivo: §f" + effectiveInterval + "s §8(" + (effectiveInterval / 60) + "m)",
                        "§7Último loot: " + lastLootStr,
                        "§7Próximo refill: " + nextRefillStr,
                        "§7Vencido (Due): " + (container.isDue() ? "§aSÍ" : "§cNO"),
                        "§7Elegible: " + (container.isEligibleForRefill() ? "§aSÍ" : "§cNO §8(" + container.getEligibilityReason() + ")")
                )
                .build();
        setItem(16, refillCard);

        // --- FILA 3: ACCIONES DE EDICIÓN Y OPERACIÓN (Slots 28, 29, 30, 32, 33, 34) ---

        // Slot 28: Asignar / Cambiar Loot Pool
        ItemBuilder poolBtn = new ItemBuilder(Material.ENDER_CHEST)
                .name("§dAsignar Loot Pool")
                .addLore(
                        "§7Pool asignado: §f" + (container.getLootPoolId() != null ? container.getLootPoolId() : "§8(Ninguno)"),
                        "",
                        "§eClick Izquierdo: §7Seleccionar de la lista"
                );
        if (container.getLootPoolId() != null) {
            poolBtn.addLore("§cClick Derecho: §7Quitar Loot Pool");
        }
        setItem(28, poolBtn.build(), event -> {
            if (event.isRightClick() && container.getLootPoolId() != null) {
                container.setLootPoolId(null);
                plugin.getContainerManager().saveContainer(container);
                MessageUtil.sendMessage(player, "&eLoot Pool desvinculado del contenedor.");
                initialize(player);
            } else {
                new SelectPoolMenu(plugin, this, container.getLootPoolId(), selectedPoolId -> {
                    container.setLootPoolId(selectedPoolId);
                    container.setManaged(true);
                    plugin.getContainerManager().saveContainer(container);
                    MessageUtil.sendMessage(player, "&aLoot Pool &d" + selectedPoolId + " &aasignado al contenedor.");
                    open(player);
                }).open(player);
            }
        });

        // Slot 29: Asignar / Cambiar Loot Table
        ItemBuilder tableBtn = new ItemBuilder(Material.BOOKSHELF)
                .name("§eAsignar Loot Table")
                .addLore(
                        "§7Tabla asignada: §f" + (container.getLootTableId() != null ? container.getLootTableId() : "§8(Ninguna)"),
                        "",
                        "§eClick Izquierdo: §7Seleccionar de la lista",
                        "§6Shift + Click: §7Escribir ID por chat"
                );
        if (container.getLootTableId() != null) {
            tableBtn.addLore("§cClick Derecho: §7Quitar Loot Table");
        }
        setItem(29, tableBtn.build(), event -> {
            if (event.isShiftClick()) {
                plugin.getGuiManager().getChatInputHandler().awaitInput(player,
                        "&aIngresa el ID de la tabla de loot (o escribe &ecancel&a):",
                        input -> {
                            String tableId = input.trim().toLowerCase();
                            if (!plugin.getLootManager().tableExists(tableId)) {
                                MessageUtil.sendMessage(player, "&cNo existe ninguna tabla con ID &e" + tableId + "&c.");
                            } else {
                                container.setLootTableId(tableId);
                                container.setManaged(true);
                                plugin.getContainerManager().saveContainer(container);
                                MessageUtil.sendMessage(player, "&aTabla &e" + tableId + " &aasignada al contenedor.");
                            }
                            open(player);
                        }
                );
            } else if (event.isRightClick() && container.getLootTableId() != null) {
                container.setLootTableId(null);
                plugin.getContainerManager().saveContainer(container);
                MessageUtil.sendMessage(player, "&eLoot Table desvinculada del contenedor.");
                initialize(player);
            } else {
                new SelectTableMenu(plugin, this, container.getLootTableId(), selectedTableId -> {
                    container.setLootTableId(selectedTableId);
                    container.setManaged(true);
                    plugin.getContainerManager().saveContainer(container);
                    MessageUtil.sendMessage(player, "&aTabla &e" + selectedTableId + " &aasignada al contenedor.");
                    open(player);
                }).open(player);
            }
        });

        // Slot 30: Alternar Refill
        ItemStack toggleRefillItem = new ItemBuilder(container.isRefillEnabled() ? Material.LIME_DYE : Material.GRAY_DYE)
                .name(container.isRefillEnabled() ? "§aRefill: Habilitado" : "§7Refill: Deshabilitado")
                .lore(
                        "§7Indica si este contenedor participa del ciclo",
                        "§7de reposición automática.",
                        "",
                        "§e» Haz clic para alternar"
                )
                .build();
        setItem(30, toggleRefillItem, event -> {
            container.setRefillEnabled(!container.isRefillEnabled());
            plugin.getContainerManager().saveContainer(container);
            initialize(player);
        });

        // Slot 32: Forzar Refill
        ItemStack forceRefillItem = new ItemBuilder(Material.BLAZE_POWDER)
                .name("§6⚡ Forzar Refill")
                .lore(
                        "§7Ejecuta una reposición inmediata de loot",
                        "§7respetando todas las reglas del sistema.",
                        "",
                        "§6» Haz clic para ejecutar Refill"
                )
                .build();
        setItem(32, forceRefillItem, event -> {
            RefillResult result = plugin.getRefillManager().refillContainer(container);
            switch (result) {
                case SUCCESS -> MessageUtil.sendMessage(player, "&a¡Refill forzado completado exitosamente!");
                case SKIPPED_NOT_EMPTY -> MessageUtil.sendMessage(player, "&cRefill saltado: el inventario no está vacío (require-empty activo).");
                case SKIPPED_PLAYER_NEARBY -> MessageUtil.sendMessage(player, "&cRefill saltado: hay jugadores cerca del contenedor.");
                case SKIPPED_DISABLED -> MessageUtil.sendMessage(player, "&cRefill saltado: contenedor o tipo deshabilitado (&e" + container.getEligibilityReason() + "&c).");
                case SKIPPED_CHUNK_NOT_LOADED -> MessageUtil.sendMessage(player, "&cRefill saltado: el chunk del contenedor no está cargado.");
                case SKIPPED_INVALID_LOOT_POOL -> MessageUtil.sendMessage(player, "&cRefill saltado: Loot Pool inválido o sin tablas.");
                case SKIPPED_NO_LOOT_TABLE -> MessageUtil.sendMessage(player, "&cRefill saltado: no tiene loot configurado o la tabla está deshabilitada.");
                default -> MessageUtil.sendMessage(player, "&eResultado de Refill: " + result.getDescription());
            }
            initialize(player);
        });

        // Slot 33: Forzar Populate
        ItemStack forcePopulateItem = new ItemBuilder(Material.NETHER_STAR)
                .name("§d✦ Forzar Populate")
                .lore(
                        "§7Genera loot inicial en el contenedor mediante",
                        "§7ContainerPopulator con las reglas de Populate.",
                        "",
                        "§d» Haz clic para ejecutar Populate"
                )
                .build();
        setItem(33, forcePopulateItem, event -> {
            PopulateResult result = plugin.getPopulateManager().getPopulator().populate(container);
            switch (result) {
                case POPULATED -> MessageUtil.sendMessage(player, "&a¡Contenedor poblado exitosamente con loot!");
                case SKIPPED_NOT_EMPTY -> MessageUtil.sendMessage(player, "&cPopulate saltado: el inventario no está vacío.");
                case SKIPPED_PLAYER -> MessageUtil.sendMessage(player, "&cPopulate cancelado: es un contenedor de jugador (PLAYER).");
                case SKIPPED_BROKEN -> MessageUtil.sendMessage(player, "&cPopulate cancelado: el contenedor está destruido (BROKEN).");
                case SKIPPED_DISABLED -> MessageUtil.sendMessage(player, "&cPopulate cancelado: el contenedor está deshabilitado.");
                case SKIPPED_INVALID_LOOT_POOL -> MessageUtil.sendMessage(player, "&cPopulate cancelado: Loot Pool inválido o sin tablas.");
                case SKIPPED_NO_LOOT_TABLE -> MessageUtil.sendMessage(player, "&cPopulate cancelado: sin loot configurado.");
                default -> MessageUtil.sendMessage(player, "&eResultado de Populate: " + result.name());
            }
            initialize(player);
        });

        // Slot 34: Teletransportar
        ItemStack tpItem = new ItemBuilder(Material.COMPASS)
                .name("§b✈ Teletransportarse al Contenedor")
                .lore(
                        "§7Destino: §f" + container.getX() + ", " + container.getY() + ", " + container.getZ() + " §8(§7" + container.getWorld() + "§8)",
                        "",
                        "§b» Haz clic para teletransportarte"
                )
                .build();
        setItem(34, tpItem, event -> {
            World w = Bukkit.getWorld(container.getWorld());
            if (w == null) {
                MessageUtil.sendMessage(player, "&cEl mundo &e" + container.getWorld() + " &cno está cargado en el servidor.");
                return;
            }
            Location loc = new Location(w, container.getX() + 0.5, container.getY() + 1.0, container.getZ() + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
            player.closeInventory();
            player.teleport(loc);
            MessageUtil.sendMessage(player, "&aTeletransportado al contenedor en &e" + container.getX() + ", " + container.getY() + ", " + container.getZ() + " &a(" + container.getWorld() + ").");
        });

        // --- FILA 4: ACCIONES CRÍTICAS CON CONFIRMACIÓN (Slots 38 y 42) ---

        // Slot 38: Convertir PLAYER -> MAP (solo disponible si es PLAYER)
        if (container.getSource() == ContainerSource.PLAYER) {
            ItemStack convertItem = new ItemBuilder(Material.GOLDEN_APPLE)
                    .name("§6⚡ Convertir PLAYER → MAP")
                    .lore(
                            "§7Convierte administrativamente este contenedor a MAP.",
                            "§aEl inventario existente se conservará intacto.",
                            "",
                            "§c⚠ Requiere confirmación administrativa.",
                            "§6» Haz clic para comenzar conversión"
                    )
                    .build();
            setItem(38, convertItem, event -> {
                new ConfirmationMenu(
                        "§6¿Convertir PLAYER a MAP?",
                        "§e" + container.getContainerType().name() + " §8(§f" + container.getWorld() + "§8)",
                        List.of(
                                "§7Posición: §f" + container.getX() + ", " + container.getY() + ", " + container.getZ(),
                                "",
                                "§7El contenedor dejará de ser §cPLAYER §7y pasará a §aMAP§7.",
                                "§7Podrá ser administrado y entrar en el sistema de refill.",
                                "§aEl inventario existente se conservará intacto.",
                                "",
                                "§6¿Confirmar conversión a MAP?"
                        ),
                        this,
                        p -> {
                            container.setSource(ContainerSource.MAP);
                            container.setStatus(ContainerStatus.ACTIVE);
                            container.setManaged(true);
                            container.setRegistered(true);
                            container.setUpdatedAt(System.currentTimeMillis());
                            plugin.getContainerManager().saveContainer(container);
                            MessageUtil.sendMessage(p, "&aContenedor convertido exitosamente de PLAYER a MAP.");
                            open(p);
                        },
                        p -> open(p)
                ).open(player);
            });
        }

        // Slot 42: Desregistrar Contenedor
        ItemStack unregisterItem = new ItemBuilder(Material.BARRIER)
                .name("§c✖ Desregistrar Contenedor")
                .lore(
                        "§7Elimina permanentemente este contenedor del",
                        "§7registro de LootRefill.",
                        "§7El bloque físico en el mundo no será eliminado.",
                        "",
                        "§c⚠ Requiere confirmación explícita.",
                        "§c» Haz clic para desregistrar"
                )
                .build();
        setItem(42, unregisterItem, event -> {
            new ConfirmationMenu(
                    "§c¿Desregistrar Contenedor?",
                    "§e" + container.getContainerType().name() + " §8(§f" + container.getWorld() + "§8)",
                    List.of(
                            "§7Posición: §f" + container.getX() + ", " + container.getY() + ", " + container.getZ(),
                            "§7Origen: §f" + container.getSource().name() + " §8| §7Estado: §f" + container.getStatus().name(),
                            "",
                            "§c⚠ Esta acción desregistrará permanentemente",
                            "§cel contenedor del sistema de LootRefill.",
                            "§7El bloque físico en el mundo no será eliminado."
                    ),
                    this,
                    p -> {
                        plugin.getContainerManager().unregisterContainer(container.getId());
                        MessageUtil.sendMessage(p, "&cContenedor desregistrado correctamente.");
                        if (previousMenu != null) {
                            previousMenu.open(p);
                        } else {
                            p.closeInventory();
                        }
                    },
                    p -> open(p)
            ).open(player);
        });

        // --- FILA 5: CONTROLES INFERIORES (Slots 45 y 53) ---

        // Slot 45: Volver
        setBackButton(45, previousMenu);

        // Slot 53: Refrescar
        ItemStack refreshItem = new ItemBuilder(Material.SUNFLOWER)
                .name("§e↻ Refrescar Ficha")
                .lore("§7Actualiza los datos mostrados en tiempo real.", "", "§e» Haz clic para refrescar")
                .build();
        setItem(53, refreshItem, event -> initialize(player));
    }

    /**
     * Sub-menú para seleccionar interactivamente un Loot Pool para el contenedor.
     */
    private static class SelectPoolMenu extends MenuHolder {
        private final LootRefillPlugin plugin;
        private final MenuHolder parentMenu;
        private final String currentSelectedId;
        private final Consumer<String> onSelected;
        private int page;

        public SelectPoolMenu(LootRefillPlugin plugin, MenuHolder parentMenu, String currentSelectedId, Consumer<String> onSelected) {
            super(54, "✦ Asignar Loot Pool");
            this.plugin = plugin;
            this.parentMenu = parentMenu;
            this.currentSelectedId = currentSelectedId;
            this.onSelected = onSelected;
            this.page = 0;
        }

        @Override
        public void initialize(Player player) {
            fillBorders(BORDER_BLACK);

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
                                    "§e» Haz clic para asignar este pool"
                            );

                    setItem(slot, item.build(), event -> onSelected.accept(pool.getId()));
                } else {
                    setItem(slot, BORDER_GRAY);
                }
            }

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
     * Sub-menú para seleccionar interactivamente una Loot Table para el contenedor.
     */
    private static class SelectTableMenu extends MenuHolder {
        private final LootRefillPlugin plugin;
        private final MenuHolder parentMenu;
        private final String currentSelectedId;
        private final Consumer<String> onSelected;
        private int page;

        public SelectTableMenu(LootRefillPlugin plugin, MenuHolder parentMenu, String currentSelectedId, Consumer<String> onSelected) {
            super(54, "✦ Asignar Loot Table");
            this.plugin = plugin;
            this.parentMenu = parentMenu;
            this.currentSelectedId = currentSelectedId;
            this.onSelected = onSelected;
            this.page = 0;
        }

        @Override
        public void initialize(Player player) {
            fillBorders(BORDER_BLACK);

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
                                    "§e» Haz clic para asignar esta tabla"
                            );

                    setItem(slot, item.build(), event -> onSelected.accept(table.getId()));
                } else {
                    setItem(slot, BORDER_GRAY);
                }
            }

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
