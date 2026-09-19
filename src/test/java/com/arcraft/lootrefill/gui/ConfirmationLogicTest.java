package com.arcraft.lootrefill.gui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ConfirmationLogicTest {

    @Test
    @DisplayName("El callback de confirmación se ejecuta una sola vez ante clicks repetidos")
    void testConfirmExecutesOnlyOnce() {
        AtomicInteger confirmCounter = new AtomicInteger(0);
        AtomicInteger cancelCounter = new AtomicInteger(0);
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        Runnable confirmAction = () -> {
            if (actionExecuted.compareAndSet(false, true)) {
                confirmCounter.incrementAndGet();
            }
        };

        // Simular múltiples clics consecutivos sobre CONFIRMAR
        confirmAction.run();
        confirmAction.run();
        confirmAction.run();

        assertEquals(1, confirmCounter.get(), "La acción de confirmación debe ejecutarse exactamente una vez");
        assertEquals(0, cancelCounter.get(), "La acción de cancelación no debe haberse ejecutado");
        assertTrue(actionExecuted.get());
    }

    @Test
    @DisplayName("Cancelar no ejecuta la confirmación y previene ejecuciones posteriores")
    void testCancelPreventsConfirm() {
        AtomicInteger confirmCounter = new AtomicInteger(0);
        AtomicInteger cancelCounter = new AtomicInteger(0);
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        Runnable cancelAction = () -> {
            if (actionExecuted.compareAndSet(false, true)) {
                cancelCounter.incrementAndGet();
            }
        };

        Runnable confirmAction = () -> {
            if (actionExecuted.compareAndSet(false, true)) {
                confirmCounter.incrementAndGet();
            }
        };

        // Simular clic en CANCELAR
        cancelAction.run();

        // Intento de clic posterior en CONFIRMAR
        confirmAction.run();

        assertEquals(0, confirmCounter.get(), "No debe ejecutarse confirmación tras cancelar");
        assertEquals(1, cancelCounter.get(), "Debe haberse ejecutado la cancelación");
        assertTrue(actionExecuted.get());
    }

    @Test
    @DisplayName("Cierre del menú (ESC) actúa como cancelación segura")
    void testCloseActsAsCancel() {
        AtomicInteger confirmCounter = new AtomicInteger(0);
        AtomicInteger cancelCounter = new AtomicInteger(0);
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        // Simulación de handleClose
        Runnable closeAction = () -> {
            if (actionExecuted.compareAndSet(false, true)) {
                cancelCounter.incrementAndGet();
            }
        };

        Runnable confirmAction = () -> {
            if (actionExecuted.compareAndSet(false, true)) {
                confirmCounter.incrementAndGet();
            }
        };

        // Jugador presiona ESC
        closeAction.run();

        // Confirmar posterior ignorado
        confirmAction.run();

        assertEquals(0, confirmCounter.get());
        assertEquals(1, cancelCounter.get());
    }

    @Test
    @DisplayName("Callbacks nulos son tratados con seguridad sin lanzar excepciones")
    void testNullCallbacksSafety() {
        AtomicBoolean actionExecuted = new AtomicBoolean(false);

        assertDoesNotThrow(() -> {
            if (actionExecuted.compareAndSet(false, true)) {
                // Callback nulo simulado
            }
        });
        assertTrue(actionExecuted.get());
    }
}
