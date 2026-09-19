package com.arcraft.lootrefill.pool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LootPoolModelTest {

    private LootPool pool;

    @BeforeEach
    void setUp() {
        pool = new LootPool("dungeon_loot", "Dungeon Loot", PoolSelectionMode.SINGLE_RANDOM, 1, 1, false, true);
    }

    @Test
    @DisplayName("Creación de Pool: valores iniciales y normalización")
    void testPoolCreation() {
        assertEquals("dungeon_loot", pool.getId());
        assertEquals("Dungeon Loot", pool.getName());
        assertEquals(PoolSelectionMode.SINGLE_RANDOM, pool.getSelectionMode());
        assertEquals(1, pool.getMinRolls());
        assertEquals(1, pool.getMaxRolls());
        assertFalse(pool.isAllowDuplicates());
        assertTrue(pool.isEnabled());
        assertFalse(pool.hasEntries());
        assertEquals(0, pool.getTotalWeight());
    }

    @Test
    @DisplayName("Edición de Nombre y Estado")
    void testEditNameAndState() {
        pool.setName("Dungeon Updated");
        assertEquals("Dungeon Updated", pool.getName());

        pool.setEnabled(false);
        assertFalse(pool.isEnabled());
    }

    @Test
    @DisplayName("Cambio de Selection Mode y Toggle Duplicados")
    void testChangeSelectionModeAndDuplicates() {
        pool.setSelectionMode(PoolSelectionMode.MIXED_RANDOM);
        assertEquals(PoolSelectionMode.MIXED_RANDOM, pool.getSelectionMode());

        pool.setAllowDuplicates(true);
        assertTrue(pool.isAllowDuplicates());

        pool.setAllowDuplicates(false);
        assertFalse(pool.isAllowDuplicates());
    }

    @Test
    @DisplayName("Validación de Min y Max Rolls")
    void testRollsValidation() {
        // minRolls no puede ser menor a 1
        pool.setMinRolls(0);
        assertEquals(1, pool.getMinRolls());

        // Al subir minRolls más allá de maxRolls, maxRolls se ajusta
        pool.setMinRolls(5);
        assertEquals(5, pool.getMinRolls());
        assertEquals(5, pool.getMaxRolls());

        // Subir maxRolls
        pool.setMaxRolls(10);
        assertEquals(10, pool.getMaxRolls());

        // Bajar maxRolls por debajo de minRolls lo fuerza al mínimo
        pool.setMaxRolls(3);
        assertEquals(5, pool.getMaxRolls());
    }

    @Test
    @DisplayName("Añadir tablas, modificar peso y prevenir duplicados")
    void testEntriesManagement() {
        LootPoolEntry entry1 = new LootPoolEntry("dungeon_loot", "common", 40);
        LootPoolEntry entry2 = new LootPoolEntry("dungeon_loot", "rare", 10);

        pool.addEntry(entry1);
        pool.addEntry(entry2);

        assertTrue(pool.hasEntries());
        assertEquals(2, pool.getEntries().size());
        assertEquals(50, pool.getTotalWeight());

        // Comprobar obtención
        assertNotNull(pool.getEntry("common"));
        assertEquals(40, pool.getEntry("common").getWeight());

        // Sobrescribir (misma tabla actualiza entrada en vez de duplicar)
        LootPoolEntry updatedEntry = new LootPoolEntry("dungeon_loot", "common", 60);
        pool.addEntry(updatedEntry);

        assertEquals(2, pool.getEntries().size());
        assertEquals(60, pool.getEntry("common").getWeight());
        assertEquals(70, pool.getTotalWeight());

        // Modificar peso directamente
        pool.getEntry("rare").setWeight(15);
        assertEquals(15, pool.getEntry("rare").getWeight());
        assertEquals(75, pool.getTotalWeight());

        // Eliminar miembro del pool
        pool.removeEntry("common");
        assertEquals(1, pool.getEntries().size());
        assertNull(pool.getEntry("common"));
        assertNotNull(pool.getEntry("rare"));
        assertEquals(15, pool.getTotalWeight());
    }
}
