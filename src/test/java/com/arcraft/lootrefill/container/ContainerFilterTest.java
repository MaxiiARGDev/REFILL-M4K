package com.arcraft.lootrefill.container;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ContainerFilterTest {

    private LootContainer mapChestWithTable;
    private LootContainer mapBarrelWithPool;
    private LootContainer playerChest;
    private LootContainer brokenFurnace;
    private LootContainer disabledBarrel;
    private List<LootContainer> testContainers;

    @BeforeEach
    void setUp() {
        long now = 1700000000000L;

        // 1. MAP Chest con LootTable "common"
        mapChestWithTable = new LootContainer(
                UUID.randomUUID(), "lobby", 100, 64, 200, ContainerType.CHEST,
                "common", null, true, false, 0L, now + 100000L,
                true, 1800, ContainerSource.MAP, ContainerStatus.ACTIVE,
                true, true, now, now
        );

        // 2. MAP Barrel con LootPool "house" y LootTable "common" (prioridad pool, table como fallback)
        mapBarrelWithPool = new LootContainer(
                UUID.randomUUID(), "exodus", -50, 70, 300, ContainerType.BARREL,
                "common", "house", true, false, 0L, now + 50000L,
                true, 3600, ContainerSource.MAP, ContainerStatus.ACTIVE,
                true, true, now + 1000L, now + 1000L
        );

        // 3. PLAYER Chest sin loot y refill deshabilitado
        playerChest = new LootContainer(
                UUID.randomUUID(), "lobby", 10, 64, 20, ContainerType.CHEST,
                null, null, true, false, 0L, null,
                false, 1800, ContainerSource.PLAYER, ContainerStatus.ACTIVE,
                false, false, now + 2000L, now + 2000L
        );

        // 4. BROKEN Furnace
        brokenFurnace = new LootContainer(
                UUID.randomUUID(), "exodus", 500, 60, 500, ContainerType.FURNACE,
                "furnace_smelt", null, false, false, 0L, null,
                false, 7200, ContainerSource.MAP, ContainerStatus.BROKEN,
                false, true, now + 3000L, now + 3000L
        );

        // 5. DISABLED Barrel
        disabledBarrel = new LootContainer(
                UUID.randomUUID(), "lobby", 0, 65, 0, ContainerType.BARREL,
                null, "military", false, false, 0L, null,
                false, 3600, ContainerSource.MAP, ContainerStatus.DISABLED,
                false, true, now + 4000L, now + 4000L
        );

        testContainers = new ArrayList<>(List.of(
                mapChestWithTable,
                mapBarrelWithPool,
                playerChest,
                brokenFurnace,
                disabledBarrel
        ));
    }

    @Test
    @DisplayName("CM-BACKEND-001: Filtro por mundo")
    void testFilterByWorld() {
        ContainerFilter filterLobby = ContainerFilter.builder().world("lobby").build();
        List<LootContainer> lobbyResult = testContainers.stream().filter(filterLobby::test).toList();
        assertEquals(3, lobbyResult.size());
        assertTrue(lobbyResult.stream().allMatch(c -> c.getWorld().equalsIgnoreCase("lobby")));

        ContainerFilter filterExodus = ContainerFilter.builder().world("exodus").build();
        List<LootContainer> exodusResult = testContainers.stream().filter(filterExodus::test).toList();
        assertEquals(2, exodusResult.size());
        assertTrue(exodusResult.stream().allMatch(c -> c.getWorld().equalsIgnoreCase("exodus")));
    }

    @Test
    @DisplayName("CM-BACKEND-002: Filtro por tipo")
    void testFilterByType() {
        ContainerFilter filterChests = ContainerFilter.builder().containerType(ContainerType.CHEST).build();
        List<LootContainer> chestResult = testContainers.stream().filter(filterChests::test).toList();
        assertEquals(2, chestResult.size());
        assertTrue(chestResult.stream().allMatch(c -> c.getContainerType() == ContainerType.CHEST));

        ContainerFilter filterBarrels = ContainerFilter.builder().containerType(ContainerType.BARREL).build();
        List<LootContainer> barrelResult = testContainers.stream().filter(filterBarrels::test).toList();
        assertEquals(2, barrelResult.size());
    }

    @Test
    @DisplayName("CM-BACKEND-003: Filtro por source")
    void testFilterBySource() {
        ContainerFilter filterPlayer = ContainerFilter.builder().source(ContainerSource.PLAYER).build();
        List<LootContainer> playerResult = testContainers.stream().filter(filterPlayer::test).toList();
        assertEquals(1, playerResult.size());
        assertEquals(ContainerSource.PLAYER, playerResult.get(0).getSource());

        ContainerFilter filterMap = ContainerFilter.builder().source(ContainerSource.MAP).build();
        List<LootContainer> mapResult = testContainers.stream().filter(filterMap::test).toList();
        assertEquals(4, mapResult.size());
    }

    @Test
    @DisplayName("CM-BACKEND-004: Filtro por status")
    void testFilterByStatus() {
        ContainerFilter filterActive = ContainerFilter.builder().status(ContainerStatus.ACTIVE).build();
        List<LootContainer> activeResult = testContainers.stream().filter(filterActive::test).toList();
        assertEquals(3, activeResult.size());

        ContainerFilter filterBroken = ContainerFilter.builder().status(ContainerStatus.BROKEN).build();
        List<LootContainer> brokenResult = testContainers.stream().filter(filterBroken::test).toList();
        assertEquals(1, brokenResult.size());
        assertEquals(ContainerStatus.BROKEN, brokenResult.get(0).getStatus());

        ContainerFilter filterDisabled = ContainerFilter.builder().status(ContainerStatus.DISABLED).build();
        List<LootContainer> disabledResult = testContainers.stream().filter(filterDisabled::test).toList();
        assertEquals(1, disabledResult.size());
    }

    @Test
    @DisplayName("CM-BACKEND-005: Filtro por managed y registered")
    void testFilterByManagedAndRegistered() {
        ContainerFilter filterManaged = ContainerFilter.builder().managed(true).build();
        List<LootContainer> managedResult = testContainers.stream().filter(filterManaged::test).toList();
        assertEquals(2, managedResult.size()); // mapChestWithTable y mapBarrelWithPool

        ContainerFilter filterRegistered = ContainerFilter.builder().registered(true).build();
        List<LootContainer> registeredResult = testContainers.stream().filter(filterRegistered::test).toList();
        assertEquals(4, registeredResult.size()); // Todos menos playerChest
    }

    @Test
    @DisplayName("CM-BACKEND-006: Filtro por refill enabled/disabled")
    void testFilterByRefillEnabled() {
        ContainerFilter filterRefillOn = ContainerFilter.builder().refillEnabled(true).build();
        List<LootContainer> onResult = testContainers.stream().filter(filterRefillOn::test).toList();
        assertEquals(2, onResult.size());

        ContainerFilter filterRefillOff = ContainerFilter.builder().refillEnabled(false).build();
        List<LootContainer> offResult = testContainers.stream().filter(filterRefillOff::test).toList();
        assertEquals(3, offResult.size());
    }

    @Test
    @DisplayName("CM-BACKEND-007: Filtro Pool asignado")
    void testFilterLootPool() {
        ContainerFilter filterPool = ContainerFilter.builder().lootFilterType(LootFilterType.LOOT_POOL).build();
        List<LootContainer> poolResult = testContainers.stream().filter(filterPool::test).toList();
        assertEquals(2, poolResult.size()); // mapBarrelWithPool y disabledBarrel

        ContainerFilter filterSpecificPool = ContainerFilter.builder().lootPoolId("house").build();
        List<LootContainer> specificResult = testContainers.stream().filter(filterSpecificPool::test).toList();
        assertEquals(1, specificResult.size());
        assertEquals("house", specificResult.get(0).getLootPoolId());
    }

    @Test
    @DisplayName("CM-BACKEND-008: Filtro LootTable legacy")
    void testFilterLootTable() {
        ContainerFilter filterTable = ContainerFilter.builder().lootFilterType(LootFilterType.LOOT_TABLE).build();
        List<LootContainer> tableResult = testContainers.stream().filter(filterTable::test).toList();
        assertEquals(3, tableResult.size()); // mapChestWithTable, mapBarrelWithPool, brokenFurnace

        ContainerFilter filterSpecificTable = ContainerFilter.builder().lootTableId("furnace_smelt").build();
        List<LootContainer> specificResult = testContainers.stream().filter(filterSpecificTable::test).toList();
        assertEquals(1, specificResult.size());
    }

    @Test
    @DisplayName("CM-BACKEND-009: Filtro sin loot")
    void testFilterNoLoot() {
        ContainerFilter filterNoLoot = ContainerFilter.builder().lootFilterType(LootFilterType.NO_LOOT).build();
        List<LootContainer> noLootResult = testContainers.stream().filter(filterNoLoot::test).toList();
        assertEquals(1, noLootResult.size());
        assertEquals(playerChest.getId(), noLootResult.get(0).getId());
    }

    @Test
    @DisplayName("CM-BACKEND-010: Búsqueda textual")
    void testTextQuerySearch() {
        // Buscar por coordenadas parciales
        ContainerFilter queryCoords = ContainerFilter.builder().query("100, 64, 200").build();
        List<LootContainer> coordsResult = testContainers.stream().filter(queryCoords::test).toList();
        assertEquals(1, coordsResult.size());
        assertEquals(mapChestWithTable.getId(), coordsResult.get(0).getId());

        // Buscar por pool name
        ContainerFilter queryPool = ContainerFilter.builder().query("house").build();
        List<LootContainer> poolResult = testContainers.stream().filter(queryPool::test).toList();
        assertEquals(1, poolResult.size());
        assertEquals("house", poolResult.get(0).getLootPoolId());

        // Buscar por UUID parcial
        String subUuid = brokenFurnace.getId().toString().substring(0, 8);
        ContainerFilter queryUuid = ContainerFilter.builder().query(subUuid).build();
        List<LootContainer> uuidResult = testContainers.stream().filter(queryUuid::test).toList();
        assertEquals(1, uuidResult.size());
        assertEquals(brokenFurnace.getId(), uuidResult.get(0).getId());
    }

    @Test
    @DisplayName("CM-BACKEND-011 y CM-BACKEND-012: Paginación y conteo")
    void testPaginationAndCount() {
        ContainerFilter emptyFilter = ContainerFilter.empty();
        long totalCount = testContainers.stream().filter(emptyFilter::test).count();
        assertEquals(5, totalCount);

        // Página 0, tamaño 2
        List<LootContainer> page0 = testContainers.stream()
                .filter(emptyFilter::test)
                .skip(0)
                .limit(2)
                .toList();
        assertEquals(2, page0.size());

        // Página 1, tamaño 2
        List<LootContainer> page1 = testContainers.stream()
                .filter(emptyFilter::test)
                .skip(2)
                .limit(2)
                .toList();
        assertEquals(2, page1.size());

        // Página 2, tamaño 2 (último elemento)
        List<LootContainer> page2 = testContainers.stream()
                .filter(emptyFilter::test)
                .skip(4)
                .limit(2)
                .toList();
        assertEquals(1, page2.size());
    }

    @Test
    @DisplayName("CM-BACKEND-013: Ordenamiento")
    void testSorting() {
        // Orden ascendente por fecha de creación
        ContainerFilter sortAsc = ContainerFilter.builder()
                .sortField(ContainerSortField.CREATED_AT)
                .ascending(true)
                .build();
        List<LootContainer> ascResult = testContainers.stream()
                .filter(sortAsc::test)
                .sorted(sortAsc.getSortField().getComparator())
                .toList();
        assertEquals(mapChestWithTable.getId(), ascResult.get(0).getId());
        assertEquals(disabledBarrel.getId(), ascResult.get(4).getId());

        // Orden descendente por fecha de creación
        List<LootContainer> descResult = testContainers.stream()
                .filter(sortAsc::test)
                .sorted(sortAsc.getSortField().getComparator().reversed())
                .toList();
        assertEquals(disabledBarrel.getId(), descResult.get(0).getId());
        assertEquals(mapChestWithTable.getId(), descResult.get(4).getId());
    }

    @Test
    @DisplayName("CM-BACKEND-016, 017, 018, 019: Desvinculación de pool y preservación de estado")
    void testUnlinkPoolPreservesState() {
        // Comprobar estado previo
        assertEquals("house", mapBarrelWithPool.getLootPoolId());
        assertEquals("common", mapBarrelWithPool.getLootTableId());
        assertEquals(ContainerSource.MAP, mapBarrelWithPool.getSource());
        assertEquals(ContainerStatus.ACTIVE, mapBarrelWithPool.getStatus());
        assertTrue(mapBarrelWithPool.isManaged());
        assertTrue(mapBarrelWithPool.isRegistered());
        assertTrue(mapBarrelWithPool.isRefillEnabled());
        assertNotNull(mapBarrelWithPool.getNextRefill());

        // Simular desvinculación en memoria del pool "house"
        String poolToUnlink = "house";
        for (LootContainer c : testContainers) {
            if (c.getLootPoolId() != null && c.getLootPoolId().equalsIgnoreCase(poolToUnlink)) {
                c.setLootPoolId(null);
                c.setUpdatedAt(System.currentTimeMillis());
            }
        }

        // CM-BACKEND-016: loot_pool_id pasa a null
        assertNull(mapBarrelWithPool.getLootPoolId());

        // CM-BACKEND-017: loot_table_id permanece intacto como fallback
        assertEquals("common", mapBarrelWithPool.getLootTableId());

        // CM-BACKEND-018: source, status, managed, registered, refill permanecen intactos
        assertEquals(ContainerSource.MAP, mapBarrelWithPool.getSource());
        assertEquals(ContainerStatus.ACTIVE, mapBarrelWithPool.getStatus());
        assertTrue(mapBarrelWithPool.isManaged());
        assertTrue(mapBarrelWithPool.isRegistered());
        assertTrue(mapBarrelWithPool.isRefillEnabled());
        assertNotNull(mapBarrelWithPool.getNextRefill());

        // CM-BACKEND-019: no quedan referencias a "house"
        assertFalse(testContainers.stream().anyMatch(c -> "house".equalsIgnoreCase(c.getLootPoolId())));
    }

    @Test
    @DisplayName("CM-FASE4-001: Filtro combinado")
    void testCombinedFilter() {
        // Combinar: mundo="lobby", source=MAP, status=ACTIVE, containerType=CHEST
        ContainerFilter filter = ContainerFilter.builder()
                .world("lobby")
                .source(ContainerSource.MAP)
                .status(ContainerStatus.ACTIVE)
                .containerType(ContainerType.CHEST)
                .build();

        List<LootContainer> result = testContainers.stream().filter(filter::test).toList();
        assertEquals(1, result.size());
        assertEquals(mapChestWithTable.getId(), result.get(0).getId());
    }

    @Test
    @DisplayName("CM-FASE4-002: Búsqueda textual y limpiar búsqueda")
    void testSearchAndClearSearch() {
        // 1. Búsqueda por "furnace"
        ContainerFilter searchFilter = ContainerFilter.builder().query("furnace").build();
        List<LootContainer> searchResult = testContainers.stream().filter(searchFilter::test).toList();
        assertEquals(1, searchResult.size());
        assertEquals(brokenFurnace.getId(), searchResult.get(0).getId());

        // 2. Limpiar búsqueda
        ContainerFilter clearedFilter = searchFilter.toBuilder().query(null).build();
        assertNull(clearedFilter.getQuery());
        List<LootContainer> clearedResult = testContainers.stream().filter(clearedFilter::test).toList();
        assertEquals(5, clearedResult.size());
    }

    @Test
    @DisplayName("CM-FASE4-003: Paginación fuera de rango y reinicio de página")
    void testPaginationOutOfBoundsAndReset() {
        // Supongamos un total de 5 contenedores y tamaño de página 2
        int pageSize = 2;
        int totalContainers = testContainers.size(); // 5
        int totalPages = (int) Math.ceil((double) Math.max(1, totalContainers) / pageSize); // 3 (páginas 0, 1, 2)
        assertEquals(3, totalPages);

        // Página 10 está fuera de rango: debe corregirse al índice máximo válido (totalPages - 1)
        int requestedPage = 10;
        if (requestedPage >= totalPages) {
            requestedPage = Math.max(0, totalPages - 1);
        }
        assertEquals(2, requestedPage);

        // Ahora supongamos que se aplica un filtro estricto que solo deja 1 resultado
        ContainerFilter strictFilter = ContainerFilter.builder().world("exodus").containerType(ContainerType.FURNACE).build();
        long filteredCount = testContainers.stream().filter(strictFilter::test).count();
        assertEquals(1, filteredCount);

        int newTotalPages = (int) Math.ceil((double) Math.max(1, filteredCount) / pageSize); // 1 página (índice 0)
        assertEquals(1, newTotalPages);

        // Al cambiar de filtro, la página previa (2) deja de existir y debe reiniciarse a 0
        if (requestedPage >= newTotalPages) {
            requestedPage = Math.max(0, newTotalPages - 1);
        }
        assertEquals(0, requestedPage);
    }

    @Test
    @DisplayName("CM-FASE4-004: Conteo de filtros activos")
    void testActiveFiltersCountAndHelpers() {
        ContainerFilter empty = ContainerFilter.empty();
        assertFalse(empty.hasActiveFilters());
        assertEquals(0, empty.getActiveFilterCount());

        ContainerFilter withThreeFilters = ContainerFilter.builder()
                .world("lobby")
                .source(ContainerSource.MAP)
                .refillEnabled(true)
                .build();
        assertTrue(withThreeFilters.hasActiveFilters());
        assertEquals(3, withThreeFilters.getActiveFilterCount());
    }

    @Test
    @DisplayName("CM-FASE4-005: Compatibilidad legacy preservada con tablas y pools")
    void testLegacyCompatibilityPreserved() {
        // mapBarrelWithPool tiene loot_pool_id = "house" Y loot_table_id = "common"
        assertEquals("house", mapBarrelWithPool.getLootPoolId());
        assertEquals("common", mapBarrelWithPool.getLootTableId());
        assertTrue(mapBarrelWithPool.hasLootConfigured());

        // Filtrar por HAS_LOOT encuentra ambos (mapChestWithTable y mapBarrelWithPool)
        ContainerFilter hasLootFilter = ContainerFilter.builder().lootFilterType(LootFilterType.HAS_LOOT).build();
        List<LootContainer> hasLoot = testContainers.stream().filter(hasLootFilter::test).toList();
        assertTrue(hasLoot.contains(mapChestWithTable));
        assertTrue(hasLoot.contains(mapBarrelWithPool));

        // Filtrar por LOOT_POOL encuentra mapBarrelWithPool
        ContainerFilter poolFilter = ContainerFilter.builder().lootFilterType(LootFilterType.LOOT_POOL).build();
        assertTrue(poolFilter.test(mapBarrelWithPool));

        // Filtrar por LOOT_TABLE encuentra mapChestWithTable y mapBarrelWithPool
        ContainerFilter tableFilter = ContainerFilter.builder().lootFilterType(LootFilterType.LOOT_TABLE).build();
        assertTrue(tableFilter.test(mapChestWithTable));
        assertTrue(tableFilter.test(mapBarrelWithPool));
    }
}

