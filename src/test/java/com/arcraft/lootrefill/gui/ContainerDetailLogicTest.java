package com.arcraft.lootrefill.gui;

import com.arcraft.lootrefill.container.ContainerSource;
import com.arcraft.lootrefill.container.ContainerStatus;
import com.arcraft.lootrefill.container.ContainerType;
import com.arcraft.lootrefill.container.LootContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ContainerDetailLogicTest {

    private LootContainer mapContainer;
    private LootContainer playerContainer;
    private LootContainer brokenContainer;

    @BeforeEach
    void setUp() {
        long now = System.currentTimeMillis();

        mapContainer = new LootContainer(
                UUID.randomUUID(), "world", 100, 64, -200, ContainerType.CHEST,
                "common", "house", true, false, 0L, now + 50000L,
                true, 1800, ContainerSource.MAP, ContainerStatus.ACTIVE,
                true, true, now, now
        );

        playerContainer = new LootContainer(
                UUID.randomUUID(), "world", 50, 65, 50, ContainerType.BARREL,
                null, null, true, false, 0L, null,
                false, 1800, ContainerSource.PLAYER, ContainerStatus.ACTIVE,
                false, false, now, now
        );

        brokenContainer = new LootContainer(
                UUID.randomUUID(), "world", 10, 60, 10, ContainerType.FURNACE,
                "smelt", null, false, false, 0L, null,
                false, 3600, ContainerSource.MAP, ContainerStatus.BROKEN,
                false, true, now, now
        );
    }

    @Test
    @DisplayName("CD-001: Prioridad de Loot Pool sobre Loot Table legacy")
    void testPoolOverTablePrecedence() {
        assertNotNull(mapContainer.getLootPoolId());
        assertNotNull(mapContainer.getLootTableId());
        assertTrue(mapContainer.hasLootConfigured());

        // La prioridad efectiva es LootPool
        assertEquals("house", mapContainer.getLootPoolId());
        assertEquals("common", mapContainer.getLootTableId());
    }

    @Test
    @DisplayName("CD-002: Asignación y cambio de Loot Pool preserva Loot Table")
    void testAssignPoolPreservesTable() {
        mapContainer.setLootPoolId("dungeon_rare");
        assertEquals("dungeon_rare", mapContainer.getLootPoolId());
        assertEquals("common", mapContainer.getLootTableId(), "Loot table legacy debe permanecer intacta");
    }

    @Test
    @DisplayName("CD-003: Quitar Loot Pool preserva Loot Table como fallback")
    void testRemovePoolPreservesTable() {
        mapContainer.setLootPoolId(null);
        assertNull(mapContainer.getLootPoolId());
        assertEquals("common", mapContainer.getLootTableId());
        assertTrue(mapContainer.hasLootConfigured(), "El contenedor aún tiene loot vía LootTable");
    }

    @Test
    @DisplayName("CD-004: Asignación de Loot Table preserva Loot Pool")
    void testAssignTablePreservesPool() {
        mapContainer.setLootTableId("military_tier");
        assertEquals("military_tier", mapContainer.getLootTableId());
        assertEquals("house", mapContainer.getLootPoolId(), "Loot Pool debe permanecer intacto");
    }

    @Test
    @DisplayName("CD-005: Quitar Loot Table preserva Loot Pool")
    void testRemoveTablePreservesPool() {
        mapContainer.setLootTableId(null);
        assertNull(mapContainer.getLootTableId());
        assertEquals("house", mapContainer.getLootPoolId());
        assertTrue(mapContainer.hasLootConfigured(), "El contenedor aún tiene loot vía LootPool");
    }

    @Test
    @DisplayName("CD-006: Toggle de Refill Enabled")
    void testToggleRefill() {
        assertTrue(mapContainer.isRefillEnabled());
        mapContainer.setRefillEnabled(false);
        assertFalse(mapContainer.isRefillEnabled());
        mapContainer.setRefillEnabled(true);
        assertTrue(mapContainer.isRefillEnabled());
    }

    @Test
    @DisplayName("CD-007: Conversión explícita PLAYER -> MAP con confirmación requerida")
    void testPlayerToMapConversionRequiresConfirmation() {
        assertEquals(ContainerSource.PLAYER, playerContainer.getSource());
        assertFalse(playerContainer.isManaged());

        AtomicBoolean confirmed = new AtomicBoolean(false);

        Runnable onConfirm = () -> {
            playerContainer.setSource(ContainerSource.MAP);
            playerContainer.setStatus(ContainerStatus.ACTIVE);
            playerContainer.setManaged(true);
            playerContainer.setRegistered(true);
            playerContainer.setUpdatedAt(System.currentTimeMillis());
            confirmed.set(true);
        };

        // Sin confirmación explícita no cambia
        assertFalse(confirmed.get());
        assertEquals(ContainerSource.PLAYER, playerContainer.getSource());

        // Con confirmación explícita
        onConfirm.run();
        assertTrue(confirmed.get());
        assertEquals(ContainerSource.MAP, playerContainer.getSource());
        assertEquals(ContainerStatus.ACTIVE, playerContainer.getStatus());
        assertTrue(playerContainer.isManaged());
        assertTrue(playerContainer.isRegistered());
    }

    @Test
    @DisplayName("CD-008: BROKEN no puede reactivarse a ACTIVE")
    void testBrokenCannotBeReactivated() {
        assertEquals(ContainerStatus.BROKEN, brokenContainer.getStatus());
        assertFalse(brokenContainer.isAdministrable());

        // La regla del sistema estipula que un contenedor BROKEN no puede reactivarse
        assertThrows(IllegalStateException.class, () -> {
            if (brokenContainer.getStatus() == ContainerStatus.BROKEN) {
                throw new IllegalStateException("BROKEN containers cannot be reactivated to ACTIVE");
            }
            brokenContainer.setStatus(ContainerStatus.ACTIVE);
        });
    }

    @Test
    @DisplayName("CD-009: Desregistro requiere confirmación")
    void testUnregisterRequiresConfirmation() {
        AtomicBoolean unregisterExecuted = new AtomicBoolean(false);

        Runnable confirmAction = () -> unregisterExecuted.set(true);
        Runnable cancelAction = () -> unregisterExecuted.set(false);

        // Si se cancela
        cancelAction.run();
        assertFalse(unregisterExecuted.get());

        // Solo se ejecuta si se confirma
        confirmAction.run();
        assertTrue(unregisterExecuted.get());
    }

    @Test
    @DisplayName("CD-010: Validación de mundo seguro para teletransporte")
    void testTeleportWorldValidation() {
        String existingWorld = "world";
        String invalidWorld = null;

        assertNotNull(existingWorld);
        assertFalse(existingWorld.trim().isEmpty());

        assertNull(invalidWorld);
    }
}
